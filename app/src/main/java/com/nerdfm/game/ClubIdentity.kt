package com.nerdfm.game

import java.io.Serializable
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class ClubCulture : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var winning = 50f
    var stability = 50f
    var youth = 50f
    fun score(): Float = 0.40f * winning + 0.35f * stability + 0.25f * youth
}

class IdentityState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    /** player id -> club that developed him (youth intake, or assumed academy product when the state was first built). */
    val homegrown = HashMap<Int, Int>()
    val culture = HashMap<Int, ClubCulture>()
    /** club id -> league minutes this season, and the share played by homegrown players aged 21 or under. */
    val minutesTotal = HashMap<Int, Int>()
    val minutesAcademy = HashMap<Int, Int>()
}

class Sustainability(val finance: Int, val squadAge: Int, val youth: Int, val contracts: Int) {
    val total: Int get() = (0.35f * finance + 0.20f * squadAge + 0.20f * youth + 0.25f * contracts).roundToInt().coerceIn(0, 100)
}

/** Academy pathway, homegrown quota, club culture and the long-term sustainability score (factor group J). */
object Identity {
    const val ACADEMY_MAX_AGE = 21

    fun state(w: World): IdentityState {
        val eco = w.eco()
        var s = eco.identity
        if (s == null) {
            s = IdentityState()
            eco.identity = s
            // A fresh career has no history: treat under-22s as academy products, and some 22-25 year olds as late graduates.
            for (p in w.players.values) {
                if (p.clubId < 0) continue
                if (p.age <= 21 || (p.age <= 25 && Random(w.seed * 31L + p.id).nextInt(100) < 30)) s.homegrown[p.id] = p.clubId
            }
            for (c in w.clubs) s.culture[c.id] = ClubCulture()
        }
        return s
    }

    fun culture(w: World, clubId: Int): ClubCulture = state(w).culture.getOrPut(clubId) { ClubCulture() }

    /** Called by the yearly youth intake for every new academy player. */
    fun onYouthIntake(w: World, club: Club, p: Player) {
        if (!Ecosystem.enabled) return
        state(w).homegrown[p.id] = club.id
    }

    /** The club's youth rating after staff: used by the intake formula instead of Club.youth. */
    fun effectiveYouth(w: World, c: Club): Float =
        if (!Ecosystem.enabled) c.youth.toFloat() else (c.youth + Staffing.youthShift(w, c.id) + ClubEnvRules.academyShift(w, c.id)).coerceIn(1f, 20f)

    fun academyRating(w: World, c: Club): Int = (effectiveYouth(w, c) * 5f).roundToInt().coerceIn(0, 100)

    fun homegrownCount(w: World, clubId: Int): Int {
        val h = state(w).homegrown
        return w.clubs[clubId].players.count { h[it] == clubId }
    }

    fun quota(c: Club): Int = if (c.tier <= 1) 8 else if (c.tier <= 3) 6 else 4

    fun academyMinutePercent(w: World, clubId: Int): Int {
        val s = state(w)
        val total = s.minutesTotal[clubId] ?: 0
        return if (total <= 0) 0 else ((s.minutesAcademy[clubId] ?: 0) * 100f / total).roundToInt()
    }

    fun sustainability(w: World, c: Club): Sustainability {
        val ratio = w.wageBill(c) * 52.0 / max(1L, c.revenue)
        val cash = c.balance.toDouble() / max(1L, c.revenue)
        val finance = (((1.0 - ratio) / 0.5 * 100.0).coerceIn(0.0, 100.0) * 2.0 + ((cash + 0.2) / 0.4 * 100.0).coerceIn(0.0, 100.0)) / 3.0
        val squad = w.squad(c)
        val n = max(1, squad.size)
        val prime = squad.count { it.age in 21..30 }.toDouble() / n
        val secure = squad.count { it.contractEnd >= w.year + 2 }.toDouble() / n
        val youth = homegrownCount(w, c.id).toDouble() / quota(c) * 100.0
        return Sustainability(finance.roundToInt(), (prime / 0.6 * 100.0).coerceIn(0.0, 100.0).roundToInt(),
            youth.coerceIn(0.0, 100.0).roundToInt(), (secure / 0.7 * 100.0).coerceIn(0.0, 100.0).roundToInt())
    }

    /** Match-day morale from club culture, centred on an average culture (50). */
    fun moraleFactor(w: World, clubId: Int): Float = (culture(w, clubId).score() - 50f) / 50f * 1.5f

    /** Weekly bookkeeping. Runs before Game.completeWeek resets lastMinutes. */
    fun weekly(w: World) {
        val s = state(w)
        s.homegrown.keys.removeAll { !w.players.containsKey(it) }
        for (c in w.clubs) {
            val cul = s.culture.getOrPut(c.id) { ClubCulture() }
            val squad = w.squad(c)
            if (squad.isEmpty()) continue
            var minutes = 0; var academy = 0
            for (p in squad) {
                minutes += p.lastMinutes
                if (p.age <= ACADEMY_MAX_AGE && s.homegrown[p.id] == c.id) academy += p.lastMinutes
            }
            s.minutesTotal[c.id] = (s.minutesTotal[c.id] ?: 0) + minutes
            s.minutesAcademy[c.id] = (s.minutesAcademy[c.id] ?: 0) + academy
            cul.winning += (c.lastPts / 3f * 100f - cul.winning) * 0.03f
            cul.stability += (squad.map { minOf(it.seasonsAtClub, 6) }.average().toFloat() / 6f * 100f - cul.stability) * 0.02f
            if (minutes > 0) cul.youth += (academy * 300f / minutes - cul.youth) * 0.03f
            cul.winning = clampF(cul.winning, 0f, 100f); cul.stability = clampF(cul.stability, 0f, 100f); cul.youth = clampF(cul.youth, 0f, 100f)
        }
    }

    fun seasonReview(w: World) {
        val s = state(w)
        s.minutesTotal.clear()
        s.minutesAcademy.clear()
        // Players who left the game (retired) must not linger in the map.
        s.homegrown.keys.removeAll { !w.players.containsKey(it) }
    }
}
