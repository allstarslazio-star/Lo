package com.nerdfm.game

/** Shared helpers for business-layer tests. */
object BusinessTestSupport {
    /** Generated squads start at 42 players, above the 40-man signing cap; tests trim them first. */
    fun shrink(w: World, club: Club, size: Int) {
        while (club.players.size > size) {
            val p = w.squad(club).minByOrNull { it.ovr } ?: return
            club.players.remove(p.id)
            for (i in club.lineup.indices) if (club.lineup[i] == p.id) club.lineup[i] = -1
            for (i in club.bench.indices) if (club.bench[i] == p.id) club.bench[i] = -1
            p.clubId = -1
            p.wage = 0
            p.contractEnd = 0
        }
    }

    fun shrinkEveryone(w: World, size: Int) {
        for (c in w.clubs) shrink(w, c, size)
    }

    /** A generated career with a rich human club, an open transfer window and trimmed squads. */
    fun richWorld(seed: Long): World {
        val w = WorldGen.generate(seed, "Tester", 0)
        shrinkEveryone(w, 30)
        val me = w.human()
        me.balance = 500_000_000L
        me.wageBudget = 1_000_000_000L
        me.transferBudget = 500_000_000L
        for (c in w.clubs) if (!c.human) c.wageBudget = 1_000_000_000L
        w.week = 1
        Lineups.autoPick(w, me)
        return w
    }
}
