package com.nerdfm.game

import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** M6 (calendar, cups, continental, internationals, travel, referees), save format 10. */
class CalendarTest {
    @get:Rule val temp = TemporaryFolder()

    private fun check(w: World, label: String) {
        assertEquals(label, emptyList<WorldIntegrity.Issue>(), WorldIntegrity.inspect(w))
        val s = FixtureCalendar.state(w)
        assertTrue(s.cupAlive.all { it in w.clubs.indices })
        for ((id, n) in s.seasonGames) assertTrue("$label club $id played $n extra games", n <= FixtureCalendar.CUP_WEEKS.size + FixtureCalendar.CONT_WEEKS.size)
        for (t in s.cupTies + s.contTies) assertTrue(t.played && t.hg >= 0 && t.ag >= 0)
    }

    private fun seasonSignature(seed: Long): List<Any> {
        val w = EcoTestSupport.world(seed, 1)
        EcoTestSupport.play(w, 33)
        val s = FixtureCalendar.state(w)
        return listOf(s.cupWinner, s.cupTies.map { "${it.round}:${it.home}:${it.away}:${it.hg}:${it.ag}:${it.winner}" }, s.contClubs.toList(), s.contTies.size)
    }

    @Test fun theThreeCompetitionsNeverShareAWeek() {
        val all = FixtureCalendar.CUP_WEEKS.toList() + FixtureCalendar.CONT_WEEKS.toList() + FixtureCalendar.INTL_WEEKS.toList()
        assertEquals("a week is used twice", all.size, all.toSet().size)
        assertTrue(all.all { it in 1..34 })
        assertEquals(7, FixtureCalendar.CUP_WEEKS.size); assertEquals(8, FixtureCalendar.CONT_WEEKS.size)
    }

    @Test fun theCupDrawIsCompleteAndDeterministic() {
        val a = EcoTestSupport.world(111L, 1)
        EcoTestSupport.play(a, 33)
        val s = FixtureCalendar.state(a)
        assertEquals("single elimination over 126 clubs needs 125 ties", 125, s.cupTies.size)
        assertTrue(s.cupWinner in a.clubs.indices)
        assertEquals(1, s.cupAlive.size)
        val perClub = HashMap<Int, Int>()
        for (t in s.cupTies) { perClub[t.home] = (perClub[t.home] ?: 0) + 1; perClub[t.away] = (perClub[t.away] ?: 0) + 1 }
        assertTrue(perClub.values.all { it <= 7 })
        assertEquals(seasonSignature(111L), seasonSignature(111L))
        check(a, "after cup season")
    }

    @Test fun theContinentalCompetitionHasGroupsSemisAndAFinal() {
        val w = EcoTestSupport.world(112L, 1)
        EcoTestSupport.play(w, 34 - 1)
        val s = FixtureCalendar.state(w)
        assertEquals(8, s.contClubs.size)
        assertEquals(24, s.contTies.count { it.round <= 6 })
        assertEquals(2, s.contTies.count { it.round == 7 })
        assertEquals(1, s.contTies.count { it.round == 8 })
        assertTrue(s.contWinner in w.clubs.indices)
        assertEquals(4, FixtureCalendar.groupTable(w, 0).size)
        for (g in 0..1) for (id in FixtureCalendar.groupTable(w, g)) assertTrue((s.contPoints[id] ?: 0) in 0..18)
    }

    @Test fun nextSeasonsQualifiersComeFromTheLeagueTable() {
        val w = EcoTestSupport.world(113L, 1)
        EcoTestSupport.play(w, 34)
        val s = FixtureCalendar.state(w)
        assertEquals(8, s.nextQualifiers.size)
        assertEquals(8, s.nextQualifiers.toSet().size)
        EcoTestSupport.play(w, 5)
        assertEquals(w.year, s.contYear)
        assertEquals(s.nextQualifiers.toSet().size, 8)
    }

    @Test fun extraMatchesStayBoundedOverFiveSeasons() {
        val w = EcoTestSupport.world(114L, 1)
        repeat(5) { season ->
            for (wk in 1..33) EcoTestSupport.play(w, 1)
            check(w, "season ${season + 1} before the last week")
            EcoTestSupport.play(w, 1)
        }
        check(w, "after five seasons")
    }

    @Test fun internationalWindowsFatigueCallUpsAndNothingElse() {
        val w = EcoTestSupport.world(115L, FixtureCalendar.INTL_WEEKS[0])
        val s = FixtureCalendar.state(w)
        FixtureCalendar.midweek(w)
        assertTrue("some players are called up", s.away.isNotEmpty())
        for (id in s.away) { val p = w.players.getValue(id); assertTrue(p.condition in 5f..100f && p.morale in 15f..100f) }
        val size = s.away.size
        FixtureCalendar.midweek(w)
        assertEquals("the hook is idempotent within a week", size, s.away.size)
        assertTrue(s.cupTies.isEmpty() || s.cupTies.none { it.round > 0 && FixtureCalendar.CUP_WEEKS[it.round - 1] == w.week })
    }

