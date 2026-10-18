package com.nerdfm.game

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min

/** One club's surroundings. Every club has one; only the human club's are changed by decisions. */
class ClubEnv : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    /** 0..100 */
    var mood = 50f
    /** Supporters who would come when the mood is perfect. Capacity limits the actual attendance. */
    var base = 0
    /** 0..100 */
    var pitch = 60f
    /** 0..100 (the building itself, not its size). */
    var stadium = 70f
}

/** A paid, timed piece of work. The cost is booked once, when the project starts. */
class EnvProject(val kind: String, val label: String, var weeksLeft: Int, val cost: Long) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

class EnvState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    val clubs = HashMap<Int, ClubEnv>()
    /** Human club facility levels, 0..3 each. */
    var training = 0
    var medical = 0
    var academy = 0
    var data = 0
    val projects = ArrayList<EnvProject>()
    var seasonFacilitySpend = 0L
    /** A supporter decision: ticket prices cannot rise this season (the year it was made). -1 = none. */
    var freezeYear = -1
    /** Absolute week of the last fan forum; -1000 = never. */
    var forumAbsWeek = -1000
    /** Press conferences already answered, keyed by (year * 40 + week) * 2 + phase. */
    val answered = HashSet<Int>()
}

enum class PressStyle(val label: String, val blurb: String) {
    CALM("Calm", "Measured and safe: a small, steady lift."),
    CONFIDENT("Confident", "Bold: pays off after a good result, backfires after a bad one."),
    BLUNT("Blunt", "Honest and sharp: can fire up the fans, but upsets the player you single out."),
    DEFLECT("Deflect", "Say nothing: costs you a little goodwill.")
}

class PressQuestion(val phase: Int, val topic: String, val playerId: Int)

/**
 * Factor groups F and G: fans, stadium, pitch, facilities and media. Effects reach matches only through the capped
 * modifiers (home crowd on MORALE, pitch on SHARPNESS/CONDITION/RECOVERY, medical centre on RECOVERY) and through
 * numbers the game already has (training multiplier, academy rating, gate receipts). MatchEngine is never touched.
 */
object ClubEnvRules {
    const val FACILITY_MAX = 3
    const val FACILITY_KINDS = "TRAINING,MEDICAL,ACADEMY,DATA"
    const val FORUM_COOLDOWN = 8

    fun state(w: World): EnvState {
        val eco = w.eco()
        var s = eco.env
        if (s == null) {
            s = EnvState()
            eco.env = s
            for (c in w.clubs) {
                val e = ClubEnv()
                val h = Mix.h(w.seed, c.id.toLong(), 7300L)
                e.mood = if (c.human) 50f else (50f + (c.rep - 50) * 0.2f).coerceIn(30f, 70f)
                e.base = max(1000, (c.capacity() * 1.05f).toInt())
                e.pitch = 60f + (Mix.int(h, 11) - 5)
                e.stadium = 70f
                s.clubs[c.id] = e
            }
        }
        return s
    }

    private fun env(w: World, clubId: Int): ClubEnv = state(w).clubs.getOrPut(clubId) { ClubEnv() }

    /** Read-only lookups for the pure factor providers: never create state. */
    private fun peekEnv(w: World, clubId: Int): ClubEnv? = w.ecosystem?.env?.clubs?.get(clubId)

    // ---------------------------------------------------------------- fans and attendance

    fun mood(w: World, clubId: Int): Float = peekEnv(w, clubId)?.mood ?: 50f

    fun attendance(w: World, c: Club): Int {
        val e = peekEnv(w, c.id) ?: return min(c.capacity(), (c.capacity() * 0.8f).toInt())
        return min(c.capacity(), (e.base * (0.5f + 0.5f * e.mood / 100f)).toInt()).coerceAtLeast(0)
    }

    fun moodLabel(m: Float): String = when {
        m >= 80f -> "Ecstatic"
        m >= 62f -> "Happy"
        m >= 40f -> "Content"
        m >= 22f -> "Restless"
        else -> "Hostile"
    }

    /** Extra multiplier on the human club's gate receipts: exactly 1.0 at mood 50, +-8% at the extremes. */
    fun gateFactor(w: World, c: Club): Double {
        if (!Ecosystem.enabled || !c.human) return 1.0
        val e = peekEnv(w, c.id) ?: return 1.0
        return 0.92 + 0.16 * (e.mood / 100.0)
    }

    private fun starCount(w: World, c: Club): Int {
        val cut = WorldGen.MEAN_OVR[c.tier.coerceIn(0, WorldGen.MEAN_OVR.size - 1)] + 8f
        return w.squad(c).count { it.ovr >= cut }
    }

    // ---------------------------------------------------------------- weekly cycle

