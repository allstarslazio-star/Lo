package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalTest {
    private fun world(seed: Long = 21L) = EcoTestSupport.world(seed)
    private val medicalRoles = listOf(StaffRole.DOCTOR, StaffRole.PHYSIO, StaffRole.SPORTS_SCIENTIST, StaffRole.COACH_FITNESS)

    /** Gives [club] a full medical and fitness department of the given ability (creating people where a role is empty). */
    private fun setQuality(w: World, club: Club, ability: Int) {
        val s = Staffing.state(w)
        for (role in medicalRoles) {
            val have = Staffing.inRole(w, club.id, role)
            val people = if (have.isNotEmpty()) have else listOf(StaffMember(s.nextId++, "Test " + role.label, 0, 40, role).also {
                it.clubId = club.id; s.members[it.id] = it; s.byClub.getOrPut(club.id) { ArrayList() }.add(it.id)
            })
            for (m in people) { m.ability = ability; m.morale = 100f }
        }
    }

    private fun removeMedical(w: World, club: Club) {
        val s = Staffing.state(w)
        for (role in medicalRoles) for (m in Staffing.inRole(w, club.id, role)) { s.members.remove(m.id); s.byClub[club.id]?.remove(m.id) }
    }

    private fun injureAll(w: World, club: Club, weeks: Int) {
        val s = MedicalRules.state(w)
        for (p in w.squad(club)) { p.injWeeks = weeks; p.injType = "muscle strain"; s.players.getOrPut(p.id) { PlayerMedical() }.lastInjWeeks = 0 }
    }

    private fun clear(w: World, club: Club) {
        val s = MedicalRules.state(w)
        for (p in w.squad(club)) { p.injWeeks = 0; p.injType = ""; s.players[p.id]?.lastInjWeeks = 0 }
    }

    // ------------------------------------------------------------------ load model

    @Test fun steadyWorkloadKeepsTheRatioNearOneAndASuddenJumpSpikesIt() {
        val w = world(); val me = w.human(); val p = w.squad(me).first()
        p.trainingIntensity = 1
        for (wk in 1..12) { w.week = wk; p.lastMinutes = 90; MedicalRules.weekly(w) }
        val pm = MedicalRules.state(w).players.getValue(p.id)
        assertEquals(1.0, MedicalRules.acwr(pm).toDouble(), 0.05)
        // A bench player suddenly plays every minute.
        val q = w.squad(me).last(); q.trainingIntensity = 1
        for (wk in 13..20) { w.week = wk; q.lastMinutes = 0; MedicalRules.weekly(w) }
        w.week = 21; q.lastMinutes = 90; MedicalRules.weekly(w)
        assertTrue("ratio ${MedicalRules.acwr(MedicalRules.state(w).players.getValue(q.id))}", MedicalRules.acwr(MedicalRules.state(w).players.getValue(q.id)) > 1.35f)
    }

    @Test fun firstObservationDoesNotCreateAFalseSpike() {
        val w = world(); val p = w.squad(w.human()).first(); p.lastMinutes = 90
        MedicalRules.weekly(w)
        assertEquals(1f, MedicalRules.acwr(MedicalRules.state(w).players.getValue(p.id)), 1e-6f)
    }

    @Test fun phasesFollowTheShareOfTheInjuryAlreadyServed() {
        val p = world().squad(world().human()).first()
        val pm = PlayerMedical().also { it.rehabTotal = 10 }
        p.injWeeks = 0; assertEquals(0, MedicalRules.phase(p, pm))
        p.injWeeks = 9; assertEquals(1, MedicalRules.phase(p, pm))
        p.injWeeks = 5; assertEquals(2, MedicalRules.phase(p, pm))
        p.injWeeks = 1; assertEquals(3, MedicalRules.phase(p, pm))
    }

    // ------------------------------------------------------------------ diagnosis

    @Test fun newInjuriesAreRecordedAndCounted() {
        val w = world(); val me = w.human(); val p = w.squad(me).first()
        p.injWeeks = 4; p.injType = "ankle knock"
        MedicalRules.weekly(w)
        val s = MedicalRules.state(w); val pm = s.players.getValue(p.id)
        assertEquals(1, pm.injuries); assertTrue(p.injWeeks in 1..30); assertEquals(p.injWeeks, pm.lastInjWeeks)
        assertEquals(1, s.log.size); assertEquals(p.id, s.log[0].playerId); assertFalse(s.log[0].relapse)
        assertEquals(1, s.seasonInjuries[me.id])
        // The same injury is not counted again the following week.
        p.injWeeks -= 1; w.week += 1
        MedicalRules.weekly(w)
        assertEquals(1, pm.injuries)
    }

    @Test fun aGoodMedicalTeamShortensInjuriesAndAPoorOneLengthensThem() {
        val w = world(); val club = w.clubs.first { it.tier == 0 && !it.human }
        fun meanWeeks(ability: Int): Double {
            setQuality(w, club, ability)
            var total = 0; var n = 0
            for (cycle in 1..12) {
                w.week = cycle; injureAll(w, club, 8)
                MedicalRules.weekly(w)
                for (p in w.squad(club)) { total += p.injWeeks; n++ }
                clear(w, club)
            }
            return total.toDouble() / n
        }
        val best = meanWeeks(20); val worst = meanWeeks(1)
        assertTrue("best $best should be well under worst $worst", best < worst - 0.8)
        assertTrue(best in 5.0..8.0 && worst in 8.0..11.0)
    }

    @Test fun rehabPolicyChangesDurationInTheExpectedDirection() {
        val w = world(); val me = w.human()
        removeMedical(w, me)
        fun meanWeeks(policy: RehabPolicy): Double {
            MedicalRules.state(w).policy = policy
            var total = 0; var n = 0
            for (cycle in 1..12) {
                w.week = cycle; injureAll(w, me, 10)
                MedicalRules.weekly(w)
                for (p in w.squad(me)) { total += p.injWeeks; n++ }
                clear(w, me)
            }
            return total.toDouble() / n
        }
        val cautious = meanWeeks(RehabPolicy.CAUTIOUS); val standard = meanWeeks(RehabPolicy.STANDARD); val fast = meanWeeks(RehabPolicy.ACCELERATED)
        assertTrue("$fast < $standard < $cautious", fast < standard - 0.8 && standard < cautious - 0.8)
    }

    @Test fun roundingKeepsTheExpectedValueSoShortInjuriesStillRespondToPolicy() {
        val w = world(); val me = w.human(); removeMedical(w, me)
        MedicalRules.state(w).policy = RehabPolicy.CAUTIOUS
        var total = 0; var n = 0
        for (cycle in 1..30) { w.week = cycle; injureAll(w, me, 2); MedicalRules.weekly(w); for (p in w.squad(me)) { total += p.injWeeks; n++ }; clear(w, me) }
        val expected = 2.0 * 1.15 * (1.0 - 0.18 * MedicalRules.diagnosisQuality(w, me.id))
        assertEquals(expected, total.toDouble() / n, 0.12)
    }

    // ------------------------------------------------------------------ return and relapse

    @Test fun returningPlayersEnterAndLeaveTheRelapseWindow() {
        val w = world(); val me = w.human(); val p = w.squad(me).first()
        p.injWeeks = 2; p.injType = "muscle strain"
        MedicalRules.weekly(w)
        val pm = MedicalRules.state(w).players.getValue(p.id)
        p.injWeeks = 0; p.lastMinutes = 0; w.week += 1
        MedicalRules.weekly(w)
        assertEquals(0, pm.sinceReturn)
        for (i in 1..MedicalRules.RELAPSE_WINDOW) { w.week += 1; MedicalRules.weekly(w); if (p.injWeeks > 0) return; assertEquals(i, pm.sinceReturn) }
        w.week += 1; MedicalRules.weekly(w)
        assertEquals(-1, pm.sinceReturn)
    }

    private fun relapses(w: World, policy: RehabPolicy, cycles: Int): Int {
        val me = w.human(); val s = MedicalRules.state(w); s.policy = policy
        removeMedical(w, me)
        var n = 0
        for (cycle in 1..cycles) {
            w.week = cycle
            for (p in w.squad(me)) {
                val pm = s.players.getOrPut(p.id) { PlayerMedical() }
                p.injWeeks = 0; pm.lastInjWeeks = 0; pm.sinceReturn = 0; pm.rehabTotal = 5; pm.injuries = 1; pm.lastType = "muscle strain"
                p.lastMinutes = 90; p.trainingIntensity = 1; pm.seeded = true; pm.acute = 115f; pm.chronic = 115f
            }
            MedicalRules.weekly(w)
            for (p in w.squad(me)) if (p.injWeeks > 0) { n++; assertTrue(s.players.getValue(p.id).relapses >= 1) }
        }
        return n
    }

    @Test fun aggressiveRehabRelapsesMoreOftenThanCautiousRehab() {
        val w = world()
        val cautious = relapses(w, RehabPolicy.CAUTIOUS, 80)
        val fast = relapses(w, RehabPolicy.ACCELERATED, 80)
        assertTrue("cautious $cautious, accelerated $fast", fast > cautious * 2 && fast > 20)
    }

    @Test fun aRelapseLastsAboutAThirdLongerThanTheFirstInjury() {
        val w = world(); val me = w.human(); val s = MedicalRules.state(w); s.policy = RehabPolicy.ACCELERATED
        removeMedical(w, me)
        val lengths = ArrayList<Int>()
        for (cycle in 1..120) {
            w.week = cycle
            for (p in w.squad(me)) {
                val pm = s.players.getOrPut(p.id) { PlayerMedical() }
                p.injWeeks = 0; pm.lastInjWeeks = 0; pm.sinceReturn = 0; pm.rehabTotal = 6; pm.lastType = "x"; p.lastMinutes = 90
            }
            MedicalRules.weekly(w)
            for (p in w.squad(me)) if (p.injWeeks > 0) lengths.add(p.injWeeks)
        }
        assertTrue(lengths.size > 20)
        // round(6 * 1.33) = 8, then the diagnosis quality and the accelerated policy (x0.85); stochastic rounding keeps the mean.
        assertEquals(8.0 * (1.0 - 0.18 * MedicalRules.diagnosisQuality(w, me.id)) * 0.85, lengths.average(), 0.6)
    }

    // ------------------------------------------------------------------ overuse and match-day penalty

    private fun overuseCount(w: World, monitored: Boolean, cycles: Int): Int {
        val me = w.human(); val s = MedicalRules.state(w)
        removeMedical(w, me)
        if (monitored) setQuality(w, me, 20)
        var n = 0
        for (cycle in 1..cycles) {
            w.week = cycle
            for (p in w.squad(me)) {
                val pm = s.players.getOrPut(p.id) { PlayerMedical() }
                p.injWeeks = 0; pm.lastInjWeeks = 0; pm.sinceReturn = -1; p.lastMinutes = 90; p.trainingIntensity = 1
                pm.seeded = true; pm.acute = 40f; pm.chronic = 40f
            }
            MedicalRules.weekly(w)
            n += w.squad(me).count { it.injWeeks > 0 && it.injType == "overuse strain" }
        }
        return n
    }

    @Test fun overuseInjuriesHappenAfterWorkloadSpikesAndMonitoringReducesThem() {
        val w = world()
        val unmonitored = overuseCount(w, false, 250)
        val monitored = overuseCount(w, true, 250)
        assertTrue("unmonitored $unmonitored vs monitored $monitored", unmonitored > 25 && monitored < unmonitored * 0.75)
    }

    @Test fun normalWorkloadsProduceNoOveruseInjuries() {
        val w = world(); val me = w.human(); val s = MedicalRules.state(w)
        var n = 0
        for (cycle in 1..60) {
            w.week = cycle
            for (p in w.squad(me)) {
                val pm = s.players.getOrPut(p.id) { PlayerMedical() }
                p.injWeeks = 0; pm.lastInjWeeks = 0; pm.sinceReturn = -1; p.lastMinutes = 90; p.trainingIntensity = 1
                pm.seeded = true; pm.acute = 115f; pm.chronic = 115f
            }
            MedicalRules.weekly(w)
            n += w.squad(me).count { it.injWeeks > 0 }
        }
        assertEquals(0, n)
    }

    @Test fun overloadPenaltyIsBoundedPureAndReducedByMonitoring() {
        val w = world(); val me = w.human(); val p = w.squad(me).first()
        assertNull("no medical section yet", w.ecosystem?.medical)
        assertEquals(0f, MedicalRules.overloadPenalty(w, me, p), 0f)
        assertNull("the provider must not create state", w.ecosystem?.medical)
        val pm = MedicalRules.state(w).players.getOrPut(p.id) { PlayerMedical() }
        pm.seeded = true; pm.chronic = 40f; pm.acute = 200f
        removeMedical(w, me)
        val bare = MedicalRules.overloadPenalty(w, me, p)
        assertTrue(bare < 0f && bare >= -MedicalRules.OVERLOAD_PENALTY_CAP * 1.4f - 1e-4f)
        setQuality(w, me, 20)
        val staffed = MedicalRules.overloadPenalty(w, me, p)
        assertTrue("$staffed should be milder than $bare", staffed > bare)
        pm.acute = pm.chronic
        assertEquals(0f, MedicalRules.overloadPenalty(w, me, p), 0f)
    }

    private class Huge(val v: Float) : PlayerFactorProvider {
        override fun conditionDelta(w: World, club: Club, p: Player, ctx: MatchContext) = v
    }

    @Test fun perPlayerProvidersAreCappedAppliedAndRemovedExactly() {
        val w = world(); val me = w.human(); val opp = w.clubs.first { it.id != me.id }
        val rnd = kotlin.random.Random(5)
        val home = MatchTeams.build(w, me, opp, true, rnd); val away = MatchTeams.build(w, opp, me, false, rnd)
        val before = (home.xi + home.bench).associate { it.p.id to it.p.condition }
        Ecosystem.withProviders(emptyList(), listOf(Huge(-99f))) { Ecosystem.beginMatch(w, home, away) }
        for (ps in home.xi + home.bench) {
            val diff = ps.p.condition - before.getValue(ps.p.id)
            assertTrue("applied $diff", diff >= -ModifierCaps.CONDITION_PER_FACTOR - 1e-4f && diff <= 0f)
        }
        assertTrue(w.ecosystem!!.applied.isNotEmpty())
        Ecosystem.endMatch(w)
        for (ps in home.xi + home.bench) assertEquals(before.getValue(ps.p.id), ps.p.condition, 1e-3f)
        assertTrue(w.ecosystem!!.applied.isEmpty())
        // NaN and no providers do nothing.
        Ecosystem.withProviders(emptyList(), listOf(Huge(Float.NaN))) { Ecosystem.beginMatch(w, home, away) }
        assertTrue(w.ecosystem!!.applied.isEmpty())
    }

    // ------------------------------------------------------------------ human actions

    @Test fun rehabPolicyActionExplainsItself() {
        val w = world()
        val same = MedicalRules.setPolicy(w, RehabPolicy.STANDARD)
        assertFalse(same.first); assertTrue(same.second.contains("already"))
        val r = MedicalRules.setPolicy(w, RehabPolicy.CAUTIOUS)
        assertTrue(r.first); assertEquals(RehabPolicy.CAUTIOUS, MedicalRules.state(w).policy)
        assertTrue(r.second.contains("injuries diagnosed from now on"))
    }

    @Test fun loadAdviceNeedsMonitoringThenLightensTheRightPlayersAndCanBeReleased() {
        val w = world(); val me = w.human(); val s = MedicalRules.state(w)
        removeMedical(w, me)
        val refused = MedicalRules.applyLoadAdvice(w)
        assertFalse(refused.first); assertTrue(refused.second.contains("sports scientist"))
        setQuality(w, me, 15)
        assertFalse("nobody flagged yet", MedicalRules.applyLoadAdvice(w).first)
        val flagged = w.squad(me).filter { it.injWeeks == 0 }.take(3)
        for (p in flagged) { p.trainingIntensity = 1; val pm = s.players.getOrPut(p.id) { PlayerMedical() }; pm.seeded = true; pm.chronic = 40f; pm.acute = 100f }
        val ok = MedicalRules.applyLoadAdvice(w)
        assertTrue(ok.first && ok.second.startsWith("3"))
        assertTrue(flagged.all { it.trainingIntensity == 0 } && s.advised.size == 3)
        val stillFlagged = MedicalRules.releaseLoadAdvice(w)
        assertFalse("still flagged, so nobody is released", stillFlagged.first)
        for (p in flagged) s.players.getValue(p.id).acute = s.players.getValue(p.id).chronic
        val released = MedicalRules.releaseLoadAdvice(w)
        assertTrue(released.first); assertTrue(flagged.all { it.trainingIntensity == 1 } && s.advised.isEmpty())
    }

    @Test fun riskTableIsSafeWithNoDataAndExplainsMissingMonitoring() {
        val w = world(); val me = w.human()
        removeMedical(w, me)
        val rows = MedicalRules.riskRows(w)
        assertEquals(me.players.size, rows.size)
        assertTrue(rows.all { it.risk == -1 || it.player.injWeeks > 0 })
        assertTrue(rows.first { it.risk == -1 }.note.contains("Not monitored"))
        setQuality(w, me, 12)
        assertTrue(MedicalRules.riskRows(w).all { it.risk >= 0 })
        assertEquals(0, MedicalRules.summary(w, me.id).seasonInjuries)
    }

    @Test fun trainingInjuryMultiplierIsBoundedAndNeutralWhenDisabled() {
        val w = world()
        val m = w.clubs.map { MedicalRules.trainingInjuryMultiplier(w, it.id) }
        assertTrue(m.all { it in 0.75f..1.25f }); assertEquals(1.0, m.average().toDouble(), 0.04)
        Ecosystem.enabled = false
        try { assertTrue(Ecosystem.trainingInjuryMultipliers(w).all { it == 1f }) } finally { Ecosystem.enabled = true }
    }

    @Test fun seasonReviewResetsSeasonCountersButKeepsTheHistory() {
        val w = world(); val me = w.human(); val p = w.squad(me).first()
        p.injWeeks = 3; p.injType = "x"; MedicalRules.weekly(w)
        val s = MedicalRules.state(w)
        assertEquals(1, s.seasonInjuries[me.id])
        MedicalRules.seasonReview(w)
        assertTrue(s.seasonInjuries.isEmpty() && s.seasonWeeksLost.isEmpty())
        assertEquals(1, s.players.getValue(p.id).injuries); assertEquals(1, s.log.size)
    }

    @Test fun medicalLogIsCappedAndLabelsCoverTheRange() {
        val w = world(); val me = w.human(); val s = MedicalRules.state(w)
        for (i in 1..80) s.log.add(0, InjuryRecord(1, 2026, 1, "x", 1, false))
        val p = w.squad(me).first(); p.injWeeks = 2; p.injType = "x"; MedicalRules.weekly(w)
        assertEquals(MedicalRules.LOG_LIMIT, s.log.size)
        assertEquals("excellent", MedicalRules.label(0.9f)); assertEquals("poor", MedicalRules.label(-0.9f)); assertNotNull(MedicalRules.label(0f))
    }
}
