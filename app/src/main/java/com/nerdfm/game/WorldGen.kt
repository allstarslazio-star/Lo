package com.nerdfm.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

fun Random.gauss(): Double {
    val u1 = 1.0 - nextDouble()
    val u2 = nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}

object WorldGen {
    val LEAGUE_NAMES = listOf(
        "Solaris Crown League", "Ironvale Championship", "Namana Premier League", "Harrowgate Union League",
        "Brightwater Alliance", "Thornfield Counties League", "Mossbank Regional League"
    )
    val MEAN_OVR = intArrayOf(66, 59, 53, 48, 44, 40, 36)
    val BASE_REP = intArrayOf(75, 62, 50, 40, 32, 25, 18)
    val STADIUM = intArrayOf(38000, 20000, 11000, 6000, 3200, 1600, 800)
    const val CLUBS_PER_LEAGUE = 18

    private val SENIOR = listOf(
        Pos.GK to 3, Pos.LB to 2, Pos.RB to 2, Pos.CB to 4, Pos.DM to 2,
        Pos.CM to 4, Pos.LW to 2, Pos.RW to 2, Pos.AM to 2, Pos.ST to 3
    )
    private val YOUTH = listOf(
        Pos.GK to 2, Pos.LB to 1, Pos.RB to 1, Pos.CB to 3, Pos.DM to 1,
        Pos.CM to 3, Pos.LW to 1, Pos.RW to 1, Pos.AM to 1, Pos.ST to 2
    )
    private val DEPTH_ADJ = floatArrayOf(3.5f, 0.5f, -3f, -6f)

    private fun secondary(pos: Pos, rnd: Random): Pos? {
        if (rnd.nextInt(10) > 2) return null
        val opts: List<Pos> = when (pos) {
            Pos.GK -> return null
            Pos.LB -> listOf(Pos.CB, Pos.LW)
            Pos.RB -> listOf(Pos.CB, Pos.RW)
            Pos.CB -> listOf(Pos.DM, Pos.LB, Pos.RB)
            Pos.DM -> listOf(Pos.CM, Pos.CB)
            Pos.CM -> listOf(Pos.DM, Pos.AM)
            Pos.LW -> listOf(Pos.AM, Pos.LB, Pos.ST)
            Pos.RW -> listOf(Pos.AM, Pos.RB, Pos.ST)
            Pos.AM -> listOf(Pos.CM, Pos.ST, Pos.LW, Pos.RW)
            Pos.ST -> listOf(Pos.AM, Pos.LW, Pos.RW)
        }
        return opts[rnd.nextInt(opts.size)]
    }

    fun genAttrs(pos: Pos, target: Float, rnd: Random): IntArray {
        val w = Roles.weights[pos]!!
        val base = target / 5f
        var mx = 0f
        for (x in w) if (x > mx) mx = x
        val raw = FloatArray(NATTR)
        val fixed = BooleanArray(NATTR)
        for (i in 0 until NATTR) {
            val a = ATTRS[i]
            val rel = w[i] / mx
            if (pos != Pos.GK && a.cat == 3) {
                raw[i] = (1 + rnd.nextInt(4)).toFloat()
                fixed[i] = true
            } else if (pos == Pos.GK && a.cat != 3 && w[i] < 0.5f) {
                raw[i] = 7f + rnd.gauss().toFloat() * 2.5f
                fixed[i] = true
            } else {
                raw[i] = base * (0.55f + 0.6f * rel) + rnd.gauss().toFloat() * 1.7f
            }
        }
        val out = IntArray(NATTR)
        for (iter in 0 until 5) {
            for (i in 0 until NATTR) out[i] = clampI(raw[i].roundToInt(), 1, 20)
            val d = (target - Roles.overall(out, pos)) / 5f
            for (i in 0 until NATTR) if (!fixed[i]) raw[i] += d
        }
        for (i in 0 until NATTR) out[i] = clampI(raw[i].roundToInt(), 1, 20)
        return out
    }

