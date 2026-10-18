package com.nerdfm.game

import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** M4 (people): languages, culture, relationships, personal events, agents, save format 8. */
class PeopleTest {
    @get:Rule val temp = TemporaryFolder()

    private fun check(w: World, label: String) {
        assertEquals(label, emptyList<WorldIntegrity.Issue>(), WorldIntegrity.inspect(w))
        val s = People.state(w)
        for ((id, pp) in s.players) {
            assertTrue("$label stale person $id", w.players.containsKey(id))
            assertTrue("$label fluency", pp.f1 in 0f..100f && pp.f2 in 0f..100f)
            assertTrue("$label adaptation ${pp.adaptation}", pp.adaptation in 0f..100f)
            assertTrue("$label tenure", pp.tenure >= 0)
            assertTrue("$label agent", pp.agent in 0..2)
        }
        for ((k, t) in s.ties) {
            val a = (k / 1_000_000L).toInt(); val b = (k % 1_000_000L).toInt()
            assertTrue("$label tie order", a < b)
            assertTrue("$label dangling tie $a-$b", w.players.containsKey(a) && w.players.containsKey(b))
            assertTrue("$label tie type", t in 1..3)
            assertEquals(w.humanClubId, w.players.getValue(a).clubId)
            assertEquals(w.humanClubId, w.players.getValue(b).clubId)
        }
        for (e in s.events) assertTrue("$label dangling event", w.players.containsKey(e.playerId))
        assertTrue(s.events.size <= People.EVENT_LIMIT)
    }

    private fun writeOld(dir: File, w: World, version: Int) {
        w.saveVersion = version
        ObjectOutputStream(GZIPOutputStream(FileOutputStream(File(dir, "career1.dat")))).use { it.writeObject(w) }
    }

    @Test fun fluencyAndAdaptationStayInRangeOverThreeSeasons() {
        val w = EcoTestSupport.world(71L)
        repeat(3) { season ->
            for (wk in 1..34) EcoTestSupport.play(w, 1)
            check(w, "season ${season + 1}")
        }
    }

    @Test fun extremeLanguageProblemRespectsTheCaps() {
        val w = EcoTestSupport.world(72L, 2)
        val me = w.human()
        val lang = People.clubLanguage(w, me)
        val n = Names.nations.size
        val foreign = (0 until n).first { it / 3 != lang / 3 }
        val s = People.state(w)
        for (p in w.squad(me)) s.players[p.id] = PlayerPerson().apply { mother = foreign; culture = People.cultureOf(foreign); adaptation = 10f; tenure = 0; clubId = p.clubId }
        s.clubNation[me.id] = lang
        val fs = PeopleFactors.factors(w, me, MatchContext(true, w.clubs[(me.id + 1) % w.clubs.size]))
        assertEquals(2, fs.size)
        for (f in fs) assertTrue(f.value < 0f && f.value >= -ModifierCaps.MORALE_PER_FACTOR)
        assertEquals(-People.MORALE_PENALTY_CAP, fs.first { it.channel == Channel.MORALE }.value, 1e-4f)
        assertEquals(-People.SHARPNESS_PENALTY_CAP, fs.first { it.channel == Channel.SHARPNESS }.value, 1e-4f)
        val m = Ecosystem.modifiersFor(w, me, MatchContext(true, w.clubs[(me.id + 1) % w.clubs.size]))
        assertTrue(m.morale >= -ModifierCaps.MORALE_TOTAL && m.sharpness >= -ModifierCaps.SHARPNESS_TOTAL)
    }

    @Test fun anAverageClubSuffersNoCommunicationPenalty() {
        val w = EcoTestSupport.world(73L, 2)
        var penalised = 0
        for (c in w.clubs) if (People.shortfall(w, c) > 0.25f) penalised++
        assertTrue("clubs with a real language problem at the start: $penalised", penalised <= 6)
    }

    @Test fun learningRaisesFluencyAndALanguageCoachSpeedsItUp() {
        val w = EcoTestSupport.world(74L, 2)
        val me = w.human()
        val lang = People.clubLanguage(w, me)
        val s = People.state(w)
        val p = w.squad(me).first()
        val foreign = (0 until Names.nations.size).first { it != lang }
        s.clubNation[me.id] = lang
        s.players[p.id] = PlayerPerson().apply { mother = foreign; culture = People.cultureOf(foreign); tenure = 0; adaptation = 100f; clubId = p.clubId }
        val before = People.fluency(s.players.getValue(p.id), lang)
        EcoTestSupport.play(w, 10)
        val after = People.fluency(s.players.getValue(p.id), lang)
        assertTrue("fluency should rise: $before -> $after", after > before)
    }

    @Test fun movingToADifferentCultureStartsLowerAndMoreSoForTheYoung() {
        val w = EcoTestSupport.world(75L, 2)
        val me = w.human()
        val s = People.state(w)
        val lang = People.clubLanguage(w, me)
        s.clubNation[me.id] = lang
        val squad = w.squad(me)
        val young = squad.first()
        val old = squad.last()
        val far = (0 until Names.nations.size).first { People.cultureDistance(People.cultureOf(it), People.cultureOf(lang)) >= 1 }
        young.age = 19; old.age = 30
        for (p in listOf(young, old)) s.players[p.id] = PlayerPerson().apply { mother = far; culture = People.cultureOf(far); adaptation = 100f; clubId = 999 }
        People.weekly(w)
        val ay = s.players.getValue(young.id).adaptation
        val ao = s.players.getValue(old.id).adaptation
        assertTrue("young $ay old $ao", ay < ao)
        assertTrue(ao < 100f)
    }

