package com.nerdfm.game

import java.io.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Deterministic hashing: people-layer facts never depend on call order or on the shared random streams. */
object Mix {
    fun h(vararg v: Long): Long {
        var x = 1469598103934665603L
        for (e in v) { x = (x xor e) * 1099511628211L; x = x xor (x ushr 29) }
        return x
    }
    /** 0 <= result < 1 */
    fun unit(h: Long): Float = ((h ushr 8) and 0xFFFFFFL).toInt() / 16777216f
    fun int(h: Long, bound: Int): Int = ((h ushr 8) and 0xFFFFFFL).toInt() % bound
}

/** One player's background. Compact on purpose: there are thousands of these in a save. */
class PlayerPerson : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    /** Language id == nation index (languages are fictional, one per fictional nation). */
    var mother = 0
    var l1 = -1
    var f1 = 0f
    var l2 = -1
    var f2 = 0f
    var culture = 0
    /** 0..100: how settled he is in the club's culture. */
    var adaptation = 100f
    /** 0 greedy, 1 loyal, 2 aggressive. */
    var agent = 0
    var tenure = 0
    var clubId = -1
}

enum class EventKind(val label: String, val positive: Boolean) {
    NEW_BABY("New family member", true),
    GOOD_NEWS("Good news", true),
    HOMESICK("Homesickness", false),
    CONTRACT_WORRY("Contract worry", false)
}

enum class EventResponse(val label: String) { TIME_OFF("Give time off"), TALK("Talk to him"), IGNORE("Ignore") }

class PersonalEvent(val id: Int, val playerId: Int, val kind: EventKind, val year: Int, val week: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var resolved = false
    var response: EventResponse? = null
}

class PeopleState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    val players = HashMap<Int, PlayerPerson>()
    /** club id -> language/nation id of the club's working language, fixed the first time it is needed. */
    val clubNation = HashMap<Int, Int>()
    /** Human club only. Key = min id * 1_000_000 + max id (so a tie is symmetric by construction); value = tie type. */
    val ties = HashMap<Long, Int>()
    /** Newest first. */
    val events = ArrayList<PersonalEvent>()
    var nextEventId = 1
}

/**
 * Factor group E: languages, culture, relationships, personal events and agents. All of it acts through state the game
 * already has: morale and sharpness (via the capped modifiers), team cohesion, the weekly progression multiplier and
 * agent fees. MatchEngine is never touched. Every roll is a pure hash of the world seed, so reloading changes nothing.
 */
object People {
    const val TIE_FRIEND = 1
    const val TIE_MENTOR = 2
    const val TIE_RIVAL = 3
    const val EVENT_LIMIT = 40
    /** Communication at or above this score costs nothing. */
    const val COMM_OK = 60f
    /** Largest communication penalties (both are also clamped by the global factor caps). */
    const val MORALE_PENALTY_CAP = 1.5f
    const val SHARPNESS_PENALTY_CAP = 1.0f
    const val AGENT_NAMES = "Greedy,Loyal,Aggressive"

    private const val KEY = 1_000_000L

    fun state(w: World): PeopleState {
        val eco = w.eco()
        return eco.people ?: PeopleState().also { eco.people = it }
    }

    // ---------------------------------------------------------------- languages and culture

    fun languageName(id: Int): String = Names.nations[Math.floorMod(id, Names.nations.size)].name + " tongue"
    fun cultureOf(nation: Int): Int = Math.floorMod(nation, 5)
    fun cultureName(c: Int): String = listOf("Northern", "Coastal", "Highland", "Southern", "Island")[Math.floorMod(c, 5)]
    fun cultureDistance(a: Int, b: Int): Int { val d = abs(a - b); return min(d, 5 - d) }
    private fun family(lang: Int): Int = Math.floorMod(lang, Names.nations.size) / 3
    fun agentName(type: Int): String = AGENT_NAMES.split(",")[Math.floorMod(type, 3)]

    private fun derivedClubNation(w: World, club: Club): Int {
        val counts = IntArray(Names.nations.size)
        for (p in w.squad(club)) counts[Math.floorMod(p.nation, counts.size)]++
        var best = 0
        for (i in counts.indices) if (counts[i] > counts[best]) best = i
        return best
    }

    /** The club's working language: the most common nation in its squad when first looked at, then fixed. */
    fun clubLanguage(w: World, club: Club): Int =
        w.ecosystem?.people?.clubNation?.get(club.id) ?: derivedClubNation(w, club)

