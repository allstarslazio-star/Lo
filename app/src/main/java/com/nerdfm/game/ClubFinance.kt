package com.nerdfm.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** status: 0 compliant, 1 warning, 2 restricted (no fee-paying signings). */
class FfpReport(val wageRatio: Double, val status: Int, val note: String)

/**
 * Commercial and financial rules for the human club: ticket pricing, sponsors, stadium projects,
 * fair-play checks, and the weekly hooks used by [Game]. Android-free so it can be unit tested.
 */
object Business {
    /** Share of the human club's base weekly income that is replaced by explicit sponsor deals. */
    const val COMMERCIAL_SHARE = 0.18

    val PRICE_FACTORS = doubleArrayOf(0.6, 0.8, 1.0, 1.25, 1.5)
    val PRICE_LABELS = listOf("Very low", "Low", "Standard", "High", "Premium")

    private val BRANDS_MAIN = listOf(
        "Aurex Energy", "Northbridge Bank", "Halcyon Telecom", "Veloria Motors", "Pinecrest Foods",
        "Lumina Airlines", "Borealis Insurance", "Tidewater Brewing", "Kestrel Logistics", "Solace Mobile"
    )
    private val BRANDS_KIT = listOf(
        "Strider Sportswear", "Fenwick Athletics", "Ardent Kit Co", "Vanta Apparel", "Oakline Sports", "Mirage Performance"
    )

    fun absWeek(w: World): Int = w.year * 40 + w.week

    // ------------------------------------------------------------------ state helpers

    /** Makes sure the business state belongs to the current human club and has starting deals. */
    fun ensureStarted(w: World) {
        val me = w.clubs.getOrNull(w.humanClubId) ?: return
        val b = w.biz()
        if (b.clubId != me.id) resetForClub(b, me)
        if (b.baseCapacity <= 0) b.baseCapacity = max(1, me.capacity())
        if (!b.started) {
            b.started = true
            b.sponsors.add(makeDeal(w, b, me, 0, 1.0, 2))
            b.sponsors.add(makeDeal(w, b, me, 1, 1.0, 2))
        }
    }

    private fun resetForClub(b: BusinessState, me: Club) {
        b.clubId = me.id
        b.started = false
        b.sponsors.clear()
        b.sponsorOffers.clear()
        b.scouts.clear()
        b.scoutMarket.clear()
        b.ticketLevel = 2
        b.baseCapacity = 0
        b.expansionSeats = 0
        b.expansionWeeksLeft = 0
        b.offersGeneratedAt = -1000
    }

    // ------------------------------------------------------------------ tickets and stadium

    /** Seats the club could fill at a given price level (before the capacity cap). */
    fun demandSeats(c: Club, base: Int, level: Int): Double {
        val interest = 0.70 + c.rep.coerceIn(0, 100) / 100.0 * 0.55
        val price = PRICE_FACTORS[level.coerceIn(0, PRICE_FACTORS.size - 1)]
        return base * interest * price.pow(-1.1)
    }

    fun attendance(c: Club, base: Int, capacity: Int, level: Int): Double =
        min(capacity.toDouble(), demandSeats(c, base, level))

    /** Gate-income multiplier relative to a standard price and the stadium the career started with. */
    fun gateMultiplier(w: World, c: Club): Double {
        if (!c.human) return 1.0
        val b = w.biz()
        if (b.baseCapacity <= 0) b.baseCapacity = max(1, c.capacity())
        val level = b.ticketLevel.coerceIn(0, PRICE_FACTORS.size - 1)
        val ref = min(b.baseCapacity.toDouble(), demandSeats(c, b.baseCapacity, 2))
        if (ref <= 0.0) return 1.0
        val cur = attendance(c, b.baseCapacity, c.capacity(), level)
        return (PRICE_FACTORS[level] * cur / ref).coerceIn(0.2, 3.0)
    }

    fun expectedAttendance(w: World, c: Club): Int {
        val b = w.biz()
        if (b.baseCapacity <= 0) b.baseCapacity = max(1, c.capacity())
        return attendance(c, b.baseCapacity, c.capacity(), b.ticketLevel).toInt()
    }

