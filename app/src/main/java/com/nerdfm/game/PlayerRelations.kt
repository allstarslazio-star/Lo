package com.nerdfm.game

import kotlin.math.roundToInt

/** Deterministic personality and dressing-room/board interactions. Android-free simulation domain. */
object PlayerRelations {
    const val PROMISE_NONE = 0
    const val PROMISE_ROTATION = 1
    const val PROMISE_REGULAR = 2
    const val PROMISE_IMPORTANT = 3
    const val PROMISE_STAR = 4

    fun personality(player: Player): String = when {
        player.ambition >= 15 && player.loyalty <= 7 -> "Driven"
        player.loyalty >= 15 && player.prof >= 12 -> "Loyal professional"
        player.consistency >= 16 && player.prof >= 14 -> "Model professional"
        player.ambition >= 15 -> "Ambitious"
        player.prof <= 7 -> "Mercurial"
        player.loyalty >= 15 -> "Loyal"
        else -> "Balanced"
    }

    fun promisePlayingTime(world: World, playerId: Int, level: Int): Boolean {
        val player = world.players[playerId] ?: return false
        if (player.clubId != world.humanClubId || level !in PROMISE_ROTATION..PROMISE_STAR) return false
        player.playingTimePromise = level
        player.promiseWeeksRemaining = 8
        player.promiseBrokenWeeks = 0
        world.addNews("Playing-time promise", "${player.name} was promised ${promiseName(level).lowercase()} football for the next eight weeks.")
        return true
    }

    fun mediaInteraction(world: World, option: Int): Boolean {
        if (option !in -1..1) return false
        val club = world.human()
        val change = when (option) {
            1 -> 1.5f // confident but measured
            -1 -> -0.5f // cautious answer has limited impact
            else -> 0.5f // calm, neutral response
        }
        club.teamCohesion = clampF(club.teamCohesion + change, 0f, 100f)
        world.managerReputation = clampF(world.managerReputation + if (option == 1) 0.4f else 0.1f, 0f, 100f)
        world.addNews("Media briefing", when (option) {
            1 -> "You backed the squad publicly and challenged the team to deliver."
            -1 -> "You kept expectations measured and avoided putting extra pressure on the squad."
            else -> "You gave a calm, balanced assessment of the team's prospects."
        })
        return true
    }

    fun weeklyUpdate(world: World) {
        for (club in world.clubs) {
            val squad = world.squad(club)
            if (squad.isEmpty()) continue
            val played = squad.count { it.lastMinutes >= 45 }
            val leaders = squad.sortedWith(compareByDescending<Player> { it.attrs[Attr.Leadership.ordinal] }.thenBy { it.id }).take(3)
            val leadership = if (leaders.isEmpty()) 0f else leaders.map { it.attrs[Attr.Leadership.ordinal] }.average().toFloat()
            val morale = squad.map { it.morale }.average().toFloat()
            val cohesionDelta = ((morale - 60f) * 0.025f) + ((leadership - 10f) * 0.04f) + ((played.toFloat() / squad.size) - 0.45f) * 0.5f
            club.teamCohesion = clampF(club.teamCohesion + cohesionDelta, 0f, 100f)
            for (player in squad) {
                if (player.promiseWeeksRemaining <= 0) continue
                val expectedMinutes = when (player.playingTimePromise) {
                    PROMISE_ROTATION -> 20
                    PROMISE_REGULAR -> 35
                    PROMISE_IMPORTANT -> 50
                    PROMISE_STAR -> 65
                    else -> 0
                }
                if (player.lastMinutes < expectedMinutes && player.injWeeks == 0 && player.suspWeeks == 0) {
                    player.promiseBrokenWeeks++
                    player.morale = clampF(player.morale - (0.7f + player.playingTimePromise * 0.25f), 15f, 100f)
                    if (player.promiseBrokenWeeks == 2 && club.id == world.humanClubId) {
                        world.addNews("Player and agent demand", "${player.name} has asked for a meeting with you and their representative because promised playing time has not been delivered.")
                    }
                } else if (player.lastMinutes >= expectedMinutes) {
                    player.promiseBrokenWeeks = 0
                    player.morale = clampF(player.morale + 0.35f, 15f, 100f)
                }
                player.promiseWeeksRemaining--
                if (player.promiseWeeksRemaining == 0) {
                    if (player.promiseBrokenWeeks >= 3) {
                        player.morale = clampF(player.morale - 3f, 15f, 100f)
                        if (club.id == world.humanClubId) world.addNews("Broken playing-time promise", "${player.name} feels let down by the playing-time promise.")
                    }
                    player.playingTimePromise = PROMISE_NONE
                    player.promiseBrokenWeeks = 0
                }
            }
        }
    }

    fun evaluateBoard(world: World) {
        val club = world.human()
        val league = world.leagueOf(club.id)
        val table = league.sortedTable()
        val position = table.indexOfFirst { it.clubId == club.id }.let { if (it < 0) table.size else it + 1 }
        val count = table.size.coerceAtLeast(1)
        val objectiveMet = when (club.boardObjective) {
            1 -> position <= maxOf(1, count / 2)
            2 -> position <= maxOf(1, (count * 2) / 3)
            3 -> position <= maxOf(1, count - 2)
            4 -> club.balance >= 0
            else -> position <= maxOf(1, count * 3 / 4)
        }
        val expected = when (club.boardObjective) { 1 -> 2f; 2 -> 1f; 3 -> 0.5f; 4 -> 0.5f; else -> 0.75f }
        club.boardConfidence = clampF(club.boardConfidence + if (objectiveMet) expected * 0.15f else -0.15f, 0f, 100f)
        world.managerReputation = clampF(world.managerReputation + if (objectiveMet) 0.08f else -0.04f, 0f, 100f)
    }

    fun promiseName(level: Int): String = when (level) {
        PROMISE_ROTATION -> "Rotation"
        PROMISE_REGULAR -> "Regular"
        PROMISE_IMPORTANT -> "Important"
        PROMISE_STAR -> "Star"
        else -> "No promise"
    }

    fun managerProfile(world: World): String = "${world.manager} · reputation ${world.managerReputation.roundToInt()}/100"
}