    fun fluency(pp: PlayerPerson, lang: Int): Float = when {
        lang == pp.mother -> 100f
        lang == pp.l1 -> pp.f1
        lang == pp.l2 -> pp.f2
        family(lang) == family(pp.mother) -> 20f
        else -> 0f
    }

    private fun setFluency(pp: PlayerPerson, lang: Int, value: Float) {
        if (lang == pp.mother) return
        val v = value.coerceIn(0f, 100f)
        when {
            pp.l1 == lang -> pp.f1 = v
            pp.l2 == lang -> pp.f2 = v
            pp.l1 == -1 -> { pp.l1 = lang; pp.f1 = v }
            pp.l2 == -1 -> { pp.l2 = lang; pp.f2 = v }
            pp.f1 <= pp.f2 -> { pp.l1 = lang; pp.f1 = v }
            else -> { pp.l2 = lang; pp.f2 = v }
        }
    }

    /** Builds a player's background from the seed alone. Used lazily and identically by reads and by the weekly update. */
    fun derive(w: World, p: Player, clubLang: Int): PlayerPerson {
        val n = Names.nations.size
        val pp = PlayerPerson()
        val h = Mix.h(w.seed, p.id.toLong(), 7001L)
        pp.mother = Math.floorMod(p.nation, n)
        pp.culture = cultureOf(pp.mother)
        pp.agent = agentOfId(p.id)
        val r = Mix.unit(Mix.h(h, 2L))
        if (r < 0.35f) {
            val l = Mix.int(Mix.h(h, 3L), n)
            if (l != pp.mother) { pp.l1 = l; pp.f1 = 25f + Mix.int(Mix.h(h, 4L), 60) }
        }
        if (r < 0.08f) {
            val l = Mix.int(Mix.h(h, 5L), n)
            if (l != pp.mother && l != pp.l1) { pp.l2 = l; pp.f2 = 25f + Mix.int(Mix.h(h, 6L), 60) }
        }
        pp.tenure = Mix.int(Mix.h(h, 7L), 150)
        pp.adaptation = 100f
        if (pp.mother != clubLang) {
            // A foreign player already at the club has had time to settle in.
            setFluency(pp, clubLang, max(fluency(pp, clubLang), 30f + Mix.int(Mix.h(h, 8L), 60)))
            pp.adaptation = 70f + Mix.int(Mix.h(h, 9L), 30)
        }
        pp.clubId = p.clubId
        return pp
    }

    /** Read-only access: the stored record when it is current, otherwise the one the next weekly update would create. */
    fun peek(w: World, p: Player, club: Club): PlayerPerson {
        val stored = w.ecosystem?.people?.players?.get(p.id)
        if (stored != null && stored.clubId == p.clubId) return stored
        return derive(w, p, clubLanguage(w, club))
    }

    fun coachQuality(w: World, clubId: Int): Float =
        Staffing.centered(w, clubId, StaffRole.LANGUAGE_COACH).coerceIn(-1f, 1f)

    /** 0..100 combination of fluency in the club's language, time at the club and the language coach. */
    fun communication(w: World, club: Club, pp: PlayerPerson, coachQ: Float): Float {
        val flu = fluency(pp, clubLanguage(w, club))
        val settled = min(100f, pp.tenure * 100f / 52f)
        return (0.7f * flu + 0.3f * settled + 8f * coachQ).coerceIn(0f, 100f)
    }

    /** Average 0..1 shortfall of the club's sixteen best players: poor communication or poor integration. */
    fun shortfall(w: World, club: Club): Float {
        val top = w.squad(club).sortedWith(compareByDescending<Player> { it.ovr }.thenBy { it.id }).take(16)
        if (top.isEmpty()) return 0f
        val q = coachQuality(w, club.id)
        var sum = 0f
        for (p in top) {
            val pp = peek(w, p, club)
            val comm = communication(w, club, pp, q)
            val s = max(0f, COMM_OK - comm) / COMM_OK
            val a = max(0f, 50f - pp.adaptation) / 50f * 0.5f
            sum += min(1f, s + a)
        }
        return sum / top.size
    }

    // ---------------------------------------------------------------- weekly cycle

