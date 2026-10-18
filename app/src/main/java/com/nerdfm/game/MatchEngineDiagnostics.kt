package com.nerdfm.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Read-only diagnostics for the match simulator.
 *
 * This file deliberately does not change match outcomes. It provides repeatable,
 * inspectable reports so balancing work can be based on batches rather than a
 * handful of memorable fixtures.
 */
data class MatchAuditIssue(
    val severity: Severity,
    val code: String,
    val message: String
) {
    enum class Severity { INFO, WARNING, ERROR }
}

data class MatchAuditReport(
    val homeName: String,
    val awayName: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val homeShots: Int,
    val awayShots: Int,
    val homeShotsOnTarget: Int,
    val awayShotsOnTarget: Int,
    val homeXg: Double,
    val awayXg: Double,
    val homePossession: Int,
    val homeYellows: Int,
    val awayYellows: Int,
    val homeReds: Int,
    val awayReds: Int,
    val eventCount: Int,
    val issues: List<MatchAuditIssue>
) {
    val totalGoals: Int get() = homeGoals + awayGoals
    val totalShots: Int get() = homeShots + awayShots
    val totalXg: Double get() = homeXg + awayXg
    val hasErrors: Boolean get() = issues.any { it.severity == MatchAuditIssue.Severity.ERROR }
    val hasWarnings: Boolean get() = issues.any { it.severity == MatchAuditIssue.Severity.WARNING }

    fun concise(): String = buildString {
        append(homeName).append(' ').append(homeGoals).append('-').append(awayGoals).append(' ')
            .append(awayName)
        append(" | shots ").append(homeShots).append('-').append(awayShots)
        append(" | xG ").append("%.2f".format(homeXg)).append('-').append("%.2f".format(awayXg))
        append(" | possession ").append(homePossession).append('%')
        append(" | cards Y/R ").append(homeYellows).append('/').append(homeReds)
            .append('-').append(awayYellows).append('/').append(awayReds)
        if (issues.isNotEmpty()) append(" | audit issues ").append(issues.size)
    }
}

