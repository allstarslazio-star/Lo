package com.nerdfm.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class EcosystemHookTest {
    @get:Rule val temp = TemporaryFolder()

    private fun boost(sign: Float) = FactorProvider { _, _, _ -> listOf(
        Factor("c", Channel.CONDITION, 9f * sign), Factor("s", Channel.SHARPNESS, 9f * sign),
        Factor("m", Channel.MORALE, 9f * sign), Factor("r", Channel.RECOVERY, 9f * sign)) }

    private fun teams(w: World): Pair<MatchTeam, MatchTeam> {
        val lg = w.leagues[0]
        val home = w.clubs[lg.clubIds[0]]
        val away = w.clubs[lg.clubIds[1]]
        val rnd = Random(5)
        return MatchTeams.build(w, home, away, true, rnd) to MatchTeams.build(w, away, home, false, rnd)
    }

    private fun snapshot(w: World) = w.players.values.associate { it.id to Triple(it.condition, it.sharpness, it.morale) }

    @Test fun theDefaultConfigurationIsDeterministic() {
        val first = WorldBaselineHarness.run(11L..12L).digest
        assertEquals("the harness itself must be deterministic", first, WorldBaselineHarness.run(11L..12L).digest)
    }

    @Test fun beginMatchAppliesBoundedDeltasAndEndMatchRestoresThemExactly() {
        val w = WorldGen.generate(3L, "Hook", 0)
        val (home, away) = teams(w)
        val before = snapshot(w)
        Ecosystem.withProviders(listOf(boost(1f))) {
            Ecosystem.beginMatch(w, home, away)
            var changed = 0
            for (ps in home.xi + home.bench + away.xi + away.bench) {
                val b = before.getValue(ps.p.id)
                assertTrue(ps.p.condition - b.first <= ModifierCaps.CONDITION_TOTAL + 1e-4f)
                assertTrue(ps.p.sharpness - b.second <= ModifierCaps.SHARPNESS_TOTAL + 1e-4f)
                assertTrue(ps.p.morale - b.third <= ModifierCaps.MORALE_TOTAL + 1e-4f)
                if (ps.p.morale != b.third) changed++
            }
            assertTrue("some players must have been modified", changed > 0)
            assertTrue(w.eco().applied.isNotEmpty())
        }
        Ecosystem.endMatch(w)
        for (p in w.players.values) {
            val b = before.getValue(p.id)
            assertEquals(b.first, p.condition, 1e-3f); assertEquals(b.second, p.sharpness, 1e-3f); assertEquals(b.third, p.morale, 1e-3f)
        }
        assertTrue(w.eco().applied.isEmpty())
    }

    @Test fun endMatchIsIdempotentAndSafeWithoutState() {
        val w = WorldGen.generate(4L, "Hook", 0)
        assertNull(w.ecosystem)
        Ecosystem.endMatch(w)
        assertNull("no state may be created when nothing was applied", w.ecosystem)
        val (home, away) = teams(w)
        Ecosystem.withProviders(listOf(boost(-1f))) { Ecosystem.beginMatch(w, home, away) }
        Ecosystem.endMatch(w)
        val once = snapshot(w)
        Ecosystem.endMatch(w)
        assertEquals(once, snapshot(w))
    }

    @Test fun anAbandonedMatchIsCleanedUpByTheNextBeginMatchAndByPreWeek() {
        val w = WorldGen.generate(5L, "Hook", 0)
        val before = snapshot(w)
        val (home, away) = teams(w)
        Ecosystem.withProviders(listOf(boost(1f))) { Ecosystem.beginMatch(w, home, away) }
        Ecosystem.preWeek(w)
        for (p in w.players.values) assertEquals(before.getValue(p.id).third, p.morale, 1e-3f)
        Ecosystem.withProviders(listOf(boost(1f))) {
            Ecosystem.beginMatch(w, home, away)
            val firstPass = snapshot(w)
            Ecosystem.beginMatch(w, home, away) // replay (e.g. the match screen reopened): must not stack
            for ((id, v) in firstPass) assertEquals("deltas must not stack for player $id", v.third, w.players.getValue(id).morale, 1e-3f)
        }
        Ecosystem.endMatch(w)
    }

    @Test fun aSaveWrittenMidMatchIsCleanedWhenLoaded() {
        val w = WorldGen.generate(6L, "Hook", 0)
        val before = snapshot(w)
        val (home, away) = teams(w)
        Ecosystem.withProviders(listOf(boost(1f))) { Ecosystem.beginMatch(w, home, away) }
        val dir: File = temp.newFolder("midmatch")
        assertTrue(Persistence.save(dir, w))
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        assertTrue(loaded.eco().applied.isEmpty())
        for (p in loaded.players.values) assertEquals(before.getValue(p.id).third, p.morale, 1e-3f)
    }

    @Test fun safeModeAppliesNothing() {
        val w = WorldGen.generate(7L, "Hook", 0)
        val before = snapshot(w)
        val (home, away) = teams(w)
        Ecosystem.enabled = false
        try { Ecosystem.withProviders(listOf(boost(1f))) { Ecosystem.beginMatch(w, home, away) } } finally { Ecosystem.enabled = true }
        assertEquals(before, snapshot(w))
        assertNull(w.ecosystem)
    }

    @Test fun weeklyRecoveryIsOneWithoutProvidersAndBoundedWithThem() {
        val w = WorldGen.generate(8L, "Hook", 0)
        Ecosystem.withProviders(emptyList()) { assertTrue(Ecosystem.weeklyRecovery(w).all { it == 1f }) }
        // one factor is clamped to its per-factor cap (0.04); a second one reaches the channel total (0.08)
        Ecosystem.withProviders(listOf(boost(1f))) { assertTrue(Ecosystem.weeklyRecovery(w).all { Math.abs(it - 1.04f) < 1e-6f }) }
        Ecosystem.withProviders(listOf(boost(-1f), boost(-1f))) { assertTrue(Ecosystem.weeklyRecovery(w).all { Math.abs(it - 0.92f) < 1e-6f }) }
        Ecosystem.withProviders(listOf(boost(1f), boost(1f), boost(1f))) { assertTrue(Ecosystem.weeklyRecovery(w).all { Math.abs(it - 1.08f) < 1e-6f }) }
    }

    @Test fun boostedRecoveryActuallyChangesWeeklyConditionGain() {
        fun conditionAfterWeek(sign: Float?): Float {
            val w = WorldGen.generate(9L, "Hook", 0)
            w.week = 1
            for (p in w.players.values) if (p.clubId >= 0) { p.condition = 50f; p.injWeeks = 0 }
            val run = { Game.startWeek(w); Game.completeWeek(w) }
            if (sign == null) run() else Ecosystem.withProviders(listOf(boost(sign))) { run() }
            return w.players.values.filter { it.clubId >= 0 }.map { it.condition }.average().toFloat()
        }
        val low = conditionAfterWeek(-1f); val none = conditionAfterWeek(null); val high = conditionAfterWeek(1f)
        assertTrue("low=$low none=$none high=$high", low < none && none < high)
    }

    @Test fun neverCreatesStateOrTouchesPlayersOutsideTheTwoSquads() {
        val w = WorldGen.generate(10L, "Hook", 0)
        val (home, away) = teams(w)
        val inMatch = (home.xi + home.bench + away.xi + away.bench).map { it.p.id }.toSet()
        val before = snapshot(w)
        Ecosystem.withProviders(listOf(boost(1f))) { Ecosystem.beginMatch(w, home, away) }
        for (p in w.players.values) if (p.id !in inMatch) assertEquals(before.getValue(p.id), Triple(p.condition, p.sharpness, p.morale))
        assertFalse(w.eco().applied.keys.any { it !in inMatch })
        Ecosystem.endMatch(w)
    }
}
