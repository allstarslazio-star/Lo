package com.nerdfm.game

/**
 * Headless season runner over the REAL game path (WorldGen -> Game.startWeek -> human match -> Game.completeWeek),
 * unlike MatchCalibrationHarness, which feeds hand-built, equal-strength teams straight into the engine.
 * Every ecosystem milestone uses it to prove that new modifiers keep league-wide averages inside the golden bands.
 */
object WorldBaselineHarness {
    class SeasonResult(
        val seed: Long,
        val fixtures: Int,
        val goals: Int,
        val homeWins: Int,
        val draws: Int,
        val awayWins: Int,
        val shots: Long,
        val xg: Double,
        val injuredPlayerWeeks: Long,
        val playerWeeks: Long,
        val moraleSum: Double,
        val conditionSum: Double,
        val digest: Long
    )

    class Aggregate(val seasons: List<SeasonResult>) {
        val fixtures: Int = seasons.sumOf { it.fixtures }
        val goalsPerMatch: Double = seasons.sumOf { it.goals }.toDouble() / fixtures
        val homeWinRate: Double = seasons.sumOf { it.homeWins }.toDouble() / fixtures
        val drawRate: Double = seasons.sumOf { it.draws }.toDouble() / fixtures
        val awayWinRate: Double = seasons.sumOf { it.awayWins }.toDouble() / fixtures
        val shotsPerMatch: Double = seasons.sumOf { it.shots }.toDouble() / fixtures
        val xgPerMatch: Double = seasons.sumOf { it.xg } / fixtures
        val injuredPer1000PlayerWeeks: Double =
            seasons.sumOf { it.injuredPlayerWeeks }.toDouble() * 1000.0 / seasons.sumOf { it.playerWeeks }
        val meanMorale: Double = seasons.sumOf { it.moraleSum } / seasons.sumOf { it.playerWeeks }
        val meanCondition: Double = seasons.sumOf { it.conditionSum } / seasons.sumOf { it.playerWeeks }
        val digest: Long = seasons.fold(FNV_BASIS) { acc, s -> (acc xor s.digest) * FNV_PRIME }

        fun text(): String = "fixtures=$fixtures goals=%.3f home=%.3f draw=%.3f away=%.3f shots=%.2f xg=%.2f inj/1000pw=%.2f morale=%.2f cond=%.2f"
            .format(goalsPerMatch, homeWinRate, drawRate, awayWinRate, shotsPerMatch, xgPerMatch, injuredPer1000PlayerWeeks, meanMorale, meanCondition)
    }

    private const val FNV_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L

    /** Plays weeks 1..34 of one generated career (every tier) and returns league-wide statistics. */
    fun playSeason(seed: Long): SeasonResult {
        val w = WorldGen.generate(seed, "Harness", 0)
        var digest = FNV_BASIS
        fun mix(v: Long) { digest = (digest xor v) * FNV_PRIME }
        var injured = 0L
        var playerWeeks = 0L
        var moraleSum = 0.0
        var conditionSum = 0.0
        var fixtures = 0; var goals = 0; var hw = 0; var dr = 0; var aw = 0
        var shots = 0L; var xg = 0.0
        for (wk in 1..34) {
            w.week = wk
            val fx = Game.startWeek(w)
            if (fx != null) {
                val engine = Game.humanEngine(w, fx)
                engine.simToEnd()
                Game.finishHuman(w, fx, engine)
            }
            for (p in w.players.values) {
                if (p.clubId < 0) continue
                playerWeeks++
                if (p.injWeeks > 0) injured++
                moraleSum += p.morale
                conditionSum += p.condition
                mix(p.condition.toRawBits().toLong()); mix(p.morale.toRawBits().toLong()); mix(p.injWeeks.toLong())
            }
            if (wk == 34) {
                for (lg in w.leagues) for (f in lg.fixtures) if (f.played) {
                    fixtures++; goals += f.hg + f.ag
                    if (f.hg > f.ag) hw++ else if (f.hg == f.ag) dr++ else aw++
                    mix((f.hg * 31 + f.ag).toLong())
                }
                for (p in w.players.values) { shots += p.seasonShots; xg += p.seasonXg }
            }
            Game.completeWeek(w)
            w.sacked = false
        }
        return SeasonResult(seed, fixtures, goals, hw, dr, aw, shots, xg, injured, playerWeeks, moraleSum, conditionSum, digest)
    }

    fun run(seeds: LongRange): Aggregate = Aggregate(seeds.map { playSeason(it) })
}
