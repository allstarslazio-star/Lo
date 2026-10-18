package com.nerdfm.game

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Release diagnostics (M8): the place to read on-device timings, run the integrity audit, inspect save files and copy a
 * reproducible report. Performance budgets are only meaningful when these numbers come from a real phone.
 */
@Composable
fun DiagnosticsScreen(w: World) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = App.scope
    val tick = App.tick
    tick.let { }
    var saveListing by remember { mutableStateOf("Scanning save files...") }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(tick) { saveListing = withContext(Dispatchers.IO) { SaveInspector.describe(ctx.filesDir) } }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Timings", 15, C.accent, true)
            T(App.deviceLabel, 11, C.muted, lines = 2)
            Spacer(Modifier.height(4.dp))
            T(PerfMonitor.report(), 11, C.text, lines = 12)
            Spacer(Modifier.height(4.dp))
            T("Budgets: week advance p95 \u2264 ${PerfBudget.WEEK_ADVANCE_MS} ms, save p95 \u2264 ${PerfBudget.SAVE_MS} ms.", 11, C.muted, lines = 3)
            Spacer(Modifier.height(8.dp))
            T("Save files", 13, C.accent, true)
            T(saveListing, 11, C.text, lines = 8)
        }
        Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            T("Integrity", 15, C.accent, true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn("Run audit now", enabled = App.busy.isEmpty(), modifier = Modifier.minTouch()) {
                    scope.launch {
                        val report = heavy("Auditing the career...") { PerfMonitor.time(PerfMonitor.AUDIT) { SaveCoordinator.withWorld { WorldIntegrity.audit(w) } } }
                        App.diagnosticsText = report.summary(14)
                        App.bump()
                    }
                }
                Btn(if (copied) "Copied" else "Copy report", modifier = Modifier.minTouch()) {
                    val text = Diagnostics.render(Diagnostics.capture(w, "manual-report", null, App.deviceLabel)) + "\n" + PerfMonitor.report()
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                }
            }
            Spacer(Modifier.height(6.dp))
            T(if (App.diagnosticsText.isEmpty()) "No audit has been run this session." else App.diagnosticsText, 11, C.text, lines = 18)
            Spacer(Modifier.height(8.dp))
            T("Reports stay on this device. Names and file paths are removed from copied text.", 11, C.muted, lines = 3)
            Spacer(Modifier.height(8.dp))
            Btn("Show tutorial again", modifier = Modifier.minTouch()) { App.tutorial.restart(); App.tutorialTick++ }
        }
    }
}
