package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsTest {
    private fun world(): World = WorldGen.generate(17L, "Notices", 0).also { it.week = 1 }

    // The cap (30) could hide a notice behind the many natural "contract ends next year" ones, so tests read them all.
    private fun all(w: World): List<Notice> = Notices.derive(w, 500)

    private fun find(w: World, id: String): Notice? = all(w).firstOrNull { it.id == id }

    private fun findPrefix(w: World, prefix: String): Notice? = all(w).firstOrNull { it.id.startsWith(prefix) }

    @Test fun deriveIsReadOnlyAndDeterministic() {
        val w = world()
        val before = StateFingerprint.of(w)
        val hadBusiness = w.business != null
        val a = Notices.derive(w)
        val b = Notices.derive(w)
        assertEquals(a, b)
        assertEquals(before, StateFingerprint.of(w))
        assertEquals("deriving notices must not lazily create business state", hadBusiness, w.business != null)
    }

    @Test fun anInvalidHumanClubYieldsNoNotices() {
        val w = World()
        w.humanClubId = 5
        assertTrue(Notices.derive(w).isEmpty())
    }

    @Test fun offersBecomeUrgentWhenTheyAreAboutToLapseAndLeadToTransfers() {
        val w = world()
        val p = w.squad(w.human()).first()
        w.offers.add(Offer(p.id, 1, 2_500_000L, w.week + 5))
        w.offers.add(Offer(w.squad(w.human())[1].id, 2, 900_000L, w.week))
        val soon = all(w).first { it.id == "offer:${w.squad(w.human())[1].id}:2" }
        val later = all(w).first { it.id == "offer:${p.id}:1" }
        assertEquals(NoticeLevel.URGENT, soon.level)
        assertEquals(NoticeLevel.ATTENTION, later.level)
        assertEquals(NavTarget("Transfers", p.id), later.target)
        assertEquals("View offer", later.actionLabel)
        assertTrue(later.body.contains(w.clubs[1].name))
    }

    @Test fun offersForPlayersNoLongerAtTheClubAreIgnored() {
        val w = world()
        val other = w.squad(w.clubs[3]).first()
        w.offers.add(Offer(other.id, 1, 1L, 10))
        w.offers.add(Offer(9_999_999, 1, 1L, 10))
        assertTrue(all(w).none { it.id.startsWith("offer:") })
    }

    @Test fun injuredOrSuspendedStartersAreReportedWithAPathToTheLineup() {
        val w = world()
        val me = w.human()
        val starter = w.players.getValue(me.lineup.first { it != -1 })
        starter.injWeeks = 4; starter.injType = "hamstring"
        val n = find(w, "unavailable:${starter.id}")!!
        assertEquals(NoticeLevel.ATTENTION, n.level)
        assertEquals("Tactics", n.target!!.screen)
        assertTrue(n.body.contains("hamstring"))
        starter.injWeeks = 0; starter.suspWeeks = 2
        assertTrue(find(w, "unavailable:${starter.id}")!!.body.contains("suspended"))
        starter.suspWeeks = 0
        assertNull(find(w, "unavailable:${starter.id}"))
    }

    @Test fun brokenPlayingTimePromisesBecomeARequest() {
        val w = world()
        val p = w.squad(w.human()).first()
        p.playingTimePromise = PlayerRelations.PROMISE_STAR; p.promiseWeeksRemaining = 3; p.promiseBrokenWeeks = 2
        val n = find(w, "promise:${p.id}")!!
        assertEquals("People", n.target!!.screen)
        assertTrue(n.title.contains("playing time"))
    }

    @Test fun unhappyPlayersWithoutAPromiseGetAnInformationalNotice() {
        val w = world()
        val p = w.squad(w.human()).first()
        p.morale = 25f
        val n = find(w, "morale:${p.id}")!!
        assertEquals(NoticeLevel.INFO, n.level)
    }

    @Test fun expiringContractsEscalateLateInTheSeason() {
        val w = world()
        val p = w.squad(w.human()).first()
        p.contractEnd = w.year + 1
        assertEquals(NoticeLevel.ATTENTION, find(w, "contract:${p.id}")!!.level)
        w.week = 25
        val late = find(w, "contract:${p.id}")!!
        assertEquals(NoticeLevel.URGENT, late.level)
        assertEquals(NavTarget("Squad", p.id), late.target)
        p.contractEnd = w.year + 3
        assertNull(find(w, "contract:${p.id}"))
    }

    @Test fun loanedPlayersDoNotTriggerContractNotices() {
        val w = world()
        val p = w.squad(w.human()).first()
        p.contractEnd = w.year
        w.biz().loans.add(LoanDeal(1, p.id, 1, 0, 100, 50, w.year + 1, 10, 0L))
        assertNull(find(w, "contract:${p.id}"))
    }

    @Test fun boardConfidenceThresholds() {
        val w = world()
        val me = w.human()
        me.boardConfidence = 80f
        assertNull(find(w, "board"))
        me.boardConfidence = 30f
        assertEquals(NoticeLevel.ATTENTION, find(w, "board")!!.level)
        me.boardConfidence = 12f
        assertEquals(NoticeLevel.URGENT, find(w, "board")!!.level)
    }

    @Test fun financialTroubleIsReported() {
        val w = world()
        val me = w.human()
        me.balance = -1L
        me.wageBudget = 0L
        val n = all(w)
        assertEquals(NoticeLevel.URGENT, n.first { it.id == "finance:balance" }.level)
        assertEquals("Finance", n.first { it.id == "finance:wages" }.target!!.screen)
    }

    @Test fun theNextFixtureAndWindowClosingAppear() {
        val w = world()
        val fx = findPrefix(w, "fixture:")!!
        assertEquals(NoticeLevel.INFO, fx.level)
        assertTrue(fx.title.startsWith("Round 1"))
        assertNull(findPrefix(w, "window:"))
        w.week = 4
        assertNotNull(findPrefix(w, "window:"))
        w.week = 21
        assertNotNull(findPrefix(w, "window:"))
        w.week = 0
        assertNull("no fixture in pre-season", findPrefix(w, "fixture:"))
    }

    @Test fun noticesAreOrderedMostUrgentFirstAndCapped() {
        val w = world()
        val me = w.human()
        me.balance = -5L
        me.boardConfidence = 10f
        for (p in w.squad(me).take(12)) p.contractEnd = w.year
        val list = Notices.derive(w, 5)
        assertEquals(5, list.size)
        assertTrue(list.zipWithNext().all { (a, b) -> a.level.ordinal <= b.level.ordinal })
        assertEquals(NoticeLevel.URGENT, list.first().level)
        assertEquals("ids are unique", list.size, list.map { it.id }.toSet().size)
    }

    @Test fun dismissalLastsForTheWeekAndTheProblemReturnsIfItPersists() {
        val w = world()
        w.human().boardConfidence = 10f
        val center = NotificationCenter()
        assertTrue(center.visible(w).any { it.id == "board" })
        center.dismiss(w, "board")
        assertTrue(center.visible(w).none { it.id == "board" })
        w.week = 2
        assertTrue("the board is still worried next week", center.visible(w).any { it.id == "board" })
        w.human().boardConfidence = 90f
        assertTrue(center.visible(w).none { it.id == "board" })
    }

    @Test fun dismissAllAndUrgentCount() {
        val w = world()
        w.human().balance = -1L
        val center = NotificationCenter()
        assertTrue(center.urgentCount(w) >= 1)
        center.dismissAll(w)
        assertTrue(center.visible(w).isEmpty())
        assertEquals(0, center.urgentCount(w))
        center.clear()
        assertFalse(center.visible(w).isEmpty())
    }
}
