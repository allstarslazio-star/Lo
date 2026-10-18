package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchConditionsTest {
    @Test fun fixtureConditionsAreDeterministicAndBounded() {
        repeat(500) { i ->
            val first = MatchConditions.forSeed(i.toLong())
            assertEquals(first, MatchConditions.forSeed(i.toLong()))
            assertTrue(first.passingMultiplier in 0.80f..1.01f)
            assertTrue(first.shootingMultiplier in 0.85f..1.01f)
            assertTrue(first.injuryMultiplier in 1.0f..1.42f)
            assertTrue(first.summary.isNotBlank())
        }
    }

    @Test fun normalConditionsDoNotChangeBaselineMultipliers() {
        val normal = MatchConditions.NORMAL
        assertEquals(1f, normal.passingMultiplier)
        assertEquals(1f, normal.shootingMultiplier)
        assertEquals(1f, normal.injuryMultiplier)
    }
}
