package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityTest {
    private fun world(seed: Long = 41L) = EcoTestSupport.world(seed)

    @Test fun homegrownRegistryStartsFromYoungLongServingPlayers() {
        val w = world(); val s = Identity.state(w)
        assertTrue(s.homegrown.isNotEmpty())
        for ((pid, cid) in s.homegrown) { val p = w.players.getValue(pid); assertEquals(cid, p.clubId); assertTrue(p.age <= 25) }
        assertEquals(w.clubs.size, s.culture.size)
    }

    @Test fun youthIntakeRegistersNewPlayersAsHomegrown() {
        val w = world(); val me = w.human()
        val before = Identity.homegrownCount(w, me.id)
        w.week = 34
        Game.completeWeek(w)
        assertEquals("season rolled over", 0, w.week)
        assertTrue(before > 0)
        val fresh = w.squad(me).filter { it.age == 16 }
        assertTrue(fresh.isNotEmpty())
        for (p in fresh) assertEquals(me.id, Identity.state(w).homegrown[p.id])
        assertTrue(Identity.homegrownCount(w, me.id) >= fresh.size)
    }

    @Test fun quotaScalesWithTier() {
        val w = world()
        assertEquals(8, Identity.quota(w.clubs.first { it.tier == 0 }))
        assertEquals(6, Identity.quota(w.clubs.first { it.tier == 3 }))
        assertEquals(4, Identity.quota(w.clubs.first { it.tier == 6 }))
    }

    @Test fun academyMinutePercentTracksHomegrownYouthMinutes() {
        val w = world(); val me = w.human(); val s = Identity.state(w)
        w.squad(me).forEach { it.lastMinutes = 0 }
        val kids = w.squad(me).take(3).onEach { it.age = 19; s.homegrown[it.id] = me.id; it.lastMinutes = 90 }
        val adult = w.squad(me).filter { it !in kids }.first().also { it.lastMinutes = 90; it.age = 27 }
        Identity.weekly(w)
        assertEquals(75, Identity.academyMinutePercent(w, me.id))
        Identity.seasonReview(w)
        assertEquals(0, Identity.academyMinutePercent(w, me.id))
        assertTrue(adult.lastMinutes == 90)
    }

    @Test fun sustainabilityComponentsAreBoundedAndRespondToTheirInputs() {
        val w = world(); val me = w.human()
        val base = Identity.sustainability(w, me)
        for (v in listOf(base.finance, base.squadAge, base.youth, base.contracts, base.total)) assertTrue(v in 0..100)
        me.balance = -me.revenue
        assertTrue(Identity.sustainability(w, me).finance < base.finance)
        w.squad(me).forEach { it.contractEnd = w.year }
        assertEquals(0, Identity.sustainability(w, me).contracts)
    }

    @Test fun cultureMovesSlowlyAndStaysInRange() {
        val w = world(); val me = w.human(); val cul = Identity.culture(w, me.id)
        me.lastPts = 3
        repeat(30) { Identity.weekly(w) }
        assertTrue(cul.winning > 60f && cul.winning <= 100f)
        assertTrue(Identity.moraleFactor(w, me.id) in -1.5f..1.5f)
        cul.winning = 100f; cul.stability = 100f; cul.youth = 100f
        assertEquals(1.5f, Identity.moraleFactor(w, me.id), 1e-4f)
    }

    @Test fun effectiveYouthUsesStaffOnlyWhenEnabled() {
        val w = world(); val me = w.human()
        Staffing.members(w, me.id).filter { it.role.dept == StaffDept.YOUTH }.forEach { it.ability = 20; it.morale = 100f }
        assertTrue(Identity.effectiveYouth(w, me) > me.youth)
        Ecosystem.enabled = false
        try { assertEquals(me.youth.toFloat(), Identity.effectiveYouth(w, me), 0f) } finally { Ecosystem.enabled = true }
        assertTrue(Identity.academyRating(w, me) in 0..100)
    }
}
