package com.nerdfm.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsAndPerfTest {
    @get:Rule val temp = TemporaryFolder()

    @Before fun reset() {
        PerfMonitor.reset()
        ThreadGuard.reset()
        ThreadGuard.isMainThread = { false }
        Diagnostics.clear()
    }

    // ------------------------------------------------------------- reproducible bug reports

    @Test fun reportCarriesSeedVersionsDateOperationAndIntegrityFindings() {
        val w = WorldGen.generate(4242L, "Private Person", 0)
        w.clubs[2].facilities = 40
        val report = Diagnostics.capture(w, "advance-week", IllegalStateException("boom"), "TestPhone/34")
        assertEquals(4242L, report.seed)
        assertEquals(Diagnostics.GAME_VERSION, report.gameVersion)
        assertEquals(w.saveVersion, report.saveVersion)
        assertEquals(w.seasonLabel(), report.season)
        assertEquals(w.week, report.week)
        assertEquals("advance-week", report.operation)
        assertTrue(report.integritySummary, report.integritySummary.contains("club.rating"))
        val text = Diagnostics.render(report)
        for (needle in listOf("seed: 4242", "game-version:", "save-version:", "season:", "week:", "operation: advance-week", "IllegalStateException", "boom", "TestPhone/34")) {
            assertTrue("missing '$needle' in:\n$text", text.contains(needle))
        }
    }

    @Test fun reportsDoNotExposePersonalInformation() {
        val w = WorldGen.generate(11L, "Jane Q. Manager", 0)
        Diagnostics.note("load", "reading /data/user/0/com.nerdfm.game/files/career1.dat for jane@example.com")
        val error = java.io.FileNotFoundException("/storage/emulated/0/Android/data/com.nerdfm.game/files/career1.dat (Permission denied)")
        val text = Diagnostics.render(Diagnostics.capture(w, "load", error))
        assertFalse(text, text.contains("Jane"))
        assertFalse(text, text.contains("jane@example.com"))
        assertFalse(text, text.contains("/data/user/0"))
        assertFalse(text, text.contains("/storage/emulated"))
        assertTrue(text, text.contains("<path>"))
        assertFalse("club names are not needed to reproduce a failure", text.contains(w.human().name))
    }

    @Test fun scrubKeepsSeasonLabelsAndOrdinaryText() {
        assertEquals("Season 2026/27 week 5", Diagnostics.scrub("Season 2026/27 week 5"))
        assertEquals("opened <path> now", Diagnostics.scrub("opened /a/b/c.txt now"))
        assertEquals("send to <email>", Diagnostics.scrub("send to who@where.org"))
    }

    @Test fun captureNeverThrowsEvenForADamagedWorld() {
        val broken = World()
        broken.humanClubId = 99
        val report = Diagnostics.capture(broken, "load")
        assertNotNull(report.integritySummary)
        val none = Diagnostics.capture(null, "startup")
        assertEquals("no world loaded", none.integritySummary)
        assertNull(none.seed)
        assertTrue(Diagnostics.render(none).contains("seed: -"))
    }

    @Test fun recentOperationTrailIsBounded() {
        for (i in 0 until Diagnostics.MAX_RECENT + 15) Diagnostics.note("op$i")
        val recent = Diagnostics.recentOperations()
        assertEquals(Diagnostics.MAX_RECENT, recent.size)
        assertEquals("op15", recent.first())
        assertEquals("op${Diagnostics.MAX_RECENT + 14}", recent.last())
    }

    @Test fun storeKeepsOnlyTheNewestReports() {
        val store = DiagnosticsStore(File(temp.root, "diag"), keep = 3)
        for (i in 1..7) assertNotNull(store.write(Diagnostics.capture(null, "op$i")))
        val files = store.list()
        assertEquals(3, files.size)
        assertEquals(listOf("failure-000005.txt", "failure-000006.txt", "failure-000007.txt"), files.map { it.name })
        assertTrue(files.last().readText().contains("operation: op7"))
    }

    @Test fun gameVersionMatchesTheGradleVersionName() {
        val gradle = File("build.gradle.kts")
        if (!gradle.isFile) return // only meaningful when run from the module directory, which Gradle does
        val name = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(gradle.readText())?.groupValues?.get(1)
        assertEquals("update Diagnostics.GAME_VERSION when versionName changes", name, Diagnostics.GAME_VERSION)
    }

    // ------------------------------------------------------------- state fingerprint

    @Test fun fingerprintIsStableAndSensitiveToState() {
        val a = WorldGen.generate(8L, "A", 0)
        val b = WorldGen.generate(8L, "B", 0) // manager name is not game state
        assertEquals(StateFingerprint.of(a), StateFingerprint.of(b))
        val before = StateFingerprint.of(a)
        a.clubs[0].balance += 1L
        assertNotEquals(before, StateFingerprint.of(a))
        a.clubs[0].balance -= 1L
        assertEquals(before, StateFingerprint.of(a))
        a.players.values.first().attrs[0] = (a.players.values.first().attrs[0] % 20) + 1
        assertNotEquals(before, StateFingerprint.of(a))
    }

    // ------------------------------------------------------------- performance gates

    @Test fun budgetsMatchTheReleasePlan() {
        assertEquals(2_000L, PerfBudget.budgetFor(PerfMonitor.WEEK))
        assertEquals(1_000L, PerfBudget.budgetFor(PerfMonitor.SAVE))
        assertNull("match simulation and loading are measured and reported only", PerfBudget.budgetFor(PerfMonitor.MATCH))
        assertNull(PerfBudget.budgetFor(PerfMonitor.LOAD))
    }

    @Test fun statsUseTheNinetyFifthPercentileAgainstTheBudget() {
        for (ms in listOf(100L, 120L, 110L, 90L, 105L, 115L, 95L, 100L, 100L, 100L)) PerfMonitor.record(PerfMonitor.WEEK, ms)
        val ok = PerfMonitor.stats(PerfMonitor.WEEK)!!
        assertEquals(10, ok.count)
        assertEquals(120L, ok.maxMs)
        assertTrue(ok.withinBudget)
        for (i in 0 until 40) PerfMonitor.record(PerfMonitor.WEEK, 2_500L)
        val slow = PerfMonitor.stats(PerfMonitor.WEEK)!!
        assertFalse(slow.line(), slow.withinBudget)
        assertTrue(slow.line().contains("OVER BUDGET"))
    }

    @Test fun unbudgetedOperationsAreReportedNotFailed() {
        PerfMonitor.record(PerfMonitor.MATCH, 9_999L)
        val s = PerfMonitor.stats(PerfMonitor.MATCH)!!
        assertTrue(s.withinBudget)
        assertTrue(s.line().contains("reported"))
    }

    @Test fun timeRecordsEvenWhenTheBlockThrows() {
        try {
            PerfMonitor.time(PerfMonitor.SAVE) { throw IllegalStateException("disk full") }
            org.junit.Assert.fail("expected exception")
        } catch (e: IllegalStateException) {
            assertEquals(1, PerfMonitor.stats(PerfMonitor.SAVE)!!.count)
        }
        assertEquals(42, PerfMonitor.time(PerfMonitor.AUDIT) { 42 })
    }

    @Test fun sampleWindowIsBoundedButTheCountIsNot() {
        for (i in 1..500) PerfMonitor.record(PerfMonitor.WEEK, i.toLong())
        val s = PerfMonitor.stats(PerfMonitor.WEEK)!!
        assertEquals(500, s.count)
        assertEquals(500L, s.maxMs)
        assertTrue("old samples must age out of the percentile window", s.p95Ms > 300L)
    }

    @Test fun mainThreadCallsAreCountedAndNeverThrow() {
        ThreadGuard.isMainThread = { true }
        PerfMonitor.time(PerfMonitor.WEEK) { }
        PerfMonitor.time(PerfMonitor.SAVE) { }
        assertEquals(2, ThreadGuard.violations)
        assertEquals(PerfMonitor.SAVE, ThreadGuard.lastViolation)
        assertTrue(PerfMonitor.report().contains("Main-thread violations: 2"))
        ThreadGuard.isMainThread = { false }
        PerfMonitor.time(PerfMonitor.WEEK) { }
        assertEquals(2, ThreadGuard.violations)
    }

    @Test fun saveCoordinatorRecordsATimingAndReturnsTheAudit() {
        val w = WorldGen.generate(6L, "Timing", 0)
        val outcome = SaveCoordinator.save(temp.newFolder(), w)
        assertTrue(outcome.ok)
        assertNotNull(outcome.audit)
        assertTrue(outcome.audit!!.ok)
        assertTrue(outcome.snapshotBytes > 1000)
        assertEquals(1, PerfMonitor.stats(PerfMonitor.SAVE)!!.count)
    }

    @Test fun aWorldThatFailsTheAuditIsStillSavedAndTheFindingIsRecorded() {
        val w = WorldGen.generate(6L, "Bad", 0)
        w.clubs[1].facilities = 99
        val dir = temp.newFolder()
        val outcome = SaveCoordinator.save(dir, w)
        assertTrue("progress must never be refused over an audit finding", outcome.ok)
        assertFalse(outcome.audit!!.ok)
        assertTrue(Diagnostics.recentOperations().any { it.contains("club.rating") })
        assertTrue(Persistence.load(dir) is LoadResult.Loaded)
    }

    @Test fun worldLockIsReentrantSoNestedSavesCannotDeadlock() {
        val w = WorldGen.generate(2L, "Lock", 0)
        val ok = SaveCoordinator.withWorld { SaveCoordinator.save(temp.newFolder(), w).ok }
        assertTrue(ok)
    }
}
