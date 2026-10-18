package com.nerdfm.game

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

enum class StaffDept(val label: String) {
    COACHING("Coaching"), YOUTH("Youth"), RECRUITMENT("Recruitment"), MEDICAL("Medical"), ANALYTICS("Analytics"), OPERATIONS("Operations")
}

/** [slots] = how many people a club can employ in the role; [wageWeight] scales the market wage. */
enum class StaffRole(val label: String, val dept: StaffDept, val slots: Int, val wageWeight: Double) {
    ASSISTANT_MANAGER("Assistant manager", StaffDept.COACHING, 1, 1.6),
    COACH_ATTACKING("Attacking coach", StaffDept.COACHING, 1, 1.1),
    COACH_DEFENDING("Defending coach", StaffDept.COACHING, 1, 1.1),
    COACH_GOALKEEPING("Goalkeeping coach", StaffDept.COACHING, 1, 0.9),
    COACH_FITNESS("Fitness coach", StaffDept.COACHING, 1, 1.0),
    COACH_SET_PIECES("Set-piece coach", StaffDept.COACHING, 1, 0.8),
    HEAD_OF_YOUTH("Head of youth", StaffDept.YOUTH, 1, 1.3),
    ACADEMY_COACH("Academy coach", StaffDept.YOUTH, 3, 0.7),
    HEAD_OF_RECRUITMENT("Head of recruitment", StaffDept.RECRUITMENT, 1, 1.4),
    CHIEF_SCOUT("Chief scout", StaffDept.RECRUITMENT, 1, 1.1),
    PHYSIO("Physiotherapist", StaffDept.MEDICAL, 2, 0.9),
    DOCTOR("Club doctor", StaffDept.MEDICAL, 1, 1.3),
    SPORTS_SCIENTIST("Sports scientist", StaffDept.MEDICAL, 1, 1.1),
    DATA_ANALYST("Data analyst", StaffDept.ANALYTICS, 3, 0.9),
    GROUNDKEEPER("Groundkeeper", StaffDept.OPERATIONS, 1, 0.6),
    KIT_MANAGER("Kit manager", StaffDept.OPERATIONS, 1, 0.5),
    /** M4: speeds up how fast foreign players learn the club's working language. Not part of any AI club's normal staffing. */
    LANGUAGE_COACH("Language coach", StaffDept.COACHING, 1, 0.7)
}

/** One person on a club's payroll (or a free agent when clubId == -1). Attributes run 1..20. */
class StaffMember(val id: Int, var name: String, val nation: Int, var age: Int, val role: StaffRole) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    var clubId = -1
    var ability = 10
    var judgement = 10
    var motivation = 10
    var loyalty = 10
    var ambition = 10
    /** Weekly wage. */
    var wage = 0
    /** The contract runs through the END of this season year (so contractEnd <= year at the season review means expiring). */
    var contractEnd = 0
    var morale = 70f
    var reputation = 40
    var weeksAtClub = 0

    /** Ability after morale: unhappy staff work below their level (0.8x at zero morale). */
    fun effective(): Float = ability * (0.8f + 0.2f * (morale / 100f).coerceIn(0f, 1f))
}

/** A bigger club wants one of the human club's staff. Resolves by matching, releasing or expiring. */
class PoachBid(val staffId: Int, val bidderClubId: Int, val newWage: Int, val expiresAbs: Int) : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
}

class StaffState : Serializable {
    private companion object { private const val serialVersionUID: Long = 1L }
    val members = HashMap<Int, StaffMember>()
    /** club id -> staff ids; free agents are not listed here. */
    val byClub = HashMap<Int, ArrayList<Int>>()
    /** club id -> weekly wage total when the club was first staffed; costs above it are extra spend for the human club. */
    val baseline = HashMap<Int, Long>()
    var nextId = 1
    val bids = ArrayList<PoachBid>()
    var seasonStaffSpend = 0L
}

