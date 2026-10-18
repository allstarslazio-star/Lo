package com.nerdfm.game

import java.io.Serializable

/** A commercial deal. slot 0 = main shirt sponsor, slot 1 = kit supplier. */
class SponsorDeal(
    val id: Int,
    val brand: String,
    val slot: Int,
    val weekly: Long,
    val signingBonus: Long,
    val winBonus: Long,
    val endYear: Int
) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

/** Optional extra terms attached to a player's contract. */
class ContractClauses : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    /** 0 = none. If a bid reaches this fee the selling club cannot refuse. */
    var releaseClause = 0L
    /** Percentage of a future sale fee owed to [sellOnClubId]. */
    var sellOnPct = 0
    var sellOnClubId = -1
    /** Paid to the player for each match he features in. */
    var appearanceBonus = 0

    fun isEmpty(): Boolean = releaseClause <= 0L && sellOnPct <= 0 && appearanceBonus <= 0
}

class NegotiationState(val playerId: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var round = 0
    /** Negotiations are blocked until this absolute week (see Business.absWeek). */
    var blockedUntil = -1
    var lastCounterWage = 0
    var lastCounterYears = 0
}

class LoanDeal(
    val id: Int,
    val playerId: Int,
    val parentClubId: Int,
    val borrowerClubId: Int,
    val originalWage: Int,
    val borrowerSharePct: Int,
    val endYear: Int,
    val endWeek: Int,
    val optionFee: Long
) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var active = true
}

class Scout(val id: Int, var name: String, var ability: Int, var judgement: Int, var wage: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    /** -1 idle, -2 free agents, otherwise league tier index. */
    var region = -1
    /** -1 any, 0 GK, 1 defenders, 2 midfielders, 3 attackers. */
    var posGroup = -1
    var reportsMade = 0
}

class ScoutReport(val playerId: Int, val year: Int, val week: Int, val scoutName: String, val text: String) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

/**
 * All state for the business layer lives here so older saves simply have `World.business == null`
 * and receive a fresh object on first use (see World.biz()).
 */
class BusinessState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }

    var clubId = -1
    var started = false

    // ticketing and stadium
    var ticketLevel = 2
    var baseCapacity = 0
    var expansionSeats = 0
    var expansionWeeksLeft = 0

    // sponsors
    val sponsors = ArrayList<SponsorDeal>()
    val sponsorOffers = ArrayList<SponsorDeal>()
    var nextDealId = 1
    var offersGeneratedAt = -1000

    // contracts
    val clauses = HashMap<Int, ContractClauses>()
    val negotiations = HashMap<Int, NegotiationState>()
    /** 0 = never, 1 = 10% sell-on, 2 = 20% sell-on (buyers pay less for the clause). */
    var sellOnPolicy = 0

    // loans
    val loans = ArrayList<LoanDeal>()
    var nextLoanId = 1

    // scouting
    val scouts = ArrayList<Scout>()
    val scoutMarket = ArrayList<Scout>()
    var nextScoutId = 1
    val knowledge = HashMap<Int, Int>()
    val reports = ArrayList<ScoutReport>()

    // fair play and bookkeeping
    var ffpStrikes = 0
    var lastReviewYear = -1
    var seasonSponsorIncome = 0L
    var seasonAgentFees = 0L
    var seasonScoutCosts = 0L
    var seasonLoanSubsidy = 0L
    var seasonBonuses = 0L
}
