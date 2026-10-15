package com.nerdfm.game

import java.io.Serializable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Pos(val label: String, val group: Int) {
    GK("GK", 0), LB("LB", 1), CB("CB", 1), RB("RB", 1), DM("DM", 2), CM("CM", 2),
    LW("LW", 3), RW("RW", 3), AM("AM", 3), ST("ST", 4)
}

enum class Attr(val label: String, val cat: Int) {
    Finishing("Finishing", 0), Passing("Passing", 0), Dribbling("Dribbling", 0), FirstTouch("First Touch", 0),
    Tackling("Tackling", 0), Marking("Marking", 0), Heading("Heading", 0), Crossing("Crossing", 0),
    LongShots("Long Shots", 0), Technique("Technique", 0), SetPieces("Set Pieces", 0),
    Vision("Vision", 1), Decisions("Decisions", 1), Composure("Composure", 1), Positioning("Positioning", 1),
    Anticipation("Anticipation", 1), WorkRate("Work Rate", 1), Determination("Determination", 1),
    Leadership("Leadership", 1), Aggression("Aggression", 1), Flair("Flair", 1), Teamwork("Teamwork", 1),
    OffTheBall("Off the Ball", 1),
    Pace("Pace", 2), Acceleration("Acceleration", 2), Stamina("Stamina", 2), Strength("Strength", 2),
    Agility("Agility", 2), Balance("Balance", 2), Jumping("Jumping", 2), Fitness("Nat. Fitness", 2),
    Reflexes("Reflexes", 3), Handling("Handling", 3), Command("Command of Area", 3),
    OneOnOnes("One on Ones", 3), Kicking("Kicking", 3)
}

val ATTRS: Array<Attr> = Attr.values()
val NATTR: Int = ATTRS.size


object Roles {
    private fun build(gk: Boolean, vararg p: Pair<Attr, Float>): FloatArray {
        val a = FloatArray(NATTR) { i ->
            val isGkAttr = ATTRS[i].cat == 3
            if (gk) (if (isGkAttr) 0.2f else 0.05f) else (if (isGkAttr) 0f else 0.12f)
        }
        for (pr in p) a[pr.first.ordinal] = pr.second
        return a
    }

    private fun line(vararg p: Pair<Attr, Float>): FloatArray {
        val a = FloatArray(NATTR)
        for (pr in p) a[pr.first.ordinal] = pr.second
        return a
    }

    val weights: Map<Pos, FloatArray> = mapOf(
        Pos.ST to build(false, Attr.Finishing to 3f, Attr.OffTheBall to 2.5f, Attr.Composure to 1.5f, Attr.Pace to 1.5f,
            Attr.Acceleration to 1.2f, Attr.Heading to 1f, Attr.FirstTouch to 1.2f, Attr.Dribbling to 1f,
            Attr.Strength to 0.8f, Attr.Technique to 0.8f, Attr.Anticipation to 0.8f, Attr.Decisions to 0.6f, Attr.Balance to 0.4f),
        Pos.AM to build(false, Attr.Passing to 2.5f, Attr.Vision to 2.5f, Attr.Technique to 2f, Attr.Dribbling to 1.5f,
            Attr.FirstTouch to 1.5f, Attr.Decisions to 1.2f, Attr.Flair to 1f, Attr.OffTheBall to 1.2f,
            Attr.Finishing to 1f, Attr.LongShots to 0.8f, Attr.Composure to 1f, Attr.Agility to 0.6f),
        Pos.LW to winger(), Pos.RW to winger(),
        Pos.CM to build(false, Attr.Passing to 2.5f, Attr.Vision to 1.5f, Attr.Decisions to 1.5f, Attr.Teamwork to 1.5f,
            Attr.WorkRate to 1.5f, Attr.Stamina to 1.5f, Attr.Tackling to 1.2f, Attr.Positioning to 1f,
            Attr.FirstTouch to 1.2f, Attr.Technique to 1f, Attr.Anticipation to 1f, Attr.Composure to 0.8f),
        Pos.DM to build(false, Attr.Tackling to 2.5f, Attr.Positioning to 2.2f, Attr.Anticipation to 2f, Attr.Marking to 1.5f,
            Attr.Teamwork to 1.2f, Attr.Passing to 1.5f, Attr.Decisions to 1.5f, Attr.Strength to 1f,
            Attr.WorkRate to 1.2f, Attr.Stamina to 1.2f, Attr.Aggression to 0.8f, Attr.Composure to 0.8f),
        Pos.CB to build(false, Attr.Marking to 2.5f, Attr.Tackling to 2.5f, Attr.Heading to 2.2f, Attr.Positioning to 2.2f,
            Attr.Strength to 1.8f, Attr.Jumping to 1.5f, Attr.Anticipation to 1.5f, Attr.Decisions to 1f,
            Attr.Composure to 1f, Attr.Pace to 1.2f, Attr.Aggression to 0.6f, Attr.Determination to 0.6f),
        Pos.LB to fullback(), Pos.RB to fullback(),
        Pos.GK to build(true, Attr.Reflexes to 3.5f, Attr.Handling to 3f, Attr.Command to 2f, Attr.OneOnOnes to 2f,
            Attr.Positioning to 1.5f, Attr.Decisions to 1f, Attr.Composure to 1f, Attr.Kicking to 1f,
            Attr.Anticipation to 1f, Attr.Agility to 1.5f, Attr.Jumping to 0.8f)
    )