/** Checks invariants that should hold after a match is completed. */
object MatchEngineAuditor {
    fun inspect(engine: MatchEngine): MatchAuditReport {
        val h = engine.home
        val a = engine.away
        val issues = ArrayList<MatchAuditIssue>()

        fun issue(severity: MatchAuditIssue.Severity, code: String, text: String) {
            issues.add(MatchAuditIssue(severity, code, text))
        }

        if (!engine.finished) {
            issue(MatchAuditIssue.Severity.WARNING, "MATCH_NOT_FINISHED",
                "The audit was run before the engine reached full-time.")
        }
        if (h.goals < 0 || a.goals < 0) {
            issue(MatchAuditIssue.Severity.ERROR, "NEGATIVE_SCORE", "A team has a negative goal count.")
        }
        if (h.shots < h.sot || a.shots < a.sot) {
            issue(MatchAuditIssue.Severity.ERROR, "SOT_EXCEEDS_SHOTS",
                "Shots on target cannot exceed total shots.")
        }
        if (h.shots < 0 || a.shots < 0 || h.sot < 0 || a.sot < 0) {
            issue(MatchAuditIssue.Severity.ERROR, "NEGATIVE_SHOTS", "Shot counters cannot be negative.")
        }
        if (h.goals > h.sot || a.goals > a.sot) {
            issue(MatchAuditIssue.Severity.WARNING, "GOALS_EXCEED_SOT",
                "Goals exceed shots on target; verify the engine's own goal/penalty accounting.")
        }
        if (!h.xg.isFinite() || !a.xg.isFinite() || h.xg < 0.0 || a.xg < 0.0) {
            issue(MatchAuditIssue.Severity.ERROR, "INVALID_XG", "Expected goals must be finite and non-negative.")
        }
        if (h.passesOk > h.passes || a.passesOk > a.passes) {
            issue(MatchAuditIssue.Severity.ERROR, "COMPLETIONS_EXCEED_ATTEMPTS",
                "Completed passes cannot exceed attempted passes.")
        }
        if (h.passes < 0 || a.passes < 0 || h.passesOk < 0 || a.passesOk < 0) {
            issue(MatchAuditIssue.Severity.ERROR, "NEGATIVE_PASSES", "Pass counters cannot be negative.")
        }
        if (h.subs < 0 || a.subs < 0) {
            issue(MatchAuditIssue.Severity.ERROR, "NEGATIVE_SUBSTITUTIONS",
                "Substitution counters cannot be negative.")
        }
        if (h.subs > 5 || a.subs > 5) {
            issue(MatchAuditIssue.Severity.WARNING, "HIGH_SUBSTITUTIONS",
                "More than five substitutions were recorded; check the competition rules in use.")
        }
        if (h.yellows < 0 || a.yellows < 0 || h.reds < 0 || a.reds < 0) {
            issue(MatchAuditIssue.Severity.ERROR, "NEGATIVE_CARDS", "Card counters cannot be negative.")
        }
        if (h.reds > 11 || a.reds > 11) {
            issue(MatchAuditIssue.Severity.ERROR, "TOO_MANY_RED_CARDS",
                "A team cannot receive more than eleven red cards in one match.")
        }
        if (engine.events.isEmpty()) {
            issue(MatchAuditIssue.Severity.ERROR, "NO_EVENTS", "A match should contain at least a kick-off event.")
        }
        if (engine.events.count { it.kind == 1 } < h.goals + a.goals) {
            issue(MatchAuditIssue.Severity.WARNING, "GOAL_EVENT_MISMATCH",
                "The event log contains fewer goal events than the scoreline.")
        }
        if (engine.events.any { it.minute < 0 || it.minute > 130 }) {
            issue(MatchAuditIssue.Severity.WARNING, "UNUSUAL_EVENT_MINUTE",
                "An event has a minute outside the expected football match range.")
        }
        if (h.onPitch.size > 11 || a.onPitch.size > 11) {
            issue(MatchAuditIssue.Severity.ERROR, "TOO_MANY_PLAYERS",
                "A side has more than eleven active players.")
        }
        if (h.onPitch.isEmpty() || a.onPitch.isEmpty()) {
            issue(MatchAuditIssue.Severity.ERROR, "EMPTY_TEAM",
                "A side has no active players on the pitch.")
        }
        if (h.avgCond !in 0f..100f || a.avgCond !in 0f..100f) {
            issue(MatchAuditIssue.Severity.WARNING, "CONDITION_OUT_OF_RANGE",
                "Average condition is outside the expected 0-100 range.")
        }

        return MatchAuditReport(
            h.club.name, a.club.name, h.goals, a.goals, h.shots, a.shots, h.sot, a.sot,
            h.xg, a.xg, engine.possessionHome(), h.yellows, a.yellows, h.reds, a.reds,
            engine.events.size, issues.toList()
        )
    }
}

/** Summary statistics for a simulation batch. */
data class DistributionSummary(
    val count: Int,
    val mean: Double,
    val standardDeviation: Double,
    val minimum: Double,
    val percentile10: Double,
    val median: Double,
    val percentile90: Double,
    val maximum: Double
) {
    companion object {
        fun of(values: List<Double>): DistributionSummary {
            require(values.isNotEmpty()) { "At least one value is required." }
            val sorted = values.sorted()
            val mean = sorted.average()
            val variance = sorted.sumOf { (it - mean) * (it - mean) } / sorted.size
            fun percentile(p: Double): Double {
                val index = (p * (sorted.size - 1)).coerceIn(0.0, (sorted.size - 1).toDouble())
                val lo = index.toInt()
                val hi = min(sorted.lastIndex, lo + 1)
                val fraction = index - lo
                return sorted[lo] * (1.0 - fraction) + sorted[hi] * fraction
            }
            return DistributionSummary(
                sorted.size, mean, sqrt(variance), sorted.first(), percentile(0.10),
                percentile(0.50), percentile(0.90), sorted.last()
            )
        }
    }
}

data class BatchMatchResult(
    val seed: Long,
    val audit: MatchAuditReport
)

