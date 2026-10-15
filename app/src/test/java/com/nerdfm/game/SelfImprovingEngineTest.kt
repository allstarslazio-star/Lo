package com.nerdfm.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovingEngineTest {
    @Test fun qLearningMovesTowardPositiveReward() {
        val profile = PlayerLearningProfile()
        repeat(30) { profile.update(7, PlayerAction.SHORT_PASS, 1f, 7, discount = 0f) }
        assertTrue(profile.value(7, PlayerAction.SHORT_PASS) > 0.5f)
        assertTrue(profile.decisionsObserved == 30L)
    }

    @Test fun stateEncodingIsStableAndBounded() {
        assertEquals(PlayerLearningState.encode(0, 100f, 20f, -2), PlayerLearningState.encode(-5, 120f, -3f, -8))
        assertTrue(PlayerLearningState.encode(9, 0f, 100f, 9) in 0..134)
    }

    @Test fun genomeMutationNeverLeavesSafeBounds() {
        val genome = ManagerGenome()
        repeat(500) { genome.mutate(Random(it.toLong()), 2f) }
        assertTrue(genome.pressing in 0f..2f)
        assertTrue(genome.line in 0f..2f)
        assertTrue(genome.tempo in 0f..2f)
        assertTrue(genome.width in 0f..2f)
        assertTrue(genome.directness in 0f..2f)
    }

    @Test fun calibrationRemainsBoundedUnderExtremeSamples() {
        val calibration = MatchCalibration()
        repeat(200) { calibration.observe(CalibrationBatch(100, 9f, 0.8f, 0.1f)) }
        assertTrue(calibration.chanceCreationMultiplier in 0.82f..1.18f)
        assertTrue(calibration.shotConversionMultiplier in 0.78f..1.22f)
        assertTrue(calibration.fatigueMultiplier in 0.85f..1.15f)
    }

    @Test fun shootingAndPressingActionsLearnFromBoundedRewardsAcrossTwoHundredSeasons() {
        val profile = PlayerLearningProfile()
        val genome = ManagerGenome()
        val random = Random(20261015)
        repeat(200) { season ->
            repeat(20) { match ->
                val state = PlayerLearningState.encode(match % 3, (100 - match).toFloat(), (match % 3 * 30).toFloat(), (season % 5) - 2)
                profile.update(state, PlayerAction.SHOOT, if (match % 4 == 0) 0.75f else -0.2f, state)
                profile.update(state, PlayerAction.PRESS, if (match % 2 == 0) 0.2f else -0.08f, state)
            }
            genome.mutate(random, 0.08f)
            assertTrue(genome.pressing in 0f..2f)
            assertTrue(genome.line in 0f..2f)
            assertTrue(genome.tempo in 0f..2f)
        }
        assertTrue(profile.decisionsObserved == 8_000L)
        assertTrue(profile.value(0, PlayerAction.SHOOT) in -1f..1f)
        assertTrue(profile.value(0, PlayerAction.PRESS) in -1f..1f)
    }

    @Test fun calibrationWaitsForLargeMatchBatch() {
        val calibration = MatchCalibration()
        repeat(63) { calibration.recordMatch(3, 20) }
        assertEquals(0L, calibration.batchesObserved)
        calibration.recordMatch(3, 20)
        assertEquals(1L, calibration.batchesObserved)
    }
}
