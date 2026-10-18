package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferAndFixtureSafetyTest {
    private fun club(id: Int, name: String) = Club(id, name, 0, 50, 10000, 0L, 1L).apply {
        revenue = 1_000_000L
        balance = 100_000L
        transferBudget = 50_000L
    }

    @Test fun recordingSameFixtureTwiceDoesNotDuplicateTableOrGateRevenue() {
        val world = World()
        val home = club(0, "Home")
        val away = club(1, "Away")
        world.clubs.add(home)
        world.clubs.add(away)
        val league = League(0, "Test League", 0)
        league.clubIds.addAll(listOf(0, 1))
        league.table[0] = TableRow(0)
        league.table[1] = TableRow(1)
        val fixture = Fixture(1, 0, 1)

        Game.record(world, league, fixture, 2, 0)
        val balanceAfterFirstRecord = home.balance
        Game.record(world, league, fixture, 2, 0)

        assertTrue(fixture.played)
        assertEquals(1, league.table[0]!!.p)
        assertEquals(1, league.table[1]!!.p)
        assertEquals(3, league.table[0]!!.pts)
        assertEquals(0, league.table[1]!!.pts)
        assertEquals(balanceAfterFirstRecord, home.balance)
    }


    @Test fun freeAgentSigningCompletesAndAddsPlayerToSquad() {
        val world = World()
        val manager = club(0, "Manager Club").apply {
            human = true
            rep = 70
            wageBudget = 100_000L
            transferBudget = 100_000L
            balance = 1_000_000L
        }
        world.clubs.add(manager)
        world.humanClubId = manager.id
        val player = Player(9, "Free Agent", 0, 24, Pos.CM, null, IntArray(NATTR) { 12 }, 60, 55, 10, 55, 55, 55)
        player.refresh()
        world.players[player.id] = player
        val wage = Transfers.wantedWage(manager, player)

        val result = Transfers.completeSigning(world, player, 0L, wage, 3)

        assertTrue(result.first)
        assertEquals(manager.id, player.clubId)
        assertTrue(manager.players.contains(player.id))
        assertEquals(wage, player.wage)
        assertTrue(result.second.contains("has signed", ignoreCase = true))
    }

    @Test fun freeAgentSigningRejectsUnderpaidContractWithoutMovingPlayer() {
        val world = World()
        val manager = club(0, "Manager Club").apply {
            human = true
            rep = 70
            wageBudget = 100_000L
            balance = 1_000_000L
        }
        world.clubs.add(manager)
        world.humanClubId = manager.id
        val player = Player(10, "Free Agent", 0, 24, Pos.CM, null, IntArray(NATTR) { 12 }, 60, 55, 10, 55, 55, 55)
        player.refresh()
        world.players[player.id] = player
        val requiredWage = Transfers.wantedWage(manager, player)

        val result = Transfers.completeSigning(world, player, 0L, requiredWage - 1, 3)

        assertFalse(result.first)
        assertEquals(-1, player.clubId)
        assertFalse(manager.players.contains(player.id))
        assertTrue(result.second.contains("at least", ignoreCase = true))
    }

    @Test fun staleOfferCannotBeAcceptedTwice() {
        val world = World()
        val seller = club(0, "Seller")
        val buyer = club(1, "Buyer")
        seller.human = true
        world.clubs.add(seller)
        world.clubs.add(buyer)
        world.humanClubId = 0
        val player = Player(1, "Test Striker", 0, 24, Pos.ST, null, IntArray(NATTR) { 10 }, 50, 50, 10, 50, 50, 50)
        player.clubId = 0
        world.players[player.id] = player
        seller.players.add(player.id)
        val offer = Offer(player.id, buyer.id, 10_000L, 4)
        world.offers.add(offer)

        world.offers.remove(offer)
        val message = Transfers.acceptOffer(world, offer)

        assertTrue(message.contains("no longer active", ignoreCase = true))
        assertEquals(0, buyer.players.size)
        assertEquals(0, player.clubId)
    }

    @Test fun buyerThatCanNoLongerAffordOfferDoesNotReceivePlayer() {
        val world = World()
        val seller = club(0, "Seller")
        val buyer = club(1, "Buyer").apply { balance = 0L; transferBudget = 0L }
        seller.human = true
        world.clubs.add(seller)
        world.clubs.add(buyer)
        world.humanClubId = 0
        val player = Player(1, "Test Striker", 0, 24, Pos.ST, null, IntArray(NATTR) { 10 }, 50, 50, 10, 50, 50, 50)
        player.clubId = 0
        world.players[player.id] = player
        seller.players.add(player.id)
        val offer = Offer(player.id, buyer.id, 10_000L, 4)
        world.offers.add(offer)

        val message = Transfers.acceptOffer(world, offer)

        assertTrue(message.contains("afford", ignoreCase = true))
        assertEquals(0, player.clubId)
        assertFalse(buyer.players.contains(player.id))
        assertTrue(world.offers.contains(offer))
    }
}
