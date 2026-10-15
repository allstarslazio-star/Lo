package com.nerdfm.game

/**
 * A compact, deterministic tactical policy for AI-controlled teams.
 *
 * The policy reads match state and returns effective settings only; it never mutates the
 * manager's saved Tactics. This keeps between-match preferences intact and makes the
 * decision rules independently testable. Randomness remains in MatchEngine's action model,
 * not in the manager's decision policy.
 */
data class MatchTacticalContext(
    val half: Int,
    val minuteInHalf: Int,
    val goalDifference: Int,
    val ownRedCards: Int,
    val opponentRedCards: Int,
    val averageCondition: Float,
    val baseMentality: Int,
    val basePressing: Int,
    val baseTempo: Int,
    val baseLine: Int,
    val baseWidth: Int,
    val basePassing: Int,
    /** Attack strengths from the AI team's left, centre and right lanes. */
    val attackByLane: List<Float>,
    /** Defensive strengths from the opponent's left, centre and right lanes. */
    val opponentDefenceByLane: List<Float>,
    /** Previous effective width, used as hysteresis so the AI does not flip-flop. */
    val previousWidth: Int = baseWidth
)

data class EffectiveMatchTactics(
    val mentality: Int,
    val pressing: Int,
    val tempo: Int,
    val line: Int,
    val width: Int,
    val passing: Int
)

object MatchTacticalAI {
    fun decide(c: MatchTacticalContext): EffectiveMatchTactics {
        require(c.half in 1..2) { "half must be 1 or 2" }
        require(c.minuteInHalf >= 0) { "minuteInHalf must be non-negative" }
        require(c.attackByLane.size == 3 && c.opponentDefenceByLane.size == 3) {
            "Lane strengths must contain left, centre and right values"
        }

        var mentality = c.baseMentality.coerceIn(-2, 2)
        var pressing = c.basePressing.coerceIn(0, 2)
        var tempo = c.baseTempo.coerceIn(0, 2)
        var line = c.baseLine.coerceIn(0, 2)
        var width = c.previousWidth.coerceIn(0, 2)
        var passing = c.basePassing.coerceIn(0, 2)

        val late = c.half == 2 && c.minuteInHalf >= 25 // 70 minutes elapsed
        val closing = c.half == 2 && c.minuteInHalf >= 30 // 75 minutes elapsed
        val condition = c.averageCondition.coerceIn(0f, 100f)

        // Match-state policy: the AI takes more risk when behind and protects space when ahead.
        if (late && c.goalDifference < 0) {
            mentality += minOf(2, -c.goalDifference)
            passing = 2 // progress the ball earlier; this increases turnover risk in the engine
            line = maxOf(line, 1)
            if (condition >= 48f) {
                tempo = 2
                pressing = maxOf(pressing, 1)
            } else {
                // A depleted team cannot sustain a full press just because it is losing.
                pressing = minOf(pressing, 1)
                tempo = maxOf(tempo, 1)
            }
            if (closing) line = 2
        } else if (closing && c.goalDifference > 0) {
            mentality -= 1
            line = 0
            passing = 0 // retain possession rather than repeatedly forcing attacks
            tempo = if (condition < 65f) 0 else minOf(tempo, 1)
            pressing = if (condition < 58f) 0 else minOf(pressing, 1)
        } else if (late && c.goalDifference == 0 && condition >= 62f && c.ownRedCards <= c.opponentRedCards) {
            // A fit team can make a modest late push without treating a draw like a crisis.
            mentality += 1
        }

        // A red-card disadvantage encourages a more conservative shape. A numerical advantage
        // permits a little more initiative, but never overrides a late lead-protection plan.
        if (c.ownRedCards > c.opponentRedCards) {
            mentality -= 1
            line = minOf(line, 1)
            pressing = minOf(pressing, 1)
        } else if (c.ownRedCards < c.opponentRedCards && !(closing && c.goalDifference > 0)) {
            mentality += 1
        }

        // Width is chosen from the matchup, not a blanket bonus: compare both wide channels
        // with the central channel. A modest threshold prevents twitchy changes on near ties.
        val leftThreat = c.attackByLane[0].coerceAtLeast(1f) /
            c.opponentDefenceByLane[2].coerceAtLeast(20f)
        val centreThreat = c.attackByLane[1].coerceAtLeast(1f) /
            c.opponentDefenceByLane[1].coerceAtLeast(20f)
        val rightThreat = c.attackByLane[2].coerceAtLeast(1f) /
            c.opponentDefenceByLane[0].coerceAtLeast(20f)
        val wideThreat = (leftThreat + rightThreat) / 2f
        val wideSwitchThreshold = if (width == 0) 1.25f else 1.12f
        val narrowSwitchThreshold = if (width == 2) 1.25f else 1.12f
        if (wideThreat > centreThreat * wideSwitchThreshold) width = 2
        else if (centreThreat > wideThreat * narrowSwitchThreshold) width = 0

        return EffectiveMatchTactics(
            mentality = mentality.coerceIn(-3, 3),
            pressing = pressing.coerceIn(0, 2),
            tempo = tempo.coerceIn(0, 2),
            line = line.coerceIn(0, 2),
            width = width.coerceIn(0, 2),
            passing = passing.coerceIn(0, 2)
        )
    }
}
