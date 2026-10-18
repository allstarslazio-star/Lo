package com.nerdfm.game

import org.junit.Assert.assertTrue
import org.junit.Test

/** Everything the Transfers screen computes per row, run for every player, in every window, so a bad index cannot crash the screen. */
class TransferScreenDataTest {
    @Test fun everyRowValueCanBeComputedForEveryPlayerInEveryWeek() {
        for (week in listOf(1, 6, 20, 34)) {
            val w = EcoTestSupport.world(51L, week)
            val me = w.human()
            var n = 0
            for (p in w.players.values) {
                AvatarSpec.of(p.id, p.nation, p.age, 0)
                Flags.design(p.nation)
                p.value(); p.ovr; p.pos.label
                if (p.clubId >= 0) { w.clubs[p.clubId].name; Transfers.askingPrice(w, p, me) }
                n++
            }
            w.windowText(); w.isWindowOpen()
            assertTrue(n > 1000)
        }
    }

    @Test fun filtersStoredInASaveAreAlwaysInsideTheSliderRanges() {
        val w = EcoTestSupport.world(52L, 3)
        assertTrue("min ovr ${w.transferFilterMinOverall}", w.transferFilterMinOverall in 20..90)
        assertTrue("max age ${w.transferFilterMaxAge}", w.transferFilterMaxAge in 17..38)
        assertTrue(w.transferFilterLeague in 0..7)
        assertTrue(w.transferFilterPosition in 0..Pos.values().size)
        assertTrue(w.transferSearchTab in 0..3)
    }
}
