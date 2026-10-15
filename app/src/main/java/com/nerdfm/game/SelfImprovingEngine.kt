package com.nerdfm.game

import java.io.Serializable
import kotlin.math.max
import kotlin.random.Random

/** Compact tabular Q learner. State keys are deliberately coarse to keep career saves bounded. */
enum class PlayerAction { SHORT_PASS, LONG_PASS, DRIBBLE, SHOOT, HOLD, PRESS }

class PlayerLearningProfile : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    private val q = HashMap<Int, FloatArray>()
    var decisionsObserved: Long = 0
        private set

    fun value(state: Int, action: PlayerAction): Float = q[state]?.get(action.ordinal) ?: 0f

    fun choose(state: Int, exploration: Float, random: Random, allowed: List<PlayerAction> = PlayerAction.values().toList()): PlayerAction {
        require(allowed.isNotEmpty())
        val values = q[state]
        if (random.nextFloat() < exploration.coerceIn(0f, 1f)) return allowed[random.nextInt(allowed.size)]
        val best = allowed.maxOf { values?.get(it.ordinal) ?: 0f }
        val ties = allowed.filter { (values?.get(it.ordinal) ?: 0f) >= best - 0.0001f }
        return ties[random.nextInt(ties.size)]
    }

    fun update(state: Int, action: PlayerAction, reward: Float, nextState: Int,
               learningRate: Float = 0.08f, discount: Float = 0.88f) {
        val current = q.getOrPut(state) { FloatArray(PlayerAction.values().size) }
        val future = q[nextState]?.maxOrNull() ?: 0f
        val target = reward.coerceIn(-1f, 1f) + discount.coerceIn(0f, 0.99f) * future
        current[action.ordinal] += learningRate.coerceIn(0.001f, 0.5f) * (target - current[action.ordinal])
        // Bound memory for long-running careers: only the most recent 512 state buckets survive.
        if (q.size > 512) q.keys.firstOrNull { it != state }?.let(q::remove)
        decisionsObserved++
    }
}

/** An evolvable manager strategy. All genes are bounded and safe to persist with a career. */
class ManagerGenome : Serializable {
    var pressing = 1f
    var line = 1f
    var tempo = 1f
    var width = 1f
    var directness = 1f
    var fitness = 0f
    var seasonsEvaluated = 0

    fun mutate(random: Random, strength: Float = 0.28f) {
        pressing = (pressing + gaussian(random) * strength).coerceIn(0f, 2f)
        line = (line + gaussian(random) * strength).coerceIn(0f, 2f)
        tempo = (tempo + gaussian(random) * strength).coerceIn(0f, 2f)
        width = (width + gaussian(random) * strength).coerceIn(0f, 2f)
        directness = (directness + gaussian(random) * strength).coerceIn(0f, 2f)
        fitness = 0f
    }

    fun copyGenesFrom(other: ManagerGenome) {
        pressing = other.pressing; line = other.line; tempo = other.tempo
        width = other.width; directness = other.directness
    }

    fun applyTo(tactics: Tactics) {
        tactics.pressing = pressing.toInt().coerceIn(0, 2)
        tactics.line = line.toInt().coerceIn(0, 2)
        tactics.tempo = tempo.toInt().coerceIn(0, 2)
        tactics.width = width.toInt().coerceIn(0, 2)
        tactics.passing = directness.toInt().coerceIn(0, 2)
    }

    companion object {
        private const val serialVersionUID: Long = 1L

        private fun gaussian(random: Random): Float {
            val u1 = max(1e-6f, random.nextFloat())
            val u2 = random.nextFloat()
            return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1.toDouble())) * kotlin.math.cos(2.0 * Math.PI * u2)).toFloat().coerceIn(-2.5f, 2.5f)
        }
    }
}

/**
 * Population-based training at season boundaries. Fitness rewards points and goal difference,
 * with a small tier-normalized term; the human manager is never modified by this routine.
 */
