package com.nerdfm.game

import java.io.File
import java.util.Properties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ------------------------------------------------------------------ settings that live outside the career save

/**
 * Small key/value store for UI preferences (tutorial progress, last screen, motion). It is deliberately separate from
 * the career save: adding a preference never needs a save-format bump or a migration.
 */
interface SettingsStore {
    fun getString(key: String, default: String = ""): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean = false): Boolean = getString(key, default.toString()).toBooleanStrictOrNull() ?: default
    fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    fun getInt(key: String, default: Int = 0): Int = getString(key, default.toString()).toIntOrNull() ?: default
    fun putInt(key: String, value: Int) = putString(key, value.toString())
    fun remove(key: String)
}

class MemorySettingsStore : SettingsStore {
    private val map = HashMap<String, String>()
    @Synchronized override fun getString(key: String, default: String): String = map[key] ?: default
    @Synchronized override fun putString(key: String, value: String) { map[key] = value }
    @Synchronized override fun remove(key: String) { map.remove(key) }
}

/** java.util.Properties backed, written through a temp file so a crash never leaves half a settings file. */
class FileSettingsStore(private val file: File) : SettingsStore {
    private val props = Properties()
    private var loaded = false

    @Synchronized private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            if (file.isFile) file.inputStream().use { props.load(it) }
        } catch (_: Exception) {
            props.clear() // unreadable preferences are not worth failing for; defaults apply
        }
    }

    @Synchronized override fun getString(key: String, default: String): String { ensureLoaded(); return props.getProperty(key) ?: default }

    @Synchronized override fun putString(key: String, value: String) {
        ensureLoaded()
        props.setProperty(key, value)
        flush()
    }

    @Synchronized override fun remove(key: String) {
        ensureLoaded()
        props.remove(key)
        flush()
    }

    private fun flush() {
        try {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { props.store(it, "NerdFM UI settings") }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (_: Exception) {
            // Preferences are best effort; the in-memory copy still applies for this session.
        }
    }
}

object SettingKeys {
    const val TUTORIAL_DONE = "tutorial.done"
    const val TUTORIAL_STEP = "tutorial.step"
    const val NAV_STATE = "nav.state"
    const val REDUCE_MOTION = "ui.reduceMotion"
}

// ------------------------------------------------------------------ onboarding

class TutorialStep(val title: String, val body: String, val screen: String)

object TutorialSteps {
    val ALL: List<TutorialStep> = listOf(
        TutorialStep("Welcome, manager", "You picked a club and started a career. Your inbox and Home screen show what needs attention.", "Home"),
        TutorialStep("Review the squad", "Open Squad or Players to see ratings, fitness and contracts. Search and filters find anyone quickly.", "Squad"),
        TutorialStep("Pick a formation and XI", "Tactics sets the formation and starting lineup. Auto-pick is on until you change it.", "Tactics"),
        TutorialStep("Review your tactics", "Mentality, tempo, pressing and width change how the team plays. Small changes are enough.", "Tactics"),
        TutorialStep("Play or simulate", "Press the Play button in the top bar on match day. Between matches it advances the week.", "Home"),
        TutorialStep("Transfers and contracts", "Offers arrive in Transfers. Renew expiring contracts from the player profile before they walk away.", "Transfers"),
        TutorialStep("Advance the calendar", "Every week the world moves on: training, injuries, finances and the transfer market. Progress saves automatically.", "Home")
    )
}

/** Dismissible, non-modal walkthrough state. Persists through a [SettingsStore]; skipping is permanent until restarted. */
class TutorialState(private val store: SettingsStore, private val steps: List<TutorialStep> = TutorialSteps.ALL) {
    var index: Int = store.getInt(SettingKeys.TUTORIAL_STEP, 0).coerceIn(0, steps.size - 1)
        private set
    var done: Boolean = store.getBoolean(SettingKeys.TUTORIAL_DONE, false)
        private set

    val active: Boolean get() = !done
    val current: TutorialStep get() = steps[index]
    val isLast: Boolean get() = index == steps.size - 1
    val total: Int get() = steps.size

    fun next() {
        if (done) return
        if (isLast) { finish(); return }
        index++
        store.putInt(SettingKeys.TUTORIAL_STEP, index)
    }

    fun previous() {
        if (done || index == 0) return
        index--
        store.putInt(SettingKeys.TUTORIAL_STEP, index)
    }