object Staffing {
    /** Slot priority: a club of tier t employs the first staffedSlots(t) entries. */
    val SLOT_ORDER: List<StaffRole> = listOf(
        StaffRole.ASSISTANT_MANAGER, StaffRole.PHYSIO, StaffRole.COACH_FITNESS, StaffRole.GROUNDKEEPER,
        StaffRole.COACH_ATTACKING, StaffRole.COACH_DEFENDING, StaffRole.COACH_GOALKEEPING, StaffRole.HEAD_OF_YOUTH,
        StaffRole.CHIEF_SCOUT, StaffRole.DOCTOR, StaffRole.KIT_MANAGER, StaffRole.ACADEMY_COACH,
        StaffRole.SPORTS_SCIENTIST, StaffRole.COACH_SET_PIECES, StaffRole.HEAD_OF_RECRUITMENT, StaffRole.DATA_ANALYST,
        StaffRole.PHYSIO, StaffRole.ACADEMY_COACH, StaffRole.DATA_ANALYST, StaffRole.ACADEMY_COACH, StaffRole.DATA_ANALYST
    )
    const val VACANT_SCORE = 3f
    const val SIGN_ON_WEEKS = 4
    const val POOL_MIN_PER_ROLE = 4
    const val BID_WEEKS = 3
    const val MAX_WAGE_SHARE_OF_SAVING = 0.25

    fun typicalAbility(tier: Int): Float = 14.5f - tier
    fun staffedSlots(tier: Int): Int = (SLOT_ORDER.size * (1f - tier * 0.085f)).roundToInt().coerceAtLeast(6)
    fun expectedSlots(tier: Int, role: StaffRole): Int = SLOT_ORDER.take(staffedSlots(tier)).count { it == role }

    fun marketWage(ability: Int, reputation: Int, role: StaffRole): Int =
        max(40, (0.0045 * ability.toDouble().pow(4.8) * role.wageWeight * (0.85 + reputation / 300.0)).toInt())

    private fun rng(w: World, salt: Int): Random = w.rng(9000 + salt)

    // ---------------------------------------------------------------- state access

    fun state(w: World): StaffState {
        val eco = w.eco()
        var s = eco.staff
        if (s == null) { s = StaffState(); eco.staff = s; generateAll(w, s) }
        return s
    }

    fun members(w: World, clubId: Int): List<StaffMember> {
        val s = state(w)
        return (s.byClub[clubId] ?: return emptyList()).mapNotNull { s.members[it] }
    }

    fun inRole(w: World, clubId: Int, role: StaffRole): List<StaffMember> =
        members(w, clubId).filter { it.role == role }.sortedWith(compareByDescending<StaffMember> { it.ability }.thenBy { it.id })

    fun freeAgents(w: World, role: StaffRole? = null): List<StaffMember> =
        state(w).members.values.filter { it.clubId == -1 && (role == null || it.role == role) }
            .sortedWith(compareByDescending<StaffMember> { it.ability }.thenBy { it.id })

    fun weeklyWages(w: World, clubId: Int): Long = members(w, clubId).sumOf { it.wage.toLong() }

    /** Vacant roles within the staffing level a club of this tier normally has (human UI highlights them). */
    fun vacancies(w: World, clubId: Int): List<StaffRole> {
        val tier = w.clubs[clubId].tier
        val out = ArrayList<StaffRole>()
        for (role in StaffRole.values()) {
            val missing = expectedSlots(tier, role) - members(w, clubId).count { it.role == role }
            repeat(max(0, missing)) { out.add(role) }
        }
        return out
    }

    // ---------------------------------------------------------------- generation

    private fun newMember(s: StaffState, rnd: Random, role: StaffRole, centre: Float, extraAge: Int = 0): StaffMember {
        val nation = Names.pickNation(rnd)
        val m = StaffMember(s.nextId++, Names.fullName(nation, rnd), nation, 33 + rnd.nextInt(30) + extraAge, role)
        m.ability = clampI((centre + rnd.gauss().toFloat() * 2.4f).roundToInt(), 1, 20)
        m.judgement = clampI((m.ability + rnd.gauss().toFloat() * 3f).roundToInt(), 1, 20)
        m.motivation = clampI((9 + rnd.gauss().toFloat() * 3.5f + (m.ability - 10) * 0.3f).roundToInt(), 1, 20)
        m.loyalty = 3 + rnd.nextInt(16)
        m.ambition = 3 + rnd.nextInt(16)
        m.reputation = clampI(m.ability * 4 + rnd.nextInt(-10, 11), 3, 98)
        m.wage = marketWage(m.ability, m.reputation, role)
        m.morale = 60f + rnd.nextInt(25)
        return m
    }