object ManagerPopulationTraining {
    fun evolve(world: World, random: Random) {
        for (league in world.leagues) {
            val table = league.sortedTable()
            if (table.isEmpty()) continue
            val byClub = table.mapIndexed { index, row ->
                val club = world.clubs[row.clubId]
                val genome = club.managerGenome
                val fitness = row.pts + row.gd * 0.22f + (table.size - index) * 0.35f
                genome.fitness = fitness
                genome.seasonsEvaluated++
                club to fitness
            }.sortedByDescending { it.second }
            val eliteCount = max(1, byClub.size / 4)
            val elites = byClub.take(eliteCount).map { it.first.managerGenome }
            for ((index, pair) in byClub.withIndex()) {
                val club = pair.first
                if (club.human) continue
                val genome = club.managerGenome
                if (index < eliteCount) {
                    genome.mutate(random, 0.08f)
                } else {
                    val parent = elites[random.nextInt(elites.size)]
                    genome.copyGenesFrom(parent)
                    genome.mutate(random, if (index >= byClub.size * 3 / 4) 0.42f else 0.24f)
                }
                // Small counter-tactic bias against the human's most-used settings. It is applied only to
                // the tactics actually played and is NOT written back into the stored genome; storing it
                // compounded every season and pushed every AI manager to the same extreme.
                val applied = if (world.humanTacticSamples > 0) {
                    val n = world.humanTacticSamples.toFloat()
                    ManagerGenome().also { g ->
                        g.copyGenesFrom(genome)
                        g.pressing = (g.pressing + (1f - world.humanPressingTotal / n) * 0.08f).coerceIn(0f, 2f)
                        g.line = (g.line + (1f - world.humanLineTotal / n) * 0.06f).coerceIn(0f, 2f)
                        g.tempo = (g.tempo + (1f - world.humanTempoTotal / n) * 0.04f).coerceIn(0f, 2f)
                    }
                } else genome
                applied.applyTo(club.tactics)
            }
        }
    }
}

/** Bounded online calibration against target aggregate statistics, never individual scorelines. */
class MatchCalibration : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var shotConversionMultiplier = 1f
    var chanceCreationMultiplier = 1f
    var fatigueMultiplier = 1f
    var batchesObserved = 0L
        private set
    private var sampleMatches = 0
    private var sampleGoals = 0
    private var sampleShots = 0

    /** Accumulate completed matches and only update after a sizeable sample. */
    fun recordMatch(goals: Int, shots: Int) {
        sampleMatches++
        sampleGoals += goals.coerceAtLeast(0)
        sampleShots += shots.coerceAtLeast(0)
        if (sampleMatches >= 64) {
            observe(CalibrationBatch(sampleMatches, sampleGoals.toFloat() / sampleMatches,
                if (sampleShots > 0) sampleGoals.toFloat() / sampleShots else 0f, 0.88f))
            sampleMatches = 0; sampleGoals = 0; sampleShots = 0
        }
    }

    fun observe(batch: CalibrationBatch, targets: CalibrationTargets = CalibrationTargets()) {
        if (batch.matches <= 0) return
        val lr = 0.025f
        // Small proportional updates are a gradient-like controller; bounds prevent runaway feedback.
        shotConversionMultiplier = (shotConversionMultiplier * (1f + lr * (targets.shotConversion - batch.shotConversion).coerceIn(-0.05f, 0.05f)))
            .coerceIn(0.78f, 1.22f)
        chanceCreationMultiplier = (chanceCreationMultiplier * (1f + lr * (targets.goalsPerMatch - batch.goalsPerMatch).coerceIn(-0.5f, 0.5f)))
            .coerceIn(0.82f, 1.18f)
        fatigueMultiplier = (fatigueMultiplier * (1f + lr * (batch.lateMatchOutputRatio - targets.lateMatchOutputRatio).coerceIn(-0.2f, 0.2f)))
            .coerceIn(0.85f, 1.15f)
        batchesObserved++
    }
}

data class CalibrationBatch(val matches: Int, val goalsPerMatch: Float, val shotConversion: Float, val lateMatchOutputRatio: Float)
data class CalibrationTargets(
    val goalsPerMatch: Float = 2.65f,
    val shotConversion: Float = 0.115f,
    val lateMatchOutputRatio: Float = 0.88f
)

/** Stable, quantized state encoding shared by match-time player policies. */
object PlayerLearningState {
    fun encode(zone: Int, condition: Float, pressure: Float, scoreDifference: Int): Int {
        val z = zone.coerceIn(0, 2)
        val fatigue = when { condition >= 75f -> 0; condition >= 45f -> 1; else -> 2 }
        val press = when { pressure < 45f -> 0; pressure < 75f -> 1; else -> 2 }
        val score = scoreDifference.coerceIn(-2, 2) + 2
        return (((z * 3 + fatigue) * 3 + press) * 5 + score).coerceIn(0, 134)
    }
}
