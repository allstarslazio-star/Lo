package com.nerdfm.game

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

fun IntArray.at(a: Attr): Int = this[a.ordinal]

class MatchEvent(val label: String, val minute: Int, val team: Int, val kind: Int, val text: String)
// kinds: 0 info, 1 goal, 2 yellow, 3 red, 4 sub, 5 injury, 6 chance, 7 save, 8 period

// ====================================================================================================
//  Ability vectors: how each match skill is derived from the 36 attributes (result is on a 0..100 scale)
// ====================================================================================================

private class AbVec(vararg p: Pair<Attr, Float>) {
    private val w = FloatArray(NATTR)
    private var tot = 0f

    init {
        for (x in p) { w[x.first.ordinal] = x.second; tot += x.second }
    }

    fun of(a: IntArray): Float {
        var s = 0f
        for (i in 0 until NATTR) {
            val wi = w[i]
            if (wi != 0f) s += wi * a[i]
        }
        return s / tot * 5f
    }
}

private object V {
    val build = AbVec(Attr.Passing to 3f, Attr.Vision to 2f, Attr.FirstTouch to 2f, Attr.Composure to 1.5f,
        Attr.Decisions to 1.5f, Attr.Technique to 1.5f, Attr.Teamwork to 0.5f)
    val carry = AbVec(Attr.Dribbling to 3f, Attr.Pace to 2f, Attr.Acceleration to 2f, Attr.Agility to 1f,
        Attr.Balance to 1f, Attr.Strength to 0.7f, Attr.Flair to 0.8f, Attr.FirstTouch to 1f)
    val create = AbVec(Attr.Vision to 3f, Attr.Passing to 2.5f, Attr.Technique to 2f, Attr.Flair to 1.2f,
        Attr.Decisions to 1f, Attr.FirstTouch to 0.8f)
    val cross = AbVec(Attr.Crossing to 4f, Attr.Technique to 1.5f, Attr.Passing to 1f, Attr.Vision to 0.7f)
    val finish = AbVec(Attr.Finishing to 4f, Attr.Composure to 2f, Attr.Technique to 1.5f, Attr.FirstTouch to 0.8f)
    val move = AbVec(Attr.OffTheBall to 3f, Attr.Anticipation to 1.5f, Attr.Acceleration to 1f, Attr.Pace to 1f,
        Attr.Decisions to 0.5f)
    val longShot = AbVec(Attr.LongShots to 4f, Attr.Technique to 1.5f, Attr.Finishing to 1f, Attr.Composure to 1f)
    val head = AbVec(Attr.Heading to 4f, Attr.Jumping to 2f, Attr.Strength to 1.5f, Attr.Anticipation to 0.5f)
    val defend = AbVec(Attr.Tackling to 2.5f, Attr.Marking to 2.5f, Attr.Positioning to 2.5f, Attr.Anticipation to 2f,
        Attr.Strength to 1f, Attr.Pace to 1f, Attr.Decisions to 1f, Attr.Heading to 0.6f, Attr.Determination to 0.5f)
    val press = AbVec(Attr.WorkRate to 2.5f, Attr.Aggression to 1.5f, Attr.Anticipation to 1.5f, Attr.Teamwork to 1.5f,
        Attr.Stamina to 1f, Attr.Acceleration to 1f, Attr.Tackling to 1f)
    val setp = AbVec(Attr.SetPieces to 4f, Attr.Technique to 1.5f, Attr.Crossing to 1f, Attr.Vision to 0.5f)
    val stop = AbVec(Attr.Reflexes to 4f, Attr.Handling to 3f, Attr.Agility to 1.5f, Attr.Positioning to 1.5f,
        Attr.Anticipation to 0.5f)
    val gk1 = AbVec(Attr.OneOnOnes to 3f, Attr.Reflexes to 1f, Attr.Agility to 1f, Attr.Positioning to 1f,
        Attr.Anticipation to 1f, Attr.Composure to 0.5f)
    val cmd = AbVec(Attr.Command to 3f, Attr.Handling to 1f, Attr.Jumping to 1f, Attr.Decisions to 1f,
        Attr.Positioning to 1f)
}

// ====================================================================================================
//  Positional weights.  Index order is Pos.ordinal:  GK LB CB RB DM CM LW RW AM ST
// ====================================================================================================

private object W {
    val BUILD = floatArrayOf(0.25f, 0.50f, 0.60f, 0.50f, 1.00f, 1.00f, 0.35f, 0.35f, 0.80f, 0.25f)
    val MIDDEF = floatArrayOf(0.02f, 0.20f, 0.20f, 0.20f, 1.00f, 0.90f, 0.30f, 0.30f, 0.35f, 0.15f)
    val PRESS = floatArrayOf(0.00f, 0.30f, 0.25f, 0.30f, 0.80f, 0.80f, 0.70f, 0.70f, 0.80f, 0.90f)
    val AERA = floatArrayOf(0.00f, 0.10f, 0.30f, 0.10f, 0.10f, 0.30f, 0.20f, 0.20f, 0.30f, 1.20f)
    val AERD = floatArrayOf(0.10f, 0.15f, 1.00f, 0.15f, 0.35f, 0.30f, 0.05f, 0.05f, 0.05f, 0.15f)

    // lane weights: L, C, R from the team's own point of view
    val ATK = arrayOf(
        floatArrayOf(0.00f, 0.00f, 0.00f), // GK
        floatArrayOf(0.30f, 0.02f, 0.00f), // LB
        floatArrayOf(0.00f, 0.03f, 0.00f), // CB
        floatArrayOf(0.00f, 0.02f, 0.30f), // RB
        floatArrayOf(0.02f, 0.08f, 0.02f), // DM
        floatArrayOf(0.10f, 0.30f, 0.10f), // CM
        floatArrayOf(0.85f, 0.20f, 0.00f), // LW
        floatArrayOf(0.00f, 0.20f, 0.85f), // RW
        floatArrayOf(0.20f, 0.85f, 0.20f), // AM
        floatArrayOf(0.10f, 0.90f, 0.10f)  // ST
    )
    val DEF = arrayOf(
        floatArrayOf(0.05f, 0.15f, 0.05f), // GK
        floatArrayOf(0.85f, 0.10f, 0.00f), // LB
        floatArrayOf(0.15f, 0.80f, 0.15f), // CB
        floatArrayOf(0.00f, 0.10f, 0.85f), // RB
        floatArrayOf(0.15f, 0.45f, 0.15f), // DM
        floatArrayOf(0.20f, 0.35f, 0.20f), // CM
        floatArrayOf(0.25f, 0.00f, 0.00f), // LW
        floatArrayOf(0.00f, 0.00f, 0.25f), // RW
        floatArrayOf(0.03f, 0.05f, 0.03f), // AM
        floatArrayOf(0.00f, 0.03f, 0.00f)  // ST
    )

    val ATK_REF = FloatArray(3)
    val DEF_REF = FloatArray(3)
    var BUILD_REF = 1f
    var MIDDEF_REF = 1f
    var PRESS_REF = 1f
    var AERA_REF = 1f
    var AERD_REF = 1f

    init {
        val f = Formations.list[0]
        var b = 0f; var m = 0f; var p = 0f; var aa = 0f; var ad = 0f
        for (s in f.slots) {
            val i = s.ordinal
            b += BUILD[i]; m += MIDDEF[i]; p += PRESS[i]; aa += AERA[i]; ad += AERD[i]
            for (c in 0..2) { ATK_REF[c] += ATK[i][c]; DEF_REF[c] += DEF[i][c] }
        }
        BUILD_REF = b; MIDDEF_REF = m; PRESS_REF = p; AERA_REF = aa; AERD_REF = ad
    }
}

// ====================================================================================================
//  Player state in a match
// ====================================================================================================

class PS(val p: Player, var slot: Pos, val day: Float) {
    var cond = p.condition
    var rating = 6.0f
    var goals = 0
    var assists = 0
    var saves = 0
    var shots = 0
    var yc = 0
    var sentOff = false
    var mins = 0
    var injured = false
    var played = false
    var injWeeks = 0

    // detailed match stats
    var sot = 0
    var xg = 0.0
    var xa = 0.0
    var keyPasses = 0
    var passes = 0
    var passesOk = 0
    var tackles = 0
    var interceptions = 0
    var blocks = 0
    var dribbles = 0
    var dribblesWon = 0
    var fouls = 0
    var fouled = 0
    var offsides = 0
    var xgPrevented = 0.0
    var enteredAt = 0

    // live multiplier (condition, morale, form, day form)
    var ef = 1f

    // match abilities, 0..100
    var bld = 0f
    var car = 0f
    var crt = 0f
    var crs = 0f
    var fin = 0f
    var mov = 0f
    var lsh = 0f
    var hed = 0f
    var dfn = 0f
    var prs = 0f
    var stp = 0f
    var gks = 0f
    var gk1 = 0f
    var atkAb = 0f
    var dfnAb = 0f

