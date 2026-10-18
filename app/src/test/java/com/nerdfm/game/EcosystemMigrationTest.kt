package com.nerdfm.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * saves/career_v3_pre_ecosystem.dat was written by the build that preceded the Club Ecosystem (save format 3,
 * generated career seed 7, five weeks played). It proves real old-format files still load.
 */
class EcosystemMigrationTest {
    @get:Rule val temp = TemporaryFolder()

    private fun loadFixture(): World {
        val dir = temp.newFolder()
        val bytes = javaClass.classLoader!!.getResourceAsStream("saves/career_v3_pre_ecosystem.dat")!!.use { it.readBytes() }
        File(dir, "career1.dat").writeBytes(bytes)
        val result = Persistence.load(dir)
        assertTrue("expected Loaded but was $result", result is LoadResult.Loaded)
        return (result as LoadResult.Loaded).world
    }

    @Test fun formatThreeSaveLoadsWithEveryExistingFieldIntact() {
        val w = loadFixture()
        assertEquals(Persistence.CURRENT_SAVE_VERSION, w.saveVersion)
        assertEquals(2026, w.year); assertEquals(6, w.week)
        assertEquals("Northmoor United", w.human().name)
        assertEquals(126, w.clubs.size); assertEquals(5612, w.players.size)
        assertNotNull(w.business); assertEquals(2, w.biz().sponsors.size)
        assertEquals(42, w.human().players.size)
        assertEquals(emptyList<WorldIntegrity.Issue>(), WorldIntegrity.inspect(w))
    }

    @Test fun ecosystemHolderIsAbsentUntilFirstUseAndThenPersists() {
        val w = loadFixture()
        assertNull(w.ecosystem)
        w.eco().applied[1] = AppliedDelta(1f, 2f, 3f)
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, w))
        val again = (Persistence.load(dir) as LoadResult.Loaded).world
        // a pending delta in a loaded save is cleaned (player 1 does not need to exist for the map to be cleared)
        assertTrue(again.eco().applied.isEmpty())
        assertEquals(Persistence.CURRENT_SAVE_VERSION, again.saveVersion)
    }

    @Test fun migratedCareerCanKeepPlayingAFullSeasonWithoutIntegrityIssues() {
        val w = loadFixture()
        for (wk in w.week..34) {
            w.week = wk
            val fx = Game.startWeek(w)
            if (fx != null) { val e = Game.humanEngine(w, fx); e.simToEnd(); Game.finishHuman(w, fx, e) }
            Game.completeWeek(w); w.sacked = false
        }
        assertEquals(2027, w.year)
        assertEquals(emptyList<WorldIntegrity.Issue>(), WorldIntegrity.inspect(w))
    }

    @Test fun olderFormatsStillStepThroughEveryMigration() {
        for (v in 1..3) {
            val dir = temp.newFolder()
            val old = World().apply { saveVersion = v }
            val bytes = java.io.ByteArrayOutputStream()
            java.io.ObjectOutputStream(java.util.zip.GZIPOutputStream(bytes)).use { it.writeObject(old) }
            File(dir, "career1.dat").writeBytes(bytes.toByteArray())
            val result = Persistence.load(dir)
            assertTrue("format $v: $result", result is LoadResult.Loaded)
            assertEquals(Persistence.CURRENT_SAVE_VERSION, (result as LoadResult.Loaded).world.saveVersion)
        }
    }

    @Test fun formatFourSaveFromM0LoadsAndGainsTheM1Sections() {
        val dir = temp.newFolder()
        File(dir, "career1.dat").writeBytes(javaClass.classLoader!!.getResourceAsStream("saves/career_v4_m0.dat")!!.use { it.readBytes() })
        val w = (Persistence.load(dir) as LoadResult.Loaded).world
        assertEquals(Persistence.CURRENT_SAVE_VERSION, w.saveVersion)
        assertEquals("Mossfield Borough", w.human().name)
        assertEquals(7, w.week)
        assertNotNull("M0 saved an (empty) ecosystem holder", w.ecosystem)
        assertNull(w.ecosystem!!.staff); assertNull(w.ecosystem!!.board); assertNull(w.ecosystem!!.identity)
        assertEquals(w.clubs.size, Staffing.state(w).byClub.size)
        assertEquals(w.clubs.size, BoardRules.state(w).clubs.size)
        assertTrue(Identity.state(w).homegrown.isNotEmpty())
        for (i in 1..34) { val fx = Game.startWeek(w); if (fx != null) { val e = Game.humanEngine(w, fx); e.simToEnd(); Game.finishHuman(w, fx, e) }; Game.completeWeek(w); w.sacked = false; if (w.year > 2026) break }
        assertEquals(emptyList<WorldIntegrity.Issue>(), WorldIntegrity.inspect(w))
    }
}
