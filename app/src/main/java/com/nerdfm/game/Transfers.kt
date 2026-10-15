package com.nerdfm.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class BidResult(val status: Int, val msg: String, val counter: Long = 0L)
// status: 0 fee accepted, 1 rejected, 2 counter, 3 not allowed

object Transfers {
    private val POS_NEED = listOf(Pos.GK to 2, Pos.LB to 2, Pos.RB to 2, Pos.CB to 4, Pos.DM to 2,
        Pos.CM to 4, Pos.LW to 2, Pos.RW to 2, Pos.AM to 2, Pos.ST to 3)

    fun requiredRep(p: Player): Int = clampI(p.ovr - 25, 5, 80)

    fun wantedWage(me: Club, p: Player): Int {
        val gap = max(0, requiredRep(p) - me.rep)
        return (p.wageDemand() * (1.0 + gap * 0.02)).toInt()
    }

    fun willJoin(me: Club, p: Player): Boolean = requiredRep(p) - me.rep <= 25

    fun askingPrice(w: World, p: Player, buyer: Club): Long {
        val seller = w.clubs[p.clubId]
        val squad = w.squad(seller).sortedByDescending { it.ovr }
        val key = squad.indexOf(p) in 0..10
        var f = 1.05 + (if (key) 0.2 else 0.0) + (if (p.listed) -0.15 else 0.0) +
            clampF((seller.rep - buyer.rep) / 200f, -0.1f, 0.1f)
        if (p.contractEnd <= w.year + 1) f *= 0.75
        if (buyer.human) f *= 1.08   // hard mode premium
        return (p.value() * f).toLong()
    }

    fun bid(w: World, p: Player, fee: Long): BidResult {
        val me = w.human()
        if (!w.isWindowOpen()) return BidResult(3, "The transfer window is closed.")
        if (p.clubId == me.id) return BidResult(3, "He already plays for you.")
        if (p.clubId < 0) return BidResult(3, "He is a free agent - sign him directly.")
        if (fee > me.transferBudget) return BidResult(3, "Your transfer budget (${money(me.transferBudget)}) does not cover this bid.")
        if (fee > me.balance) return BidResult(3, "The club cannot afford this fee.")
        val seller = w.clubs[p.clubId]
        if (w.squad(seller).size <= 19) return BidResult(1, "${seller.name} refuse to sell - their squad is too thin.")
        val ask = askingPrice(w, p, me)
        return when {
            fee >= ask -> BidResult(0, "${seller.name} have accepted your bid of ${money(fee)}.")
            fee >= (ask * 0.85).toLong() -> BidResult(2, "${seller.name} have rejected your bid but would sell for ${money(ask)}.", ask)
            else -> BidResult(1, "${seller.name} have flatly rejected your bid.")
        }
    }

    fun move(w: World, p: Player, to: Club, fee: Long, wage: Int, years: Int, rnd: Random) {
        if (p.clubId >= 0) {
            val from = w.clubs[p.clubId]
            from.players.remove(p.id)
            from.balance += fee
            from.transferBudget += (fee * 0.5).toLong()
        }
        to.balance -= fee
        to.transferBudget = max(0L, to.transferBudget - fee)
        p.clubId = to.id
        p.wage = wage
        p.contractEnd = w.year + years + (if (w.week > 5) 0 else 0)
        p.listed = false
        p.morale = 75f
        p.seasonsAtClub = 0
        to.players.add(p.id)
        for (i in 0 until 11) if (to.lineup[i] == p.id) to.lineup[i] = -1
    }

    /** Human finalises a purchase with personal terms. Returns message and whether it worked. */
    fun completeSigning(w: World, p: Player, fee: Long, wage: Int, years: Int): Pair<Boolean, String> {
        val me = w.human()
        if (!willJoin(me, p)) return false to "${p.name} is not interested in joining a club of your stature."
        val wanted = wantedWage(me, p)
        if (wage < wanted) return false to "${p.name} wants at least ${money(wanted.toLong())} per week."
        val newBill = w.wageBill(me) + wage
        if (newBill > me.wageBudget) return false to "The board will not sanction this - it would break the wage budget (${money(me.wageBudget)}/wk)."
        if (me.players.size >= 40) return false to "Your squad is full (40 players)."
        val rnd = w.rng(p.id)
        val from = if (p.clubId >= 0) w.clubs[p.clubId].name else "free agency"
        move(w, p, me, fee, wage, years, rnd)
        w.addNews("Signing complete", "${p.name} (${p.pos.label}, ${p.ovr}) joins from $from for ${if (fee > 0) money(fee) else "free"}.")
        return true to "${p.name} has signed for ${me.name}!"
    }

    fun renew(w: World, p: Player, wage: Int, years: Int): Pair<Boolean, String> {
        val me = w.human()
        var wanted = (p.wageDemand() * 1.0).toInt()
        if (p.morale < 45f) wanted = (wanted * 1.3).toInt()
        if (p.age >= 33) wanted = (wanted * 0.8).toInt()
        if (wage < wanted) return false to "${p.name} wants at least ${money(wanted.toLong())} per week."
        val newBill = w.wageBill(me) - p.wage + wage
        if (newBill > me.wageBudget) return false to "The board will not sanction this - it breaks the wage budget."
        p.wage = wage
        p.contractEnd = w.year + years
        p.morale = min(100f, p.morale + 8f)
        return true to "${p.name} has signed a new deal until ${p.contractEnd}."
    }