    private fun generateAll(w: World, s: StaffState) {
        val rnd = rng(w, 1)
        for (c in w.clubs) {
            val list = ArrayList<Int>()
            s.byClub[c.id] = list
            val centre = typicalAbility(c.tier)
            for (role in SLOT_ORDER.take(staffedSlots(c.tier))) {
                val m = newMember(s, rnd, role, centre)
                m.clubId = c.id
                m.contractEnd = w.year + rnd.nextInt(4)
                m.weeksAtClub = rnd.nextInt(200)
                s.members[m.id] = m
                list.add(m.id)
            }
            s.baseline[c.id] = list.sumOf { s.members.getValue(it).wage.toLong() }
        }
        for (role in StaffRole.values()) refillPool(w, s, rnd, role, 7)
    }

    private fun refillPool(w: World, s: StaffState, rnd: Random, role: StaffRole, target: Int) {
        var have = s.members.values.count { it.clubId == -1 && it.role == role }
        while (have < target) {
            val centre = 6f + rnd.nextFloat() * 8f
            val m = newMember(s, rnd, role, centre)
            s.members[m.id] = m
            have++
        }
    }

    // ---------------------------------------------------------------- effects (all centred on a tier-typical club)

    /** How much better (+) or worse (-) than a normal club of its tier the club's [role] is, roughly -1..+1. */
    fun centered(w: World, clubId: Int, role: StaffRole): Float {
        val tier = w.clubs[clubId].tier
        val have = inRole(w, clubId, role).take(role.slots)
        var sum = 0f
        for (i in 0 until role.slots) sum += if (i < have.size) have[i].effective() else VACANT_SCORE
        val expected = expectedSlots(tier, role)
        val expectedSum = expected * typicalAbility(tier) + (role.slots - expected) * VACANT_SCORE
        return (sum - expectedSum) / role.slots / 10f
    }

    /** Multiplies the weekly chance of attribute progression in Game.completeWeek. */
    fun trainingMultiplier(w: World, clubId: Int): Float {
        if (!Ecosystem.enabled) return 1f
        val c = { r: StaffRole -> centered(w, clubId, r) }
        val q = 0.25f * c(StaffRole.ASSISTANT_MANAGER) + 0.15f * (c(StaffRole.COACH_ATTACKING) + c(StaffRole.COACH_DEFENDING) +
            c(StaffRole.COACH_GOALKEEPING) + c(StaffRole.COACH_FITNESS) + c(StaffRole.COACH_SET_PIECES))
        return (1f + 0.18f * q).coerceIn(0.85f, 1.20f)
    }

    /** Extra weekly condition recovery from the medical and fitness departments (before the global caps). */
    fun recoveryFactor(w: World, clubId: Int): Float {
        val q = 0.4f * centered(w, clubId, StaffRole.PHYSIO) + 0.3f * centered(w, clubId, StaffRole.COACH_FITNESS) +
            0.3f * centered(w, clubId, StaffRole.SPORTS_SCIENTIST)
        return 0.05f * q
    }

    /** Match-day morale from the assistant manager's man-management. */
    fun moraleFactor(w: World, clubId: Int): Float {
        val am = inRole(w, clubId, StaffRole.ASSISTANT_MANAGER).firstOrNull() ?: return -0.5f
        val typical = typicalAbility(w.clubs[clubId].tier)
        return 2f * ((am.motivation + (am.effective() - typical) * 0.3f) - 10f) / 10f
    }

    /** Head of youth and academy coaches shift the club's effective youth rating (used for the yearly intake). */
    fun youthShift(w: World, clubId: Int): Float =
        4f * (0.6f * centered(w, clubId, StaffRole.HEAD_OF_YOUTH) + 0.4f * centered(w, clubId, StaffRole.ACADEMY_COACH))

    /** Extra yearly spend of the human club against the staffing level it started with (never below a quarter saving). */
    fun netWeeklyCost(w: World, c: Club): Long {
        if (!Ecosystem.enabled || !c.human) return 0L
        val s = state(w)
        val base = s.baseline[c.id] ?: return 0L
        return max(weeklyWages(w, c.id) - base, -(base * MAX_WAGE_SHARE_OF_SAVING).toLong())
    }

    // ---------------------------------------------------------------- human actions

