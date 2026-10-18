package com.nerdfm.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubModifiersTest {
    private fun f(channel: Channel, v: Float, source: String = "test") = Factor(source, channel, v)

    @Test fun noFactorsGiveExactlyNeutral() {
        val m = ClubModifiers.combine(emptyList())
        assertTrue(m.isNeutral)
        assertEquals(1f, m.recoveryMultiplier, 0f)
        assertTrue(ClubModifiers.combine(listOf(f(Channel.MORALE, 0f))).isNeutral)
    }

    @Test fun oneFactorIsClampedToItsPerFactorCap() {
        val m = ClubModifiers.combine(listOf(f(Channel.CONDITION, 50f), f(Channel.MORALE, -50f)))
        assertEquals(ModifierCaps.CONDITION_PER_FACTOR, m.condition, 0f)
        assertEquals(-ModifierCaps.MORALE_PER_FACTOR, m.morale, 0f)
        assertEquals(0f, m.sharpness, 0f)
    }

    @Test fun manyFactorsAreClampedToTheChannelTotal() {
        val many = List(50) { f(Channel.SHARPNESS, 100f, "s$it") } + List(50) { f(Channel.RECOVERY, -100f, "r$it") }
        val m = ClubModifiers.combine(many)
        assertEquals(ModifierCaps.SHARPNESS_TOTAL, m.sharpness, 0f)
        assertEquals(-ModifierCaps.RECOVERY_TOTAL, m.recovery, 0f)
        assertEquals(1f - ModifierCaps.RECOVERY_TOTAL, m.recoveryMultiplier, 1e-6f)
    }

    @Test fun positiveAndNegativeFactorsCancelBeforeTheTotalCap() {
        val m = ClubModifiers.combine(listOf(f(Channel.MORALE, 3f), f(Channel.MORALE, 3f), f(Channel.MORALE, -3f)))
        assertEquals(3f, m.morale, 1e-6f)
    }

    @Test fun nanIsIgnoredAndInfinityIsClamped() {
        val m = ClubModifiers.combine(listOf(
            f(Channel.CONDITION, Float.NaN), f(Channel.MORALE, Float.POSITIVE_INFINITY), f(Channel.SHARPNESS, Float.NEGATIVE_INFINITY)))
        assertEquals(0f, m.condition, 0f)
        assertEquals(ModifierCaps.MORALE_PER_FACTOR, m.morale, 0f)
        assertEquals(-ModifierCaps.SHARPNESS_PER_FACTOR, m.sharpness, 0f)
    }

    @Test fun randomFactorSetsNeverExceedAnyCap() {
        val rnd = Random(20261016)
        repeat(20_000) {
            val factors = List(rnd.nextInt(0, 40)) {
                Factor("f$it", Channel.values()[rnd.nextInt(Channel.values().size)], (rnd.nextFloat() - 0.5f) * 1000f)
            }
            val m = ClubModifiers.combine(factors)
            assertTrue(Math.abs(m.condition) <= ModifierCaps.CONDITION_TOTAL)
            assertTrue(Math.abs(m.sharpness) <= ModifierCaps.SHARPNESS_TOTAL)
            assertTrue(Math.abs(m.morale) <= ModifierCaps.MORALE_TOTAL)
            assertTrue(Math.abs(m.recovery) <= ModifierCaps.RECOVERY_TOTAL)
            assertTrue(m.recoveryMultiplier in 0.92f..1.08f)
        }
    }

    @Test fun worstCombinedEffectivenessSwingStaysUnderTheCapAndIsNotVacuous() {
        val swing = ClubModifiers.worstCaseSwing()
        assertTrue("worst swing $swing exceeds ${ModifierCaps.MAX_EFFECTIVENESS_SWING}", swing <= ModifierCaps.MAX_EFFECTIVENESS_SWING)
        assertTrue("caps should allow a real effect, got $swing", swing > 0.01f)
    }

    @Test fun effectivenessMirrorMatchesTheEngineFormulaAtItsAnchors() {
        // MatchEngine: e = cf*mf*ff*sf with cf=.62+.38c/100, mf=.95+.10m/100, ff=.97+.06f/100, sf=.88+.12s/100
        assertEquals(1.05f * 1.03f, ClubModifiers.engineEffectiveness(100f, 100f, 100f, 100f), 1e-6f)
        assertEquals(0.62f * 0.95f * 0.97f * 0.88f, ClubModifiers.engineEffectiveness(0f, 0f, 0f, 0f), 1e-6f)
        assertEquals((0.62f + 0.38f) * (0.95f + 0.07f) * (0.97f + 0.036f) * (0.88f + 0.12f * 0.9f),
            ClubModifiers.engineEffectiveness(100f, 70f, 90f, 60f), 1e-5f)
    }
}
