package com.nerdfm.game

import kotlin.math.roundToInt

object Lineups {
    fun available(p: Player): Boolean = p.injWeeks <= 0 && p.suspWeeks <= 0

    fun eff(p: Player, slot: Pos): Float =
        Roles.overall(p.attrs, slot) * Roles.familiarity(p, slot) * (0.8f + 0.2f * (p.condition / 100f))

    private fun slotOrder(f: Formation): List<Int> {
        val pri = mapOf(Pos.GK to 0, Pos.ST to 1, Pos.CB to 2, Pos.AM to 3, Pos.DM to 4, Pos.CM to 5,
            Pos.LW to 6, Pos.RW to 6, Pos.LB to 7, Pos.RB to 7)
        return f.slots.indices.sortedBy { pri[f.slots[it]] ?: 9 }
    }

    /** Greedy best XI + bench for a club using its current formation. Returns total quality. */
    fun autoPick(w: World, c: Club): Float {
        val f = Formations.list[c.tactics.formation]
        val all = w.squad(c)
        val pool = all.filter { available(it) }.toMutableList()
        val extra = all.filter { !available(it) }.toMutableList()
        val chosen = IntArray(11) { -1 }
        var total = 0f
        for (i in slotOrder(f)) {
            val slot = f.slots[i]
            var best: Player? = null
            var bs = -1f
            for (p in pool) {
                val s = eff(p, slot)
                if (s > bs) { bs = s; best = p }
            }
            if (best == null) {
                for (p in extra) {
                    val s = eff(p, slot)
                    if (s > bs) { bs = s; best = p }
                }
                if (best != null) extra.remove(best)
            } else pool.remove(best)
            if (best != null) { chosen[i] = best.id; total += bs }
        }
        for (i in 0 until 11) c.lineup[i] = chosen[i]
        // bench: best keeper then best remaining by ovr
        val bench = ArrayList<Player>()
        val gk = pool.filter { it.pos == Pos.GK }.maxByOrNull { it.ovr }
        if (gk != null) { bench.add(gk); pool.remove(gk) }
        pool.sortByDescending { it.ovr }
        for (p in pool) { if (bench.size >= 7) break; bench.add(p) }
        for (i in 0 until 7) c.bench[i] = if (i < bench.size) bench[i].id else -1
        return total
    }

    /** Validate a human lineup: replace unavailable / missing players. Returns number replaced. */
    fun fixHuman(w: World, c: Club): Int {
        if (c.autoPick) { autoPick(w, c); return 0 }
        val f = Formations.list[c.tactics.formation]
        val used = HashSet<Int>()
        var replaced = 0
        fun valid(id: Int): Boolean {
            val p = w.players[id] ?: return false
            return p.clubId == c.id && available(p) && !used.contains(id)
        }
        for (i in 0 until 11) {
            if (valid(c.lineup[i])) used.add(c.lineup[i]) else c.lineup[i] = -1
        }
        for (i in 0 until 7) {
            if (valid(c.bench[i])) used.add(c.bench[i]) else c.bench[i] = -1
        }
        val pool = w.squad(c).filter { available(it) && !used.contains(it.id) }.toMutableList()
        for (i in 0 until 11) {
            if (c.lineup[i] == -1) {
                val slot = f.slots[i]
                val b = pool.maxByOrNull { eff(it, slot) }
                if (b != null) { c.lineup[i] = b.id; pool.remove(b); used.add(b.id); replaced++ }
            }
        }
        for (i in 0 until 7) {
            if (c.bench[i] == -1) {
                val b = pool.maxByOrNull { it.ovr }
                if (b != null) { c.bench[i] = b.id; pool.remove(b); used.add(b.id) }
            }
        }
        return replaced
    }

    fun strength(w: World, c: Club): Float {
        val s = w.squad(c).filter { available(it) }.sortedByDescending { it.ovr }.take(14)
        if (s.isEmpty()) return 20f
        return s.sumOf { it.ovr }.toFloat() / s.size
    }

    /** AI chooses formation by fit, then mentality by relative strength. */
    fun aiTactics(w: World, c: Club, opp: Club?, home: Boolean) {
        var bestF = c.tactics.formation
        var bestScore = -1f
        for (fi in Formations.list.indices) {
            val old = c.tactics.formation
            c.tactics.formation = fi
            val q = autoPick(w, c)
            val f = Formations.list[fi]
            val sc = q * (1f + 0.5f * (f.attM + f.midM + f.defM - 3f))
            c.tactics.formation = old
            if (sc > bestScore) { bestScore = sc; bestF = fi }
        }
        c.tactics.formation = bestF
        autoPick(w, c)
        var m = 0
        if (opp != null) {
            val d = strength(w, c) - strength(w, opp) + (if (home) 1.5f else -1.5f)
            m = when {
                d > 7f -> 2
                d > 3f -> 1
                d < -7f -> -2
                d < -3f -> -1
                else -> 0
            }
            c.tactics.pressing = if (d > 4f) 2 else if (d < -4f) 0 else 1
            c.tactics.line = if (d > 5f) 2 else if (d < -5f) 0 else 1
        }
        c.tactics.mentality = m
        // Blend situational strength-based choices with the club's evolved long-term policy.
        // Genome genes are persistent; match-state adjustments still happen inside MatchEngine.
        c.tactics.pressing = ((c.tactics.pressing + c.managerGenome.pressing) / 2f).roundToInt().coerceIn(0, 2)
        c.tactics.line = ((c.tactics.line + c.managerGenome.line) / 2f).roundToInt().coerceIn(0, 2)
        c.tactics.tempo = c.managerGenome.tempo.roundToInt().coerceIn(0, 2)
        c.tactics.width = c.managerGenome.width.roundToInt().coerceIn(0, 2)
        c.tactics.passing = c.managerGenome.directness.roundToInt().coerceIn(0, 2)
    }
}
