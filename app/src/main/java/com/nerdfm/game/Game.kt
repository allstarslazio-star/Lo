package com.nerdfm.game

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

object Game {
    // ------------------------------------------------------------ matches

    fun startWeek(w: World): Fixture? {
        if (w.week < 1 || w.week > 34) return null
        for (c in w.clubs) { c.weeklyIncome = 0L; c.weeklyExpense = 0L }
        val rnd = w.rng(1)
        var human: Fixture? = null
        for (lg in w.leagues) {
            for (fx in lg.fixtures) {
                if (fx.round != w.week || fx.played) continue
                if (fx.home == w.humanClubId || fx.away == w.humanClubId) { human = fx; continue }
                simFixture(w, lg, fx, rnd)
            }
        }
        return human
    }

    private fun simFixture(w: World, lg: League, fx: Fixture, rnd: Random) {
        val hc = w.clubs[fx.home]
        val ac = w.clubs[fx.away]
        val ht = MatchTeams.build(w, hc, ac, true, rnd)
        val at = MatchTeams.build(w, ac, hc, false, rnd)
        val eng = MatchEngine(ht, at, rnd)
        eng.simToEnd()
        eng.applyToPlayers(rnd)
        w.matchCalibration.recordMatch(ht.goals + at.goals, ht.shots + at.shots)
        record(w, lg, fx, ht.goals, at.goals)
    }

    fun humanEngine(w: World, fx: Fixture): MatchEngine {
        val rnd = Random(w.seed + w.year * 1000L + w.week * 31L + 7)
        val hc = w.clubs[fx.home]
        val ac = w.clubs[fx.away]
        val ht = MatchTeams.build(w, hc, ac, true, rnd)
        val at = MatchTeams.build(w, ac, hc, false, rnd)
        return MatchEngine(ht, at, rnd)
    }

    fun finishHuman(w: World, fx: Fixture, eng: MatchEngine) {
        val rnd = w.rng(3)
        eng.applyToPlayers(rnd)
        w.matchCalibration.recordMatch(eng.home.goals + eng.away.goals, eng.home.shots + eng.away.shots)
        val lg = w.leagueOf(w.humanClubId)
        val me = w.human()
        val mine = if (fx.home == me.id) eng.home else eng.away
        // persist in-match instruction changes (not formation)
        me.tactics.mentality = mine.tactics.mentality
        me.tactics.tempo = mine.tactics.tempo
        me.tactics.pressing = mine.tactics.pressing
        me.tactics.width = mine.tactics.width
        me.tactics.line = mine.tactics.line
        me.tactics.passing = mine.tactics.passing
        record(w, lg, fx, eng.home.goals, eng.away.goals)
        val motm = eng.manOfTheMatch()
        fx.report = "MOTM: " + (motm?.p?.name ?: "-")
        val opp = if (fx.home == me.id) w.clubs[fx.away] else w.clubs[fx.home]
        val mg = mine.goals
        val og = if (fx.home == me.id) eng.away.goals else eng.home.goals
        val res = if (mg > og) "won" else if (mg < og) "lost" else "drew"
        w.lastResult = "You $res $mg-$og vs ${opp.name}"
        // board
        val rank = leagueRank(w, me)
        val n = lg.clubIds.size
        val expPpg = 2.0f - 1.3f * rank / (n - 1).toFloat()
        val pts = if (mg > og) 3 else if (mg == og) 1 else 0
        me.boardConfidence = clampF(me.boardConfidence + (pts - expPpg) * 0.9f, 0f, 100f)
    }

    fun leagueRank(w: World, c: Club): Int {
        val lg = w.leagues[c.tier]
        val order = lg.clubIds.sortedByDescending { w.clubs[it].rep }
        return order.indexOf(c.id)
    }

