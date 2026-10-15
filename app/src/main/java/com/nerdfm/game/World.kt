package com.nerdfm.game

import java.io.Serializable
import kotlin.random.Random

class World : Serializable {
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
    val matchCalibration = MatchCalibration()
    // Transfer-search filters are career state, so leaving the screen never resets them.
    var transferFilterLeague = 0
    var transferFilterPosition = 0
    var transferFilterMinOverall = 45
    var transferFilterMaxAge = 34
    var transferSearchTab = 0

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
        val date = java.time.LocalDate.of(year, 7, 1).plusDays((week.coerceAtLeast(0) * 7L))
        return date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }

    fun isWindowOpen(): Boolean = week in 0..4 || week in 18..21

    fun windowText(): String = when {
        week in 0..4 -> "Summer window open"
        week in 18..21 -> "Winter window open"
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

    fun wageBill(c: Club): Long {
        var s = 0L
        for (id in c.players) s += players[id]!!.wage
        for (coach in c.coaches) s += coach.wage
        return s
    }
}
