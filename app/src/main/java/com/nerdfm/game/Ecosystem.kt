package com.nerdfm.game

import java.io.Serializable

/** Who a factor is being asked about: a match (home/away, opponent) or, with a null opponent, the weekly cycle. */
class MatchContext(val home: Boolean, val opponent: Club?) {
    companion object { val WEEKLY = MatchContext(true, null) }
}

/**
 * Every ecosystem system (staff, medical, fans, pitch, ...) contributes through one of these. A provider must be
 * pure and deterministic: no clock, no unseeded randomness, no mutation of the world.
 */
fun interface FactorProvider {
    fun factors(w: World, club: Club, ctx: MatchContext): List<Factor>
}

/**
 * Per-player match-day condition adjustment (overload, ...). Same rules as [FactorProvider]: pure and deterministic.
 * Each value is clamped to the per-factor condition cap and the sum with the club's factors to the total cap.
 */
fun interface PlayerFactorProvider {
    fun conditionDelta(w: World, club: Club, p: Player, ctx: MatchContext): Float
}

/** Deltas that were really applied to one player (after clamping), so they can be taken off again exactly. */
class AppliedDelta(val condition: Float, val sharpness: Float, val morale: Float) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

/**
 * Club Ecosystem state. Created lazily by World.eco(); older saves simply have World.ecosystem == null.
 * Later milestones add their own sections here.
 */
class EcosystemState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }

    /** Deltas currently applied for a match that has begun but not ended; empty between matches (and saved empty). */
    val applied = HashMap<Int, AppliedDelta>()

    // Sections added per milestone. All are null in older saves and created lazily by their owning object.
    /** M1: staff framework. */
    var staff: StaffState? = null
    /** M1: owner, chairman, director of football, objectives, manager contract. */
    var board: BoardState? = null
    /** M1: homegrown players, culture, academy minutes. */
    var identity: IdentityState? = null
    /** M2: player workloads, injury history, rehab policy. */
    var medical: MedicalState? = null
    /** M4: languages, culture, relationships, personal events. */
    var people: PeopleState? = null
    /** M5: fans, stadium, pitch, facilities, media. */
    var env: EnvState? = null
    /** M6: cups, continental competition, international windows, travel. */
    var calendar: CalendarState? = null
}

/**
 * Gameplay hooks of the Club Ecosystem. The match engine is never edited: modifiers are applied to Player inputs
 * after MatchTeams.build and taken off again right after MatchEngine.applyToPlayers, so they never leak into the
 * career state. With no providers registered every hook is an exact no-op (proved by EcosystemHookTest).
 */
object Ecosystem {
    /** Safe-mode switch: when false no modifier is applied (stale ones are still removed). */
    @Volatile var enabled = true

    /** Registration point. Milestones M1..M7 add their providers here; tests swap the list via [withProviders]. */
    @Volatile internal var providers: List<FactorProvider> = listOf(StaffFactors, BoardFactors, CultureFactors, PeopleFactors, EnvFactors, CalendarFactors, RefereeFactors)

    /** Per-player providers (M2 onwards). */
    @Volatile internal var playerProviders: List<PlayerFactorProvider> = listOf(MedicalLoadFactors)

    internal fun <T> withProviders(list: List<FactorProvider>, players: List<PlayerFactorProvider> = emptyList(), block: () -> T): T {
        val previous = providers
        val previousPlayers = playerProviders
        providers = list
        playerProviders = players
        try { return block() } finally { providers = previous; playerProviders = previousPlayers }
    }

    fun modifiersFor(w: World, club: Club, ctx: MatchContext): ClubModifiers {
        val list = providers
        if (!enabled || list.isEmpty()) return ClubModifiers.NEUTRAL
        val factors = ArrayList<Factor>()
        for (p in list) factors.addAll(p.factors(w, club, ctx))
        return ClubModifiers.combine(factors)
    }

    /** Call after both teams were built and before the engine runs. Removes anything left by an abandoned match first. */
    fun beginMatch(w: World, home: MatchTeam, away: MatchTeam) {
        endMatch(w)
        apply(w, home, MatchContext(true, away.club))
        apply(w, away, MatchContext(false, home.club))
    }

