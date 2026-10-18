package com.nerdfm.game

import org.junit.Assert.*
import org.junit.Test

class ContractDealsTest {
    private fun world(): World = BusinessTestSupport.richWorld(9L)

    private fun star(w: World): Player = w.squad(w.human()).maxByOrNull { it.ovr }!!

    @Test fun generousOfferIsAcceptedAndChargesAgentFee() {
        val w = world()
        val me = w.human()
        val p = star(w)
        p.morale = 70f
        val wage = (ContractDeals.demandedWage(p) * 1.2).toInt()
        val before = me.balance
        val reply = ContractDeals.propose(w, p, ContractTerms(wage, 3))
        assertEquals(0, reply.outcome)
        assertEquals(wage, p.wage)
        assertEquals(w.year + 3, p.contractEnd)
        assertEquals(before - ContractDeals.renewalAgentFee(wage, 3), me.balance)
    }

    @Test fun lowOfferGetsTwoCountersThenTalksCollapseAndAgentBlocksFurtherTalks() {
        val w = world()
        val p = star(w)
        val low = ContractTerms(ContractDeals.demandedWage(p) / 2, 3)
        assertEquals(1, ContractDeals.propose(w, p, low).outcome)
        assertEquals(1, ContractDeals.propose(w, p, low).outcome)
        assertEquals(2, ContractDeals.propose(w, p, low).outcome)
        val blocked = ContractDeals.propose(w, p, low)
        assertEquals(3, blocked.outcome)
        assertTrue(ContractDeals.weeksUntilTalks(w, p) > 0)
    }

    @Test fun agentsCounterIsAcceptableWhenYouAgree() {
        val w = world()
        val p = star(w)
        p.morale = 70f
        val low = ContractTerms(ContractDeals.demandedWage(p) / 2, 3)
        val counter = ContractDeals.propose(w, p, low).counter
        assertNotNull(counter)
        assertEquals(0, ContractDeals.propose(w, p, counter!!).outcome)
    }

    @Test fun talksResumeAfterTheCooldown() {
        val w = world()
        val p = star(w)
        val low = ContractTerms(ContractDeals.demandedWage(p) / 2, 3)
        repeat(3) { ContractDeals.propose(w, p, low) }
        w.week += ContractDeals.COOLDOWN_WEEKS
        assertEquals(1, ContractDeals.propose(w, p, low).outcome)
    }

    @Test fun invalidTermsDoNotUseARound() {
        val w = world()
        val p = star(w)
        assertEquals(3, ContractDeals.propose(w, p, ContractTerms(0, 3)).outcome)
        assertEquals(3, ContractDeals.propose(w, p, ContractTerms(1000, 9)).outcome)
        assertEquals(3, ContractDeals.propose(w, p, ContractTerms(1000, 3, -5L)).outcome)
        assertEquals(0, w.biz().negotiations[p.id]?.round ?: 0)
    }

    @Test fun cannotNegotiateWithAnotherClubsPlayer() {
        val w = world()
        val other = w.players.values.first { it.clubId >= 0 && it.clubId != w.humanClubId }
        assertEquals(3, ContractDeals.propose(w, other, ContractTerms(5000, 3)).outcome)
    }

    @Test fun wageBudgetStillBindsGenerousOffers() {
        val w = world()
        val me = w.human()
        val p = star(w)
        me.wageBudget = 1L
        val reply = ContractDeals.propose(w, p, ContractTerms(ContractDeals.demandedWage(p) * 2, 3))
        assertEquals(3, reply.outcome)
    }

    @Test fun clausesAreStoredAndClearedOnTransfer() {
        val w = world()
        val p = star(w)
        val wage = (ContractDeals.demandedWage(p) * 1.5).toInt()
        val reply = ContractDeals.propose(w, p, ContractTerms(wage, 3, releaseClause = 40_000_000L, appearanceBonus = 200))
        assertEquals(0, reply.outcome)
        assertEquals(40_000_000L, ContractDeals.releaseClauseFor(w, p))
        assertEquals(200, w.biz().clauses[p.id]!!.appearanceBonus)
        val buyer = w.clubs.first { !it.human }
        Transfers.move(w, p, buyer, 1_000_000L, wage, 2, kotlin.random.Random(1))
        assertNull(w.biz().clauses[p.id])
    }

    @Test fun releaseClauseForcesAcceptanceOfALowBid() {
        val w = world()
        val me = w.human()
        val p = w.players.values.first { it.clubId >= 0 && it.clubId != me.id && w.squad(w.clubs[it.clubId]).size > 22 }
        val ask = Transfers.askingPrice(w, p, me)
        val clause = maxOf(1000L, ask / 2)
        w.biz().clauses[p.id] = ContractClauses().apply { releaseClause = clause }
        assertEquals(0, Transfers.bid(w, p, clause).status)
        assertNotEquals(0, Transfers.bid(w, p, clause - 1).status)
    }

    @Test fun sellOnClauseSharesLaterSaleWithTheOriginalClub() {
        val w = world()
        val me = w.human()
        val p = star(w)
        ContractDeals.setSellOnPolicy(w, 1)
        ContractDeals.registerSellOn(w, p, me.id)
        val a = w.clubs.first { !it.human }
        val b = w.clubs.first { !it.human && it.id != a.id }
        Transfers.move(w, p, a, 0L, 5000, 2, kotlin.random.Random(1))
        val before = me.balance
        val aBefore = a.balance
        Transfers.move(w, p, b, 1_000_000L, 5000, 2, kotlin.random.Random(2))
        assertEquals(before + 100_000L, me.balance)
        assertEquals(aBefore + 1_000_000L - 100_000L, a.balance)
        assertNull(w.biz().clauses[p.id])
    }

    @Test fun sellOnPolicyLowersFeeAndIsValidated() {
        val w = world()
        assertFalse(ContractDeals.setSellOnPolicy(w, 5))
        assertEquals(1_000_000L, ContractDeals.saleFeeAfterClause(w, 1_000_000L))
        ContractDeals.setSellOnPolicy(w, 1)
        assertEquals(940_000L, ContractDeals.saleFeeAfterClause(w, 1_000_000L))
        ContractDeals.setSellOnPolicy(w, 2)
        assertEquals(880_000L, ContractDeals.saleFeeAfterClause(w, 1_000_000L))
    }

    @Test fun appearanceBonusIsPaidOnlyForPlayersWhoPlayed() {
        val w = world()
        val me = w.human()
        val p = star(w)
        w.biz().clauses[p.id] = ContractClauses().apply { appearanceBonus = 700 }
        p.lastMinutes = 0
        val before = me.balance
        Business.preWeek(w)
        assertEquals(before, me.balance)
        p.lastMinutes = 90
        Business.preWeek(w)
        assertEquals(before - 700L, me.balance)
    }

    @Test fun signingChargesAgentFeeOnTopOfTheTransferFee() {
        val w = world()
        val me = w.human()
        val fa = w.players.values.filter { it.clubId < 0 && it.age in 19..30 }.minByOrNull { it.ovr }!!
        val wage = Transfers.wantedWage(me, fa)
        val before = me.balance
        val result = Transfers.completeSigning(w, fa, 0L, wage, 2)
        assertTrue(result.second, result.first)
        assertEquals(before - ContractDeals.agentFee(0L, wage, 2), me.balance)
    }
}
