package com.nerdfm.game

import org.junit.Assert.*
import org.junit.Test

class PlayerRelationsTest {
    @Test fun personalityIsDeterministicFromExistingPlayerTraits() {
        val p = player(1, ambition = 18, loyalty = 4)
        assertEquals("Driven", PlayerRelations.personality(p))
        assertEquals(PlayerRelations.personality(p), PlayerRelations.personality(p))
    }

    @Test fun promisesRejectInvalidPlayersAndSetEightWeekTerm() {
        val w = World()
        val club = Club(0, "Fictional FC", 0, 50, 10000, 0L, 0L).apply { human = true }
        w.clubs.add(club); w.humanClubId = 0
        val p = player(1).apply { clubId = 0 }
        w.players[p.id] = p; club.players.add(p.id)
        assertFalse(PlayerRelations.promisePlayingTime(w, p.id, 99))
        assertTrue(PlayerRelations.promisePlayingTime(w, p.id, PlayerRelations.PROMISE_IMPORTANT))
        assertEquals(8, p.promiseWeeksRemaining)
        assertEquals(PlayerRelations.PROMISE_IMPORTANT, p.playingTimePromise)
    }

    @Test fun mediaInteractionsAreBoundedAndDeterministic() {
        val w = World()
        w.clubs.add(Club(0, "Fictional FC", 0, 50, 10000, 0L, 0L))
        w.humanClubId = 0
        assertFalse(PlayerRelations.mediaInteraction(w, 5))
        assertTrue(PlayerRelations.mediaInteraction(w, 1))
        assertEquals(56.5f, w.human().teamCohesion, 0.001f)
        assertTrue(w.managerReputation in 0f..100f)
        assertEquals("Media briefing", w.news.first().title)
    }

    @Test fun weeklyUpdateMaintainsCohesionAndPromiseBounds() {
        val w = World()
        val c = Club(0, "Fictional FC", 0, 50, 10000, 0L, 0L)
        w.clubs.add(c); w.humanClubId = 0
        val p = player(1).apply {
            clubId = 0; morale = 80f; playingTimePromise = PlayerRelations.PROMISE_STAR
            promiseWeeksRemaining = 4; lastMinutes = 0
        }
        w.players[p.id] = p; c.players.add(p.id)
        PlayerRelations.weeklyUpdate(w)
        assertTrue(c.teamCohesion in 0f..100f)
        assertEquals(3, p.promiseWeeksRemaining)
        assertEquals(1, p.promiseBrokenWeeks)
        assertTrue(p.morale < 80f)
    }

    private fun player(id: Int, ambition: Int = 10, loyalty: Int = 10) = Player(id, "Fictional Player $id", 0, 24, Pos.CM, null,
        IntArray(NATTR) { 10 }, 70, 12, 10, 12, ambition, loyalty).apply { refresh() }
}