    fun makePlayer(w: World, rnd: Random, pos: Pos, targetOvr: Float, age: Int, pa: Int, nation: Int = Names.pickNation(rnd)): Player {
        val attrs = genAttrs(pos, targetOvr, rnd)
        // ageing physical profile
        val p = Player(
            w.nextPid++, Names.fullName(nation, rnd), nation, age, pos, secondary(pos, rnd), attrs,
            0,
            clampI((12 + rnd.gauss() * 3.5).roundToInt(), 1, 20),
            clampI((10 + rnd.gauss() * 4).roundToInt(), 1, 20),
            clampI((12 + rnd.gauss() * 3.5).roundToInt(), 1, 20),
            clampI((11 + rnd.gauss() * 4).roundToInt(), 1, 20),
            clampI((11 + rnd.gauss() * 4).roundToInt(), 1, 20)
        )
        p.refresh()
        p.pa = clampI(max(pa, p.ovr), 1, 97)
        p.condition = 100f
        p.morale = 70f
        p.form = 60f
        return p
    }

    private fun potentialFor(ovr: Int, age: Int, rnd: Random): Int {
        if (age >= 25) return ovr
        val years = 25 - age
        var g = years * (1.6 + rnd.gauss() * 1.6)
        if (g < 0) g = 0.0
        if (rnd.nextInt(25) == 0) g += 8.0 + rnd.nextInt(10)
        return (ovr + g).roundToInt()
    }

    private fun seniorAge(rnd: Random): Int = clampI((26 + rnd.gauss() * 4.3).roundToInt(), 18, 37)

    fun signContract(w: World, p: Player, clubId: Int, rnd: Random) {
        p.clubId = clubId
        p.wage = max(300, (p.wageDemand() * (0.85 + rnd.nextDouble() * 0.3)).toInt())
        val maxY = if (p.age >= 33) 1 else 4
        p.contractEnd = w.year + 1 + rnd.nextInt(maxY)
    }

    fun generate(seed: Long, manager: String, humanClub: Int = 0): World {
        val w = World()
        w.seed = seed
        w.manager = manager
        val rnd = Random(seed)

        // club names
        val roots = ArrayList<String>()
        for (a in Names.clubPre) for (b in Names.clubSuf) roots.add(a + b)
        roots.shuffle(rnd)
        val palette = listOf(0xFFD32F2F, 0xFF1976D2, 0xFF388E3C, 0xFFF9A825, 0xFF7B1FA2, 0xFF00838F, 0xFFEF6C00,
            0xFF5D4037, 0xFF455A64, 0xFFC2185B, 0xFFFFFFFF, 0xFF212121)

        var cid = 0
        for (t in 0 until 7) {
            val lg = League(t, LEAGUE_NAMES[t], t)
            w.leagues.add(lg)
            for (i in 0 until CLUBS_PER_LEAGUE) {
                val root = roots[cid]
                val name = root.replaceFirstChar { it.uppercase() } + " " + Names.clubWord[rnd.nextInt(Names.clubWord.size)]
                val rep = clampI(BASE_REP[t] + ((8.5 - i) * 1.15).toInt() + rnd.nextInt(5) - 2, 5, 95)
                val stadium = (STADIUM[t] * (0.7 + rep / 150.0) * (0.9 + rnd.nextDouble() * 0.2)).toInt()
                val c = Club(cid, name, t, rep, stadium, palette[rnd.nextInt(palette.size)], palette[rnd.nextInt(palette.size)])
                c.facilities = clampI((BASE_REP[t] / 5 + rnd.nextInt(5)), 2, 18)
                c.youth = clampI((BASE_REP[t] / 5 + rnd.nextInt(5)), 2, 18)
                c.scout = clampI((BASE_REP[t] / 5 + rnd.nextInt(5)), 2, 18)
                w.clubs.add(c)
                lg.clubIds.add(cid)
                cid++
            }
        }

        // squads
        for (c in w.clubs) {
            val q = MEAN_OVR[c.tier] + (c.rep - BASE_REP[c.tier]) * 0.25f + rnd.gauss().toFloat() * 1.5f
            for ((pos, n) in SENIOR) {
                for (k in 0 until n) {
                    val age = seniorAge(rnd)
                    var target = q + DEPTH_ADJ[min(k, 3)] + rnd.gauss().toFloat() * 3.5f
                    if (age < 24) target -= (24 - age) * 1.1f
                    if (age > 31) target -= (age - 31) * 0.8f
                    target = clampF(target, 18f, 92f)
                    val p = makePlayer(w, rnd, pos, target, age, potentialFor(target.roundToInt(), age, rnd))
                    signContract(w, p, c.id, rnd)
                    w.players[p.id] = p
                    c.players.add(p.id)
                }
            }
            for ((pos, n) in YOUTH) {
                for (k in 0 until n) {
                    val age = 16 + rnd.nextInt(4)
                    val target = clampF(q - 14f + rnd.gauss().toFloat() * 4f, 15f, 70f)
                    var pa = target + 8 + rnd.nextInt(14)
                    if (rnd.nextInt(18) == 0) pa += 15f
                    val p = makePlayer(w, rnd, pos, target, age, pa.roundToInt())
                    signContract(w, p, c.id, rnd)
                    w.players[p.id] = p
                    c.players.add(p.id)
                }
            }
            val bill = w.wageBill(c)
            c.revenue = (bill * 52 / (0.5 + 0.2 * rnd.nextDouble())).toLong()
            c.wageBudget = (bill * 1.06).toLong()
            c.transferBudget = (c.revenue * 0.12 * (0.5 + rnd.nextDouble())).toLong()
            c.balance = (c.revenue * (0.1 + 0.3 * rnd.nextDouble())).toLong()
            c.seasonStartBalance = c.balance
        }

        // free agents
        addFreeAgents(w, rnd, 320)

        w.humanClubId = humanClub
        w.clubs[humanClub].human = true
        w.clubs[humanClub].boardConfidence = 60f
        for (c in w.clubs) Lineups.autoPick(w, c)
        startSeason(w, rnd)
        return w
    }