    private fun winger(): FloatArray = build(false, Attr.Pace to 2f, Attr.Acceleration to 2f, Attr.Dribbling to 2.2f,
        Attr.Crossing to 1.8f, Attr.Technique to 1.2f, Attr.Agility to 1f, Attr.OffTheBall to 1.2f, Attr.Finishing to 0.8f,
        Attr.Stamina to 0.8f, Attr.WorkRate to 0.6f, Attr.FirstTouch to 1f)

    private fun fullback(): FloatArray = build(false, Attr.Tackling to 2f, Attr.Marking to 1.5f, Attr.Pace to 1.8f,
        Attr.Crossing to 1.5f, Attr.Stamina to 1.8f, Attr.Positioning to 1.5f, Attr.Acceleration to 1.2f,
        Attr.WorkRate to 1.2f, Attr.Dribbling to 0.8f, Attr.Anticipation to 1f, Attr.Teamwork to 0.8f)

    // Line scores used by the match engine
    val ATT_W = line(Attr.Finishing to 2.5f, Attr.OffTheBall to 2f, Attr.Dribbling to 1.5f, Attr.Pace to 1.5f,
        Attr.Acceleration to 1f, Attr.Technique to 1.5f, Attr.Passing to 1.2f, Attr.Vision to 1.2f,
        Attr.FirstTouch to 1.2f, Attr.Composure to 1.2f, Attr.Crossing to 0.8f, Attr.LongShots to 0.6f,
        Attr.Flair to 0.5f, Attr.Heading to 0.5f, Attr.Decisions to 0.8f)
    val MID_W = line(Attr.Passing to 2.5f, Attr.Vision to 2f, Attr.Decisions to 1.5f, Attr.Teamwork to 1.5f,
        Attr.WorkRate to 1.5f, Attr.Stamina to 1.2f, Attr.Tackling to 1.2f, Attr.Positioning to 1.2f,
        Attr.FirstTouch to 1.2f, Attr.Technique to 1.2f, Attr.Anticipation to 1f, Attr.Composure to 1f,
        Attr.Strength to 0.5f, Attr.Aggression to 0.5f, Attr.Balance to 0.3f)
    val DEF_W = line(Attr.Tackling to 2.5f, Attr.Marking to 2.5f, Attr.Positioning to 2.2f, Attr.Heading to 1.8f,
        Attr.Anticipation to 1.8f, Attr.Strength to 1.2f, Attr.Pace to 1.2f, Attr.Jumping to 1f, Attr.Decisions to 1f,
        Attr.Composure to 0.8f, Attr.Aggression to 0.6f, Attr.Determination to 0.6f, Attr.Teamwork to 0.6f,
        Attr.Acceleration to 0.5f)

    // How much each slot contributes to each team line
    val ATT_ROLE: Map<Pos, Float> = mapOf(Pos.GK to 0f, Pos.CB to 0.05f, Pos.LB to 0.25f, Pos.RB to 0.25f, Pos.DM to 0.12f,
        Pos.CM to 0.4f, Pos.LW to 0.85f, Pos.RW to 0.85f, Pos.AM to 0.95f, Pos.ST to 1.2f)
    val MID_ROLE: Map<Pos, Float> = mapOf(Pos.GK to 0.02f, Pos.CB to 0.15f, Pos.LB to 0.3f, Pos.RB to 0.3f, Pos.DM to 0.9f,
        Pos.CM to 1f, Pos.LW to 0.35f, Pos.RW to 0.35f, Pos.AM to 0.75f, Pos.ST to 0.15f)
    val DEF_ROLE: Map<Pos, Float> = mapOf(Pos.GK to 0.05f, Pos.CB to 1f, Pos.LB to 0.7f, Pos.RB to 0.7f, Pos.DM to 0.8f,
        Pos.CM to 0.4f, Pos.LW to 0.12f, Pos.RW to 0.12f, Pos.AM to 0.08f, Pos.ST to 0.04f)

