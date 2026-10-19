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

class TransferScreenPerformanceTest {
    @Test fun theTransferSearchOverEveryPlayerIsFastAndGivesTheSameListAsTheSlowWay() {
        val w = EcoTestSupport.world(53L, 1)
        val me = w.human()
        fun slow() = w.players.values.filter { p -> p.clubId >= 0 && p.clubId != me.id && p.age <= 34 && shownOvr(w, p) >= 45 }
            .sortedByDescending { shownOvr(w, it) }.take(60)
        val s0 = System.nanoTime()
        val reference = slow().map { it.id }
        val slowMs = (System.nanoTime() - s0) / 1_000_000L
        val t0 = System.nanoTime()
        val ovr = shownOvrFn(w)
        val fast = w.players.values.filter { p -> p.clubId >= 0 && p.clubId != me.id && p.age <= 34 }
            .map { it to ovr(it) }.filter { it.second >= 45 }.sortedByDescending { it.second }.take(60).map { it.first.id }
        val ms = (System.nanoTime() - t0) / 1_000_000L
        println("transfer search: old way $slowMs ms, new way $ms ms on this machine")
        assertTrue("took $ms ms", ms < 400L)
        assertTrue(fast.toSet() == reference.toSet() || fast.map { ovr(w.players[it]!!) } == reference.map { ovr(w.players[it]!!) })
    }

    @Test fun displayedOverallIsIdenticalToTheOldPerAttributeComputation() {
        val w = EcoTestSupport.world(54L, 2)
        val ovr = shownOvrFn(w)
        for (p in w.players.values.take(1500)) assertTrue(ovr(p) == shownOvr(w, p))
    }
}
