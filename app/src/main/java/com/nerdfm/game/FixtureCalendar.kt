package com.nerdfm.game

import java.io.Serializable
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** One knockout or group match outside the league. Round numbers start at 1. */
class CupTie(val round: Int, val home: Int, val away: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var played = false
    var hg = 0
    var ag = 0
    /** Club id of the winner; -1 for an undecided or drawn group match. */
    var winner = -1
    var decidedBy = ""
}

class CalendarState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    /** Master switch for cups, continental games and international windows (the league is never affected). */
    var enabled = true
    var doneKey = -1

    // domestic cup (one knockout over every club)
    var cupYear = -1
    val cupTies = ArrayList<CupTie>()
    var cupAlive = ArrayList<Int>()
    var cupWinner = -1

    // continental competition (8 clubs: two groups of four, then semi-finals and a final)
    var contYear = -1
    /** Group A members first (4), then group B (4). */
    val contClubs = ArrayList<Int>()
    val contPoints = HashMap<Int, Int>()
    val contGd = HashMap<Int, Int>()
    val contTies = ArrayList<CupTie>()
    var contWinner = -1
    /** Tier-0 finishing order of the season that just ended (best first); used to qualify for the next one. */
    var nextQualifiers = ArrayList<Int>()

    // what happened this week (cleared every new week)
    val midweek = HashSet<Int>()
    val longTrip = HashSet<Int>()
    /** Players on international duty this week. */
    val away = HashSet<Int>()

    /** Cup and continental matches played this season, per club. */
    val seasonGames = HashMap<Int, Int>()
}

class Referee(val id: Int, val name: String, val strictness: Int, val consistency: Int)

class CalEntry(val week: Int, val label: String, val kind: Int)

/**
 * Factor group H. The league schedule is not touched: cups, the continental competition and international windows
 * sit alongside it, in weeks the league leaves free of other competitions. Extra matches are played with the same
 * match engine through the same ecosystem hooks as league matches, so fatigue and injuries come out of the existing
 * systems. Travel, congestion and referees only reach matches through the capped modifiers.
 */
