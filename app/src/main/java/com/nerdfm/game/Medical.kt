package com.nerdfm.game

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** How hard the human club's medical team pushes a recovering player. Applies to injuries diagnosed from now on. */
enum class RehabPolicy(val label: String, val durationFactor: Float, val relapseFactor: Float, val blurb: String) {
    CAUTIOUS("Cautious", 1.15f, 0.5f, "Players stay out about 15% longer and relapse half as often."),
    STANDARD("Standard", 1.0f, 1.0f, "Normal timetable and normal relapse risk."),
    ACCELERATED("Accelerated", 0.85f, 1.6f, "Players return about 15% sooner but relapse 60% more often.")
}

/** One player's workload and injury history. Loads are in "match-equivalents x 100": a full match is 100, plus training. */
class PlayerMedical : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    /** Fast-moving load (about a week and a half). */
    var acute = 0f
    /** Slow-moving load (about ten weeks). */
    var chronic = 0f
    var seeded = false
    /** Injuries recorded since the career began (new, training and relapse). */
    var injuries = 0
    var relapses = 0
    /** p.injWeeks as seen at the previous weekly check; used to detect new injuries and returns. */
    var lastInjWeeks = 0
    /** Length of the current (or last) injury after diagnosis. */
    var rehabTotal = 0
    var lastType = ""
    /** Weeks since the player came back from injury; -1 outside the relapse window. */
    var sinceReturn = -1
}

/** A line of the human club's medical log. */
class InjuryRecord(val playerId: Int, val year: Int, val week: Int, val type: String, val weeks: Int, val relapse: Boolean) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

class MedicalState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    val players = HashMap<Int, PlayerMedical>()
    var policy = RehabPolicy.STANDARD
    /** Newest first, human club only, capped at [MedicalRules.LOG_LIMIT]. */
    val log = ArrayList<InjuryRecord>()
    /** club id -> injuries diagnosed this season / weeks of absence those injuries cost. */
    val seasonInjuries = HashMap<Int, Int>()
    val seasonWeeksLost = HashMap<Int, Int>()
    /** Players whose training intensity was lowered by "load advice" (so it can be put back). */
    val advised = HashSet<Int>()
    /** Career totals across every club: injuries the medical layer itself created (diagnostics for tuning and tests). */
    var totalRelapses = 0
    var totalOveruse = 0
}

/** One row of the human club's workload table. [risk]: -1 unmonitored, 0 low, 1 moderate, 2 high. */
class RiskRow(val player: Player, val acwr: Float, val risk: Int, val note: String, val injuries: Int, val phase: Int)

/**
 * Medical and sports-science layer (factor group C). Everything here acts on state the game already has:
 * injury length after diagnosis, relapses and overuse injuries (new injury sources of the same kind as the training
 * injury in Game.completeWeek), and a small per-player match-day condition penalty for overloaded players through the
 * capped modifier machinery. MatchEngine itself is never touched.
 */
object MedicalRules {
    const val LOG_LIMIT = 60
    const val RELAPSE_WINDOW = 6
    /** A relapse lasts this much longer than the injury it follows (the UEFA study reports longer absences for re-injuries). */
    const val RELAPSE_LENGTH = 1.33f
    const val OVERLOAD_RATIO = 1.3f
    /** Weekly relapse risk for a player who played, in the first three weeks back and in the later ones. Calibrated so that
     *  the league-wide injury prevalence stays inside the golden band (see GoldenBaselineTest). */
    const val RELAPSE_BASE_EARLY = 0.014f
    const val RELAPSE_BASE_LATE = 0.006f
    const val OVERUSE_BASE = 0.006f
    const val OVERUSE_CAP = 0.02f
    /** Largest per-player match-day condition penalty from overload (also clamped by the global condition caps). */
    const val OVERLOAD_PENALTY_CAP = 2.0f

    fun state(w: World): MedicalState {
        val eco = w.eco()
        return eco.medical ?: MedicalState().also { eco.medical = it }
    }

    private fun rng(w: World, salt: Int): Random = w.rng(9800 + salt)

    // ---------------------------------------------------------------- staff quality (about -1..+1, 0 = normal for the club's tier)

    /** Doctor and physios: faster, safer diagnosis and rehab. */
    fun diagnosisQuality(w: World, clubId: Int): Float =
        (0.6f * Staffing.centered(w, clubId, StaffRole.DOCTOR) + 0.4f * Staffing.centered(w, clubId, StaffRole.PHYSIO) + ClubEnvRules.facilityBonus(w, clubId, "MEDICAL", 0.1f)).coerceIn(-1f, 1f)