    fun release(w: World, p: Player): String {
        val me = w.human()
        val yearsLeft = max(0, p.contractEnd - (w.year + 1))
        val cost = p.wage.toLong() * 52L * (yearsLeft + 1) / 2L
        me.balance -= cost
        me.players.remove(p.id)
        p.clubId = -1
        p.wage = 0
        p.contractEnd = 0
        p.listed = false
        for (i in 0 until 11) if (me.lineup[i] == p.id) me.lineup[i] = -1
        for (i in 0 until 7) if (me.bench[i] == p.id) me.bench[i] = -1
        return "${p.name} was released. Settlement cost ${money(cost)}."
    }

    fun counterOffer(w: World, o: Offer, counterFee: Long): String {
        val p = w.players[o.playerId] ?: return "Player no longer available."
        if (p.clubId != w.humanClubId || !w.isWindowOpen()) return "This offer can no longer be negotiated."
        val buyer = w.clubs.getOrNull(o.fromClub) ?: return "The buying club is no longer available."
        if (counterFee <= o.fee) return "Your counteroffer must be higher than the original bid."
        if (counterFee > (p.value() * 2.5).toLong()) return "${buyer.name} are unlikely to consider such a high fee."
        if (counterFee > buyer.balance || counterFee > buyer.transferBudget) return "${buyer.name} cannot afford that fee."
        val rnd = w.rng(p.id + counterFee.toInt())
        val acceptChance = (0.72 - (counterFee.toDouble() / p.value().coerceAtLeast(1L) - 1.0).coerceAtLeast(0.0) * 0.28 + (buyer.rep - w.human().rep) * 0.003).coerceIn(0.08, 0.92)
        if (rnd.nextDouble() < acceptChance) {
            o.fee = counterFee
            w.addNews("Counteroffer accepted", "${buyer.name} accepted your revised ${money(counterFee)} offer for ${p.name}. You can now accept or reject the deal.")
            return "${buyer.name} accepted your counteroffer of ${money(counterFee)}."
        }
        w.offers.remove(o)
        w.addNews("Counteroffer rejected", "${buyer.name} rejected your ${money(counterFee)} counteroffer for ${p.name}.")
        return "${buyer.name} rejected your counteroffer."
    }

    fun acceptOffer(w: World, o: Offer): String {
        val p = w.players[o.playerId] ?: return "Player no longer available."
        val buyer = w.clubs[o.fromClub]
        val me = w.human()
        if (p.clubId != me.id) { w.offers.remove(o); return "Player no longer at the club." }
        if (me.players.size <= 17) return "You cannot sell - your squad would drop below the minimum."
        val rnd = w.rng(p.id)
        val wage = max(300, (p.wageDemand() * (0.9 + rnd.nextDouble() * 0.3)).toInt())
        val from = me
        from.players.remove(p.id)
        from.balance += o.fee
        from.transferBudget += (o.fee * 0.6).toLong()
        buyer.balance -= o.fee
        buyer.transferBudget = max(0L, buyer.transferBudget - o.fee)
        p.clubId = buyer.id
        p.wage = wage
        p.contractEnd = w.year + 1 + rnd.nextInt(4)
        p.listed = false
        buyer.players.add(p.id)
        for (i in 0 until 11) if (me.lineup[i] == p.id) me.lineup[i] = -1
        for (i in 0 until 7) if (me.bench[i] == p.id) me.bench[i] = -1
        w.offers.removeAll { it.playerId == p.id }
        w.addNews("Player sold", "${p.name} joined ${buyer.name} for ${money(o.fee)}.")
        return "${p.name} sold to ${buyer.name} for ${money(o.fee)}."
    }

    // ---------------------------------------------------------------- AI behaviour

    fun generateOffers(w: World, rnd: Random) {
        if (!w.isWindowOpen()) return
        val me = w.human()
        val squad = w.squad(me).sortedByDescending { it.ovr }
        // Occasionally surface realistic scouting interest before a formal bid arrives.
        if (squad.isNotEmpty() && rnd.nextDouble() < 0.22) {
            val watched = squad.filter { !w.offers.any { offer -> offer.playerId == it.id } }.randomOrNull(rnd)
            if (watched != null) {
                val interestedClubs = w.clubs.filter { c -> !c.human && c.id != watched.clubId && c.rep >= me.rep - 5 && c.transferBudget >= watched.value() * 0.8 }
                if (interestedClubs.isNotEmpty()) {
                    val scoutClub = interestedClubs[rnd.nextInt(interestedClubs.size)]
                    w.addNews("Scouting interest", "${scoutClub.name} have been monitoring ${watched.name}. No formal bid has been made yet.")
                }
            }
        }
        for ((i, p) in squad.withIndex()) {
            val pr = if (p.listed) 0.35 else if (i < 12) 0.04 else 0.015
            if (rnd.nextDouble() > pr) continue
            if (w.offers.any { it.playerId == p.id }) continue
            val cands = w.clubs.filter { c ->
                !c.human && c.tier <= me.tier + 1 && c.rep >= me.rep - 15 && c.transferBudget > p.value() * 0.7
            }
            if (cands.isEmpty()) continue
            val buyer = cands[rnd.nextInt(cands.size)]
            val f = if (p.listed) 0.8 + rnd.nextDouble() * 0.4 else 0.85 + rnd.nextDouble() * 0.5
            val fee = (p.value() * f).toLong()
            if (fee > buyer.balance || fee > buyer.transferBudget) continue
            w.offers.add(Offer(p.id, buyer.id, fee, w.week + 2))
            w.addNews("Transfer offer", "${buyer.name} offer ${money(fee)} for ${p.name}. See Transfers > Offers.")
        }
    }

