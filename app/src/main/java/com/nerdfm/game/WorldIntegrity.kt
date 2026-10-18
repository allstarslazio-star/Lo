package com.nerdfm.game

/**
 * Pure Kotlin integrity checks shared by long-season tests, save diagnostics and release diagnostics.
 *
 * Findings have a [Severity]: ERROR means the career is internally inconsistent (impossible values, dangling ids,
 * standings that disagree with results); WARNING means suspicious but legal (an expired contract on a registered player,
 * a stale shortlist entry). [inspect] keeps returning errors only, [inspectAll] adds warnings, and [audit] returns a
 * capped, summarised [Report]. Nothing here mutates the world.
 */
object WorldIntegrity {
    enum class Severity { ERROR, WARNING }

    data class Issue(val code: String, val detail: String, val severity: Severity = Severity.ERROR)

    const val MAX_MONEY = 9_000_000_000_000L
    const val MAX_WAGE = 10_000_000
    const val DEFAULT_PER_CODE_LIMIT = 25
    private const val MAX_ROUND = 34
    private const val MAX_CONTRACT_YEARS = 10

    /** Capped findings plus the true count per code, so a broken career cannot produce an unbounded report. */
    class Report(val issues: List<Issue>, val totals: Map<String, Int>) {
        private val severityByCode: Map<String, Severity> = issues.associate { it.code to it.severity }
        val errorCount: Int = totals.filterKeys { severityByCode[it] == Severity.ERROR }.values.sum()
        val warningCount: Int = totals.filterKeys { severityByCode[it] == Severity.WARNING }.values.sum()
        val ok: Boolean get() = errorCount == 0

        fun summary(maxCodes: Int = 6): String {
            if (totals.isEmpty()) return "World integrity: no findings."
            val ordered = totals.entries.sortedWith(
                compareBy<Map.Entry<String, Int>> { if (severityByCode[it.key] == Severity.ERROR) 0 else 1 }.thenByDescending { it.value }.thenBy { it.key }
            )
            val shown = ordered.take(maxCodes).joinToString(", ") { "${it.key} x${it.value}" }
            val more = if (ordered.size > maxCodes) ", +${ordered.size - maxCodes} more" else ""
            return "World integrity: $errorCount error(s), $warningCount warning(s): $shown$more"
        }
    }

    /** Returns every detected ERROR (capped per code) without mutating the supplied career. */
    fun inspect(world: World): List<Issue> = inspectAll(world).filter { it.severity == Severity.ERROR }

    /** Errors and warnings, capped per code at [perCodeLimit]. */
    fun inspectAll(world: World, perCodeLimit: Int = DEFAULT_PER_CODE_LIMIT): List<Issue> = audit(world, perCodeLimit).issues

    fun audit(world: World, perCodeLimit: Int = DEFAULT_PER_CODE_LIMIT): Report {
        val all = collect(world)
        val totals = LinkedHashMap<String, Int>()
        val kept = ArrayList<Issue>()
        val perCode = HashMap<String, Int>()
        for (issue in all) {
            totals[issue.code] = (totals[issue.code] ?: 0) + 1
            val n = perCode[issue.code] ?: 0
            if (n < perCodeLimit) { kept += issue; perCode[issue.code] = n + 1 }
        }
        return Report(kept, totals)
    }

    fun requireValid(world: World) {
        val report = audit(world)
        val errors = report.issues.filter { it.severity == Severity.ERROR }
        require(errors.isEmpty()) {
            "World integrity failed (${report.errorCount} error(s)): " + errors.take(5).joinToString { "${it.code}: ${it.detail}" }
        }
    }

    private fun bad(f: Float) = f.isNaN() || f.isInfinite()
    private fun bad(d: Double) = d.isNaN() || d.isInfinite()

