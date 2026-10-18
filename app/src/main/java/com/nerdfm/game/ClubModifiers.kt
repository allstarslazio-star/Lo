package com.nerdfm.game

import kotlin.math.abs
import kotlin.math.max

/**
 * Every cap of the Club Ecosystem layer lives here. The match engine is frozen, so ecosystem systems may only
 * nudge inputs the engine already reads (see [Channel]). Units are Player points, i.e. the same 0..100 scales
 * Player.condition / sharpness / morale use.
 *
 * Engine sensitivity (MatchEngine, per-player effectiveness e = cf * mf * ff * sharpnessFactor * dayForm):
 *   cf = 0.62 + 0.38 * condition / 100   -> 1 condition point  ~ 0.38% of e at full fitness
 *   mf = 0.95 + 0.10 * morale / 100      -> 1 morale point     ~ 0.10%
 *   sharpnessFactor = 0.88 + 0.12 * sharpness / 100 -> 1 point ~ 0.12%
 * The caps below keep the worst combined swing under [MAX_EFFECTIVENESS_SWING]; WorldBaselineHarness and
 * ClubModifiersTest prove that numerically.
 */
object ModifierCaps {
    const val CONDITION_PER_FACTOR = 2.5f
    const val CONDITION_TOTAL = 4f
    const val SHARPNESS_PER_FACTOR = 2.5f
    const val SHARPNESS_TOTAL = 4f
    const val MORALE_PER_FACTOR = 3f
    const val MORALE_TOTAL = 5f
    /** Weekly condition recovery is scaled by (1 + recovery); a factor shifts the multiplier by at most this much. */
    const val RECOVERY_PER_FACTOR = 0.04f
    const val RECOVERY_TOTAL = 0.08f

    /** Largest allowed relative change of one player's match effectiveness from ALL channels together. */
    const val MAX_EFFECTIVENESS_SWING = 0.035f

    /** Lowest condition for which MAX_EFFECTIVENESS_SWING is guaranteed; exhausted players barely play. */
    const val SWING_GUARANTEE_MIN_CONDITION = 40f
}

/** The inputs a factor may touch. Each has its own per-factor and combined cap. */
enum class Channel(val perFactorCap: Float, val totalCap: Float) {
    CONDITION(ModifierCaps.CONDITION_PER_FACTOR, ModifierCaps.CONDITION_TOTAL),
    SHARPNESS(ModifierCaps.SHARPNESS_PER_FACTOR, ModifierCaps.SHARPNESS_TOTAL),
    MORALE(ModifierCaps.MORALE_PER_FACTOR, ModifierCaps.MORALE_TOTAL),
    RECOVERY(ModifierCaps.RECOVERY_PER_FACTOR, ModifierCaps.RECOVERY_TOTAL)
}

/** One named contribution from one ecosystem system (a staff member, the pitch, a language barrier, ...). */
class Factor(val source: String, val channel: Channel, val value: Float)

/**
 * The bounded output for one club and one match (or week). Build it only through [combine], which clamps every
 * factor and then the sum of each channel, so the caps hold however many factors exist.
 */
class ClubModifiers private constructor(
    val condition: Float,
    val sharpness: Float,
    val morale: Float,
    /** Additive change of the weekly recovery multiplier, in -0.08..+0.08. */
    val recovery: Float
) {
    val recoveryMultiplier: Float get() = 1f + recovery
    val isNeutral: Boolean get() = condition == 0f && sharpness == 0f && morale == 0f && recovery == 0f

    companion object {
        val NEUTRAL = ClubModifiers(0f, 0f, 0f, 0f)

        fun combine(factors: List<Factor>): ClubModifiers {
            if (factors.isEmpty()) return NEUTRAL
            val sums = FloatArray(Channel.values().size)
            for (f in factors) {
                val v = if (f.value.isNaN()) 0f else f.value
                val cap = f.channel.perFactorCap
                sums[f.channel.ordinal] += v.coerceIn(-cap, cap)
            }
            fun total(c: Channel): Float = sums[c.ordinal].coerceIn(-c.totalCap, c.totalCap)
            return ClubModifiers(total(Channel.CONDITION), total(Channel.SHARPNESS), total(Channel.MORALE), total(Channel.RECOVERY))
        }

        /** Mirror of the engine's per-player effectiveness (form and day form held at their means). */
        fun engineEffectiveness(condition: Float, morale: Float, sharpness: Float, form: Float = 60f): Float {
            val cf = 0.62f + 0.38f * max(0f, condition) / 100f
            val mf = 0.95f + 0.10f * morale / 100f
            val ff = 0.97f + 0.06f * form / 100f
            val sf = 0.88f + 0.12f * (sharpness / 100f)
            return cf * mf * ff * sf
        }

        /**
         * Largest relative change of engineEffectiveness reachable with the combined caps, over every
         * condition >= SWING_GUARANTEE_MIN_CONDITION, morale and sharpness the game allows. Used by the cap proof test.
         */
        fun worstCaseSwing(): Float {
            var worst = 0f
            var cond = ModifierCaps.SWING_GUARANTEE_MIN_CONDITION
            while (cond <= 100f) {
                var morale = 15f
                while (morale <= 100f) {
                    var sharp = 20f
                    while (sharp <= 100f) {
                        val base = engineEffectiveness(cond, morale, sharp)
                        for (sign in intArrayOf(-1, 1)) {
                            val c = (cond + sign * ModifierCaps.CONDITION_TOTAL).coerceIn(0f, 100f)
                            val m = (morale + sign * ModifierCaps.MORALE_TOTAL).coerceIn(15f, 100f)
                            val s = (sharp + sign * ModifierCaps.SHARPNESS_TOTAL).coerceIn(20f, 100f)
                            worst = max(worst, abs(engineEffectiveness(c, m, s) / base - 1f))
                        }
                        sharp += 10f
                    }
                    morale += 10f
                }
                cond += 5f
            }
            return worst
        }
    }
}
