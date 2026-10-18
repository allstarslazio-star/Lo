package com.nerdfm.game

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

enum class TransferPolicy(val label: String, val blurb: String, val budgetFactor: Double) {
    SELL_TO_PROFIT("Sell to profit", "Spends little, expects the club to make money on players.", 0.65),
    YOUTH_FIRST("Youth first", "Wants academy players in the team and modest spending.", 0.90),
    BALANCED("Balanced", "Spends in line with income.", 1.00),
    BIG_SPENDER("Big spender", "Backs the manager with money and expects results.", 1.35)
}

enum class DofPhilosophy(val label: String) {
    YOUNG_TALENT("Young talent"), EXPERIENCE("Experienced players"), BARGAINS("Bargains and free agents"), STARS("Star signings")
}

enum class ObjectiveKind(val label: String) {
    LEAGUE_FINISH("League finish"), PROFIT("Profit"), ACADEMY_MINUTES("Academy minutes"), SUSTAINABILITY("Sustainability")
}

/** Traits run 1..20. */
class Owner(var name: String, var ambition: Int, var patience: Int, var wealth: Int, var policy: TransferPolicy, var sinceYear: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

class Chairman(var name: String, var ambition: Int, var meddling: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

/** The director of football has his own transfer philosophy; [alignment] (0..100) is how well it matches the manager's behaviour. */
class Director(var name: String, var philosophy: DofPhilosophy, var ability: Int, var stubbornness: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var alignment = 60f
    var lastActAbs = -100
}

class BoardObjective(val id: Int, val kind: ObjectiveKind, val target: Int, val setYear: Int, val deadlineYear: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    /** 0 active, 1 met, 2 failed. */
    var status = 0
    /** Latest measured value (league position, profit sign, academy minute %, sustainability score). */
    var latest = 0

    fun describe(): String = when (kind) {
        ObjectiveKind.LEAGUE_FINISH -> "Finish in the top $target by $deadlineYear"
        ObjectiveKind.PROFIT -> "Make a profit over the season ($deadlineYear)"
        ObjectiveKind.ACADEMY_MINUTES -> "Give academy players at least $target% of league minutes by $deadlineYear"
        ObjectiveKind.SUSTAINABILITY -> "Reach a sustainability score of $target by $deadlineYear"
    }
}

class ManagerContract(var endYear: Int, var weeklyWage: Long, val compensationWeeks: Int, var signedAbs: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var compensationPaid = false
}

class DofSuggestion(val id: Int, val playerId: Int, val year: Int, val week: Int, val reason: String) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var handled = false
}

class ClubBoard(val clubId: Int, var owner: Owner, var chairman: Chairman, var director: Director) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    val objectives = ArrayList<BoardObjective>()
    var nextObjectiveId = 1
    var freezeWeeks = 0
}

class BoardState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    val clubs = HashMap<Int, ClubBoard>()
    var contract: ManagerContract? = null
    val suggestions = ArrayList<DofSuggestion>()
    var nextSuggestionId = 1
}

object BoardRules {
    const val BASE_SACK_THRESHOLD = 8f
    const val PROTECTED_WEEKS = 12
    const val SUGGESTION_LIFE_WEEKS = 4
    const val MAX_PENDING_SUGGESTIONS = 3
    const val UNILATERAL_ALIGNMENT = 35f
    const val SQUAD_LIMIT_FOR_DOF = 36

    private fun rng(w: World, salt: Int): Random = w.rng(9500 + salt)
    private fun clubRng(w: World, clubId: Int, salt: Int): Random = Random(w.seed * 37L + w.year * 1009L + clubId * 7919L + salt)

    // ---------------------------------------------------------------- state

    fun state(w: World): BoardState {
        val eco = w.eco()
        var s = eco.board
        if (s == null) { s = BoardState(); eco.board = s; generateAll(w, s) }
        return s
    }

    fun boardOf(w: World, clubId: Int): ClubBoard = state(w).clubs.getValue(clubId)