    /** Called from Ecosystem.preWeek. */
    fun weekly(w: World) {
        val s = state(w)
        for (c in w.clubs) if (!s.clubNation.containsKey(c.id)) s.clubNation[c.id] = derivedClubNation(w, c)
        for (c in w.clubs) {
            val lang = s.clubNation.getValue(c.id)
            val q = coachQuality(w, c.id)
            for (p in w.squad(c)) {
                var pp = s.players[p.id]
                if (pp == null) { pp = derive(w, p, lang); s.players[p.id] = pp }
                else if (pp.clubId != c.id) arrive(pp, p, c.id, lang)
                pp.tenure++
                learn(pp, p, lang, q)
                if (pp.adaptation < 100f) pp.adaptation = min(100f, pp.adaptation + 1.2f + 0.5f * max(0f, q))
            }
        }
        prune(w)
        syncTies(w, s)
        applyCohesion(w, s)
        rollEvents(w, s)
    }

    private fun arrive(pp: PlayerPerson, p: Player, clubId: Int, lang: Int) {
        pp.tenure = 0
        val dist = cultureDistance(pp.culture, cultureOf(lang))
        // Qualitative finding only (THIRD_PARTY_NOTES.md): a big cultural move starts low, and harder on the young.
        pp.adaptation = (100f - 25f * dist - (if (p.age <= 21) 15f else 0f)).coerceIn(10f, 100f)
        pp.clubId = clubId
    }

    private fun learn(pp: PlayerPerson, p: Player, lang: Int, coachQ: Float) {
        if (pp.mother == lang) return
        val rate = (0.35f + 0.7f * max(0f, coachQ)) * (if (p.age <= 23) 1.3f else if (p.age >= 30) 0.8f else 1f)
        setFluency(pp, lang, fluency(pp, lang) + rate)
    }

    /** Drops records of players who left the game (retirements happen in the season rollover). Safe to call any time. */
    fun prune(w: World) {
        val s = w.ecosystem?.people ?: return
        s.players.keys.removeAll { !w.players.containsKey(it) }
        s.ties.keys.removeAll { k ->
            val a = w.players[(k / KEY).toInt()]
            val b = w.players[(k % KEY).toInt()]
            a == null || b == null || a.clubId != w.humanClubId || b.clubId != w.humanClubId
        }
        s.events.removeAll { !w.players.containsKey(it.playerId) }
    }

    /** Load-time repair of damaged numbers; absent sections stay absent. */
    fun repair(w: World) {
        val s = w.ecosystem?.people ?: return
        for (pp in s.players.values) {
            if (!pp.f1.isFinite()) pp.f1 = 0f
            if (!pp.f2.isFinite()) pp.f2 = 0f
            pp.f1 = pp.f1.coerceIn(0f, 100f); pp.f2 = pp.f2.coerceIn(0f, 100f)
            if (!pp.adaptation.isFinite()) pp.adaptation = 100f
            pp.adaptation = pp.adaptation.coerceIn(0f, 100f)
            if (pp.tenure < 0) pp.tenure = 0
        }
        prune(w)
    }

    // ---------------------------------------------------------------- relationships (human club only)

    fun tieKey(a: Int, b: Int): Long = min(a, b).toLong() * KEY + max(a, b)

    /** Pure rule for the tie between two players of the same club; 0 when there is none. */
    fun tieOf(w: World, a: Player, b: Player, pa: PlayerPerson, pb: PlayerPerson): Int {
        val lo = min(a.id, b.id).toLong(); val hi = max(a.id, b.id).toLong()
        val h = Mix.unit(Mix.h(w.seed, lo, hi, 7100L))
        val h2 = Mix.unit(Mix.h(w.seed, lo, hi, 7101L))
        val older = if (a.age >= b.age) a else b
        val younger = if (older === a) b else a
        if (older.age >= 28 && younger.age <= 21 && older.attrs[Attr.Leadership.ordinal] >= 11 &&
            older.pos.group == younger.pos.group && h < 0.6f) return TIE_MENTOR
        if (abs(a.age - b.age) <= 6 && pa.tenure >= 8 && pb.tenure >= 8 && pa.mother == pb.mother && h < 0.10f) return TIE_FRIEND
        if (a.pos.group == b.pos.group && abs(a.ovr - b.ovr) <= 3 && a.ambition >= 13 && b.ambition >= 13 && h2 < 0.15f) return TIE_RIVAL
        return 0
    }

    private fun syncTies(w: World, s: PeopleState) {
        val squad = w.squad(w.human())
        val ids = squad.map { it.id }.toHashSet()
        s.ties.keys.removeAll { k -> !ids.contains((k / KEY).toInt()) || !ids.contains((k % KEY).toInt()) }
        for (i in squad.indices) {
            val a = squad[i]
            val pa = s.players[a.id] ?: continue
            for (j in i + 1 until squad.size) {
                val b = squad[j]
                val pb = s.players[b.id] ?: continue
                val t = tieOf(w, a, b, pa, pb)
                val key = tieKey(a.id, b.id)
                if (t == 0) s.ties.remove(key) else s.ties[key] = t
            }
        }
    }

