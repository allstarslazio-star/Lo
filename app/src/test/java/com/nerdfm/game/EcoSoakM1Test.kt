package com.nerdfm.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Headless multi-season runs for the M1 systems (staff, board, identity) through the real game loop. */
class EcoSoakM1Test {
    @get:Rule val temp = TemporaryFolder()

    private fun checkInvariants(w: World, label: String) {
        assertEquals(label, emptyList<WorldIntegrity.Issue>(), WorldIntegrity.inspect(w))
        val s = Staffing.state(w)
        for (c in w.clubs) {
            val ms = Staffing.members(w, c.id)
            for (role in StaffRole.values()) assertTrue("$label ${c.name} too many ${role.label}", ms.count { it.role == role } <= role.slots)
            for (m in ms) { assertEquals(c.id, m.clubId); assertTrue(m.ability in 1..20 && m.wage >= 40 && m.morale in 0f..100f && m.age in 20..90) }
            assertTrue("$label ${c.name} has ${Staffing.vacancies(w, c.id).size} vacancies", c.human || Staffing.vacancies(w, c.id).size <= 4)
            assertTrue("$label ${c.name} squad ${c.players.size}", c.players.size in 11..45 || !c.human)
        }
        for ((id, list) in s.byClub) for (mid in list) assertEquals("$label byClub mismatch", id, s.members.getValue(mid).clubId)
        assertTrue("$label pool size ${s.members.values.count { it.clubId == -1 }}", s.members.values.count { it.clubId == -1 } < 500)
        val board = BoardRules.state(w)
        for (b in board.clubs.values) {
            assertTrue(b.owner.patience in 1..20 && b.owner.wealth in 1..20 && b.owner.ambition in 1..20)
            assertTrue(b.director.alignment in 0f..100f && b.freezeWeeks in 0..30)
        }
        assertTrue(board.clubs.getValue(w.humanClubId).objectives.isNotEmpty())
        val me = w.human()
        assertTrue("$label human balance ${me.balance}", me.balance > -(me.revenue * 0.6).toLong())
    }

    @Test fun threeSeasonsRunCleanlyAcrossSeeds() {
        for (seed in longArrayOf(51L, 52L)) {
            val w = EcoTestSupport.world(seed)
            val startYear = w.year
            repeat(3) {
                EcoTestSupport.play(w, 34)
                checkInvariants(w, "seed $seed after season ${it + 1}")
            }
            assertEquals(startYear + 3, w.year)
        }
    }

    @Test fun stateSurvivesSaveAndLoadAfterPlay() {
        val w = EcoTestSupport.world(53L)
        EcoTestSupport.play(w, 20)
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, w))
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        val a = Staffing.state(w); val b = Staffing.state(loaded)
        assertEquals(a.members.size, b.members.size)
        assertEquals(a.baseline, b.baseline)
        assertEquals(BoardRules.state(w).clubs.getValue(w.humanClubId).director.alignment, BoardRules.state(loaded).clubs.getValue(loaded.humanClubId).director.alignment, 0f)
        assertEquals(Identity.state(w).homegrown, Identity.state(loaded).homegrown)
        checkInvariants(loaded, "loaded")
        EcoTestSupport.play(loaded, 14)
        checkInvariants(loaded, "loaded+14 weeks")
    }

    @Test fun humanStaffSpendingStaysWithinSanityOverASeason() {
        val w = EcoTestSupport.world(54L)
        val me = w.human()
        val start = me.balance
        EcoTestSupport.play(w, 34)
        // Spending the staff budget exactly as generated costs nothing extra; the season must not swing by more than the club's revenue.
        assertTrue("balance moved from $start to ${me.balance}", Math.abs(me.balance - start) < me.revenue)
    }
}
