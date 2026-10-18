package com.nerdfm.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BusinessFinanceTest {
    private fun world(): World = WorldGen.generate(7L, "Tester", 0)

    @Test fun gateMultiplierIsOneAtStandardPricesAndStartingStadium() {
        val w = world()
        val me = w.human()
        Business.ensureStarted(w)
        assertEquals(1.0, Business.gateMultiplier(w, me), 1e-9)
    }

    @Test fun aiClubsAreNeverAffectedByTicketPricing() {
        val w = world()
        val ai = w.clubs.first { !it.human }
        assertEquals(1.0, Business.gateMultiplier(w, ai), 0.0)
    }

    @Test fun premiumPricesHurtSmallClubsButHelpSellOutClubs() {
        val w = world()
        val me = w.human()
        Business.ensureStarted(w)
        me.rep = 20
        Business.setTicketLevel(w, 4)
        assertTrue(Business.gateMultiplier(w, me) < 1.0)
        me.rep = 100
        assertTrue(Business.gateMultiplier(w, me) > 1.0)
    }

    @Test fun invalidTicketLevelIsRejected() {
        val w = world()
        assertFalse(Business.setTicketLevel(w, 9))
        assertEquals(2, w.biz().ticketLevel)
    }

    @Test fun startingSponsorsKeepCommercialIncomeNeutral() {
        val w = world()
        val me = w.human()
        Business.ensureStarted(w)
        val base = (me.revenue * 0.55 / 52.0).toLong()
        val replaced = (base * Business.COMMERCIAL_SHARE).toLong()
        assertEquals(2, Business.activeSponsors(w).size)
        assertTrue(Math.abs(Business.sponsorIncome(w) - replaced) <= 3L)
    }

    @Test fun acceptingAnOfferPaysBonusAndFillsTheSlot() {
        val w = world()
        val me = w.human()
        me.balance = 500_000_000L
        Business.ensureStarted(w)
        Business.refreshOffers(w, Random(1))
        val main = Business.activeSponsors(w).first { it.slot == 0 }
        assertTrue(Business.terminateSponsor(w, main.id).first)
        assertFalse(Business.slotTaken(w, 0))
        val offer = w.biz().sponsorOffers.first { it.slot == 0 }
        val before = me.balance
        assertTrue(Business.acceptSponsor(w, offer.id).first)
        assertEquals(before + offer.signingBonus, me.balance)
        assertTrue(Business.slotTaken(w, 0))
        assertTrue(w.biz().sponsorOffers.none { it.slot == 0 })
    }

    @Test fun cannotSignSecondSponsorInTakenSlot() {
        val w = world()
        Business.ensureStarted(w)
        Business.refreshOffers(w, Random(2))
        val offer = w.biz().sponsorOffers.first { it.slot == 1 }
        assertFalse(Business.acceptSponsor(w, offer.id).first)
    }

    @Test fun winBonusOnlyPaidOnWins() {
        val w = world()
        val me = w.human()
        Business.ensureStarted(w)
        val before = me.balance
        Business.onHumanResult(w, 1, 1)
        Business.onHumanResult(w, 0, 2)
        assertEquals(before, me.balance)
        Business.onHumanResult(w, 2, 0)
        assertTrue(me.balance > before)
    }

    @Test fun fairPlayRestrictionBlocksFeesButNotFreeTransfers() {
        val w = world()
        val me = w.human()
        me.revenue = 1L
        assertEquals(2, Business.ffpReport(w, me).status)
        assertNotNull(Business.signingRestriction(w, 100_000L))
        assertNull(Business.signingRestriction(w, 0L))
    }

    @Test fun healthyClubIsCompliant() {
        val w = world()
        val me = w.human()
        me.balance = 1_000_000L
        me.revenue = w.wageBill(me) * 52L * 2L
        assertEquals(0, Business.ffpReport(w, me).status)
    }

    @Test fun stadiumExpansionCompletesAndOnlyOneProjectRuns() {
        val w = world()
        val me = w.human()
        me.balance = 9_000_000_000L
        Business.ensureStarted(w)
        val capacity = me.capacity()
        assertTrue(Business.startExpansion(w, 2000).first)
        assertFalse(Business.startExpansion(w, 2000).first)
        val rnd = Random(5)
        repeat(8 + 2000 / 500) { Business.postWeek(w, rnd) }
        assertEquals(capacity + 2000, me.capacity())
        assertEquals(0, w.biz().expansionSeats)
    }

    @Test fun expansionNeedsCashAndSaneSize() {
        val w = world()
        val me = w.human()
        me.balance = 0L
        assertFalse(Business.startExpansion(w, 2000).first)
        me.balance = 9_000_000_000L
        assertFalse(Business.startExpansion(w, 10).first)
        assertFalse(Business.startExpansion(w, 50_000).first)
    }

    @Test fun businessStateSurvivesSaveAndLoad() {
        val w = world()
        Business.ensureStarted(w)
        w.biz().ticketLevel = 3
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "nerdfm-biz-" + System.nanoTime())
        try {
            assertTrue(Persistence.save(dir, w))
            val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
            assertEquals(3, loaded.biz().ticketLevel)
            assertEquals(2, loaded.biz().sponsors.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun oldSavesWithoutBusinessStateGetFreshOne() {
        val w = world()
        w.business = null
        val state = w.biz()
        assertNotNull(state)
        assertSame(state, w.biz())
    }
}
