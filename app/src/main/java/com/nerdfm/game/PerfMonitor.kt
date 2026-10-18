package com.nerdfm.game

import kotlin.math.ceil

/** Release gates from the M8 plan. They are only meaningful when measured on a representative mid-range device. */
object PerfBudget {
    const val WEEK_ADVANCE_MS = 2_000L
    const val SAVE_MS = 1_000L

    /** A desktop JVM is not the target hardware; unit tests only use this loose bound to catch pathological slowdowns. */
    const val DESKTOP_SANITY_FACTOR = 10L

    fun budgetFor(operation: String): Long? = when (operation) {
        PerfMonitor.WEEK -> WEEK_ADVANCE_MS
        PerfMonitor.SAVE -> SAVE_MS
        else -> null
    }
}

/**
 * Records main-thread violations. Production code never crashes on a violation; the count is surfaced in the
 * release-diagnostics panel and asserted to stay zero by tests that install a fake detector.
 */
object ThreadGuard {
    /** MainActivity installs `Looper.getMainLooper().thread == Thread.currentThread()`; plain JVM runs default to false. */
    @Volatile var isMainThread: () -> Boolean = { false }

    @get:Synchronized
    var violations: Int = 0
        private set

    @get:Synchronized
    var lastViolation: String = ""
        private set

    @Synchronized
    fun check(operation: String) {
        if (isMainThread()) {
            violations++
            lastViolation = operation
        }
    }

    @Synchronized
    fun reset() {
        violations = 0
        lastViolation = ""
    }
}

/** Thread-safe timing log for the heavy operations the release process measures. */
object PerfMonitor {
    const val WEEK = "week-advance"
    const val SEASON = "season-rollover"
    const val SAVE = "career-save"
    const val LOAD = "career-load"
    const val MATCH = "match-simulation"
    const val AUDIT = "integrity-audit"

    private const val MAX_SAMPLES = 200

    class Stats(
        val operation: String,
        val count: Int,
        val lastMs: Long,
        val maxMs: Long,
        val meanMs: Double,
        val p95Ms: Long,
        val budgetMs: Long?
    ) {
        /** Operations without a budget are "measured and reported" only, so they always pass. */
        val withinBudget: Boolean get() = budgetMs == null || p95Ms <= budgetMs

        fun line(): String {
            val verdict = when {
                budgetMs == null -> "reported"
                withinBudget -> "PASS (budget ${budgetMs} ms)"
                else -> "OVER BUDGET (${budgetMs} ms)"
            }
            return "%s: n=%d last=%d ms mean=%.0f ms p95=%d ms max=%d ms -> %s".format(operation, count, lastMs, meanMs, p95Ms, maxMs, verdict)
        }
    }

    private class Series {
        val samples = ArrayDeque<Long>()
        var count = 0
        var max = 0L
        var last = 0L
    }

    private val series = LinkedHashMap<String, Series>()

    @Synchronized
    fun record(operation: String, millis: Long) {
        val s = series.getOrPut(operation) { Series() }
        val value = if (millis < 0L) 0L else millis
        s.samples.addLast(value)
        while (s.samples.size > MAX_SAMPLES) s.samples.removeFirst()
        s.count++
        s.last = value
        if (value > s.max) s.max = value
    }

    /** Times [block], records the sample even when it throws, and notes a main-thread call in [ThreadGuard]. */
    fun <T> time(operation: String, block: () -> T): T {
        ThreadGuard.check(operation)
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            record(operation, (System.nanoTime() - start) / 1_000_000L)
        }
    }

    @Synchronized
    fun stats(operation: String): Stats? {
        val s = series[operation] ?: return null
        if (s.samples.isEmpty()) return null
        val sorted = s.samples.sorted()
        val index = (ceil(0.95 * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)
        return Stats(operation, s.count, s.last, s.max, sorted.average(), sorted[index], PerfBudget.budgetFor(operation))
    }

    @Synchronized
    fun all(): List<Stats> = series.keys.toList().mapNotNull { stats(it) }

    @Synchronized
    fun reset() {
        series.clear()
    }

    fun report(): String {
        val rows = all()
        val body = if (rows.isEmpty()) "No timings recorded yet." else rows.joinToString("\n") { it.line() }
        return body + "\nMain-thread violations: ${ThreadGuard.violations}" +
            (if (ThreadGuard.lastViolation.isNotEmpty()) " (last: ${ThreadGuard.lastViolation})" else "")
    }
}
