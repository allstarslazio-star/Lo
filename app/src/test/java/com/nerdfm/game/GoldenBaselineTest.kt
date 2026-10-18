package com.nerdfm.game

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0 regression gates for the Club Ecosystem layer.
 *
 * 1. The match engine is frozen: SHA-256 of its sources, plus an engine-isolation campaign whose numbers may not move.
 * 2. League-wide averages through the REAL game path (WorldBaselineHarness, seeds 1..6, ~12.9k fixtures) must stay in
 *    bands around the values recorded before any ecosystem modifier existed. Bands are wide enough for legitimate
 *    modifier effects and narrow enough to catch a calibration break.
 * 3. Even with every club at the maximum allowed boost (or penalty), averages stay inside those bands.
 *
 * To re-baseline deliberately (e.g. an approved engine change), update the constants below and say why in the commit.
 */
class GoldenBaselineTest {
    // ---- 1. frozen engine ------------------------------------------------------------------------------------
    private val frozenHashes = mapOf(
        "MatchEngine.kt" to "c96224d9e987df31683cdb507b7d87c25524fac9943d6867c0b46f1ebbb72b8f",
        "MatchConditions.kt" to "638114f4e20026d8148ca581f16285b6d07fda476e8df2a8915f6e3f0dbd4799"
    )

    @Test fun matchEngineSourcesAreFrozen() {
        for ((name, expected) in frozenHashes) {
            val file = File("src/main/java/com/nerdfm/game/$name")
            assertTrue("${file.absolutePath} not found: unit tests must run with the app module as working directory", file.isFile)
            val text = file.readText().replace("\r\n", "\n")
            val actual = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
            assertEquals("$name changed; the match engine is frozen (see ecosystem brief)", expected, actual)
        }
    }

    @Test fun engineIsolationCampaignKeepsItsGoldenNumbers() {
        val r = MatchCalibrationHarness.run() // 5,000 equal-strength matches, seed 20261016, no ecosystem involved
        assertEquals(2.56, r.goals.mean, 0.02)
        assertEquals(88.85, r.totalShots.mean, 0.4)
        assertEquals(8.91, r.totalXg.mean, 0.1)
        assertEquals(0.452, r.homeWinRate, 0.006)
        assertEquals(0.245, r.drawRate, 0.006)
        assertEquals(0.303, r.awayWinRate, 0.006)
        assertEquals(6.22, r.totalYellowCards.mean, 0.1)
        assertEquals(0.683, r.totalRedCards.mean, 0.03)
        assertEquals(0, r.errorCount)
    }

    // ---- 2. real game path ----------------------------------------------------------------------------------
    private object Golden { // measured on the pre-ecosystem code, seeds 1..6
        const val GOALS = 2.208; const val GOALS_TOL = 0.06
        const val HOME = 0.433; const val DRAW = 0.279; const val AWAY = 0.288; const val RATE_TOL = 0.015
        const val SHOTS = 77.31; const val SHOTS_TOL = 1.5
        const val XG = 7.80; const val XG_TOL = 0.15
        const val INJ = 25.02; const val INJ_TOL = 2.5
        const val MORALE = 75.37; const val MORALE_TOL = 1.0
        const val COND = 90.39; const val COND_TOL = 1.0
    }

    /** Extreme all-clubs scenarios may drift a bit further than normal play (bands x1.5). */
    private val EXTREME_SCALE = 1.5

    private fun assertInsideGoldenBands(label: String, a: WorldBaselineHarness.Aggregate, scale: Double = 1.0) {
        fun near(name: String, expected: Double, tol: Double, actual: Double) =
            assertTrue("$label: $name=%.3f outside %.3f +/- %.3f\n${a.text()}".format(actual, expected, tol), Math.abs(actual - expected) <= tol * scale)
        near("goals/match", Golden.GOALS, Golden.GOALS_TOL, a.goalsPerMatch)
        near("home win rate", Golden.HOME, Golden.RATE_TOL, a.homeWinRate)
        near("draw rate", Golden.DRAW, Golden.RATE_TOL, a.drawRate)
        near("away win rate", Golden.AWAY, Golden.RATE_TOL, a.awayWinRate)
        near("shots/match", Golden.SHOTS, Golden.SHOTS_TOL, a.shotsPerMatch)
        near("xG/match", Golden.XG, Golden.XG_TOL, a.xgPerMatch)
        near("injured per 1000 player-weeks", Golden.INJ, Golden.INJ_TOL, a.injuredPer1000PlayerWeeks)
        near("mean morale", Golden.MORALE, Golden.MORALE_TOL, a.meanMorale)
        near("mean condition", Golden.COND, Golden.COND_TOL, a.meanCondition)
    }