    /** Called from Ecosystem.preWeek. */
    fun weekly(w: World) {
        val s = state(w)
        val rnd = w.rng(9700)
        val abs = w.year * 40 + w.week
        for (c in w.clubs) {
            val e = s.clubs.getOrPut(c.id) { ClubEnv() }
            // --- fans
            var d = (c.lastPts - 1) * 1.4f + (50f - e.mood) * 0.04f
            d += min(0.3f, 0.1f * starCount(w, c))
            val sq = max(1, c.players.size)
            d += 0.25f * Identity.homegrownCount(w, c.id) / sq
            if (e.stadium < 50f) d -= (50f - e.stadium) / 50f * 0.2f
            if (c.human) {
                val level = w.biz().ticketLevel.coerceIn(0, Business.PRICE_FACTORS.size - 1)
                d -= (level - 2) * 0.25f
                if (s.freezeYear == w.year) d += 0.3f
            }
            e.mood = clampF(e.mood + d, 0f, 100f)
            e.base = (e.base * (1f + (e.mood - 50f) / 50f * 0.0004f)).toInt().coerceIn((c.capacity() * 0.6f).toInt().coerceAtLeast(500), (c.capacity() * 1.6f).toInt().coerceAtLeast(1000))
            // --- pitch: moves slowly toward a target set by the groundkeeper and the season
            val gk = Staffing.centered(w, c.id, StaffRole.GROUNDKEEPER).coerceIn(-1f, 1f)
            val winter = if (w.week in 14..26) -6f else 0f
            val target = 60f + 12f * gk + winter
            e.pitch = clampF(e.pitch + (target - e.pitch) * 0.06f, 0f, 100f)
            // --- stadium: the human club's decays until repaired, AI clubs keep theirs up
            if (c.human) e.stadium = clampF(e.stadium - 0.15f, 0f, 100f)
            else e.stadium = clampF(e.stadium + (65f - e.stadium) * 0.02f, 0f, 100f)
        }
        progressProjects(w, s)
        Media.rumours(w, rnd)
        if (s.answered.size > 200) s.answered.removeAll { it < abs * 2 - 200 }
    }

    // ---------------------------------------------------------------- facilities and projects (human club)

    fun level(s: EnvState, kind: String): Int = when (kind) {
        "TRAINING" -> s.training; "MEDICAL" -> s.medical; "ACADEMY" -> s.academy; "DATA" -> s.data; else -> 0
    }

    /** Facility level (0..3) of the human club scaled by [perLevel]; AI clubs have no facility model and get 0. */
    fun facilityBonus(w: World, clubId: Int, kind: String, perLevel: Float): Float {
        if (!Ecosystem.enabled || clubId != w.humanClubId) return 0f
        val s = w.eco().env ?: return 0f
        return level(s, kind) * perLevel
    }

    fun kindLabel(kind: String): String = when (kind) {
        "TRAINING" -> "Training ground"; "MEDICAL" -> "Medical centre"; "ACADEMY" -> "Academy"; "DATA" -> "Data suite"
        "PITCH" -> "Pitch relaying"; "STADIUM" -> "Stadium repairs"; else -> kind
    }

    fun upgradeCost(w: World, kind: String): Long {
        val me = w.human()
        val s = state(w)
        return when (kind) {
            "PITCH" -> me.revenue / 60L
            "STADIUM" -> me.revenue / 40L
            else -> (me.revenue / 25L) * (level(s, kind) + 1)
        }
    }

    fun upgradeWeeks(w: World, kind: String): Int = when (kind) {
        "PITCH" -> 4
        "STADIUM" -> 8
        else -> 6 + 4 * level(state(w), kind)
    }

    /** Null when the project can start, otherwise the reason it cannot (shown on the disabled button). */
    fun blockedReason(w: World, kind: String): String? {
        val s = state(w)
        val me = w.human()
        if (s.projects.any { it.kind == kind }) return "Already under way"
        if (kind in FACILITY_KINDS.split(",") && level(s, kind) >= FACILITY_MAX) return "Fully upgraded"
        val cost = upgradeCost(w, kind)
        if (me.balance < cost) return "Needs " + money(cost) + ", you have " + money(me.balance)
        return null
    }

    fun startProject(w: World, kind: String): Pair<Boolean, String> {
        val reason = blockedReason(w, kind)
        if (reason != null) return false to reason
        val s = state(w)
        val me = w.human()
        val cost = upgradeCost(w, kind)
        val weeks = upgradeWeeks(w, kind)
        me.balance -= cost
        s.seasonFacilitySpend += cost
        s.projects.add(EnvProject(kind, kindLabel(kind), weeks, cost))
        w.addNews("Project approved", kindLabel(kind) + " work has begun. Cost " + money(cost) + ", about " + weeks + " weeks.")
        return true to kindLabel(kind) + " started for " + money(cost) + " (" + weeks + " weeks)."
    }