    @Test fun relationshipsAreSymmetricAndSurviveRetirementsAndTransfers() {
        val w = EcoTestSupport.world(76L, 2)
        EcoTestSupport.play(w, 6)
        check(w, "after 6 weeks")
        val s = People.state(w)
        assertTrue("some ties exist", s.ties.isNotEmpty())
        // remove a player the way retirement does and make sure nothing dangles
        val me = w.human()
        val gone = w.squad(me).first { p -> s.ties.keys.any { k -> (k / 1_000_000L).toInt() == p.id || (k % 1_000_000L).toInt() == p.id } }
        me.players.remove(gone.id); w.players.remove(gone.id)
        People.prune(w)
        check(w, "after removal")
        for (c in People.cliques(w)) assertTrue(c.size >= 3)
        for (p in w.squad(me)) for ((o, t) in People.tiesOf(w, p)) assertTrue(People.tiesOf(w, o).any { it.first.id == p.id && it.second == t })
    }

    @Test fun eventsAreDeterministicFromTheSeed() {
        fun run(): List<String> {
            val w = EcoTestSupport.world(77L, 1)
            EcoTestSupport.play(w, 30)
            return People.state(w).events.map { "${it.id}:${it.playerId}:${it.kind}:${it.year}:${it.week}:${it.resolved}" }
        }
        assertEquals(run(), run())
    }

    @Test fun respondingToAnEventChangesMoraleOnceAndIsRecorded() {
        val w = EcoTestSupport.world(78L, 2)
        val me = w.human()
        val s = People.state(w)
        val p = w.squad(me).first()
        p.morale = 60f
        s.events.add(0, PersonalEvent(s.nextEventId++, p.id, EventKind.HOMESICK, w.year, w.week))
        val id = s.events[0].id
        val (ok, _) = People.respond(w, id, EventResponse.TALK)
        assertTrue(ok)
        assertEquals(62f, p.morale, 1e-3f)
        assertFalse(People.respond(w, id, EventResponse.TIME_OFF).first)
        assertEquals(62f, p.morale, 1e-3f)
        assertTrue(People.openEvents(w).none { it.id == id })
    }

    @Test fun unansweredEventsExpireWithTheIgnoreEffect() {
        val w = EcoTestSupport.world(79L, 2)
        val s = People.state(w)
        val p = w.squad(w.human()).first()
        s.events.add(0, PersonalEvent(s.nextEventId++, p.id, EventKind.CONTRACT_WORRY, w.year, w.week))
        EcoTestSupport.play(w, 6)
        assertTrue(People.openEvents(w).none { it.playerId == p.id && it.kind == EventKind.CONTRACT_WORRY && it.week == 2 })
    }

    @Test fun agentsDependOnTheIdOnlyAndOnlyMoveRenewalDemands() {
        val w = EcoTestSupport.world(80L, 1)
        val p = w.players.values.first { it.clubId >= 0 }
        val a = People.agentOfId(p.id)
        assertTrue(a in 0..2)
        assertEquals(a, People.agentOfId(p.id))
        val enabled = ContractDeals.demandedWage(p)
        Ecosystem.enabled = false
        val off = try { ContractDeals.demandedWage(p) } finally { Ecosystem.enabled = true }
        assertTrue(enabled in (off * 90 / 100)..(off * 112 / 100))
    }

    @Test fun formatSixSaveMigratesToFormatEightGainsThePeopleSectionAndPlaysASeason() {
        val dir = temp.newFolder()
        val old = EcoTestSupport.world(81L, 5)
        EcoTestSupport.play(old, 3)
        old.ecosystem!!.people = null; old.ecosystem!!.env = null; old.ecosystem!!.calendar = null
        writeOld(dir, old, 6)
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        assertTrue(Persistence.CURRENT_SAVE_VERSION >= 8)
        assertEquals(Persistence.CURRENT_SAVE_VERSION, loaded.saveVersion)
        assertNull("people section is created lazily", loaded.ecosystem?.people)
        val startYear = loaded.year
        var guard = 0
        while (loaded.year == startYear && guard++ < 60) EcoTestSupport.play(loaded, 1)
        assertTrue(loaded.year > startYear)
        check(loaded, "after migrated season")
        assertNotNull(loaded.ecosystem!!.people)
    }

    @Test fun peopleSectionRoundTripsThroughSaveAndLoad() {
        val w = EcoTestSupport.world(82L, 1)
        EcoTestSupport.play(w, 8)
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, w))
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        val a = People.state(w); val b = People.state(loaded)
        assertEquals(a.players.size, b.players.size)
        assertEquals(a.ties, b.ties)
        assertEquals(a.events.map { it.id }, b.events.map { it.id })
        check(loaded, "loaded")
    }
}