    private fun collect(world: World): List<Issue> {
        val out = ArrayList<Issue>()
        fun err(code: String, detail: String) { out += Issue(code, detail, Severity.ERROR) }
        fun warn(code: String, detail: String) { out += Issue(code, detail, Severity.WARNING) }
        val clubCount = world.clubs.size

        if (world.clubs.isEmpty()) err("clubs.empty", "World contains no clubs")
        if (world.leagues.isEmpty()) err("leagues.empty", "World contains no leagues")
        if (world.humanClubId !in world.clubs.indices) {
            err("human.club", "Human club id ${world.humanClubId} is outside the club list")
        }

        // ---- identifiers
        val seenClubIds = HashSet<Int>()
        for ((index, club) in world.clubs.withIndex()) {
            if (!seenClubIds.add(club.id)) err("club.duplicate-id", "Club id ${club.id} (${club.name}) is used more than once")
            if (club.id != index) err("club.index", "${club.name} has id ${club.id} but sits at position $index")
        }
        val seenLeagueIds = HashSet<Int>()
        for (league in world.leagues) if (!seenLeagueIds.add(league.id)) err("league.duplicate-id", "League id ${league.id} (${league.name}) is used more than once")

        // ---- players
        var highestPlayerId = 0
        for ((id, player) in world.players) {
            if (id > highestPlayerId) highestPlayerId = id
            if (id != player.id) err("player.key", "Player map key $id differs from player id ${player.id}")
            if (player.age !in 15..50) err("player.age", "${player.name} has invalid age ${player.age}")
            if (player.attrs.size != NATTR) err("player.attributes", "${player.name} has ${player.attrs.size} attributes; expected $NATTR")
            else if (player.attrs.any { it !in 1..20 }) err("player.attribute.range", "${player.name} has an attribute outside 1..20")
            if (player.pa !in 1..100) err("player.potential", "${player.name} has invalid potential ${player.pa}")
            if (bad(player.condition) || player.condition !in 0f..100f) err("player.condition", "${player.name} has condition ${player.condition}")
            if (bad(player.morale) || player.morale !in 0f..100f) err("player.morale", "${player.name} has morale ${player.morale}")
            if (player.clubId !in -1 until clubCount) err("player.club", "${player.name} references missing club ${player.clubId}")
            else if (player.clubId >= 0 && world.clubs[player.clubId].players.count { it == player.id } != 1) {
                err("player.membership", "${player.name} must occur exactly once in club ${player.clubId}")
            }

            // contracts
            if (player.wage < 0 || player.wage > MAX_WAGE) err("contract.wage", "${player.name} has wage ${player.wage}")
            if (player.clubId >= 0) {
                if (player.contractEnd > world.year + MAX_CONTRACT_YEARS) err("contract.date", "${player.name} has a contract ending in ${player.contractEnd}")
                else if (player.contractEnd < world.year) warn("contract.expired", "${player.name} is registered with a contract that ended in ${player.contractEnd}")
            } else if (player.wage > 0) {
                warn("contract.free-agent", "${player.name} is a free agent but still has a wage of ${player.wage}")
            }

            // statistics
            if (player.apps < 0 || player.goals < 0 || player.assists < 0 || player.minutes < 0) {
                err("stats.negative", "${player.name} has a negative statistic")
            }
            if (player.apps == 0 && (player.goals > 0 || player.assists > 0 || player.minutes > 0)) {
                err("stats.without-apps", "${player.name} has recorded statistics without any appearance")
            }
            if (bad(player.ratingSum)) err("stats.rating", "${player.name} has a non-finite rating total")
            else if (player.apps > 0 && (player.ratingSum < 0f || player.ratingSum > 10f * player.apps + 0.01f)) {
                err("stats.rating", "${player.name} has rating total ${player.ratingSum} over ${player.apps} appearance(s)")
            }
        }
        if (world.players.isNotEmpty() && world.nextPid <= highestPlayerId) {
            warn("player.next-id", "Next player id ${world.nextPid} is not above the highest existing id $highestPlayerId")
        }

        // ---- clubs
        val leagueOfClub = HashMap<Int, League>()
        for (league in world.leagues) for (id in league.clubIds) leagueOfClub.putIfAbsent(id, league)
        for (club in world.clubs) {
            if (club.balance > MAX_MONEY || club.balance < -MAX_MONEY) err("club.balance", "${club.name} balance is outside the supported range")
            if (club.revenue < 0L || club.wageBudget < 0L || club.transferBudget < 0L) err("club.finance", "${club.name} has a negative revenue or budget")
            if (club.facilities !in 1..20 || club.youth !in 1..20 || club.scout !in 1..20) err("club.rating", "${club.name} has a facilities, youth or scouting rating outside 1..20")
            val league = leagueOfClub[club.id]
            if (league != null && club.tier != league.tier) err("club.tier", "${club.name} has tier ${club.tier} but plays in ${league.name} (tier ${league.tier})")
            if (club.players.isEmpty()) err("club.squad-empty", "${club.name} has no players")
            if (club.players.size != club.players.toSet().size) err("club.duplicate-player", "${club.name} contains duplicate player ids")
            if (club.tactics.formation !in Formations.list.indices) err("club.tactics", "${club.name} uses unknown formation ${club.tactics.formation}")
            else if (club.tactics.mentality !in -2..2 || club.tactics.tempo !in 0..2 || club.tactics.pressing !in 0..2 ||
                club.tactics.width !in 0..2 || club.tactics.line !in 0..2 || club.tactics.passing !in 0..2) {
                err("club.tactics", "${club.name} has a tactical setting outside its range")
            }
            if (bad(club.boardConfidence) || club.boardConfidence !in 0f..100f) err("club.confidence", "${club.name} has board confidence ${club.boardConfidence}")
            for (id in club.players) {
                val player = world.players[id]
                if (player == null) err("club.missing-player", "${club.name} references missing player $id")
                else if (player.clubId != club.id) err("club.player-owner", "${player.name} is listed by ${club.name} but belongs to ${player.clubId}")
            }
        }

        // ---- leagues, fixtures and standings
        for (league in world.leagues) {
            val members = league.clubIds.toSet()
            if (members.size != league.clubIds.size) err("league.duplicate-club", "${league.name} has duplicate club ids")
            for (id in league.clubIds) if (id !in world.clubs.indices) err("league.missing-club", "${league.name} references missing club $id")

            val exact = HashSet<Triple<Int, Int, Int>>()
            val pairs = HashSet<Pair<Int, Int>>()
            val roundClub = HashSet<Pair<Int, Int>>()
            for (fixture in league.fixtures) {
                val teamsOk = fixture.home in world.clubs.indices && fixture.away in world.clubs.indices && fixture.home != fixture.away
                if (!teamsOk) err("fixture.teams", "${league.name} has invalid fixture ${fixture.home}-${fixture.away}")
                else if (fixture.home !in members || fixture.away !in members) err("fixture.foreign-club", "${league.name} has fixture ${fixture.home}-${fixture.away} with a club from another league")
                if (fixture.round !in 1..MAX_ROUND) err("fixture.round", "${league.name} has fixture in round ${fixture.round}")
                if (fixture.played && (fixture.hg < 0 || fixture.ag < 0 || fixture.hg > 20 || fixture.ag > 20)) {
                    err("fixture.score", "${league.name} has invalid score ${fixture.hg}-${fixture.ag}")
                }
                if (fixture.played && fixture.round > world.week) err("fixture.future-played", "${league.name} round ${fixture.round} is marked played in week ${world.week}")
                if (!fixture.played && fixture.round < world.week) warn("fixture.unplayed-past", "${league.name} round ${fixture.round} is unplayed in week ${world.week}")
                if (!exact.add(Triple(fixture.round, fixture.home, fixture.away))) err("fixture.duplicate", "${league.name} has a duplicate fixture in round ${fixture.round}")
                if (!pairs.add(fixture.home to fixture.away)) err("fixture.pair-duplicate", "${league.name} lists ${fixture.home}-${fixture.away} more than once")
                if (!roundClub.add(fixture.round to fixture.home)) err("fixture.conflict", "Club ${fixture.home} plays twice in round ${fixture.round} of ${league.name}")
                else if (fixture.home != fixture.away && !roundClub.add(fixture.round to fixture.away)) err("fixture.conflict", "Club ${fixture.away} plays twice in round ${fixture.round} of ${league.name}")
            }

            if (league.table.keys != members) err("table.membership", "${league.name} table rows do not match its clubs")
            for ((clubId, row) in league.table) {
                if (row.p != row.w + row.d + row.l || row.pts != row.w * 3 + row.d || row.p < 0 || row.gf < 0 || row.ga < 0) {
                    err("table.arithmetic", "${league.name}: club $clubId has inconsistent table totals")
                }
                if (row.form.length > 5 || row.form.any { it != 'W' && it != 'D' && it != 'L' }) err("table.form", "${league.name}: club $clubId has invalid form '${row.form}'")
            }
            // Recompute the table from the played fixtures; the record path is the only legal writer.
            if (league.table.keys == members) {
                val p = HashMap<Int, IntArray>() // played, won, drawn, lost, gf, ga
                for (id in members) p[id] = IntArray(6)
                for (f in league.fixtures) {
                    if (!f.played) continue
                    val h = p[f.home] ?: continue
                    val a = p[f.away] ?: continue
                    h[0]++; a[0]++
                    h[4] += f.hg; h[5] += f.ag; a[4] += f.ag; a[5] += f.hg
                    when { f.hg > f.ag -> { h[1]++; a[3]++ }; f.hg < f.ag -> { a[1]++; h[3]++ }; else -> { h[2]++; a[2]++ } }
                }
                for ((id, c) in p) {
                    val row = league.table.getValue(id)
                    if (row.p != c[0] || row.w != c[1] || row.d != c[2] || row.l != c[3] || row.gf != c[4] || row.ga != c[5]) {
                        err("table.mismatch", "${league.name}: standings of club $id disagree with the played fixtures")
                    }
                }
            }
        }

        // ---- transfers and shortlists
        for (offer in world.offers) {
            if (world.players[offer.playerId] == null) err("offer.player", "Offer references missing player ${offer.playerId}")
            if (offer.fromClub !in world.clubs.indices) err("offer.club", "Offer references missing club ${offer.fromClub}")
            if (offer.fee < 0L) err("offer.fee", "Offer for player ${offer.playerId} has fee ${offer.fee}")
        }
        for (id in world.shortlist) if (world.players[id] == null) warn("shortlist.player", "Shortlist references missing player $id")
        for (id in world.scouted) if (world.players[id] == null) warn("scouted.player", "Scouted set references missing player $id")

        // ---- business records
        val business = world.business
        if (business != null) {
            for (loan in business.loans) {
                if (world.players[loan.playerId] == null) warn("loan.stale", "Loan ${loan.id} references a player who no longer exists")
                if (loan.parentClubId !in world.clubs.indices || loan.borrowerClubId !in world.clubs.indices) {
                    err("loan.club", "Loan ${loan.id} references a missing club")
                }
            }
            for ((playerId, clause) in business.clauses) {
                if (clause.releaseClause < 0L || clause.sellOnPct !in 0..100 || clause.appearanceBonus < 0) err("clause.value", "Clause of player $playerId has an invalid value")
                if (clause.sellOnClubId != -1 && clause.sellOnClubId !in world.clubs.indices) err("clause.club", "Clause of player $playerId names missing club ${clause.sellOnClubId}")
            }
            for (deal in business.sponsors) {
                if (deal.weekly < 0L || deal.signingBonus < 0L || deal.winBonus < 0L) err("sponsor.value", "Sponsor ${deal.brand} has a negative amount")
            }
        }
        return out
    }
}
