package com.nerdfm.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Save round trip, older-save migration, integration and multi-season runs for the M3 data layer. */
class EcoSoakM3Test {
    @get:Rule val temp = TemporaryFolder()

    private fun check(w: World, label: String) {
        assertEquals(label, emptyList<WorldIntegrity.Issue>(), WorldIntegrity.inspect(w))
        val level = Analytics.humanLevel(w)
        assertTrue("$label level $level", level in 0f..100f)
        val b = w.biz()
        assertTrue("$label reports ${b.reports.size}", b.reports.size <= 40)
        assertTrue("$label stale knowledge", b.knowledge.keys.all { w.players.containsKey(it) })
        assertTrue("$label knowledge range", b.knowledge.values.all { it in 0..100 })
        val targets = Analytics.dataTargets(w)
        assertTrue("$label targets ${targets.size}", targets.size <= 8 && targets.all { it.player.clubId != w.humanClubId })
        assertTrue(targets.map { it.player.id }.toSet().size == targets.size)
        val opp = Analytics.nextOpponent(w)
        if (opp != null) {
            val r = Analytics.oppositionReport(w, opp)
            assertTrue("$label confidence ${r.confidence}", r.confidence in 0..100 && r.keyPlayerOvr in 0..120)
        }
        for (row in Analytics.performanceRows(w)) assertTrue(row.xgPer90.isFinite() && row.keyPassesPer90.isFinite() && row.defensivePer90.isFinite())
    }

    private fun hireScouts(w: World) {
        val b = w.biz()
        Business.ensureStarted(w)
        ScoutingNetwork.refreshMarket(w, w.rng(3))
        EcoTestSupport.richHuman(w)
        for (s in b.scoutMarket.toList().take(2)) ScoutingNetwork.hire(w, s.id)
        for ((i, s) in b.scouts.withIndex()) ScoutingNetwork.assign(w, s.id, i, -1)
    }

    @Test fun threeSeasonsWithScoutsAndChangingPlansStayConsistent() {
        val w = EcoTestSupport.world(71L)
        hireScouts(w)
        assertTrue("scouts hired", w.biz().scouts.isNotEmpty())
        val plans = DataPlan.values()
        repeat(3) { season ->
            for (wk in 1..34) {
                if (wk % 9 == 1) Analytics.setPlan(w, plans[(season + wk / 9) % plans.size])
                EcoTestSupport.play(w, 1)
                EcoTestSupport.richHuman(w)
            }
            check(w, "season ${season + 1}")
        }
        assertTrue("scouting produced reports", w.biz().scouts.sumOf { it.reportsMade } > 0 || w.scouted.isNotEmpty())
    }

