package com.nerdfm.game

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Plain, Android-free support tooling: an incident log, a hang/slowdown watchdog and the zip the player can share.
 * Everything here is deterministic given its inputs so it can be unit-tested on a desktop JVM.
 */

/** Append-only text log of slowdowns, hangs and unclean exits. Capped in size; never throws. */
class IncidentLog(private val file: File, private val maxBytes: Long = 200_000L, private val clock: () -> String = { java.time.Instant.now().toString() }) {
    @Synchronized
    fun append(kind: String, detail: String) {
        try {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > maxBytes) {
                // keep the newest half
                val text = file.readText()
                file.writeText(text.substring(text.length / 2).substringAfter('\n', ""))
            }
            file.appendText("[" + clock() + "] " + kind + ": " + detail.trim() + "\n")
        } catch (_: Exception) {
            // diagnostics must never become a second problem
        }
    }

    @Synchronized
    fun read(): String = try { if (file.exists()) file.readText() else "" } catch (_: Exception) { "" }

    @Synchronized
    fun clear() { try { file.delete() } catch (_: Exception) { } }
}

/**
 * Notices when the main thread stops answering. A daemon thread asks the main thread to "pong" every [pollMs]; if the pong
 * is late the watchdog logs one incident per stall (with the main thread's stack) and, for smaller delays, a rate-limited
 * "slow response" note. [poll] holds all the logic and takes the time as an argument so tests do not need real sleeping.
 */
class HangWatchdog(
    private val log: IncidentLog,
    private val postToMain: (Runnable) -> Unit,
    private val mainStack: () -> String,
    private val context: () -> String = { "" },
    val slowMs: Long = 700L,
    val hangMs: Long = 3_000L,
    val minGapBetweenSlowMs: Long = 10_000L,
    val maxSlowNotes: Int = 40
) {
    private var sentAt = -1L
    @Volatile private var answeredAt = -1L
    private var hangReported = false
    private var slowStack: String? = null
    private var lastSlowNoteAt = Long.MIN_VALUE / 2
    var slowNotes = 0; private set
    var hangs = 0; private set
    @Volatile private var running = false
    private var thread: Thread? = null

    private fun pong(now: () -> Long) = Runnable { answeredAt = now() }

    /** One watchdog step at time [now] (ms). */
    @Synchronized
    fun poll(now: Long, clock: () -> Long = { now }) {
        if (sentAt < 0L || answeredAt >= sentAt) {
            // previous ping answered: measure it, then send the next
            if (sentAt >= 0L) {
                val delay = answeredAt - sentAt
                if (hangReported) { hangReported = false }
                else if (delay >= slowMs && slowNotes < maxSlowNotes && now - lastSlowNoteAt >= minGapBetweenSlowMs) {
                    slowNotes++; lastSlowNoteAt = now
                    log.append("slow-ui", "main thread answered ${delay} ms late. ${context()}" + (slowStack?.let { "\n" + it } ?: ""))
                }
            }
            slowStack = null
            sentAt = now
            postToMain(pong(clock))
            return
        }
        // a ping is outstanding: remember where the main thread was while it was slow (taken once per ping, only when notes are still allowed)
        if (slowStack == null && !hangReported && now - sentAt >= slowMs && slowNotes < maxSlowNotes && now - lastSlowNoteAt >= minGapBetweenSlowMs) slowStack = mainStack()
        if (!hangReported && now - sentAt >= hangMs) {
            hangReported = true; hangs++
            log.append("hang", "main thread has not responded for ${now - sentAt} ms. ${context()}\n" + mainStack())
        }
    }

    fun start(pollMs: Long = 500L) {
        if (running) return
        running = true
        val t = Thread({
            while (running) {
                try { poll(System.currentTimeMillis(), { System.currentTimeMillis() }) } catch (_: Throwable) { }
                try { Thread.sleep(pollMs) } catch (_: InterruptedException) { return@Thread }
            }
        }, "nerdfm-watchdog")
        t.isDaemon = true
        thread = t
        t.start()
    }

    fun stop() { running = false; thread?.interrupt() }
}

/** Remembers that the app is in the foreground; if the next start still finds it, the last session ended badly. */
class SessionMarker(private val file: File) {
    fun begin(info: String) { try { file.parentFile?.mkdirs(); file.writeText(info) } catch (_: Exception) { } }
    fun update(info: String) = begin(info)
    /** Returns what the unfinished session left behind, or null if the last one ended cleanly. */
    fun consumeUnclean(): String? = try {
        if (file.exists()) file.readText().also { file.delete() } else null
    } catch (_: Exception) { null }
    fun endCleanly() { try { file.delete() } catch (_: Exception) { } }
}

object SupportBundle {
    const val MAX_SAVE_BYTES = 25L * 1024 * 1024

    /** The readable report that goes first in the zip (header, timings, incidents and every recorded failure). */
    fun reportText(header: String, perf: String, incidents: String, failures: List<File>): String {
        val sb = StringBuilder()
        sb.append("NerdFM support report\n").append(header.trim()).append("\n\n")
        sb.append("== Timings ==\n").append(perf.trim()).append("\n\n")
        sb.append("== Incidents (slowdowns, hangs, unclean exits) ==\n").append(if (incidents.isBlank()) "none recorded\n" else incidents.trim() + "\n").append('\n')
        sb.append("== Recorded failures (").append(failures.size).append(") ==\n")
        for (f in failures) {
            sb.append("--- ").append(f.name).append(" ---\n")
            sb.append(try { f.readText() } catch (_: Exception) { "(unreadable)" }).append('\n')
        }
        return sb.toString()
    }

    /**
     * Builds the shareable zip in [outDir]: report.txt and, when asked and not too large, the career save. Old reports in
     * [outDir] are removed first so the folder never grows. Returns null if the file could not be written.
     */
    fun build(outDir: File, stamp: String, header: String, perf: String, incidents: String, failures: List<File>, save: File?): File? {
        return try {
            outDir.mkdirs()
            outDir.listFiles { f -> f.name.startsWith("nerdfm-report-") }?.forEach { it.delete() }
            val zip = File(outDir, "nerdfm-report-$stamp.zip")
            ZipOutputStream(zip.outputStream().buffered()).use { z ->
                z.putNextEntry(ZipEntry("report.txt"))
                z.write(reportText(header, perf, incidents, failures).toByteArray(Charsets.UTF_8))
                z.closeEntry()
                if (save != null && save.isFile && save.length() <= MAX_SAVE_BYTES) {
                    z.putNextEntry(ZipEntry("career1.dat"))
                    FileInputStream(save).use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
            zip
        } catch (_: Exception) {
            null
        }
    }
}