    private fun hireBlocker(w: World, m: StaffMember, me: Club): String? {
        if (m.clubId != -1) return "${m.name} is already employed."
        if (members(w, me.id).count { it.role == m.role } >= m.role.slots) return "You already employ the maximum number of ${m.role.label.lowercase()} staff (${m.role.slots})."
        if (m.reputation - me.rep > 30) return "${m.name} is not interested in a club of your standing."
        val signOn = m.wage.toLong() * SIGN_ON_WEEKS
        if (me.balance < signOn) return "You cannot afford the sign-on fee of ${money(signOn)}."
        return null
    }

    fun hire(w: World, staffId: Int, years: Int): Pair<Boolean, String> {
        val s = state(w)
        val m = s.members[staffId] ?: return false to "This candidate is no longer available."
        if (years !in 1..4) return false to "Choose a contract of 1 to 4 years."
        val me = w.human()
        hireBlocker(w, m, me)?.let { return false to it }
        val signOn = m.wage.toLong() * SIGN_ON_WEEKS
        me.balance -= signOn
        s.seasonStaffSpend += signOn
        m.clubId = me.id
        m.contractEnd = w.year + years - 1
        m.weeksAtClub = 0
        m.morale = 75f
        s.byClub.getOrPut(me.id) { ArrayList() }.add(m.id)
        w.addNews("Staff appointment", "${m.name} joins as ${m.role.label.lowercase()} on ${money(m.wage.toLong())} per week until ${m.contractEnd}.")
        return true to "${m.name} has joined as ${m.role.label.lowercase()}."
    }

    fun severance(w: World, m: StaffMember): Long {
        val seasonsLeft = max(0, m.contractEnd - w.year) + 1
        return m.wage.toLong() * 52L * seasonsLeft / 4L
    }

    fun fire(w: World, staffId: Int): Pair<Boolean, String> {
        val s = state(w)
        val m = s.members[staffId] ?: return false to "This person is no longer on the payroll."
        val me = w.human()
        if (m.clubId != me.id) return false to "${m.name} does not work for your club."
        val cost = severance(w, m)
        if (me.balance < cost) return false to "You cannot afford the compensation of ${money(cost)}."
        me.balance -= cost
        s.seasonStaffSpend += cost
        release(s, m)
        for (other in members(w, me.id).filter { it.role.dept == m.role.dept }) other.morale = clampF(other.morale - 4f, 0f, 100f)
        s.bids.removeAll { it.staffId == staffId }
        w.addNews("Staff departure", "${m.name} (${m.role.label.lowercase()}) was dismissed. Compensation: ${money(cost)}.")
        return true to "${m.name} was dismissed. Compensation paid: ${money(cost)}."
    }

    fun renewalWage(m: StaffMember): Int {
        val market = marketWage(m.ability, m.reputation, m.role)
        val mood = if (m.morale < 40f) 1.25 else if (m.morale > 75f) 0.97 else 1.0
        return max(40, (market * mood).toInt())
    }

    fun renew(w: World, staffId: Int, years: Int): Pair<Boolean, String> {
        val s = state(w)
        val m = s.members[staffId] ?: return false to "This person is no longer on the payroll."
        val me = w.human()
        if (m.clubId != me.id) return false to "${m.name} does not work for your club."
        if (years !in 1..4) return false to "Choose a contract of 1 to 4 years."
        if (m.morale < 20f) return false to "${m.name} is too unhappy to sign a new contract."
        m.wage = max(m.wage, renewalWage(m))
        m.contractEnd = w.year + years - 1
        m.morale = clampF(m.morale + 6f, 0f, 100f)
        return true to "${m.name} signed until ${m.contractEnd} on ${money(m.wage.toLong())} per week."
    }

    fun matchBid(w: World, staffId: Int): Pair<Boolean, String> {
        val s = state(w)
        val bid = s.bids.firstOrNull { it.staffId == staffId } ?: return false to "That offer has expired."
        val m = s.members[staffId] ?: return false to "That person has left."
        val me = w.human()
        m.wage = max(m.wage, bid.newWage)
        m.contractEnd = max(m.contractEnd, w.year + 1)
        m.morale = clampF(m.morale + 10f, 0f, 100f)
        s.bids.remove(bid)
        w.addNews("Staff retained", "${m.name} turned down ${w.clubs[bid.bidderClubId].name} and signed a new deal on ${money(m.wage.toLong())} per week.")
        return true to "${m.name} stays. New wage ${money(m.wage.toLong())} per week."
    }