    private fun newOwner(w: World, rnd: Random, club: Club): Owner {
        val nation = Names.pickNation(rnd)
        val wealth = clampI((2 + club.rep / 100f * 14f + rnd.gauss().toFloat() * 3f).roundToInt(), 1, 20)
        val ambition = clampI((5 + club.rep / 100f * 8f + rnd.gauss().toFloat() * 3.5f).roundToInt(), 1, 20)
        val patience = clampI((10 + rnd.gauss().toFloat() * 4f).roundToInt(), 2, 19)
        val roll = rnd.nextFloat() - (if (wealth >= 14) 0.12f else 0f)
        val policy = when {
            roll < 0.20f -> TransferPolicy.BIG_SPENDER
            roll < 0.45f -> TransferPolicy.SELL_TO_PROFIT
            roll < 0.65f -> TransferPolicy.YOUTH_FIRST
            else -> TransferPolicy.BALANCED
        }
        return Owner(Names.fullName(nation, rnd), ambition, patience, wealth, policy, w.year)
    }

    private fun newDirector(rnd: Random): Director {
        val d = Director(Names.fullName(Names.pickNation(rnd), rnd), DofPhilosophy.values()[rnd.nextInt(DofPhilosophy.values().size)],
            clampI((11 + rnd.gauss().toFloat() * 3f).roundToInt(), 3, 20), 1 + rnd.nextInt(20))
        return d
    }

    private fun generateAll(w: World, s: BoardState) {
        val rnd = rng(w, 1)
        for (c in w.clubs) {
            val owner = newOwner(w, rnd, c)
            val chairman = Chairman(Names.fullName(Names.pickNation(rnd), rnd), clampI(owner.ambition + rnd.nextInt(-3, 4), 1, 20), 1 + rnd.nextInt(20))
            s.clubs[c.id] = ClubBoard(c.id, owner, chairman, newDirector(rnd))
        }
        val me = w.human()
        assignObjectives(w, s.clubs.getValue(me.id), me)
        val wage = max(100L, (me.revenue / 52.0 * 0.004 * (0.5 + w.managerReputation / 100.0)).toLong())
        s.contract = ManagerContract(w.year + 2, wage, 26, Business.absWeek(w) - 100)
    }

    // ---------------------------------------------------------------- objectives

    fun horizon(owner: Owner): Int = 1 + if (owner.patience >= 13) 2 else if (owner.patience >= 8) 1 else 0

    fun leaguePosition(w: World, c: Club): Int {
        val table = w.leagueOf(c.id).sortedTable()
        val i = table.indexOfFirst { it.clubId == c.id }
        return if (i < 0) table.size else i + 1
    }

    fun assignObjectives(w: World, board: ClubBoard, club: Club) {
        board.objectives.removeAll { it.status != 0 }
        val n = w.leagueOf(club.id).clubIds.size
        val expected = Game.leagueRank(w, club) + 1
        val slack = (if (board.owner.ambition >= 14) 2 else if (board.owner.ambition >= 9) 1 else 0) + (if (board.owner.policy == TransferPolicy.BIG_SPENDER) 2 else 0)
        val target = clampI(expected - slack, 1, max(1, n - 3))
        val deadline = w.year + horizon(board.owner) - 1
        if (board.objectives.none { it.kind == ObjectiveKind.LEAGUE_FINISH }) {
            board.objectives.add(BoardObjective(board.nextObjectiveId++, ObjectiveKind.LEAGUE_FINISH, target, w.year, deadline))
        }
        val second = when (board.owner.policy) {
            TransferPolicy.SELL_TO_PROFIT -> ObjectiveKind.PROFIT
            TransferPolicy.YOUTH_FIRST -> ObjectiveKind.ACADEMY_MINUTES
            TransferPolicy.BALANCED -> ObjectiveKind.SUSTAINABILITY
            TransferPolicy.BIG_SPENDER -> null
        }
        if (second != null && board.objectives.none { it.kind == second }) {
            val t = when (second) { ObjectiveKind.ACADEMY_MINUTES -> 12; ObjectiveKind.SUSTAINABILITY -> 55; else -> 0 }
            board.objectives.add(BoardObjective(board.nextObjectiveId++, second, t, w.year, w.year + (if (second == ObjectiveKind.PROFIT) 0 else 1)))
        }
    }