    /** Safe mode (Ecosystem.enabled = false) must be the original game, bit for bit: digest recorded before any ecosystem code existed. */
    @Test fun safeModeReproducesTheOriginalGameExactly() {
        Ecosystem.enabled = false
        val digest = try { WorldBaselineHarness.run(1L..6L).digest } finally { Ecosystem.enabled = true }
        assertEquals("safe mode diverged from the pre-ecosystem game (digest recorded on JDK 21, x86-64)", 4500399442611641785L, digest)
    }

    @Test fun realGamePathAveragesMatchTheGoldenBands() {
        val a = WorldBaselineHarness.run(1L..6L)
        assertTrue("fixtures=${a.fixtures}", a.fixtures > 12_000)
        assertInsideGoldenBands("no modifiers", a)
    }

    private fun maxed(sign: Float) = FactorProvider { _, _, _ -> listOf(
        Factor("a", Channel.CONDITION, 99f * sign), Factor("b", Channel.CONDITION, 99f * sign),
        Factor("c", Channel.SHARPNESS, 99f * sign), Factor("d", Channel.SHARPNESS, 99f * sign),
        Factor("e", Channel.MORALE, 99f * sign), Factor("f", Channel.MORALE, 99f * sign),
        Factor("g", Channel.RECOVERY, 99f * sign), Factor("h", Channel.RECOVERY, 99f * sign)) }

    @Test fun everyClubAtMaximumBoostOrPenaltyStaysInsideTheGoldenBands() {
        for (sign in floatArrayOf(1f, -1f)) {
            val a = Ecosystem.withProviders(listOf(maxed(sign))) { WorldBaselineHarness.run(1L..6L) }
            assertInsideGoldenBands(if (sign > 0) "all clubs max boost" else "all clubs max penalty", a, EXTREME_SCALE)
        }
    }

    /** One-sided worst case: half the clubs at maximum boost, the other half at maximum penalty. */
    @Test fun aMaximallyBoostedClubGainsAtMostAQuarterPointPerGameOverAMaximallyPenalisedOne() {
        val split = FactorProvider { w, c, ctx -> maxed(if (c.id % 2 == 0) 1f else -1f).factors(w, c, ctx) }
        var even = 0.0; var odd = 0.0; var evenN = 0; var oddN = 0
        Ecosystem.withProviders(listOf(split)) {
            for (seed in 1L..4L) {
                val w = WorldGen.generate(seed, "Harness", 0)
                for (wk in 1..34) {
                    w.week = wk
                    val fx = Game.startWeek(w)
                    if (fx != null) { val e = Game.humanEngine(w, fx); e.simToEnd(); Game.finishHuman(w, fx, e) }
                    if (wk == 34) for (lg in w.leagues) for (f in lg.fixtures) if (f.played) {
                        val hp = if (f.hg > f.ag) 3 else if (f.hg == f.ag) 1 else 0
                        val ap = if (f.ag > f.hg) 3 else if (f.hg == f.ag) 1 else 0
                        if (f.home % 2 == 0) { even += hp; evenN++ } else { odd += hp; oddN++ }
                        if (f.away % 2 == 0) { even += ap; evenN++ } else { odd += ap; oddN++ }
                    }
                    Game.completeWeek(w); w.sacked = false
                }
            }
        }
        val diff = even / evenN - odd / oddN
        assertTrue("modifiers must have a real effect, diff=$diff", diff > 0.05)
        assertTrue("combined modifiers swing too far: diff=$diff points per game", diff < 0.25)
    }
}
