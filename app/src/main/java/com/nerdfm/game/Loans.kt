package com.nerdfm.game

import kotlin.math.max
import kotlin.random.Random

/** Loan-in, loan-out, recall, and option-to-buy rules. Android-free. */
object Loans {
    const val MAX_LOANS_IN = 4
    const val HALF_SEASON_END_WEEK = 18
    const val SEASON_END_WEEK = 34

    fun active(w: World): List<LoanDeal> = w.biz().loans.filter { it.active }

    fun loanFor(w: World, playerId: Int): LoanDeal? = w.biz().loans.firstOrNull { it.active && it.playerId == playerId }

    /** Weekly wage the parent club keeps paying while its player is on loan elsewhere. */
    fun parentWageSupport(w: World, c: Club): Long {
        var total = 0L
        for (d in w.biz().loans) {
            if (d.active && d.parentClubId == c.id) {
                total += d.originalWage.toLong() * (100 - d.borrowerSharePct) / 100L
            }
        }
        return total
    }

    private fun removeFromClub(c: Club, p: Player) {
        c.players.remove(p.id)
        for (i in c.lineup.indices) if (c.lineup[i] == p.id) c.lineup[i] = -1
        for (i in c.bench.indices) if (c.bench[i] == p.id) c.bench[i] = -1
    }

    private fun refreshLineup(w: World, me: Club) {
        if (me.autoPick) Lineups.autoPick(w, me) else Lineups.fixHuman(w, me)
    }

    fun loanFee(p: Player): Long = (p.value() * 0.04).toLong()

    // ------------------------------------------------------------------ loan in

    /** Returns the reason a loan cannot go ahead, or null if it can. */
    fun loanInProblem(w: World, p: Player, sharePct: Int): String? {
        val me = w.human()
        if (!w.isWindowOpen()) return "The transfer window is closed."
        if (p.clubId < 0) return "Free agents can be signed directly."
        if (p.clubId == me.id) return "${p.name} already plays for you."
        val parent = w.clubs.getOrNull(p.clubId) ?: return "That club no longer exists."
        if (loanFor(w, p.id) != null) return "${p.name} is already on loan."
        if (sharePct !in 50..100) return "The wage share must be between 50% and 100%."
        if (active(w).count { it.borrowerClubId == me.id } >= MAX_LOANS_IN) return "You can have at most $MAX_LOANS_IN players on loan."
        if (me.players.size >= 40) return "Your squad is full (40 players)."
        val squad = w.squad(parent)
        if (squad.size <= 22) return "${parent.name} cannot spare players."
        val core = squad.sortedByDescending { it.ovr }.take(11).map { it.id }
        if (p.id in core && p.age > 21) return "${parent.name} will not loan out a first-team regular."
        if (!Transfers.willJoin(me, p)) return "${p.name} would not join a club of your stature, even on loan."
        val borrowerWage = p.wage * sharePct / 100
        if (w.wageBill(me) + borrowerWage > me.wageBudget) return "The board will not sanction this: it breaks the wage budget."
        val fee = loanFee(p)
        if (me.balance < fee) return "You cannot afford the ${money(fee)} loan fee."
        return null
    }

    fun loanIn(w: World, p: Player, sharePct: Int, halfSeason: Boolean, withOption: Boolean): Pair<Boolean, String> {
        val problem = loanInProblem(w, p, sharePct)
        if (problem != null) return false to problem
        val me = w.human()
        val parent = w.clubs[p.clubId]
        val b = w.biz()
        val endWeek = if (halfSeason && w.week <= 4) HALF_SEASON_END_WEEK else SEASON_END_WEEK
        val optionFee = if (withOption) Transfers.askingPrice(w, p, me) else 0L
        val fee = loanFee(p)
        val deal = LoanDeal(b.nextLoanId, p.id, parent.id, me.id, p.wage, sharePct, w.year, endWeek, optionFee)
        b.nextLoanId++
        b.loans.add(deal)
        removeFromClub(parent, p)
        me.balance -= fee
        parent.balance += fee
        p.clubId = me.id
        p.wage = p.wage * sharePct / 100
        p.listed = false
        p.morale = 70f
        if (!me.players.contains(p.id)) me.players.add(p.id)
        w.addNews("Loan signing", "${p.name} joins on loan from ${parent.name} until " + (if (endWeek >= SEASON_END_WEEK) "the end of the season" else "mid-season") + ". You pay ${sharePct}% of his wages.")
        refreshLineup(w, me)
        return true to "${p.name} joined on loan. Loan fee ${money(fee)}."
    }

    // ------------------------------------------------------------------ loan out

    /** AI clubs willing to take one of your players, with the share of wages they would pay. */
    fun loanOutOptions(w: World, p: Player): List<Pair<Club, Int>> {
        val me = w.human()
        if (p.clubId != me.id || !w.isWindowOpen()) return emptyList()
        if (loanFor(w, p.id) != null || me.players.size <= 18) return emptyList()
        val result = ArrayList<Pair<Club, Int>>()
        for (club in w.clubs) {
            if (club.human || club.id == me.id) continue
            if (club.players.size >= 36 || club.balance < 0) continue
            val strength = Lineups.strength(w, club)
            if (p.ovr < strength - 10f) continue
            val share = if (p.ovr >= strength) 90 else 70
            val cost = p.wage * share / 100
            if (w.wageBill(club) + cost > club.wageBudget * 1.03) continue
            result.add(club to share)
        }
        return result.sortedByDescending { it.first.rep }.take(6)
    }

