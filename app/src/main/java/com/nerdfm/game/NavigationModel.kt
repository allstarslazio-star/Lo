package com.nerdfm.game

/** Where a notification or shortcut leads: a management screen and, optionally, a specific player to open there. */
data class NavTarget(val screen: String, val playerId: Int? = null)

/** The management screens. [screen] is the stable key already used by App.screen and the Shell. */
enum class Destination(val screen: String, val label: String, val primary: Boolean) {
    HOME("Home", "Home", true),
    INBOX("Inbox", "Inbox", false),
    SQUAD("Squad", "Squad", true),
    PLAYERS("Players", "Players", false),
    PEOPLE("People", "People", false),
    TACTICS("Tactics", "Tactics", true),
    TRAINING("Training", "Training", false),
    STAFF("Staff", "Staff", false),
    LEAGUE("League", "Competitions", true),
    TRANSFERS("Transfers", "Transfers", true),
    FINANCE("Finance", "Finance", false),
    BUSINESS("Business", "Business", false),
    ECOSYSTEM("Ecosystem", "Ecosystem", false),
    CLUB("Club", "Club", true),
    DIAGNOSTICS("Diagnostics", "Diagnostics", false);

    companion object {
        val ALL: List<Destination> = values().toList()
        val PRIMARY: List<Destination> = values().filter { it.primary }
        val SECONDARY: List<Destination> = values().filter { !it.primary }
        fun fromScreen(screen: String): Destination? = values().firstOrNull { it.screen == screen }
        fun isKnown(screen: String): Boolean = fromScreen(screen) != null
    }
}

/**
 * Pure navigation state: current screen, bounded back stack and unsaved-change guards. It never touches the career
 * [World], so navigating can never reset, duplicate or discard career state. The Compose layer mirrors [current].
 *
 * Guards: a screen with unsaved edits calls [markDirty]. A navigation away from it (or a back press) is then
 * [Result.Blocked] until the caller confirms with [discardAndContinue] or the edits are saved with [markClean].
 */
class NavigationModel(start: String = Destination.HOME.screen) {
    sealed class Result {
        object Done : Result()
        object NoOp : Result()
        data class Blocked(val screen: String, val reason: String) : Result()
    }

    var current: String = if (Destination.isKnown(start)) start else Destination.HOME.screen
        private set

    private val stack = ArrayList<String>()
    private val dirty = LinkedHashMap<String, String>()
    private var pending: String? = null
    private var pendingIsBack = false

    val backStack: List<String> get() = stack.toList()
    val canGoBack: Boolean get() = stack.isNotEmpty()
    val pendingTarget: String? get() = pending

    fun markDirty(screen: String, reason: String = "You have unsaved changes on $screen.") { dirty[screen] = reason }
    fun markClean(screen: String) { dirty.remove(screen) }
    fun isDirty(screen: String): Boolean = dirty.containsKey(screen)

    /** Navigates to [screen]. Unknown names and the current screen are ignored; a dirty current screen blocks. */
    fun navigate(screen: String): Result {
        if (!Destination.isKnown(screen) || screen == current) return Result.NoOp
        dirty[current]?.let { reason ->
            pending = screen
            pendingIsBack = false
            return Result.Blocked(current, reason)
        }
        push(screen)
        return Result.Done
    }

    /** Goes back one screen (skipping entries equal to the current one). Blocked while the current screen is dirty. */
    fun back(): Result {
        while (stack.isNotEmpty() && stack.last() == current) stack.removeAt(stack.size - 1)
        if (stack.isEmpty()) return Result.NoOp
        dirty[current]?.let { reason ->
            pending = stack.last()
            pendingIsBack = true
            return Result.Blocked(current, reason)
        }
        current = stack.removeAt(stack.size - 1)
        return Result.Done
    }

    /** Called after the user confirmed they want to leave a dirty screen. Completes the blocked navigation. */
    fun discardAndContinue(): Result {
        val target = pending ?: return Result.NoOp
        val wasBack = pendingIsBack
        dirty.remove(current)
        pending = null
        pendingIsBack = false
        if (wasBack) {
            while (stack.isNotEmpty() && stack.last() == current) stack.removeAt(stack.size - 1)
            if (stack.isNotEmpty()) current = stack.removeAt(stack.size - 1) else return Result.NoOp
        } else {
            push(target)
        }
        return Result.Done
    }

    fun cancelPending() { pending = null; pendingIsBack = false }

    /** Forgets history (new career, loaded career, new job). Never called by ordinary navigation. */
    fun reset(start: String = Destination.HOME.screen) {
        stack.clear()
        dirty.clear()
        pending = null
        pendingIsBack = false
        current = if (Destination.isKnown(start)) start else Destination.HOME.screen
    }

    private fun push(screen: String) {
        // Revisiting an earlier screen behaves like going back to it, so the stack never grows duplicates or loops.
        val existing = stack.lastIndexOf(screen)
        if (existing >= 0) {
            while (stack.size > existing) stack.removeAt(stack.size - 1)
            current = screen
            return
        }
        stack.add(current)
        current = screen
        while (stack.size > MAX_STACK) stack.removeAt(0)
    }

    /** `current|a,b,c`; safe to store in settings and to restore after process death. */
    fun encode(): String = current + "|" + stack.joinToString(",")

    /** Restores a state written by [encode]. Anything unknown or malformed is dropped; the result is never invalid. */
    fun restore(encoded: String?) {
        reset()
        if (encoded.isNullOrBlank()) return
        val parts = encoded.split('|', limit = 2)
        val cur = parts[0]
        if (!Destination.isKnown(cur)) return
        current = cur
        if (parts.size > 1) {
            for (name in parts[1].split(',')) {
                if (name.isNotEmpty() && Destination.isKnown(name) && name != current && name !in stack) stack.add(name)
            }
        }
        while (stack.size > MAX_STACK) stack.removeAt(0)
    }

    companion object {
        const val MAX_STACK = 20
    }
}