    private fun progressProjects(w: World, s: EnvState) {
        val done = ArrayList<EnvProject>()
        for (p in s.projects) { p.weeksLeft--; if (p.weeksLeft <= 0) done.add(p) }
        for (p in done) {
            s.projects.remove(p)
            when (p.kind) {
                "TRAINING" -> s.training = min(FACILITY_MAX, s.training + 1)
                "MEDICAL" -> s.medical = min(FACILITY_MAX, s.medical + 1)
                "ACADEMY" -> s.academy = min(FACILITY_MAX, s.academy + 1)
                "DATA" -> s.data = min(FACILITY_MAX, s.data + 1)
                "PITCH" -> { val e = env(w, w.humanClubId); e.pitch = clampF(e.pitch + 30f, 0f, 100f) }
                "STADIUM" -> { val e = env(w, w.humanClubId); e.stadium = clampF(e.stadium + 35f, 0f, 100f) }
            }
            w.addNews("Project finished", p.label + " is complete.")
        }
    }

    /** Training-ground effect on the weekly progression chance (human club; 1.0 for everyone else). */
    fun trainingBoost(w: World, clubId: Int): Float {
        if (clubId != w.humanClubId) return 1f
        val s = w.ecosystem?.env ?: return 1f
        return 1f + 0.02f * s.training
    }

    /** Academy effect on the club's effective youth rating (human club; 0 for everyone else). */
    fun academyShift(w: World, clubId: Int): Float {
        if (clubId != w.humanClubId) return 0f
        val s = w.ecosystem?.env ?: return 0f
        return 0.5f * s.academy
    }

    /** The data-suite level, 0..3. Nothing consumes it until the analytics milestone is built. */
    fun dataSuiteLevel(w: World): Int = w.ecosystem?.env?.data ?: 0

    // ---------------------------------------------------------------- supporter decisions (human club)

    fun freezePrices(w: World): Pair<Boolean, String> {
        val s = state(w)
        if (s.freezeYear == w.year) return false to "Ticket prices are already frozen for this season."
        s.freezeYear = w.year
        w.biz().ticketLevel = min(w.biz().ticketLevel, 2)
        return true to "Prices are frozen for the season. Supporters are grateful, but you cannot raise them."
    }

    fun priceFrozen(w: World): Boolean = w.ecosystem?.env?.freezeYear == w.year

    fun forumBlockedReason(w: World): String? {
        val s = state(w)
        val abs = w.year * 40 + w.week
        val wait = FORUM_COOLDOWN - (abs - s.forumAbsWeek)
        return if (wait > 0) "Next forum in $wait week(s)" else null
    }

    fun holdForum(w: World): Pair<Boolean, String> {
        val reason = forumBlockedReason(w)
        if (reason != null) return false to reason
        val s = state(w)
        s.forumAbsWeek = w.year * 40 + w.week
        val e = env(w, w.humanClubId)
        val gain = if (w.human().lastPts >= 1) 3f else 1.5f
        e.mood = clampF(e.mood + gain, 0f, 100f)
        w.addNews("Fan forum", "You met supporters' representatives. They appreciated being listened to.")
        return true to "The forum went well: mood +" + gain.toInt() + "."
    }

    // ---------------------------------------------------------------- season review

    fun seasonReview(w: World) {
        val s = state(w)
        s.seasonFacilitySpend = 0L
        if (s.freezeYear in 0 until w.year) s.freezeYear = -1
    }

    fun prune(w: World) {
        val s = w.ecosystem?.env ?: return
        s.clubs.keys.removeAll { it < 0 || it >= w.clubs.size }
    }

    fun repair(w: World) {
        val s = w.ecosystem?.env ?: return
        for (e in s.clubs.values) {
            if (!e.mood.isFinite()) e.mood = 50f
            if (!e.pitch.isFinite()) e.pitch = 60f
            if (!e.stadium.isFinite()) e.stadium = 70f
            e.mood = e.mood.coerceIn(0f, 100f); e.pitch = e.pitch.coerceIn(0f, 100f); e.stadium = e.stadium.coerceIn(0f, 100f)
            if (e.base < 0) e.base = 1000
        }
        s.training = s.training.coerceIn(0, FACILITY_MAX); s.medical = s.medical.coerceIn(0, FACILITY_MAX)
        s.academy = s.academy.coerceIn(0, FACILITY_MAX); s.data = s.data.coerceIn(0, FACILITY_MAX)
        prune(w)
    }
}