    /** Sports scientist and fitness coach: how well workloads are tracked and managed. */
    fun monitoringQuality(w: World, clubId: Int): Float =
        (0.6f * Staffing.centered(w, clubId, StaffRole.SPORTS_SCIENTIST) + 0.4f * Staffing.centered(w, clubId, StaffRole.COACH_FITNESS)).coerceIn(-1f, 1f)

    fun isMonitored(w: World, clubId: Int): Boolean =
        Staffing.inRole(w, clubId, StaffRole.SPORTS_SCIENTIST).isNotEmpty() || Staffing.inRole(w, clubId, StaffRole.COACH_FITNESS).isNotEmpty()

    // ---------------------------------------------------------------- load model

    fun acwr(pm: PlayerMedical): Float = if (!pm.seeded) 1f else pm.acute / max(25f, pm.chronic)

    /** Weekly load of one player: the minutes he played plus his training intensity. */
    fun weeklyLoad(p: Player): Float = p.lastMinutes / 90f * 100f + when (p.trainingIntensity) { 0 -> 0f; 2 -> 35f; else -> 15f }

    private fun updateLoad(w: World, p: Player, pm: PlayerMedical) {
        val load = weeklyLoad(p) + FixtureCalendar.midweekMinutes(w, p.id) / 90f * 100f
        if (!pm.seeded) { pm.acute = load; pm.chronic = load; pm.seeded = true; return }
        pm.acute = 0.5f * pm.acute + 0.5f * load
        pm.chronic = 0.9f * pm.chronic + 0.1f * load
    }

    /** 0 healthy, 1 treatment, 2 rehabilitation, 3 return to play. */
    fun phase(p: Player, pm: PlayerMedical?): Int {
        if (p.injWeeks <= 0) return 0
        val total = max(1, pm?.rehabTotal ?: p.injWeeks)
        val done = 1f - min(p.injWeeks, total).toFloat() / total
        return if (done < 0.4f) 1 else if (done < 0.8f) 2 else 3
    }

    // ---------------------------------------------------------------- weekly cycle

    /** Called from Ecosystem.preWeek, after the week's matches and before Game.completeWeek resets minutes. */
    fun weekly(w: World) {
        val s = state(w)
        prune(w)
        val rnd = rng(w, 1)
        for (c in w.clubs) {
            val diag = diagnosisQuality(w, c.id)
            val monitor = monitoringQuality(w, c.id)
            val policy = if (c.human) s.policy else RehabPolicy.STANDARD
            for (p in w.squad(c)) {
                val pm = s.players.getOrPut(p.id) { PlayerMedical() }
                updateLoad(w, p, pm)
                if (p.injWeeks > 0 && pm.lastInjWeeks == 0) {
                    diagnose(w, s, c, p, pm, diag, policy, rnd, relapse = false)
                } else if (p.injWeeks > 0) {
                    pm.lastInjWeeks = p.injWeeks
                } else {
                    if (pm.lastInjWeeks > 0) { pm.lastInjWeeks = 0; pm.sinceReturn = 0 }
                    else if (pm.sinceReturn >= 0) pm.sinceReturn = if (pm.sinceReturn + 1 > RELAPSE_WINDOW) -1 else pm.sinceReturn + 1
                    if (p.lastMinutes > 0) rollRisks(w, s, c, p, pm, diag, monitor, policy, rnd)
                }
            }
        }
    }

    /** Drops records of players who left the game (retirements happen inside the season rollover). */
    fun prune(w: World) {
        val s = w.ecosystem?.medical ?: return
        s.players.keys.removeAll { !w.players.containsKey(it) }
        s.advised.removeAll { !w.players.containsKey(it) }
    }

    /** Applies the doctor's and the rehab policy's effect to a freshly detected injury and records it. */
    private fun diagnose(w: World, s: MedicalState, c: Club, p: Player, pm: PlayerMedical, diag: Float, policy: RehabPolicy, rnd: Random, relapse: Boolean) {
        val factor = (1f - 0.18f * diag) * policy.durationFactor
        val target = p.injWeeks * factor
        val weeks = (target + rnd.nextFloat()).toInt().coerceIn(1, 30)
        p.injWeeks = weeks
        pm.lastInjWeeks = weeks
        pm.rehabTotal = weeks
        pm.lastType = p.injType
        pm.sinceReturn = -1
        pm.injuries++
        if (relapse) { pm.relapses++; s.totalRelapses++ }
        s.seasonInjuries[c.id] = (s.seasonInjuries[c.id] ?: 0) + 1
        s.seasonWeeksLost[c.id] = (s.seasonWeeksLost[c.id] ?: 0) + weeks
        if (c.human) {
            s.log.add(0, InjuryRecord(p.id, w.year, w.week, p.injType, weeks, relapse))
            while (s.log.size > LOG_LIMIT) s.log.removeAt(s.log.size - 1)
        }
    }

