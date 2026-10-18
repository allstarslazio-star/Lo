package com.nerdfm.game

import org.junit.Assert.*
import org.junit.Test

class WorldIntegrityTest {
    @Test fun validSmallWorldPassesWithoutMutation() {
        val world = World()
        val club = Club(0, "Fictional FC", 0, 50, 9000, 0L, 0L)
        world.clubs.add(club)
        world.leagues.add(League(0, "Fictional League", 0))
        world.humanClubId = 0
        val player = Player(1, "Alex Example", 0, 24, Pos.CM, null, IntArray(NATTR) { 10 }, 70, 12, 10, 12, 10, 10)
        player.clubId = 0
        club.players.add(player.id)
        world.players[player.id] = player
        val beforeBalance = club.balance
        assertTrue(WorldIntegrity.inspect(world).isEmpty())
        WorldIntegrity.requireValid(world)
        assertEquals(beforeBalance, club.balance)
    }

    @Test fun invalidMembershipAndAgeAreReported() {
        val world = World()
        val club = Club(0, "Fictional FC", 0, 50, 9000, 0L, 0L)
        world.clubs.add(club)
        world.humanClubId = 0
        val player = Player(1, "Alex Example", 0, 8, Pos.CM, null, IntArray(NATTR) { 10 }, 70, 12, 10, 12, 10, 10)
        player.clubId = 0
        world.players[player.id] = player
        val codes = WorldIntegrity.inspect(world).map { it.code }.toSet()
        assertTrue(codes.contains("player.age"))
        assertTrue(codes.contains("player.membership"))
    }

    @Test fun invalidFixtureScoreIsReported() {
        val world = World()
        world.clubs.add(Club(0, "Fictional A", 0, 50, 9000, 0L, 0L))
        world.clubs.add(Club(1, "Fictional B", 0, 50, 9000, 0L, 0L))
        val league = League(0, "Fictional League", 0)
        league.fixtures.add(Fixture(1, 0, 1).apply { played = true; hg = -1; ag = 2 })
        world.leagues.add(league)
        val codes = WorldIntegrity.inspect(world).map { it.code }
        assertTrue(codes.contains("fixture.score"))
    }
}
