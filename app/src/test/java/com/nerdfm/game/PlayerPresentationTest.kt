package com.nerdfm.game

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPresentationTest {
    private fun player(form: Float) = Player(1, "Player", 0, 24, Pos.CM, null, IntArray(NATTR) { 50 }, 60, 50, 20, 50, 50, 50).apply { this.form = form }

    @Test fun performanceTrendIsExplicitAndStableAtMiddleForm() {
        assertEquals("↑ Improving", player(75f).performanceTrend())
        assertEquals("↓ Declining", player(45f).performanceTrend())
        assertEquals("→ Steady", player(60f).performanceTrend())
    }
}
