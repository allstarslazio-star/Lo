package com.nerdfm.game

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Report a problem": shows what the app recorded (crashes, slowdowns, hangs) and builds a zip the player can share
 * from the Android share sheet. The save can be added so the problem can be reproduced; nothing is sent automatically.
 */
@Composable
fun SupportCard() {
    val ctx = LocalContext.current
    val scope = App.scope
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableIntStateOf(SupportShare.pendingCount(ctx)) }
    val dir = App.diagnosticsDir
    val latest = remember(pending) { dir?.let { d -> DiagnosticsStore(d).list().lastOrNull()?.let { f -> try { f.readText() } catch (_: Exception) { "" } } }.orEmpty() }
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth()) {
        T(if (pending > 0) "Problem report ($pending recorded)" else "Problem report", 13, if (pending > 0) C.amber else C.accent, true)
        T(
            if (pending > 0) "The app recorded a crash, slowdown or hang. Share the report so it can be fixed."
            else "If the app slows down, hangs or crashes, it records what happened here. You can share it any time.", 11, C.muted, lines = 3
        )
        if (latest.isNotEmpty()) T(latest.take(500), 10, C.muted, lines = 7)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Share report") {
                scope.launch { status = withContext(Dispatchers.IO) { SupportShare.share(ctx, false) } }
            }
            Btn("Share report + save") {
                scope.launch { status = withContext(Dispatchers.IO) { SupportShare.share(ctx, true) } }
            }
            if (pending > 0) Btn("Clear") {
                dir?.let { d -> DiagnosticsStore(d).list().forEach { it.delete() } }
                SupportShare.incidents?.clear()
                pending = 0; status = "Cleared."
            }
        }
        if (status.isNotEmpty()) T(status, 11, C.text, lines = 3)
        T("The save lets the problem be reproduced exactly. Reports stay on your phone until you share them. Names and file paths are removed from error text.", 10, C.muted, lines = 4)
    }
}
