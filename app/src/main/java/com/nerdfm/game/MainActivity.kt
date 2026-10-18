package com.nerdfm.game

import android.os.Bundle
import android.os.Build
import android.os.Looper
import java.io.File
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
    var screen by mutableStateOf("Home")
    var tick by mutableIntStateOf(0)
    var busy by mutableStateOf("")
    var pendingFixture by mutableStateOf<Fixture?>(null)
    var engine by mutableStateOf<MatchEngine?>(null)
    fun bump() { tick++ }

    // ---- M7 navigation / notifications / onboarding (UI state only; never part of the career save)
    val nav = NavigationModel()
    val notices = NotificationCenter()
    val advanceGuard = ActionGuard()
    var focusPlayerId by mutableStateOf<Int?>(null)
    var pendingLeave by mutableStateOf<NavigationModel.Result.Blocked?>(null)
    var settings: SettingsStore = MemorySettingsStore()
    var tutorial: TutorialState = TutorialState(settings)
    var tutorialTick by mutableIntStateOf(0)

    // ---- M8 diagnostics
    var diagnosticsText by mutableStateOf("")
    /** Non-empty when a save failed; the Shell shows it until dismissed so progress is never silently lost. */
    var saveWarning by mutableStateOf("")
    var diagnosticsDir: File? = null
    val deviceLabel: String get() = "${Build.MANUFACTURER} ${Build.MODEL} / API ${Build.VERSION.SDK_INT}"

    fun navigate(dest: String) {
        when (val r = nav.navigate(dest)) {
            NavigationModel.Result.Done -> { screen = nav.current; persistNavigation() }
            is NavigationModel.Result.Blocked -> pendingLeave = r
            NavigationModel.Result.NoOp -> {}
        }
    }

    fun navigate(target: NavTarget) {
        focusPlayerId = target.playerId
        navigate(target.screen)
    }

    /** Returns false when there is nothing to go back to (the caller then asks before leaving the career). */
    fun goBack(): Boolean {
        return when (val r = nav.back()) {
            NavigationModel.Result.Done -> { screen = nav.current; persistNavigation(); true }
            is NavigationModel.Result.Blocked -> { pendingLeave = r; true }
            NavigationModel.Result.NoOp -> false
        }
    }

    fun confirmLeave() {
        pendingLeave = null
        if (nav.discardAndContinue() == NavigationModel.Result.Done) { screen = nav.current; persistNavigation() }
    }

    fun cancelLeave() { pendingLeave = null; nav.cancelPending() }

    /** New or loaded career: start from Home with an empty history and no leftover dismissals. */
    fun resetNavigation(start: String = "Home") {
        nav.reset(start)
        screen = nav.current
        notices.clear()
        pendingLeave = null
        focusPlayerId = null
    }

    fun persistNavigation() { settings.putString(SettingKeys.NAV_STATE, nav.encode()); SupportShare.markState() }

    fun restoreNavigation() {
        nav.restore(settings.getString(SettingKeys.NAV_STATE, ""))
        screen = nav.current
        notices.clear()
        pendingLeave = null
    }

    // App-level scope: NOT tied to any screen. The loading screen replaces the screen that
    // started the job, which used to cancel the job's scope and leave "busy" stuck forever.
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e ->
                android.util.Log.e("NerdFM", "Background job failed", e)
                try {
                    val dir = diagnosticsDir
                    if (dir != null) DiagnosticsStore(dir).write(Diagnostics.capture(world, "background-job", e, deviceLabel))
                } catch (_: Throwable) {
                    // Diagnostics must never turn one failure into two.
                }
                busy = ""
                advanceGuard.finish()
                bump()
            }
    )
}

/** Writes any uncaught crash (including Compose/UI ones on the main thread) to the diagnostics folder, then lets Android handle it as usual. */
private fun installCrashRecorder() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        try {
            val dir = App.diagnosticsDir
            if (dir != null) DiagnosticsStore(dir).write(Diagnostics.capture(null, "uncaught crash on ${thread.name}", error, App.deviceLabel))
        } catch (_: Throwable) {
            // recording must never hide the original crash
        }
        previous?.uncaughtException(thread, error)
    }
}

class MainActivity : ComponentActivity() {
    override fun onStart() { super.onStart(); SupportShare.markState() }
    override fun onStop() { SupportShare.leaveCleanly(); super.onStop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.settings = FileSettingsStore(File(filesDir, "ui-settings.properties"))
        App.tutorial = TutorialState(App.settings)
        App.diagnosticsDir = File(filesDir, "diagnostics")
        installCrashRecorder()
        val mainHandler = android.os.Handler(Looper.getMainLooper())
        SupportShare.install(this, { r -> mainHandler.post(r) }, Looper.getMainLooper().thread)
        SupportShare.noteUncleanExit()
        ThreadGuard.isMainThread = { Looper.getMainLooper().thread == Thread.currentThread() }
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
}

suspend fun <R> heavy(label: String, block: () -> R): R {
    App.busy = label
    Diagnostics.note(label)
    try {
        return withContext(Dispatchers.Default) { block() }
    } finally {
        App.busy = ""
    }
}
