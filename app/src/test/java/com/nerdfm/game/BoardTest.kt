package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {
    private fun world(seed: Long = 31L) = EcoTestSupport.world(seed)

    @Test fun everyClubHasAnOwnerChairmanAndDirectorWithTraitsInRange() {
        val w = world(); val s = BoardRules.state(w)
        assertEquals(w.clubs.size, s.clubs.size)
        for (c in w.clubs) {
            val b = s.clubs.getValue(c.id)
            for (v in listOf(b.owner.ambition, b.owner.patience, b.owner.wealth, b.chairman.ambition, b.chairman.meddling, b.director.ability, b.director.stubbornness)) assertTrue(v in 1..20)
            assertEquals(60f, b.director.alignment, 0f)
        }
        assertTrue("policies vary", s.clubs.values.map { it.owner.policy }.toSet().size == TransferPolicy.values().size)
        val rich = s.clubs.values.filter { w.clubs[it.clubId].tier == 0 }.map { it.owner.wealth }.average()
        val poor = s.clubs.values.filter { w.clubs[it.clubId].tier == 6 }.map { it.owner.wealth }.average()
        assertTrue("top clubs have wealthier owners ($rich vs $poor)", rich > poor + 3)
    }

    @Test fun theHumanClubGetsObjectivesAndAManagerContract() {
        val w = world(); val s = BoardRules.state(w); val me = w.human()
        val objs = s.clubs.getValue(me.id).objectives
        assertTrue(objs.isNotEmpty() && objs.any { it.kind == ObjectiveKind.LEAGUE_FINISH })
        for (o in objs) { assertEquals(0, o.status); assertTrue(o.deadlineYear >= w.year); assertTrue(o.describe().isNotBlank()) }
        val c = assertNotNull(s.contract).let { s.contract!! }
        assertTrue(c.endYear > w.year && c.weeklyWage > 0L && c.compensationWeeks == 26)
    }

    @Test fun sackThresholdFollowsOwnerPatienceAndContractProtection() {
        val w = world(); val s = BoardRules.state(w); val b = s.clubs.getValue(w.humanClubId)
        s.contract!!.signedAbs = Business.absWeek(w) - 100
        b.owner.patience = 10
        assertEquals(8f, BoardRules.sackThreshold(w), 1e-4f)
        b.owner.patience = 20
        assertEquals(2f, BoardRules.sackThreshold(w), 1e-4f)
        b.owner.patience = 1
        assertEquals(13.4f, BoardRules.sackThreshold(w), 1e-4f)
        s.contract!!.signedAbs = Business.absWeek(w) - 2
        assertEquals("a fresh contract protects the manager", 3f, BoardRules.sackThreshold(w), 1e-4f)
        Ecosystem.enabled = false
        try { assertEquals(8f, BoardRules.sackThreshold(w), 0f) } finally { Ecosystem.enabled = true }
    }

    @Test fun aFrozenBudgetBlocksFeesButNotFreeSignings() {
        val w = world(); val b = BoardRules.state(w).clubs.getValue(w.humanClubId)
        assertNull(BoardRules.signingRestriction(w, 1_000_000L))
        b.freezeWeeks = 5
        assertTrue(BoardRules.signingRestriction(w, 1_000_000L)!!.contains("5"))
        assertNull(BoardRules.signingRestriction(w, 0L))
        EcoTestSupport.richHuman(w); w.week = 1
        val target = w.players.values.first { it.clubId >= 0 && it.clubId != w.humanClubId }
        BusinessTestSupport.shrink(w, w.human(), 30)
        val r = Transfers.completeSigning(w, target, Transfers.askingPrice(w, target, w.human()) * 2, Transfers.wantedWage(w.human(), target) * 2, 3)
        assertFalse(r.first); assertTrue(r.second, r.second.contains("frozen") || r.second.contains("window") || r.second.contains("interested"))
        EcoTestSupport.richHuman(w)
        w.week = 1
        for (i in 1..5) { w.week = i; Ecosystem.preWeek(w) }
        assertEquals(0, b.freezeWeeks)
    }

    @Test fun leagueObjectiveIsMetEarlyAndFailedAtTheDeadline() {
        val w = world(); val me = w.human(); val s = BoardRules.state(w); val b = s.clubs.getValue(me.id)
        val o = b.objectives.first { it.kind == ObjectiveKind.LEAGUE_FINISH }
        val lg = w.leagueOf(me.id)
        // make the human club win the league on paper
        for (r in lg.table.values) { r.pts = 0 }
        lg.table.getValue(me.id).pts = 99
        val before = me.boardConfidence
        BoardRules.seasonReview(w)
        assertEquals(1, o.status)
        assertTrue(me.boardConfidence >= before + 4f)
        // a second world: finish last and run to the deadline
        val w2 = world(32L); val me2 = w2.human(); val b2 = BoardRules.state(w2).clubs.getValue(me2.id)
        val o2 = b2.objectives.first { it.kind == ObjectiveKind.LEAGUE_FINISH }
        w2.year = o2.deadlineYear
        for (r in w2.leagueOf(me2.id).table.values) r.pts = 50
        w2.leagueOf(me2.id).table.getValue(me2.id).pts = 0
        val conf = me2.boardConfidence
        BoardRules.seasonReview(w2)
        assertEquals(2, o2.status)
        assertTrue(me2.boardConfidence <= conf - 7f)
        assertTrue("fresh objectives are issued after resolution", b2.objectives.count { it.status == 0 } >= 1)
    }

    @Test fun objectiveSetDependsOnTransferPolicy() {
        val w = world(); val me = w.human(); val b = BoardRules.state(w).clubs.getValue(me.id)
        for ((policy, kind) in listOf(TransferPolicy.SELL_TO_PROFIT to ObjectiveKind.PROFIT, TransferPolicy.YOUTH_FIRST to ObjectiveKind.ACADEMY_MINUTES, TransferPolicy.BALANCED to ObjectiveKind.SUSTAINABILITY)) {
            b.owner.policy = policy; b.objectives.clear()
            BoardRules.assignObjectives(w, b, me)
            assertTrue("$policy -> $kind", b.objectives.any { it.kind == kind })
        }
        b.owner.policy = TransferPolicy.BIG_SPENDER; b.objectives.clear()
        BoardRules.assignObjectives(w, b, me)
        assertEquals(listOf(ObjectiveKind.LEAGUE_FINISH), b.objectives.map { it.kind })
    }

    @Test fun ownerPolicyAndWealthScaleTransferBudgets() {
        val w = world(); val s = BoardRules.state(w)
        val a = w.clubs[1]; val b = w.clubs[2]
        s.clubs.getValue(a.id).owner.policy = TransferPolicy.BIG_SPENDER; s.clubs.getValue(a.id).owner.wealth = 20
        s.clubs.getValue(b.id).owner.policy = TransferPolicy.SELL_TO_PROFIT; s.clubs.getValue(b.id).owner.wealth = 1
        a.transferBudget = 1_000_000L; b.transferBudget = 1_000_000L
        BoardRules.adjustBudgets(w)
        assertTrue(a.transferBudget > 1_300_000L && b.transferBudget < 700_000L)
    }

    @Test fun ownerEventsHappenAtPlausibleRatesAndAreReproducible() {
        var takeovers = 0; var investments = 0; var firstRun = ""
        for (seed in 1L..6L) {
            val w = world(seed); val s = BoardRules.state(w)
            val owners = s.clubs.values.associate { it.clubId to it.owner.name }
            val balances = w.clubs.associate { it.id to it.balance }
            BoardRules.seasonReview(w)
            takeovers += s.clubs.values.count { owners[it.clubId] != it.owner.name }
            investments += w.clubs.count { it.balance > balances.getValue(it.id) }
            if (seed == 1L) firstRun = s.clubs.values.joinToString { it.owner.name }
        }
        val clubs = 126 * 6
        assertTrue("takeovers=$takeovers", takeovers in 5..(clubs * 8 / 100))
        assertTrue("investments=$investments", investments in 3..(clubs * 12 / 100))
        val again = world(1L).also { BoardRules.seasonReview(it) }
        assertEquals(firstRun, BoardRules.state(again).clubs.values.joinToString { it.owner.name })
    }

    @Test fun alignmentRisesWhenThePhilosophyIsFollowedAndFallsWhenItIsNot() {
        val w = world(); val me = w.human(); val b = BoardRules.state(w).clubs.getValue(me.id)
        for (p in w.squad(me)) { p.lastMinutes = 0 }
        w.squad(me).take(11).forEach { it.lastMinutes = 90; it.age = 19 }
        b.director.philosophy = DofPhilosophy.YOUNG_TALENT; b.director.alignment = 50f
        BoardRules.weekly(w)
        val up = b.director.alignment
        w.squad(me).take(11).forEach { it.age = 31 }
        b.director.alignment = 50f
        BoardRules.weekly(w)
        assertTrue("up=$up down=${b.director.alignment}", up > 50f && b.director.alignment < 50f)
        for (ph in DofPhilosophy.values()) assertTrue(BoardRules.fit(w, me, ph) in -1f..1f)
    }

    @Test fun suggestionsCanBeAcceptedDeclinedOrIgnored() {
        val w = world(); EcoTestSupport.richHuman(w); val me = w.human(); val s = BoardRules.state(w); val d = s.clubs.getValue(me.id).director
        BusinessTestSupport.shrink(w, me, 30)
        w.week = 4
        var made = false
        for (salt in 0..40) { w.week = 4 + (salt % 2) * 0; Ecosystem.withProviders(emptyList()) { BoardRules.weekly(w) }; if (s.suggestions.isNotEmpty()) { made = true; break }; w.year++ }
        assertTrue("a suggestion is eventually made", made)
        val sg = s.suggestions.first { !it.handled }
        val p = w.players.getValue(sg.playerId)
        assertEquals(-1, p.clubId)
        d.alignment = 50f
        val size = me.players.size
        val r = BoardRules.acceptSuggestion(w, sg.id)
        if (r.first) { assertEquals(size + 1, me.players.size); assertEquals(54f, d.alignment, 1e-3f); assertTrue(sg.handled) }
        val other = s.suggestions.firstOrNull { !it.handled }
        if (other != null) {
            d.alignment = 50f
            assertTrue(BoardRules.declineSuggestion(w, other.id).first)
            assertEquals(47f, d.alignment, 1e-3f)
            assertFalse(BoardRules.declineSuggestion(w, other.id).first)
        }
        assertFalse(BoardRules.acceptSuggestion(w, 9999).first)
    }

    @Test fun ignoredSuggestionsExpireAndCostTrust() {
        val w = world(); val me = w.human(); val s = BoardRules.state(w); val d = s.clubs.getValue(me.id).director
        s.suggestions.add(DofSuggestion(1, 1, w.year, w.week, "x"))
        d.alignment = 50f
        w.week = w.week + BoardRules.SUGGESTION_LIFE_WEEKS
        BoardRules.weekly(w)
        assertTrue(s.suggestions.first().handled)
        assertTrue(d.alignment < 50f + 1.2f)
    }

    @Test fun aMisalignedDirectorSignsPlayersWithoutAskingButNeverBreaksLimits() {
        val w = world(); EcoTestSupport.richHuman(w); val me = w.human(); val s = BoardRules.state(w); val d = s.clubs.getValue(me.id).director
        BusinessTestSupport.shrink(w, me, 28)
        var signed = 0
        for (i in 0 until 60) {
            w.week = 3
            d.alignment = 10f; d.lastActAbs = -100
            val before = me.players.size
            Ecosystem.withProviders(emptyList()) { BoardRules.weekly(w) }
            if (me.players.size > before) signed++
            w.year += 1
            assertTrue(me.players.size <= 40)
            assertTrue(w.wageBill(me) <= me.wageBudget)
        }
        assertTrue("signed=$signed", signed >= 3)
        // an aligned director never does it
        d.alignment = 80f
        val size = me.players.size
        for (i in 0 until 20) { w.week = 3; w.year++; Ecosystem.withProviders(emptyList()) { BoardRules.weekly(w) }; d.alignment = 80f }
        assertEquals(size, me.players.size)
    }

    @Test fun contractRenewalDependsOnBoardConfidenceAndCompensationIsPaidOnce() {
        val w = world(); val me = w.human(); val s = BoardRules.state(w); val c = s.contract!!
        c.endYear = w.year + 1; me.boardConfidence = 90f
        BoardRules.renewContract(w, s, me)
        assertEquals("not yet due", w.year + 1, c.endYear)
        c.endYear = w.year; me.boardConfidence = 70f
        BoardRules.renewContract(w, s, me)
        assertEquals(w.year + 3, c.endYear); assertFalse(w.sacked)
        c.endYear = w.year; me.boardConfidence = 40f
        BoardRules.renewContract(w, s, me)
        assertEquals(w.year + 1, c.endYear)
        c.endYear = w.year; me.boardConfidence = 10f
        BoardRules.renewContract(w, s, me)
        assertTrue(w.sacked)
        val before = me.balance
        BoardRules.afterWeek(w); BoardRules.afterWeek(w)
        assertEquals(before - c.weeklyWage * 26, me.balance)
    }
}
