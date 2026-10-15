package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferNegotiationTest {
    private fun player(id: Int, ovr: Int): Player = Player(
        id, "Test Player", 0, 24, Pos.CM, null, IntArray(NATTR) { 50 }, ovr, 50, 20, 50, 50, 50
    ).apply { this.ovr = ovr }

    private fun club(rep: Int): Club = Club(0, "Test Club", 0, rep, 10000, 0L, 0L)

    @Test fun interestLabelsReflectReputationGapAndProvideReason() {
        val p = player(1, 70)
        assertEquals("Interested", Transfers.interestLabel(club(60), p))
        assertEquals("Undecided", Transfers.interestLabel(club(30), p))
        assertEquals("Not interested", Transfers.interestLabel(club(10), p))
        assertEquals(true, Transfers.interestReason(club(60), p).isNotBlank())
    }
}