    fun record(w: World, lg: League, fx: Fixture, hg: Int, ag: Int) {
        fx.played = true
        fx.hg = hg
        fx.ag = ag
        val hr = lg.table[fx.home]!!
        val ar = lg.table[fx.away]!!
        hr.p++; ar.p++
        hr.gf += hg; hr.ga += ag; ar.gf += ag; ar.ga += hg
        val hc = w.clubs[fx.home]
        val ac = w.clubs[fx.away]
        if (hg > ag) {
            hr.w++; hr.pts += 3; ar.l++; hr.form.append('W'); ar.form.append('L'); hc.lastPts = 3; ac.lastPts = 0
        } else if (hg < ag) {
            ar.w++; ar.pts += 3; hr.l++; hr.form.append('L'); ar.form.append('W'); hc.lastPts = 0; ac.lastPts = 3
        } else {
            hr.d++; ar.d++; hr.pts++; ar.pts++; hr.form.append('D'); ar.form.append('D'); hc.lastPts = 1; ac.lastPts = 1
        }
        if (hr.form.length > 5) hr.form.deleteCharAt(0)
        if (ar.form.length > 5) ar.form.deleteCharAt(0)
        // gate receipts
        val att = clampF(0.8f + 0.04f * hr.pts / max(1, hr.p) * 3f + 0.1f * (hc.rep / 100f), 0.7f, 1.2f)
        val gate = (hc.revenue * 0.30 / 17.0 * att).toLong()
        hc.balance += gate
        hc.weeklyIncome += gate
    }

    // ------------------------------------------------------------ weekly cycle