    fun expectedDemand(w: World, c: Club): Int {
        val b = w.biz()
        if (b.baseCapacity <= 0) b.baseCapacity = max(1, c.capacity())
        return demandSeats(c, b.baseCapacity, b.ticketLevel).toInt()
    }

    fun setTicketLevel(w: World, level: Int): Boolean {
        if (level !in PRICE_FACTORS.indices) return false
        w.biz().ticketLevel = level
        return true
    }

    fun expansionCost(c: Club, seats: Int): Long =
        (seats.toLong() * (c.revenue * 0.30 / max(1, c.capacity())) * 7.0).toLong()

    fun startExpansion(w: World, seats: Int): Pair<Boolean, String> {
        val me = w.human()
        val b = w.biz()
        if (b.expansionSeats > 0) return false to "A stadium project is already under way."
        if (seats < 500 || seats > 20000) return false to "Choose between 500 and 20,000 new seats."
        if (me.capacity() + seats > 120000) return false to "The ground cannot be extended beyond 120,000 seats."
        val cost = expansionCost(me, seats)
        if (me.balance < cost * 3 / 2) return false to "The board wants at least ${money(cost * 3 / 2)} in the bank to start this project."
        me.balance -= cost
        b.expansionSeats = seats
        b.expansionWeeksLeft = 8 + seats / 500
        w.addNews("Stadium project approved", "Construction of $seats new seats has begun. Cost ${money(cost)}, about ${b.expansionWeeksLeft} weeks.")
        return true to "Stadium expansion started: $seats seats for ${money(cost)}."
    }

    private fun progressExpansion(w: World) {
        val b = w.biz()
        if (b.expansionSeats <= 0) return
        b.expansionWeeksLeft--
        if (b.expansionWeeksLeft <= 0) {
            val me = w.human()
            me.stadiumBase += b.expansionSeats
            w.addNews("Stadium extension opened", "${b.expansionSeats} new seats are open. Capacity is now ${me.capacity()}.")
            b.expansionSeats = 0
            b.expansionWeeksLeft = 0
        }
    }

    // ------------------------------------------------------------------ sponsors

    fun baseSponsorWeekly(c: Club, slot: Int): Long {
        val commercial = c.revenue * 0.55 / 52.0 * COMMERCIAL_SHARE
        val share = if (slot == 0) 0.65 else 0.35
        return (commercial * share).toLong()
    }

    private fun makeDeal(w: World, b: BusinessState, c: Club, slot: Int, mult: Double, years: Int): SponsorDeal {
        val weekly = max(50L, (baseSponsorWeekly(c, slot) * mult).toLong())
        val brands = if (slot == 0) BRANDS_MAIN else BRANDS_KIT
        val brand = brands[(b.nextDealId * 7 + c.id).mod(brands.size)]
        val deal = SponsorDeal(b.nextDealId, brand, slot, weekly, weekly * 6L, weekly / 2L, w.year + years - 1)
        b.nextDealId++
        return deal
    }

    fun activeSponsors(w: World): List<SponsorDeal> = w.biz().sponsors.filter { it.endYear >= w.year }

    fun sponsorIncome(w: World): Long = activeSponsors(w).sumOf { it.weekly }

    fun refreshOffers(w: World, rnd: Random) {
        val me = w.clubs.getOrNull(w.humanClubId) ?: return
        val b = w.biz()
        b.sponsorOffers.clear()
        val repFactor = 0.85 + me.rep.coerceIn(0, 100) / 100.0 * 0.3 + w.managerReputation.coerceIn(0f, 100f) / 100.0 * 0.2
        for (slot in 0..1) {
            for (k in 0..1) {
                val years = 1 + rnd.nextInt(4)
                val mult = repFactor * (0.85 + rnd.nextDouble() * 0.4) * (1.0 + (4 - years) * 0.02)
                b.sponsorOffers.add(makeDeal(w, b, me, slot, mult, years))
            }
        }
        b.offersGeneratedAt = absWeek(w)
    }

    fun slotTaken(w: World, slot: Int): Boolean = activeSponsors(w).any { it.slot == slot }

