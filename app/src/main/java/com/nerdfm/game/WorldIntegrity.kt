package com.nerdfm.game

/**
 * Pure Kotlin integrity audit shared by long-season tests, save diagnostics and the in-app release panel.
 *
 * Contract:
 *  - It NEVER mutates the supplied world (no repair, no clamping). A finding is a report, not a fix.
 *  - [Severity.ERROR] means the state is impossible for a correct game and must fail a soak test.
 *  - [Severity.WARNING] means suspicious or transient state (for example a loan whose player retired this
 *    week and is cleaned up by the next weekly tick). Warnings never fail [requireValid].
 *  - [inspect] keeps its original meaning (errors only) so earlier callers and tests are unaffected.
 *
 * Checks are written against the real NerdFM model (World/Club/Player/League/Fixture/BusinessState), not an
 * idealised one; each rule below was cross-checked with the code path that is supposed to maintain it.
 */
object WorldIntegrity {
    enum class Severity { ERROR, WARNING }

    data class Issue(val code: String, val detail: String, val severity: Severity = Severity.ERROR)

    /** Result of a full audit. [issues] is capped per code; [totals] still counts every occurrence. */
    class Report(
        val issues: List<Issue>,
        val errorCount: Int,
        val warningCount: Int,
        val totals: Map<String, Int>
    ) {
        val ok: Boolean get() = errorCount == 0

        fun errors(): List<Issue> = issues.filter { it.severity == Severity.ERROR }

        fun warnings(): List<Issue> = issues.filter { it.severity == Severity.WARNING }

        /** One line per distinct code: severity, occurrence count and the first example. */
        fun summary(maxLines: Int = 12): String {
            if (issues.isEmpty()) return "World integrity: no findings."
            val sb = StringBuilder("World integrity: $errorCount error(s), $warningCount warning(s)")
            var lines = 0
            val seen = HashSet<String>()
            for (issue in issues.sortedBy { it.severity.ordinal }) {
                if (!seen.add(issue.code)) continue
                if (lines >= maxLines) { sb.append("\n  ... more codes omitted"); break }
                sb.append("\n  [").append(issue.severity.name).append("] ").append(issue.code)
                    .append(" x").append(totals[issue.code] ?: 1).append(": ").append(issue.detail)
                lines++
            }
            return sb.toString()
        }
    }

    const val DEFAULT_PER_CODE_LIMIT = 25
    const val MAX_MONEY = 9_000_000_000_000L
    const val MAX_WAGE = 1_000_000_000
    const val MAX_CONTRACT_YEARS = 10
    const val MAX_REASONABLE_PLAYERS = 15_000
    const val MAX_REASONABLE_CLUBS = 500
    private const val MAX_MINUTES_PER_APPEARANCE = 130
    private const val MAX_GOALS_PER_APPEARANCE = 10

    private class Sink(private val perCode: Int) {
        val issues = ArrayList<Issue>()
        val totals = LinkedHashMap<String, Int>()
        var errorTotal = 0
        var warningTotal = 0

        private fun add(severity: Severity, code: String, detail: String) {
            val n = (totals[code] ?: 0) + 1
            totals[code] = n
            if (severity == Severity.ERROR) errorTotal++ else warningTotal++
            if (n <= perCode) issues += Issue(code, detail, severity)
        }

        fun error(code: String, detail: String) = add(Severity.ERROR, code, detail)
        fun warn(code: String, detail: String) = add(Severity.WARNING, code, detail)
    }

    /** Full audit including warnings. Does not modify [world]. */
    fun audit(world: World, perCodeLimit: Int = DEFAULT_PER_CODE_LIMIT): Report {
        val sink = Sink(perCodeLimit)
        checkShell(world, sink)
        checkIdentifiers(world, sink)
        checkPlayers(world, sink)
        checkContracts(world, sink)
        checkPlayerStats(world, sink)
        checkClubs(world, sink)
        checkLeagues(world, sink)
        checkFixtures(world, sink)
        checkStandings(world, sink)
        checkTransferRecords(world, sink)
        checkBusiness(world, sink)
        checkEcosystem(world, sink)
        return Report(sink.issues, sink.errorTotal, sink.warningTotal, sink.totals)
    }