    fun completeWeek(w: World) {
        val rnd = w.rng(2)
        val squadTopCut = HashMap<Int, Int>()
        for (c in w.clubs) {
            val sorted = w.squad(c).sortedByDescending { it.ovr }
            squadTopCut[c.id] = if (sorted.size > 14) sorted[13].ovr else 0
        }
        for (p in w.players.values) {
            if (p.injWeeks > 0) {
                p.injWeeks--
                if (p.injWeeks == 0) p.injType = ""
            }
            if (p.suspWeeks > 0) p.suspWeeks--
            if (p.clubId >= 0) {
                val c = w.clubs[p.clubId]
                val recovery = when (p.trainingIntensity) { 0 -> 1.20f; 2 -> 0.72f; else -> 1f }
                val gain = (28f + p.attrs.at(Attr.Fitness) * 1.1f + c.facilities * 0.5f) * recovery
                p.condition = min(100f, p.condition + gain)
                val playedSharpness = if (p.lastMinutes >= 60) 5f else if (p.lastMinutes >= 20) 2f else -1.5f
                val trainingSharpness = when (p.trainingIntensity) { 0 -> 1.5f; 2 -> 4.5f; else -> 3f }
                p.sharpness = clampF(p.sharpness + playedSharpness + trainingSharpness + (c.facilities - 8) * 0.15f, 20f, 100f)
                if (p.injWeeks > 0) { p.condition = min(p.condition, 60f); p.sharpness = min(p.sharpness, 65f) }
                else if (p.trainingIntensity == 2 && rnd.nextDouble() < (0.0025 + (100f - p.condition) / 10000f)) {
                    p.injWeeks = 1 + rnd.nextInt(3)
                    p.injType = listOf("muscle strain", "ankle knock", "fatigue-related injury")[rnd.nextInt(3)]
                    if (p.clubId == w.humanClubId) w.addNews("Training injury", "${p.name} suffered a ${p.injType} during an intensive training week and is expected to miss ${p.injWeeks} week(s).")
                }
                // Attribute growth is deliberately gradual, potential-limited, and influenced by coaches/facilities.
                if (p.age <= 30 && p.ovr < p.pa && p.injWeeks == 0 && p.trainingIntensity > 0) {
                    val coachBonus = c.coaches.maxOfOrNull { it.rating } ?: 45
                    val chance = (0.025 + c.facilities * 0.0015 + coachBonus * 0.00035 + p.attrs.at(Attr.Determination) * 0.0005) * if (p.trainingIntensity == 2) 1.25 else 1.0
                    if (rnd.nextDouble() < chance) {
                        val focus = when (p.trainingFocus) {
                            "Attacking" -> listOf(Attr.Finishing, Attr.FirstTouch, Attr.OffTheBall, Attr.Technique)
                            "Defending" -> listOf(Attr.Tackling, Attr.Marking, Attr.Positioning, Attr.Anticipation)
                            "Physical" -> listOf(Attr.Pace, Attr.Stamina, Attr.Strength, Attr.Acceleration)
                            "Passing" -> listOf(Attr.Passing, Attr.Vision, Attr.Decisions, Attr.FirstTouch)
                            "Goalkeeping" -> listOf(Attr.Reflexes, Attr.Handling, Attr.OneOnOnes, Attr.Command)
                            else -> ATTRS.filter { it.cat != 3 }
                        }
                        val eligible = focus.filter { p.attrs[it.ordinal] < 20 }
                        if (eligible.isNotEmpty()) {
                            val a = eligible[rnd.nextInt(eligible.size)]
                            p.attrs[a.ordinal]++
                            p.refresh()
                            if (p.clubId == w.humanClubId) w.addNews("Training progress", "${p.name} improved ${a.label} to ${p.attrs[a.ordinal]} after focused training.")
                        }
                    }
                }
                if (w.week >= 1) {
                    var d = 0f
                    if (p.lastMinutes >= 45) d += 1.5f
                    else if (p.ovr >= (squadTopCut[c.id] ?: 0) && p.injWeeks == 0) d -= 2.5f
                    d += (c.lastPts - 1) * 1.5f
                    d += (70f - p.morale) * 0.08f
                    p.morale = clampF(p.morale + d, 15f, 100f)
                } else {
                    p.morale = clampF(p.morale + (70f - p.morale) * 0.3f, 15f, 100f)
                }
            } else {
                p.condition = 100f
            }
            p.lastMinutes = 0
        }
        // Weekly coaching costs are part of the club wage bill and are intentionally transparent.
        // Finances
        for (c in w.clubs) {
            val base = (c.revenue * 0.55 / 52.0).toLong()
            val wages = w.wageBill(c)
            val upkeep = (c.revenue * 0.05 / 52.0 * (0.5 + c.facilities / 10.0)).toLong()
            c.balance += base - wages - upkeep
            c.weeklyIncome += base
            c.weeklyExpense = wages + upkeep
        }
        windowNews(w)
        // offers
        w.offers.removeAll { it.expiresWeek < w.week || w.players[it.playerId]?.clubId != w.humanClubId }
        scoutingTick(w, rnd)
        Transfers.aiSquadMaintenance(w, rnd)
        if (w.isWindowOpen()) {
            Transfers.aiMarket(w, rnd)
            Transfers.generateOffers(w, rnd)
        }
        // injuries / suspensions news for human
        val me = w.human()
        for (p in w.squad(me)) {
            if (p.injWeeks > 0 && p.lastMinutes == 0 && p.condition < 61f && p.injType.isNotEmpty() && p.injWeeks >= 4 && rnd.nextInt(3) == 0) {
                // occasional reminders are handled in the squad screen; no spam
            }
        }
        // Persist the human manager's commonly used tactical settings for AI adaptation.
        w.humanTacticSamples++
        w.humanPressingTotal += me.tactics.pressing
        w.humanLineTotal += me.tactics.line
        w.humanTempoTotal += me.tactics.tempo
        // board pressure
        val bill = w.wageBill(me)
        if (bill > me.wageBudget) {
            me.boardConfidence = max(0f, me.boardConfidence - 0.3f)
            if (w.news.none { it.title == "Wage budget warning" && it.year == w.year && it.week == w.week }) {
                w.addNews("Wage budget warning", "Your weekly wage bill ${money(bill)} exceeds the approved budget ${money(me.wageBudget)}. The board expects action.")
            }
        }
        if (me.balance < 0) me.boardConfidence = max(0f, me.boardConfidence - 0.5f)
        if (me.autoPick) Lineups.autoPick(w, me) else Lineups.fixHuman(w, me)
        if (w.week >= 8 && me.boardConfidence <= 8f) {
            w.sacked = true
            w.sackReason = "The board lost all faith in your project."
        }
        if (me.balance < -(me.revenue * 0.6).toLong()) {
            w.sacked = true
            w.sackReason = "The club is bankrupt. Administrators have dismissed you."
        }
        if (w.week == 34) {
            endSeason(w, rnd)
        } else {
            w.week++
        }
        w.dayInWeek = 0
    }

