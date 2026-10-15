package com.nerdfm.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Mode { MENU, NEWGAME, GAME, MATCH }

object App {
    var world by mutableStateOf<World?>(null)
    var mode by mutableStateOf(Mode.MENU)
    var screen by mutableStateOf("Inbox")
    var tick by mutableIntStateOf(0)
    var busy by mutableStateOf("")
    var pendingFixture by mutableStateOf<Fixture?>(null)
    var engine by mutableStateOf<MatchEngine?>(null)
    var profilePlayerId by mutableIntStateOf(-1)
    fun bump() { tick++ }

    // App-level scope: NOT tied to any screen. The loading screen replaces the screen that
    // started the job, which used to cancel the job's scope and leave "busy" stuck forever.
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e ->
                android.util.Log.e("NerdFM", "Background job failed", e)
                busy = ""
                bump()
            }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val ctl = WindowInsetsControllerCompat(window, window.decorView)
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent {
            NerdTheme {
                Surface(color = C.bg, modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { Root() }
                }
            }
        }
    }
}

@Composable
fun Root() {
    val tick = App.tick
    val busy = App.busy
    if (busy.isNotEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                T(busy, 16)
            }
        }
        return
    }
    val w = App.world
    if (w != null && w.sacked && App.mode == Mode.GAME) {
        SackedScreen(w)
        return
    }
    when (App.mode) {
        Mode.MENU -> MainMenu()
        Mode.NEWGAME -> NewGameScreen()
        Mode.GAME -> if (w != null) GameShell(w, tick)
        Mode.MATCH -> if (w != null) MatchScreen(w)
    }
    // Player profile reachable from any screen (long-press or tap a name). View-only during a match.
    val profileId = App.profilePlayerId
    if (w != null && profileId >= 0) {
        val pp = w.players[profileId]
        if (pp != null) {
            PlayerDialog(w, pp, { App.profilePlayerId = -1 }, App.mode == Mode.MATCH) { App.bump() }
        }
    }
}

suspend fun <R> heavy(label: String, block: () -> R): R {
    App.busy = label
    try {
        return withContext(Dispatchers.Default) { block() }
    } finally {
        App.busy = ""
    }
}
