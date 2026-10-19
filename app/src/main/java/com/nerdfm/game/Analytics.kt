package com.nerdfm.game

import java.io.Serializable
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Data-provider package the human club can buy. Prices are shares of weekly revenue, so they scale with the club. */
enum class DataPlan(val label: String, val bonus: Float, val priceShare: Double, val blurb: String) {
    NONE("None", 0f, 0.0, "Basic league tables only."),
    BASIC("Basic", 6f, 0.0010, "Match event data for your own games."),
    PRO("Professional", 12f, 0.0022, "Event data for every league game and an opposition database."),
    ELITE("Elite", 20f, 0.0040, "Tracking data, live models and a full video library.")
}

class AnalyticsState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    /** The human club's data plan as an ordinal of [DataPlan]; -1 = the plan a club of this tier normally has. */
    var plan = -1
}

/** What the analysts say about the next opponent. Everything shown is already degraded by the club's data level. */
class OppositionReport(
    val club: Club,
    val formation: String,
    val mentality: String,
    val strongestGroup: String,
    val weakestGroup: String,
    val keyPlayer: String,
    val keyPlayerOvr: Int,
    val form: String,
    val confidence: Int,
    val advice: String
)

/** A player the data model likes, with the reason. [modelOvr] carries the model's error. */
class DataTarget(val player: Player, val modelOvr: Int, val reason: String)

/**
 * Data and analytics (factor group D). The data level (0-100, 50 = normal) comes from analysts, recruitment staff and the
 * club's data plan. It narrows scouting error, speeds up scouting, produces opposition reports and gives a tiny
 * sharpness benefit through the capped modifier channels. It never changes match results by itself.
 */
object Analytics {
    /** Largest SHARPNESS contribution (points) at the best / worst data level. */
    const val SHARPNESS_SWING = 0.5f

    fun state(w: World): AnalyticsState {
        val eco = w.eco()
        return eco.analytics ?: AnalyticsState().also { eco.analytics = it }
    }

    /** The plan a club of this tier normally has: the same for every club of a tier, so the league average stays put. */
    fun defaultPlan(tier: Int): DataPlan = if (tier <= 1) DataPlan.PRO else if (tier <= 3) DataPlan.BASIC else DataPlan.NONE

    fun planOf(w: World, club: Club): DataPlan {
        if (!club.human) return defaultPlan(club.tier)
        val v = w.ecosystem?.analytics?.plan ?: -1
        return if (v in 0 until DataPlan.values().size) DataPlan.values()[v] else defaultPlan(club.tier)
    }

    private fun price(club: Club, plan: DataPlan): Long = (club.revenue / 52.0 * plan.priceShare).toLong()

    /** Extra weekly cost (or saving) of the human club's plan compared with its normal one. Never saves more than the normal price. */
    fun netWeeklyCost(w: World, c: Club): Long {
        if (!Ecosystem.enabled || !c.human) return 0L
        val base = price(c, defaultPlan(c.tier))
        return max(price(c, planOf(w, c)) - base, -base)
    }

    // ---------------------------------------------------------------- data level

    fun analystQuality(w: World, clubId: Int): Float = Staffing.centered(w, clubId, StaffRole.DATA_ANALYST).coerceIn(-1f, 1f)

    fun recruitmentQuality(w: World, clubId: Int): Float =
        (0.6f * Staffing.centered(w, clubId, StaffRole.HEAD_OF_RECRUITMENT) + 0.4f * Staffing.centered(w, clubId, StaffRole.CHIEF_SCOUT)).coerceIn(-1f, 1f)

    /** 0..100; 50 is a normal club of the same tier with the normal plan. */
    fun dataLevel(w: World, club: Club): Float {
        if (!Ecosystem.enabled) return 50f
        val planShift = planOf(w, club).bonus - defaultPlan(club.tier).bonus
        return (50f + 28f * analystQuality(w, club.id) + 14f * recruitmentQuality(w, club.id) + planShift + 5f * ClubEnvRules.facilityBonus(w, club.id, "DATA", 1f)).coerceIn(0f, 100f)
    }

    fun humanLevel(w: World): Float = dataLevel(w, w.human())

    /** Multiplier on scouting error: 1.0 at a normal level, about 0.69 with the best setup a tier-0 club can build (level 81), 1.5 at level 0. */
    fun errorFactor(w: World): Float = if (!Ecosystem.enabled) 1f else 1.5f - humanLevel(w) / 100f

    /** The club-wide scouting error (in attribute points) used for players the club has not fully scouted. */
    fun scoutError(w: World): Float = (20 - w.human().scout) / 10f * errorFactor(w)