/** Crowd, pitch and facility effects on the capped channels. */
object EnvFactors : FactorProvider {
    override fun factors(w: World, club: Club, ctx: MatchContext): List<Factor> {
        val s = w.ecosystem?.env ?: return emptyList()
        val e = s.clubs[club.id] ?: return emptyList()
        val out = ArrayList<Factor>()
        // Weekly recovery: pitch quality and (human club) the medical centre.
        val recovery = ((e.pitch - 60f) / 40f * 0.02f).coerceIn(-0.03f, 0.02f) + (if (club.human) 0.01f * s.medical else 0f)
        if (recovery != 0f) out.add(Factor("Pitch and medical centre", Channel.RECOVERY, recovery))
        // Match-day effects belong to the home side only.
        if (ctx.opponent != null && ctx.home) {
            val crowd = ((e.mood - 50f) / 25f).coerceIn(-1f, 1f)
            if (crowd != 0f) out.add(Factor("Crowd", Channel.MORALE, crowd))
            val q = e.pitch
            if (q < 60f) {
                val tempo = 0.8f + 0.2f * club.tactics.tempo
                out.add(Factor("Poor pitch", Channel.SHARPNESS, -((60f - q) / 60f) * 1.5f * tempo))
                out.add(Factor("Poor pitch", Channel.CONDITION, -((60f - q) / 60f)))
            } else if (q > 60f) {
                out.add(Factor("Good pitch", Channel.SHARPNESS, (q - 60f) / 40f * 0.5f))
            }
        }
        return out
    }
}

/** Press conferences, answers and rumours. */
object Media {
    private val TOPICS = listOf("your recent form", "your star player", "injuries in the squad", "the board's patience", "a rival's comments")

    private fun key(w: World, phase: Int) = (w.year * 40 + w.week) * 2 + phase

    /** Phase 0 = before the match, 1 = after it. Null when nothing is asked or it has been answered already. */
    fun question(w: World, phase: Int): PressQuestion? {
        if (w.week !in 1..34) return null
        val s = w.ecosystem?.env
        if (s != null && s.answered.contains(key(w, phase))) return null
        val hasMatch = w.humanFixture() != null
        if (phase == 0 && !hasMatch) return null
        if (phase == 1 && (hasMatch || w.lastResult.isEmpty())) return null
        val h = Mix.h(w.seed, w.year.toLong(), w.week.toLong(), phase.toLong(), 7400L)
        val top = w.squad(w.human()).sortedWith(compareByDescending<Player> { it.ovr }.thenBy { it.id }).take(11)
        if (top.isEmpty()) return null
        return PressQuestion(phase, TOPICS[Mix.int(h, TOPICS.size)], top[Mix.int(Mix.h(h, 1L), top.size)].id)
    }

    /** Media pressure grows with the size of the club. */
    fun pressure(c: Club): Float = 0.6f + 0.8f * (c.rep / 100f).coerceIn(0f, 1f)

    fun answer(w: World, phase: Int, style: PressStyle): Pair<Boolean, String> {
        val q = question(w, phase) ?: return false to "There is no press conference to answer."
        val s = ClubEnvRules.state(w)
        val me = w.human()
        val e = s.clubs.getOrPut(me.id) { ClubEnv() }
        val p = w.players[q.playerId]
        val good = me.lastPts >= 1
        var board = 0f; var fans = 0f; var player = 0f
        when (style) {
            PressStyle.CALM -> { board = 0.2f; fans = 0.3f; player = 0.5f }
            PressStyle.CONFIDENT -> if (good) { board = 0.5f; fans = 0.8f; player = 1.5f } else { board = -0.5f; fans = -0.8f; player = -0.5f }
            PressStyle.BLUNT -> { board = if (me.lastPts == 3) 0.3f else -0.4f; fans = if (good) 0.6f else -0.3f; player = -1.5f }
            PressStyle.DEFLECT -> { board = 0f; fans = -0.2f; player = 0f }
        }
        val k = pressure(me)
        me.boardConfidence = clampF(me.boardConfidence + board * k, 0f, 100f)
        e.mood = clampF(e.mood + fans * k, 0f, 100f)
        if (p != null) p.morale = clampF(p.morale + player * k, 15f, 100f)
        s.answered.add(key(w, phase))
        w.addNews("Press conference", "Asked about " + q.topic + (if (p != null) " (and about " + p.name + ")" else "") + ", you gave a " + style.label.lowercase() + " answer.")
        return true to "You gave a " + style.label.lowercase() + " answer."
    }

    /** A headline now and then. Words only: rumours change nothing in the game. */
    fun rumours(w: World, rnd: kotlin.random.Random) {
        if (rnd.nextFloat() >= 0.12f) return
        val top = w.squad(w.human()).sortedWith(compareByDescending<Player> { it.ovr }.thenBy { it.id }).take(8)
        if (top.isEmpty()) return
        val p = top[rnd.nextInt(top.size)]
        val buyer = w.clubs[rnd.nextInt(w.clubs.size)]
        if (buyer.id == w.humanClubId) return
        w.addNews("Rumour", "${buyer.name} are said to be monitoring ${p.name}. Nothing has been confirmed.")
    }
}