    fun overall(attrs: IntArray, pos: Pos): Float {
        val w = weights[pos]!!
        var s = 0f
        var t = 0f
        for (i in 0 until NATTR) {
            s += w[i] * attrs[i]
            t += w[i]
        }
        return s / t * 5f
    }

    fun lineScore(attrs: IntArray, w: FloatArray): Float {
        var s = 0f
        var t = 0f
        for (i in 0 until NATTR) {
            s += w[i] * attrs[i]
            t += w[i]
        }
        return s / t * 5f
    }

    fun familiarity(p: Player, slot: Pos): Float {
        if (p.pos == slot) return 1f
        if (p.pos2 == slot) return 0.96f
        if (slot == Pos.GK || p.pos == Pos.GK) return 0.25f
        val d = abs(p.pos.group - slot.group)
        return when (d) {
            0 -> 0.93f
            1 -> 0.85f
            2 -> 0.72f
            else -> 0.6f
        }
    }
}

class Formation(val name: String, val slots: List<Pos>, val attM: Float, val midM: Float, val defM: Float) : Serializable

object Formations {
    val list: List<Formation> = listOf(
        Formation("4-4-2", listOf(Pos.GK, Pos.LB, Pos.CB, Pos.CB, Pos.RB, Pos.LW, Pos.CM, Pos.CM, Pos.RW, Pos.ST, Pos.ST), 1.00f, 1.00f, 1.00f),
        Formation("4-3-3", listOf(Pos.GK, Pos.LB, Pos.CB, Pos.CB, Pos.RB, Pos.CM, Pos.DM, Pos.CM, Pos.LW, Pos.RW, Pos.ST), 1.03f, 1.02f, 0.97f),
        Formation("4-2-3-1", listOf(Pos.GK, Pos.LB, Pos.CB, Pos.CB, Pos.RB, Pos.DM, Pos.DM, Pos.LW, Pos.AM, Pos.RW, Pos.ST), 1.01f, 1.03f, 1.00f),
        Formation("3-5-2", listOf(Pos.GK, Pos.CB, Pos.CB, Pos.CB, Pos.LB, Pos.CM, Pos.DM, Pos.CM, Pos.RB, Pos.ST, Pos.ST), 1.00f, 1.05f, 0.95f),
        Formation("5-3-2", listOf(Pos.GK, Pos.LB, Pos.CB, Pos.CB, Pos.CB, Pos.RB, Pos.CM, Pos.DM, Pos.CM, Pos.ST, Pos.ST), 0.95f, 0.98f, 1.06f),
        Formation("4-1-4-1", listOf(Pos.GK, Pos.LB, Pos.CB, Pos.CB, Pos.RB, Pos.DM, Pos.LW, Pos.CM, Pos.CM, Pos.RW, Pos.ST), 0.97f, 1.02f, 1.03f),
        Formation("3-4-3", listOf(Pos.GK, Pos.CB, Pos.CB, Pos.CB, Pos.LB, Pos.CM, Pos.CM, Pos.RB, Pos.LW, Pos.RW, Pos.ST), 1.06f, 1.00f, 0.92f)
    )
}

class Tactics : Serializable {
    var formation = 0
    var mentality = 0   // -2 .. 2
    var tempo = 1       // 0 low, 1 normal, 2 high
    var pressing = 1    // 0 low, 1 normal, 2 high
    var width = 1       // 0 narrow, 1 normal, 2 wide
    var line = 1        // 0 deep, 1 normal, 2 high
    var passing = 1     // 0 short, 1 mixed, 2 direct

    fun copyFrom(o: Tactics) {
        formation = o.formation; mentality = o.mentality; tempo = o.tempo
        pressing = o.pressing; width = o.width; line = o.line; passing = o.passing
    }
}

