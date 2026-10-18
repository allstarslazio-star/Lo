package com.nerdfm.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * A hired scouting network. Each scout covers one league (or free agents) and slowly builds
 * knowledge of players until they are fully assessed and appear in the existing `World.scouted` set.
 */
object ScoutingNetwork {
    private val FIRST = listOf("Marco", "Elias", "Tomas", "Hugo", "Ibrahim", "Soren", "Dario", "Kaito", "Nuno", "Felix", "Anders", "Pedro")
    private val LAST = listOf("Rinaldi", "Okafor", "Lindqvist", "Moreau", "Haddad", "Petrov", "Alvarez", "Tanaka", "Brandt", "Costa", "Novak", "Mbeki")
    const val MARKET_SIZE = 6

    fun maxScouts(me: Club): Int = (2 + me.scout / 5).coerceIn(2, 6)

    fun posGroupLabel(g: Int): String = when (g) {
        0 -> "Goalkeepers"
        1 -> "Defenders"
        2 -> "Midfielders"
        3 -> "Attackers"
        else -> "Any position"
    }

    fun regionLabel(region: Int): String = when (region) {
        -1 -> "Idle"
        -2 -> "Free agents"
        else -> "League ${region + 1}"
    }

    private fun scoutWage(me: Club, ability: Int): Int =
        max(120, (me.revenue / 52.0 * 0.0035 * (0.5 + ability / 10.0)).toInt())

    private fun newScout(b: BusinessState, me: Club, rnd: Random): Scout {
        val ability = 4 + rnd.nextInt(15)
        val judgement = clampI(ability + rnd.nextInt(7) - 3, 1, 20)
        val name = FIRST[rnd.nextInt(FIRST.size)] + " " + LAST[rnd.nextInt(LAST.size)]
        val s = Scout(b.nextScoutId, name, ability, judgement, scoutWage(me, ability))
        b.nextScoutId++
        return s
    }

    fun refreshMarket(w: World, rnd: Random) {
        val me = w.clubs.getOrNull(w.humanClubId) ?: return
        val b = w.biz()
        while (b.scoutMarket.size < MARKET_SIZE) b.scoutMarket.add(newScout(b, me, rnd))
    }

    fun hire(w: World, scoutId: Int): Pair<Boolean, String> {
        val me = w.human()
        val b = w.biz()
        val s = b.scoutMarket.firstOrNull { it.id == scoutId } ?: return false to "That scout is no longer available."
        if (b.scouts.size >= maxScouts(me)) return false to "Your scouting level only supports ${maxScouts(me)} scouts. Upgrade the scouting network first."
        if (w.wageBill(me) + s.wage > me.wageBudget) return false to "The board will not sanction ${money(s.wage.toLong())}/wk: it breaks the wage budget."
        b.scoutMarket.remove(s)
        b.scouts.add(s)
        w.addNews("Scout hired", "${s.name} (ability ${s.ability}) joined your scouting team on ${money(s.wage.toLong())} per week.")
        return true to "${s.name} hired."
    }

    fun fire(w: World, scoutId: Int): Pair<Boolean, String> {
        val b = w.biz()
        val s = b.scouts.firstOrNull { it.id == scoutId } ?: return false to "That scout is not on your staff."
        b.scouts.remove(s)
        return true to "${s.name} has left the club."
    }

    fun assign(w: World, scoutId: Int, region: Int, posGroup: Int): Boolean {
        val s = w.biz().scouts.firstOrNull { it.id == scoutId } ?: return false
        if (region < -2 || region > 6 || posGroup < -1 || posGroup > 3) return false
        s.region = region
        s.posGroup = posGroup
        return true
    }

    fun knowledgeOf(w: World, p: Player): Int {
        if (p.clubId == w.humanClubId || w.scouted.contains(p.id)) return 100
        return w.biz().knowledge[p.id] ?: 0
    }

    private fun matchesGroup(p: Player, posGroup: Int): Boolean = when (posGroup) {
        -1 -> true
        3 -> p.pos.group >= 3
        else -> p.pos.group == posGroup
    }

    private fun candidates(w: World, me: Club, b: BusinessState, s: Scout): List<Player> {
        val out = ArrayList<Player>()
        for (p in w.players.values) {
            if (p.clubId == me.id) continue
            if (w.scouted.contains(p.id)) continue
            if ((b.knowledge[p.id] ?: 0) >= 100) continue
            if (!matchesGroup(p, s.posGroup)) continue
            val inRegion = if (s.region == -2) p.clubId < 0
            else p.clubId >= 0 && w.clubs[p.clubId].tier == s.region
            if (inRegion) out.add(p)
        }
        return out
    }

    private fun work(w: World, me: Club, b: BusinessState, s: Scout, rnd: Random) {
        if (s.region == -1) return
        val pool = candidates(w, me, b, s)
        if (pool.isEmpty()) return
        val looks = 1 + s.ability / 7
        val samples = 1 + s.judgement / 6
        for (i in 0 until looks) {
            var pick = pool[rnd.nextInt(pool.size)]
            for (k in 1 until samples) {
                val other = pool[rnd.nextInt(pool.size)]
                if (other.pa > pick.pa) pick = other
            }
            val before = b.knowledge[pick.id] ?: 0
            val after = min(100, before + 18 + s.ability * 2 + me.scout + Analytics.scoutingBonus(w))
            b.knowledge[pick.id] = after
            if (after >= 100 && !w.scouted.contains(pick.id)) {
                w.scouted.add(pick.id)
                s.reportsMade++
                writeReport(w, me, b, s, pick, rnd)
            }
        }
    }

    private fun writeReport(w: World, me: Club, b: BusinessState, s: Scout, p: Player, rnd: Random) {
        val err = Analytics.reportError(w, s.judgement)
        val seenOvr = p.ovr + (if (err > 0) rnd.nextInt(2 * err + 1) - err else 0)
        val seenPa = p.pa + (if (err > 0) rnd.nextInt(2 * err + 1) - err else 0)
        val strength = Lineups.strength(w, me)
        val advice = when {
            seenOvr >= strength -> "Recommend: sign"
            seenPa >= strength + 5 && p.age <= 22 -> "Recommend: one for the future"
            else -> "Recommend: pass"
        }
        val where = if (p.clubId >= 0) w.clubs[p.clubId].name else "free agent"
        val text = "${p.name} (${p.pos.label}, age ${p.age}, $where): about $seenOvr now, potential about ${seenPa / 20f} stars. $advice" + (if (Ecosystem.enabled) " (reliability ${Analytics.reportReliability(err)}%)." else ".")
        b.reports.add(0, ScoutReport(p.id, w.year, w.week, s.name, text))
        while (b.reports.size > 40) b.reports.removeAt(b.reports.size - 1)
        if (advice == "Recommend: sign") {
            w.addNews("Scout report", "${s.name} fully assessed ${p.name}. $advice.")
        }
    }

    fun weekly(w: World, rnd: Random) {
        if (w.clubs.isEmpty()) return
        val me = w.clubs.getOrNull(w.humanClubId) ?: return
        val b = w.biz()
        if (w.week % 8 == 0 && b.scoutMarket.isNotEmpty()) b.scoutMarket.removeAt(0)
        refreshMarket(w, rnd)
        b.knowledge.keys.removeAll { !w.players.containsKey(it) }
        for (s in b.scouts) work(w, me, b, s, rnd)
    }
}