    fun acceptSponsor(w: World, dealId: Int): Pair<Boolean, String> {
        val b = w.biz()
        val offer = b.sponsorOffers.firstOrNull { it.id == dealId } ?: return false to "That offer is no longer available."
        if (slotTaken(w, offer.slot)) return false to "You already have a ${slotName(offer.slot)} deal. Terminate it first or wait for it to expire."
        val me = w.human()
        b.sponsors.add(offer)
        b.sponsorOffers.removeAll { it.slot == offer.slot }
        me.balance += offer.signingBonus
        b.seasonSponsorIncome += offer.signingBonus
        w.addNews("Sponsor signed", "${offer.brand} agreed a ${slotName(offer.slot)} deal worth ${money(offer.weekly)} per week until ${offer.endYear}.")
        return true to "${offer.brand} signed. Signing bonus ${money(offer.signingBonus)} received."
    }

    fun terminationPenalty(deal: SponsorDeal): Long = deal.weekly * 8L

    fun terminateSponsor(w: World, dealId: Int): Pair<Boolean, String> {
        val b = w.biz()
        val deal = b.sponsors.firstOrNull { it.id == dealId } ?: return false to "That deal no longer exists."
        val me = w.human()
        val penalty = terminationPenalty(deal)
        if (me.balance < penalty) return false to "You cannot afford the ${money(penalty)} termination penalty."
        me.balance -= penalty
        b.sponsors.remove(deal)
        w.addNews("Sponsor deal ended", "You bought out the ${deal.brand} deal for ${money(penalty)}.")
        return true to "${deal.brand} deal terminated for ${money(penalty)}."
    }

    fun slotName(slot: Int): String = if (slot == 0) "main sponsor" else "kit supplier"

    private fun expireSponsors(w: World) {
        val b = w.biz()
        val expired = b.sponsors.filter { it.endYear < w.year }
        for (d in expired) {
            b.sponsors.remove(d)
            w.addNews("Sponsor deal expired", "Your ${slotName(d.slot)} deal with ${d.brand} has ended. New offers are available under Business.")
        }
    }

    /** Pays win bonuses when the human club wins. */
    fun onHumanResult(w: World, goalsFor: Int, goalsAgainst: Int) {
        if (goalsFor <= goalsAgainst || w.clubs.isEmpty()) return
        val bonus = activeSponsors(w).sumOf { it.winBonus }
        if (bonus <= 0L) return
        w.human().balance += bonus
        w.biz().seasonSponsorIncome += bonus
    }

    // ------------------------------------------------------------------ fair play

    fun ffpReport(w: World, c: Club): FfpReport {
        val revenue = max(1L, c.revenue).toDouble()
        val ratio = w.wageBill(c) * 52.0 / revenue
        val deficit = c.balance < 0
        val status = when {
            ratio > 1.0 || (deficit && ratio > 0.85) -> 2
            ratio > 0.85 || deficit -> 1
            else -> 0
        }
        val pct = (ratio * 100).toInt()
        val note = when (status) {
            0 -> "Compliant: wages are $pct% of revenue."
            1 -> "Warning: wages are $pct% of revenue" + (if (deficit) " and the club is in deficit." else ". Keep them under 85%.")
            else -> "Restricted: wages are $pct% of revenue" + (if (deficit) " with a deficit" else "") + ". Fee-paying signings are blocked."
        }
        return FfpReport(ratio, status, note)
    }

    /** Returns a message when fair-play rules stop the human club paying a transfer fee. */
    fun signingRestriction(w: World, fee: Long): String? {
        if (fee <= 0L || w.clubs.isEmpty()) return null
        val me = w.clubs.getOrNull(w.humanClubId) ?: return null
        val report = ffpReport(w, me)
        return if (report.status == 2) "Fair-play restrictions: ${report.note}" else null
    }