    /** Errors only; identical in meaning to the original M8 groundwork. */
    fun inspect(world: World): List<Issue> = audit(world).errors()

    /** Errors and warnings. */
    fun inspectAll(world: World): List<Issue> = audit(world).issues

    fun requireValid(world: World) {
        val errors = audit(world).errors()
        require(errors.isEmpty()) {
            "World integrity failed (${errors.size} issue(s)): " + errors.take(5).joinToString { "${it.code}: ${it.detail}" }
        }
    }

    // ------------------------------------------------------------------ individual checks

    private fun checkShell(world: World, s: Sink) {
        if (world.clubs.isEmpty()) s.error("clubs.empty", "World contains no clubs")
        if (world.leagues.isEmpty()) s.error("leagues.empty", "World contains no leagues")
        if (world.humanClubId !in world.clubs.indices) {
            s.error("human.club", "Human club id ${world.humanClubId} is outside the club list")
        }
        if (world.week !in 0..34) s.error("world.week", "Week ${world.week} is outside 0..34")
        if (world.year < 1) s.error("world.year", "Year ${world.year} is not positive")
        if (!(world.managerReputation in 0f..100f)) s.error("world.reputation", "Manager reputation ${world.managerReputation} is outside 0..100")
        if (world.players.size > MAX_REASONABLE_PLAYERS) s.warn("world.player-count", "${world.players.size} players exist; expected far fewer")
        if (world.clubs.size > MAX_REASONABLE_CLUBS) s.warn("world.club-count", "${world.clubs.size} clubs exist; expected far fewer")
        val humans = world.clubs.count { it.human }
        val humanFlagged = world.clubs.getOrNull(world.humanClubId)?.human == true
        if (humans != 1 || !humanFlagged) {
            s.warn("human.flag", "$humans club(s) are flagged human; the human club (${world.humanClubId}) flagged=$humanFlagged")
        }
    }

    private fun checkIdentifiers(world: World, s: Sink) {
        val clubIds = HashSet<Int>()
        for ((index, club) in world.clubs.withIndex()) {
            if (!clubIds.add(club.id)) s.error("club.duplicate-id", "Club id ${club.id} (${club.name}) occurs more than once")
            if (club.id != index) s.error("club.index", "${club.name} has id ${club.id} at list index $index")
        }
        val leagueIds = HashSet<Int>()
        for ((index, league) in world.leagues.withIndex()) {
            if (!leagueIds.add(league.id)) s.error("league.duplicate-id", "League id ${league.id} occurs more than once")
            if (league.id != index) s.error("league.index", "${league.name} has id ${league.id} at list index $index")
        }
        val coachIds = HashSet<Int>()
        for (club in world.clubs) for (coach in club.coaches) {
            if (!coachIds.add(coach.id)) s.warn("coach.duplicate-id", "Coach id ${coach.id} is employed more than once")
        }
        for (coach in world.coachMarket) {
            if (coach.id in coachIds) s.warn("coach.market-overlap", "Coach ${coach.name} is both employed and on the market")
        }
        val maxPlayerId = world.players.keys.maxOrNull()
        if (maxPlayerId != null && maxPlayerId >= world.nextPid) {
            s.warn("player.next-id", "Highest player id $maxPlayerId is not below nextPid ${world.nextPid}; new players could collide")
        }
    }