    fun recompute() {
        val a = p.attrs
        val fam = Roles.familiarity(p, slot)
        val fm = fam.pow(0.75f)
        bld = V.build.of(a) * fm
        car = V.carry.of(a) * fm
        crt = V.create.of(a) * fm
        crs = V.cross.of(a) * fm
        fin = V.finish.of(a) * fm
        mov = V.move.of(a) * fm
        lsh = V.longShot.of(a) * fm
        hed = V.head.of(a) * fm
        dfn = V.defend.of(a) * fm
        prs = V.press.of(a) * fm
        stp = V.setp.of(a) * fm
        val g = if (slot == Pos.GK) fam else 0.25f
        gks = V.stop.of(a) * g
        gk1 = V.gk1.of(a) * g
        atkAb = 0.30f * crt + 0.28f * car + 0.17f * crs + 0.25f * (0.5f * fin + 0.5f * mov)
        dfnAb = 0.70f * dfn + 0.30f * prs
    }
}

class MatchTeam(val club: Club, val tactics: Tactics, val xi: List<PS>, benchList: List<PS>, val ai: Boolean, val calibration: MatchCalibration = MatchCalibration()) {
    val onPitch = ArrayList<PS>(xi)
    val bench = ArrayList<PS>(benchList)
    val everyone = ArrayList<PS>()
    var goals = 0
    var shots = 0
    var sot = 0
    var xg = 0.0
    var corners = 0
    var fouls = 0
    var yellows = 0
    var reds = 0
    var subs = 0
    var offsides = 0
    var passes = 0
    var passesOk = 0
    var blocked = 0
    var bigChances = 0
    var tackles = 0
    var woodwork = 0
    var penalties = 0

    // team aggregates, refreshed every minute
    var ctrl = 60f
    var midDef = 60f
    var pressP = 60f
    var aerA = 60f
    var aerD = 60f
    var pace = 60f
    var gkStop = 40f
    var gk1 = 40f
    var gkCmd = 40f
    var avgCond = 100f
    val atk = FloatArray(3) { 60f }
    val dfn = FloatArray(3) { 60f }
    var gkPS: PS? = null

    // effective tactics this minute (AI adapts these; the human's own tactics are never overwritten)
    var eMent = 0
    var ePress = 1
    var eTempo = 1
    var eLine = 1
    var eWidth = 1
    var ePass = 1

    var mom = 0f
    var nextSub = 60

    init {
        everyone.addAll(xi)
        everyone.addAll(benchList)
        // Start from the manager's saved settings; AI changes are transient match state.
        eMent = tactics.mentality
        ePress = tactics.pressing
        eTempo = tactics.tempo
        eLine = tactics.line
        eWidth = tactics.width
        ePass = tactics.passing
        for (ps in xi) ps.played = true
    }

    fun formation(): Formation = Formations.list[tactics.formation]
}

object MatchTeams {
    fun build(w: World, c: Club, opp: Club?, home: Boolean, rnd: Random): MatchTeam {
        if (c.human) Lineups.fixHuman(w, c) else Lineups.aiTactics(w, c, opp, home)
        val f = Formations.list[c.tactics.formation]
        val xi = ArrayList<PS>()
        for (i in 0 until 11) {
            val p = w.players[c.lineup[i]] ?: continue
            xi.add(PS(p, f.slots[i], dayForm(p, rnd)))
        }
        val bench = ArrayList<PS>()
        for (i in 0 until 7) {
            val p = w.players[c.bench[i]] ?: continue
            bench.add(PS(p, p.pos, dayForm(p, rnd)))
        }
        val t = Tactics()
        t.copyFrom(c.tactics)
        return MatchTeam(c, t, xi, bench, !c.human, w.matchCalibration)
    }

    private fun dayForm(p: Player, rnd: Random): Float {
        val sd = 0.035f * (1.15f - p.consistency / 20f * 0.7f)
        return 1f + rnd.gauss().toFloat() * sd
    }
}

// ====================================================================================================
//  Tunable constants
// ====================================================================================================

private object K {
    // logit offsets / slopes of the three phase transitions
    var A0 = 1.75f; var B0 = 1.10f      // build-up (own third -> midfield)
    var A1 = 0.55f; var B1 = 1.20f      // midfield (-> final third)
    var A2 = -0.55f; var B2 = 1.30f     // final third (-> shot)
    var HOME = 0.035f                    // home edge on control / press
    var GOAL = 1.07f                     // shot -> goal conversion (Phase 11 retune with TEMPO: ~26 shots/match, ~10% conversion)
    var XG = 1.00f                       // global chance-quality scale
    var FOUL = 1.00f                     // global foul scale
    var PEN = 0.060f                     // chance a foul in the final third is a penalty
    var CORNER = 0.11f                   // chance a failed final-third move ends in a corner
    var OFFSIDE = 0.085f
    var LONG = 0.02f                     // chance of a speculative long shot (Phase 4: was 0.055)
    var TEMPO = 3.3f                     // Phase 11: shots/fouls scale ~1/TEMPO; 3.3 gives ~26 total shots per match (was 1.9 = ~45)
    var YEL = 0.30f                      // base yellow-card chance per foul (was 0.145)
    var RED = 0.0019f                    // base straight-red chance per foul (was 0.0026)
    var REPEAT = 0.25f                   // players already booked foul this fraction as often (was 0.75)
    const val MAX_TOTAL_GOALS = 8        // guardrail: once 8 goals are in, nobody scores again (garbage-time realism)
}

// shot kinds
private const val S_THR = 0     // through ball
private const val S_HEAD = 1    // header from a cross
private const val S_FOOT = 2    // volley / first-time shot from a cross
private const val S_CUT = 3     // cut-back
private const val S_DRI = 4     // dribble and shoot
private const val S_1V1 = 5     // clean through
private const val S_LONG = 6    // long range
private const val S_FK = 7      // direct free kick
private const val S_PEN = 8     // penalty
private const val S_COR = 9     // header from a corner / set-piece delivery
private const val S_REB = 10    // rebound

class MatchEngine(val home: MatchTeam, val away: MatchTeam, val rnd: Random, private val calibration: MatchCalibration = home.calibration) {
    val events = ArrayList<MatchEvent>()
    var half = 1
    var t = 0
    var atHalfTime = false
    var finished = false
    var minutesPlayed = 0
    private val teams = arrayOf(home, away)
    private val possSec = DoubleArray(2)
    private var poss = 0
    private var zone = 1
    private var trans = false
    private var budget = 0f
    private var stopSecs = 0f
    private var firstKick = 0
    private val refStrict = exp(0.22f * rnd.gauss().toFloat())
    private var announcedStop = false

    init {
        for (tm in teams) for (ps in tm.everyone) ps.recompute()
        for (tm in teams) {
            tm.nextSub = 55 + rnd.nextInt(14)
        }
        firstKick = if (rnd.nextBoolean()) 0 else 1
        poss = firstKick
        stopSecs = 25f + rnd.nextInt(50)
        refresh()
        add(-1, 0, "Kick-off: ${home.club.name} v ${away.club.name}.")
    }

    fun minuteNum(): Int = (if (half == 1) 0 else 45) + min(t, 45)

    fun label(): String {
        val base = if (half == 1) 0 else 45
        return if (t > 45) "${base + 45}+${t - 45}'" else "${base + t}'"
    }

    private fun add(team: Int, kind: Int, text: String) {
        events.add(MatchEvent(label(), minuteNum(), team, kind, text))
    }

    fun possessionHome(): Int {
        val s = possSec[0] + possSec[1]
        return if (s <= 0.0) 50 else (possSec[0] / s * 100.0).toInt()
    }

    // ------------------------------------------------------------------ control

    private fun limit(): Int = 45 + clampI(ceil(stopSecs / 60f).toInt(), 1, 7)

    fun step(): Boolean {
        if (finished) return false
        if (atHalfTime) return true
        t++
        val limit = limit()
        if (t > limit) {
            if (half == 1) {
                atHalfTime = true
                add(-1, 8, "Half-time: ${home.club.name} ${home.goals}-${away.goals} ${away.club.name}.")
                add(-1, 0, statLine())
                return true
            } else {
                finished = true
                add(-1, 8, "Full-time: ${home.club.name} ${home.goals}-${away.goals} ${away.club.name}.")
                add(-1, 0, statLine())
                return false
            }
        }
        if (t == 46 && !announcedStop) {
            announcedStop = true
            add(-1, 8, "${limit - 45} minute${if (limit - 45 == 1) "" else "s"} of added time.")
        }
        playMinute()
        return true
    }

    fun resumeSecondHalf() {
        if (!atHalfTime) return
        atHalfTime = false
        half = 2
        t = 0
        stopSecs = 20f + rnd.nextInt(40)
        announcedStop = false
        poss = 1 - firstKick
        zone = 1
        trans = false
        budget = 0f
        add(-1, 8, "The second half is underway.")
    }

    fun simToEnd() {
        var guard = 0
        while (!finished && guard < 400) {
            if (atHalfTime) resumeSecondHalf()
            step()
            guard++
        }
    }

    private fun statLine(): String =
        "Possession ${possessionHome()}-${100 - possessionHome()}, shots ${home.shots}-${away.shots} " +
            "(on target ${home.sot}-${away.sot}), xG ${"%.1f".format(home.xg)}-${"%.1f".format(away.xg)}."

    // ------------------------------------------------------------------ strengths