    fun seasonReview(w: World) {
        if (w.clubs.isEmpty()) return
        val b = w.biz()
        if (b.lastReviewYear == w.year) return
        b.lastReviewYear = w.year
        val me = w.clubs.getOrNull(w.humanClubId) ?: return
        val report = ffpReport(w, me)
        if (report.status >= 1) {
            b.ffpStrikes++
            val fine = (me.revenue * 0.005 * report.status).toLong()
            me.balance -= fine
            me.boardConfidence = clampF(me.boardConfidence - 2f * report.status, 0f, 100f)
            w.addNews("Fair-play review", report.note + " Fine ${money(fine)}. Strikes: ${b.ffpStrikes}.")
        } else if (b.ffpStrikes > 0 && report.wageRatio < 0.75) {
            b.ffpStrikes--
        }
        w.addNews(
            "Commercial review",
            "Sponsor income ${money(b.seasonSponsorIncome)}, agent fees ${money(b.seasonAgentFees)}, scouting ${money(b.seasonScoutCosts)}, bonuses ${money(b.seasonBonuses)}, loan wage support ${money(b.seasonLoanSubsidy)}."
        )
        b.seasonSponsorIncome = 0L
        b.seasonAgentFees = 0L
        b.seasonScoutCosts = 0L
        b.seasonBonuses = 0L
        b.seasonLoanSubsidy = 0L
    }

    // ------------------------------------------------------------------ AI club planning

    fun aiPlanning(w: World, rnd: Random) {
        val windowOpen = w.isWindowOpen()
        for (c in w.clubs) {
            if (c.human) continue
            val weeklyRevenue = c.revenue / 52.0
            val bill = w.wageBill(c)
            if (c.balance < 0) c.transferBudget = 0L
            if (w.week == 0 || w.week == 18) {
                val spare = max(0.0, c.balance - c.revenue * 0.15)
                val target = (c.revenue * 0.05 + spare * 0.3).toLong().coerceIn(0L, (c.revenue * 0.25).toLong())
                if (c.balance >= 0 && target > c.transferBudget) c.transferBudget = target
            }
            if (windowOpen && bill > weeklyRevenue * 0.95) {
                val squad = w.squad(c)
                if (squad.size > 24) {
                    val core = squad.sortedByDescending { it.ovr }.take(11).map { it.id }.toSet()
                    val victim = squad.filter { it.id !in core && !it.listed }.maxByOrNull { it.wage }
                    if (victim != null) victim.listed = true
                }
            }
        }
    }

    // ------------------------------------------------------------------ weekly hooks used by Game

    /** Call before the weekly player loop resets minutes played. */
    fun preWeek(w: World) {
        if (w.clubs.isEmpty()) return
        val me = w.clubs.getOrNull(w.humanClubId) ?: return
        val b = w.biz()
        var paid = 0L
        for (p in w.squad(me)) {
            val bonus = b.clauses[p.id]?.appearanceBonus ?: 0
            if (bonus > 0 && p.lastMinutes > 0) paid += bonus.toLong()
        }
        if (paid > 0L) {
            me.balance -= paid
            b.seasonBonuses += paid
        }
    }

    /** Extra income/expense for a club on top of the base weekly figures. */
    fun weeklyAdjustment(w: World, c: Club, base: Long): Long {
        var adj = 0L
        if (c.human) {
            ensureStarted(w)
            adj -= (base * COMMERCIAL_SHARE).toLong()
            val income = sponsorIncome(w)
            adj += income
            w.biz().seasonSponsorIncome += income
        }
        val support = Loans.parentWageSupport(w, c)
        if (support > 0L) {
            adj -= support
            if (c.human) w.biz().seasonLoanSubsidy += support
        }
        if (c.human) {
            val scoutCost = w.biz().scouts.sumOf { it.wage.toLong() }
            w.biz().seasonScoutCosts += scoutCost
        }
        return adj
    }

    /** Call after the weekly finances and market have run, before any season rollover. */
    fun postWeek(w: World, rnd: Random) {
        if (w.clubs.isEmpty()) return
        ensureStarted(w)
        val b = w.biz()
        expireSponsors(w)
        b.clauses.keys.removeAll { !w.players.containsKey(it) }
        b.negotiations.keys.removeAll { !w.players.containsKey(it) }
        if (absWeek(w) - b.offersGeneratedAt >= 13) refreshOffers(w, rnd)
        progressExpansion(w)
        Loans.weekly(w)
        ContractDeals.weeklyClauseCheck(w, rnd)
        ScoutingNetwork.weekly(w, rnd)
        aiPlanning(w, rnd)
    }
}