    private fun checkPlayers(world: World, s: Sink) {
        for ((id, player) in world.players) {
            val who = player.name
            if (id != player.id) s.error("player.key", "Player map key $id differs from player id ${player.id}")
            if (player.age !in 15..50) s.error("player.age", "$who has invalid age ${player.age}")
            if (player.attrs.size != NATTR) s.error("player.attributes", "$who has ${player.attrs.size} attributes; expected $NATTR")
            else if (player.attrs.any { it !in 1..20 }) s.error("player.attribute.range", "$who has an attribute outside 1..20")
            if (player.pa !in 1..100) s.error("player.potential", "$who has invalid potential ${player.pa}")
            if (player.nation !in Names.nations.indices) s.error("player.nation", "$who has unknown nation ${player.nation}")
            if (!(player.condition in 0f..100f)) s.error("player.condition", "$who has condition ${player.condition}")
            if (!(player.morale in 0f..100f)) s.error("player.morale", "$who has morale ${player.morale}")
            if (!(player.sharpness in 0f..100f)) s.error("player.sharpness", "$who has sharpness ${player.sharpness}")
            if (!(player.form in 0f..100f)) s.error("player.form", "$who has form ${player.form}")
            if (player.injWeeks !in 0..250) s.error("player.injury", "$who has injury length ${player.injWeeks}")
            if (player.suspWeeks !in 0..60) s.error("player.suspension", "$who has suspension length ${player.suspWeeks}")
            if (player.trainingIntensity !in 0..2) s.error("player.training", "$who has training intensity ${player.trainingIntensity}")
            if (player.playingTimePromise !in 0..4 || player.promiseWeeksRemaining < 0 || player.promiseBrokenWeeks < 0) {
                s.error("player.promise", "$who has an invalid playing-time promise state")
            }
            if (player.clubId !in -1 until world.clubs.size) s.error("player.club", "$who references missing club ${player.clubId}")
            if (player.clubId >= 0 && player.clubId < world.clubs.size &&
                world.clubs[player.clubId].players.count { it == player.id } != 1
            ) {
                s.error("player.membership", "$who must occur exactly once in club ${player.clubId}")
            }
        }
    }

    private fun checkContracts(world: World, s: Sink) {
        for (player in world.players.values) {
            if (player.wage < 0 || player.wage > MAX_WAGE) s.error("contract.wage", "${player.name} has wage ${player.wage}")
            if (player.clubId >= 0) {
                if (player.contractEnd > world.year + MAX_CONTRACT_YEARS) {
                    s.error("contract.date", "${player.name} has a contract ending in ${player.contractEnd}, more than $MAX_CONTRACT_YEARS years away")
                } else if (player.contractEnd < world.year) {
                    s.warn("contract.expired", "${player.name} is still registered with a contract that ended in ${player.contractEnd}")
                }
            } else if (player.wage != 0 || player.contractEnd != 0) {
                s.warn("contract.free-agent", "Free agent ${player.name} keeps wage ${player.wage} / contract ${player.contractEnd}")
            }
        }
    }

    private fun checkPlayerStats(world: World, s: Sink) {
        for (p in world.players.values) {
            if (p.apps < 0 || p.goals < 0 || p.assists < 0 || p.minutes < 0 || p.yellows < 0 || p.lastMinutes !in 0..MAX_MINUTES_PER_APPEARANCE) {
                s.error("stats.negative", "${p.name} has a negative or impossible season statistic")
            }
            if (p.seasonShots < 0 || p.seasonShotsOnTarget < 0 || p.seasonKeyPasses < 0 || p.seasonTackles < 0 ||
                p.seasonInterceptions < 0 || p.seasonSaves < 0 || !(p.seasonXg >= 0.0) || p.seasonXg.isInfinite()
            ) {
                s.error("stats.career", "${p.name} has a negative or non-finite career statistic")
            }
            if (p.apps == 0 && (p.minutes > 0 || p.goals > 0 || p.assists > 0)) {
                s.error("stats.without-apps", "${p.name} has minutes/goals/assists but no appearances")
            }
            if (p.apps > 0) {
                if (p.minutes > p.apps * MAX_MINUTES_PER_APPEARANCE) s.warn("stats.minutes", "${p.name} has ${p.minutes} minutes in ${p.apps} appearances")
                if (p.goals > p.apps * MAX_GOALS_PER_APPEARANCE) s.warn("stats.goals", "${p.name} has ${p.goals} goals in ${p.apps} appearances")
                val sum = p.ratingSum
                if (sum.isNaN() || sum.isInfinite()) s.error("stats.rating", "${p.name} has a non-finite rating total")
                else if (sum < p.apps * 3f - 0.5f - p.apps * 0.001f || sum > p.apps * 10f + 0.5f + p.apps * 0.001f) {
                    s.warn("stats.rating", "${p.name} rating total $sum is impossible for ${p.apps} appearances")
                }
            }
            if (p.seasonShotsOnTarget > p.seasonShots) s.warn("stats.shots", "${p.name} has more shots on target than shots")
        }
    }