    fun acceptBid(w: World, staffId: Int): Pair<Boolean, String> {
        val s = state(w)
        val bid = s.bids.firstOrNull { it.staffId == staffId } ?: return false to "That offer has expired."
        val m = s.members[staffId] ?: return false to "That person has left."
        val fee = m.wage.toLong() * 8L
        w.human().balance += fee
        moveToClub(s, m, bid.bidderClubId, bid.newWage, w.year + 2)
        s.bids.remove(bid)
        w.addNews("Staff departure", "${m.name} left for ${w.clubs[bid.bidderClubId].name}. You received ${money(fee)} compensation.")
        return true to "${m.name} was allowed to leave. Compensation received: ${money(fee)}."
    }

    // ---------------------------------------------------------------- internal moves

    private fun release(s: StaffState, m: StaffMember) {
        s.byClub[m.clubId]?.remove(m.id)
        m.clubId = -1
        m.weeksAtClub = 0
    }

    private fun moveToClub(s: StaffState, m: StaffMember, clubId: Int, wage: Int, contractEnd: Int) {
        release(s, m)
        m.clubId = clubId
        m.wage = wage
        m.contractEnd = contractEnd
        m.morale = 80f
        s.byClub.getOrPut(clubId) { ArrayList() }.add(m.id)
    }

    // ---------------------------------------------------------------- weekly and seasonal simulation

    /** Call once per week from Ecosystem.preWeek. Uses its own seeded Random; never touches the game's streams. */
    fun weekly(w: World) {
        val s = state(w)
        val rnd = rng(w, 100 + w.week)
        for (m in s.members.values) {
            if (m.clubId < 0) continue
            m.weeksAtClub++
            val club = w.clubs[m.clubId]
            val fair = marketWage(m.ability, m.reputation, m.role)
            val wageSat = ((m.wage.toFloat() / fair - 1f) * 10f).coerceIn(-10f, 10f)
            var target = 65f + (club.lastPts - 1) * 5f + wageSat
            if (club.human) target += (club.boardConfidence - 50f) / 10f
            m.morale = clampF(m.morale + (target - m.morale) * 0.1f, 0f, 100f)
        }
        s.bids.removeAll { Business.absWeek(w) > it.expiresAbs || !s.members.containsKey(it.staffId) }
        for (id in s.bids.map { it.staffId }.toList()) {
            val m = s.members[id] ?: continue
            if (m.morale < 25f && m.loyalty < 10) { // an unhappy, disloyal employee takes the bigger offer at once
                val bid = s.bids.first { it.staffId == id }
                moveToClub(s, m, bid.bidderClubId, bid.newWage, w.year + 2)
                s.bids.remove(bid)
                w.addNews("Staff departure", "${m.name} walked out for ${w.clubs[bid.bidderClubId].name}.")
            }
        }
        aiFillVacancies(w, s, rnd)
        if (w.week % 6 == 0) aiPoaching(w, s, rnd)
        for (role in StaffRole.values()) refillPool(w, s, rnd, role, POOL_MIN_PER_ROLE)
    }

    private fun aiFillVacancies(w: World, s: StaffState, rnd: Random) {
        for (c in w.clubs) {
            if (c.human || rnd.nextFloat() > 0.12f) continue
            val role = vacancies(w, c.id).firstOrNull() ?: continue
            val cap = (s.baseline[c.id] ?: 0L).toDouble() / max(1, staffedSlots(c.tier)) * 2.0
            val pick = freeAgents(w, role).firstOrNull { it.wage <= cap && it.reputation - c.rep <= 30 } ?: continue
            moveToClub(s, pick, c.id, pick.wage, w.year + 1 + rnd.nextInt(3))
        }
    }