    /** Step one calendar day inside the current week (no fixtures are played). */
    fun advanceDay(w: World) {
        if (w.dayInWeek < 6) w.dayInWeek++
        windowNews(w)
    }

    /** Transfer-window reminders, once per title/year/week. */
    fun windowNews(w: World) {
        if (!w.isWindowOpen()) return
        val left = w.windowDaysRemaining()
        val title = when {
            left == 0 -> "Transfer deadline day"
            left in 1..7 -> "Transfer deadline approaching"
            else -> return
        }
        if (w.news.none { it.title == title && it.year == w.year && it.week == w.week }) {
            val body = if (left == 0) "Today is the last day of the window. Finalise outstanding deals now."
                       else "${w.windowText()}. Finalise outstanding deals before the window closes."
            w.addNews(title, body)
        }
    }

    // ------------------------------------------------------------ scouting

    private fun scoutingTick(w: World, rnd: Random) {
        if (w.scoutLeague == -1) return
        val me = w.human()
        val found = ArrayList<Player>()
        val pool = w.players.values.filter { p ->
            val inLeague = if (w.scoutLeague == -2) p.clubId == -1
            else p.clubId >= 0 && w.clubs[p.clubId].tier == w.scoutLeague
            inLeague && p.clubId != me.id && (w.scoutPos == -1 || p.pos.ordinal == w.scoutPos) && !w.scouted.contains(p.id)
        }
        if (pool.isEmpty()) return
        val n = 2 + me.scout / 6
        for (i in 0 until n) {
            val p = pool[rnd.nextInt(pool.size)]
            w.scouted.add(p.id)
            found.add(p)
        }
        val best = found.maxByOrNull { max(it.ovr, it.pa) }
        if (best != null) {
            w.addNews("Scout report", "Scouts fully assessed ${found.size} players. Standout: ${best.name} (${best.pos.label}, age ${best.age}, ${best.ovr} now, up to ${best.pa}).")
        }
    }

    // ------------------------------------------------------------ season end

