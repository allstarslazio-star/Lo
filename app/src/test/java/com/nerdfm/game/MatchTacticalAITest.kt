package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchTacticalAITest {
    private fun context(
        minute: Int = 30,
        difference: Int = -1,
        condition: Float = 75f,
        ownReds: Int = 0,
        opponentReds: Int = 0,
        attack: List<Float> = listOf(60f, 60f, 60f),
        defence: List<Float> = listOf(60f, 60f, 60f)
    ) = MatchTacticalContext(
        half = 2,
        minuteInHalf = minute,
        goalDifference = difference,
        ownRedCards = ownReds,
        opponentRedCards = opponentReds,
        averageCondition = condition,
        baseMentality = 0,
        basePressing = 1,
        baseTempo = 1,
        baseLine = 1,
        baseWidth = 1,
        basePassing = 1,
        attackByLane = attack,
        opponentDefenceByLane = defence
    )

    @Test fun teamChasingLateUsesMoreDirectAndFasterPlayWhenFit() {
        val result = MatchTacticalAI.decide(context(difference = -1, condition = 80f))
        assertEquals(2, result.passing)
        assertEquals(2, result.tempo)
        assertEquals(2, result.line)
        assertTrue(result.mentality > 0)
    }

    @Test fun exhaustedTeamDoesNotAutomaticallyUseMaximumPressingWhenBehind() {
        val result = MatchTacticalAI.decide(context(difference = -2, condition = 35f))
        assertTrue(result.pressing <= 1)
        assertEquals(2, result.passing)
    }

    @Test fun teamProtectingLateLeadSlowsDownAndDropsLine() {
        val result = MatchTacticalAI.decide(context(difference = 1, condition = 70f))
        assertEquals(0, result.line)
        assertEquals(0, result.passing)
        assertTrue(result.mentality < 0)
    }

    @Test fun numericalDisadvantageRestrainsPressingAndLine() {
        val result = MatchTacticalAI.decide(context(difference = 0, ownReds = 1, opponentReds = 0))
        assertTrue(result.pressing <= 1)
        assertTrue(result.line <= 1)
        assertTrue(result.mentality < 0)
    }

    @Test fun aiTargetsStrongWideMatchup() {
        val result = MatchTacticalAI.decide(
            context(attack = listOf(85f, 35f, 85f), defence = listOf(30f, 65f, 30f))
        )
        assertEquals(2, result.width)
    }

    @Test fun aiTargetsStrongCentralMatchup() {
        val result = MatchTacticalAI.decide(
            context(attack = listOf(35f, 85f, 35f), defence = listOf(65f, 30f, 65f))
        )
        assertEquals(0, result.width)
    }

    @Test fun nearlyEvenLaneMatchupKeepsManagerWidth() {
        val result = MatchTacticalAI.decide(
            context(attack = listOf(60f, 60f, 60f), defence = listOf(60f, 60f, 60f))
        )
        assertEquals(1, result.width)
    }

    @Test fun widthHysteresisPreventsAWeakSignalFromUndoingTheLastDecision() {
        val balanced = MatchTacticalAI.decide(
            context(attack = listOf(60f, 60f, 60f), defence = listOf(60f, 60f, 60f))
                .copy(previousWidth = 2)
        )
        assertEquals(2, balanced.width)
    }

    @Test fun outputAlwaysStaysInsideEngineRanges() {
        val result = MatchTacticalAI.decide(
            context(difference = -9, condition = 100f, ownReds = 0, opponentReds = 8)
        )
        assertTrue(result.mentality in -3..3)
        assertTrue(result.pressing in 0..2)
        assertTrue(result.tempo in 0..2)
        assertTrue(result.line in 0..2)
        assertTrue(result.width in 0..2)
        assertTrue(result.passing in 0..2)
    }
}
