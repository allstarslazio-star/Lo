package com.nerdfm.game

import java.io.File
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/** Measure-only calibration report; fixed seeds 3,000,000..3,000,499, each with a newly generated World/squad. */
class MatchRealismReportTest {
    @Test fun seededFiveHundredMatchRealismReport() {
        val seeds = MatchSeedPlan.range(3_000_000L, 500)
        val engines = LinkedHashMap<Long, MatchEngine>()
        val batch = MatchBatchRunner.run(seeds, factory = { seed ->
            val world = WorldGen.generate(seed, "Realism report", 0)
            val homeClub = world.clubs[0]
            val awayClub = world.clubs[1]
            val teamRandom = Random(seed xor 0x5EEDL)
            val home = MatchTeams.build(world, homeClub, awayClub, true, teamRandom)
            val away = MatchTeams.build(world, awayClub, homeClub, false, teamRandom)
            MatchEngine(home, away, Random(seed xor 0xB47C4L), world.matchCalibration).also { engines[seed] = it }
        })

        // Raw in-match ratings are unclamped; the game clamps them to 3..10 when a match is applied to players.
        val rawRatings = engines.values.flatMap { it.home.everyone + it.away.everyone }.map { it.rating }
        engines.forEach { (seed, engine) -> engine.applyToPlayers(Random(seed xor 0xA991L)) }
        val ratings = engines.values.flatMap { it.home.everyone + it.away.everyone }.filter { it.played }.map { it.rating }

        val rows = batch.matches.map { it.audit }
        val yellowCards = engines.values.sumOf { it.home.yellows + it.away.yellows }.toDouble() / rows.size
        val redCards = engines.values.sumOf { it.home.reds + it.away.reds }.toDouble() / rows.size
        val homeFit = poissonGoodnessOfFit(rows.map { it.homeGoals })
        val awayFit = poissonGoodnessOfFit(rows.map { it.awayGoals })
        val totalFit = poissonGoodnessOfFit(rows.map { it.totalGoals })
        val balanceFindings = MatchBalanceEvaluator.evaluate(batch)

        val report = buildString {
            appendLine("NERDFM REALISM REPORT — MEASURE ONLY")
            appendLine("Fixtures: ${rows.size}; seeds: ${seeds.first()}..${seeds.last()} (inclusive)")
            appendLine("Fresh generated World and squads per fixture: yes")
            appendLine("Target season: Premier League 2025/26, 380 matches")
            appendLine("Target retrieval date: 15 Oct 2026 (user-supplied; not independently verified)")
            appendLine("\nSIMULATION METRICS")
            appendLine("Mean home goals: %.3f".format(batch.homeGoals.mean))
            appendLine("Mean away goals: %.3f".format(batch.awayGoals.mean))
            appendLine("Mean total goals: %.3f".format(batch.goals.mean))
            appendLine("Home win rate: %.2f%%".format(batch.homeWinRate * 100.0))
            appendLine("Draw rate: %.2f%%".format(batch.drawRate * 100.0))
            appendLine("Away win rate: %.2f%%".format(batch.awayWinRate * 100.0))
            appendLine("Yellow cards/match: %.3f".format(yellowCards))
            appendLine("Red cards/match: %.3f".format(redCards))
            appendLine("Raw in-match player ratings (before clamp): %.3f..%.3f; above 10.0: %.2f%%".format(
                rawRatings.minOrNull(), rawRatings.maxOrNull(), 100.0 * rawRatings.count { it > 10f } / rawRatings.size))
            appendLine("Final stored player ratings (after applyToPlayers): %.3f..%.3f".format(ratings.minOrNull(), ratings.maxOrNull()))
            appendLine("Fouls/match: %.2f; corners/match: %.2f; shots/match: %.2f".format(
                engines.values.sumOf { it.home.fouls + it.away.fouls }.toDouble() / rows.size,
                engines.values.sumOf { it.home.corners + it.away.corners }.toDouble() / rows.size,
                batch.totalShots.mean))
            appendLine("Matches with 8+ total goals: ${rows.count { it.totalGoals >= 8 }} of ${rows.size}; highest total: ${rows.maxOf { it.totalGoals }}")
            appendLine("\nFITTED POISSON CHI-SQUARE GOODNESS-OF-FIT")
            appendLine("Metric | fitted lambda | pooled bins | chi-square | df | p-value")
            appendLine(homeFit.asReportRow("Home goals"))
            appendLine("  pooled bins: ${homeFit.details.joinToString("; ")}")
            appendLine(awayFit.asReportRow("Away goals"))
            appendLine("  pooled bins: ${awayFit.details.joinToString("; ")}")
            appendLine(totalFit.asReportRow("Total goals"))
            appendLine("  pooled bins: ${totalFit.details.joinToString("; ")}")
            appendLine("Tail categories are pooled contiguously until each expected count is at least 5; lambda is fitted to this sample.")
            appendLine("\nTARGET COMPARISON (simulation minus target; percentage points for rates)")
            appendLine("Metric | simulated | target | difference | source / note")
            appendLine("Goals/match | %.3f | 2.750 | %+.3f | Wikipedia, 2025-26 Premier League: 1,045 / 380 (user-supplied)".format(batch.goals.mean, batch.goals.mean - 2.75))
            appendLine("Home win rate | %.2f%% | UNVERIFIED | UNVERIFIED | no sourced target supplied".format(batch.homeWinRate * 100.0))
            appendLine("Draw rate | %.2f%% | 27.4%% | %+.2f pp | Wikipedia table: 104 / 380 (user-derived)".format(batch.drawRate * 100.0, batch.drawRate * 100.0 - 27.4))
            appendLine("Away win rate | %.2f%% | UNVERIFIED | UNVERIFIED | no sourced target supplied".format(batch.awayWinRate * 100.0))
            appendLine("Decisive matches | %.2f%% | 72.6%% | %+.2f pp | user-supplied target".format((batch.homeWinRate + batch.awayWinRate) * 100.0, (batch.homeWinRate + batch.awayWinRate) * 100.0 - 72.6))
            appendLine("Yellow cards/match | %.3f | 4.050 | %+.3f | koppafootball.com (single secondary source; user-supplied)".format(yellowCards, yellowCards - 4.05))
            appendLine("Red cards/match | %.3f | 0.126 | %+.3f | koppafootball.com (single secondary source; user-supplied)".format(redCards, redCards - 0.126))
            appendLine("\nMatchBalanceEvaluator findings (diagnostic only):")
            if (balanceFindings.isEmpty()) appendLine("- none") else balanceFindings.forEach { appendLine("- ${it.severity}: ${it.code}: ${it.observed}; ${it.expected}") }
            appendLine("\nINTERPRETATION: descriptive sample diagnostics only; no realism pass/fail assertion and no engine tuning.")
            appendLine("\nLICENSING NOTE")
            appendLine("References read: 04-references.md entries for football-data.co.uk, StatsBomb Open Data, FBref/Understat, Dixon-Coles/Poisson, Android Compose/Architecture, and the game-engine examples; target-source names and figures supplied directly by the user.")
            appendLine("The handoff reference note labels Openfoot Manager and FootballEngine as GPL-3.0, but also says license details were written from memory and require verification. Their source was not read in this phase; those license claims are NOT VERIFIED here. No GPL code was copied.")
            appendLine("Target figures are user-supplied and not independently verified in this offline phase.")
        }
        println(report)
        val workingDirectory = File(System.getProperty("user.dir"))
        val projectRoot = if (workingDirectory.name == "app") workingDirectory.parentFile else workingDirectory
        val output = File(projectRoot, "app/build/reports/realism-report.txt")
        output.parentFile?.mkdirs()
        output.writeText(report)

        // The only hard assertions are simulator safety/invariants, not realism targets.
        assertTrue("no fixture may exceed eight total goals", batch.matches.all { it.audit.totalGoals <= 8 })
        assertTrue("all player ratings must stay in 3.0..10.0", ratings.all { it in 3.0f..10.0f })
    }

