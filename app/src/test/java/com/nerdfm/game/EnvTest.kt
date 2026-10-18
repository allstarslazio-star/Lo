package com.nerdfm.game

import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** M5 (fans, stadium, pitch, facilities, media), save format 9. */
class EnvTest {
    @get:Rule val temp = TemporaryFolder()

    private fun check(w: World, label: String) {
        assertEquals(label, emptyList<WorldIntegrity.Issue>(), WorldIntegrity.inspect(w))
        val s = ClubEnvRules.state(w)
        for ((id, e) in s.clubs) {
            assertTrue("$label club $id", id in w.clubs.indices)
            assertTrue("$label mood ${e.mood}", e.mood in 0f..100f)
            assertTrue("$label pitch ${e.pitch}", e.pitch in 0f..100f)
            assertTrue("$label stadium ${e.stadium}", e.stadium in 0f..100f)
            val c = w.clubs[id]
            val att = ClubEnvRules.attendance(w, c)
            assertTrue("$label attendance $att vs ${c.capacity()}", att in 0..c.capacity())
        }
        assertTrue(s.training in 0..3 && s.medical in 0..3 && s.academy in 0..3 && s.data in 0..3)
    }

    @Test fun attendanceAndConditionsStayInRangeOverThreeSeasons() {
        val w = EcoTestSupport.world(91L)
        repeat(3) { season ->
            for (wk in 1..34) EcoTestSupport.play(w, 1)
            check(w, "season ${season + 1}")
        }
    }

    @Test fun pitchQualityMovesSlowly() {
        val w = EcoTestSupport.world(92L, 1)
        val s = ClubEnvRules.state(w)
        var prev = w.clubs.associate { it.id to s.clubs.getValue(it.id).pitch }
        repeat(20) {
            EcoTestSupport.play(w, 1)
            for (c in w.clubs) {
                val now = s.clubs.getValue(c.id).pitch
                assertTrue("pitch jumped ${prev.getValue(c.id)} -> $now", Math.abs(now - prev.getValue(c.id)) <= 3f)
            }
            prev = w.clubs.associate { it.id to s.clubs.getValue(it.id).pitch }
        }
    }

    @Test fun resultsMoveFanMood() {
        val w = EcoTestSupport.world(93L, 2)
        val me = w.human()
        val e = ClubEnvRules.state(w).clubs.getValue(me.id)
        e.mood = 50f; me.lastPts = 3
        ClubEnvRules.weekly(w)
        assertTrue("win should lift mood: ${e.mood}", e.mood > 50f)
        e.mood = 50f; me.lastPts = 0
        ClubEnvRules.weekly(w)
        assertTrue("defeat should lower mood: ${e.mood}", e.mood < 50f)
    }

    @Test fun facilitySpendIsBookedOnceAndTheLevelRisesWhenTheWorkIsDone() {
        val w = EcoTestSupport.world(94L, 2)
        EcoTestSupport.richHuman(w)
        val me = w.human()
        val s = ClubEnvRules.state(w)
        val cost = ClubEnvRules.upgradeCost(w, "TRAINING")
        val before = me.balance
        val (ok, _) = ClubEnvRules.startProject(w, "TRAINING")
        assertTrue(ok)
        assertEquals(before - cost, me.balance)
        assertEquals(cost, s.seasonFacilitySpend)
        assertEquals("Already under way", ClubEnvRules.blockedReason(w, "TRAINING"))
        assertFalse(ClubEnvRules.startProject(w, "TRAINING").first)
        assertEquals(cost, s.seasonFacilitySpend)
        EcoTestSupport.play(w, 7)
        assertEquals(1, s.training)
        assertTrue(s.projects.none { it.kind == "TRAINING" })
        assertEquals(cost, s.seasonFacilitySpend)
        assertEquals((me.revenue / 25L) * 2, ClubEnvRules.upgradeCost(w, "TRAINING"))
        assertEquals(1.02f, ClubEnvRules.trainingBoost(w, me.id), 1e-4f)
    }

    @Test fun unaffordableAndFinishedUpgradesAreBlockedWithAReason() {
        val w = EcoTestSupport.world(95L, 2)
        val me = w.human()
        me.balance = 0L
        val reason = ClubEnvRules.blockedReason(w, "MEDICAL")
        assertNotNull(reason); assertTrue(reason!!.startsWith("Needs"))
        assertFalse(ClubEnvRules.startProject(w, "MEDICAL").first)
        assertEquals(0L, me.balance)
        EcoTestSupport.richHuman(w)
        ClubEnvRules.state(w).academy = 3
        assertEquals("Fully upgraded", ClubEnvRules.blockedReason(w, "ACADEMY"))
    }

