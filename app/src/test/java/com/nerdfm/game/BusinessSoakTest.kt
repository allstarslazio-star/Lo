package com.nerdfm.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BusinessSoakTest {
    private fun businessActions(w: World) {
        val me = w.human()
        me.balance += 50_000_000L
        Business.refreshOffers(w, Random(w.week.toLong() + 1))
        for (o in w.biz().sponsorOffers.toList()) Business.acceptSponsor(w, o.id)
        ScoutingNetwork.refreshMarket(w, Random(1))
        w.biz().scoutMarket.firstOrNull()?.let { ScoutingNetwork.hire(w, it.id) }
        for (s in w.biz().scouts) ScoutingNetwork.assign(w, s.id, 1, -1)
        val candidate = w.players.values.firstOrNull { it.clubId >= 0 && it.clubId != me.id && Loans.loanInProblem(w, it, 50) == null }
        if (candidate != null) Loans.loanIn(w, candidate, 50, false, false)
        Business.setTicketLevel(w, w.week % 5)
        val expiring = w.squad(me).filter { Loans.loanFor(w, it.id) == null }.minByOrNull { it.contractEnd }
        if (expiring != null) ContractDeals.propose(w, expiring, ContractTerms(ContractDeals.demandedWage(expiring), 2))
    }

    @Test fun aSeasonAndAHalfWithBusinessActivityStaysConsistentAndSaves() {
        val w = WorldGen.generate(77L, "Soak", 0)
        val me = w.human()
        BusinessTestSupport.shrink(w, me, 36)
        Business.ensureStarted(w)
        var weeks = 0
        while (weeks < 55 && !w.sacked) {
            if (w.week == 1 || w.week == 19) businessActions(w)
            val fixture = Game.startWeek(w)
            if (fixture != null) {
                val engine = Game.humanEngine(w, fixture)
                engine.simToEnd()
                Game.finishHuman(w, fixture, engine)
            }
            Game.completeWeek(w)
            weeks++
            val issues = WorldIntegrity.inspect(w)
            assertTrue("after week $weeks: " + issues.take(3), issues.isEmpty())
            for (c in w.clubs) assertTrue(c.balance > -50_000_000_000L)
        }
        assertTrue(weeks >= 36 || w.sacked)
        assertTrue(Business.ffpReport(w, me).wageRatio >= 0.0)
        assertTrue(w.biz().loans.none { it.active && w.players[it.playerId]?.clubId != it.borrowerClubId })
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "nerdfm-soak-" + System.nanoTime())
        try {
            assertTrue(Persistence.save(dir, w))
            val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
            assertTrue(WorldIntegrity.inspect(loaded).isEmpty())
            assertEquals(w.biz().sponsors.size, loaded.biz().sponsors.size)
            assertEquals(w.biz().scouts.size, loaded.biz().scouts.size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