    @Test fun travelAndCongestionAreCappedConditionPenalties() {
        val w = EcoTestSupport.world(116L, 3)
        val s = FixtureCalendar.state(w)
        val me = w.human()
        val far = w.clubs.maxByOrNull { FixtureCalendar.distanceKm(w, me.id, it.id) }!!
        assertTrue(FixtureCalendar.distanceKm(w, me.id, far.id) > FixtureCalendar.LONG_TRIP_KM)
        val away = CalendarFactors.factors(w, me, MatchContext(false, far))
        assertTrue(away.all { it.channel == Channel.CONDITION && it.value < 0f && it.value >= -ModifierCaps.CONDITION_PER_FACTOR })
        assertTrue(CalendarFactors.factors(w, me, MatchContext(true, far)).isEmpty())
        s.midweek.add(me.id)
        assertEquals(1, CalendarFactors.factors(w, me, MatchContext(true, far)).size)
        val m = Ecosystem.modifiersFor(w, me, MatchContext(false, far))
        assertTrue(m.condition >= -ModifierCaps.CONDITION_TOTAL)
        assertEquals(FixtureCalendar.distanceKm(w, me.id, far.id), FixtureCalendar.distanceKm(w, far.id, me.id), 1e-3f)
        s.longTrip.add(me.id)
        assertTrue(CalendarFactors.factors(w, me, MatchContext.WEEKLY).sumOf { it.value.toDouble() } >= -0.04)
    }

    @Test fun refereesAreDeterministicAndOnlyNudgeMorale() {
        val w = EcoTestSupport.world(117L, 5)
        val a = Referees.forMatch(w, 3, 9, w.year, w.week)
        val b = Referees.forMatch(w, 3, 9, w.year, w.week)
        assertEquals(a.id, b.id); assertEquals(a.name, b.name)
        assertTrue(a.strictness in 1..10 && a.consistency in 1..10)
        val seen = HashSet<Int>()
        for (h in 0 until 40) seen.add(Referees.forMatch(w, h, h + 1, w.year, w.week).id)
        assertTrue("pool is used", seen.size > 5)
        for (c in w.clubs.take(30)) for (f in RefereeFactors.factors(w, c, MatchContext(true, w.clubs[(c.id + 1) % w.clubs.size]))) {
            assertEquals(Channel.MORALE, f.channel); assertTrue(Math.abs(f.value) <= 0.4f)
        }
        assertTrue(Referees.describe(a).contains(a.name))
    }

    @Test fun calendarViewListsTheLeagueCupsAndWindowsAndWarnsAboutCongestion() {
        val w = EcoTestSupport.world(118L, 1)
        EcoTestSupport.play(w, 3)
        val entries = FixtureCalendar.humanCalendar(w)
        assertTrue(entries.count { it.kind == 0 } >= 30)
        assertEquals(4, entries.count { it.kind == 3 })
        assertTrue(entries.zipWithNext().all { it.first.week <= it.second.week })
        assertNotNull(FixtureCalendar.congestion(w))
    }

    @Test fun aDisabledCalendarPlaysExactlyLikeNoCalendar() {
        fun run(disable: Boolean): List<Int> {
            val w = EcoTestSupport.world(119L, 1)
            if (disable) FixtureCalendar.state(w).enabled = false
            val prev = FixtureCalendar.hookEnabled
            if (!disable) FixtureCalendar.hookEnabled = false
            try { EcoTestSupport.play(w, 34) } finally { FixtureCalendar.hookEnabled = prev }
            if (disable) assertTrue(FixtureCalendar.state(w).cupTies.isEmpty() && FixtureCalendar.state(w).seasonGames.isEmpty())
            return w.leagues.flatMap { l -> l.fixtures.map { it.hg * 100 + it.ag } }
        }
        assertEquals(run(true), run(false))
    }

    @Test fun formatNineSaveMigratesToFormatTenAndPlaysAFullSeasonWithCupsOn() {
        val dir = temp.newFolder()
        val old = EcoTestSupport.world(120L, 5)
        EcoTestSupport.play(old, 12)
        old.ecosystem!!.calendar = null
        old.saveVersion = 9
        ObjectOutputStream(GZIPOutputStream(FileOutputStream(File(dir, "career1.dat")))).use { it.writeObject(old) }
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        assertEquals(Persistence.CURRENT_SAVE_VERSION, loaded.saveVersion)
        assertEquals(10, Persistence.CURRENT_SAVE_VERSION)
        assertNull(loaded.ecosystem?.calendar)
        val startYear = loaded.year
        var guard = 0
        while (loaded.year == startYear && guard++ < 60) EcoTestSupport.play(loaded, 1)
        assertTrue(loaded.year > startYear)
        val s = FixtureCalendar.state(loaded)
        assertTrue("brackets catch up after a mid-season load", s.cupTies.size >= 100 || s.cupYear != startYear)
        check(loaded, "after migrated season")
    }

    @Test fun calendarSectionRoundTrips() {
        val w = EcoTestSupport.world(121L, 1)
        EcoTestSupport.play(w, 12)
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, w))
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        val a = FixtureCalendar.state(w); val b = FixtureCalendar.state(loaded)
        assertEquals(a.cupTies.size, b.cupTies.size)
        assertEquals(a.contClubs, b.contClubs)
        assertEquals(a.cupAlive, b.cupAlive)
        assertEquals(a.seasonGames, b.seasonGames)
    }

    @Test fun weeklyAdvanceWithCupsAndEuropeStaysFast() {
        val w = EcoTestSupport.world(122L, 1)
        var worst = 0L
        for (wk in 1..12) {
            val t = System.nanoTime()
            EcoTestSupport.play(w, 1)
            worst = maxOf(worst, System.nanoTime() - t)
        }
        assertTrue("slowest week ${worst / 1_000_000} ms", worst < 2_000_000_000L)
    }
}