    private fun endSeason(w: World, rnd: Random) {
        // Evolve only AI manager genomes at the season boundary; human tactics are preserved.
        ManagerPopulationTraining.evolve(w, kotlin.random.Random(rnd.nextLong()))
        val me = w.human()
        val myLg = w.leagueOf(me.id)
        val myTable = myLg.sortedTable()
        val myPos = myTable.indexOfFirst { it.clubId == me.id } + 1
        val expPos = leagueRank(w, me) + 1
        // prizes + history
        for (lg in w.leagues) {
            val tbl = lg.sortedTable()
            for ((i, row) in tbl.withIndex()) {
                val c = w.clubs[row.clubId]
                val prize = (c.revenue * 0.15 * (1.3 - 0.6 * i / (tbl.size - 1.0))).toLong()
                c.balance += prize
            }
            w.history.add(0, "${w.seasonLabel()} ${lg.name}: ${w.clubs[tbl[0].clubId].name}")
        }
        if (w.history.size > 70) while (w.history.size > 70) w.history.removeAt(w.history.size - 1)
        val champion = myTable[0].clubId == me.id
        if (champion) w.trophies++

        // promotion / relegation (3 up, 3 down)
        val tables = w.leagues.map { it.sortedTable().map { r -> r.clubId } }
        val moves = HashMap<Int, Int>() // clubId -> new tier
        for (t in 0 until 6) {
            val down = tables[t].takeLast(3)
            val up = tables[t + 1].take(3)
            for (c in down) moves[c] = t + 1
            for (c in up) moves[c] = t
        }
        var myMove = 0
        for ((cid, nt) in moves) {
            val c = w.clubs[cid]
            val old = c.tier
            if (nt == old) continue
            w.leagues[old].clubIds.remove(cid)
            w.leagues[nt].clubIds.add(cid)
            c.tier = nt
            val factor = exp(0.115 * (WorldGen.MEAN_OVR[nt] - WorldGen.MEAN_OVR[old]))
            c.revenue = (c.revenue * factor).toLong()
            c.wageBudget = (c.revenue / 52.0 * 0.64).toLong()
            c.transferBudget = (c.revenue * 0.1).toLong()
            if (cid == me.id) myMove = if (nt < old) 1 else -1
        }

        // board review
        var delta = (expPos - myPos) * 2.5f
        if (champion) delta += 12f
        if (myMove == 1) delta += 8f
        if (myMove == -1) delta -= 14f
        me.boardConfidence = clampF(me.boardConfidence + delta, 0f, 100f)
        val review = "Finished $myPos${ord(myPos)} in ${myLg.name} (expected $expPos${ord(expPos)}). " +
            (if (champion) "CHAMPIONS! " else "") +
            (if (myMove == 1) "Promoted! " else if (myMove == -1) "Relegated. " else "") +
            "Board confidence now ${me.boardConfidence.roundToInt()}%."
        w.seasonsManaged++

        // players: age, develop, retire, contracts
        val retired = ArrayList<Player>()
        for (p in w.players.values.toList()) {
            p.age++
            val devStart = if (p.seasonStartOvr > 0) p.seasonStartOvr else p.ovr
            develop(w, p, rnd)
            p.lastDevDelta = p.ovr - devStart
            p.seasonStartOvr = p.ovr
            p.seasonsAtClub++
            if (p.age >= 34 && rnd.nextDouble() < (p.age - 33) * 0.25) retired.add(p)
        }
        for (p in retired) {
            if (p.clubId >= 0) {
                val c = w.clubs[p.clubId]
                c.players.remove(p.id)
                if (c.human) w.addNews("Retirement", "${p.name} (${p.age}) has retired from football.")
            }
            w.players.remove(p.id)
            w.shortlist.remove(p.id)
            w.scouted.remove(p.id)
        }
        val expiring = ArrayList<Player>()
        for (p in w.players.values) if (p.clubId >= 0 && p.contractEnd <= w.year + 1) expiring.add(p)
        for (p in expiring) {
            val c = w.clubs[p.clubId]
            if (c.human) {
                c.players.remove(p.id)
                w.addNews("Contract expired", "${p.name} left on a free transfer - his contract ran out.")
                p.clubId = -1; p.wage = 0; p.contractEnd = 0
            } else if (rnd.nextDouble() < 0.72 && p.age <= 33) {
                p.contractEnd = w.year + 1 + 1 + rnd.nextInt(4)
                p.wage = max(300, (p.wageDemand() * (0.9 + rnd.nextDouble() * 0.2)).toInt())
            } else {
                c.players.remove(p.id)
                p.clubId = -1; p.wage = 0; p.contractEnd = 0
            }
        }
        // youth intake
        for (c in w.clubs) {
            val q = WorldGen.MEAN_OVR[c.tier] + (c.rep - WorldGen.BASE_REP[c.tier]) * 0.25f
            val k = 3 + rnd.nextInt(3)
            var bestProspect: Player? = null
            for (i in 0 until k) {
                val pos = listOf(Pos.GK, Pos.LB, Pos.RB, Pos.CB, Pos.CB, Pos.DM, Pos.CM, Pos.CM, Pos.LW, Pos.RW, Pos.AM, Pos.ST, Pos.ST)[rnd.nextInt(13)]
                val target = clampF(q - 17f + c.youth * 0.5f + rnd.gauss().toFloat() * 4f, 12f, 65f)
                var pa = target + 10 + rnd.nextInt(16) + c.youth * 0.4f
                if (rnd.nextInt(20) == 0) pa += 14f
                val p = WorldGen.makePlayer(w, rnd, pos, target, 16, pa.roundToInt(), Names.pickNation(rnd))
                p.clubId = c.id
                p.wage = 300
                p.contractEnd = w.year + 1 + 3
                w.players[p.id] = p
                c.players.add(p.id)
                if (bestProspect == null || p.pa > bestProspect.pa) bestProspect = p
            }
            if (c.human && bestProspect != null) {
                w.addNews("Youth intake", "$k youngsters joined. Best prospect: ${bestProspect.name} (${bestProspect.pos.label}), potential ${(bestProspect.pa / 20f * 10).roundToInt() / 10f} stars.")
            }
        }
        // budgets & finances for the new season
        for (c in w.clubs) {
            if (!c.human) {
                c.wageBudget = max(c.wageBudget, (c.revenue / 52.0 * 0.62).toLong())
                c.transferBudget = max(c.transferBudget, (c.revenue * 0.1 * (0.5 + rnd.nextDouble())).toLong())
                if (c.balance < 0) c.balance = (c.revenue * 0.05).toLong()
            } else {
                c.transferBudget = max(0L, (c.revenue * 0.10).toLong() + max(0L, (c.balance - c.seasonStartBalance) / 4L))
                if (champion || myMove == 1) c.wageBudget = (c.wageBudget * 1.08).toLong()
            }
        }
        WorldGen.addFreeAgents(w, rnd, 120)
        val fa = w.players.values.filter { it.clubId < 0 }
        if (fa.size > 700) {
            val drop = fa.sortedBy { it.ovr }.take(fa.size - 600)
            for (p in drop) { w.players.remove(p.id); w.shortlist.remove(p.id); w.scouted.remove(p.id) }
        }
        w.offers.clear()
        w.year++
        w.week = 0
        Transfers.aiSquadMaintenance(w, rnd)
        for (c in w.clubs) {
            if (!c.human) Lineups.autoPick(w, c)
        }
        WorldGen.startSeason(w, rnd)
        w.addNews("Season review ${w.year - 1}/${w.year % 100}", review)
        if (me.boardConfidence <= 14f) {
            w.sacked = true
            w.sackReason = "After the season review the board decided to part ways."
        }
        if (me.autoPick) Lineups.autoPick(w, me) else Lineups.fixHuman(w, me)
    }

