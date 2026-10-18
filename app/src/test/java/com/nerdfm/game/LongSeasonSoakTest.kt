package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Default runs stay short enough for every pull request. The release soak is the same test with more work:
 *   NERDFM_SOAK_SEASONS=30 NERDFM_SOAK_CAREERS=3 gradle testDebugUnitTest --tests '*LongSeasonSoakTest*'
 * (the "Soak" CI workflow does exactly that). Nothing here claims a 30-season pass unless that run is green.
 */
class LongSeasonSoakTest {
    @get:Rule val temp = TemporaryFolder()

    @Before fun resetDiagnostics() {
        PerfMonitor.reset()
        Diagnostics.clear()
    }

    private fun env(name: String, default: Int): Int = System.getenv(name)?.toIntOrNull()?.coerceAtLeast(1) ?: default

    @Test fun headlessCareersCompleteWithoutExceptionsOrInvalidState() {
        val seasons = env("NERDFM_SOAK_SEASONS", 2)
        val careers = env("NERDFM_SOAK_CAREERS", 1)
        for (i in 0 until careers) {
            val report = HeadlessCareerRunner.run(HeadlessCareerRunner.Config(seed = 1_000L + i * 17L, seasons = seasons), temp.newFolder("career-$i"))
            println(report.text())
            assertTrue(report.text(), report.ok())
            assertEquals("every requested season must complete", seasons, report.seasons)
            assertTrue("a season has 35 weekly ticks", report.weeks >= seasons * 35)
        }
    }

    @Test fun weeklyAdvanceStaysWithinTheDesktopSanityBound() {
        val report = HeadlessCareerRunner.run(HeadlessCareerRunner.Config(seed = 2_024L, seasons = 1, maxWeeks = 6, roundTripEverySeason = false), temp.newFolder())
        assertTrue(report.text(), report.ok())
        val stats = PerfMonitor.stats(PerfMonitor.WEEK)!!
        println(stats.line())
        // The 2 s budget is a DEVICE gate. On a desktop JVM only a 10x-looser bound is asserted, to catch regressions.
        assertTrue(stats.line(), stats.p95Ms <= PerfBudget.WEEK_ADVANCE_MS * PerfBudget.DESKTOP_SANITY_FACTOR)
        assertEquals(0, ThreadGuard.violations)
    }

    @Test fun sameSeedProducesTheSameCareerAndDifferentSeedsDiverge() {
        fun fingerprint(seed: Long): Long =
            HeadlessCareerRunner.run(HeadlessCareerRunner.Config(seed = seed, seasons = 1, maxWeeks = 8, roundTripEverySeason = false), temp.newFolder()).finalFingerprint
        val a = fingerprint(31L)
        val b = fingerprint(31L)
        val c = fingerprint(32L)
        assertEquals("the determinism contract: same seed, same inputs, same state", a, b)
        assertNotEquals(a, c)
    }

    @Test fun aSavedAndReloadedCareerKeepsItsFingerprint() {
        val w = WorldGen.generate(77L, "Round", 0)
        for (i in 0 until 3) { Game.startWeek(w)?.let { f -> val e = Game.humanEngine(w, f); e.simToEnd(); Game.finishHuman(w, f, e) }; Game.completeWeek(w) }
        val before = StateFingerprint.of(w)
        val dir = temp.newFolder()
        assertTrue(SaveCoordinator.save(dir, w).ok)
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        assertEquals(before, StateFingerprint.of(loaded))
        assertEquals("the audit must be read-only", before, StateFingerprint.of(w))
    }
}
