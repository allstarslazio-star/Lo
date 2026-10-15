package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PostPhase10FixesTest {
    @get:Rule val temp = TemporaryFolder()

    private fun player(id: Int = 1) = Player(id, "Fix Tester", 0, 24, Pos.ST, null, IntArray(NATTR) { 50 }, 70, 50, 20, 50, 50, 50)

    @Test fun developmentTrendUsesOvrChangeNotForm() {
        val p = player().apply { seasonStartOvr = 60; ovr = 63; form = 40f }
        assertEquals(3, p.developmentDelta())
        assertTrue(p.developmentTrend().startsWith("↑"))
        p.ovr = 60; p.lastDevDelta = -2
        assertEquals(-2, p.developmentDelta())
        assertTrue(p.developmentTrend().startsWith("↓"))
    }

    @Test fun per90AndAccuracyAreSafeForLowMinutes() {
        val p = player().apply { minutes = 45; seasonShots = 0 }
        assertEquals(0.0, p.per90(5.0), 0.0)
        assertEquals(0, p.shotAccuracyPct())
        p.minutes = 180; p.seasonShots = 10; p.seasonShotsOnTarget = 4
        assertEquals(1.0, p.per90(2.0), 1e-9)
        assertEquals(40, p.shotAccuracyPct())
    }

    @Test fun oldSaveScoutClubIdIsNormalisedWhenNoInterestPending() {
        val directory = temp.newFolder("scout-normalise")
        val world = World()
        world.players[1] = player(1).apply { scoutInterestClubId = 0; scoutInterestWeeks = 0 }
        world.players[2] = player(2).apply { scoutInterestClubId = 4; scoutInterestWeeks = 2 }
        assertTrue(Persistence.save(directory, world))
        val loaded = (Persistence.load(directory) as LoadResult.Loaded).world
        assertEquals(-1, loaded.players[1]!!.scoutInterestClubId)
        assertEquals(4, loaded.players[2]!!.scoutInterestClubId)
    }

    @Test fun undecidedPlayersNeedAWagePremium() {
        val w = WorldGen.generate(5L, "Wage Tester")
        val me = w.human()
        val p = w.players.values.first { it.clubId != me.id }
        me.rep = Transfers.requiredRep(p) - 10
        assertEquals("Undecided", Transfers.interestLabel(me, p))
        assertTrue(Transfers.requiredWage(me, p) > Transfers.wantedWage(me, p))
    }
}