object FixtureCalendar {
    val CUP_WEEKS = intArrayOf(2, 7, 11, 15, 20, 27, 31)
    val CONT_WEEKS = intArrayOf(4, 9, 13, 18, 23, 25, 29, 33)
    val INTL_WEEKS = intArrayOf(8, 17, 26, 32)
    const val LONG_TRIP_KM = 350f
    const val CONT_SIZE = 8
    private val GROUP_MD = arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(0, 2, 1, 3), intArrayOf(0, 3, 1, 2),
        intArrayOf(1, 0, 3, 2), intArrayOf(2, 0, 3, 1), intArrayOf(3, 0, 2, 1))

    /** Test switch: with false the hook returns before touching any state, as if the feature did not exist. */
    @Volatile var hookEnabled = true

    fun state(w: World): CalendarState {
        val eco = w.eco()
        return eco.calendar ?: CalendarState().also { eco.calendar = it }
    }

    // ---------------------------------------------------------------- geography

    private fun position(w: World, clubId: Int): Pair<Float, Float> {
        val h = Mix.h(w.seed, clubId.toLong(), 7600L)
        return Mix.int(h, 1000).toFloat() to Mix.int(Mix.h(h, 1L), 1000).toFloat()
    }

    /** Pseudo-geography: every club has a fixed spot on a 1000 x 1000 km map. */
    fun distanceKm(w: World, a: Int, b: Int): Float {
        val pa = position(w, a); val pb = position(w, b)
        return hypot(pa.first - pb.first, pa.second - pb.second)
    }

    // ---------------------------------------------------------------- the weekly hook

    /** Called from Game.startWeek before the league matches. Idempotent per week. */
    fun midweek(w: World) {
        if (!hookEnabled || !Ecosystem.enabled) return
        if (w.week !in 1..34) return
        val s = state(w)
        if (!s.enabled) return
        val key = w.year * 40 + w.week
        if (s.doneKey == key) return
        s.doneKey = key
        s.midweek.clear(); s.longTrip.clear(); s.away.clear()
        if (s.cupYear != w.year || s.contYear != w.year) setupSeason(w, s)
        if (w.week in INTL_WEEKS) internationalWindow(w, s)
        val cr = CUP_WEEKS.indexOf(w.week)
        if (cr >= 0) playCupRound(w, s, cr + 1, quick = false)
        val kr = CONT_WEEKS.indexOf(w.week)
        if (kr >= 0) playContinental(w, s, kr, quick = false)
    }

    private fun byRep(w: World): Comparator<Int> = compareByDescending<Int> { w.clubs[it].rep }.thenBy { it }

    private fun setupSeason(w: World, s: CalendarState) {
        if (s.cupYear != w.year) {
            s.cupYear = w.year
            s.cupTies.clear()
            s.cupWinner = -1
            s.cupAlive = ArrayList(w.clubs.map { it.id })
            s.seasonGames.clear()
        }
        if (s.contYear != w.year) {
            s.contYear = w.year
            s.contClubs.clear(); s.contPoints.clear(); s.contGd.clear(); s.contTies.clear(); s.contWinner = -1
            val q = if (s.nextQualifiers.size == CONT_SIZE && s.nextQualifiers.toSet().size == CONT_SIZE && s.nextQualifiers.all { it in w.clubs.indices })
                s.nextQualifiers.toList() else w.leagues[0].clubIds.sortedWith(byRep(w)).take(CONT_SIZE)
            if (q.size == CONT_SIZE) {
                val seeded = q.sortedWith(byRep(w))
                for (i in intArrayOf(0, 3, 4, 7)) s.contClubs.add(seeded[i])
                for (i in intArrayOf(1, 2, 5, 6)) s.contClubs.add(seeded[i])
                for (id in s.contClubs) { s.contPoints[id] = 0; s.contGd[id] = 0; pay(w, id, 0.01) }
            }
        }
        // A career loaded in mid-season catches up quietly (no engine matches) so the brackets are consistent.
        for (i in CUP_WEEKS.indices) if (CUP_WEEKS[i] < w.week) playCupRound(w, s, i + 1, quick = true)
        for (i in CONT_WEEKS.indices) if (CONT_WEEKS[i] < w.week) playContinental(w, s, i, quick = true)
    }

    private fun pay(w: World, clubId: Int, share: Double) {
        val c = w.clubs[clubId]
        val amount = (c.revenue * share).toLong()
        c.balance += amount
        c.weeklyIncome += amount
    }

    // ---------------------------------------------------------------- matches

    private fun strength(w: World, c: Club): Double {
        val top = w.squad(c).sortedByDescending { it.ovr }.take(11)
        return if (top.isEmpty()) 40.0 else top.map { it.ovr }.average()
    }

    private fun poisson(rnd: Random, lambda: Double): Int {
        val l = Math.exp(-lambda)
        var k = 0
        var p = 1.0
        do { k++; p *= rnd.nextDouble() } while (p > l && k < 10)
        return k - 1
    }

    private fun quickScore(w: World, home: Club, away: Club, salt: Long): Pair<Int, Int> {
        val rnd = Random(Mix.h(w.seed, w.year.toLong(), home.id.toLong(), away.id.toLong(), salt))
        val diff = strength(w, home) - strength(w, away)
        return poisson(rnd, (1.35 + 0.15 + 0.05 * diff).coerceIn(0.3, 3.5)) to poisson(rnd, (1.35 - 0.05 * diff).coerceIn(0.3, 3.5))
    }

    /** A real engine match, played through the ecosystem hooks, that records nothing in any league table. */
    private fun simMatch(w: World, home: Club, away: Club, salt: Long): Pair<Int, Int> {
        val rnd = Random(Mix.h(w.seed, w.year.toLong(), w.week.toLong(), home.id.toLong(), away.id.toLong(), salt))
        val ht = MatchTeams.build(w, home, away, true, rnd)
        val at = MatchTeams.build(w, away, home, false, rnd)
        Ecosystem.beginMatch(w, ht, at)
        val eng = MatchEngine(ht, at, rnd, conditions = MatchConditions.forSeed(Mix.h(w.seed, w.year.toLong(), w.week.toLong(), home.id.toLong(), away.id.toLong(), salt + 1L)))
        eng.simToEnd()
        eng.applyToPlayers(rnd)
        Ecosystem.endMatch(w)
        return ht.goals to at.goals
    }

    private fun resolve(w: World, s: CalendarState, tie: CupTie, knockout: Boolean, quick: Boolean, salt: Long) {
        val h = w.clubs[tie.home]; val a = w.clubs[tie.away]
        val score = if (quick) quickScore(w, h, a, salt) else simMatch(w, h, a, salt)
        tie.hg = score.first; tie.ag = score.second; tie.played = true
        if (tie.hg != tie.ag) tie.winner = if (tie.hg > tie.ag) tie.home else tie.away
        else if (knockout) {
            val p = (0.5 + (h.rep - a.rep) * 0.002).coerceIn(0.3, 0.7)
            val roll = Mix.unit(Mix.h(w.seed, w.year.toLong(), tie.round.toLong(), tie.home.toLong(), tie.away.toLong(), salt + 2L))
            tie.winner = if (roll < p) tie.home else tie.away
            tie.decidedBy = "extra time and penalties"
        }
        if (!quick) {
            s.midweek.add(h.id); s.midweek.add(a.id)
            s.seasonGames[h.id] = (s.seasonGames[h.id] ?: 0) + 1
            s.seasonGames[a.id] = (s.seasonGames[a.id] ?: 0) + 1
            if (distanceKm(w, h.id, a.id) > LONG_TRIP_KM) s.longTrip.add(a.id)
        }
    }

    private fun tellHuman(w: World, tie: CupTie, title: String, quick: Boolean) {
        if (quick || (tie.home != w.humanClubId && tie.away != w.humanClubId)) return
        val me = w.humanClubId
        val opp = w.clubs[if (tie.home == me) tie.away else tie.home]
        val mine = if (tie.home == me) tie.hg else tie.ag
        val theirs = if (tie.home == me) tie.ag else tie.hg
        val verdict = if (tie.winner == me) "won" else if (tie.winner == -1) "drew" else "lost"
        w.addNews(title, "You $verdict $mine-$theirs ${if (tie.home == me) "at home to" else "away at"} ${opp.name}." + (if (tie.decidedBy.isNotEmpty()) " Decided by ${tie.decidedBy}." else ""))
    }

    // ---------------------------------------------------------------- domestic cup

    /** Plays round [round]: the survivors are shuffled by a seeded draw; with an odd number the biggest club gets a bye. */
    private fun playCupRound(w: World, s: CalendarState, round: Int, quick: Boolean) {
        if (s.cupTies.any { it.round == round }) return
        if (s.cupAlive.size <= 1) return
        val alive = ArrayList(s.cupAlive.sorted())
        alive.shuffle(Random(Mix.h(w.seed, w.year.toLong(), round.toLong(), 7500L)))
        val next = ArrayList<Int>()
        if (alive.size % 2 == 1) {
            val bye = alive.sortedWith(byRep(w)).first()
            alive.remove(bye)
            next.add(bye)
        }
        var i = 0
        while (i + 1 < alive.size) {
            val tie = CupTie(round, alive[i], alive[i + 1])
            i += 2
            resolve(w, s, tie, knockout = true, quick = quick, salt = 100L + round)
            s.cupTies.add(tie)
            next.add(tie.winner)
            if (!quick) { pay(w, tie.winner, 0.0015 * round); tellHuman(w, tie, "Cup round $round", false) }
        }
        s.cupAlive = next
        if (next.size == 1) {
            s.cupWinner = next[0]
            if (!quick) {
                pay(w, s.cupWinner, 0.03)
                if (s.cupWinner == w.humanClubId) { w.trophies++; w.addNews("Cup winners!", "You won the domestic cup.") }
            }
        }
    }

    // ---------------------------------------------------------------- continental competition

    private fun table(w: World, s: CalendarState, group: Int): List<Int> {
        val members = s.contClubs.subList(group * 4, group * 4 + 4)
        return members.sortedWith(compareByDescending<Int> { s.contPoints[it] ?: 0 }.thenByDescending { s.contGd[it] ?: 0 }.then(byRep(w)))
    }

    fun groupTable(w: World, group: Int): List<Int> {
        val s = w.ecosystem?.calendar ?: return emptyList()
        if (s.contClubs.size != CONT_SIZE) return emptyList()
        return table(w, s, group)
    }

    private fun playContinental(w: World, s: CalendarState, k: Int, quick: Boolean) {
        if (s.contClubs.size != CONT_SIZE) return
        val round = k + 1
        if (s.contTies.any { it.round == round }) return
        if (k in 0..5) {
            val md = GROUP_MD[k]
            for (g in 0..1) {
                val members = s.contClubs.subList(g * 4, g * 4 + 4)
                for (pair in 0..1) {
                    val tie = CupTie(round, members[md[pair * 2]], members[md[pair * 2 + 1]])
                    resolve(w, s, tie, knockout = false, quick = quick, salt = 200L + round)
                    s.contTies.add(tie)
                    val hp = if (tie.hg > tie.ag) 3 else if (tie.hg == tie.ag) 1 else 0
                    val ap = if (tie.ag > tie.hg) 3 else if (tie.hg == tie.ag) 1 else 0
                    s.contPoints[tie.home] = (s.contPoints[tie.home] ?: 0) + hp
                    s.contPoints[tie.away] = (s.contPoints[tie.away] ?: 0) + ap
                    s.contGd[tie.home] = (s.contGd[tie.home] ?: 0) + tie.hg - tie.ag
                    s.contGd[tie.away] = (s.contGd[tie.away] ?: 0) + tie.ag - tie.hg
                    if (!quick) {
                        if (tie.winner != -1) pay(w, tie.winner, 0.004) else { pay(w, tie.home, 0.0015); pay(w, tie.away, 0.0015) }
                        tellHuman(w, tie, "Continental group match", false)
                    }
                }
            }
        } else if (k == 6) {
            if (s.contTies.count { it.round <= 6 } < 24) return
            val a = table(w, s, 0); val b = table(w, s, 1)
            val semis = listOf(CupTie(7, a[0], b[1]), CupTie(7, b[0], a[1]))
            for (tie in semis) {
                resolve(w, s, tie, knockout = true, quick = quick, salt = 300L)
                s.contTies.add(tie)
                if (!quick) { pay(w, tie.winner, 0.01); tellHuman(w, tie, "Continental semi-final", false) }
            }
        } else if (k == 7) {
            val finalists = s.contTies.filter { it.round == 7 }.map { it.winner }
            if (finalists.size != 2 || finalists.any { it == -1 }) return
            val ordered = finalists.sortedWith(byRep(w))
            val tie = CupTie(8, ordered[0], ordered[1])
            resolve(w, s, tie, knockout = true, quick = quick, salt = 400L)
            s.contTies.add(tie)
            s.contWinner = tie.winner
            if (!quick) {
                pay(w, tie.winner, 0.04)
                tellHuman(w, tie, "Continental final", false)
                if (tie.winner == w.humanClubId) { w.trophies++; w.addNews("Continental champions!", "You won the continental competition.") }
            }
        }
    }

    // ---------------------------------------------------------------- international windows

    private fun internationalWindow(w: World, s: CalendarState) {
        val n = Names.nations.size
        val byNation = HashMap<Int, ArrayList<Player>>()
        for (p in w.players.values) {
            if (p.clubId < 0 || p.age !in 18..36 || p.injWeeks > 0 || p.suspWeeks > 0) continue
            byNation.getOrPut(Math.floorMod(p.nation, n)) { ArrayList() }.add(p)
        }
        var mine = 0
        for (nation in byNation.keys.sorted()) {
            val picked = byNation.getValue(nation).sortedWith(compareByDescending<Player> { it.ovr }.thenBy { it.id }).take(14)
            for (p in picked) {
                s.away.add(p.id)
                p.morale = clampF(p.morale + 2f, 15f, 100f)
                p.condition = clampF(p.condition - 8f, 5f, 100f)
                val h = Mix.h(w.seed, w.year.toLong(), w.week.toLong(), p.id.toLong(), 7700L)
                if (Mix.unit(h) < 0.015f) {
                    p.injWeeks = 1 + Mix.int(Mix.h(h, 1L), 4)
                    p.injType = "international duty injury"
                    if (p.clubId == w.humanClubId) w.addNews("Injured on international duty", "${p.name} came back from international duty injured (${p.injWeeks} week(s) out).")
                }
                if (p.clubId == w.humanClubId) mine++
            }
        }
        if (mine > 0) w.addNews("International window", "$mine of your players are away on national-team duty. Expect tired legs this week.")
    }

    // ---------------------------------------------------------------- season end and repair

    fun seasonReview(w: World) {
        val s = w.ecosystem?.calendar ?: return
        s.nextQualifiers = ArrayList(w.leagues[0].sortedTable().take(CONT_SIZE).map { it.clubId })
        s.seasonGames.clear()
        s.midweek.clear(); s.longTrip.clear(); s.away.clear()
    }

    fun repair(w: World) {
        val s = w.ecosystem?.calendar ?: return
        val n = w.clubs.size
        s.cupAlive.removeAll { it !in 0 until n }
        s.cupTies.removeAll { it.home !in 0 until n || it.away !in 0 until n }
        s.contTies.removeAll { it.home !in 0 until n || it.away !in 0 until n }
        if (s.contClubs.size != CONT_SIZE || s.contClubs.any { it !in 0 until n }) { s.contClubs.clear(); s.contYear = -1 }
        s.nextQualifiers.removeAll { it !in 0 until n }
        s.away.removeAll { !w.players.containsKey(it) }
    }

    // ---------------------------------------------------------------- reports for the UI

    fun humanCalendar(w: World): List<CalEntry> {
        val me = w.human()
        val s = w.ecosystem?.calendar
        val out = ArrayList<CalEntry>()
        for (f in w.leagueOf(me.id).fixtures) {
            if (f.home != me.id && f.away != me.id) continue
            val home = f.home == me.id
            val opp = w.clubs[if (home) f.away else f.home]
            out.add(CalEntry(f.round, (if (home) "League: vs " else "League: at ") + opp.name + (if (f.played) "  ${f.hg}-${f.ag}" else ""), 0))
        }
        if (s != null) {
            for (t in s.cupTies) if (t.home == me.id || t.away == me.id)
                out.add(CalEntry(CUP_WEEKS[t.round - 1], "Cup round ${t.round}: " + tieText(w, t, me.id), 1))
            val nextCup = (s.cupTies.maxOfOrNull { it.round } ?: 0) + 1
            if (s.cupYear == w.year && s.cupAlive.contains(me.id) && s.cupAlive.size > 1)
                for (r in nextCup..CUP_WEEKS.size) if (CUP_WEEKS[r - 1] >= w.week) out.add(CalEntry(CUP_WEEKS[r - 1], "Cup round $r (if you are still in)", 1))
            for (t in s.contTies) if (t.home == me.id || t.away == me.id)
                out.add(CalEntry(CONT_WEEKS[t.round - 1], "Continental: " + tieText(w, t, me.id), 2))
            if (s.contYear == w.year && s.contClubs.contains(me.id)) {
                val nextCont = (s.contTies.maxOfOrNull { it.round } ?: 0) + 1
                for (r in nextCont..CONT_WEEKS.size) if (CONT_WEEKS[r - 1] >= w.week && r <= 6) out.add(CalEntry(CONT_WEEKS[r - 1], "Continental group match $r", 2))
            }
        }
        for (wk in INTL_WEEKS) out.add(CalEntry(wk, "International window", 3))
        return out.sortedWith(compareBy<CalEntry> { it.week }.thenBy { it.kind })
    }

    private fun tieText(w: World, t: CupTie, me: Int): String {
        val opp = w.clubs[if (t.home == me) t.away else t.home]
        val home = t.home == me
        return (if (home) "vs " else "at ") + opp.name + (if (t.played) "  ${t.hg}-${t.ag}" else "")
    }

    /** Warnings about the human club's upcoming runs of extra matches. */
    fun congestion(w: World): List<String> {
        val weeks = humanCalendar(w).filter { (it.kind == 1 || it.kind == 2) && it.week >= w.week }.map { it.week }.distinct().sorted()
        val out = ArrayList<String>()
        for (i in 0 until weeks.size - 1) if (weeks[i + 1] - weeks[i] <= 2) out.add("Weeks ${weeks[i]}-${weeks[i + 1]}: two extra matches in three weeks on top of the league. Rotate the squad.")
        return out
    }

    fun bracketLines(w: World, continental: Boolean): List<String> {
        val s = w.ecosystem?.calendar ?: return emptyList()
        val ties = if (continental) s.contTies else s.cupTies
        val out = ArrayList<String>()
        for (t in ties.sortedWith(compareByDescending<CupTie> { it.round }.thenBy { it.home }).take(24)) {
            out.add("R${t.round}: ${w.clubs[t.home].name} ${t.hg}-${t.ag} ${w.clubs[t.away].name}" + (if (t.decidedBy.isNotEmpty()) " (pens)" else ""))
        }
        return out
    }
}