class Player(
    val id: Int,
    var name: String,
    val nation: Int,
    var age: Int,
    var pos: Pos,
    var pos2: Pos?,
    val attrs: IntArray,
    var pa: Int,
    val consistency: Int,
    val injuryProne: Int,
    val prof: Int,
    val ambition: Int,
    val loyalty: Int
) : Serializable {
    var clubId = -1
    var wage = 0
    var contractEnd = 2027
    var condition = 100f
    var sharpness = 75f
    var trainingFocus = "Balanced"
    var trainingIntensity = 1 // 0 light, 1 normal, 2 intensive
    var morale = 70f
    var form = 60f
    var injWeeks = 0
    var injType = ""
    var suspWeeks = 0
    var yellows = 0
    var listed = false
    var ovr = 0
    var apps = 0
    var goals = 0
    var assists = 0
    var minutes = 0
    var ratingSum = 0f
    var seasonShots = 0
    var seasonShotsOnTarget = 0
    var seasonXg = 0.0
    var seasonKeyPasses = 0
    var seasonTackles = 0
    var seasonInterceptions = 0
    var seasonSaves = 0
    var lastMinutes = 0
    var seasonsAtClub = 0
    /** Persistent, compact reinforcement-learning table; no neural runtime required. */
    val learning = PlayerLearningProfile()

    fun refresh() {
        ovr = Roles.overall(attrs, pos).roundToInt()
    }

    fun ovrAt(p: Pos): Int = (Roles.overall(attrs, p) * Roles.familiarity(this, p)).roundToInt()

    fun avgRating(): Float = if (apps == 0) 0f else ratingSum / apps

    fun value(): Long {
        val eff: Double = if (age <= 23) ovr * 0.65 + max(pa, ovr) * 0.35 else ovr.toDouble()
        var v = 400000.0 * exp((eff - 40.0) * 0.115)
        val f = when {
            age <= 20 -> 1.25
            age <= 24 -> 1.1
            age <= 28 -> 1.0
            age == 29 -> 0.85
            age == 30 -> 0.7
            age == 31 -> 0.55
            age == 32 -> 0.4
            else -> 0.25
        }
        v *= f
        return max(20000L, (v / 1000.0).toLong() * 1000L)
    }

    fun wageDemand(): Int {
        val w = value() * 0.0016
        return max(300, (w / 50.0).toInt() * 50)
    }

    fun stars(): Float = pa / 20f
}

class Club(
    val id: Int,
    val name: String,
    var tier: Int,
    var rep: Int,
    val stadiumBase: Int,
    val colorA: Long,
    val colorB: Long
) : Serializable {
    var balance = 0L
    var revenue = 0L           // annual
    var wageBudget = 0L        // weekly
    var transferBudget = 0L
    var facilities = 8         // 1..20
    var youth = 8
    var scout = 8
    var human = false
    /** Evolutionary policy genes used by AI clubs between seasons. */
    val managerGenome = ManagerGenome()
    val players = ArrayList<Int>()
    val tactics = Tactics()
    val lineup = IntArray(11) { -1 }
    val bench = IntArray(7) { -1 }
    var autoPick = true
    var boardConfidence = 60f
    var weeklyIncome = 0L
    var weeklyExpense = 0L
    var seasonStartBalance = 0L
    var lastPts = 0
    var lastGate = 0L
    val coaches = ArrayList<Coach>()

    fun capacity(): Int = stadiumBase
}

class TableRow(val clubId: Int) : Serializable {
    var p = 0
    var w = 0
    var d = 0
    var l = 0
    var gf = 0
    var ga = 0
    var pts = 0
    val gd: Int get() = gf - ga
    val form = StringBuilder()
}

class Fixture(val round: Int, val home: Int, val away: Int) : Serializable {
    var played = false
    var hg = 0
    var ag = 0
    var report = ""
}

class League(val id: Int, val name: String, val tier: Int) : Serializable {
    val clubIds = ArrayList<Int>()
    val fixtures = ArrayList<Fixture>()
    val table = HashMap<Int, TableRow>()

    fun sortedTable(): List<TableRow> =
        table.values.sortedWith(
            compareByDescending<TableRow> { it.pts }.thenByDescending { it.gd }.thenByDescending { it.gf }.thenBy { it.clubId }
        )
}

class News(val year: Int, val week: Int, val title: String, val body: String) : Serializable {
    var read = false
}

class Offer(val playerId: Int, val fromClub: Int, var fee: Long, val expiresWeek: Int) : Serializable

class Coach(
    val id: Int,
    var name: String,
    var specialty: String,
    var rating: Int,
    var wage: Int,
    var reputation: Int = 50
) : Serializable

class Shortlisted(val playerId: Int) : Serializable

fun money(v: Long): String {
    val a = abs(v)
    val s = when {
        a >= 1_000_000L -> String.format("%.2fM", a / 1_000_000.0)
        a >= 1_000L -> String.format("%.0fK", a / 1_000.0)
        else -> a.toString()
    }
    return (if (v < 0) "-" else "") + "\u20A5" + s
}

fun clampI(v: Int, lo: Int, hi: Int): Int = max(lo, min(hi, v))
fun clampF(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))
