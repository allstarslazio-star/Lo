package com.nerdfm.game

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min

/** What the squad works on in the week's sessions (one focus at a time; the choice is the cost). */
enum class PrepFocus(val label: String, val blurb: String) {
    BALANCED("Balanced week", "No special preparation. Familiarity and set pieces drift slowly."),
    SYSTEM("Drill the system", "Doubles how fast the team gets used to the current tactics. Costs a little freshness."),
    SET_PIECES("Set pieces", "Sets up corners, free kicks and throw-ins. A set-piece coach makes it count more. Costs a little freshness."),
    OPPOSITION("Study the opponent", "Uses the opposition report for this week's match: a small boost to sharpness and morale. Needs a fixture.")
}

/** Factor group I: tactical familiarity, set pieces and match preparation. Human club only; saved lazily (format 11). */
class PrepState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var focus = PrepFocus.BALANCED.ordinal
    /** 0..100: how well the team knows the tactics it plays. */
    var familiarity = 70f
    /** 0..100: how well drilled the set pieces are. */
    var setPieces = 50f
    /** The tactic signature the familiarity belongs to. */
    var signature = -1
    /** Number of weeks the same signature has been kept. */
    var stableWeeks = 0
    var lastChangeNote = ""
    /** Season log of tactical changes (for the report). */
    var seasonChanges = 0
}

object Preparation {
    const val FAMILIARITY_SWING = 1.5f
    const val SET_PIECE_SWING = 1.0f
    const val OPPOSITION_SHARPNESS = 0.9f
    const val OPPOSITION_MORALE = 0.5f
    const val FOCUS_FATIGUE = 0.4f
    const val FORMATION_PENALTY = 22f
    const val OTHER_PENALTY = 5f

    fun state(w: World): PrepState {
        val e = w.eco()
        return e.prep ?: PrepState().also { e.prep = it }
    }

    fun focusOf(w: World): PrepFocus = PrepFocus.values()[state(w).focus.coerceIn(0, PrepFocus.values().size - 1)]

    fun setFocus(w: World, f: PrepFocus) { state(w).focus = f.ordinal }

    /** One number per tactical choice; any difference between two signatures is a change the squad has to learn. */
    fun signatureOf(t: Tactics): Int =
        ((((t.formation * 5 + t.mentality + 2) * 3 + t.tempo) * 3 + t.pressing) * 3 + t.width) * 9 + t.line * 3 + t.passing

    fun formationOf(signature: Int): Int = signature / 243 / 5

    /** Assistant manager and attackers/defenders coaches teach tactics: about -1..+1 around a normal club. */
    fun teachingQuality(w: World, clubId: Int): Float =
        (0.6f * Staffing.centered(w, clubId, StaffRole.ASSISTANT_MANAGER) +
            0.2f * Staffing.centered(w, clubId, StaffRole.COACH_ATTACKING) +
            0.2f * Staffing.centered(w, clubId, StaffRole.COACH_DEFENDING)).coerceIn(-1f, 1f)

    fun setPieceQuality(w: World, clubId: Int): Float = Staffing.centered(w, clubId, StaffRole.COACH_SET_PIECES).coerceIn(-1f, 1f)