object Referees {
    const val POOL = 24

    fun referee(w: World, id: Int): Referee {
        val h = Mix.h(w.seed, id.toLong(), 7800L)
        val nation = Mix.int(h, Names.nations.size)
        return Referee(id, Names.fullName(nation, Random(Mix.h(h, 1L))), 1 + Mix.int(Mix.h(h, 2L), 10), 1 + Mix.int(Mix.h(h, 3L), 10))
    }

    /** Deterministic from the seed, the week and the two clubs. */
    fun forMatch(w: World, home: Int, away: Int, year: Int, week: Int): Referee =
        referee(w, Mix.int(Mix.h(w.seed, year.toLong(), week.toLong(), home.toLong(), away.toLong(), 7801L), POOL))

    fun describe(r: Referee): String =
        r.name + ": " + (if (r.strictness >= 8) "strict" else if (r.strictness <= 3) "lenient" else "average strictness") +
            ", " + (if (r.consistency <= 3) "inconsistent" else if (r.consistency >= 8) "very consistent" else "fairly consistent")
}

/** Travel and fixture congestion on the capped condition and recovery channels. */
object CalendarFactors : FactorProvider {
    override fun factors(w: World, club: Club, ctx: MatchContext): List<Factor> {
        val s = w.ecosystem?.calendar ?: return emptyList()
        val out = ArrayList<Factor>()
        val opp = ctx.opponent
        if (opp != null) {
            if (s.midweek.contains(club.id)) out.add(Factor("Fixture congestion", Channel.CONDITION, -1f))
            if (!ctx.home) {
                val d = FixtureCalendar.distanceKm(w, club.id, opp.id)
                if (d > FixtureCalendar.LONG_TRIP_KM) out.add(Factor("Long away trip", Channel.CONDITION, -min(1.5f, (d - FixtureCalendar.LONG_TRIP_KM) / 700f * 1.5f)))
            }
        } else {
            if (s.midweek.contains(club.id)) out.add(Factor("Midweek match", Channel.RECOVERY, -0.02f))
            if (s.longTrip.contains(club.id)) out.add(Factor("Long midweek trip", Channel.RECOVERY, -0.01f))
        }
        return out
    }
}

/**
 * Referees reach a match only through the pre-match modifiers (the engine's card rates are frozen): a strict referee
 * costs a hard-pressing side a little morale, a lenient one gives it a little, and an inconsistent one tilts toward the
 * home side. Card counts themselves are NOT changed.
 */
object RefereeFactors : FactorProvider {
    override fun factors(w: World, club: Club, ctx: MatchContext): List<Factor> {
        val opp = ctx.opponent ?: return emptyList()
        val home = if (ctx.home) club.id else opp.id
        val away = if (ctx.home) opp.id else club.id
        val r = Referees.forMatch(w, home, away, w.year, w.week)
        var v = 0f
        if (club.tactics.pressing == 2) {
            if (r.strictness >= 7) v -= 0.4f * (r.strictness - 6) / 4f
            else if (r.strictness <= 3) v += 0.2f
        }
        if (r.consistency <= 3) v += if (ctx.home) 0.2f else -0.2f
        return if (v == 0f) emptyList() else listOf(Factor("Referee", Channel.MORALE, v))
    }
}