    private fun refresh() {
        for (i in 0..1) {
            val tm = teams[i]
            val opp = teams[1 - i]
            var bs = 0f; var ms = 0f; var pr = 0f; var aa = 0f; var ad = 0f
            var pace = 0f; var paceW = 0f
            var cond = 0f; var n = 0
            val atk = floatArrayOf(0f, 0f, 0f)
            val dfn = floatArrayOf(0f, 0f, 0f)
            var gk: PS? = null
            for (ps in tm.onPitch) {
                val cf = 0.62f + 0.38f * max(0f, ps.cond) / 100f
                val mf = 0.95f + 0.10f * ps.p.morale / 100f
                val ff = 0.97f + 0.06f * ps.p.form / 100f
                val sharpnessFactor = 0.88f + 0.12f * (ps.p.sharpness / 100f)
                var e = cf * mf * ff * sharpnessFactor * ps.day
                if (ps.injured) e *= 0.7f
                ps.ef = e
                val s = ps.slot.ordinal
                bs += W.BUILD[s] * ps.bld * e
                ms += W.MIDDEF[s] * (0.5f * ps.dfn + 0.5f * ps.prs) * e
                pr += W.PRESS[s] * ps.prs * e
                aa += W.AERA[s] * ps.hed * e
                ad += W.AERD[s] * (0.6f * ps.hed + 0.4f * ps.dfn) * e
                for (c in 0..2) {
                    atk[c] += W.ATK[s][c] * ps.atkAb * e
                    dfn[c] += W.DEF[s][c] * ps.dfnAb * e
                }
                if (ps.slot.group >= 3) { pace += ps.car * e; paceW += 1f }
                if (ps.slot == Pos.GK) gk = ps
                if (ps.slot != Pos.GK) { cond += ps.cond; n++ }
            }
            val home = if (i == 0) K.HOME else 0f
            tm.ctrl = bs / W.BUILD_REF * (1f + home + 0.05f * tm.mom)
            tm.midDef = ms / W.MIDDEF_REF * (1f + 0.6f * home)
            tm.pressP = pr / W.PRESS_REF * (1f + 0.6f * home + 0.04f * tm.mom)
            tm.aerA = aa / W.AERA_REF
            tm.aerD = ad / W.AERD_REF
            tm.pace = if (paceW > 0f) pace / paceW else 50f
            for (c in 0..2) {
                tm.atk[c] = atk[c] / W.ATK_REF[c]
                tm.dfn[c] = dfn[c] / W.DEF_REF[c]
            }
            tm.gkPS = gk
            if (gk != null) {
                tm.gkStop = gk.gks * gk.ef
                tm.gk1 = gk.gk1 * gk.ef
            } else { tm.gkStop = 15f; tm.gk1 = 15f }
            tm.avgCond = if (n > 0) cond / n else 100f

            // Effective tactics are recalculated from the live state. The AI policy returns
            // transient settings only; saved/user tactics are never mutated by match simulation.
            if (tm.ai) {
                val decision = MatchTacticalAI.decide(
                    MatchTacticalContext(
                        half = half,
                        minuteInHalf = t,
                        goalDifference = tm.goals - opp.goals,
                        ownRedCards = tm.reds,
                        opponentRedCards = opp.reds,
                        averageCondition = tm.avgCond,
                        baseMentality = tm.tactics.mentality,
                        basePressing = tm.tactics.pressing,
                        baseTempo = tm.tactics.tempo,
                        baseLine = tm.tactics.line,
                        baseWidth = tm.tactics.width,
                        basePassing = tm.tactics.passing,
                        attackByLane = tm.atk.toList(),
                        opponentDefenceByLane = opp.dfn.toList(),
                        previousWidth = tm.eWidth
                    )
                )
                tm.eMent = decision.mentality
                tm.ePress = decision.pressing
                tm.eLine = decision.line
                tm.eTempo = decision.tempo
                tm.eWidth = decision.width
                tm.ePass = decision.passing
            } else {
                tm.eMent = clampI(tm.tactics.mentality, -3, 3)
                tm.ePress = clampI(tm.tactics.pressing, 0, 2)
                tm.eLine = clampI(tm.tactics.line, 0, 2)
                tm.eTempo = clampI(tm.tactics.tempo, 0, 2)
                tm.eWidth = clampI(tm.tactics.width, 0, 2)
                tm.ePass = clampI(tm.tactics.passing, 0, 2)
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun sig(x: Float): Float = 1f / (1f + exp(-x))
    private fun rf(): Float = rnd.nextFloat()
    private fun lg(r: Float): Float = ln(max(0.05f, r))
    private fun pickOf(vararg s: String): String = s[rnd.nextInt(s.size)]
    private fun rate(ps: PS?, d: Float) { if (ps != null) ps.rating += d }
    private fun tmOf(i: Int): MatchTeam = teams[i]

    private inline fun pickBy(list: List<PS>, w: (PS) -> Float): PS? {
        var tot = 0f
        for (ps in list) tot += max(0f, w(ps))
        if (tot <= 0f) return null
        var r = rf() * tot
        for (ps in list) {
            r -= max(0f, w(ps))
            if (r <= 0f) return ps
        }
        return list.lastOrNull()
    }

    private fun gauss(): Float = rnd.gauss().toFloat()

    private fun noise(sd: Float): Float = exp(sd * gauss() - 0.5f * sd * sd)

    private fun turnover(to: Int, z: Int, fast: Boolean) {
        poss = to
        zone = z
        trans = fast
    }

    // ------------------------------------------------------------------ minute

    private fun playMinute() {
        minutesPlayed++
        for (tm in teams) {
            val tf = 1f + 0.10f * (tm.eTempo - 1) + 0.20f * (tm.ePress - 1)
            for (ps in tm.onPitch) {
                ps.mins++
                val st = ps.p.attrs.at(Attr.Stamina)
                val wr = ps.p.attrs.at(Attr.WorkRate)
                val de = ps.p.attrs.at(Attr.Determination)
                var drain = 0.30f * (1.3f - st / 20f * 0.6f) * tf * (0.9f + wr / 100f) * (1.05f - de / 400f)
                if (ps.slot == Pos.GK) drain *= 0.25f
                ps.cond = max(0f, ps.cond - drain)
            }
        }
        refresh()

        budget += 60f
        var guard = 0
        while (budget > 0f && guard < 45 && !atHalfTime) {
            budget -= phase()
            guard++
        }
        if (budget > 20f) budget = 20f

        offBallFouls()
        injuryCheck(home, 0)
        injuryCheck(away, 1)
        aiSubs(home, away, 0)
        aiSubs(away, home, 1)
        val mn = minuteNum()
        if (half == 1 && t == 30 || half == 2 && t == 30 || (half == 2 && t == 75)) {
            if (abs(possessionHome() - 50) >= 9) {
                val lead = if (possessionHome() > 50) home.club.name else away.club.name
                add(-1, 0, "$lead are dominating the ball (${statLine()})")
            }
        }
        for (tm in teams) tm.mom *= 0.93f
        if (mn < 0) return
    }

    private fun phase(): Float {
        val ai = poss
        val att = teams[ai]
        val def = teams[1 - ai]
        val z = zone
        var dur = when (z) { 0 -> 10f; 1 -> 11f; else -> 8f }
        dur *= (if (att.eTempo == 0) 1.18f else if (att.eTempo == 2) 0.84f else 1f)
        dur *= (if (att.ePass == 0) 1.08f else if (att.ePass == 2) 0.88f else 1f)
        dur *= 0.65f + 0.8f * rf()
        dur *= K.TEMPO
        possSec[ai] += dur.toDouble()
        when (z) {
            0 -> buildUp(att, def, ai)
            1 -> midfield(att, def, ai)
            else -> finalThird(att, def, ai)
        }
        return dur
    }

    // ------------------------------------------------------------------ passing stats

    private fun creditPasses(att: MatchTeam, actor: PS?, n: Int, ok: Boolean) {
        val failed = if (ok) 0 else 1
        att.passes += n
        att.passesOk += n - failed
        if (actor != null) { actor.passes += n; actor.passesOk += n - failed }
    }

    private fun pickActor(att: MatchTeam, wt: FloatArray): PS? =
        pickBy(att.onPitch) { ps -> wt[ps.slot.ordinal] * (0.3f + ps.bld / 60f) * (0.3f + ps.bld / 60f) }

    // ------------------------------------------------------------------ zone 0: build-up

    private fun buildUp(att: MatchTeam, def: MatchTeam, ai: Int) {
        val actor = pickActor(att, W.BUILD)
        val learner = actor?.p?.learning
        val learningState = if (actor != null) PlayerLearningState.encode(
            zone, actor.cond, def.pressP, att.goals - def.goals
        ) else 0
        val learnedAction = if (learner != null) learner.choose(
            learningState,
            exploration = (0.24f - actor!!.p.apps.coerceAtMost(100) * 0.0015f).coerceAtLeast(0.08f),
            random = rnd,
            allowed = listOf(PlayerAction.SHORT_PASS, PlayerAction.LONG_PASS, PlayerAction.DRIBBLE, PlayerAction.HOLD)
        ) else PlayerAction.SHORT_PASS
        val pi = when (def.ePress) { 0 -> 0.80f; 1 -> 1.0f; else -> 1.30f }
        var r = att.ctrl / max(10f, def.pressP * pi * (1f + 0.05f * (def.eLine - 1)))
        if (att.ePass == 0) r *= 1.06f
        if (att.ePass == 2) r *= 1.02f
        val risk = 0.03f * att.eMent
        val pOk = sig(K.A0 + K.B0 * lg(r) + risk)
        val n = 1 + (if (rf() < 0.6f) 1 else 0) + (if (att.eTempo == 2) 0 else 1)
        val learnedLongBallChance = when (learnedAction) {
            PlayerAction.LONG_PASS -> 0.34f
            PlayerAction.SHORT_PASS -> 0.12f
            PlayerAction.DRIBBLE -> 0.08f
            else -> 0.18f
        }
        val longBallChance = if (att.ePass == 2) 0.22f else learnedLongBallChance
        if ((att.ePass == 2 || learnedAction == PlayerAction.LONG_PASS) && rf() < longBallChance) {
            // Long ball over the press; the player's learned preference affects choice, not physics.
            val rr = (att.aerA * (0.85f + 0.3f * att.gkStop / 80f)) / max(10f, def.aerD)
            val pl = sig(-0.35f + 1.0f * lg(rr))
            val longPassSuccess = rf() < pl
            creditPasses(att, att.gkPS ?: actor, 1, longPassSuccess)
            if (longPassSuccess) {
                zone = 2; trans = false
                learner?.update(learningState, learnedAction, 0.35f, PlayerLearningState.encode(zone, actor?.cond ?: 60f, def.pressP, att.goals - def.goals))
            } else {
                learner?.update(learningState, learnedAction, -0.35f, learningState)
                winBall(def, 1 - ai, 1, 0.12f, att)
            }
            return
        }
        if (rf() < pOk) {
            creditPasses(att, actor, n, true)
            zone = 1
            trans = false
            learner?.update(learningState, learnedAction, if (learnedAction == PlayerAction.HOLD) 0.08f else 0.18f,
                PlayerLearningState.encode(zone, actor?.cond ?: 60f, def.pressP, att.goals - def.goals))
        } else {
            learner?.update(learningState, learnedAction, -0.25f, learningState)
            creditPasses(att, actor, n, false)
            // pressed in own third: foul, or the ball is won high
            val uf = rf()
            if (uf < 0.10f * K.FOUL * (1f + 0.12f * (def.ePress - 1))) {
                foul(def, att, 1 - ai, ai, 0, false, actor)
                return
            }
            actor?.let { rate(it, -0.10f) }
            winBall(def, 1 - ai, if (rf() < 0.30f + 0.18f * (def.ePress - 1)) 2 else 1, 0.35f + 0.10f * (def.ePress - 1), att)
        }
    }

    /** Defender team wins the ball. [z] is the zone from the winner's point of view. */
    private fun winBall(winner: MatchTeam, wi: Int, z: Int, pFast: Float, loser: MatchTeam) {
        val dp = pickBy(winner.onPitch) { ps -> W.MIDDEF[ps.slot.ordinal] * (0.3f + ps.dfnAb / 60f) + 0.1f }
        var pressAction = PlayerAction.PRESS
        var pressState = 0
        if (dp != null) {
            pressState = PlayerLearningState.encode(zone, dp.cond, loser.pressP, winner.goals - loser.goals)
            pressAction = dp.p.learning.choose(pressState, 0.04f, rnd, listOf(PlayerAction.PRESS, PlayerAction.HOLD))
            dp.tackles++
            winner.tackles++
            if (rf() < 0.5f) { dp.interceptions++; rate(dp, 0.05f) } else rate(dp, 0.07f)
        }
        var fast = false
        var zz = z
        if (z >= 1) {
            val pc = pFast + 0.08f * loser.eMent + 0.05f * (loser.eLine - 1) + (if (winner.ePass == 2) 0.06f else 0f) +
                (if (winner.pace > 62f) 0.03f else 0f)
            val pressingAdjustment = if (pressAction == PlayerAction.PRESS) 0.035f else -0.025f
            if (rf() < clampF(pc + pressingAdjustment, 0.03f, 0.7f)) { zz = 2; fast = true }
        }
        if (z == 2) fast = fast || rf() < 0.5f
        if (dp != null) dp.p.learning.update(pressState, pressAction, if (fast) 0.25f else -0.08f,
            PlayerLearningState.encode(zz, dp.cond, loser.pressP, winner.goals - loser.goals))
        turnover(wi, zz, fast)
    }

    // ------------------------------------------------------------------ zone 1: midfield

    private fun midfield(att: MatchTeam, def: MatchTeam, ai: Int) {
        val actor = pickActor(att, W.BUILD)
        val pi = when (def.ePress) { 0 -> 0.85f; 1 -> 1.0f; else -> 1.20f }
        val denial = 0.55f * def.midDef + 0.45f * def.pressP * pi
        var r = att.ctrl * (if (att.eTempo == 2) 1.02f else if (att.eTempo == 0) 0.98f else 1f) / max(10f, denial)
        if (def.eLine == 0) r *= 1.05f
        if (def.eLine == 2) r *= 0.97f
        r *= 1f + 0.03f * att.eMent
        val pOk = sig(K.A1 + K.B1 * lg(r))
        val n = 1 + (if (rf() < 0.6f) 1 else 0) + (if (att.eTempo == 2) 0 else 1)
        if (rf() < pOk) {
            creditPasses(att, actor, n, true)
            zone = 2
            trans = false
            if (actor != null && rf() < 0.07f) { actor.dribbles++; actor.dribblesWon++; rate(actor, 0.04f) }
            return
        }
        creditPasses(att, actor, n, false)
        val uf = rf()
        if (uf < 0.115f * K.FOUL * (1f + 0.12f * (def.ePress - 1))) {
            foul(def, att, 1 - ai, ai, 1, false, actor)
            return
        }
        if (uf > 0.95f) {
            // attacking player fouls
            foulAgainst(att, def, ai, 1 - ai, 1)
            return
        }
        actor?.let { rate(it, -0.06f) }
        if (rf() < 0.12f) {
            // ball out of play, possession stays or goes
            turnover(1 - ai, 1, false)
            return
        }
        winBall(def, 1 - ai, if (rf() < 0.14f) 1 else 1, 0.15f, att)
    }

    // ------------------------------------------------------------------ zone 2: final third

    private fun finalThird(att: MatchTeam, def: MatchTeam, ai: Int) {
        val fast = trans
        trans = false
        val u = rf()
        val pLong = K.LONG * (1f + 0.5f * (att.ePass == 2).toInt() + 0.25f * att.eMent) * (if (fast) 0.3f else 1f)
        if (u < pLong) { longShot(att, def, ai); return }
        val pRecycle = if (fast) 0.0f else 0.14f - 0.03f * att.eMent + 0.04f * (att.eTempo == 0).toInt()
        if (u < pLong + pRecycle) {
            creditPasses(att, pickActor(att, W.BUILD), 1, true)
            zone = 1
            return
        }
        val ch = chooseChannel(att, def)
        val r = channelRatio(att, def, ch, fast)
        val pOk = sig(K.A2 + K.B2 * lg(r))
        if (rf() < pOk) {
            createChance(att, def, ai, ch, fast, r)
        } else {
            failFinal(att, def, ai, ch, fast)
        }
    }

    private fun chooseChannel(att: MatchTeam, def: MatchTeam): Int {
        val wd = att.eWidth
        val wide = if (wd == 2) 1.30f else if (wd == 0) 0.78f else 1f
        val mid = if (wd == 2) 0.82f else if (wd == 0) 1.25f else 1f
        val wl = att.atk[0].pow(1.1f) * (60f / max(20f, def.dfn[2])).pow(0.8f) * wide
        val wc = att.atk[1].pow(1.1f) * (60f / max(20f, def.dfn[1])).pow(0.8f) * mid
        val wr = att.atk[2].pow(1.1f) * (60f / max(20f, def.dfn[0])).pow(0.8f) * wide
        var x = rf() * (wl + wc + wr)
        x -= wl
        if (x <= 0f) return 0
        x -= wc
        if (x <= 0f) return 1
        return 2
    }

    private fun channelRatio(att: MatchTeam, def: MatchTeam, ch: Int, fast: Boolean): Float {
        var d = def.dfn[2 - ch]
        if (fast) d *= (1f - 0.30f * (1f + 0.35f * att.eMent + 0.25f * (att.eLine - 1)).coerceIn(0.4f, 1.6f))
        var r = att.atk[ch] / max(10f, d)
        r *= (1f + 0.045f * att.eMent) / (1f - 0.035f * def.eMent)
        if (!fast) r *= 1f - 0.025f * (def.eLine - 1)
        if (def.ePress == 2) r *= 1f + 0.015f
        return r
    }

    private fun longShot(att: MatchTeam, def: MatchTeam, ai: Int) {
        val shooter = pickBy(att.onPitch) { ps ->
            val w = when (ps.slot) { Pos.CM -> 2.4f; Pos.AM -> 2.6f; Pos.DM -> 1.4f; Pos.LW, Pos.RW -> 1.6f; Pos.ST -> 1.0f
                Pos.LB, Pos.RB -> 0.7f; Pos.CB -> 0.3f; Pos.GK -> 0f }
            w * (ps.lsh * ps.ef / 60f).pow(2f)
        } ?: return
        val xg = 0.032f * K.XG * (shooter.lsh * shooter.ef / 60f).pow(0.8f) * noise(0.5f) * (1f + 0.04f * (def.eLine - 1)) *
            (60f / max(25f, def.dfn[1])).pow(0.2f)
        shoot(att, def, ai, shooter, null, S_LONG, xg, 0, 0)
    }

    private fun failFinal(att: MatchTeam, def: MatchTeam, ai: Int, ch: Int, fast: Boolean) {
        val di = 1 - ai
        val actor = pickBy(att.onPitch) { ps -> W.ATK[ps.slot.ordinal][ch] * (0.3f + ps.atkAb / 60f) }
        val u = rf()
        var c = 0.10f * K.FOUL * (1f + 0.15f * (def.ePress - 1))
        if (u < c) { foul(def, att, di, ai, 2, fast, actor); return }
        c += K.OFFSIDE * (1f + 0.40f * (def.eLine - 1)) * (if (att.ePass == 2) 1.25f else 1f) * (if (fast) 1.2f else 1f)
        if (u < c) {
            att.offsides++
            actor?.let { it.offsides++; rate(it, -0.06f) }
            if (rf() < 0.10f) add(ai, 0, "Offside! ${actor?.p?.name ?: "The forward"} (${att.club.name}) is caught out by the line.")
            turnover(di, 1, false)
            stopSecs += 6f
            return
        }
        c += K.CORNER
        if (u < c) {
            val db = pickBy(def.onPitch) { ps -> W.DEF[ps.slot.ordinal][2 - ch] * (0.3f + ps.dfn / 60f) }
            db?.let { it.blocks++; rate(it, 0.06f) }
            corner(att, def, ai)
            return
        }
        c += 0.035f
        if (u < c) { foulAgainst(att, def, ai, di, 2); return }
        // possession lost
        val db = pickBy(def.onPitch) { ps -> W.DEF[ps.slot.ordinal][2 - ch] * (0.3f + ps.dfn / 60f) }
        db?.let {
            it.tackles++
            def.tackles++
            if (rf() < 0.45f) { it.interceptions++; rate(it, 0.05f) } else rate(it, 0.07f)
        }
        if (actor != null && rf() < 0.5f) { actor.dribbles++; rate(actor, -0.05f) }
        val x = rf()
        if (x < 0.12f) turnover(ai, 2, false)                      // loose ball, attackers keep it
        else if (x < 0.50f) turnover(di, 0, false)                 // cleared / keeper
        else if (x < 0.86f) turnover(di, 1, false)
        else turnover(di, 2, true)                                 // sparks a counter
    }

    // ------------------------------------------------------------------ chance creation

    private fun createChance(att: MatchTeam, def: MatchTeam, ai: Int, ch: Int, fast: Boolean, r: Float) {
        val wide = ch != 1
        // choose style
        var wThr: Float; var wCrs: Float; var wCut: Float; var wDri: Float
        if (fast) { wThr = 0.46f; wCrs = 0.10f; wCut = 0.08f; wDri = 0.36f }
        else if (wide) {
            wThr = 0.08f
            wCrs = 0.50f + 0.14f * (att.eWidth - 1) + 0.06f * (att.ePass == 2).toInt()
            wCut = 0.17f
            wDri = 0.20f
        } else {
            wThr = 0.44f - 0.06f * (att.ePass == 2).toInt()
            wCrs = 0.10f + 0.10f * (att.ePass == 2).toInt() + 0.05f * (att.eWidth - 1)
            wCut = 0.12f
            wDri = 0.24f
        }
        var x = rf() * (wThr + wCrs + wCut + wDri)
        var style = S_THR
        x -= wThr
        if (x > 0f) {
            x -= wCrs
            style = if (x <= 0f) S_HEAD else {
                x -= wCut
                if (x <= 0f) S_CUT else S_DRI
            }
        }
        if (style == S_HEAD && rf() < 0.38f) style = S_FOOT
        if (fast && style == S_THR && rf() < 0.55f) style = S_1V1

        // creator
        val creator: PS? = when (style) {
            S_HEAD, S_FOOT -> pickBy(att.onPitch) { ps -> W.ATK[ps.slot.ordinal][ch] * (ps.crs * ps.ef / 60f).pow(2.5f) }
            S_DRI -> null
            else -> pickBy(att.onPitch) { ps ->
                val w = W.ATK[ps.slot.ordinal][ch] + (if (ps.slot == Pos.AM || ps.slot == Pos.CM) 0.25f else 0f)
                w * (ps.crt * ps.ef / 60f).pow(2.5f)
            }
        }
        val kind = if (style == S_HEAD) S_HEAD else style
        val shooter = pickShooter(att, kind, ch, creator) ?: return
        val keyDef = pickBy(def.onPitch) { ps -> W.DEF[ps.slot.ordinal][2 - ch] * (0.3f + ps.dfn / 60f) }

        // chance quality
        val base = when (style) {
            S_THR -> 0.118f
            S_HEAD -> 0.074f
            S_FOOT -> 0.062f
            S_CUT -> 0.140f
            S_DRI -> 0.088f
            else -> 0.250f
        }
        var q = r.pow(0.42f)
        if (creator != null) q *= (creator.crt * creator.ef / 60f).pow(0.22f)
        q *= (shooter.mov * shooter.ef / 60f).pow(0.30f)
        if (keyDef != null) q /= (keyDef.dfnAb * keyDef.ef / 60f).pow(0.22f)
        if (style == S_HEAD || style == S_FOOT) {
            q *= (creator?.let { it.crs * it.ef / 60f } ?: 1f).pow(0.35f)
            val air = (shooter.hed * shooter.ef / 60f) / max(0.4f, (keyDef?.let { 0.6f * it.hed + 0.4f * it.dfn } ?: 55f) * (keyDef?.ef ?: 1f) / 60f)
            q *= if (style == S_HEAD) air.pow(0.6f) else air.pow(0.2f)
            q *= 1f - 0.05f * (def.eLine - 1) * 0f
        }
        if (style == S_THR || style == S_1V1) q *= 1f + 0.05f * (def.eLine - 1) - 0.04f * (def.eLine == 0).toInt()
        if (style == S_DRI) q *= (0.5f + 0.5f * shooter.car * shooter.ef / 60f)
        if (fast) q *= 1.30f * (att.pace / max(35f, def.dfn[1])).coerceIn(0.8f, 1.3f).pow(0.5f)
        val xg = clampF(base * q * K.XG * calibration.chanceCreationMultiplier * noise(0.55f), 0.012f, 0.82f)
        // key pass credit
        val pAssist = when (style) { S_THR -> 0.90f; S_HEAD -> 0.97f; S_FOOT -> 0.97f; S_CUT -> 0.97f; S_1V1 -> 0.75f; else -> 0.10f }
        val assist = if (creator != null && creator !== shooter && rf() < pAssist) creator else null
        creditPasses(att, creator ?: shooter, 1, true)
        shoot(att, def, ai, shooter, assist, if (style == S_HEAD) S_HEAD else style, xg, ch, 1)
    }

    private fun pickShooter(att: MatchTeam, kind: Int, ch: Int, creator: PS?): PS? {
        val headerish = kind == S_HEAD
        return pickBy(att.onPitch) { ps ->
            if (ps === creator) 0f else {
                val s = ps.slot
                var w = if (headerish) when (s) { Pos.ST -> 5f; Pos.CB -> 1.6f; Pos.AM -> 0.9f; Pos.LW, Pos.RW -> 0.9f; Pos.CM -> 1.0f
                    Pos.DM -> 0.6f; Pos.LB, Pos.RB -> 0.3f; Pos.GK -> 0f }
                else when (s) { Pos.ST -> 5f; Pos.AM -> 2.4f; Pos.LW, Pos.RW -> 2.6f; Pos.CM -> 1.2f; Pos.DM -> 0.4f
                    Pos.LB, Pos.RB -> 0.45f; Pos.CB -> 0.25f; Pos.GK -> 0f }
                if (kind == S_DRI) w *= 0.3f + W.ATK[s.ordinal][ch] * 2f
                w * (ps.mov * ps.ef / 60f).pow(1.4f) * (0.4f + (if (headerish) ps.hed else ps.fin) * ps.ef / 60f)
            }
        }
    }

    // ------------------------------------------------------------------ shooting

    private fun shotQuality(shooter: PS, kind: Int): Float {
        val q = when (kind) {
            S_HEAD, S_COR -> 0.65f * shooter.hed + 0.35f * shooter.fin
            S_LONG -> shooter.lsh
            S_FK -> 0.8f * shooter.stp + 0.2f * shooter.lsh
            S_PEN -> 0.5f * shooter.fin + 0.3f * shooter.stp + 0.2f * shooter.p.attrs.at(Attr.Composure) * 5f
            else -> 0.85f * shooter.fin + 0.15f * shooter.mov
        }
        return q * shooter.ef
    }

    /** Resolves one shot. Sets possession / zone for what happens next. */
    private fun shoot(att: MatchTeam, def: MatchTeam, ai: Int, shooter: PS, assist: PS?, kind: Int, xg: Float, ch: Int, depth: Int) {
        val di = 1 - ai
        att.shots++
        shooter.shots++
        att.xg += xg.toDouble()
        shooter.xg += xg.toDouble()
        assist?.let { it.xa += xg.toDouble(); if (xg > 0.08f) it.keyPasses++ }
        if (xg >= 0.25f) att.bigChances++
        val gk = def.gkPS
        val oneOne = kind == S_1V1
        val gkQ = if (oneOne) 0.55f * def.gkStop + 0.45f * def.gk1 else def.gkStop
        val fq = shotQuality(shooter, kind)
        val skill = ((fq / 60f).pow(0.6f) / (max(15f, gkQ) / 60f).pow(0.55f)).coerceIn(0.45f, 1.9f)
        val capped = home.goals + away.goals >= K.MAX_TOTAL_GOALS
        val pGoal = if (capped) 0f else clampF(xg * skill * K.GOAL * calibration.shotConversionMultiplier, 0f, 0.97f)
        val name = shooter.p.name
        if (rf() < pGoal) {
            // offside / VAR check on open-play goals
            if (kind != S_PEN && kind != S_FK && rf() < 0.032f) {
                att.sot++
                shooter.sot++
                add(ai, 6, "Goal disallowed! $name (${att.club.name}) had the ball in the net but the flag is up.")
                stopSecs += 40f
                turnover(di, 0, false)
                return
            }
            shooter.p.learning.update(PlayerLearningState.encode(zone, shooter.cond, def.pressP, att.goals - def.goals), PlayerAction.SHOOT,
                (1f - xg).coerceIn(-1f, 1f), PlayerLearningState.encode(zone, shooter.cond, def.pressP, att.goals - def.goals))
            scoreGoal(att, def, ai, shooter, assist, kind, xg)
            return
        }
        // xG-based learning rewards calibration of shot choice quality, not a fixed goal bonus.
        shooter.p.learning.update(PlayerLearningState.encode(zone, shooter.cond, def.pressP, att.goals - def.goals), PlayerAction.SHOOT,
            (-xg).coerceIn(-1f, 1f), PlayerLearningState.encode(zone, shooter.cond, def.pressP, att.goals - def.goals))
        // non-goal: save, block or miss
        val headerKind = kind == S_HEAD || kind == S_COR
        val wSave = 0.16f + 1.25f * xg
        val wBlock = if (headerKind || kind == S_FK) 0.06f else max(0.03f, 0.30f * (1f - min(0.9f, xg * 2.2f)) *
            (max(30f, def.dfn[1]) / 60f).pow(0.4f))
        val wOff = max(0.12f, 0.58f - 0.9f * xg) * (0.85f + 0.3f * (60f / max(25f, fq)).coerceIn(0.5f, 1.6f).pow(0.5f))
        var x = rf() * (wSave + wBlock + wOff)
        x -= wSave
        if (x <= 0f) {
            // saved
            att.sot++
            shooter.sot++
            rate(shooter, 0.06f + 0.2f * xg)
            if (gk != null) {
                gk.saves++
                gk.xgPrevented += xg.toDouble()
                gk.rating += 0.10f + 0.9f * xg
            }
            if (xg >= 0.11f || rf() < 0.12f) {
                val how = if (xg >= 0.30f) pickOf("makes a stunning reaction save", "stands tall to deny", "produces a huge save", "smothers it brilliantly")
                else pickOf("saves well", "gets down to turn it away", "tips it round the post", "pushes it away", "holds on safely")
                add(ai, 7, "Save! ${gk?.p?.name ?: "The keeper"} $how from $name (${att.club.name}).")
            }
            val u = rf()
            if (u < 0.20f + 0.08f * (xg > 0.12f).toInt()) corner(att, def, ai)
            else if (u < 0.20f + 0.08f * (xg > 0.12f).toInt() + (if (xg > 0.08f && depth > 0) 0.09f else 0f)) {
                val reb = pickBy(att.onPitch) { ps ->
                    if (ps.slot == Pos.GK) 0f else (if (ps === shooter) 1.2f else 0.8f) * (ps.mov * ps.ef / 60f).pow(1.5f) *
                        (if (ps.slot == Pos.ST) 2f else if (ps.slot.group >= 3) 1.2f else 0.7f)
                }
                if (reb != null) {
                    val rx = clampF(0.19f * K.XG * noise(0.4f), 0.04f, 0.5f)
                    shoot(att, def, ai, reb, null, S_REB, rx, ch, 0)
                } else turnover(di, 0, false)
            } else turnover(di, 0, false)
            return
        }
        x -= wBlock
        if (x <= 0f) {
            att.blocked++
            val db = pickBy(def.onPitch) { ps -> if (ps.slot == Pos.GK) 0f else (0.3f + ps.dfn / 60f) * (if (ps.slot.group == 1) 1.6f else 1f) }
            db?.let { it.blocks++; rate(it, 0.10f) }
            if (xg > 0.14f) add(ai, 6, "Last-ditch block by ${db?.p?.name ?: "a defender"} denies $name (${att.club.name})!")
            val u = rf()
            if (u < 0.27f) corner(att, def, ai)
            else if (u < 0.55f) turnover(ai, 2, false)
            else turnover(di, 1, false)
            return
        }
        // off target
        if (xg > 0.07f && rf() < 0.07f) {
            att.woodwork++
            att.sot++
            shooter.sot++
            add(ai, 6, "$name (${att.club.name}) ${pickOf("rattles the post", "hits the crossbar", "strikes the woodwork")}!")
            val u = rf()
            if (u < 0.35f) turnover(ai, 2, false) else turnover(di, 0, false)
            return
        }
        if (xg > 0.24f) {
            rate(shooter, -0.25f)
            add(ai, 6, "$name misses a big chance for ${att.club.name}! ${pickOf("Blazed over the bar.", "Dragged wide of the post.", "Somehow he drags it wide.", "It should have been a goal.")}")
        } else if (xg > 0.12f && rf() < 0.45f) {
            rate(shooter, -0.08f)
            add(ai, 6, "$name (${att.club.name}) ${pickOf("fires over", "shoots wide", "sees his effort drift past the post", "can't keep it down")}.")
        } else rate(shooter, -0.02f)
        if (kind == S_LONG || rf() < 0.5f) turnover(di, 0, false)
        else turnover(di, 0, false)
    }

    private fun scoreGoal(att: MatchTeam, def: MatchTeam, ai: Int, shooter: PS, assist: PS?, kind: Int, xg: Float) {
        val di = 1 - ai
        val before = att.goals - def.goals
        att.goals++
        att.sot++
        shooter.goals++
        shooter.sot++
        rate(shooter, if (shooter.slot.group >= 3 || shooter.slot == Pos.AM) 0.80f else 0.95f)
        if (assist != null) { assist.assists++; assist.rating += 0.50f }
        def.gkPS?.let { it.rating -= 0.18f }
        for (d in def.onPitch) if (d.slot.group == 1) d.rating -= 0.10f
        for (d in def.onPitch) if (d.slot.group == 2) d.rating -= 0.04f
        att.mom = min(1.5f, att.mom + 0.30f)
        def.mom = max(-1.5f, def.mom - 0.25f)
        stopSecs += 45f
        val how = when (kind) {
            S_HEAD, S_COR -> pickOf("rises highest to head home", "powers a header past the keeper", "nods it in at the far post", "glances a header into the corner", "meets it perfectly with his head")
            S_LONG -> pickOf("hits a thunderbolt from distance", "unleashes a long-range screamer", "curls one in from outside the box", "rifles it into the top corner from range")
            S_FK -> pickOf("bends the free-kick into the top corner", "beats the wall and the keeper from the free-kick", "whips the free-kick past the wall")
            S_PEN -> pickOf("sends the keeper the wrong way from the spot", "converts the penalty", "buries the penalty")
            S_REB -> pickOf("pounces on the rebound", "tucks home the loose ball", "follows up to score")
            S_1V1 -> pickOf("rounds the keeper and slots in", "is clean through and finishes calmly", "lifts it over the onrushing keeper", "keeps his nerve one-on-one")
            S_FOOT -> pickOf("volleys it home", "meets the cross first time and scores", "smashes in the loose ball")
            S_CUT -> pickOf("side-foots in the cut-back", "arrives late to tap in", "slots in from the edge of the six-yard box")
            S_DRI -> pickOf("dribbles in and finishes", "cuts inside and curls it in", "beats his man and fires home")
            else -> pickOf("finishes coolly", "slots it home", "fires past the keeper", "tucks it away", "buries the chance", "drills it into the corner")
        }
        val ass = if (assist != null) " Assisted by ${assist.p.name}." else ""
        val now = att.goals - def.goals
        val tag = when {
            before < 0 && now == 0 -> "Equaliser! "
            before == 0 && now == 1 && att.goals + def.goals == 1 -> "The deadlock is broken! "
            before == 0 && now == 1 -> "Takes the lead! "
            before < 0 && now < 0 -> "Pulls one back! "
            before >= 1 -> if (now >= 3) "It's a rout! " else "Extends the lead! "
            else -> ""
        }
        add(ai, 1, "GOAL! $tag${shooter.p.name} $how for ${att.club.name}.$ass (${home.goals}-${away.goals})")
        turnover(di, 1, false)
    }

    // ------------------------------------------------------------------ set pieces

    private fun corner(att: MatchTeam, def: MatchTeam, ai: Int) {
        att.corners++
        val di = 1 - ai
        stopSecs += 6f
        val taker = att.onPitch.maxByOrNull { it.stp * it.ef } ?: return
        if (rf() < 0.10f) { creditPasses(att, taker, 1, true); turnover(ai, 2, false); return }
        val del = (taker.stp * taker.ef / 60f).pow(0.7f)
        val aer = (att.aerA / max(10f, def.aerD)).pow(0.6f)
        val pHead = clampF(0.30f * aer * del, 0.08f, 0.62f)
        if (rf() < pHead) {
            val shooter = pickBy(att.onPitch) { ps ->
                val w = when (ps.slot) { Pos.CB -> 2.6f; Pos.ST -> 3.2f; Pos.GK -> 0f; Pos.DM -> 0.9f; Pos.CM -> 0.8f; else -> 0.6f }
                if (ps === taker) 0f else w * (ps.hed * ps.ef / 60f).pow(2.2f)
            } ?: return
            val xg = clampF(0.085f * K.XG * del.pow(0.5f) * aer.pow(0.8f) * noise(0.55f), 0.015f, 0.5f)
            taker.passes++
            taker.passesOk++
            shoot(att, def, ai, shooter, taker, S_COR, xg, 1, 1)
            return
        }
        val u = rf()
        when {
            u < 0.50f -> turnover(di, if (rf() < 0.7f) 1 else 0, false)
            u < 0.72f -> turnover(di, 0, false)
            else -> turnover(ai, 2, false)
        }
    }

    private fun freeKick(att: MatchTeam, def: MatchTeam, ai: Int, z: Int) {
        val di = 1 - ai
        stopSecs += 8f
        if (z == 2) {
            if (rf() < K.PEN) { penalty(att, def, ai); return }
            val taker = att.onPitch.maxByOrNull { it.stp * it.ef + it.lsh * 0.3f } ?: return
            val u = rf()
            if (u < 0.30f) {
                val xg = clampF(0.050f * K.XG * (taker.stp * taker.ef / 60f).pow(1.5f) * noise(0.5f), 0.01f, 0.4f)
                shoot(att, def, ai, taker, null, S_FK, xg, 1, 0)
            } else if (u < 0.65f) {
                val del = (taker.stp * taker.ef / 60f).pow(0.7f)
                val aer = (att.aerA / max(10f, def.aerD)).pow(0.6f)
                if (rf() < clampF(0.26f * aer * del, 0.06f, 0.55f)) {
                    val shooter = pickBy(att.onPitch) { ps ->
                        val w = when (ps.slot) { Pos.CB -> 2.4f; Pos.ST -> 3.0f; Pos.GK -> 0f; else -> 0.7f }
                        if (ps === taker) 0f else w * (ps.hed * ps.ef / 60f).pow(2.2f)
                    } ?: return
                    val xg = clampF(0.070f * K.XG * del.pow(0.5f) * aer.pow(0.8f) * noise(0.55f), 0.012f, 0.45f)
                    shoot(att, def, ai, shooter, taker, S_COR, xg, 1, 1)
                } else turnover(di, if (rf() < 0.6f) 1 else 0, false)
            } else turnover(ai, 2, false)
        } else if (z == 1) {
            if (rf() < 0.18f) turnover(ai, 2, false) else turnover(ai, 1, false)
        } else turnover(ai, 0, false)
    }

    private fun penalty(att: MatchTeam, def: MatchTeam, ai: Int) {
        att.penalties++
        val taker = att.onPitch.maxByOrNull { shotQuality(it, S_PEN) + gauss() * 3f } ?: return
        val gk = def.gkPS
        att.shots++
        taker.shots++
        att.xg += 0.76
        taker.xg += 0.76
        att.bigChances++
        stopSecs += 60f
        add(ai, 6, "Penalty to ${att.club.name}! ${taker.p.name} steps up.")
        val q = shotQuality(taker, S_PEN)
        val pg = clampF(0.76f * K.GOAL * (q / 60f).pow(0.5f) / (max(15f, def.gkStop) / 60f).pow(0.3f), 0.5f, 0.92f)
        if (home.goals + away.goals < K.MAX_TOTAL_GOALS && rf() < pg) {
            att.goals++
            att.sot++
            taker.goals++
            taker.sot++
            taker.rating += 0.9f
            gk?.let { it.rating -= 0.2f }
            att.mom = min(1.5f, att.mom + 0.25f)
            def.mom = max(-1.5f, def.mom - 0.2f)
            add(ai, 1, "GOAL! ${taker.p.name} converts the penalty for ${att.club.name}. (${home.goals}-${away.goals})")
            turnover(1 - ai, 1, false)
        } else if (rf() < 0.62f) {
            att.sot++
            taker.sot++
            gk?.let { it.saves++; it.rating += 0.7f; it.xgPrevented += 0.76 }
            add(ai, 7, "Saved! ${gk?.p?.name ?: "The keeper"} stops the penalty from ${taker.p.name}.")
            turnover(1 - ai, 0, false)
        } else {
            taker.rating -= 0.5f
            add(ai, 6, "${taker.p.name} puts the penalty ${pickOf("wide", "over the bar", "off the post")}!")
            turnover(1 - ai, 0, false)
        }
    }

    // ------------------------------------------------------------------ discipline & injuries

    /** [def] commits a foul against [att] in zone [z] (from [att]'s point of view). */
    private fun foul(def: MatchTeam, att: MatchTeam, di: Int, ai: Int, z: Int, cynical: Boolean, victim: PS?) {
        val fouler = pickBy(def.onPitch) { ps ->
            val g = ps.slot.group
            val zw = when (z) {
                2 -> when (g) { 0 -> 0.02f; 1 -> 1.0f; 2 -> 0.7f; 3 -> 0.35f; else -> 0.15f }
                1 -> when (g) { 0 -> 0f; 1 -> 0.5f; 2 -> 1.0f; 3 -> 0.6f; else -> 0.3f }
                else -> when (g) { 0 -> 0f; 1 -> 0.2f; 2 -> 0.5f; 3 -> 0.8f; else -> 1.0f }
            }
            zw * (ps.p.attrs.at(Attr.Aggression) + 5f) * (if (ps.yc > 0) K.REPEAT else 1f)
        } ?: return
        def.fouls++
        fouler.fouls++
        victim?.let { it.fouled++ }
        val hc = if (di == 0) 0.93f else 1.07f
        val aggr = fouler.p.attrs.at(Attr.Aggression)
        var pY = K.YEL * (0.70f + aggr / 20f * 0.70f) * refStrict * hc * (if (z == 2) 1.12f else 1f)
        if (cynical) pY *= 2.6f
        val pR = K.RED * refStrict * hc * (if (cynical && z == 2) 7f else 1f)
        val c = rf()
        if (c < pR) {
            sendOff(def, fouler, di, true)
        } else if (c < pR + pY) {
            fouler.yc++
            def.yellows++
            fouler.rating -= 0.15f
            stopSecs += 20f
            if (fouler.yc >= 2) {
                add(di, 2, "Second yellow! ${fouler.p.name} (${def.club.name}) is booked again.")
                sendOff(def, fouler, di, false)
            } else {
                val why = if (cynical) "for a cynical foul" else pickOf("for a late challenge", "for a mistimed tackle", "for persistent fouling", "for a reckless lunge", "for pulling back an opponent")
                add(di, 2, "Yellow card for ${fouler.p.name} (${def.club.name}) $why.")
            }
        } else rate(fouler, -0.04f)
        freeKick(att, def, ai, z)
    }

    /** [att]'s player fouls (pushing, handling...), free kick to the defending side. */
    private fun foulAgainst(att: MatchTeam, def: MatchTeam, ai: Int, di: Int, z: Int) {
        val culprit = pickBy(att.onPitch) { ps -> if (ps.slot == Pos.GK) 0f else (ps.p.attrs.at(Attr.Aggression) + 6f) * (if (ps.yc > 0) K.REPEAT else 1f) } ?: return
        att.fouls++
        culprit.fouls++
        rate(culprit, -0.04f)
        val c = rf()
        val pY = 0.07f * refStrict * (if (ai == 0) 0.93f else 1.07f)
        if (c < pY) {
            culprit.yc++
            att.yellows++
            culprit.rating -= 0.12f
            if (culprit.yc >= 2) {
                add(ai, 2, "Second yellow! ${culprit.p.name} (${att.club.name}) is booked again.")
                sendOff(att, culprit, ai, false)
            } else add(ai, 2, "Yellow card for ${culprit.p.name} (${att.club.name}) for a foul in the build-up.")
        }
        turnover(di, if (z == 2) 0 else 1, false)
        stopSecs += 5f
    }

    /** Random off-the-ball and dead-ball fouls that don't come from a contested phase. */
    private fun offBallFouls() {
        for (i in 0..1) {
            if (rf() > 0.035f * K.FOUL) continue
            val tm = teams[i]
            val opp = teams[1 - i]
            val culprit = pickBy(tm.onPitch) { ps -> if (ps.slot == Pos.GK) 0f else (ps.p.attrs.at(Attr.Aggression) + 5f) * (if (ps.yc > 0) K.REPEAT else 1f) } ?: continue
            tm.fouls++
            culprit.fouls++
            val pY = 0.12f * refStrict * (if (i == 0) 0.93f else 1.07f)
            if (rf() < pY) {
                culprit.yc++
                tm.yellows++
                culprit.rating -= 0.12f
                if (culprit.yc >= 2) {
                    add(i, 2, "Second yellow! ${culprit.p.name} (${tm.club.name}) is booked again.")
                    sendOff(tm, culprit, i, false)
                } else add(i, 2, "Yellow card for ${culprit.p.name} (${tm.club.name}) for dissent.")
            }
            if (opp.fouls < 0) return
        }
    }

    private fun sendOff(tm: MatchTeam, ps: PS, idx: Int, straight: Boolean) {
        if (ps.sentOff) return
        ps.sentOff = true
        tm.reds++
        ps.rating -= 1.2f
        tm.onPitch.remove(ps)
        stopSecs += 30f
        if (straight) add(idx, 3, "RED CARD! ${ps.p.name} (${tm.club.name}) is sent off for a ${pickOf("serious foul", "dangerous challenge", "denying a clear goalscoring chance", "moment of madness")}.")
        else add(idx, 3, "${ps.p.name} (${tm.club.name}) must go. They're down to ${tm.onPitch.size}.")
        if (ps.slot == Pos.GK && tm.onPitch.isNotEmpty()) {
            val gkBench = tm.bench.firstOrNull { it.p.pos == Pos.GK }
            val out = tm.onPitch.filter { it.slot != Pos.GK }.minByOrNull { it.p.ovr }
            if (gkBench != null && tm.subs < 5 && out != null) {
                substitute(tm, out, gkBench)
            } else {
                val stand = tm.onPitch.maxByOrNull { it.p.attrs.at(Attr.Reflexes) + it.p.attrs.at(Attr.Handling) }
                if (stand != null) { stand.slot = Pos.GK; stand.recompute() }
            }
        }
        refresh()
    }

    private val injTypes = listOf("Hamstring strain", "Ankle sprain", "Knee ligament", "Calf strain", "Groin strain",
        "Muscle fatigue", "Bruised thigh", "Hip flexor", "Back spasm", "Concussion")

    private fun injuryCheck(tm: MatchTeam, idx: Int) {
        if (tm.onPitch.isEmpty()) return
        val ps = tm.onPitch[rnd.nextInt(tm.onPitch.size)]
        if (ps.injured) return
        val fit = 1.15f - ps.p.attrs.at(Attr.Fitness) / 40f
        val pr = 0.0015f * (0.6f + ps.p.injuryProne / 20f) * (1f + (100f - ps.cond) / 150f) * fit
        if (rf() > pr) return
        ps.injured = true
        val tbl = intArrayOf(1, 1, 1, 2, 2, 2, 3, 3, 4, 5, 6, 8, 12, 20)
        ps.injWeeks = tbl[rnd.nextInt(tbl.size)]
        ps.p.injType = injTypes[rnd.nextInt(injTypes.size)]
        stopSecs += 70f
        add(idx, 5, "${ps.p.name} (${tm.club.name}) goes down injured - ${ps.p.injType}.")
        if (tm.subs < 5 && tm.bench.isNotEmpty()) {
            val inn = bestSub(tm, ps) ?: return
            substitute(tm, ps, inn)
        } else {
            ps.cond = min(ps.cond, 35f)
        }
    }

    // ------------------------------------------------------------------ substitutions

    private fun bestSub(tm: MatchTeam, out: PS): PS? {
        if (tm.bench.isEmpty()) return null
        return tm.bench.maxByOrNull { b ->
            val f = Roles.familiarity(b.p, out.slot)
            Roles.overall(b.p.attrs, out.slot) * f * (0.8f + 0.2f * b.p.condition / 100f)
        }
    }

    private fun aiSubs(tm: MatchTeam, opp: MatchTeam, idx: Int) {
        if (!tm.ai || tm.subs >= 5 || tm.bench.isEmpty()) return
        val minute = minuteNum()
        val tired = tm.onPitch.count { it.slot != Pos.GK && it.cond < 42f }
        if (minute < tm.nextSub && tired == 0) return
        if (half == 1 && minute < 40) return
        val diff = tm.goals - opp.goals
        var out: PS? = null
        var inn: PS? = null
        val outfield = tm.onPitch.filter { it.slot != Pos.GK }
        if (outfield.isEmpty()) return
        fun worn(p: PS): Float = p.cond + 6f * (p.rating - 6f) + (if (p.yc > 0) -4f else 0f)
        if (diff < 0 && minute >= 58) {
            val cand = tm.bench.filter { it.p.pos.group >= 3 || it.p.pos == Pos.AM }
            inn = cand.maxByOrNull { it.p.ovr * (0.8f + 0.2f * it.p.condition / 100f) }
            out = outfield.filter { it.slot.group in 1..2 }.minByOrNull { worn(it) - (if (it.slot == Pos.DM) 8f else 0f) }
        } else if (diff > 0 && minute >= 68) {
            val cand = tm.bench.filter { it.p.pos == Pos.CB || it.p.pos == Pos.DM || it.p.pos == Pos.CM }
            inn = cand.maxByOrNull { it.p.ovr * (0.8f + 0.2f * it.p.condition / 100f) }
            out = outfield.filter { it.slot.group >= 3 || it.slot == Pos.AM }.minByOrNull { worn(it) }
        }
        if (out == null || inn == null) {
            out = outfield.minByOrNull { worn(it) }
            if (out != null) inn = bestSub(tm, out)
        }
        if (out == null || inn == null) { tm.nextSub = minute + 3; return }
        substitute(tm, out, inn)
        tm.nextSub = minute + 5 + rnd.nextInt(9)
    }

    fun substitute(tm: MatchTeam, out: PS, inn: PS): Boolean {
        if (tm.subs >= 5) return false
        val i = tm.onPitch.indexOf(out)
        if (i < 0 || !tm.bench.contains(inn)) return false
        inn.slot = out.slot
        inn.recompute()
        inn.played = true
        inn.enteredAt = minuteNum()
        tm.onPitch[i] = inn
        tm.bench.remove(inn)
        tm.subs++
        stopSecs += 30f
        val idx = if (tm === home) 0 else 1
        add(idx, 4, "Substitution (${tm.club.name}): ${inn.p.name} replaces ${out.p.name}.")
        refresh()
        return true
    }

    // ------------------------------------------------------------------ results

    fun applyToPlayers(rnd2: Random) {
        for (idx in 0..1) {
            val tm = teams[idx]
            val gf = tm.goals
            val ga = teams[1 - idx].goals
            for (ps in tm.everyone) {
                val p = ps.p
                if (!ps.played) { p.lastMinutes = 0; continue }
                var r = ps.rating + (if (gf > ga) 0.25f else if (gf < ga) -0.2f else 0f) + rnd2.gauss().toFloat() * 0.25f
                if (ps.passes >= 12) r += (ps.passesOk.toFloat() / ps.passes - 0.80f) * 1.5f
                r += min(0.4f, ps.tackles * 0.04f + ps.interceptions * 0.03f)
                val defensive = ps.slot == Pos.GK || ps.slot.group == 1 || ps.slot == Pos.DM
                if (defensive) {
                    if (ga == 0) r += 0.3f else r -= min(0.6f, 0.15f * ga)
                }
                r = clampF(r, 3f, 10.0f)
                ps.rating = r
                p.apps++
                p.goals += ps.goals
                p.assists += ps.assists
                p.minutes += ps.mins
                p.ratingSum += r
                p.seasonShots += ps.shots
                p.seasonShotsOnTarget += ps.sot
                p.seasonXg += ps.xg
                p.seasonKeyPasses += ps.keyPasses
                p.seasonTackles += ps.tackles
                p.seasonInterceptions += ps.interceptions
                p.seasonSaves += ps.saves
                p.lastMinutes = ps.mins
                p.condition = max(5f, ps.cond)
                p.form = clampF(p.form * 0.7f + r * 10f * 0.3f, 10f, 100f)
                if (ps.yc >= 1 && !ps.sentOff) {
                    p.yellows += 1
                    if (p.yellows >= 5) { p.suspWeeks = 1; p.yellows = 0 }
                }
                if (ps.sentOff) p.suspWeeks = 1 + (if (rnd2.nextInt(4) == 0) 2 else 0)
                if (ps.injured) p.injWeeks = max(p.injWeeks, ps.injWeeks)
            }
        }
    }

    fun manOfTheMatch(): PS? {
        var best: PS? = null
        for (tm in teams) for (ps in tm.everyone) {
            if (!ps.played) continue
            if (best == null || ps.rating > best.rating) best = ps
        }
        return best
    }

    // exposed for tuning tools
    fun debugAvgDrain(): Float = home.avgCond
}

private fun Boolean.toInt(): Int = if (this) 1 else 0
