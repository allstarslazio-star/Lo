package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoutingInterestTest {
    @Test fun scoutingInterestHasBoundedPendingWindow() {
        val player = Player(1, "Scouted Player", 0, 22, Pos.ST, null, IntArray(NATTR) { 50 }, 70, 50, 20, 50, 50, 50, 50)
        player.scoutInterestClubId = 3
        player.scoutInterestWeeks = 2
        assertEquals(3, player.scoutInterestClubId)
        assertEquals(2, player.scoutInterestWeeks)
        player.scoutInterestWeeks--
        assertEquals(1, player.scoutInterestWeeks)
    }
}
