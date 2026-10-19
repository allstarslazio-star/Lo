package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparationTest {
    private fun world(seed: Long = 31L, week: Int = 1) = EcoTestSupport.world(seed, week)

    private fun setRole(w: World, club: Club, role: StaffRole, ability: Int) {
        val s = Staffing.state(w)
        val have = Staffing.inRole(w, club.id, role)
        val people = if (have.isNotEmpty()) have else listOf(StaffMember(s.nextId++, "T " + role.label, 0, 40, role).also {
            it.clubId = club.id; s.members[it.id] = it; s.byClub.getOrPut(club.id) { ArrayList() }.add(it.id)
        })
        for (m in people) { m.ability = ability; m.morale = 100f }
    }

    @Test fun lazyStateStartsNeutralAndOldSavesHaveNone() {
        val w = world()
        assertNull(w.ecosystem?.prep)
        assertEquals(70f, Preparation.state(w).familiarity, 0.01f)
        assertEquals(PrepFocus.BALANCED, Preparation.focusOf(w))
    }

    @Test fun firstWeekRecordsTheSignatureWithoutPenalty() {
        val w = world(); val before = Preparation.state(w).familiarity
        Preparation.weekly(w)
        assertEquals(before, Preparation.state(w).familiarity, 0.001f)
        assertEquals(Preparation.signatureOf(w.human().tactics), Preparation.state(w).signature)
    }

    @Test fun unchangedTacticsBuildFamiliarityUpToTheCap() {
        val w = world(); Preparation.weekly(w)
        var last = Preparation.state(w).familiarity
        repeat(40) { Preparation.weekly(w); val f = Preparation.state(w).familiarity; assertTrue(f >= last); assertTrue(f <= 100f); last = f }
        assertEquals(100f, last, 0.001f)
        assertTrue(Preparation.state(w).stableWeeks >= 40)
    }

    @Test fun aNewFormationCostsMoreThanATweak() {
        val a = world(); val b = world()
        for (w in listOf(a, b)) { Preparation.weekly(w); Preparation.state(w).familiarity = 80f }
        a.human().tactics.formation = (a.human().tactics.formation + 1) % 4
        b.human().tactics.pressing = (b.human().tactics.pressing + 1) % 3
        Preparation.weekly(a); Preparation.weekly(b)
        val fa = Preparation.state(a).familiarity; val fb = Preparation.state(b).familiarity
        assertTrue("formation $fa vs tweak $fb", fa < fb)
        assertTrue(fb < 80f)
        assertEquals(1, Preparation.state(a).seasonChanges)
        assertEquals(0, Preparation.state(a).stableWeeks)
    }

    @Test fun familiarityNeverFallsBelowTheFloor() {
        val w = world(); Preparation.weekly(w)
        repeat(30) { w.human().tactics.formation = (w.human().tactics.formation + 1) % 4; Preparation.weekly(w) }
        assertTrue(Preparation.state(w).familiarity >= 15f)
    }

    @Test fun systemFocusDoublesTheGain() {
        val a = world(); val b = world()
        for (w in listOf(a, b)) { Preparation.weekly(w); Preparation.state(w).familiarity = 50f }
        Preparation.setFocus(b, PrepFocus.SYSTEM)
        Preparation.weekly(a); Preparation.weekly(b)
        val ga = Preparation.state(a).familiarity - 50f; val gb = Preparation.state(b).familiarity - 50f
        assertEquals(2f * ga, gb, 0.01f)
    }

    @Test fun theFocusResetsAfterTheWeek() {
        val w = world(); Preparation.setFocus(w, PrepFocus.SET_PIECES)
        Preparation.weekly(w)
        assertEquals(PrepFocus.BALANCED, Preparation.focusOf(w))
    }

    @Test fun aGoodTeacherBuildsFamiliarityFaster() {
        val a = world(); val b = world()
        for (w in listOf(a, b)) { Preparation.weekly(w); Preparation.state(w).familiarity = 40f }
        setRole(a, a.human(), StaffRole.ASSISTANT_MANAGER, 20)
        setRole(b, b.human(), StaffRole.ASSISTANT_MANAGER, 1)
        repeat(5) { Preparation.weekly(a); Preparation.weekly(b) }
        assertTrue(Preparation.state(a).familiarity > Preparation.state(b).familiarity)
    }

    @Test fun setPieceWorkApproachesAStaffDefinedCeiling() {
        val w = world(); val me = w.human()
        setRole(w, me, StaffRole.COACH_SET_PIECES, 20)
        Preparation.weekly(w)
        repeat(30) { Preparation.setFocus(w, PrepFocus.SET_PIECES); Preparation.weekly(w) }
        val good = Preparation.state(w).setPieces
        val v = world(); setRole(v, v.human(), StaffRole.COACH_SET_PIECES, 1)
        repeat(30) { Preparation.setFocus(v, PrepFocus.SET_PIECES); Preparation.weekly(v) }
        assertTrue("good $good vs poor ${Preparation.state(v).setPieces}", good > Preparation.state(v).setPieces + 10f)
        assertTrue(good <= 100f)
    }

    @Test fun factorsAreHumanOnlyAndInsideTheCaps() {
        val w = world(); val me = w.human(); val other = w.clubs.first { it.id != me.id }
        assertTrue(PrepFactors.factors(w, other, MatchContext(true, me)).isEmpty())
        for (f in listOf(0f, 100f)) for (sp in listOf(0f, 100f)) for (focus in PrepFocus.values()) {
            Preparation.state(w).familiarity = f; Preparation.state(w).setPieces = sp; Preparation.setFocus(w, focus)
            val m = Ecosystem.modifiersFor(w, me, MatchContext(true, other))
            assertTrue(m.sharpness in -ModifierCaps.SHARPNESS_TOTAL..ModifierCaps.SHARPNESS_TOTAL)
            assertTrue(m.morale in -ModifierCaps.MORALE_TOTAL..ModifierCaps.MORALE_TOTAL)
            assertTrue(m.condition in -ModifierCaps.CONDITION_TOTAL..ModifierCaps.CONDITION_TOTAL)
        }
    }

    @Test fun oppositionStudyNeedsAnOpponentAndHelpsMoreWithData() {
        val w = world(); val me = w.human(); val other = w.clubs.first { it.id != me.id }
        val none = Ecosystem.modifiersFor(w, me, MatchContext(true, other)).morale
        Preparation.setFocus(w, PrepFocus.OPPOSITION)
        val withFocus = Ecosystem.modifiersFor(w, me, MatchContext(true, other))
        assertTrue(withFocus.morale > none)
        val weekly = Ecosystem.modifiersFor(w, me, MatchContext.WEEKLY).morale
        assertEquals(none, weekly, 0.0001f)
        assertTrue(Preparation.oppositionBoost(w) in 0.5f..1f)
    }

    @Test fun lowFamiliarityHurtsAndHighHelpsSharpnessWithinBounds() {
        val w = world()
        Preparation.state(w).familiarity = 15f
        assertTrue(Preparation.familiarityFactor(w) < 0f)
        Preparation.state(w).familiarity = 100f
        assertTrue(Preparation.familiarityFactor(w) in 0.5f..Preparation.FAMILIARITY_SWING)
    }

    @Test fun safeModeReproducesNoPreparationEffect() {
        val w = world()
        Ecosystem.enabled = false
        try {
            assertEquals(ClubModifiers.NEUTRAL, Ecosystem.modifiersFor(w, w.human(), MatchContext(true, null)))
            val before = Preparation.state(w).familiarity
            Ecosystem.preWeek(w)
            assertEquals(before, Preparation.state(w).familiarity, 0f)
        } finally { Ecosystem.enabled = true }
    }

    @Test fun repairClampsCorruptValues() {
        val w = world(); val s = Preparation.state(w)
        s.familiarity = Float.NaN; s.setPieces = 900f; s.focus = 99; s.stableWeeks = -3
        Preparation.repair(w)
        assertEquals(70f, s.familiarity, 0f); assertEquals(100f, s.setPieces, 0f); assertEquals(0, s.focus); assertEquals(0, s.stableWeeks)
    }

    @Test fun stateSurvivesASaveLoadRoundTrip() {
        val dir = java.nio.file.Files.createTempDirectory("prep").toFile()
        try {
            val w = world(); Preparation.weekly(w)
            Preparation.state(w).familiarity = 63f; Preparation.state(w).setPieces = 41f
            assertTrue(Persistence.save(dir, w))
            val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
            assertEquals(63f, Preparation.state(loaded).familiarity, 0f)
            assertEquals(41f, Preparation.state(loaded).setPieces, 0f)
            assertEquals(Persistence.CURRENT_SAVE_VERSION, loaded.saveVersion)
        } finally { dir.deleteRecursively() }
    }

    @Test fun seasonReviewResetsOnlyTheSeasonCounter() {
        val w = world(); val s = Preparation.state(w)
        s.seasonChanges = 5; s.familiarity = 66f
        Preparation.seasonReview(w)
        assertEquals(0, s.seasonChanges); assertEquals(66f, s.familiarity, 0f)
        assertFalse(s.lastChangeNote == "x")
    }
}