    /** Weekly update, before the week's minutes are reset. Detects tactical changes by comparing with the signature kept in the save. */
    fun weekly(w: World) {
        val me = w.human()
        val s = state(w)
        val sig = signatureOf(me.tactics)
        val teach = teachingQuality(w, me.id)
        val focus = focusOf(w)
        if (s.signature == -1) {
            s.signature = sig; s.stableWeeks = 0
        } else if (s.signature != sig) {
            // the exact change is unknown after a reload, so the penalty is derived from how far the signatures differ in formation
            val formationChanged = formationOf(s.signature) != formationOf(sig)
            val hit = (if (formationChanged) FORMATION_PENALTY else 0f) + OTHER_PENALTY
            s.familiarity = max(15f, s.familiarity - hit * (1.15f - 0.3f * teach))
            s.signature = sig; s.stableWeeks = 0
            s.seasonChanges++
            s.lastChangeNote = if (formationChanged) "New formation: the players are still learning it." else "Tactical tweak: a little settling-in needed."
        } else {
            s.stableWeeks++
            val gain = (1.6f + 0.9f * teach) * (if (focus == PrepFocus.SYSTEM) 2f else 1f)
            s.familiarity = min(100f, s.familiarity + max(0.4f, gain))
        }
        // set pieces: slow drift towards a ceiling set by the coach, faster when drilled
        val ceiling = 55f + 35f * setPieceQuality(w, me.id)
        val rate = if (focus == PrepFocus.SET_PIECES) 4f else 0.8f
        s.setPieces = if (s.setPieces < ceiling) min(ceiling, s.setPieces + rate) else max(ceiling, s.setPieces - 0.5f)
        s.setPieces = s.setPieces.coerceIn(0f, 100f)
        // the focus lasts one week
        s.focus = PrepFocus.BALANCED.ordinal
    }

    fun familiarityFactor(w: World): Float {
        val s = state(w)
        return ((s.familiarity - 60f) / 40f * FAMILIARITY_SWING).coerceIn(-2f, FAMILIARITY_SWING)
    }

    fun setPieceFactor(w: World): Float = ((state(w).setPieces - 50f) / 50f * SET_PIECE_SWING).coerceIn(-1f, SET_PIECE_SWING)

    /** The opposition boost scales with the club's data quality (0.5..1). */
    fun oppositionBoost(w: World): Float = 0.5f + 0.5f * (Analytics.humanLevel(w) / 100f)

    fun label(v: Float): String = when {
        v >= 85f -> "Excellent"; v >= 70f -> "Good"; v >= 50f -> "Settling"; v >= 30f -> "Poor"; else -> "Very poor"
    }

    fun summary(w: World): String {
        val s = state(w)
        return "Tactical familiarity ${s.familiarity.toInt()} (${label(s.familiarity)}), set pieces ${s.setPieces.toInt()}, same tactics for ${s.stableWeeks} weeks"
    }

    fun seasonReview(w: World) { state(w).seasonChanges = 0 }

    fun repair(w: World) {
        val s = w.ecosystem?.prep ?: return
        s.familiarity = if (s.familiarity.isNaN()) 70f else s.familiarity.coerceIn(0f, 100f)
        s.setPieces = if (s.setPieces.isNaN()) 50f else s.setPieces.coerceIn(0f, 100f)
        if (s.focus < 0 || s.focus >= PrepFocus.values().size) s.focus = 0
        if (s.stableWeeks < 0) s.stableWeeks = 0
    }
}

/** Preparation reaches matches only through the capped sharpness, morale and condition channels. */
object PrepFactors : FactorProvider {
    override fun factors(w: World, club: Club, ctx: MatchContext): List<Factor> {
        if (club.id != w.humanClubId) return emptyList()
        val out = ArrayList<Factor>(4)
        out += Factor("Tactical familiarity", Channel.SHARPNESS, Preparation.familiarityFactor(w))
        out += Factor("Set pieces", Channel.SHARPNESS, Preparation.setPieceFactor(w))
        val f = Preparation.focusOf(w)
        if (ctx.opponent != null && f == PrepFocus.OPPOSITION) {
            val b = Preparation.oppositionBoost(w)
            out += Factor("Opposition study", Channel.SHARPNESS, Preparation.OPPOSITION_SHARPNESS * b)
            out += Factor("Opposition study", Channel.MORALE, Preparation.OPPOSITION_MORALE * b)
        }
        if (f == PrepFocus.SYSTEM || f == PrepFocus.SET_PIECES) out += Factor("Extra sessions", Channel.CONDITION, -Preparation.FOCUS_FATIGUE)
        return out
    }
}