    @Test fun planAndScoutingStateSurviveSaveAndLoad() {
        val w = EcoTestSupport.world(72L)
        hireScouts(w)
        Analytics.setPlan(w, DataPlan.ELITE)
        EcoTestSupport.play(w, 15)
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, w))
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        assertEquals(DataPlan.ELITE, Analytics.planOf(loaded, loaded.human()))
        assertEquals(Analytics.humanLevel(w), Analytics.humanLevel(loaded), 0f)
        assertEquals(w.biz().reports.size, loaded.biz().reports.size)
        assertEquals(w.biz().knowledge, loaded.biz().knowledge)
        val opp = Analytics.nextOpponent(w)!!
        val a = Analytics.oppositionReport(w, opp); val b = Analytics.oppositionReport(loaded, loaded.clubs[opp.id])
        assertEquals(listOf(a.formation, a.strongestGroup, a.weakestGroup, a.keyPlayer, a.keyPlayerOvr, a.advice), listOf(b.formation, b.strongestGroup, b.weakestGroup, b.keyPlayer, b.keyPlayerOvr, b.advice))
        assertEquals(Analytics.dataTargets(w).map { it.player.id }, Analytics.dataTargets(loaded).map { it.player.id })
        check(loaded, "loaded"); EcoTestSupport.play(loaded, 10); check(loaded, "loaded+10")
    }

    @Test fun formatSixSaveFromM2LoadsAndGainsTheAnalyticsSection() {
        val dir = temp.newFolder()
        File(dir, "career1.dat").writeBytes(javaClass.classLoader!!.getResourceAsStream("saves/career_v6_m2.dat")!!.use { it.readBytes() })
        val w = (Persistence.load(dir) as LoadResult.Loaded).world
        assertEquals(Persistence.CURRENT_SAVE_VERSION, w.saveVersion)
        assertNotNull(w.ecosystem!!.staff); assertNotNull(w.ecosystem!!.board); assertNotNull(w.ecosystem!!.identity)
        assertNull("analytics section is created lazily", w.ecosystem!!.analytics)
        assertEquals("a club with no plan chosen keeps its tier's normal plan", Analytics.defaultPlan(w.human().tier), Analytics.planOf(w, w.human()))
        assertEquals(0L, Analytics.netWeeklyCost(w, w.human()))
        val startYear = w.year; var guard = 0
        while (w.year == startYear && guard++ < 60) EcoTestSupport.play(w, 1)
        assertTrue(w.year > startYear)
        check(w, "after migrated season")
    }

    @Test fun invalidPlanIndexIsRepairedOnLoad() {
        val w = EcoTestSupport.world(73L)
        Analytics.state(w).plan = 42
        Analytics.repair(w)
        assertEquals(-1, Analytics.state(w).plan)
        Analytics.state(w).plan = -9; Analytics.repair(w); assertEquals(-1, Analytics.state(w).plan)
        val old = EcoTestSupport.world(74L); Analytics.repair(old); assertNull(old.ecosystem?.analytics)
    }

    @Test fun modelApprovalFollowsTheModelRatingAndSharpensWithBetterData() {
        val w = EcoTestSupport.world(75L); val me = w.human()
        val mine = Lineups.strength(w, me)
        val star = w.players.values.filter { it.clubId != me.id }.maxByOrNull { it.ovr }!!
        val weak = w.players.values.filter { it.clubId != me.id }.minByOrNull { it.ovr }!!
        assertEquals(1, Analytics.modelApproval(w, star)); assertEquals(-1, Analytics.modelApproval(w, weak))
        assertTrue(weak.ovr < mine - 6 && star.ovr > mine)
        // The rating is stable within a season and its error band never exceeds the documented size.
        val m = Analytics.modelRating(w, star); assertEquals(m, Analytics.modelRating(w, star))
        val err = Math.round((100f - Analytics.humanLevel(w)) / 20f)
        assertTrue(Math.abs(m - star.ovr) <= err)
    }

    @Test fun targetsAreEmptyWhenNothingFitsAndNeverIncludeOwnPlayers() {
        val w = EcoTestSupport.world(76L)
        val t = Analytics.dataTargets(w, 3)
        assertTrue(t.size <= 3)
        assertTrue(t.none { it.player.clubId == w.humanClubId })
        assertTrue(Analytics.dataTargets(w, 0).isEmpty())
    }

    @Test fun safeModeKeepsAnalyticsStateUntouchedAndFree() {
        val w = EcoTestSupport.world(77L)
        Ecosystem.enabled = false
        try { EcoTestSupport.play(w, 8); assertEquals(50f, Analytics.humanLevel(w), 0f); assertEquals(0L, Analytics.netWeeklyCost(w, w.human())) } finally { Ecosystem.enabled = true }
        assertNull(w.ecosystem?.analytics)
    }

    @Test fun datasetPlanCostIsBookedThroughTheWeeklyBudget() {
        val w = EcoTestSupport.world(78L); val me = w.human()
        EcoTestSupport.richHuman(w)
        val cheap = Analytics.setPlan(w, DataPlan.NONE)
        if (Analytics.defaultPlan(me.tier) != DataPlan.NONE) assertTrue(cheap.first)
        val saving = Analytics.netWeeklyCost(w, me)
        assertTrue("saving $saving", saving <= 0L)
        val dear = Analytics.setPlan(w, DataPlan.ELITE)
        assertTrue(dear.first)
        assertTrue(Analytics.netWeeklyCost(w, me) > 0L)
        assertFalse("same plan twice is refused", Analytics.setPlan(w, DataPlan.ELITE).first)
    }
}