    private fun checkClubs(world: World, s: Sink) {
        val populated = world.leagues.any { it.clubIds.isNotEmpty() }
        val leagueOfClub = HashMap<Int, Int>()
        for ((index, league) in world.leagues.withIndex()) {
            for (id in league.clubIds) {
                val previous = leagueOfClub.put(id, index)
                if (previous != null && previous != index) s.error("club.multi-league", "Club $id appears in leagues $previous and $index")
            }
        }
        for (club in world.clubs) {
            val name = club.name
            for (money in longArrayOf(club.balance, club.revenue, club.wageBudget, club.transferBudget)) {
                if (money > MAX_MONEY || money < -MAX_MONEY) s.error("club.balance", "$name has a finance figure outside the supported range")
            }
            if (club.revenue < 0L || club.wageBudget < 0L) s.error("club.finance", "$name has negative revenue or wage budget")
            if (club.players.size != club.players.toSet().size) s.error("club.duplicate-player", "$name contains duplicate player ids")
            for (id in club.players) {
                val player = world.players[id]
                if (player == null) s.error("club.missing-player", "$name references missing player $id")
                else if (player.clubId != club.id) s.error("club.player-owner", "${player.name} is listed by $name but belongs to ${player.clubId}")
            }
            for (coach in club.coaches) {
                if (coach.wage < 0 || coach.rating !in 0..100) s.error("club.coach", "$name has coach ${coach.name} with impossible wage/rating")
            }
            if (club.facilities !in 1..20 || club.youth !in 1..20 || club.scout !in 1..20) {
                s.error("club.rating", "$name has a facilities/youth/scout level outside 1..20")
            }
            if (!(club.boardConfidence in 0f..100f) || !(club.teamCohesion in 0f..100f)) {
                s.error("club.confidence", "$name has board confidence/cohesion outside 0..100")
            }
            val t = club.tactics
            if (t.formation !in Formations.list.indices || t.mentality !in -2..2 || t.tempo !in 0..2 ||
                t.pressing !in 0..2 || t.width !in 0..2 || t.line !in 0..2 || t.passing !in 0..2
            ) {
                s.error("club.tactics", "$name has an out-of-range tactical setting")
            }
            for (slot in club.lineup) {
                if (slot != -1 && slot !in club.players) { s.warn("club.lineup", "$name lineup references player $slot who is not in the squad"); break }
            }
            val starters = club.lineup.filter { it != -1 }
            if (starters.size != starters.toSet().size) s.warn("club.lineup-duplicate", "$name picks a player twice in the lineup")
            if (populated) {
                val index = leagueOfClub[club.id]
                if (index == null) s.error("club.no-league", "$name belongs to no league")
                else if (index != club.tier) s.error("club.tier", "$name has tier ${club.tier} but sits in league $index")
                if (club.players.isEmpty()) s.error("club.squad-empty", "$name has no players")
                else if (club.players.size < 16) s.warn("club.squad-small", "$name has only ${club.players.size} players")
                if (club.players.size > 60) s.warn("club.squad-large", "$name has ${club.players.size} players")
            }
        }
    }

    private fun checkLeagues(world: World, s: Sink) {
        for (league in world.leagues) {
            if (league.clubIds.toSet().size != league.clubIds.size) s.error("league.duplicate-club", "${league.name} has duplicate club ids")
            for (id in league.clubIds) if (id !in world.clubs.indices) s.error("league.missing-club", "${league.name} references missing club $id")
            if (league.clubIds.isNotEmpty() && league.clubIds.size != WorldGen.CLUBS_PER_LEAGUE) {
                s.warn("league.size", "${league.name} has ${league.clubIds.size} clubs; expected ${WorldGen.CLUBS_PER_LEAGUE}")
            }
        }
    }

