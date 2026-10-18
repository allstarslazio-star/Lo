package com.nerdfm.game
// Stand-ins for UI-layer symbols that live in files this offline check does not compile (MainActivity.kt, Squad.kt).
object App {
    var screen = "Inbox"; var busy = ""; var tick = 0; fun bump() { tick++ }
    val notices = NotificationCenter()
    val nav = NavigationModel()
    var focusPlayerId: Int? = null
    var settings: SettingsStore = MemorySettingsStore()
    var tutorial: TutorialState = TutorialState(settings)
    var tutorialTick = 0
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
    var diagnosticsText = ""
    var saveWarning = ""
    var diagnosticsDir: java.io.File? = null
    var world: World? = null
    val deviceLabel: String get() = "stub"
    fun navigate(dest: String) {}
    fun navigate(target: NavTarget) {}
}
fun isKnown(w: World, p: Player): Boolean = true
fun shownOvr(w: World, p: Player): Int = p.ovr
fun shownAttr(w: World, p: Player, i: Int): Int = 0
fun potText(w: World, p: Player): String = ""
fun attrText(w: World, p: Player, i: Int): String = ""
fun statusText(p: Player): String = ""
@androidx.compose.runtime.Composable fun PlayerDialog(w: World, p: Player, onClose: () -> Unit, onChanged: () -> Unit) {}
suspend fun <R> heavy(label: String, block: () -> R): R = block()
object SupportShare {
    var incidents: IncidentLog? = null
    fun pendingCount(ctx: android.content.Context): Int = 0
    fun share(ctx: android.content.Context, includeSave: Boolean): String = ""
}
