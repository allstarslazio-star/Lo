package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class SupportBundleTest {
    @get:Rule val temp = TemporaryFolder()

    // ---------------- incident log
    @Test fun logAppendsReadsAndClears() {
        val log = IncidentLog(File(temp.root, "d/incidents.log"), clock = { "T" })
        log.append("hang", "stuck")
        log.append("slow-ui", "late")
        assertEquals("[T] hang: stuck\n[T] slow-ui: late\n", log.read())
        log.clear(); assertEquals("", log.read())
    }

    @Test fun logStaysBoundedAndKeepsTheNewestLines() {
        val log = IncidentLog(File(temp.root, "i.log"), maxBytes = 2_000L, clock = { "T" })
        repeat(400) { log.append("n", "entry-$it") }
        val text = log.read()
        assertTrue("size ${text.length}", text.length < 4_000)
        assertTrue(text.contains("entry-399"))
        assertFalse(text.contains("entry-0\n"))
    }

    @Test fun logNeverThrowsOnAnUnwritablePath() {
        val blocker = temp.newFile("blocker")
        IncidentLog(File(blocker, "child/x.log")).append("a", "b")
    }

    // ---------------- watchdog
    private class Rig {
        val queue = ArrayList<Runnable>()
        val log = IncidentLog(File.createTempFile("wdog", ".log").also { it.deleteOnExit() }, clock = { "T" })
        val dog = HangWatchdog(log, { queue.add(it) }, { "  at Main.stuck" }, { "ctx" })
        fun runMain() { queue.toList().also { queue.clear() }.forEach { it.run() } }
    }

    @Test fun aResponsiveMainThreadProducesNoIncidents() {
        val r = Rig(); var t = 0L
        repeat(20) { r.dog.poll(t) { t }; r.runMain(); t += 500 }
        assertEquals("", r.log.read())
        assertEquals(0, r.dog.hangs)
    }

    @Test fun aStalledMainThreadIsReportedOnceWithItsStack() {
        val r = Rig(); var t = 0L
        r.dog.poll(t) { t }              // ping sent, never answered
        repeat(12) { t += 500; r.dog.poll(t) { t } }
        assertEquals(1, r.dog.hangs)
        val text = r.log.read()
        assertTrue(text, text.contains("hang:") && text.contains("Main.stuck") && text.contains("ctx"))
        assertEquals(1, text.lines().count { it.contains("] hang:") })
    }

    @Test fun afterAHangRecoversTheWatchdogKeepsWorking() {
        val r = Rig(); var t = 0L
        r.dog.poll(t) { t }
        t += 4_000; r.dog.poll(t) { t }
        t += 100; r.runMain()            // main thread finally answers
        r.dog.poll(t) { t }              // measures the old ping, sends a new one
        r.runMain(); t += 500
        r.dog.poll(t) { t }
        assertEquals(1, r.dog.hangs)
        t += 4_000; r.dog.poll(t) { t }  // second stall: sent at t-4000 unanswered
        r.dog.poll(t + 4_000) { t + 4_000 }
        assertEquals(2, r.dog.hangs)
    }

    @Test fun aLateButAnsweredPingIsASlowNoteAndIsRateLimited() {
        val r = Rig(); var t = 0L
        var notes = 0
        repeat(6) {
            r.dog.poll(t) { t }
            t += 900; r.runMain()        // answered 900 ms late
            r.dog.poll(t) { t }          // records the delay and sends the next ping
            r.runMain(); t += 600
        }
        notes = r.log.read().lines().count { it.contains("slow-ui") }
        assertTrue("notes $notes", notes in 1..2)
        assertEquals(notes, r.dog.slowNotes)
    }

    @Test fun slowNotesAreCapped() {
        val r = Rig(); var t = 0L
        repeat(200) {
            r.dog.poll(t) { t }; t += 1_000; r.runMain(); r.dog.poll(t) { t }; r.runMain(); t += 20_000
        }
        assertTrue(r.dog.slowNotes <= r.dog.maxSlowNotes)
    }

    @Test fun aRealWatchdogThreadDetectsARealStall() {
        val log = IncidentLog(File(temp.root, "real.log"), clock = { "T" })
        val queue = java.util.concurrent.LinkedBlockingQueue<Runnable>()
        val dog = HangWatchdog(log, { queue.add(it) }, { "  at stalled" }, hangMs = 300L, slowMs = 150L)
        dog.start(pollMs = 40L)
        Thread.sleep(900)                  // nobody drains the "main thread" queue
        dog.stop()
        assertTrue(log.read(), log.read().contains("hang:"))
    }

    // ---------------- session marker
    @Test fun markerReportsOnlyUncleanSessions() {
        val m = SessionMarker(File(temp.root, "s/marker"))
        assertNull(m.consumeUnclean())
        m.begin("screen=Transfers"); m.update("screen=Squad")
        assertEquals("screen=Squad", m.consumeUnclean())
        assertNull(m.consumeUnclean())
        m.begin("x"); m.endCleanly(); assertNull(m.consumeUnclean())
    }

    // ---------------- bundle
    @Test fun bundleHoldsAReadableReportAndOptionallyTheSave() {
        val fail = temp.newFile("failure-000001.txt").also { it.writeText("error: Boom\n  at X.y:1") }
        val save = temp.newFile("career1.dat").also { it.writeBytes(ByteArray(1000) { 7 }) }
        val out = File(temp.root, "reports")
        val zip = SupportBundle.build(out, "s1", "device: test", "week-advance: n=1", "[T] hang: stuck", listOf(fail), save)
        assertNotNull(zip)
        ZipFile(zip!!).use { z ->
            assertEquals(setOf("report.txt", "career1.dat"), z.entries().toList().map { it.name }.toSet())
            val text = z.getInputStream(z.getEntry("report.txt")).readBytes().toString(Charsets.UTF_8)
            for (needle in listOf("device: test", "week-advance", "hang: stuck", "error: Boom", "failure-000001.txt")) assertTrue(needle, text.contains(needle))
            assertEquals(1000, z.getInputStream(z.getEntry("career1.dat")).readBytes().size)
        }
        val without = SupportBundle.build(out, "s2", "h", "p", "", emptyList(), null)!!
        ZipFile(without).use { assertEquals(listOf("report.txt"), it.entries().toList().map { e -> e.name }) }
        assertEquals("only the newest report is kept", 1, out.listFiles()!!.size)
        assertTrue(ZipFile(without).use { z -> z.getInputStream(z.getEntry("report.txt")).readBytes().toString(Charsets.UTF_8).contains("none recorded") })
    }

    @Test fun anOversizedSaveIsLeftOutRatherThanFailingTheReport() {
        val save = temp.newFile("big.dat")
        java.io.RandomAccessFile(save, "rw").use { it.setLength(SupportBundle.MAX_SAVE_BYTES + 1) }
        val zip = SupportBundle.build(File(temp.root, "r"), "s", "h", "p", "", emptyList(), save)!!
        ZipFile(zip).use { assertEquals(listOf("report.txt"), it.entries().toList().map { e -> e.name }) }
    }

    @Test fun bundleReturnsNullInsteadOfThrowingWhenTheFolderIsBlocked() {
        val blocker = temp.newFile("blocked")
        assertNull(SupportBundle.build(File(blocker, "sub"), "s", "h", "p", "", emptyList(), null))
    }

    // ---------------- perf hook
    @Test fun anOverBudgetOperationNotifiesTheHookAndAFastOneDoesNot() {
        val seen = ArrayList<String>()
        PerfMonitor.onOverBudget = { op, ms, b -> seen.add("$op:$ms>$b") }
        try {
            PerfMonitor.time(PerfMonitor.WEEK) { }
            assertTrue(seen.isEmpty())
            PerfMonitor.time(PerfMonitor.WEEK) { Thread.sleep(PerfBudget.WEEK_ADVANCE_MS + 60) }
            assertEquals(1, seen.size)
            PerfMonitor.time(PerfMonitor.MATCH) { Thread.sleep(5) }   // no budget -> never reported
            assertEquals(1, seen.size)
        } finally { PerfMonitor.onOverBudget = null }
    }

    @Test fun aThrowingHookCannotBreakTheMeasuredOperation() {
        PerfMonitor.onOverBudget = { _, _, _ -> throw IllegalStateException("hook") }
        try { assertEquals(5, PerfMonitor.time(PerfMonitor.SAVE) { Thread.sleep(PerfBudget.SAVE_MS + 30); 5 }) } finally { PerfMonitor.onOverBudget = null }
    }
}
