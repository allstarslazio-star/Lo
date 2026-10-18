package com.nerdfm.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UiSupportTest {
    @get:Rule val temp = TemporaryFolder()

    // ------------------------------------------------------------- settings
    @Test fun memoryStoreReturnsDefaultsForMissingOrMalformedValues() {
        val s = MemorySettingsStore()
        assertEquals("d", s.getString("k", "d"))
        assertEquals(7, s.getInt("n", 7))
        s.putString("n", "abc")
        assertEquals(7, s.getInt("n", 7))
        s.putInt("n", 3); s.putBoolean("b", true)
        assertEquals(3, s.getInt("n")); assertTrue(s.getBoolean("b"))
        s.remove("b")
        assertFalse(s.getBoolean("b"))
    }

    @Test fun fileStorePersistsAcrossInstancesAndLeavesNoTempFile() {
        val file = File(temp.root, "ui/settings.properties")
        val a = FileSettingsStore(file)
        a.putString("nav.state", "Squad|Home"); a.putBoolean("tutorial.done", true)
        val b = FileSettingsStore(file)
        assertEquals("Squad|Home", b.getString("nav.state"))
        assertTrue(b.getBoolean("tutorial.done"))
        b.remove("nav.state")
        assertEquals("", FileSettingsStore(file).getString("nav.state"))
        assertFalse(File(file.parentFile, "settings.properties.tmp").exists())
    }

    @Test fun anUnreadableSettingsFileFallsBackToDefaults() {
        val dir = File(temp.root, "bad"); assertTrue(dir.mkdirs())
        val file = File(dir, "settings.properties")
        assertTrue(file.mkdir()) // a directory where a file is expected
        val s = FileSettingsStore(file)
        assertEquals("fallback", s.getString("anything", "fallback"))
        s.putString("x", "1") // must not throw even though it cannot be written
        assertEquals("1", s.getString("x"))
    }

    // ------------------------------------------------------------- tutorial
    @Test fun tutorialCoversTheSevenPlannedTopicsAndPointsAtRealScreens() {
        assertEquals(7, TutorialSteps.ALL.size)
        for (step in TutorialSteps.ALL) assertTrue(step.screen, Destination.isKnown(step.screen))
        val text = TutorialSteps.ALL.joinToString(" ") { it.title.lowercase() }
        for (topic in listOf("squad", "formation", "tactics", "play", "transfers", "calendar")) assertTrue(topic, text.contains(topic))
    }

    @Test fun tutorialStepsForwardBackAndFinish() {
        val t = TutorialState(MemorySettingsStore())
        assertTrue(t.active); assertEquals(0, t.index)
        t.previous(); assertEquals(0, t.index)
        repeat(t.total - 1) { t.next() }
        assertTrue(t.isLast)
        t.next()
        assertFalse(t.active)
        t.next(); t.previous()
        assertEquals("a finished tutorial no longer moves", t.total - 1, t.index)
    }

    @Test fun skippingIsPermanentUntilRestartedAndSurvivesRestarts() {
        val store = MemorySettingsStore()
        val t = TutorialState(store)
        t.next(); t.next()
        assertEquals(2, TutorialState(store).index)
        t.skip()
        assertFalse(TutorialState(store).active)
        val again = TutorialState(store)
        again.restart()
        assertTrue(TutorialState(store).active)
        assertEquals(0, TutorialState(store).index)
    }

    @Test fun aStoredStepOutsideTheRangeIsClamped() {
        val store = MemorySettingsStore()
        store.putInt(SettingKeys.TUTORIAL_STEP, 999)
        assertEquals(TutorialSteps.ALL.size - 1, TutorialState(store).index)
        store.putInt(SettingKeys.TUTORIAL_STEP, -4)
        assertEquals(0, TutorialState(store).index)
    }

    // ------------------------------------------------------------- duplicate-action guard
    @Test fun guardAdmitsOneRunAtATimeAndIgnoresRapidRepeats() {
        var now = 1_000L
        val g = ActionGuard(600L) { now }
        assertTrue(g.tryStart())
        assertFalse("already running", g.tryStart())
        g.finish()
        now += 100
        assertFalse("too soon after the last start", g.tryStart())
        now += 600
        assertTrue(g.tryStart())
        assertTrue(g.isRunning)
    }

    @Test fun runExclusiveReleasesTheGuardEvenWhenTheBlockThrows() {
        var now = 0L
        val g = ActionGuard(10L) { now }
        try { g.runExclusive { throw IllegalStateException("x") } } catch (_: IllegalStateException) { }
        assertFalse(g.isRunning)
        now = 50
        assertEquals("ok", g.runExclusive { "ok" })
        assertNull("a call while another is running returns null", run {
            now = 100
            g.tryStart()
            g.runExclusive { "never" }
        })
    }

    @Test fun aDoubleTapAdvancesTheWeekOnlyOnce() {
        val w = WorldGen.generate(5L, "Tap", 0)
        var now = 0L
        val g = ActionGuard(600L) { now }
        var advanced = 0
        repeat(5) {
            g.runExclusive { advanced++; Game.startWeek(w); Game.completeWeek(w) }
            now += 50
        }
        assertEquals(1, advanced)
        assertEquals(1, w.week)
    }

    // ------------------------------------------------------------- accessibility
    @Test fun everyTextColourPairMeetsWcagAa() {
        for (r in Accessibility.audit()) assertTrue(r.line(), r.passes)
    }

    @Test fun contrastMathMatchesKnownValues() {
        assertEquals(21.0, Accessibility.contrastRatio(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, Accessibility.contrastRatio(0xFF123456, 0xFF123456), 0.0001)
        assertEquals(Accessibility.contrastRatio(0xFFAA0000, 0xFF00AA00), Accessibility.contrastRatio(0xFF00AA00, 0xFFAA0000), 1e-9)
        assertTrue("a dim grey on near-black must be flagged", Accessibility.contrastRatio(0xFF3A3F3A, Palette.BG) < Accessibility.MIN_CONTRAST_TEXT)
    }

    @Test fun touchTargetAndFontScaleRules() {
        assertEquals(48, Accessibility.MIN_TOUCH_DP)
        assertEquals(1f, Accessibility.clampFontScale(Float.NaN), 0f)
        assertEquals(Accessibility.MAX_FONT_SCALE, Accessibility.clampFontScale(3f), 0f)
        assertEquals(0.85f, Accessibility.clampFontScale(0.2f), 0f)
        assertEquals(1.3f, Accessibility.clampFontScale(1.3f), 0f)
    }
}