    private fun countPos(w: World, c: Club, pos: Pos): Int = c.players.count { w.players[it]!!.pos == pos }

    fun aiMarket(w: World, rnd: Random) {
        val clubs = w.clubs.filter { !it.human }
        val tries = 24
        val all = w.players.values
        for (n in 0 until tries) {
            val c = clubs[rnd.nextInt(clubs.size)]
            if (c.players.size >= 36) continue
            // find need
            var needPos = Pos.CM
            var needScore = -999f
            val squad = w.squad(c)
            val q = Lineups.strength(w, c)
            for ((pos, cnt) in POS_NEED) {
                val here = squad.filter { it.pos == pos }.sortedByDescending { it.ovr }
                val shortage = (cnt - here.size) * 10f
                val second = if (here.size >= 2) here[1].ovr else (if (here.isNotEmpty()) here[0].ovr - 5 else 0)
                val weak = q - second
                val sc = shortage + weak + rnd.nextFloat() * 4f
                if (sc > needScore) { needScore = sc; needPos = pos }
            }
            val here = squad.filter { it.pos == needPos }.sortedByDescending { it.ovr }
            val bar = (if (here.size >= 2) here[1].ovr else if (here.isNotEmpty()) here[0].ovr - 4 else 0) + 1
            val bill = w.wageBill(c)
            var best: Player? = null
            var bestS = -1f
            for (p in all) {
                if (p.pos != needPos || p.clubId == c.id || p.age > 33) continue
                if (p.ovr < bar || p.ovr > q + 9) continue
                val human = p.clubId >= 0 && w.clubs[p.clubId].human
                if (human) continue
                val fee = if (p.clubId < 0) 0L else (p.value() * 1.1).toLong()
                if (fee > c.transferBudget || fee > c.balance) continue
                val wage = max(300, p.wageDemand())
                if (bill + wage > c.wageBudget * 1.03) continue
                if (p.clubId >= 0) {
                    val seller = w.clubs[p.clubId]
                    if (seller.players.size <= 22) continue
                    if (countPos(w, seller, p.pos) <= 2) continue
                    if (seller.rep > c.rep + 12) continue
                }
                val s = p.ovr + rnd.nextFloat() * 5f + (if (p.age < 25) 2f else 0f)
                if (s > bestS) { bestS = s; best = p }
            }
            val pick = best ?: continue
            val fee = if (pick.clubId < 0) 0L else (pick.value() * (1.0 + rnd.nextDouble() * 0.15)).toLong()
            val wage = max(300, pick.wageDemand())
            if (pick.ovr >= 72 && pick.clubId >= 0) {
                w.addNews("Transfer news", "${pick.name} (${pick.ovr}) moves from ${w.clubs[pick.clubId].name} to ${c.name} for ${money(fee)}.")
            }
            move(w, pick, c, fee, wage, 1 + rnd.nextInt(4), rnd)
        }
    }

    fun aiSquadMaintenance(w: World, rnd: Random) {
        for (c in w.clubs) {
            if (c.human) continue
            // trim
            while (c.players.size > 38) {
                val worst = w.squad(c).minByOrNull { it.ovr + (if (it.age < 20) 8 else 0) } ?: break
                c.players.remove(worst.id)
                worst.clubId = -1
                worst.wage = 0
                worst.contractEnd = 0
            }
            // fill
            var guard = 0
            while ((c.players.size < 23 || countPos(w, c, Pos.GK) < 2) && guard < 12) {
                guard++
                val needGk = countPos(w, c, Pos.GK) < 2
                val q = Lineups.strength(w, c)
                val target = q - 6
                val cand = w.players.values.filter {
                    it.clubId < 0 && it.age >= 18 && (!needGk || it.pos == Pos.GK)
                }.minByOrNull { Math.abs(it.ovr - target) } ?: break
                signFree(w, cand, c, rnd)
            }
        }
    }

    fun signFree(w: World, p: Player, c: Club, rnd: Random) {
        p.clubId = c.id
        p.wage = max(300, (p.wageDemand() * 0.9).toInt())
        p.contractEnd = w.year + 1 + rnd.nextInt(2)
        p.morale = 70f
        c.players.add(p.id)
    }
}
