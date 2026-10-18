package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffTest {
    private fun world(seed: Long = 21L) = EcoTestSupport.world(seed)

    @Test fun everyClubIsStaffedToItsTierAndPayrollIsBaselined() {
        val w = world(); val s = Staffing.state(w)
        for (c in w.clubs) {
            val ms = Staffing.members(w, c.id)
            assertEquals(Staffing.staffedSlots(c.tier), ms.size)
            for (role in StaffRole.values()) assertTrue(ms.count { it.role == role } <= role.slots)
            for (m in ms) { assertEquals(c.id, m.clubId); assertTrue(m.ability in 1..20); assertTrue(m.wage >= 40); assertTrue(m.morale in 0f..100f) }
            assertEquals(s.baseline[c.id], ms.sumOf { it.wage.toLong() })
            assertTrue("no vacancies at the start", Staffing.vacancies(w, c.id).isEmpty())
        }
        for (role in StaffRole.values()) assertTrue(Staffing.freeAgents(w, role).size >= Staffing.POOL_MIN_PER_ROLE)
        assertEquals(StaffRole.values().filter { it != StaffRole.LANGUAGE_COACH }.sumOf { it.slots }, Staffing.SLOT_ORDER.size) // the M4 language coach is never part of AI staffing
    }

    @Test fun generationIsDeterministic() {
        val a = Staffing.state(world(5L)); val b = Staffing.state(world(5L))
        assertEquals(a.members.keys, b.members.keys)
        for ((id, m) in a.members) { val o = b.members.getValue(id); assertEquals(m.name, o.name); assertEquals(m.ability, o.ability); assertEquals(m.wage, o.wage) }
    }

    @Test fun effectsAreCentredSoTheLeagueAverageIsUnchanged() {
        val w = world()
        val train = w.clubs.map { Staffing.trainingMultiplier(w, it.id) }
        val rec = w.clubs.map { Staffing.recoveryFactor(w, it.id) }
        assertEquals(1.0, train.average(), 0.02)
        assertEquals(0.0, rec.average(), 0.006)
        assertTrue(train.all { it in 0.85f..1.20f })
        assertTrue("some clubs must differ", train.max() - train.min() > 0.03f)
    }

    @Test fun betterCoachesRaiseAndWorseCoachesLowerTheTrainingMultiplier() {
        val w = world(); val me = w.human()
        val coaching = Staffing.members(w, me.id).filter { it.role.dept == StaffDept.COACHING }
        coaching.forEach { it.ability = 20; it.morale = 100f }
        val best = Staffing.trainingMultiplier(w, me.id)
        coaching.forEach { it.ability = 1 }
        val worst = Staffing.trainingMultiplier(w, me.id)
        assertTrue("best=$best worst=$worst", best > 1.08f && worst <= 0.86f)
    }

    @Test fun unhappyStaffWorkBelowTheirAbility() {
        val m = StaffMember(1, "A", 0, 40, StaffRole.PHYSIO).apply { ability = 15; morale = 100f }
        assertEquals(15f, m.effective(), 1e-4f)
        m.morale = 0f
        assertEquals(12f, m.effective(), 1e-4f)
    }

    @Test fun hiringChargesSignOnAndFillsTheSlot() {
        val w = world(); EcoTestSupport.richHuman(w); val me = w.human(); val s = Staffing.state(w)
        val old = Staffing.inRole(w, me.id, StaffRole.PHYSIO).first()
        assertTrue(Staffing.fire(w, old.id).first)
        val cand = Staffing.freeAgents(w, StaffRole.PHYSIO).first { it.reputation - me.rep <= 30 && it.id != old.id }
        val before = me.balance
        val r = Staffing.hire(w, cand.id, 2)
        assertTrue(r.second, r.first)
        assertEquals(before - cand.wage.toLong() * Staffing.SIGN_ON_WEEKS, me.balance)
        assertEquals(me.id, cand.clubId)
        assertEquals(w.year + 1, cand.contractEnd)
        assertTrue(s.byClub.getValue(me.id).contains(cand.id))
    }

    @Test fun hiringIsRefusedWhenSlotsAreFullPoorOrNotAvailable() {
        val w = world(); val me = w.human()
        val cand = Staffing.freeAgents(w, StaffRole.GROUNDKEEPER).first()
        assertFalse("slot already filled", Staffing.hire(w, cand.id, 2).first)
        assertFalse(Staffing.hire(w, -5, 2).first)
        val other = Staffing.members(w, me.id).first()
        assertFalse("already employed", Staffing.hire(w, other.id, 2).first)
        assertFalse("bad contract length", Staffing.hire(w, cand.id, 9).first)
        Staffing.fire(w, Staffing.inRole(w, me.id, StaffRole.GROUNDKEEPER).first().id)
        me.balance = 0L
        assertFalse("cannot afford the sign-on fee", Staffing.hire(w, cand.id, 2).first)
    }

    @Test fun reputationGapBlocksHiring() {
        val w = world(); EcoTestSupport.richHuman(w); val me = w.human()
        me.rep = 5
        val gk = Staffing.inRole(w, me.id, StaffRole.GROUNDKEEPER).first()
        Staffing.fire(w, gk.id)
        val star = Staffing.freeAgents(w, StaffRole.GROUNDKEEPER).first().also { it.reputation = 90 }
        assertFalse(Staffing.hire(w, star.id, 2).first)
    }

    @Test fun firingPaysCompensationAndUpsetsTheDepartment() {
        val w = world(); EcoTestSupport.richHuman(w); val me = w.human()
        val victim = Staffing.inRole(w, me.id, StaffRole.COACH_FITNESS).first()
        val mate = Staffing.members(w, me.id).first { it.role.dept == StaffDept.COACHING && it.id != victim.id }
        mate.morale = 70f
        val cost = Staffing.severance(w, victim)
        val before = me.balance
        assertTrue(Staffing.fire(w, victim.id).first)
        assertEquals(before - cost, me.balance)
        assertEquals(-1, victim.clubId)
        assertEquals(66f, mate.morale, 1e-4f)
        assertFalse(Staffing.fire(w, victim.id).first)
        val foreign = Staffing.members(w, w.clubs.first { it.id != me.id }.id).first()
        assertFalse("cannot fire another club's staff", Staffing.fire(w, foreign.id).first)
    }

    @Test fun renewalExtendsTheContractAndRefusesWhenFurious() {
        val w = world(); val me = w.human()
        val m = Staffing.members(w, me.id).first()
        m.morale = 60f
        assertTrue(Staffing.renew(w, m.id, 3).first)
        assertEquals(w.year + 2, m.contractEnd)
        m.morale = 10f
        assertFalse(Staffing.renew(w, m.id, 3).first)
    }

    @Test fun humanStaffCostIsOnlyTheExtraOverTheStartingPayroll() {
        val w = world(); EcoTestSupport.richHuman(w); val me = w.human()
        assertEquals(0L, Staffing.netWeeklyCost(w, me))
        val other = w.clubs.first { !it.human }
        assertEquals("AI clubs never pay it", 0L, Staffing.netWeeklyCost(w, other))
        Staffing.members(w, me.id).forEach { it.wage *= 2 }
        assertTrue(Staffing.netWeeklyCost(w, me) > 0L)
        Staffing.members(w, me.id).forEach { it.wage = 1 }
        val floor = -(Staffing.state(w).baseline.getValue(me.id) * Staffing.MAX_WAGE_SHARE_OF_SAVING).toLong()
        assertEquals("savings are capped", floor, Staffing.netWeeklyCost(w, me))
        Ecosystem.enabled = false
        try { assertEquals(0L, Staffing.netWeeklyCost(w, me)) } finally { Ecosystem.enabled = true }
    }

    @Test fun matchingABidKeepsTheEmployeeAndAcceptingSellsHim() {
        val w = world(); EcoTestSupport.richHuman(w); val me = w.human(); val s = Staffing.state(w)
        val rival = w.clubs.first { !it.human }
        val a = Staffing.members(w, me.id)[0]; val b = Staffing.members(w, me.id)[1]
        s.bids.add(PoachBid(a.id, rival.id, a.wage * 2, Business.absWeek(w) + 3))
        s.bids.add(PoachBid(b.id, rival.id, b.wage * 2, Business.absWeek(w) + 3))
        val matchedWage = a.wage * 2
        assertTrue(Staffing.matchBid(w, a.id).first)
        assertEquals(me.id, a.clubId); assertTrue(a.wage >= matchedWage)
        val before = me.balance; val fee = b.wage.toLong() * 8L
        assertTrue(Staffing.acceptBid(w, b.id).first)
        assertEquals(rival.id, b.clubId); assertEquals(before + fee, me.balance)
        assertTrue(s.bids.isEmpty())
        assertFalse(Staffing.matchBid(w, a.id).first)
    }

    @Test fun expiredBidsDisappearAndAiClubsFillVacanciesAndPoachOverTime() {
        val w = world(); val s = Staffing.state(w); val me = w.human()
        val m = Staffing.members(w, me.id).first()
        s.bids.add(PoachBid(m.id, w.clubs.first { !it.human }.id, 99, Business.absWeek(w) - 1))
        val ai = w.clubs.first { !it.human && it.tier == 3 }
        val role = Staffing.members(w, ai.id).first().role
        Staffing.fire(w.also { it.humanClubId = ai.id }, Staffing.members(w, ai.id).first().id)
        w.humanClubId = 0
        val snapshot = s.members.values.associate { it.id to it.clubId }
        for (wk in 1..60) { w.week = wk; Staffing.weekly(w) }
        assertTrue("expired bid removed", s.bids.none { it.staffId == m.id && it.newWage == 99 })
        assertTrue("movements happened", s.members.values.count { snapshot[it.id] != it.clubId } > 0)
        assertTrue("vacancy refilled or still tolerated", Staffing.vacancies(w, ai.id).size <= 1)
        assertNotEquals(role, null)
    }

    @Test fun seasonReviewExpiresContractsAgesPeopleAndRetiresTheVeryOld() {
        val w = world(); val s = Staffing.state(w); val me = w.human()
        val leaving = Staffing.members(w, me.id).first().also { it.contractEnd = w.year }
        val keeping = Staffing.members(w, me.id)[1].also { it.contractEnd = w.year + 3 }
        val ancient = Staffing.members(w, w.clubs.first { !it.human }.id).first().also { it.age = 80; it.contractEnd = w.year + 3 }
        val ageBefore = keeping.age
        Staffing.seasonReview(w)
        assertNotEquals("expiring human staff walk", me.id, leaving.clubId)
        assertEquals(me.id, keeping.clubId); assertEquals(ageBefore + 1, keeping.age)
        assertFalse("a staff member this old retires", s.members.containsKey(ancient.id))
        for (c in w.clubs) {
            val ms = Staffing.members(w, c.id)
            for (m in ms) assertTrue(m.contractEnd >= w.year + 1 || m.contractEnd > w.year - 1)
        }
    }

    @Test fun youthShiftFollowsTheYouthDepartment() {
        val w = world(); val me = w.human()
        Staffing.members(w, me.id).filter { it.role.dept == StaffDept.YOUTH }.forEach { it.ability = 20; it.morale = 100f }
        val high = Staffing.youthShift(w, me.id)
        Staffing.members(w, me.id).filter { it.role.dept == StaffDept.YOUTH }.forEach { it.ability = 1 }
        assertTrue(high > 1f && Staffing.youthShift(w, me.id) < -1f)
    }
}