    private fun measure(w: World, club: Club, o: BoardObjective): Int = when (o.kind) {
        ObjectiveKind.LEAGUE_FINISH -> leaguePosition(w, club)
        ObjectiveKind.PROFIT -> if (club.balance - club.seasonStartBalance >= 0L) 1 else 0
        ObjectiveKind.ACADEMY_MINUTES -> Identity.academyMinutePercent(w, club.id)
        ObjectiveKind.SUSTAINABILITY -> Identity.sustainability(w, club).total
    }

    private fun isMet(o: BoardObjective, value: Int): Boolean = when (o.kind) {
        ObjectiveKind.LEAGUE_FINISH -> value <= o.target
        ObjectiveKind.PROFIT -> value >= 1
        else -> value >= o.target
    }

    // ---------------------------------------------------------------- hooks used by Game / Transfers

    /** Confidence at or below which the board sacks the manager (default 8). Patient owners and a fresh contract lower it. */
    fun sackThreshold(w: World): Float {
        if (!Ecosystem.enabled) return BASE_SACK_THRESHOLD
        val s = state(w)
        val board = s.clubs.getValue(w.humanClubId)
        var t = BASE_SACK_THRESHOLD + (10 - board.owner.patience) * 0.6f
        val contract = s.contract
        if (contract != null && Business.absWeek(w) - contract.signedAbs < PROTECTED_WEEKS) t = min(t, 3f)
        return t
    }

    fun signingRestriction(w: World, fee: Long): String? {
        if (!Ecosystem.enabled || fee <= 0L) return null
        val board = state(w).clubs[w.humanClubId] ?: return null
        return if (board.freezeWeeks > 0) "The owner has frozen transfer spending for ${board.freezeWeeks} more week(s). Free signings are still allowed." else null
    }

    /** After the yearly budget reset: owner policy and wealth scale every club's transfer budget. */
    fun adjustBudgets(w: World) {
        if (!Ecosystem.enabled) return
        val s = state(w)
        for (c in w.clubs) {
            val o = s.clubs.getValue(c.id).owner
            c.transferBudget = (c.transferBudget * o.policy.budgetFactor * (0.85 + 0.3 * o.wealth / 20.0)).toLong()
        }
    }

    // ---------------------------------------------------------------- weekly

    fun weekly(w: World) {
        val s = state(w)
        val me = w.human()
        val board = s.clubs.getValue(me.id)
        if (board.freezeWeeks > 0) board.freezeWeeks--
        val dir = board.director
        val abs = Business.absWeek(w)
        // Alignment follows how closely the manager's selections and spending match the director's philosophy.
        dir.alignment = clampF(dir.alignment + 1.1f * fit(w, me, dir.philosophy) - 0.12f, 0f, 100f)
        for (sg in s.suggestions) {
            if (!sg.handled && (w.year * 40 + w.week) - (sg.year * 40 + sg.week) >= SUGGESTION_LIFE_WEEKS) {
                sg.handled = true
                dir.alignment = clampF(dir.alignment - 1.5f, 0f, 100f)
            }
        }
        s.suggestions.removeAll { it.handled && (w.year * 40 + w.week) - (it.year * 40 + it.week) > 12 }
        val rnd = rng(w, 100 + w.week)
        if (w.isWindowOpen() && w.week % 4 == 0 && s.suggestions.count { !it.handled } < MAX_PENDING_SUGGESTIONS && rnd.nextFloat() < 0.6f) suggest(w, s, board, me, rnd)
        if (dir.alignment < UNILATERAL_ALIGNMENT && w.isWindowOpen() && w.week % 3 == 0 && abs - dir.lastActAbs >= 3 && rnd.nextFloat() < 0.35f) unilateralSigning(w, board, me, rnd)
    }

