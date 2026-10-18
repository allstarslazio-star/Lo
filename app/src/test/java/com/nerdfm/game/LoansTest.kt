package com.nerdfm.game

import org.junit.Assert.*
import org.junit.Test

class LoansTest {
    private fun world(): World = BusinessTestSupport.richWorld(21L)

    private fun target(w: World): Player =
        w.players.values.first { it.clubId >= 0 && it.clubId != w.humanClubId && Loans.loanInProblem(w, it, 50) == null }

    @Test fun loanInMovesThePlayerAndSplitsWages() {
        val w = world()
        val me = w.human()
        val p = target(w)
        val parent = w.clubs[p.clubId]
        val originalWage = p.wage
        val result = Loans.loanIn(w, p, 50, false, false)
        assertTrue(result.second, result.first)
        assertEquals(me.id, p.clubId)
        assertTrue(me.players.contains(p.id))
        assertFalse(parent.players.contains(p.id))
        assertEquals(originalWage * 50 / 100, p.wage)
        assertEquals(originalWage.toLong() * 50 / 100, Loans.parentWageSupport(w, parent))
        assertNotNull(Loans.loanFor(w, p.id))
    }

    @Test fun loanEndsAtTheEndOfTheSeasonAndPlayerReturns() {
        val w = world()
        val me = w.human()
        val p = target(w)
        val parent = w.clubs[p.clubId]
        val originalWage = p.wage
        assertTrue(Loans.loanIn(w, p, 100, false, false).first)
        w.week = 34
        Loans.weekly(w)
        assertEquals(parent.id, p.clubId)
        assertEquals(originalWage, p.wage)
        assertFalse(me.players.contains(p.id))
        assertTrue(parent.players.contains(p.id))
        assertNull(Loans.loanFor(w, p.id))
    }

    @Test fun halfSeasonLoanEndsAtWeekEighteen() {
        val w = world()
        val p = target(w)
        assertTrue(Loans.loanIn(w, p, 100, true, false).first)
        w.week = 17
        Loans.weekly(w)
        assertNotNull(Loans.loanFor(w, p.id))
        w.week = 18
        Loans.weekly(w)
        assertNull(Loans.loanFor(w, p.id))
    }

    @Test fun loanedPlayersCannotBeReleasedOrSold() {
        val w = world()
        val me = w.human()
        val p = target(w)
        assertTrue(Loans.loanIn(w, p, 100, false, false).first)
        assertTrue(Transfers.release(w, p).contains("on loan"))
        assertEquals(me.id, p.clubId)
        val buyer = w.clubs.first { !it.human }
        val offer = Offer(p.id, buyer.id, 1_000_000L, w.week + 2)
        w.offers.add(offer)
        buyer.balance = 100_000_000L
        buyer.transferBudget = 100_000_000L
        assertTrue(Transfers.acceptOffer(w, offer).contains("on loan"))
        assertEquals(me.id, p.clubId)
    }

    @Test fun optionToBuyMakesTheMovepermanentAndPaysTheParent() {
        val w = world()
        val me = w.human()
        val p = target(w)
        val parent = w.clubs[p.clubId]
        assertTrue(Loans.loanIn(w, p, 100, false, true).first)
        val deal = Loans.loanFor(w, p.id)!!
        assertTrue(deal.optionFee > 0L)
        val parentBefore = parent.balance
        val result = Loans.exercisePurchase(w, deal.id)
        assertTrue(result.second, result.first)
        assertEquals(me.id, p.clubId)
        assertNull(Loans.loanFor(w, p.id))
        assertEquals(parentBefore + deal.optionFee, parent.balance)
        assertEquals(1, me.players.count { it == p.id })
        assertTrue(WorldIntegrity.inspect(w).isEmpty())
    }

    @Test fun optionCannotBeUsedWithoutOneOrWithoutTheWindow() {
        val w = world()
        val p = target(w)
        assertTrue(Loans.loanIn(w, p, 100, false, false).first)
        val deal = Loans.loanFor(w, p.id)!!
        assertFalse(Loans.exercisePurchase(w, deal.id).first)
    }

    @Test fun cannotLoanMoreThanTheLimit() {
        val w = world()
        var done = 0
        for (candidate in w.players.values.filter { it.clubId >= 0 && it.clubId != w.humanClubId }) {
            if (done >= Loans.MAX_LOANS_IN) break
            if (Loans.loanIn(w, candidate, 50, false, false).first) done++
        }
        assertEquals(Loans.MAX_LOANS_IN, done)
        val extra = w.players.values.firstOrNull { it.clubId >= 0 && it.clubId != w.humanClubId && Loans.loanFor(w, it.id) == null }
        assertNotNull(extra)
        assertNotNull(Loans.loanInProblem(w, extra!!, 50))
    }

    @Test fun windowMustBeOpen() {
        val w = world()
        val p = target(w)
        w.week = 10
        assertNotNull(Loans.loanInProblem(w, p, 50))
        assertTrue(Loans.loanOutOptions(w, w.squad(w.human()).first()).isEmpty())
    }

    @Test fun loanOutSendsPlayerAwayAndParentKeepsPayingRemainingWages() {
        val w = world()
        val me = w.human()
        val p = w.squad(me).maxByOrNull { it.ovr }!!
        val options = Loans.loanOutOptions(w, p)
        assertTrue(options.isNotEmpty())
        val borrower = options.first().first
        val share = options.first().second
        val originalWage = p.wage
        val result = Loans.loanOut(w, p, borrower, false)
        assertTrue(result.second, result.first)
        assertEquals(borrower.id, p.clubId)
        assertFalse(me.players.contains(p.id))
        assertEquals(originalWage * share / 100, p.wage)
        assertEquals(originalWage.toLong() * (100 - share) / 100, Loans.parentWageSupport(w, me))
        assertTrue(WorldIntegrity.inspect(w).isEmpty())
        assertTrue(Loans.recall(w, Loans.loanFor(w, p.id)!!.id).first)
        assertEquals(me.id, p.clubId)
        assertEquals(originalWage, p.wage)
        assertTrue(WorldIntegrity.inspect(w).isEmpty())
    }

    @Test fun loanedOutPlayersAreNotPoachedOrTrimmedByAiClubs() {
        val w = world()
        val me = w.human()
        val p = w.squad(me).maxByOrNull { it.ovr }!!
        val borrower = Loans.loanOutOptions(w, p).first().first
        assertTrue(Loans.loanOut(w, p, borrower, false).first)
        // Overfill the borrower so maintenance wants to trim someone.
        val filler = w.players.values.filter { it.clubId < 0 }.take(12)
        for (f in filler) { f.clubId = borrower.id; borrower.players.add(f.id) }
        Transfers.aiSquadMaintenance(w, kotlin.random.Random(1))
        assertEquals(borrower.id, p.clubId)
    }
}
