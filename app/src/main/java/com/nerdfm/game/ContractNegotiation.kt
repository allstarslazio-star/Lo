package com.nerdfm.game

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class ContractTerms(
    val wage: Int,
    val years: Int,
    val releaseClause: Long = 0L,
    val signingBonus: Long = 0L,
    val appearanceBonus: Int = 0
)

/** outcome: 0 accepted, 1 counter-offer, 2 talks collapsed, 3 invalid or blocked (no round used). */
class NegotiationReply(val outcome: Int, val message: String, val counter: ContractTerms? = null)

/** Multi-round contract talks, agent fees, release clauses and sell-on clauses. */
object ContractDeals {
    const val MAX_ROUNDS = 3
    const val COOLDOWN_WEEKS = 8

    fun agentFee(fee: Long, wage: Int, years: Int): Long =
        (fee * 0.03 + wage * 52.0 * years * 0.02).toLong()

    fun renewalAgentFee(wage: Int, years: Int): Long = (wage * 52.0 * years * 0.02).toLong()

    fun demandedWage(p: Player): Int {
        var d = p.wageDemand().toDouble() * People.renewalDemandMultiplier(p)
        if (p.morale < 45f) d *= 1.3
        if (p.age >= 33) d *= 0.8
        return max(300, d.toInt())
    }

    /** >= 1.0 means the player and agent will sign. */
    fun satisfaction(p: Player, t: ContractTerms): Double {
        val demand = demandedWage(p).toDouble()
        var score = t.wage / demand
        score += (t.signingBonus / (demand * 52.0)) * 0.8
        score += min(t.appearanceBonus, 5000) * 25.0 / (demand * 52.0) * 0.6
        score += if (p.age <= 26) 0.02 * (t.years - 3) else if (p.age >= 31) 0.03 * (3 - t.years) else 0.0
        if (t.releaseClause > 0L) {
            if (p.ambition >= 13) score += 0.05
            if (p.loyalty >= 14) score -= 0.04
        }
        return score
    }

    /** The wage the agent asks for in a given round, keeping the other terms as offered. */
    fun counterWage(p: Player, t: ContractTerms, round: Int): Int {
        val demand = demandedWage(p).toDouble()
        val extras = satisfaction(p, t) - t.wage / demand
        val premium = 0.04 * (MAX_ROUNDS - round).coerceAtLeast(0)
        val needed = demand * (1.0 + premium - extras)
        return max(300, ceil(needed).toInt())
    }

    fun weeksUntilTalks(w: World, p: Player): Int {
        val st = w.biz().negotiations[p.id] ?: return 0
        return max(0, st.blockedUntil - Business.absWeek(w))
    }

    fun propose(w: World, p: Player, t: ContractTerms): NegotiationReply {
        val me = w.human()
        if (p.clubId != me.id) return NegotiationReply(3, "${p.name} is not at your club.")
        if (t.wage <= 0 || t.years !in 1..5) return NegotiationReply(3, "Enter valid terms: a wage above zero and 1 to 5 years.")
        if (t.releaseClause < 0L || t.signingBonus < 0L || t.appearanceBonus < 0) return NegotiationReply(3, "Clauses and bonuses cannot be negative.")
        val b = w.biz()
        val abs = Business.absWeek(w)
        val st = b.negotiations.getOrPut(p.id) { NegotiationState(p.id) }
        if (st.blockedUntil > abs) {
            return NegotiationReply(3, "${p.name}'s agent will not talk for another ${st.blockedUntil - abs} week(s).")
        }
        if (st.blockedUntil in 0..abs) {
            st.blockedUntil = -1
            st.round = 0
        }
        val upfront = t.signingBonus + renewalAgentFee(t.wage, t.years)
        val newBill = w.wageBill(me) - p.wage + t.wage
        val score = satisfaction(p, t)
        if (score >= 1.0) {
            if (newBill > me.wageBudget) return NegotiationReply(3, "The board will not sanction this: it breaks the wage budget (${money(me.wageBudget)}/wk).")
            if (me.balance < upfront) return NegotiationReply(3, "You cannot afford ${money(upfront)} in signing bonus and agent fees.")
            applyTerms(w, me, p, t, upfront, st)
            return NegotiationReply(0, "${p.name} signed until ${p.contractEnd}. Agent fee and bonus: ${money(upfront)}.")
        }
        st.round++
        if (st.round >= MAX_ROUNDS) {
            st.round = 0
            st.blockedUntil = abs + COOLDOWN_WEEKS
            p.morale = clampF(p.morale - 3f, 15f, 100f)
            return NegotiationReply(2, "Talks collapsed. ${p.name}'s agent walked away and will not talk for $COOLDOWN_WEEKS weeks.")
        }
        val wage = counterWage(p, t, st.round)
        st.lastCounterWage = wage
        st.lastCounterYears = t.years
        val counter = ContractTerms(wage, t.years, t.releaseClause, t.signingBonus, t.appearanceBonus)
        return NegotiationReply(
            1,
            "Round ${st.round} of $MAX_ROUNDS: the agent wants ${money(wage.toLong())} per week on those terms.",
            counter
        )
    }

