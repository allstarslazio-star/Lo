package com.nerdfm.game

import java.io.Serializable
import kotlin.random.Random

class World : Serializable {
    var saveVersion: Int = Persistence.CURRENT_SAVE_VERSION
    var seed = 0L
    var year = 2026
    var week = 0
    var humanClubId = 0
    var manager = "Manager"
    val players = HashMap<Int, Player>()
    var nextPid = 1
    var nextCoachId = 1
    val clubs = ArrayList<Club>()
    val leagues = ArrayList<League>()
    val news = ArrayList<News>()
    val offers = ArrayList<Offer>()
    val shortlist = ArrayList<Int>()
    val coachMarket = ArrayList<Coach>()
    val scouted = HashSet<Int>()
    val history = ArrayList<String>()
    var sacked = false
    var sackReason = ""
    var scoutLeague = -1      // -1 none, -2 free agents, else league index
    var scoutPos = -1         // -1 any, else Pos ordinal
    var seasonsManaged = 0
    var trophies = 0
    var lastResult = ""
    /** Persisted global calibration state; updates are applied only from aggregate batches. */
    var matchCalibration = MatchCalibration()
    // Transfer-search filters are career state, so leaving the screen never resets them.
    var transferFilterLeague = 0
    var transferFilterPosition = 0
    var transferFilterMinOverall = 45
    var transferFilterMaxAge = 34
    var transferSearchTab = 0

    private companion object {
        private const val serialVersionUID: Long = 1L
    }

    fun human(): Club = clubs[humanClubId]
    fun club(id: Int): Club = clubs[id]
    fun leagueOf(clubId: Int): League {
        val t = clubs[clubId].tier
        return leagues[t]
    }
    fun rng(salt: Int): Random = Random(seed * 7919L + year * 131L + week * 17L + salt)

    fun addNews(title: String, body: String) {
        news.add(0, News(year, week, title, body))
        if (news.size > 150) news.removeAt(news.size - 1)
    }

    fun squad(c: Club): List<Player> = c.players.mapNotNull { players[it] }

    fun seasonLabel(): String = "$year/${(year + 1) % 100}"

    /** A readable weekly career date; week 0 is pre-season. */
    fun dateLabel(): String {
        val date = careerDate()
        return date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }

    /** Calendar date follows the existing weekly fixture cadence (week zero starts 1 July). */
    fun careerDate(): java.time.LocalDate = java.time.LocalDate.of(year, 7, 1)
        .plusDays(week.coerceAtLeast(0) * 7L)

    fun isWindowOpen(): Boolean {
        val date = careerDate()
        return date.monthValue == 7 || date.monthValue == 8 || date.monthValue == 1
    }

    fun windowDaysRemaining(): Int {
        val date = careerDate()
        val end = when {
            date.monthValue == 7 || date.monthValue == 8 -> java.time.LocalDate.of(date.year, 8, 31)
            date.monthValue == 1 -> java.time.LocalDate.of(date.year, 1, 31)
            else -> return 0
        }
        return if (date <= end && isWindowOpen()) java.time.temporal.ChronoUnit.DAYS.between(date, end).toInt() else 0
    }

    fun windowText(): String = when {
        isWindowOpen() && careerDate().monthValue in 7..8 -> "Summer window open · ${windowDaysRemaining()} days left"
        isWindowOpen() && careerDate().monthValue == 1 -> "Winter window open · ${windowDaysRemaining()} days left"
        else -> "Window closed"
    }

    fun humanFixture(): Fixture? {
        val lg = leagueOf(humanClubId)
        if (week < 1 || week > 34) return null
        for (f in lg.fixtures) {
            if (f.round == week && !f.played && (f.home == humanClubId || f.away == humanClubId)) return f
        }
        return null
    }

    /** Board-approved weekly wage-cap bounds, scaled to annual revenue. */
    fun wageBudgetLimits(c: Club): LongRange {
        val weeklyRevenue = (c.revenue / 52L).coerceAtLeast(1_000L)
        val financialFactor = when {
            c.balance < 0L -> 0.55
            c.balance > c.revenue -> 0.90
            else -> 0.80
        }
        val minimum = (weeklyRevenue * 0.35).toLong().coerceAtLeast(1_000L)
        val maximum = (weeklyRevenue * financialFactor).toLong().coerceAtLeast(minimum)
        return minimum..maximum
    }

    fun wageBill(c: Club): Long {
        var s = 0L
        for (id in c.players) s += players[id]!!.wage
        for (coach in c.coaches) s += coach.wage
        return s
    }
}
