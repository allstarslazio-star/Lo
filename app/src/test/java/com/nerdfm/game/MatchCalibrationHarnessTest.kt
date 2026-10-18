package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCalibrationHarnessTest {
    @Test fun seededCalibrationCampaignIsRepeatable() {
        val first = MatchCalibrationHarness.run(matches = 12, firstSeed = 9000L)
        val second = MatchCalibrationHarness.run(matches = 12, firstSeed = 9000L)
        assertTrue(first.errorCount == 0)
        assertEquals(12, first.matches.size)
        assertTrue(first.homePossession.mean in 0.0..100.0)
        assertTrue(first.totalYellowCards.mean >= 0.0)
        assertTrue(first.totalRedCards.mean >= 0.0)
        assertTrue(MatchReportDigest.sameResults(first, second))
    }

    @Test fun fiveThousandSeededMatchesMeetInitialScorelineTargets() {
        val report = MatchCalibrationHarness.run()
        assertTrue("goals/match=${report.goals.mean}", report.goals.mean in 2.5..2.9)
        assertTrue("home win rate=${report.homeWinRate}", report.homeWinRate in 0.43..0.47)
        assertTrue("draw rate=${report.drawRate}", report.drawRate in 0.23..0.27)
        assertTrue("maximum team score=${report.maximumGoalsByOneTeam}", report.maximumGoalsByOneTeam <= 9)
        assertEquals(0, report.errorCount)
    }

    @Test fun calibrationCampaignRejectsEmptySample() {
        try {
            MatchCalibrationHarness.run(matches = 0)
            throw AssertionError("Expected an empty calibration sample to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected validation behaviour.
        }
    }
}