    fun loanOut(w: World, p: Player, borrower: Club, halfSeason: Boolean): Pair<Boolean, String> {
        val me = w.human()
        val option = loanOutOptions(w, p).firstOrNull { it.first.id == borrower.id }
            ?: return false to "${borrower.name} are no longer willing to take ${p.name} on loan."
        val share = option.second
        val b = w.biz()
        val endWeek = if (halfSeason && w.week <= 4) HALF_SEASON_END_WEEK else SEASON_END_WEEK
        val deal = LoanDeal(b.nextLoanId, p.id, me.id, borrower.id, p.wage, share, w.year, endWeek, 0L)
        b.nextLoanId++
        b.loans.add(deal)
        removeFromClub(me, p)
        p.clubId = borrower.id
        p.wage = p.wage * share / 100
        p.listed = false
        if (!borrower.players.contains(p.id)) borrower.players.add(p.id)
        w.addNews("Loan agreed", "${p.name} joins ${borrower.name} on loan. They pay $share% of his wages.")
        refreshLineup(w, me)
        return true to "${p.name} loaned to ${borrower.name}; they pay $share% of his wages."
    }

    // ------------------------------------------------------------------ ending and buying

    fun returnPlayer(w: World, d: LoanDeal, reason: String) {
        if (!d.active) return
        d.active = false
        val p = w.players[d.playerId] ?: return
        if (p.clubId != d.borrowerClubId) return
        val borrower = w.clubs.getOrNull(d.borrowerClubId)
        val parent = w.clubs.getOrNull(d.parentClubId)
        if (borrower != null) removeFromClub(borrower, p)
        if (parent != null) {
            p.clubId = parent.id
            p.wage = d.originalWage
            if (!parent.players.contains(p.id)) parent.players.add(p.id)
        } else {
            p.clubId = -1
            p.wage = 0
        }
        val involvesHuman = (borrower?.human == true) || (parent?.human == true)
        if (involvesHuman) w.addNews("Loan ended", "${p.name} returned to ${parent?.name ?: "free agency"} ($reason).")
    }

    fun recall(w: World, dealId: Int): Pair<Boolean, String> {
        val d = active(w).firstOrNull { it.id == dealId } ?: return false to "That loan is no longer active."
        val me = w.human()
        if (d.parentClubId != me.id && d.borrowerClubId != me.id) return false to "That loan does not involve your club."
        val name = w.players[d.playerId]?.name ?: "The player"
        returnPlayer(w, d, "recalled")
        if (d.parentClubId == me.id) refreshLineup(w, me)
        return true to "$name has been sent back."
    }

    fun exercisePurchase(w: World, dealId: Int): Pair<Boolean, String> {
        val d = active(w).firstOrNull { it.id == dealId } ?: return false to "That loan is no longer active."
        val me = w.human()
        if (d.borrowerClubId != me.id) return false to "You are not the borrowing club."
        if (d.optionFee <= 0L) return false to "This loan has no option to buy."
        if (!w.isWindowOpen()) return false to "The transfer window is closed."
        val p = w.players[d.playerId] ?: return false to "The player no longer exists."
        val parent = w.clubs.getOrNull(d.parentClubId) ?: return false to "The parent club no longer exists."
        val fee = d.optionFee
        Business.signingRestriction(w, fee)?.let { return false to it }
        if (fee > me.transferBudget) return false to "Your transfer budget (${money(me.transferBudget)}) does not cover ${money(fee)}."
        if (fee > me.balance) return false to "The club cannot afford ${money(fee)}."
        val wage = max(d.originalWage, Transfers.wantedWage(me, p))
        if (w.wageBill(me) - p.wage + wage > me.wageBudget) return false to "The board will not sanction this: it breaks the wage budget."
        val name = p.name
        // Hand him back to the parent first so the normal transfer moves money in the right direction.
        removeFromClub(me, p)
        if (!parent.players.contains(p.id)) parent.players.add(p.id)
        p.clubId = parent.id
        p.wage = d.originalWage
        d.active = false
        Transfers.move(w, p, me, fee, wage, 3, Random(w.seed + p.id))
        w.addNews("Option to buy exercised", "$name signed permanently for ${money(fee)}.")
        refreshLineup(w, me)
        return true to "$name is now yours permanently for ${money(fee)}."
    }

    /** Ends loans that have reached their end date or that lost their player. Call weekly. */
    fun weekly(w: World) {
        val b = w.biz()
        for (d in b.loans.toList()) {
            if (!d.active) continue
            val p = w.players[d.playerId]
            if (p == null || p.clubId != d.borrowerClubId) {
                d.active = false
                continue
            }
            val over = w.year > d.endYear || (w.year == d.endYear && w.week >= d.endWeek)
            if (over) returnPlayer(w, d, "loan period finished")
        }
        if (b.loans.size > 40) b.loans.removeAll { !it.active }
    }
}