    /** -1..+1: how well the club's recent behaviour fits a philosophy. */
    fun fit(w: World, club: Club, p: DofPhilosophy): Float {
        val squad = w.squad(club)
        val played = squad.filter { it.lastMinutes > 0 }
        if (played.isEmpty()) return 0f
        val total = played.sumOf { it.lastMinutes }.toFloat()
        fun share(pred: (Player) -> Boolean): Float = played.filter(pred).sumOf { it.lastMinutes } / total
        return when (p) {
            DofPhilosophy.YOUNG_TALENT -> ((share { it.age <= 23 } - 0.20f) * 3f).coerceIn(-1f, 1f)
            DofPhilosophy.EXPERIENCE -> ((share { it.age >= 28 } - 0.45f) * 2.5f).coerceIn(-1f, 1f)
            DofPhilosophy.BARGAINS -> ((0.70f - (w.wageBill(club) * 52f / max(1L, club.revenue))) * 3f).coerceIn(-1f, 1f)
            DofPhilosophy.STARS -> {
                val top = squad.sortedByDescending { it.ovr }.take(11)
                ((top.map { it.ovr }.average().toFloat() - WorldGen.MEAN_OVR[club.tier]) / 6f).coerceIn(-1f, 1f)
            }
        }
    }

    private fun candidateScore(p: Player, ph: DofPhilosophy): Float = when (ph) {
        DofPhilosophy.YOUNG_TALENT -> if (p.age <= 22) p.pa.toFloat() else -1f
        DofPhilosophy.EXPERIENCE -> if (p.age >= 28) p.ovr.toFloat() else -1f
        DofPhilosophy.BARGAINS -> p.ovr.toFloat() - p.wageDemand() / 400f
        DofPhilosophy.STARS -> p.ovr.toFloat()
    }

    private fun candidates(w: World, board: ClubBoard, me: Club): List<Player> =
        w.players.values.filter { it.clubId == -1 && Transfers.willJoin(me, it) && !it.listed }
            .map { it to candidateScore(it, board.director.philosophy) }.filter { it.second > 0f }
            .sortedWith(compareByDescending<Pair<Player, Float>> { it.second }.thenBy { it.first.id }).take(10).map { it.first }

    private fun suggest(w: World, s: BoardState, board: ClubBoard, me: Club, rnd: Random) {
        val pending = s.suggestions.filter { !it.handled }.map { it.playerId }.toSet()
        val pick = candidates(w, board, me).filter { it.id !in pending && !me.players.contains(it.id) }.let { if (it.isEmpty()) null else it[rnd.nextInt(min(it.size, 4))] } ?: return
        val reason = "${board.director.name} recommends ${pick.name} (${pick.pos.label}, ${pick.age}, ${pick.ovr}) - fits his '${board.director.philosophy.label}' approach."
        s.suggestions.add(DofSuggestion(s.nextSuggestionId++, pick.id, w.year, w.week, reason))
        w.addNews("Director of football", reason)
    }

    private fun unilateralSigning(w: World, board: ClubBoard, me: Club, rnd: Random) {
        if (me.players.size > SQUAD_LIMIT_FOR_DOF) return
        val pick = candidates(w, board, me).firstOrNull { me.players.size < 40 } ?: return
        val wage = Transfers.wantedWage(me, pick)
        if (w.wageBill(me) + wage > me.wageBudget) return
        Transfers.move(w, pick, me, 0L, wage, 2, rnd)
        board.director.lastActAbs = Business.absWeek(w)
        board.director.alignment = clampF(board.director.alignment - 1f, 0f, 100f)
        w.addNews("Director of football", "${board.director.name} signed ${pick.name} (${pick.pos.label}, ${pick.ovr}) on a free transfer without consulting you.")
    }

    fun acceptSuggestion(w: World, id: Int): Pair<Boolean, String> {
        val s = state(w)
        val sg = s.suggestions.firstOrNull { it.id == id && !it.handled } ?: return false to "That recommendation is no longer open."
        val p = w.players[sg.playerId] ?: return false to "That player is no longer available."
        val me = w.human()
        if (p.clubId != -1) { sg.handled = true; return false to "${p.name} has already signed elsewhere." }
        val result = Transfers.completeSigning(w, p, 0L, Transfers.wantedWage(me, p), 2)
        if (result.first) {
            sg.handled = true
            val d = s.clubs.getValue(me.id).director
            d.alignment = clampF(d.alignment + 4f, 0f, 100f)
        }
        return result
    }

    fun declineSuggestion(w: World, id: Int): Pair<Boolean, String> {
        val s = state(w)
        val sg = s.suggestions.firstOrNull { it.id == id && !it.handled } ?: return false to "That recommendation is no longer open."
        sg.handled = true
        val d = s.clubs.getValue(w.humanClubId).director
        d.alignment = clampF(d.alignment - 3f, 0f, 100f)
        return true to "You turned down ${d.name}'s recommendation. His trust in your judgement dips slightly."
    }