    private fun rollRisks(w: World, s: MedicalState, c: Club, p: Player, pm: PlayerMedical, diag: Float, monitor: Float, policy: RehabPolicy, rnd: Random) {
        val prone = 0.6f + p.injuryProne / 20f
        // 1. Relapse in the weeks after coming back.
        if (pm.sinceReturn in 0..RELAPSE_WINDOW && pm.rehabTotal > 0) {
            val base = if (pm.sinceReturn <= 2) RELAPSE_BASE_EARLY else RELAPSE_BASE_LATE
            val risk = base * policy.relapseFactor * (1f - 0.35f * diag) * (1f + 0.12f * min(pm.injuries, 5)) * prone * (1f + 0.5f * max(0f, acwr(pm) - 1.2f))
            if (rnd.nextFloat() < risk) {
                p.injType = pm.lastType.ifEmpty { "muscle strain" }
                p.injWeeks = max(1, (pm.rehabTotal * RELAPSE_LENGTH).roundToInt())
                diagnose(w, s, c, p, pm, diag, policy, rnd, relapse = true)
                if (c.human) w.addNews("Injury setback", "${p.name} has suffered a relapse (${p.injType}) and will miss ${p.injWeeks} week(s).")
                return
            }
        }
        // 2. Overuse: a player thrown into a heavy load far above what he is used to.
        if (p.lastMinutes >= 45) {
            val over = max(0f, acwr(pm) - 1.35f)
            if (over > 0f) {
                val risk = min(OVERUSE_CAP, OVERUSE_BASE * over / 0.3f) * (1f - 0.4f * monitor) * prone
                if (rnd.nextFloat() < risk) {
                    p.injType = "overuse strain"
                    s.totalOveruse++
                    p.injWeeks = 1 + rnd.nextInt(3)
                    diagnose(w, s, c, p, pm, diag, policy, rnd, relapse = false)
                    if (c.human) w.addNews("Overuse injury", "${p.name} broke down after a sudden jump in workload (${p.injWeeks} week(s) out).")
                }
            }
        }
    }

    /** Medical staff make the intensive-training injury of Game.completeWeek rarer (or commoner). 1.0 when disabled. */
    fun trainingInjuryMultiplier(w: World, clubId: Int): Float {
        if (!Ecosystem.enabled) return 1f
        return (1f - 0.25f * monitoringQuality(w, clubId)).coerceIn(0.75f, 1.25f)
    }

    /** Match-day condition penalty for one overloaded player. Pure: reads state, never creates it. */
    fun overloadPenalty(w: World, club: Club, p: Player): Float {
        val pm = w.ecosystem?.medical?.players?.get(p.id) ?: return 0f
        val over = acwr(pm) - OVERLOAD_RATIO
        if (over <= 0f) return 0f
        val relief = 1f - 0.4f * monitoringQuality(w, club.id)
        return -min(OVERLOAD_PENALTY_CAP, over * 2.5f) * relief
    }

    /** Load-time repair: a damaged workload record is reset rather than allowed to poison risk maths. Absent sections stay absent. */
    fun repair(w: World) {
        val s = w.ecosystem?.medical ?: return
        for (pm in s.players.values) {
            if (!pm.acute.isFinite() || !pm.chronic.isFinite() || pm.acute < 0f || pm.chronic < 0f) { pm.acute = 0f; pm.chronic = 0f; pm.seeded = false }
            if (pm.sinceReturn < -1 || pm.sinceReturn > RELAPSE_WINDOW) pm.sinceReturn = -1
            if (pm.lastInjWeeks < 0) pm.lastInjWeeks = 0
        }
        s.log.removeAll { !w.players.containsKey(it.playerId) }
    }

    fun seasonReview(w: World) {
        val s = state(w)
        s.seasonInjuries.clear()
        s.seasonWeeksLost.clear()
        for (pm in s.players.values) pm.sinceReturn = if (pm.sinceReturn >= 0) min(pm.sinceReturn, RELAPSE_WINDOW) else -1
    }

