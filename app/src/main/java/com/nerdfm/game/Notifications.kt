package com.nerdfm.game

import kotlin.math.roundToInt

enum class NoticeLevel { URGENT, ATTENTION, INFO }

/** A derived, read-only message about the human club. [target] is where the "action" button should lead. */
data class Notice(
    val id: String,
    val level: NoticeLevel,
    val title: String,
    val body: String,
    val target: NavTarget? = null,
    val actionLabel: String? = null
)

/**
 * Derives actionable notices from the career state. Nothing here is persisted or mutates the world, so it needs no
 * save-format change: the same world always yields the same notices.
 */
object Notices {
    const val MAX_NOTICES = 30
    private const val BOARD_WORRIED = 35f
    private const val BOARD_CRISIS = 20f
    private const val LOW_MORALE = 40f

    fun derive(w: World, max: Int = MAX_NOTICES): List<Notice> {
        if (w.humanClubId !in w.clubs.indices) return emptyList()
        val me = w.human()
        val out = ArrayList<Notice>()

        // transfer offers: urgent when they are about to lapse
        for (o in w.offers) {
            val p = w.players[o.playerId] ?: continue
            if (p.clubId != me.id || o.fromClub !in w.clubs.indices) continue
            val left = (o.expiresWeek - w.week).coerceAtLeast(0)
            out += Notice(
                "offer:${o.playerId}:${o.fromClub}", if (left <= 1) NoticeLevel.URGENT else NoticeLevel.ATTENTION,
                "Transfer offer for ${p.name}", "${w.clubs[o.fromClub].name} bid ${money(o.fee)}. " +
                    (if (left == 0) "It expires this week." else "It expires in $left week(s)."),
                NavTarget(Destination.TRANSFERS.screen, p.id), "View offer"
            )
        }

        // injuries and suspensions that change the selected XI
        for (id in me.lineup) {
            val p = w.players[id] ?: continue
            if (p.injWeeks > 0 || p.suspWeeks > 0) {
                val why = if (p.injWeeks > 0) "injured (${p.injType.ifEmpty { "injury" }}, ${p.injWeeks} wk)" else "suspended (${p.suspWeeks} match(es))"
                out += Notice("unavailable:${p.id}", NoticeLevel.ATTENTION, "${p.name} cannot play", "Your starter is $why. Check the lineup.",
                    NavTarget(Destination.TACTICS.screen, p.id), "Review lineup")
            }
        }

        // playing-time requests and unhappy players
        for (p in w.squad(me)) {
            if (p.promiseWeeksRemaining > 0 && p.promiseBrokenWeeks >= 2) {
                out += Notice("promise:${p.id}", NoticeLevel.ATTENTION, "${p.name} wants more playing time",
                    "He was promised ${PlayerRelations.promiseName(p.playingTimePromise).lowercase()} football and is not getting it.",
                    NavTarget(Destination.PEOPLE.screen, p.id), "Talk to player")
            } else if (p.morale < LOW_MORALE && p.playingTimePromise == PlayerRelations.PROMISE_NONE) {
                out += Notice("morale:${p.id}", NoticeLevel.INFO, "${p.name} is unhappy", "Morale is ${p.morale.roundToInt()}. A promise or more minutes may help.",
                    NavTarget(Destination.PEOPLE.screen, p.id), "Open profile")
            }
        }

        // contracts that end at the coming rollover (loans belong to the parent club)
        // Read the raw field: w.biz() would lazily create state, and deriving notices must not touch the world.
        val loaned = HashSet<Int>()
        w.business?.loans?.forEach { if (it.active) loaned.add(it.playerId) }
        for (p in w.squad(me)) {
            if (p.contractEnd <= w.year + 1 && p.id !in loaned) {
                val late = w.week >= 20
                out += Notice("contract:${p.id}", if (late) NoticeLevel.URGENT else NoticeLevel.ATTENTION,
                    "${p.name}'s contract is running out", "It ends in ${p.contractEnd}. " + (if (late) "Renew now or he leaves for free." else "Open talks before the window closes."),
                    NavTarget(Destination.SQUAD.screen, p.id), "Renew contract")
            }
        }

        // board and finances
        if (me.boardConfidence < BOARD_WORRIED) {
            out += Notice("board", if (me.boardConfidence < BOARD_CRISIS) NoticeLevel.URGENT else NoticeLevel.ATTENTION, "The board is worried",
                "Confidence is ${me.boardConfidence.roundToInt()}%. Results or finances need to improve.", NavTarget(Destination.PEOPLE.screen), "See objective")
        }
        if (me.balance < 0L) {
            out += Notice("finance:balance", NoticeLevel.URGENT, "The club is in the red", "Balance is ${money(me.balance)}. A deep deficit ends your job.",
                NavTarget(Destination.FINANCE.screen), "Open finances")
        }
        val bill = w.wageBill(me)
        if (bill > me.wageBudget) {
            out += Notice("finance:wages", NoticeLevel.ATTENTION, "Wage bill over budget", "${money(bill)} / wk against a budget of ${money(me.wageBudget)}.",
                NavTarget(Destination.FINANCE.screen), "Open finances")
        }

        // calendar
        val fx = w.humanFixture()
        if (fx != null) {
            val home = fx.home == me.id
            val opp = w.clubs[if (home) fx.away else fx.home].name
            out += Notice("fixture:${w.year}:${fx.round}", NoticeLevel.INFO, "Round ${fx.round}: ${if (home) "home" else "away"} v $opp",
                "Your next fixture. Pick the team before you play.", NavTarget(Destination.TACTICS.screen), "Set team")
        }
        if (w.week == 4 || w.week == 21) {
            out += Notice("window:${w.year}:${w.week}", NoticeLevel.ATTENTION, "Transfer window closing", "The window shuts after this week.",
                NavTarget(Destination.TRANSFERS.screen), "Open transfers")
        }

        return out.sortedWith(compareBy<Notice> { it.level.ordinal }.thenBy { it.id }).take(max)
    }
}

/**
 * In-memory dismissal. A dismissed notice stays hidden for the rest of the week it was dismissed in and returns if the
 * underlying problem is still there next week, so persistent problems are never silenced forever.
 */
class NotificationCenter {
    private val dismissedAt = HashMap<String, Int>()

    private fun stamp(w: World): Int = w.year * 40 + w.week

    fun visible(w: World, max: Int = Notices.MAX_NOTICES): List<Notice> {
        val now = stamp(w)
        return Notices.derive(w, 500).filter { dismissedAt[it.id] != now }.take(max)
    }

    fun dismiss(w: World, id: String) { dismissedAt[id] = stamp(w) }

    fun dismissAll(w: World) { for (n in Notices.derive(w, 500)) dismissedAt[n.id] = stamp(w) }

    fun urgentCount(w: World): Int = visible(w).count { it.level == NoticeLevel.URGENT }

    fun clear() = dismissedAt.clear()
}