    private fun applyTerms(w: World, me: Club, p: Player, t: ContractTerms, upfront: Long, st: NegotiationState) {
        val b = w.biz()
        me.balance -= upfront
        b.seasonAgentFees += renewalAgentFee(t.wage, t.years)
        b.seasonBonuses += t.signingBonus
        p.wage = t.wage
        p.contractEnd = w.year + t.years
        p.morale = min(100f, p.morale + 8f)
        val clauses = b.clauses.getOrPut(p.id) { ContractClauses() }
        clauses.releaseClause = t.releaseClause
        clauses.appearanceBonus = t.appearanceBonus
        if (clauses.isEmpty()) b.clauses.remove(p.id)
        st.round = 0
        st.blockedUntil = -1
        w.addNews("Contract agreed", "${p.name} signed a new deal until ${p.contractEnd} on ${money(t.wage.toLong())} per week.")
    }

    // ------------------------------------------------------------------ release clauses

    /** The release clause that applies to a player, including deterministic clauses for AI-club stars. */
    fun releaseClauseFor(w: World, p: Player): Long {
        val own = w.biz().clauses[p.id]?.releaseClause ?: 0L
        if (own > 0L) return own
        if (p.clubId < 0) return 0L
        val club = w.clubs.getOrNull(p.clubId) ?: return 0L
        if (club.human) return 0L
        if (p.ovr < 62 || p.age !in 19..31 || p.contractEnd - w.year < 2) return 0L
        if ((p.id * 31 + 7).mod(5) != 0) return 0L
        return (p.value() * 2.4).toLong()
    }

    /** Rival clubs may trigger release clauses held by your players while a window is open. */
    fun weeklyClauseCheck(w: World, rnd: Random) {
        if (w.clubs.isEmpty() || !w.isWindowOpen()) return
        val me = w.clubs.getOrNull(w.humanClubId) ?: return
        val b = w.biz()
        val holders = w.squad(me).filter { (b.clauses[it.id]?.releaseClause ?: 0L) > 0L }
        for (p in holders) {
            if (me.players.size <= 18) return
            val clause = b.clauses[p.id]?.releaseClause ?: continue
            val chance = 0.03 + p.ambition * 0.003
            if (rnd.nextDouble() > chance) continue
            val buyers = w.clubs.filter { !it.human && it.balance >= clause && it.players.size < 40 && it.rep >= me.rep - 10 }
            if (buyers.isEmpty()) continue
            val buyer = buyers[rnd.nextInt(buyers.size)]
            val wage = max(300, p.wageDemand())
            val years = 1 + rnd.nextInt(4)
            val name = p.name
            Transfers.move(w, p, buyer, clause, wage, years, rnd)
            w.addNews("Release clause triggered", "${buyer.name} paid ${money(clause)} to activate the release clause of $name.")
        }
    }

    // ------------------------------------------------------------------ sell-on clauses

    fun sellOnPercent(w: World): Int = w.biz().sellOnPolicy.coerceIn(0, 2) * 10

    fun setSellOnPolicy(w: World, policy: Int): Boolean {
        if (policy !in 0..2) return false
        w.biz().sellOnPolicy = policy
        return true
    }

    /** Buyers pay less when you keep a sell-on percentage. */
    fun saleFeeAfterClause(w: World, fee: Long): Long {
        val pct = sellOnPercent(w)
        return if (pct <= 0) fee else fee * (100L - pct * 6L / 10L) / 100L
    }

    fun registerSellOn(w: World, p: Player, ownerClubId: Int) {
        val pct = sellOnPercent(w)
        if (pct <= 0) return
        val clauses = w.biz().clauses.getOrPut(p.id) { ContractClauses() }
        clauses.sellOnPct = pct
        clauses.sellOnClubId = ownerClubId
    }

    /** Call whenever a player changes club for a fee: pays any sell-on share and resets personal clauses. */
    fun onTransfer(w: World, p: Player, fromClubId: Int, fee: Long) {
        val b = w.biz()
        val clauses = b.clauses[p.id] ?: return
        if (clauses.sellOnPct > 0 && clauses.sellOnClubId >= 0 && fromClubId >= 0 && fromClubId != clauses.sellOnClubId && fee > 0L) {
            val owner = w.clubs.getOrNull(clauses.sellOnClubId)
            val seller = w.clubs.getOrNull(fromClubId)
            if (owner != null && seller != null) {
                val share = fee * clauses.sellOnPct / 100L
                seller.balance -= share
                owner.balance += share
                if (owner.human) w.addNews("Sell-on clause paid", "${money(share)} received after ${p.name} moved on.")
            }
            clauses.sellOnPct = 0
            clauses.sellOnClubId = -1
        }
        clauses.releaseClause = 0L
        clauses.appearanceBonus = 0
        if (clauses.isEmpty()) b.clauses.remove(p.id)
    }
}