data class MatchBatchReport(
    val matches: List<BatchMatchResult>,
    val goals: DistributionSummary,
    val homeGoals: DistributionSummary,
    val awayGoals: DistributionSummary,
    val totalXg: DistributionSummary,
    val totalShots: DistributionSummary,
    val homePossession: DistributionSummary,
    val totalYellowCards: DistributionSummary,
    val totalRedCards: DistributionSummary,
    val maximumGoalsByOneTeam: Int,
    val homeWinRate: Double,
    val drawRate: Double,
    val awayWinRate: Double,
    val warningCount: Int,
    val errorCount: Int
) {
    fun toText(): String = buildString {
        appendLine("MATCH ENGINE BATCH")
        appendLine("Fixtures: ${matches.size}")
        appendLine("Goals/match: mean %.2f, SD %.2f, range %.0f-%.0f".format(
            goals.mean, goals.standardDeviation, goals.minimum, goals.maximum))
        appendLine("Home goals: %.2f | Away goals: %.2f".format(homeGoals.mean, awayGoals.mean))
        appendLine("Total xG: %.2f | Shots: %.2f".format(totalXg.mean, totalShots.mean))
        appendLine("Possession: home mean %.1f%% | yellow cards/match %.2f | red cards/match %.3f".format(
            homePossession.mean, totalYellowCards.mean, totalRedCards.mean))
        appendLine("Maximum goals by one team: $maximumGoalsByOneTeam")
        appendLine("Results: home %.1f%%, draw %.1f%%, away %.1f%%".format(
            homeWinRate * 100.0, drawRate * 100.0, awayWinRate * 100.0))
        appendLine("Audit warnings: $warningCount | errors: $errorCount")
        val broken = matches.filter { it.audit.hasErrors }
        if (broken.isNotEmpty()) {
            appendLine("Fixtures with errors:")
            broken.take(10).forEach { appendLine(" - seed ${it.seed}: ${it.audit.concise()}") }
        }
    }
}

/**
 * Runs isolated fixtures using a caller-provided factory. A factory should build fresh
 * teams/world state for every seed; never reuse mutated player objects between fixtures.
 */
object MatchBatchRunner {
    fun run(
        seeds: Iterable<Long>,
        factory: (Long) -> MatchEngine,
        failOnAuditError: Boolean = false
    ): MatchBatchReport {
        val rows = ArrayList<BatchMatchResult>()
        for (seed in seeds) {
            val engine = factory(seed)
            engine.simToEnd()
            val audit = MatchEngineAuditor.inspect(engine)
            rows.add(BatchMatchResult(seed, audit))
            if (failOnAuditError && audit.hasErrors) {
                val errors = audit.issues.filter { it.severity == MatchAuditIssue.Severity.ERROR }
                throw IllegalStateException("Match seed $seed failed audit: ${errors.joinToString { it.code }}")
            }
        }
        require(rows.isNotEmpty()) { "At least one seed is required." }
        val audits = rows.map { it.audit }
        val homeWins = audits.count { it.homeGoals > it.awayGoals }
        val draws = audits.count { it.homeGoals == it.awayGoals }
        val awayWins = audits.size - homeWins - draws
        val denominator = audits.size.toDouble()
        return MatchBatchReport(
            rows,
            DistributionSummary.of(audits.map { it.totalGoals.toDouble() }),
            DistributionSummary.of(audits.map { it.homeGoals.toDouble() }),
            DistributionSummary.of(audits.map { it.awayGoals.toDouble() }),
            DistributionSummary.of(audits.map { it.totalXg }),
            DistributionSummary.of(audits.map { it.totalShots.toDouble() }),
            DistributionSummary.of(audits.map { it.homePossession.toDouble() }),
            DistributionSummary.of(audits.map { (it.homeYellows + it.awayYellows).toDouble() }),
            DistributionSummary.of(audits.map { (it.homeReds + it.awayReds).toDouble() }),
            audits.maxOf { max(it.homeGoals, it.awayGoals) },
            homeWins / denominator, draws / denominator, awayWins / denominator,
            audits.sumOf { it.issues.count { i -> i.severity == MatchAuditIssue.Severity.WARNING } },
            audits.sumOf { it.issues.count { i -> i.severity == MatchAuditIssue.Severity.ERROR } }
        )
    }
}

/** A stable, human-readable digest for comparing reports across code changes. */
object MatchReportDigest {
    fun digest(report: MatchBatchReport): String {
        val sorted = report.matches.sortedBy { it.seed }
        return buildString {
            sorted.forEach { row ->
                append(row.seed).append(':')
                    .append(row.audit.homeGoals).append('-')
                    .append(row.audit.awayGoals).append(':')
                    .append(row.audit.homeShots).append('-')
                    .append(row.audit.awayShots).append(':')
                    .append("%.3f".format(row.audit.homeXg)).append('-')
                    .append("%.3f".format(row.audit.awayXg)).append(';')
            }
        }
    }

