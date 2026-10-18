package com.nerdfm.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Save round trip, older-save migration and multi-season runs for the M2 medical layer. */
class EcoSoakM2Test {
    @get:Rule val temp = TemporaryFolder()

    private fun check(w: World, label: String) {
        assertEquals(label, emptyList<WorldIntegrity.Issue>(), WorldIntegrity.inspect(w))
        val s = MedicalRules.state(w)
        for ((id, pm) in s.players) {
            val p = w.players[id]
            assertNotNull("$label stale medical record $id", p)
            assertTrue("$label non-finite load", pm.acute.isFinite() && pm.chronic.isFinite() && pm.acute >= 0f && pm.chronic >= 0f)
            assertTrue("$label sinceReturn ${pm.sinceReturn}", pm.sinceReturn in -1..MedicalRules.RELAPSE_WINDOW)
            assertTrue("$label injuries ${pm.injuries}", pm.injuries >= 0 && pm.relapses in 0..pm.injuries)
            assertTrue("$label injWeeks ${p!!.injWeeks}", p.injWeeks in 0..40)
        }
        assertTrue("$label log ${s.log.size}", s.log.size <= MedicalRules.LOG_LIMIT)
        assertTrue(s.seasonInjuries.values.all { it >= 0 } && s.seasonWeeksLost.values.all { it >= 0 })
        for (r in MedicalRules.riskRows(w)) assertTrue(r.acwr.isFinite() && r.risk in -1..2 && r.phase in 0..3)
    }

    @Test fun threeSeasonsStayConsistentAndRelapsesAreRareButReal() {
        val w = EcoTestSupport.world(61L)
        var playerWeeks = 0L; var injured = 0L
        repeat(3) { season ->
            for (wk in 1..34) {
                EcoTestSupport.play(w, 1)
                for (p in w.players.values) if (p.clubId >= 0) { playerWeeks++; if (p.injWeeks > 0) injured++ }
            }
            check(w, "season ${season + 1}")
        }
        val s = MedicalRules.state(w)
        val per1000 = injured * 1000.0 / playerWeeks
        assertTrue("prevalence $per1000", per1000 in 20.0..32.0)
        assertTrue("relapses ${s.totalRelapses}", s.totalRelapses in 100..2500)
        assertTrue("overuse ${s.totalOveruse}", s.totalOveruse in 20..2500)
        val injuries = s.players.values.sumOf { it.injuries }
        assertTrue("relapse share", (s.totalRelapses + s.totalOveruse).toDouble() / injuries < 0.35)
    }

    @Test fun medicalStateSurvivesSaveAndLoad() {
        val w = EcoTestSupport.world(62L)
        EcoTestSupport.play(w, 25)
        MedicalRules.state(w).policy = RehabPolicy.CAUTIOUS
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, w))
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        val a = MedicalRules.state(w); val b = MedicalRules.state(loaded)
        assertEquals(RehabPolicy.CAUTIOUS, b.policy)
        assertEquals(a.players.size, b.players.size)
        assertEquals(a.log.size, b.log.size)
        assertEquals(a.totalRelapses, b.totalRelapses)
        for ((id, pm) in a.players) { val o = b.players.getValue(id); assertEquals(pm.acute, o.acute, 0f); assertEquals(pm.injuries, o.injuries); assertEquals(pm.sinceReturn, o.sinceReturn) }
        check(loaded, "loaded")
        EcoTestSupport.play(loaded, 12)
        check(loaded, "loaded+12")
    }

    @Test fun sameSeedGivesIdenticalMedicalHistory() {
        fun run(): List<Int> { val w = EcoTestSupport.world(63L); EcoTestSupport.play(w, 30); val s = MedicalRules.state(w); return listOf(s.totalRelapses, s.totalOveruse, s.players.values.sumOf { it.injuries }, s.log.size) }
        assertEquals(run(), run())
    }

    @Test fun formatFiveSaveFromM1LoadsGainsTheMedicalSectionAndPlaysASeason() {
        val dir = temp.newFolder()
        File(dir, "career1.dat").writeBytes(javaClass.classLoader!!.getResourceAsStream("saves/career_v5_m1.dat")!!.use { it.readBytes() })
        val w = (Persistence.load(dir) as LoadResult.Loaded).world
        assertEquals(Persistence.CURRENT_SAVE_VERSION, w.saveVersion)
        assertEquals("Brookbury United", w.human().name)
        assertEquals(8, w.week)
        assertNotNull("M1 sections survive", w.ecosystem!!.staff); assertNotNull(w.ecosystem!!.board); assertNotNull(w.ecosystem!!.identity)
        assertNull("medical section is created lazily", w.ecosystem!!.medical)
        MedicalRules.repair(w)
        assertEquals(RehabPolicy.STANDARD, MedicalRules.state(w).policy)
        val startYear = w.year
        var guard = 0
        while (w.year == startYear && guard++ < 60) EcoTestSupport.play(w, 1)
        assertTrue(w.year > startYear)
        check(w, "after migrated season")
        assertTrue(MedicalRules.state(w).players.isNotEmpty())
    }

    @Test fun damagedRecordsAreRepairedOnLoad() {
        val w = EcoTestSupport.world(64L)
        EcoTestSupport.play(w, 3)
        val s = MedicalRules.state(w)
        val any = s.players.values.first()
        any.acute = Float.NaN; any.sinceReturn = 99; any.lastInjWeeks = -4
        s.log.add(InjuryRecord(-999, 2026, 1, "ghost", 1, false))
        MedicalRules.repair(w)
        assertTrue(any.acute.isFinite() && any.sinceReturn == -1 && any.lastInjWeeks == 0 && !any.seeded)
        assertTrue(s.log.none { it.playerId == -999 })
        // An older save with no medical section is left alone.
        val old = EcoTestSupport.world(65L); MedicalRules.repair(old); assertNull(old.ecosystem?.medical)
    }

    @Test fun safeModeLeavesMedicalStateUntouched() {
        val w = EcoTestSupport.world(66L)
        Ecosystem.enabled = false
        try { EcoTestSupport.play(w, 10) } finally { Ecosystem.enabled = true }
        assertNull(w.ecosystem?.medical)
    }
}
