package com.nerdfm.game

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorldCalendarTest {
    @get:Rule val temp = TemporaryFolder()

    private fun at(week: Int, day: Int) = World().apply { year = 2026; this.week = week; dayInWeek = day }

    @Test fun summerWindowIsDayAccurate() {
        val a = at(8, 0)
        assertEquals(LocalDate.of(2026, 8, 26), a.careerDate())
        assertTrue(a.isWindowOpen())
        assertEquals(5, a.windowDaysRemaining())

        val b = at(8, 5)
        assertEquals(LocalDate.of(2026, 8, 31), b.careerDate())
        assertTrue(b.isWindowOpen())
        assertEquals(0, b.windowDaysRemaining())

        val c = at(8, 6)
        assertEquals(LocalDate.of(2026, 9, 1), c.careerDate())
        assertFalse(c.isWindowOpen())
    }

    @Test fun januaryWindowIsOpenInWeek27() {
        val w = at(27, 0)
        assertEquals(LocalDate.of(2027, 1, 6), w.careerDate())
        assertTrue(w.isWindowOpen())
    }

    @Test fun advanceDayCapsAtSixAndDeadlineDayNewsIsPostedOnce() {
        val w = at(8, 4)
        Game.advanceDay(w)
        assertEquals(5, w.dayInWeek)
        assertEquals(1, w.news.count { it.title == "Transfer deadline day" })
        Game.windowNews(w)
        assertEquals(1, w.news.count { it.title == "Transfer deadline day" })
        Game.advanceDay(w)
        assertEquals(6, w.dayInWeek)
        Game.advanceDay(w)
        assertEquals(6, w.dayInWeek)
        assertEquals(1, w.news.count { it.title == "Transfer deadline day" })
    }

    @Test fun deadlineApproachingNewsAppearsInFinalWeek() {
        val w = at(8, 0)
        Game.windowNews(w)
        assertEquals(1, w.news.count { it.title == "Transfer deadline approaching" })
    }

    @Test fun completeWeekResetsDayAndIncrementsWeek() {
        val w = WorldGen.generate(77L, "Calendar Tester")
        w.week = 3
        w.dayInWeek = 4
        Game.completeWeek(w)
        assertEquals(4, w.week)
        assertEquals(0, w.dayInWeek)
    }

    @Test fun dayInWeekSurvivesSaveAndLoad() {
        val directory = temp.newFolder("day-in-week")
        val world = World().apply { year = 2026; week = 5; dayInWeek = 3 }
        assertTrue(Persistence.save(directory, world))
        val loaded = (Persistence.load(directory) as LoadResult.Loaded).world
        assertEquals(3, loaded.dayInWeek)
        assertEquals(5, loaded.week)
    }
}
