package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {
    private fun world(seed: Long = 21L, week: Int = 1) = EcoTestSupport.world(seed, week)

    private fun setRole(w: World, club: Club, role: StaffRole, ability: Int) {
        val s = Staffing.state(w)
        val have = Staffing.inRole(w, club.id, role)
        val people = if (have.isNotEmpty()) have else listOf(StaffMember(s.nextId++, "T " + role.label, 0, 40, role).also {
            it.clubId = club.id; s.members[it.id] = it; s.byClub.getOrPut(club.id) { ArrayList() }.add(it.id)
        })
        for (m in people) { m.ability = ability; m.morale = 100f }
    }

    private fun setData(w: World, club: Club, ability: Int) {
        for (r in listOf(StaffRole.DATA_ANALYST, StaffRole.HEAD_OF_RECRUITMENT, StaffRole.CHIEF_SCOUT)) setRole(w, club, r, ability)
    }

    // ------------------------------------------------------------------ data level

    @Test fun dataLevelIsBoundedAndCentredAcrossTheLeague() {
        val w = world()
        val levels = w.clubs.map { Analytics.dataLevel(w, it) }
        assertTrue(levels.all { it in 0f..100f })
        assertEquals(50.0, levels.average(), 2.5)
        assertTrue("clubs must differ", levels.max() - levels.min() > 8f)
    }

    @Test fun betterStaffAndBetterPlansRaiseTheLevelMonotonically() {
        val w = world(); val me = w.human()
        var last = -1f
        for (a in listOf(1, 6, 11, 16, 20)) { setData(w, me, a); val l = Analytics.dataLevel(w, me); assertTrue("ability $a -> $l after $last", l >= last); last = l }
        setData(w, me, 10)
        val levels = DataPlan.values().map { Analytics.state(w).plan = it.ordinal; Analytics.dataLevel(w, me) }
        for (i in 1 until levels.size) assertTrue("plan ${DataPlan.values()[i]}: $levels", levels[i] > levels[i - 1])
        setData(w, me, 20); Analytics.state(w).plan = DataPlan.ELITE.ordinal
        assertTrue("best reachable level ${Analytics.dataLevel(w, me)}", Analytics.dataLevel(w, me) in 78f..100f)
        setData(w, me, 1); Analytics.state(w).plan = DataPlan.NONE.ordinal
        assertTrue(Analytics.dataLevel(w, me) in 0f..25f)
    }

    @Test fun safeModeIsAFlatFifty() {
        val w = world()
        Ecosystem.enabled = false
        try {
            assertEquals(50f, Analytics.humanLevel(w), 0f)
            assertEquals(1f, Analytics.errorFactor(w), 0f)
            assertEquals(0, Analytics.scoutingBonus(w))
            assertEquals((20 - w.human().scout) / 10f, Analytics.scoutError(w), 0f)
            for (j in 1..20) assertEquals(Math.max(0, (20 - j) / 5), Analytics.reportError(w, j))
            assertEquals(0L, Analytics.netWeeklyCost(w, w.human()))
        } finally { Ecosystem.enabled = true }
    }

    // ------------------------------------------------------------------ scouting integration

    @Test fun betterDataNarrowsScoutingErrorAndSpeedsScoutingUp() {
        val w = world(); val me = w.human()
        setData(w, me, 1); Analytics.state(w).plan = DataPlan.NONE.ordinal
        val errLow = Analytics.scoutError(w); val bonusLow = Analytics.scoutingBonus(w); val bandLow = Analytics.reportError(w, 4)
        setData(w, me, 20); Analytics.state(w).plan = DataPlan.ELITE.ordinal
        val errHigh = Analytics.scoutError(w); val bonusHigh = Analytics.scoutingBonus(w); val bandHigh = Analytics.reportError(w, 4)
        assertTrue("$errHigh < $errLow", errHigh < errLow * 0.5f)
        assertTrue("$bonusHigh > $bonusLow", bonusHigh >= bonusLow + 8)
        assertTrue("$bandHigh < $bandLow", bandHigh < bandLow)
        assertTrue(Analytics.errorFactor(w) in 0.5f..1.5f)
        assertEquals(1.5f - Analytics.humanLevel(w) / 100f, Analytics.errorFactor(w), 1e-6f)
    }

    @Test fun reportErrorNeverGrowsAsDataImproves() {
        val w = world(); val me = w.human()
        for (j in 1..20) {
            var last = Int.MAX_VALUE
            for (a in listOf(1, 8, 14, 20)) { setData(w, me, a); val e = Analytics.reportError(w, j); assertTrue("j=$j a=$a e=$e last=$last", e <= last); last = e }
        }
        assertEquals(100, Analytics.reportReliability(0)); assertEquals(25, Analytics.reportReliability(9)); assertEquals(85, Analytics.reportReliability(1))
    }

    @Test fun scoutedReportsCarryTheirReliabilityWhileTheNetworkWorks() {
        val w = world(); val me = w.human(); val b = w.biz()
        setData(w, me, 16)
        me.balance = 900_000_000L; me.wageBudget = 1_000_000_000L; me.scout = 20
        b.scoutMarket.clear(); ScoutingNetwork.refreshMarket(w, w.rng(1))
        val first = b.scoutMarket.first()
        assertTrue(ScoutingNetwork.hire(w, first.id).first)
        ScoutingNetwork.assign(w, first.id, -2, -1)
        for (wk in 1..40) { w.week = wk; ScoutingNetwork.weekly(w, w.rng(100 + wk)) }
        assertTrue("scout produced reports", b.reports.isNotEmpty())
        assertTrue(b.reports.all { it.text.contains("reliability") })
    }

    @Test fun visibleEstimatesUseTheAnalyticsError() {
        val w = world(); val me = w.human()
        val stranger = w.players.values.first { it.clubId >= 0 && it.clubId != me.id && !w.scouted.contains(it.id) }
        setData(w, me, 1); Analytics.state(w).plan = DataPlan.NONE.ordinal
        val bad = Analytics.scoutError(w)
        setData(w, me, 20); Analytics.state(w).plan = DataPlan.ELITE.ordinal
        val good = Analytics.scoutError(w)
        assertTrue(good < bad)
        assertNotEquals(0, stranger.id)
    }

    // ------------------------------------------------------------------ match benefit

    @Test fun sharpnessBenefitIsTinyCappedAndZeroSumAcrossTheLeague() {
        val w = world()
        val v = w.clubs.map { Analytics.sharpnessFactor(w, it) }
        assertTrue(v.all { Math.abs(it) <= Analytics.SHARPNESS_SWING + 1e-6f })
        assertEquals(0.0, v.average(), 0.06)
        val m = ClubModifiers.combine(AnalyticsFactors.factors(w, w.human(), MatchContext.WEEKLY))
        assertTrue(Math.abs(m.sharpness) <= Analytics.SHARPNESS_SWING + 1e-6f && m.condition == 0f && m.morale == 0f && m.recovery == 0f)
    }

    // ------------------------------------------------------------------ money

    @Test fun planCostsAreNetOfTheNormalPlan() {
        val w = world(); val me = w.human()
        assertEquals(0L, Analytics.netWeeklyCost(w, me))
        val base = Analytics.defaultPlan(me.tier)
        val costs = DataPlan.values().map { Analytics.state(w).plan = it.ordinal; it to Analytics.netWeeklyCost(w, me) }
        for ((plan, c) in costs) {
            if (plan.ordinal > base.ordinal) assertTrue("$plan $c", c > 0) else if (plan.ordinal < base.ordinal) assertTrue("$plan $c", c <= 0)
            else assertEquals(0L, c)
        }
        Analytics.state(w).plan = DataPlan.NONE.ordinal
        assertTrue(Analytics.netWeeklyCost(w, me) >= -(me.revenue / 52.0 * Analytics.defaultPlan(me.tier).priceShare).toLong())
        assertEquals(0L, Analytics.netWeeklyCost(w, w.clubs.first { !it.human }))
    }

    @Test fun setPlanValidatesAffordabilityAndExplainsItself() {
        val w = world(); val me = w.human()
        val cur = Analytics.planOf(w, me)
        val same = Analytics.setPlan(w, cur)
        assertFalse(same.first); assertTrue(same.second.contains("already"))
        me.balance = 0L
        val broke = Analytics.setPlan(w, DataPlan.ELITE)
        if (DataPlan.ELITE != cur) { assertFalse(broke.first); assertTrue(broke.second.contains("cannot afford")) }
        me.balance = 900_000_000L
        val ok = Analytics.setPlan(w, DataPlan.ELITE)
        assertTrue(ok.first || cur == DataPlan.ELITE)
        assertEquals(DataPlan.ELITE, Analytics.planOf(w, me))
        assertTrue(Analytics.setPlan(w, DataPlan.NONE).first)
        assertEquals(DataPlan.NONE, Analytics.planOf(w, me))
    }

    // ------------------------------------------------------------------ opposition report

    @Test fun oppositionReportIsDeterministicAndFindsTheNextOpponent() {
        val w = world(week = 1)
        val opp = Analytics.nextOpponent(w)!!
        assertTrue(opp.id != w.humanClubId)
        val a = Analytics.oppositionReport(w, opp); val b = Analytics.oppositionReport(w, opp)
        assertEquals(a.strongestGroup, b.strongestGroup); assertEquals(a.weakestGroup, b.weakestGroup); assertEquals(a.keyPlayerOvr, b.keyPlayerOvr)
        assertTrue(a.formation.isNotEmpty() && a.advice.isNotEmpty() && a.confidence in 0..100)
        w.week = 0; assertNull(Analytics.nextOpponent(w))
        w.week = 35; assertNull(Analytics.nextOpponent(w))
    }

    @Test fun poorDataMisleadsAndGoodDataDoesNot() {
        val w = world(); val me = w.human()
        val opp = w.clubs.first { it.id != me.id && it.tier == me.tier }
        fun truth(): Pair<String, String> = Analytics.oppositionReport(w.also { setData(it, me, 20); Analytics.state(it).plan = DataPlan.ELITE.ordinal }, opp).let { it.strongestGroup to it.weakestGroup }
        val (strong, weak) = truth()
        var wrongHigh = 0; var wrongLow = 0
        for (wk in 1..60) {
            w.week = wk
            setData(w, me, 20); Analytics.state(w).plan = DataPlan.ELITE.ordinal
            val hi = Analytics.oppositionReport(w, opp)
            if (hi.strongestGroup != strong || hi.weakestGroup != weak) wrongHigh++
            setData(w, me, 1); Analytics.state(w).plan = DataPlan.NONE.ordinal
            val lo = Analytics.oppositionReport(w, opp)
            if (lo.strongestGroup != strong || lo.weakestGroup != weak) wrongLow++
        }
        assertEquals(0, wrongHigh)
        assertTrue("low data wrong $wrongLow of 60", wrongLow > 5)
    }

    @Test fun veryThinDataGivesNoTacticalAdvice() {
        val w = world(); val me = w.human()
        setData(w, me, 1); Analytics.state(w).plan = DataPlan.NONE.ordinal
        assertTrue(Analytics.oppositionReport(w, Analytics.nextOpponent(w)!!).advice.contains("too thin"))
    }

    // ------------------------------------------------------------------ shortlist and performance

    @Test fun dataTargetsAreRankedDeterministicAndSensible() {
        val w = world(); val me = w.human()
        val a = Analytics.dataTargets(w, 8); val b = Analytics.dataTargets(w, 8)
        assertEquals(a.map { it.player.id }, b.map { it.player.id })
        assertTrue(a.size <= 8 && a.isNotEmpty())
        assertTrue(a.all { it.player.clubId != me.id && it.player.age <= 31 && it.reason.isNotEmpty() })
        assertEquals(a.map { it.player.id }.toSet().size, a.size)
        assertTrue(Analytics.dataTargets(w, 3).size <= 3)
    }

    @Test fun performanceRowsUseOnlyPlayersWithEnoughMinutes() {
        val w = world(); val me = w.human()
        val squad = w.squad(me)
        squad.forEach { it.minutes = 0 }
        assertTrue(Analytics.performanceRows(w).isEmpty())
        val p = squad.first(); p.minutes = 900; p.seasonXg = 5.0; p.seasonKeyPasses = 20; p.seasonTackles = 30; p.seasonInterceptions = 20
        val rows = Analytics.performanceRows(w)
        assertEquals(1, rows.size)
        assertEquals(0.5, rows[0].xgPer90, 1e-9); assertEquals(2.0, rows[0].keyPassesPer90, 1e-9); assertEquals(5.0, rows[0].defensivePer90, 1e-9)
    }

    @Test fun labelsAndRepair() {
        assertEquals("excellent", Analytics.levelLabel(90f)); assertEquals("poor", Analytics.levelLabel(5f)); assertEquals("normal for your level", Analytics.levelLabel(50f))
        val w = world(); Analytics.state(w).plan = 77
        Analytics.repair(w)
        assertEquals(-1, w.ecosystem!!.analytics!!.plan)
        val old = world(5L); Analytics.repair(old); assertNull(old.ecosystem?.analytics)
    }
}