    /** Friendships steady the dressing room, rivalries unsettle it. Bounded, and tiny next to the existing cohesion rules. */
    private fun applyCohesion(w: World, s: PeopleState) {
        val me = w.human()
        val size = max(1, me.players.size)
        val friends = s.ties.values.count { it == TIE_FRIEND }
        val rivals = s.ties.values.count { it == TIE_RIVAL }
        val delta = (((friends - 1.2f * rivals) / size) - 0.3f) * 0.5f
        me.teamCohesion = clampF(me.teamCohesion + delta.coerceIn(-0.25f, 0.25f), 0f, 100f)
    }

    /** A mentor next to a young player of his position speeds progression slightly (human club; 1.0 for everyone else). */
    fun trainingBoost(w: World, clubId: Int): Float {
        if (clubId != w.humanClubId) return 1f
        val s = w.ecosystem?.people ?: return 1f
        val pairs = s.ties.values.count { it == TIE_MENTOR }
        return 1f + min(0.04f, 0.01f * pairs)
    }

    fun tiesOf(w: World, p: Player): List<Pair<Player, Int>> {
        val s = w.ecosystem?.people ?: return emptyList()
        val out = ArrayList<Pair<Player, Int>>()
        for ((k, t) in s.ties) {
            val a = (k / KEY).toInt(); val b = (k % KEY).toInt()
            val other = if (a == p.id) b else if (b == p.id) a else continue
            val o = w.players[other] ?: continue
            out.add(o to t)
        }
        return out.sortedWith(compareBy<Pair<Player, Int>> { it.second }.thenBy { it.first.name })
    }

