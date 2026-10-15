package com.nerdfm.game

import org.junit.Assert.assertTrue
import org.junit.Test

class WageBudgetTest {
    @Test fun wageBudgetLimitsArePositiveAndRespondToClubFinances() {
        val world = World()
        val club = Club(0, "Test", 0, 50, 10000, 0L, 0L).apply { revenue = 52_000_000L; balance = 0L }
        val normal = world.wageBudgetLimits(club)
        assertTrue(normal.first >= 1_000L)
        assertTrue(normal.last >= normal.first)
        club.balance = -1L
        val distressed = world.wageBudgetLimits(club)
        assertTrue(distressed.last < normal.last)
    }
}