    private fun aiPoaching(w: World, s: StaffState, rnd: Random) {
        val abs = Business.absWeek(w)
        for (bidder in w.clubs) {
            if (bidder.human || rnd.nextFloat() > 0.06f) continue
            val victims = s.members.values.filter { m ->
                m.clubId >= 0 && m.clubId != bidder.id && w.clubs[m.clubId].rep + 8 <= bidder.rep &&
                    m.ability >= inRole(w, bidder.id, m.role).getOrNull(m.role.slots - 1).let { (it?.ability ?: 0) + 2 } &&
                    members(w, bidder.id).count { it.role == m.role } <= m.role.slots
            }
            val target = victims.maxWithOrNull(compareBy<StaffMember> { it.ability }.thenBy { -it.id }) ?: continue
            val offer = (target.wage * (1.15 + rnd.nextDouble() * 0.2)).toInt()
            val victimClub = w.clubs[target.clubId]
            if (victimClub.human) {
                if (target.loyalty >= 16 || s.bids.any { it.staffId == target.id }) continue
                s.bids.add(PoachBid(target.id, bidder.id, offer, abs + BID_WEEKS))
                w.addNews("Staff poaching attempt", "${bidder.name} want ${target.name} (${target.role.label.lowercase()}) for ${money(offer.toLong())} per week. Match the offer or let him go from the Org screen within $BID_WEEKS weeks.")
            } else {
                // Replace the weakest incumbent of that role and release him to the pool.
                val slotFull = members(w, bidder.id).count { it.role == target.role } >= target.role.slots
                if (slotFull) {
                    val weakest = inRole(w, bidder.id, target.role).last()
                    release(s, weakest)
                }
                moveToClub(s, target, bidder.id, offer, w.year + 2)
            }
        }
    }

    /** Season review: contracts expire, people age, abilities drift. Call before the year number changes. */
    fun seasonReview(w: World) {
        val s = state(w)
        val rnd = rng(w, 5000 + w.year)
        val expiring = s.members.values.filter { it.clubId >= 0 && it.contractEnd <= w.year }.sortedBy { it.id }
        for (m in expiring) {
            val club = w.clubs[m.clubId]
            if (club.human) {
                w.addNews("Staff contract expired", "${m.name} (${m.role.label.lowercase()}) left because his contract ran out.")
                release(s, m)
            } else if (rnd.nextFloat() < 0.75f && m.ability + 1 >= typicalAbility(club.tier)) {
                m.contractEnd = w.year + 1 + rnd.nextInt(3)
                m.wage = max(40, (renewalWage(m) * (0.95 + rnd.nextDouble() * 0.15)).toInt())
            } else release(s, m)
        }
        for (m in s.members.values.sortedBy { it.id }) {
            m.age++
            val drift = rnd.nextFloat()
            if (m.age < 50 && drift < 0.18f) m.ability = min(20, m.ability + 1)
            else if (m.age >= 58 && drift < 0.22f) m.ability = max(1, m.ability - 1)
            if (m.clubId >= 0) {
                val club = w.clubs[m.clubId]
                m.reputation = clampI((m.reputation * 0.8f + (m.ability * 4f + club.rep * 0.3f) * 0.2f).roundToInt(), 3, 98)
            }
        }
        val retired = s.members.values.filter { it.age >= 66 && rnd.nextFloat() < 0.4f + (it.age - 66) * 0.1f }.map { it.id }.sorted()
        for (id in retired) {
            val m = s.members.getValue(id)
            if (m.clubId >= 0) {
                if (w.clubs[m.clubId].human) w.addNews("Staff retirement", "${m.name} (${m.role.label.lowercase()}) retired.")
                release(s, m)
            }
            s.members.remove(id)
            s.bids.removeAll { it.staffId == id }
        }
        for (role in StaffRole.values()) refillPool(w, s, rnd, role, POOL_MIN_PER_ROLE + w.clubs.count { !it.human && vacancies(w, it.id).contains(role) })
        // AI clubs restock straight away: a whole department expiring at once must not leave them short for months.
        for (c in w.clubs) {
            if (c.human) continue
            val cap = (s.baseline[c.id] ?: 0L).toDouble() / max(1, staffedSlots(c.tier)) * 2.0
            for (role in vacancies(w, c.id)) {
                val pool = freeAgents(w, role)
                val pick = pool.firstOrNull { it.wage <= cap && it.reputation - c.rep <= 30 } ?: pool.minByOrNull { it.wage } ?: continue
                moveToClub(s, pick, c.id, min(pick.wage, max(40, cap.toInt())), w.year + 1 + rnd.nextInt(3))
            }
        }
        s.seasonStaffSpend = 0L
    }
}