    fun sameResults(first: MatchBatchReport, second: MatchBatchReport): Boolean =
        digest(first) == digest(second)
}

/**
 * Lightweight guardrails for future balance tuning. These are intentionally broad:
 * real football has variance, and a tuning harness should flag suspicious batches,
 * not force every sample to match one exact target.
 */
data class BalanceTargets(
    val minimumMeanGoals: Double = 1.6,
    val maximumMeanGoals: Double = 3.8,
    val minimumMeanShots: Double = 12.0,
    val maximumMeanShots: Double = 34.0,
    val maximumAuditErrors: Int = 0,
    val minimumHomeWinRate: Double = 0.20,
    val maximumHomeWinRate: Double = 0.65,
    val maximumGoalsByOneTeam: Int = 9
)

data class BalanceFinding(
    val code: String,
    val severity: MatchAuditIssue.Severity,
    val observed: String,
    val expected: String
)

object MatchBalanceEvaluator {
    fun evaluate(report: MatchBatchReport, targets: BalanceTargets = BalanceTargets()): List<BalanceFinding> {
        val findings = ArrayList<BalanceFinding>()
        fun check(
            code: String,
            value: Double,
            low: Double,
            high: Double,
            label: String
        ) {
            if (value < low || value > high) {
                findings.add(BalanceFinding(
                    code, MatchAuditIssue.Severity.WARNING,
                    "$label = %.3f".format(value),
                    "expected %.3f..%.3f".format(low, high)
                ))
            }
        }
        check("GOALS_MEAN", report.goals.mean, targets.minimumMeanGoals,
            targets.maximumMeanGoals, "mean goals")
        check("SHOTS_MEAN", report.totalShots.mean, targets.minimumMeanShots,
            targets.maximumMeanShots, "mean shots")
        check("HOME_ADVANTAGE", report.homeWinRate, targets.minimumHomeWinRate,
            targets.maximumHomeWinRate, "home win rate")
        if (report.maximumGoalsByOneTeam > targets.maximumGoalsByOneTeam) {
            findings.add(BalanceFinding(
                "EXTREME_SCORELINE", MatchAuditIssue.Severity.WARNING,
                report.maximumGoalsByOneTeam.toString(), "at most ${targets.maximumGoalsByOneTeam} goals by one team"
            ))
        }
        if (report.errorCount > targets.maximumAuditErrors) {
            findings.add(BalanceFinding(
                "AUDIT_ERRORS", MatchAuditIssue.Severity.ERROR,
                report.errorCount.toString(), "at most ${targets.maximumAuditErrors}"
            ))
        }
        if (report.matches.size < 100) {
            findings.add(BalanceFinding(
                "SMALL_SAMPLE", MatchAuditIssue.Severity.INFO,
                report.matches.size.toString(), "100+ fixtures recommended for initial tuning"
            ))
        }
        return findings
    }
}

/** Utilities for deterministic, seed-separated test campaigns. */
object MatchSeedPlan {
    fun range(first: Long, count: Int): List<Long> {
        require(count >= 0) { "count must be non-negative" }
        return (0 until count).map { first + it.toLong() }
    }

    fun interleaved(first: Long, count: Int, stride: Long): List<Long> {
        require(count >= 0) { "count must be non-negative" }
        require(stride > 0L) { "stride must be positive" }
        return (0 until count).map { first + it.toLong() * stride }
    }

    fun paired(first: Long, pairs: Int): List<Pair<Long, Long>> {
        require(pairs >= 0) { "pairs must be non-negative" }
        return (0 until pairs).map { i ->
            val base = first + i.toLong() * 2L
            base to (base + 1L)
        }
    }
}

/** Generic validation helpers shared by diagnostics and future match-data importers. */
object EngineInputValidation {
    fun validateAttributeVector(attributes: IntArray): List<String> {
        val problems = ArrayList<String>()
        if (attributes.size != NATTR) {
            problems.add("Expected $NATTR attributes but received ${attributes.size}.")
            return problems
        }
        attributes.forEachIndexed { index, value ->
            if (value !in 1..100) problems.add("Attribute ${ATTRS[index].name} is outside 1..100: $value.")
        }
        return problems
    }