    /** Call right after MatchEngine.applyToPlayers. Idempotent: a second call, or a call with nothing applied, does nothing. */
    fun endMatch(w: World) {
        val state = w.ecosystem ?: return
        if (state.applied.isEmpty()) return
        for ((id, d) in state.applied) {
            val p = w.players[id] ?: continue
            p.condition = (p.condition - d.condition).coerceIn(5f, 100f)
            p.sharpness = (p.sharpness - d.sharpness).coerceIn(20f, 100f)
            p.morale = (p.morale - d.morale).coerceIn(15f, 100f)
        }
        state.applied.clear()
    }

    /** Start of Game.completeWeek (before players' minutes are reset): removes stale match modifiers, runs weekly systems. */
    fun preWeek(w: World) {
        endMatch(w)
        if (!enabled) return
        Identity.weekly(w)
        Staffing.weekly(w)
        BoardRules.weekly(w)
        MedicalRules.weekly(w)
        People.weekly(w)
        ClubEnvRules.weekly(w)
    }

    /** End of Game.completeWeek, after any season rollover and sacking. */
    fun postWeek(w: World) {
        if (!enabled) return
        BoardRules.afterWeek(w)
        MedicalRules.prune(w)
        People.prune(w)
        ClubEnvRules.prune(w)
    }

    /** Start of the season rollover, tables still intact. */
    fun seasonReview(w: World) {
        if (!enabled) return
        Staffing.seasonReview(w)
        BoardRules.seasonReview(w)
        Identity.seasonReview(w)
        MedicalRules.seasonReview(w)
        ClubEnvRules.seasonReview(w)
        FixtureCalendar.seasonReview(w)
        NameForge.fixDuplicates(w)
    }

    /** Per-club multiplier on the weekly chance of attribute progression (staff quality). All 1.0 when disabled. */
    fun trainingMultipliers(w: World): FloatArray {
        val out = FloatArray(w.clubs.size) { 1f }
        if (!enabled) return out
        for (c in w.clubs) out[c.id] = Staffing.trainingMultiplier(w, c.id) * People.trainingBoost(w, c.id) * ClubEnvRules.trainingBoost(w, c.id)
        return out
    }

    /** Per-club multiplier on the chance of the intensive-training injury (medical staff). All 1.0 when disabled. */
    fun trainingInjuryMultipliers(w: World): FloatArray {
        val out = FloatArray(w.clubs.size) { 1f }
        if (!enabled) return out
        for (c in w.clubs) out[c.id] = MedicalRules.trainingInjuryMultiplier(w, c.id)
        return out
    }

    /** Per-club multiplier on this week's condition recovery (index = club id). All 1.0 without providers. */
    fun weeklyRecovery(w: World): FloatArray {
        val out = FloatArray(w.clubs.size) { 1f }
        if (!enabled || providers.isEmpty()) return out
        for (c in w.clubs) out[c.id] = modifiersFor(w, c, MatchContext.WEEKLY).recoveryMultiplier
        return out
    }

    private fun apply(w: World, team: MatchTeam, ctx: MatchContext) {
        if (!enabled) return
        val m = modifiersFor(w, team.club, ctx)
        val perPlayer = playerProviders
        if (m.isNeutral && perPlayer.isEmpty()) return
        val state = w.eco()
        for (ps in team.xi + team.bench) {
            val p = ps.p
            if (state.applied.containsKey(p.id)) continue
            var dc = m.condition
            for (pr in perPlayer) {
                val v = pr.conditionDelta(w, team.club, p, ctx)
                if (!v.isNaN()) dc += v.coerceIn(-ModifierCaps.CONDITION_PER_FACTOR, ModifierCaps.CONDITION_PER_FACTOR)
            }
            dc = dc.coerceIn(-ModifierCaps.CONDITION_TOTAL, ModifierCaps.CONDITION_TOTAL)
            if (dc == 0f && m.sharpness == 0f && m.morale == 0f) continue
            val c = (p.condition + dc).coerceIn(0f, 100f)
            val s = (p.sharpness + m.sharpness).coerceIn(20f, 100f)
            val mo = (p.morale + m.morale).coerceIn(15f, 100f)
            val delta = AppliedDelta(c - p.condition, s - p.sharpness, mo - p.morale)
            p.condition = c
            ps.cond += delta.condition
            p.sharpness = s
            p.morale = mo
            state.applied[p.id] = delta
        }
    }
}