    fun addFreeAgents(w: World, rnd: Random, n: Int) {
        val all = SENIOR.flatMap { pr -> List(pr.second) { pr.first } }
        for (i in 0 until n) {
            val pos = all[rnd.nextInt(all.size)]
            val age = clampI((25 + rnd.gauss() * 5).roundToInt(), 18, 36)
            val target = 26f + rnd.nextFloat().toDouble().pow(1.7).toFloat() * 40f
            val p = makePlayer(w, rnd, pos, target, age, potentialFor(target.roundToInt(), age, rnd))
            p.clubId = -1
            p.wage = 0
            p.contractEnd = 0
            w.players[p.id] = p
        }
    }

    fun startSeason(w: World, rnd: Random) {
        for (lg in w.leagues) {
            lg.fixtures.clear()
            lg.table.clear()
            for (id in lg.clubIds) lg.table[id] = TableRow(id)
            makeFixtures(lg, rnd)
        }
        for (c in w.clubs) c.seasonStartBalance = c.balance
        for (p in w.players.values) {
            p.apps = 0; p.goals = 0; p.assists = 0; p.minutes = 0; p.ratingSum = 0f
            p.yellows = 0
        }
    }

    private fun makeFixtures(lg: League, rnd: Random) {
        val ids = ArrayList(lg.clubIds)
        ids.shuffle(rnd)
        val n = ids.size
        val firstHalf = ArrayList<Fixture>()
        val rot = ArrayList(ids)
        for (r in 0 until n - 1) {
            for (i in 0 until n / 2) {
                val a = rot[i]
                val b = rot[n - 1 - i]
                val homeFirst = (r + i) % 2 == 0
                firstHalf.add(Fixture(r + 1, if (homeFirst) a else b, if (homeFirst) b else a))
            }
            val last = rot.removeAt(n - 1)
            rot.add(1, last)
        }
        lg.fixtures.addAll(firstHalf)
        for (f in firstHalf) lg.fixtures.add(Fixture(f.round + n - 1, f.away, f.home))
    }
}
