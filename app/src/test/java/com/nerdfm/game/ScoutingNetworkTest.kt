package com.nerdfm.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ScoutingNetworkTest {
    private fun world(): World = BusinessTestSupport.richWorld(31L)

    @Test fun marketFillsAndHiringRespectsCapacity() {
        val w = world()
        val me = w.human()
        me.scout = 0
        ScoutingNetwork.refreshMarket(w, Random(1))
        assertEquals(ScoutingNetwork.MARKET_SIZE, w.biz().scoutMarket.size)
        val ids = w.biz().scoutMarket.map { it.id }
        assertTrue(ScoutingNetwork.hire(w, ids[0]).first)
        assertTrue(ScoutingNetwork.hire(w, ids[1]).first)
        assertEquals(2, ScoutingNetwork.maxScouts(me))
        assertFalse(ScoutingNetwork.hire(w, ids[2]).first)
        assertEquals(2, w.biz().scouts.size)
    }

    @Test fun scoutWagesCountTowardTheWageBill() {
        val w = world()
        val me = w.human()
        val before = w.wageBill(me)
        ScoutingNetwork.refreshMarket(w, Random(2))
        val scout = w.biz().scoutMarket.first()
        assertTrue(ScoutingNetwork.hire(w, scout.id).first)
        assertEquals(before + scout.wage, w.wageBill(me))
        assertTrue(ScoutingNetwork.fire(w, scout.id).first)
        assertEquals(before, w.wageBill(me))
    }

    @Test fun wageBudgetBlocksHiring() {
        val w = world()
        w.human().wageBudget = 1L
        ScoutingNetwork.refreshMarket(w, Random(3))
        assertFalse(ScoutingNetwork.hire(w, w.biz().scoutMarket.first().id).first)
    }

    @Test fun assignmentValidatesRegionAndPositionGroup() {
        val w = world()
        ScoutingNetwork.refreshMarket(w, Random(4))
        val id = w.biz().scoutMarket.first().id
        assertTrue(ScoutingNetwork.hire(w, id).first)
        assertFalse(ScoutingNetwork.assign(w, id, 7, 0))
        assertFalse(ScoutingNetwork.assign(w, id, -3, 0))
        assertFalse(ScoutingNetwork.assign(w, id, 2, 4))
        assertTrue(ScoutingNetwork.assign(w, id, 2, 3))
        assertFalse(ScoutingNetwork.assign(w, 9999, 2, 3))
    }

    @Test fun idleScoutsLearnNothing() {
        val w = world()
        ScoutingNetwork.refreshMarket(w, Random(5))
        assertTrue(ScoutingNetwork.hire(w, w.biz().scoutMarket.first().id).first)
        repeat(10) { ScoutingNetwork.weekly(w, Random(it.toLong())) }
        assertTrue(w.biz().knowledge.isEmpty())
    }

    @Test fun assignedScoutsBuildKnowledgeAndFullyAssessedPlayersBecomeScouted() {
        val w = world()
        val me = w.human()
        me.scout = 20
        ScoutingNetwork.refreshMarket(w, Random(6))
        repeat(2) { ScoutingNetwork.hire(w, w.biz().scoutMarket.first().id) }
        assertEquals(2, w.biz().scouts.size)
        for (s in w.biz().scouts) ScoutingNetwork.assign(w, s.id, 1, 0)
        repeat(60) { ScoutingNetwork.weekly(w, Random(it.toLong() + 100)) }
        val knowledge = w.biz().knowledge
        assertTrue(knowledge.isNotEmpty())
        assertTrue(knowledge.values.all { it in 1..100 })
        for ((id, value) in knowledge) if (value >= 100) assertTrue(w.scouted.contains(id))
        val completed = knowledge.count { it.value >= 100 }
        if (completed > 0) assertTrue(w.biz().reports.isNotEmpty())
        for (id in knowledge.keys) {
            val p = w.players[id]!!
            assertTrue(p.clubId >= 0 && w.clubs[p.clubId].tier == 1)
            assertEquals(0, p.pos.group)
        }
    }

    @Test fun knowledgeOfOwnPlayersIsComplete() {
        val w = world()
        val own = w.squad(w.human()).first()
        assertEquals(100, ScoutingNetwork.knowledgeOf(w, own))
        val other = w.players.values.first { it.clubId >= 0 && it.clubId != w.humanClubId }
        assertEquals(0, ScoutingNetwork.knowledgeOf(w, other))
    }

    @Test fun staleKnowledgeForRetiredPlayersIsPruned() {
        val w = world()
        ScoutingNetwork.refreshMarket(w, Random(7))
        w.biz().knowledge[123456] = 40
        ScoutingNetwork.weekly(w, Random(1))
        assertFalse(w.biz().knowledge.containsKey(123456))
    }
}