    /** Extra knowledge a scout gains per look thanks to data and recruitment staff (about -5 .. +7). */
    fun scoutingBonus(w: World): Int {
        if (!Ecosystem.enabled) return 0
        val me = w.human()
        return ((humanLevel(w) - 50f) / 10f + 2f * recruitmentQuality(w, me.id)).roundToInt()
    }

    /** Width of the +- error band of a full scouting report (before the scout's own judgement is considered). */
    fun reportError(w: World, judgement: Int): Int {
        val base = max(0, (20 - judgement) / 5)
        return if (!Ecosystem.enabled) base else (base * errorFactor(w)).roundToInt()
    }

    fun reportReliability(err: Int): Int = (100 - 15 * err).coerceAtLeast(25)

    // ---------------------------------------------------------------- match benefit

    fun sharpnessFactor(w: World, club: Club): Float = (dataLevel(w, club) - 50f) / 50f * SHARPNESS_SWING

    // ---------------------------------------------------------------- opposition report

    private fun groupName(g: Int): String = when (g) { 0 -> "goalkeeper"; 1 -> "defence"; 2 -> "midfield"; else -> "attack" }

    private fun groupAverage(w: World, c: Club, group: Int): Double {
        val xs = w.squad(c).filter { it.pos.group.coerceAtMost(3) == group && Lineups.available(it) }.sortedByDescending { it.ovr }
        val take = if (group == 0) 1 else if (group == 1) 4 else if (group == 2) 3 else 3
        val top = xs.take(take)
        return if (top.isEmpty()) 0.0 else top.sumOf { it.ovr }.toDouble() / top.size
    }

    private fun mentalityText(m: Int): String = when {
        m <= -2 -> "very defensive"
        m == -1 -> "cautious"
        m == 0 -> "balanced"
        m == 1 -> "positive"
        else -> "all-out attack"
    }

    /** The human club's view of [opp]. Deterministic for a given week: reloading never changes what the analysts say. */
    fun oppositionReport(w: World, opp: Club): OppositionReport {
        val me = w.human()
        val level = humanLevel(w)
        val rnd = Random(w.seed * 131L + w.year * 977L + w.week * 31L + opp.id)
        val groups = (0..3).map { it to groupAverage(w, opp, it) }
        var strong = groups.maxByOrNull { it.second }!!.first
        var weak = groups.minByOrNull { it.second }!!.first
        // A poorly informed club can mix the two up; the chance falls to zero at a data level of 60.
        val mixUp = max(0f, (60f - level) / 60f) * 0.5f
        if (strong != weak && rnd.nextFloat() < mixUp) { val t = strong; strong = weak; weak = t }
        val key = w.squad(opp).filter { Lineups.available(it) }.maxByOrNull { it.ovr }
        val err = ((100f - level) / 25f).roundToInt()
        val seen = if (key == null) 0 else key.ovr + (if (err > 0) rnd.nextInt(2 * err + 1) - err else 0)
        val formation = Formations.list.getOrNull(opp.tactics.formation)?.name ?: "unknown"
        val row = w.leagueOf(opp.id).table[opp.id]
        val form = row?.form?.toString()?.ifEmpty { "no games yet" } ?: "no games yet"
        val myStrength = Lineups.strength(w, me); val theirStrength = Lineups.strength(w, opp)
        val advice = when {
            level < 30f -> "Our data is too thin to give tactical advice."
            theirStrength > myStrength + 3f -> "They are stronger on paper: stay compact and counter-attack."
            theirStrength < myStrength - 3f -> "We should control this game; the main risk is complacency."
            else -> "An even game. Focus on their " + groupName(weak) + "."
        }
        return OppositionReport(opp, formation, mentalityText(opp.tactics.mentality), groupName(strong), groupName(weak),
            key?.name ?: "unknown", seen, form, level.roundToInt().coerceIn(0, 100), advice)
    }

    /** The opponent in the human club's next unplayed league fixture, or null (no fixture, or the season is over). */
    fun nextOpponent(w: World): Club? {
        val fx = w.humanFixture() ?: return null
        val id = if (fx.home == w.humanClubId) fx.away else fx.home
        return w.clubs.getOrNull(id)
    }

    // ---------------------------------------------------------------- recruitment shortlist from the data model

    /** The data model's rating of [p]: his true rating plus an error that shrinks as the data level rises. Stable within a season. */
    fun modelRating(w: World, p: Player): Int {
        val err = ((100f - humanLevel(w)) / 20f).roundToInt()
        val noise = if (err > 0) Random(w.seed * 17L + p.id * 31L + w.year).nextInt(2 * err + 1) - err else 0
        return p.ovr + noise
    }