    private data class Fit(
        val lambda: Double, val bins: Int, val statistic: Double, val df: Int, val p: Double,
        val details: List<String>
    ) {
        fun asReportRow(label: String) = "$label | %.4f | %d | %.4f | %d | %.6f".format(lambda, bins, statistic, df, p)
    }

    private fun poissonGoodnessOfFit(values: List<Int>): Fit {
        val n = values.size
        val lambda = values.average()
        val observed = (0..7).map { k -> values.count { it == k }.toDouble() } + values.count { it >= 8 }.toDouble()
        val probabilities = (0..7).map { k -> poissonPmf(k, lambda) } + (1.0 - (0..7).sumOf { poissonPmf(it, lambda) })
        val groupedObserved = ArrayList<Double>()
        val groupedExpected = ArrayList<Double>()
        val groupedStarts = ArrayList<Int>()
        val groupedEnds = ArrayList<Int>()
        var obs = 0.0
        var expCount = 0.0
        var groupStart = 0
        for (i in observed.indices) {
            obs += observed[i]
            expCount += probabilities[i] * n
            if (expCount >= 5.0) {
                groupedObserved.add(obs); groupedExpected.add(expCount)
                groupedStarts.add(groupStart); groupedEnds.add(i)
                obs = 0.0; expCount = 0.0; groupStart = i + 1
            }
        }
        if (expCount > 0.0) {
            if (groupedExpected.isEmpty()) {
                groupedObserved.add(obs); groupedExpected.add(expCount)
                groupedStarts.add(groupStart); groupedEnds.add(observed.lastIndex)
            } else {
                val last = groupedObserved.lastIndex
                groupedObserved[last] = groupedObserved[last] + obs
                groupedExpected[last] = groupedExpected[last] + expCount
                groupedEnds[last] = observed.lastIndex
            }
        }
        val statistic = groupedObserved.indices.sumOf { i ->
            val delta = groupedObserved[i] - groupedExpected[i]
            delta * delta / groupedExpected[i]
        }
        val df = (groupedObserved.size - 2).coerceAtLeast(1) // one fitted parameter plus sum constraint
        val details = groupedObserved.indices.map { i ->
            val start = groupedStarts[i]
            val end = groupedEnds[i]
            val label = when {
                start == end && end == 8 -> "8+"
                start == end -> start.toString()
                end == 8 -> "$start+"
                else -> "$start-$end"
            }
            "$label obs=${groupedObserved[i].toInt()} exp=${"%.2f".format(groupedExpected[i])}"
        }
        return Fit(lambda, groupedObserved.size, statistic, df, gammaQ(df / 2.0, statistic / 2.0), details)
    }