    @Test fun crowdAndPitchActOnTheHomeSideOnlyAndStayBounded() {
        val w = EcoTestSupport.world(96L, 2)
        val me = w.human()
        val opp = w.clubs[(me.id + 1) % w.clubs.size]
        val e = ClubEnvRules.state(w).clubs.getValue(me.id)
        e.mood = 0f; e.pitch = 0f
        val home = EnvFactors.factors(w, me, MatchContext(true, opp))
        assertEquals(-1f, home.first { it.channel == Channel.MORALE }.value, 1e-4f)
        assertTrue(home.first { it.channel == Channel.SHARPNESS }.value < 0f)
        val away = EnvFactors.factors(w, me, MatchContext(false, opp))
        assertTrue("no crowd or pitch effect away from home", away.none { it.channel != Channel.RECOVERY })
        val weekly = EnvFactors.factors(w, me, MatchContext.WEEKLY)
        assertTrue(weekly.all { it.channel == Channel.RECOVERY })
        val m = Ecosystem.modifiersFor(w, me, MatchContext(true, opp))
        assertTrue(m.morale >= -ModifierCaps.MORALE_TOTAL && m.sharpness >= -ModifierCaps.SHARPNESS_TOTAL && m.condition >= -ModifierCaps.CONDITION_TOTAL)
        assertTrue(m.recovery >= -ModifierCaps.RECOVERY_TOTAL)
        assertTrue("worst-case swing ${ClubModifiers.worstCaseSwing()}", ClubModifiers.worstCaseSwing() <= ModifierCaps.MAX_EFFECTIVENESS_SWING)
    }

    @Test fun aPressConferenceIsAnsweredOnceAndEffectsAreBounded() {
        val w = EcoTestSupport.world(97L, 2)
        val me = w.human()
        val q1 = Media.question(w, 0)
        assertNotNull(q1)
        assertEquals(q1!!.topic, Media.question(w, 0)!!.topic)
        val boardBefore = me.boardConfidence
        assertTrue(Media.answer(w, 0, PressStyle.CALM).first)
        assertNull(Media.question(w, 0))
        assertFalse(Media.answer(w, 0, PressStyle.CONFIDENT).first)
        assertTrue(Math.abs(me.boardConfidence - boardBefore) <= 2f)
        assertTrue(Media.pressure(me) in 0.6f..1.4f)
    }

    @Test fun supporterDecisionsHaveCooldownsAndTheFreezeEndsWithTheSeason() {
        val w = EcoTestSupport.world(98L, 3)
        assertTrue(ClubEnvRules.holdForum(w).first)
        assertNotNull(ClubEnvRules.forumBlockedReason(w))
        assertFalse(ClubEnvRules.holdForum(w).first)
        assertTrue(ClubEnvRules.freezePrices(w).first)
        assertTrue(ClubEnvRules.priceFrozen(w))
        assertFalse(ClubEnvRules.freezePrices(w).first)
        w.year++
        ClubEnvRules.seasonReview(w)
        assertFalse(ClubEnvRules.priceFrozen(w))
    }

    /** Hostile crowds and wrecked pitches everywhere, at the largest sizes the factors can reach, must not break the league. */
    @Test fun extremeCrowdsAndPitchesKeepLeagueAveragesNearTheBaseline() {
        val base = WorldBaselineHarness.run(1L..2L)
        val worst = FactorProvider { _, _, ctx ->
            if (ctx.opponent != null && ctx.home) listOf(
                Factor("c", Channel.MORALE, -1f), Factor("s", Channel.SHARPNESS, -1.8f), Factor("k", Channel.CONDITION, -1f), Factor("r", Channel.RECOVERY, -0.03f))
            else listOf(Factor("r", Channel.RECOVERY, -0.03f))
        }
        val extreme = Ecosystem.withProviders(listOf(worst)) { WorldBaselineHarness.run(1L..2L) }
        assertEquals(base.goalsPerMatch, extreme.goalsPerMatch, 0.09)
        assertEquals(base.homeWinRate, extreme.homeWinRate, 0.03)
        assertEquals(base.injuredPer1000PlayerWeeks, extreme.injuredPer1000PlayerWeeks, 4.0)
    }

    @Test fun formatEightSaveMigratesToFormatNineAndPlaysASeason() {
        val dir = temp.newFolder()
        val old = EcoTestSupport.world(99L, 5)
        EcoTestSupport.play(old, 3)
        old.ecosystem!!.env = null; old.ecosystem!!.calendar = null
        old.saveVersion = 8
        ObjectOutputStream(GZIPOutputStream(FileOutputStream(File(dir, "career1.dat")))).use { it.writeObject(old) }
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        assertEquals(Persistence.CURRENT_SAVE_VERSION, loaded.saveVersion)
        assertTrue(Persistence.CURRENT_SAVE_VERSION >= 9)
        assertNull("env section is created lazily", loaded.ecosystem?.env)
        val startYear = loaded.year
        var guard = 0
        while (loaded.year == startYear && guard++ < 60) EcoTestSupport.play(loaded, 1)
        assertTrue(loaded.year > startYear)
        check(loaded, "after migrated season")
    }

    @Test fun envSectionRoundTrips() {
        val w = EcoTestSupport.world(100L, 1)
        EcoTestSupport.richHuman(w)
        EcoTestSupport.play(w, 4)
        ClubEnvRules.startProject(w, "PITCH")
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, w))
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        val a = ClubEnvRules.state(w); val b = ClubEnvRules.state(loaded)
        assertEquals(a.projects.map { it.kind to it.weeksLeft }, b.projects.map { it.kind to it.weeksLeft })
        for ((id, e) in a.clubs) { val o = b.clubs.getValue(id); assertEquals(e.mood, o.mood, 0f); assertEquals(e.pitch, o.pitch, 0f) }
        check(loaded, "loaded")
    }
}