    /** What the model says about a recommended or targeted player: 1 approves (first-team level), 0 neutral, -1 below the squad's level. */
    fun modelApproval(w: World, p: Player): Int {
        val mine = Lineups.strength(w, w.human())
        val m = modelRating(w, p)
        return if (m >= mine - 1) 1 else if (m >= mine - 6) 0 else -1
    }

    /** Weak spots: position groups where the club's second-best player of the group is poor compared with its other groups. */
    private fun needs(w: World, me: Club): Set<Int> {
        val avg = (0..3).map { g -> g to groupAverage(w, me, g) }
        val mean = avg.map { it.second }.average()
        return avg.filter { it.second < mean - 1.5 }.map { it.first }.toSet()
    }

    /**
     * Players the model recommends: young or fairly priced, in a position group where the squad is thin, not already at the club.
     * The model's rating carries the same error band as scouting reports, so a poor data level misleads.
     */
    fun dataTargets(w: World, limit: Int = 8): List<DataTarget> {
        val me = w.human()
        val needGroups = needs(w, me)
        val out = ArrayList<Pair<Double, DataTarget>>()
        val mine = Lineups.strength(w, me)
        for (p in w.players.values) {
            if (p.clubId == me.id || p.age > 31) continue
            val model = modelRating(w, p)
            val group = p.pos.group.coerceAtMost(3)
            val fillsNeed = group in needGroups
            val bargain = p.clubId < 0
            val prospect = p.age <= 22 && p.pa >= mine + 5
            if (model < mine - 1 && !prospect) continue
            val reason = when {
                prospect -> "High-potential youngster"
                fillsNeed && bargain -> "Free agent for a thin " + groupName(group)
                fillsNeed -> "Fills a thin " + groupName(group)
                bargain -> "Free agent, rated at first-team level"
                else -> continue
            }
            val score = model + (if (fillsNeed) 3.0 else 0.0) + (if (bargain) 2.0 else 0.0) + (if (prospect) (p.pa - model) * 0.3 else 0.0) - max(0, p.age - 27) * 0.7
            out.add(score to DataTarget(p, model, reason))
        }
        return out.sortedWith(compareByDescending<Pair<Double, DataTarget>> { it.first }.thenBy { it.second.player.id }).take(limit).map { it.second }
    }

    // ---------------------------------------------------------------- own-squad performance data

    /** Per-90 metrics from the season's recorded events. Only players with at least 180 minutes are meaningful. */
    class PerformanceRow(val player: Player, val xgPer90: Double, val keyPassesPer90: Double, val defensivePer90: Double, val rating: Float, val minutes: Int)

    fun performanceRows(w: World): List<PerformanceRow> =
        w.squad(w.human()).filter { it.minutes >= 180 }.map { p ->
            val n = p.minutes / 90.0
            PerformanceRow(p, p.seasonXg / n, p.seasonKeyPasses / n, (p.seasonTackles + p.seasonInterceptions) / n, p.avgRating(), p.minutes)
        }

    // ---------------------------------------------------------------- human actions

    fun setPlan(w: World, plan: DataPlan): Pair<Boolean, String> {
        val me = w.human()
        val s = state(w)
        if (planOf(w, me) == plan) return false to "You already subscribe to the ${plan.label.lowercase()} data package."
        val before = planOf(w, me)
        val priceDelta = price(me, plan) - price(me, before)
        if (priceDelta > 0 && me.balance < priceDelta * 8) return false to "You cannot afford ${money(priceDelta)} more per week for data: keep at least eight weeks of the extra cost in the bank."
        s.plan = plan.ordinal
        val net = netWeeklyCost(w, me)
        return true to "Data package changed to ${plan.label.lowercase()}. Net effect on your weekly budget: " + (if (net >= 0) "+" else "") + money(net) + "."
    }

    fun levelLabel(level: Float): String = when {
        level >= 80f -> "excellent"
        level >= 62f -> "good"
        level > 38f -> "normal for your level"
        level > 20f -> "weak"
        else -> "poor"
    }

    /** Drops scouting knowledge of players who left the game (retirements happen inside the season rollover). */
    fun prune(w: World) {
        val b = w.business ?: return
        b.knowledge.keys.removeAll { !w.players.containsKey(it) }
    }

    /** Called when the save is loaded: keeps the plan index valid. */
    fun repair(w: World) {
        val s = w.ecosystem?.analytics ?: return
        if (s.plan < -1 || s.plan >= DataPlan.values().size) s.plan = -1
    }
}

/** Sharpness from preparation: a well-informed club walks into matches slightly better drilled (and the reverse). */
object AnalyticsFactors : FactorProvider {
    override fun factors(w: World, club: Club, ctx: MatchContext): List<Factor> =
        listOf(Factor("Data and preparation", Channel.SHARPNESS, Analytics.sharpnessFactor(w, club)))
}