    fun ord(n: Int): String = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    // ------------------------------------------------------------ development

    private fun develop(w: World, p: Player, rnd: Random) {
        val age = p.age
        val fac = if (p.clubId >= 0) w.clubs[p.clubId].facilities / 20f else 0.4f
        val prof = 0.8f + 0.4f * p.prof / 20f
        val room = (p.pa - p.ovr).toFloat()
        val d: Float = when {
            age <= 21 -> clampF(room * 0.30f * prof * (0.85f + 0.3f * fac) + rnd.gauss().toFloat() * 1.5f, -1f, 9f)
            age <= 24 -> clampF(room * 0.20f * prof + rnd.gauss().toFloat() * 1.2f, -1.5f, 6f)
            age <= 28 -> rnd.gauss().toFloat() * 1.0f + room * 0.05f
            age <= 31 -> -0.8f + rnd.gauss().toFloat() * 1.2f
            else -> -2f - (age - 32) * 0.5f + rnd.gauss().toFloat() * 1.3f
        }
        val target = d.roundToInt()
        if (target != 0) {
            val up = target > 0
            val w2 = Roles.weights[p.pos]!!
            var changed = 0
            var guard = 0
            val before = p.ovr
            while (guard < 60) {
                guard++
                p.refresh()
                if (up && p.ovr - before >= target) break
                if (!up && before - p.ovr >= -target) break
                // choose attribute weighted by role relevance
                var tot = 0f
                val wt = FloatArray(NATTR)
                for (i in 0 until NATTR) {
                    var x = w2[i]
                    if (!up && age >= 29 && ATTRS[i].cat == 2) x = x * 3f + 0.4f
                    if (up && ATTRS[i].cat == 3 && p.pos != Pos.GK) x = 0f
                    if (up && p.attrs[i] >= 20) x = 0f
                    if (!up && p.attrs[i] <= 1) x = 0f
                    wt[i] = x
                    tot += x
                }
                if (tot <= 0f) break
                var r = rnd.nextFloat() * tot
                var pick = 0
                for (i in 0 until NATTR) { r -= wt[i]; if (r <= 0f) { pick = i; break } }
                p.attrs[pick] = clampI(p.attrs[pick] + (if (up) 1 else -1), 1, 20)
                changed++
            }
        }
        p.refresh()
        if (p.ovr > p.pa) p.pa = p.ovr
        if (age >= 29) p.pa = p.ovr
    }
}