    fun skip() = finish()

    fun finish() {
        done = true
        store.putBoolean(SettingKeys.TUTORIAL_DONE, true)
    }

    /** "Show tutorial again" from the menu. */
    fun restart() {
        done = false
        index = 0
        store.putBoolean(SettingKeys.TUTORIAL_DONE, false)
        store.putInt(SettingKeys.TUTORIAL_STEP, 0)
    }
}

// ------------------------------------------------------------------ preventing duplicate actions

/**
 * Admits one run at a time and ignores repeat taps inside [minIntervalMs]. The week-advance button uses it so a
 * double tap can never advance two weeks or start two simulations.
 */
class ActionGuard(private val minIntervalMs: Long = 600L, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private var running = false
    private var lastStart = Long.MIN_VALUE / 2

    @Synchronized fun tryStart(): Boolean {
        val now = clock()
        if (running || now - lastStart < minIntervalMs) return false
        running = true
        lastStart = now
        return true
    }

    @Synchronized fun finish() { running = false }

    @get:Synchronized val isRunning: Boolean get() = running

    /** Runs [block] only when admitted and always releases the guard, even when [block] throws. */
    inline fun <T> runExclusive(block: () -> T): T? {
        if (!tryStart()) return null
        try { return block() } finally { finish() }
    }
}

// ------------------------------------------------------------------ accessibility

/** Single source of truth for the palette; Theme.kt wraps these in Compose Colors, tests audit the numbers. */
object Palette {
    const val BG: Long = 0xFF080A08
    const val SURFACE: Long = 0xFF111411
    const val CARD: Long = 0xFF1A1E1A
    const val LINE: Long = 0xFF2A302A
    const val ACCENT: Long = 0xFF22C55E
    const val GREEN: Long = 0xFF4ADE80
    const val RED: Long = 0xFFF85149
    const val AMBER: Long = 0xFFD29922
    const val STAR: Long = 0xFFFFC107
    const val TEXT: Long = 0xFFE8EEE8
    const val MUTED: Long = 0xFF8E998E
}

object Accessibility {
    const val MIN_TOUCH_DP = 48
    const val MIN_BODY_SP = 11
    const val MIN_CONTRAST_TEXT = 4.5
    const val MAX_FONT_SCALE = 1.6f

    class ColorPair(val name: String, val foreground: Long, val background: Long)

    class Result(val pair: ColorPair, val ratio: Double) {
        val passes: Boolean get() = ratio >= MIN_CONTRAST_TEXT
        fun line(): String = "%s: %.2f:1 %s".format(pair.name, ratio, if (passes) "ok" else "FAILS")
    }

    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance of an 0xAARRGGBB colour (alpha ignored). */
    fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    fun contrastRatio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Every text-on-surface combination the UI actually draws. */
    val TEXT_PAIRS: List<ColorPair> = listOf(
        ColorPair("text on background", Palette.TEXT, Palette.BG), ColorPair("text on surface", Palette.TEXT, Palette.SURFACE),
        ColorPair("text on card", Palette.TEXT, Palette.CARD), ColorPair("muted on background", Palette.MUTED, Palette.BG),
        ColorPair("muted on surface", Palette.MUTED, Palette.SURFACE), ColorPair("muted on card", Palette.MUTED, Palette.CARD),
        ColorPair("accent on surface", Palette.ACCENT, Palette.SURFACE), ColorPair("accent on card", Palette.ACCENT, Palette.CARD),
        ColorPair("green on surface", Palette.GREEN, Palette.SURFACE), ColorPair("red on surface", Palette.RED, Palette.SURFACE),
        ColorPair("red on card", Palette.RED, Palette.CARD), ColorPair("amber on surface", Palette.AMBER, Palette.SURFACE),
        ColorPair("amber on card", Palette.AMBER, Palette.CARD), ColorPair("black on accent button", 0xFF000000, Palette.ACCENT)
    )

    fun audit(): List<Result> = TEXT_PAIRS.map { Result(it, contrastRatio(it.foreground, it.background)) }

    /** Honour the user's system font size but cap it so dense tables stay usable. */
    fun clampFontScale(scale: Float): Float = if (scale.isNaN()) 1f else min(MAX_FONT_SCALE, max(0.85f, scale))
}