    private fun poissonPmf(k: Int, lambda: Double): Double {
        var factorial = 1.0
        for (i in 2..k) factorial *= i.toDouble()
        return exp(-lambda) * lambda.powInt(k) / factorial
    }

    private fun Double.powInt(power: Int): Double {
        var result = 1.0
        repeat(power) { result *= this }
        return result
    }

    // Regularized upper incomplete gamma Q(a,x), Numerical Recipes series/continued fraction.
    private fun gammaQ(a: Double, x: Double): Double {
        if (x <= 0.0) return 1.0
        val gln = lnGamma(a)
        if (x < a + 1.0) {
            var ap = a
            var sum = 1.0 / a
            var delta = sum
            repeat(10000) {
                ap += 1.0
                delta *= x / ap
                sum += delta
                if (kotlin.math.abs(delta) < kotlin.math.abs(sum) * 1e-14) return (1.0 - sum * exp(-x + a * ln(x) - gln)).coerceIn(0.0, 1.0)
            }
            return (1.0 - sum * exp(-x + a * ln(x) - gln)).coerceIn(0.0, 1.0)
        }
        val tiny = 1e-300
        var b = x + 1.0 - a
        var c = 1.0 / tiny
        var d = 1.0 / maxOf(kotlin.math.abs(b), tiny) * if (b < 0.0) -1.0 else 1.0
        var h = d
        for (i in 1..10000) {
            val an = -i.toDouble() * (i - a)
            b += 2.0
            d = an * d + b
            if (kotlin.math.abs(d) < tiny) d = tiny
            c = b + an / c
            if (kotlin.math.abs(c) < tiny) c = tiny
            d = 1.0 / d
            val change = d * c
            h *= change
            if (kotlin.math.abs(change - 1.0) < 1e-14) break
        }
        return (exp(-x + a * ln(x) - gln) * h).coerceIn(0.0, 1.0)
    }

    private fun lnGamma(z: Double): Double {
        val coefficients = doubleArrayOf(676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7)
        if (z < 0.5) return ln(Math.PI) - ln(kotlin.math.sin(Math.PI * z)) - lnGamma(1.0 - z)
        var x = 0.99999999999980993
        val shifted = z - 1.0
        coefficients.forEachIndexed { i, coefficient -> x += coefficient / (shifted + i + 1.0) }
        val t = shifted + coefficients.size - 0.5
        return 0.5 * ln(2.0 * Math.PI) + (shifted + 0.5) * ln(t) - t + ln(x)
    }
}