    // ---------------------------------------------------------------- human actions and reports

    fun setPolicy(w: World, policy: RehabPolicy): Pair<Boolean, String> {
        val s = state(w)
        if (s.policy == policy) return false to "Rehab policy is already ${policy.label.lowercase()}."
        s.policy = policy
        return true to "Rehab policy set to ${policy.label.lowercase()}. ${policy.blurb} It applies to injuries diagnosed from now on."
    }

    fun riskLevel(w: World, p: Player, pm: PlayerMedical?): Pair<Int, String> {
        if (p.injWeeks > 0) return 0 to "Injured"
        if (pm == null) return 0 to "No data yet"
        val a = acwr(pm)
        if (a > 1.6f) return 2 to "Workload far above his normal"
        if (pm.sinceReturn in 0..2) return 2 to "Just back from injury"
        if (a > OVERLOAD_RATIO) return 1 to "Workload above his normal"
        if (pm.sinceReturn in 3..RELAPSE_WINDOW) return 1 to "Recently injured"
        if (pm.injuries >= 3) return 1 to "Long injury record"
        return 0 to "Fine"
    }

    /** Workload table of the human squad, highest risk first. Without a sports scientist or fitness coach nothing is measured. */
    fun riskRows(w: World): List<RiskRow> {
        val me = w.human()
        val s = state(w)
        val monitored = isMonitored(w, me.id)
        return w.squad(me).map { p ->
            val pm = s.players[p.id]
            val (level, note) = riskLevel(w, p, pm)
            if (!monitored && p.injWeeks == 0) RiskRow(p, 1f, -1, "Not monitored: hire a sports scientist or fitness coach", pm?.injuries ?: 0, 0)
            else RiskRow(p, if (pm == null) 1f else acwr(pm), level, note, pm?.injuries ?: 0, phase(p, pm))
        }.sortedWith(compareByDescending<RiskRow> { it.risk }.thenByDescending { it.player.injWeeks }.thenBy { it.player.name })
    }

    /** Puts every high- and moderate-risk fit player on light training. Needs workload monitoring. */
    fun applyLoadAdvice(w: World): Pair<Boolean, String> {
        val me = w.human()
        if (!isMonitored(w, me.id)) return false to "You need a sports scientist or a fitness coach to monitor workloads. Hire one in the Organisation tab."
        val s = state(w)
        var n = 0
        for (r in riskRows(w)) {
            val p = r.player
            if (p.injWeeks == 0 && r.risk >= 1 && p.trainingIntensity != 0) {
                p.trainingIntensity = 0
                s.advised.add(p.id)
                n++
            }
        }
        return if (n == 0) false to "Nobody needs lighter training right now."
        else true to "$n player(s) moved to light training until their workload settles."
    }

    /** Puts players back on normal training once they are no longer flagged. */
    fun releaseLoadAdvice(w: World): Pair<Boolean, String> {
        val s = state(w)
        val flagged = riskRows(w).filter { it.risk >= 1 }.map { it.player.id }.toSet()
        var n = 0
        for (id in s.advised.toList()) {
            val p = w.players[id]
            if (p != null && p.clubId == w.humanClubId && id !in flagged && p.trainingIntensity == 0) { p.trainingIntensity = 1; n++ }
            if (p == null || id !in flagged) s.advised.remove(id)
        }
        return if (n == 0) false to "Nobody is waiting to be moved back to normal training."
        else true to "$n player(s) are back on normal training."
    }

    class Summary(val injuredNow: Int, val seasonInjuries: Int, val seasonWeeksLost: Int, val relapses: Int)

    fun summary(w: World, clubId: Int): Summary {
        val s = state(w)
        val squad = w.squad(w.clubs[clubId])
        return Summary(squad.count { it.injWeeks > 0 }, s.seasonInjuries[clubId] ?: 0, s.seasonWeeksLost[clubId] ?: 0,
            squad.sumOf { s.players[it.id]?.relapses ?: 0 })
    }

    fun label(q: Float): String = when {
        q >= 0.6f -> "excellent"
        q >= 0.2f -> "good"
        q > -0.2f -> "normal for your level"
        q > -0.6f -> "weak"
        else -> "poor"
    }
}

/** Per-player match-day penalty for players whose workload jumped far above what they are used to. */
object MedicalLoadFactors : PlayerFactorProvider {
    override fun conditionDelta(w: World, club: Club, p: Player, ctx: MatchContext): Float = MedicalRules.overloadPenalty(w, club, p)
}