    fun validateLineup(playerIds: List<Int>, squadIds: Set<Int>): List<String> {
        val problems = ArrayList<String>()
        if (playerIds.size != 11) problems.add("A starting lineup must contain exactly 11 players.")
        if (playerIds.distinct().size != playerIds.size) problems.add("A player appears more than once in the lineup.")
        playerIds.filterNot { it in squadIds }.forEach {
            problems.add("Lineup player id $it is not present in the supplied squad.")
        }
        return problems
    }

    fun validateProbability(name: String, value: Double): String? =
        if (!value.isFinite() || value !in 0.0..1.0) "$name must be finite and in [0, 1]." else null

    fun validateNonNegative(name: String, value: Double): String? =
        if (!value.isFinite() || value < 0.0) "$name must be finite and non-negative." else null
}

/** Compact, comparable summary of a single fixture, useful in bug reports. */
data class FixtureTrace(
    val seed: Long,
    val home: String,
    val away: String,
    val score: String,
    val eventKinds: Map<Int, Int>,
    val firstEvents: List<String>,
    val lastEvents: List<String>
) {
    companion object {
        fun from(seed: Long, engine: MatchEngine, sampleSize: Int = 5): FixtureTrace {
            require(sampleSize >= 0) { "sampleSize must be non-negative" }
            val events = engine.events
            return FixtureTrace(
                seed,
                engine.home.club.name,
                engine.away.club.name,
                "${engine.home.goals}-${engine.away.goals}",
                events.groupingBy { it.kind }.eachCount().toSortedMap(),
                events.take(sampleSize).map { "${it.minute}' ${it.text}" },
                events.takeLast(sampleSize).map { "${it.minute}' ${it.text}" }
            )
        }
    }

    fun toText(): String = buildString {
        appendLine("$home v $away | $score | seed=$seed")
        appendLine("Event counts: ${eventKinds.entries.joinToString { "${it.key}=${it.value}" }}")
        appendLine("Opening events:")
        firstEvents.forEach { appendLine("  $it") }
        appendLine("Closing events:")
        lastEvents.forEach { appendLine("  $it") }
    }
}

/**
 * Reproducible calibration entry point using NerdFM's real match engine and fictional teams.
 * The default campaign is intentionally explicit so a developer can run 5,000+ fixtures
 * without putting a costly campaign in every ordinary unit-test invocation.
 */
object MatchCalibrationHarness {
    const val DEFAULT_MATCHES: Int = 5_000
    const val DEFAULT_SEED: Long = 20261016L

    fun run(matches: Int = DEFAULT_MATCHES, firstSeed: Long = DEFAULT_SEED): MatchBatchReport {
        require(matches >= 1) { "At least one match is required." }
        return MatchBatchRunner.run(MatchSeedPlan.range(firstSeed, matches), ::createFixture, failOnAuditError = true)
    }

    private fun createFixture(seed: Long): MatchEngine {
        val rnd = Random(seed)
        val world = World().apply { this.seed = seed }
        val home = Club(0, "Calibration Home", 0, 60, 20_000, 0xFF2255AAL, 0xFFEEEEEE)
        val away = Club(1, "Calibration Away", 0, 60, 20_000, 0xFFAA5522L, 0xFFEEEEEE)
        val positions = Formations.list[0].slots + listOf(
            Pos.GK, Pos.CB, Pos.CB, Pos.CM, Pos.CM, Pos.LW, Pos.RW
        )

        fun makeTeam(club: Club): MatchTeam {
            val roster = positions.mapIndexed { index, pos ->
                val target = 60f + rnd.nextInt(-3, 4)
                val player = WorldGen.makePlayer(world, rnd, pos, target, 24 + rnd.nextInt(7), 70)
                player.clubId = club.id
                player.id.also { world.players[it] = player; club.players.add(it) }
                PS(player, pos, (1.0 + rnd.gauss() * 0.025).toFloat())
            }
            val tactics = Tactics()
            tactics.formation = 0
            tactics.mentality = 0
            tactics.tempo = 1
            tactics.pressing = 1
            tactics.width = 1
            tactics.line = 1
            tactics.passing = 1
            return MatchTeam(club, tactics, roster.take(11), roster.drop(11), true, MatchCalibration())
        }

        val homeTeam = makeTeam(home)
        val awayTeam = makeTeam(away)
        return MatchEngine(homeTeam, awayTeam, rnd, homeTeam.calibration)
    }
}
