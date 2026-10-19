package com.nerdfm.game

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Android side of the support report: owns the incident log, watchdog and unclean-exit marker, and shares the zip. */
object SupportShare {
    @Volatile var incidents: IncidentLog? = null
    @Volatile var marker: SessionMarker? = null
    private var watchdog: HangWatchdog? = null

    /** Call once from MainActivity.onCreate after App.diagnosticsDir is set. */
    fun install(ctx: Context, mainHandlerPost: (Runnable) -> Unit, mainThread: Thread) {
        val dir = File(ctx.filesDir, "diagnostics")
        val log = IncidentLog(File(dir, "incidents.log"))
        incidents = log
        marker = SessionMarker(File(dir, "session.marker"))
        PerfMonitor.onOverBudget = { op, ms, budget -> log.append("slow-operation", "$op took $ms ms (budget $budget ms). ${context()}") }
        if (watchdog == null) {
            watchdog = HangWatchdog(
                log, mainHandlerPost,
                mainStack = { mainThread.stackTrace.take(25).joinToString("\n") { "  at " + it } },
                context = { context() }
            ).also { it.start() }
        }
    }

    /** Records a previous session that never ended cleanly (crash that bypassed the recorder, hang killed by Android, force stop). */
    fun noteUncleanExit() {
        val left = marker?.consumeUnclean() ?: return
        incidents?.append("unclean-exit", "the previous session did not end cleanly. Last known state: $left")
    }

    fun context(): String {
        val w = App.world
        return "screen=${App.screen} mode=${App.mode} " + (if (w != null) "season=${w.seasonLabel()} week=${w.week}" else "no career loaded") + " busy='${App.busy}'"
    }

    fun markState() { marker?.update(context()) }

    fun leaveCleanly() { marker?.endCleanly() }

    /** Builds the zip (optionally with the career save) and opens the Android share sheet. Returns a short status line. */
    fun share(ctx: Context, includeSave: Boolean): String {
        val diag = File(ctx.filesDir, "diagnostics")
        val failures = DiagnosticsStore(diag).list()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val header = "device: ${App.deviceLabel}\ngame-version: ${Diagnostics.GAME_VERSION}\nstate: ${context()}\nincluded save: $includeSave"
        val save = if (includeSave) File(ctx.filesDir, Persistence.SAVE_NAME) else null
        val zip = SupportBundle.build(File(ctx.cacheDir, "reports"), stamp, header, PerfMonitor.report(), incidents?.read().orEmpty(), failures, save)
            ?: return "Could not create the report file. Free some storage and try again."
        return try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".reports", zip)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NerdFM support report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Share NerdFM report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
            "Report ready (" + (zip.length() / 1024) + " KB). Choose where to send it."
        } catch (e: Exception) {
            "The report was saved but could not be shared: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Number of things worth reporting right now, for the menu badge. */
    fun pendingCount(ctx: Context): Int =
        DiagnosticsStore(File(ctx.filesDir, "diagnostics")).list().size + (incidents?.read()?.lines()?.count { it.isNotBlank() } ?: 0)
}