    /** Called after every week, once a sacking may have happened: pays the contract's compensation exactly once. */
    fun afterWeek(w: World) {
        val s = state(w)
        val c = s.contract ?: return
        if (w.sacked && !c.compensationPaid) {
            c.compensationPaid = true
            val owed = c.weeklyWage * c.compensationWeeks
            w.human().balance -= owed
            w.addNews("Contract settlement", "The club paid ${money(owed)} compensation under your contract (${c.compensationWeeks} weeks of pay).")
        }
    }

    // ---------------------------------------------------------------- seasonal

    fun seasonReview(w: World) {
        val s = state(w)
        val me = w.human()
        val board = s.clubs.getValue(me.id)
        val mine = board.objectives.filter { it.status == 0 }
        for (o in mine) {
            val v = measure(w, me, o)
            o.latest = v
            if (isMet(o, v)) {
                o.status = 1
                me.boardConfidence = clampF(me.boardConfidence + 5f, 0f, 100f)
                w.addNews("Objective achieved", o.describe() + ".")
            } else if (w.year >= o.deadlineYear) {
                o.status = 2
                me.boardConfidence = clampF(me.boardConfidence - 8f, 0f, 100f)
                w.addNews("Objective failed", o.describe() + ". The board is disappointed.")
            }
        }
        if (board.objectives.count { it.status == 0 } < 2) assignObjectives(w, board, me)
        ownerEvents(w, s)
        renewContract(w, s, me)
        // Fee-paying budgets rest on the review: a bankrupt human club earns a freeze.
        if (me.balance < 0L && board.owner.patience < 12) board.freezeWeeks = 10 + rng(w, 3).nextInt(6)
    }

    private fun ownerEvents(w: World, s: BoardState) {
        for (c in w.clubs) {
            val board = s.clubs.getValue(c.id)
            val rnd = clubRng(w, c.id, 31)
            val unhappy = c.human && c.boardConfidence < 35f
            if (rnd.nextFloat() < 0.03f + (if (unhappy) 0.05f else 0f)) {
                val old = board.owner
                board.owner = newOwner(w, rnd, c)
                if (rnd.nextFloat() < 0.5f) board.director = newDirector(rnd)
                if (c.human) {
                    c.boardConfidence = clampF((c.boardConfidence + 55f) / 2f, 0f, 100f)
                    board.freezeWeeks = 0
                    board.objectives.clear()
                    assignObjectives(w, board, c)
                    w.addNews("Takeover", "${board.owner.name} has bought the club from ${old.name}. New owner: ${board.owner.policy.label.lowercase()} policy, " +
                        "ambition ${board.owner.ambition}, patience ${board.owner.patience}. Objectives were reset.")
                }
            } else if (rnd.nextFloat() < 0.04f * board.owner.wealth / 10f) {
                val amount = (c.revenue * (0.04 + 0.12 * rnd.nextDouble()) * board.owner.wealth / 12.0).toLong()
                c.balance += amount
                c.transferBudget += (amount * 0.6).toLong()
                if (c.human) w.addNews("Owner investment", "${board.owner.name} invested ${money(amount)} in the club; ${money((amount * 0.6).toLong())} is available for transfers.")
            }
        }
    }

    internal fun renewContract(w: World, s: BoardState, me: Club) {
        val c = s.contract ?: return
        if (c.endYear > w.year) return
        when {
            me.boardConfidence >= 50f -> {
                c.endYear = w.year + 3
                c.weeklyWage = (c.weeklyWage * 1.1).toLong()
                c.signedAbs = Business.absWeek(w)
                w.addNews("Contract renewed", "The board offered you a new three-year contract worth ${money(c.weeklyWage)} per week.")
            }
            me.boardConfidence >= 35f -> {
                c.endYear = w.year + 1
                c.signedAbs = Business.absWeek(w)
                w.addNews("Contract extended", "The board only agreed a one-year extension. Results must improve.")
            }
            else -> {
                w.sacked = true
                w.sackReason = "The board did not renew your contract."
            }
        }
    }
}
