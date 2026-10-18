package com.nerdfm.game

/** Shared helpers for the ecosystem tests. */
object EcoTestSupport {
    fun world(seed: Long = 21L, week: Int = 1): World = WorldGen.generate(seed, "EcoTester", 0).also { it.week = week }

    /** Plays [weeks] weeks (human fixture auto-played) starting at the world's current week; handles season rollover. */
    fun play(w: World, weeks: Int) {
        repeat(weeks) {
            if (w.week < 1) w.week = 1
            val fx = Game.startWeek(w)
            if (fx != null) { val e = Game.humanEngine(w, fx); e.simToEnd(); Game.finishHuman(w, fx, e) }
            Game.completeWeek(w)
            w.sacked = false
        }
    }

    fun richHuman(w: World) {
        val me = w.human()
        me.balance = 900_000_000L; me.wageBudget = 1_000_000_000L; me.transferBudget = 500_000_000L
    }
}