    private fun checkFixtures(world: World, s: Sink) {
        for (league in world.leagues) {
            val n = league.clubIds.size
            val members = league.clubIds.toHashSet()
            val maxRound = if (n >= 2) 2 * (n - 1) else Int.MAX_VALUE
            val seenKeys = HashSet<Long>()
            val seenPairs = HashSet<Long>()
            val seenRoundClub = HashSet<Long>()
            for (f in league.fixtures) {
                val validTeams = f.home in world.clubs.indices && f.away in world.clubs.indices && f.home != f.away
                if (!validTeams) s.error("fixture.teams", "${league.name} has invalid fixture ${f.home}-${f.away}")
                if (f.round < 1 || f.round > maxRound) s.error("fixture.round", "${league.name} has fixture in round ${f.round}")
                if (f.played && (f.hg < 0 || f.ag < 0 || f.hg > 20 || f.ag > 20)) {
                    s.error("fixture.score", "${league.name} has invalid score ${f.hg}-${f.ag}")
                }
                if (f.played && f.round > world.week) s.error("fixture.future-played", "${league.name} round ${f.round} is played but the calendar is at week ${world.week}")
                if (!f.played && f.round < world.week) s.warn("fixture.unplayed-past", "${league.name} round ${f.round} ${f.home}-${f.away} is unplayed at week ${world.week}")
                if (!validTeams) continue
                if (members.isNotEmpty() && (f.home !in members || f.away !in members)) {
                    s.error("fixture.foreign-club", "${league.name} fixture ${f.home}-${f.away} involves a club outside the league")
                }
                val key = (f.round.toLong() shl 40) or (f.home.toLong() shl 20) or f.away.toLong()
                if (!seenKeys.add(key)) s.error("fixture.duplicate", "${league.name} has a duplicate fixture in round ${f.round}")
                val pair = (f.home.toLong() shl 20) or f.away.toLong()
                if (!seenPairs.add(pair)) s.error("fixture.pair-duplicate", "${league.name} schedules ${f.home} v ${f.away} more than once")
                for (club in intArrayOf(f.home, f.away)) {
                    if (!seenRoundClub.add((f.round.toLong() shl 20) or club.toLong())) {
                        s.error("fixture.conflict", "${league.name} club $club plays twice in round ${f.round}")
                    }
                }
            }
            if (n >= 2 && league.fixtures.isNotEmpty() && league.fixtures.size != n * (n - 1)) {
                s.warn("fixture.count", "${league.name} has ${league.fixtures.size} fixtures; a double round-robin of $n clubs has ${n * (n - 1)}")
            }
        }
    }

    private fun checkStandings(world: World, s: Sink) {
        for (league in world.leagues) {
            if (league.clubIds.isEmpty()) continue
            val anyPlayed = league.fixtures.any { it.played }
            if (league.table.isEmpty() && !anyPlayed) continue
            if (league.table.keys != league.clubIds.toSet()) {
                s.error("table.membership", "${league.name} table rows do not match the league's clubs")
            }
            // Recompute from results: p, w, d, l, gf, ga.
            val calc = HashMap<Int, IntArray>()
            for (f in league.fixtures) {
                if (!f.played || f.home !in league.table || f.away !in league.table) continue
                val h = calc.getOrPut(f.home) { IntArray(6) }
                val a = calc.getOrPut(f.away) { IntArray(6) }
                h[0]++; a[0]++
                h[4] += f.hg; h[5] += f.ag; a[4] += f.ag; a[5] += f.hg
                if (f.hg > f.ag) { h[1]++; a[3]++ } else if (f.hg < f.ag) { a[1]++; h[3]++ } else { h[2]++; a[2]++ }
            }
            for ((id, row) in league.table) {
                val who = world.clubs.getOrNull(id)?.name ?: "club $id"
                if (row.clubId != id) s.error("table.key", "${league.name} row for $id carries club ${row.clubId}")
                if (row.p != row.w + row.d + row.l || row.pts != row.w * 3 + row.d || row.p < 0 || row.gf < 0 || row.ga < 0) {
                    s.error("table.arithmetic", "$who has an internally inconsistent table row")
                }
                if (row.form.length > 5 || row.form.any { it != 'W' && it != 'D' && it != 'L' }) {
                    s.error("table.form", "$who has an invalid form string '${row.form}'")
                }
                val e = calc[id] ?: IntArray(6)
                if (row.p != e[0] || row.w != e[1] || row.d != e[2] || row.l != e[3] || row.gf != e[4] || row.ga != e[5]) {
                    s.error(
                        "table.mismatch",
                        "$who table says P${row.p} W${row.w} D${row.d} L${row.l} ${row.gf}:${row.ga} but results give " +
                            "P${e[0]} W${e[1]} D${e[2]} L${e[3]} ${e[4]}:${e[5]}"
                    )
                }
            }
        }
    }