    /** Friendship groups of three or more in the human squad, biggest first. */
    fun cliques(w: World): List<List<Player>> {
        val s = w.ecosystem?.people ?: return emptyList()
        val squad = w.squad(w.human())
        val parent = HashMap<Int, Int>()
        for (p in squad) parent[p.id] = p.id
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]!!
            return r
        }
        for ((k, t) in s.ties) {
            if (t != TIE_FRIEND) continue
            val a = (k / KEY).toInt(); val b = (k % KEY).toInt()
            if (!parent.containsKey(a) || !parent.containsKey(b)) continue
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[max(ra, rb)] = min(ra, rb)
        }
        val groups = HashMap<Int, ArrayList<Player>>()
        for (p in squad) groups.getOrPut(find(p.id)) { ArrayList() }.add(p)
        return groups.values.filter { it.size >= 3 }.sortedWith(compareByDescending<ArrayList<Player>> { it.size }.thenBy { it[0].id })
    }

    // ---------------------------------------------------------------- personal events (human club only)

    private fun absWeek(year: Int, week: Int): Int = year * 40 + week

    private fun rollEvents(w: World, s: PeopleState) {
        for (e in s.events.toList()) {
            if (!e.resolved && absWeek(w.year, w.week) - absWeek(e.year, e.week) >= 4) resolve(w, e, EventResponse.IGNORE, auto = true)
        }
        val me = w.human()
        val lang = clubLanguage(w, me)
        for (p in w.squad(me)) {
            val h = Mix.h(w.seed, w.year.toLong(), w.week.toLong(), p.id.toLong(), 7200L)
            if (Mix.unit(h) >= 0.004f) continue
            if (s.events.any { it.playerId == p.id && (!it.resolved || (it.year == w.year && it.week == w.week)) }) continue
            val pp = s.players[p.id] ?: continue
            val options = ArrayList<EventKind>()
            if (p.age >= 20) options.add(EventKind.NEW_BABY)
            options.add(EventKind.GOOD_NEWS)
            if (pp.mother != lang && pp.adaptation < 75f) options.add(EventKind.HOMESICK)
            if (p.contractEnd <= w.year + 1) options.add(EventKind.CONTRACT_WORRY)
            val kind = options[Mix.int(Mix.h(h, 1L), options.size)]
            val delta = when (kind) { EventKind.NEW_BABY -> 2f; EventKind.GOOD_NEWS -> 3f; EventKind.HOMESICK -> -2.5f; EventKind.CONTRACT_WORRY -> -2f }
            p.morale = clampF(p.morale + delta, 15f, 100f)
            s.events.add(0, PersonalEvent(s.nextEventId++, p.id, kind, w.year, w.week))
            w.addNews(kind.label, when (kind) {
                EventKind.NEW_BABY -> "${p.name} has become a parent. His mood is lifted; you may want to give him some time."
                EventKind.GOOD_NEWS -> "${p.name} has had good news away from football and is in great spirits."
                EventKind.HOMESICK -> "${p.name} is struggling to settle and misses home. A word from you could help."
                EventKind.CONTRACT_WORRY -> "${p.name} is worried about his contract situation and it is on his mind in training."
            })
        }
        while (s.events.size > EVENT_LIMIT) s.events.removeAt(s.events.size - 1)
    }

    private fun resolve(w: World, e: PersonalEvent, r: EventResponse, auto: Boolean) {
        val p = w.players[e.playerId]
        e.resolved = true
        e.response = r
        if (p == null) return
        var morale = 0f
        var sharp = 0f
        var adapt = 0f
        if (e.kind.positive) {
            if (r == EventResponse.TIME_OFF) { morale = 1.5f; sharp = -1f }
            else if (r == EventResponse.TALK) { morale = 1f }
        } else {
            if (r == EventResponse.TIME_OFF) { morale = 2.5f; sharp = -2f; if (e.kind == EventKind.HOMESICK) adapt = 4f }
            else if (r == EventResponse.TALK) { morale = 2f; if (e.kind == EventKind.HOMESICK) adapt = 3f }
            else { morale = -1.5f }
        }
        p.morale = clampF(p.morale + morale, 15f, 100f)
        p.sharpness = clampF(p.sharpness + sharp, 20f, 100f)
        if (adapt != 0f) w.ecosystem?.people?.players?.get(p.id)?.let { it.adaptation = min(100f, it.adaptation + adapt) }
        if (auto) w.addNews("Unanswered: ${e.kind.label}", "${p.name}'s ${e.kind.label.lowercase()} was left unanswered for weeks.")
    }

    fun respond(w: World, eventId: Int, r: EventResponse): Pair<Boolean, String> {
        val s = state(w)
        val e = s.events.firstOrNull { it.id == eventId } ?: return false to "That matter is no longer open."
        if (e.resolved) return false to "You have already dealt with this."
        val p = w.players[e.playerId] ?: return false to "That player has left the club."
        resolve(w, e, r, auto = false)
        return true to "${r.label}: ${p.name} appreciates it."
    }

    fun openEvents(w: World): List<PersonalEvent> = (w.ecosystem?.people?.events ?: emptyList<PersonalEvent>()).filter { !it.resolved }

    // ---------------------------------------------------------------- agents

    /** Depends on the player id alone, so the wage-demand rules (which have no World at hand) can use it. */
    fun agentOfId(playerId: Int): Int = Mix.int(Mix.h(Mix.h(playerId.toLong(), 7001L), 1L), 3)

    /** Multiplier on a renewal wage demand: greedy agents push, loyal ones soften. Exactly 1 when the ecosystem is off. */
    fun renewalDemandMultiplier(p: Player): Double {
        if (!Ecosystem.enabled) return 1.0
        return when (agentOfId(p.id)) { 0 -> 1.10; 1 -> 0.95; else -> 1.06 }
    }

    // ---------------------------------------------------------------- reports for the UI

    class Profile(val mother: String, val learned: List<String>, val culture: String, val adaptation: Int, val tenure: Int,
                  val communication: Int, val agent: String)

    fun profile(w: World, p: Player): Profile? {
        if (p.clubId < 0) return null
        val club = w.clubs[p.clubId]
        val pp = peek(w, p, club)
        val learned = ArrayList<String>()
        if (pp.l1 >= 0) learned.add(languageName(pp.l1) + " " + pp.f1.toInt() + "%")
        if (pp.l2 >= 0) learned.add(languageName(pp.l2) + " " + pp.f2.toInt() + "%")
        return Profile(languageName(pp.mother), learned, cultureName(pp.culture), pp.adaptation.toInt(), pp.tenure,
            communication(w, club, pp, coachQuality(w, club.id)).toInt(), agentName(pp.agent))
    }
}

/** Communication and integration: a small, capped morale and sharpness penalty for clubs with a language problem. */
object PeopleFactors : FactorProvider {
    override fun factors(w: World, club: Club, ctx: MatchContext): List<Factor> {
        val s = People.shortfall(w, club)
        if (s <= 0f) return emptyList()
        return listOf(
            Factor("Communication", Channel.MORALE, -min(People.MORALE_PENALTY_CAP, 4f * s)),
            Factor("Communication", Channel.SHARPNESS, -min(People.SHARPNESS_PENALTY_CAP, 3f * s))
        )
    }
}
