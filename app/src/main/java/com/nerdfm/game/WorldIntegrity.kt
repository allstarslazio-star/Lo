package com.nerdfm.game

/** Pure Kotlin integrity checks shared by long-season tests and release diagnostics. */
object WorldIntegrity {
    data class Issue(val code: String, val detail: String)

    /** Returns every detected invariant violation without mutating the supplied career. */
    fun inspect(world: World): List<Issue> {
        val issues = ArrayList<Issue>()
        if (world.clubs.isEmpty()) issues += Issue("clubs.empty", "World contains no clubs")
        if (world.leagues.isEmpty()) issues += Issue("leagues.empty", "World contains no leagues")
        if (world.humanClubId !in world.clubs.indices) {
            issues += Issue("human.club", "Human club id ${world.humanClubId} is outside the club list")
        }
        for ((id, player) in world.players) {
            if (id != player.id) issues += Issue("player.key", "Player map key $id differs from player id ${player.id}")
            if (player.age !in 15..50) issues += Issue("player.age", "${player.name} has invalid age ${player.age}")
            if (player.attrs.size != NATTR) issues += Issue("player.attributes", "${player.name} has ${player.attrs.size} attributes; expected $NATTR")
            else if (player.attrs.any { it !in 1..20 }) issues += Issue("player.attribute.range", "${player.name} has an attribute outside 1..20")
            if (player.pa !in 1..100) issues += Issue("player.potential", "${player.name} has invalid potential ${player.pa}")
            if (player.condition !in 0f..100f) issues += Issue("player.condition", "${player.name} has condition ${player.condition}")
            if (player.morale !in 0f..100f) issues += Issue("player.morale", "${player.name} has morale ${player.morale}")
            if (player.clubId !in -1 until world.clubs.size) issues += Issue("player.club", "${player.name} references missing club ${player.clubId}")
            if (player.clubId >= 0 && world.clubs[player.clubId].players.count { it == player.id } != 1) {
                issues += Issue("player.membership", "${player.name} must occur exactly once in club ${player.clubId}")
            }
        }
        for (club in world.clubs) {
            if (club.balance < -9_000_000_000_000L) issues += Issue("club.balance", "${club.name} balance is outside supported range")
            if (club.players.size != club.players.toSet().size) issues += Issue("club.duplicate-player", "${club.name} contains duplicate player ids")
            for (id in club.players) {
                val player = world.players[id]
                if (player == null) issues += Issue("club.missing-player", "${club.name} references missing player $id")
                else if (player.clubId != club.id) issues += Issue("club.player-owner", "${player.name} is listed by ${club.name} but belongs to ${player.clubId}")
            }
        }
        for (league in world.leagues) {
            if (league.clubIds.toSet().size != league.clubIds.size) issues += Issue("league.duplicate-club", "${league.name} has duplicate club ids")
            for (id in league.clubIds) if (id !in world.clubs.indices) issues += Issue("league.missing-club", "${league.name} references missing club $id")
            val seen = HashSet<Pair<Int, Int>>()
            for (fixture in league.fixtures) {
                if (fixture.home !in world.clubs.indices || fixture.away !in world.clubs.indices || fixture.home == fixture.away) {
                    issues += Issue("fixture.teams", "${league.name} has invalid fixture ${fixture.home}-${fixture.away}")
                }
                if (fixture.round < 1) issues += Issue("fixture.round", "${league.name} has fixture in round ${fixture.round}")
                if (fixture.played && (fixture.hg < 0 || fixture.ag < 0 || fixture.hg > 20 || fixture.ag > 20)) {
                    issues += Issue("fixture.score", "${league.name} has invalid score ${fixture.hg}-${fixture.ag}")
                }
                if (fixture.home != fixture.away && !seen.add(fixture.round to (fixture.home * 10_000 + fixture.away))) {
                    issues += Issue("fixture.duplicate", "${league.name} has a duplicate fixture in round ${fixture.round}")
                }
            }
        }
        return issues
    }

    fun requireValid(world: World) {
        val issues = inspect(world)
        require(issues.isEmpty()) {
            "World integrity failed (${issues.size} issue(s)): " + issues.take(5).joinToString { "${it.code}: ${it.detail}" }
        }
    }
}