    private fun checkTransferRecords(world: World, s: Sink) {
        for (offer in world.offers) {
            val player = world.players[offer.playerId]
            if (player == null) s.error("offer.player", "An offer references missing player ${offer.playerId}")
            else if (player.clubId != world.humanClubId) s.warn("offer.stale", "An offer for ${player.name} outlived his time at the human club")
            if (offer.fromClub !in world.clubs.indices) s.error("offer.club", "An offer comes from missing club ${offer.fromClub}")
            if (offer.fee < 0L) s.error("offer.fee", "An offer carries a negative fee ${offer.fee}")
        }
        for (id in world.shortlist) if (id !in world.players) s.warn("shortlist.player", "Shortlist references missing player $id")
        if (world.shortlist.size != world.shortlist.toSet().size) s.warn("shortlist.duplicate", "Shortlist contains duplicates")
        for (id in world.scouted) if (id !in world.players) s.warn("scouted.player", "Scouted set references missing player $id")
    }

    private fun checkBusiness(world: World, s: Sink) {
        val b = world.business ?: return
        val activeByPlayer = HashSet<Int>()
        for (d in b.loans) {
            if (!d.active) continue
            if (d.parentClubId !in world.clubs.indices || d.borrowerClubId !in world.clubs.indices) {
                s.error("loan.club", "Loan ${d.id} references a missing club")
                continue
            }
            if (d.parentClubId == d.borrowerClubId) s.error("loan.same-club", "Loan ${d.id} lends a player to his own club")
            if (d.borrowerSharePct !in 0..100) s.error("loan.share", "Loan ${d.id} has wage share ${d.borrowerSharePct}")
            if (!activeByPlayer.add(d.playerId)) s.error("loan.duplicate", "Player ${d.playerId} has more than one active loan")
            val player = world.players[d.playerId]
            // A retirement at season rollover is reconciled by Loans.weekly on the next tick, so these are warnings.
            if (player == null) s.warn("loan.stale", "Loan ${d.id} references a player who no longer exists")
            else if (player.clubId != d.borrowerClubId) s.warn("loan.location", "Loan ${d.id}: ${player.name} is not at the borrowing club")
        }
        for ((playerId, clause) in b.clauses) {
            if (playerId !in world.players) s.warn("clause.player", "Contract clauses exist for missing player $playerId")
            if (clause.releaseClause < 0L || clause.sellOnPct !in 0..100 || clause.appearanceBonus < 0) {
                s.error("clause.value", "Player $playerId has an impossible contract clause")
            }
            if (clause.sellOnClubId != -1 && clause.sellOnClubId !in world.clubs.indices) {
                s.error("clause.club", "Player $playerId has a sell-on beneficiary that does not exist")
            }
        }
        for (id in b.negotiations.keys) if (id !in world.players) s.warn("negotiation.player", "Negotiation state exists for missing player $id")
        for (deal in b.sponsors) {
            if (deal.weekly < 0L || deal.signingBonus < 0L || deal.winBonus < 0L) s.error("sponsor.value", "Sponsor ${deal.brand} has a negative amount")
        }
        for (scout in b.scouts) if (scout.wage < 0) s.error("scout.wage", "Scout ${scout.name} has a negative wage")
    }

    private fun checkEcosystem(world: World, s: Sink) {
        val state = world.ecosystem ?: return
        if (state.applied.isNotEmpty()) {
            s.warn("ecosystem.applied", "${state.applied.size} match modifier(s) are still applied; they are removed when the match ends")
        }
    }
}
