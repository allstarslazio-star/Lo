package com.nerdfm.game

import java.io.File
import kotlin.math.max
import kotlin.random.Random

/**
 * Headless multi-season career driver over the REAL game path (WorldGen -> Game.startWeek -> human match ->
 * Game.completeWeek). A tiny "bot manager" keeps the human club playable (renews expiring deals, signs free agents
 * when the squad thins out, takes a new job after a sacking) so the run can cover many seasons without a UI.
 *
 * After every week it checks the invariants the M8 plan lists; after every season it saves, reloads and proves the
 * reloaded career is the same state. Any exception is captured with a reproducible [Diagnostics] report.
 */
object HeadlessCareerRunner {
    class Config(
        val seed: Long,
        val seasons: Int,
        val humanClub: Int = 0,
        val maxWeeks: Int = Int.MAX_VALUE,
        val roundTripEverySeason: Boolean = true
    )

    class Report(val seed: Long) {
        var weeks = 0
        var seasons = 0
        var sackings = 0
        var startPlayers = 0
        var maxPlayers = 0
        var worstWeekMs = 0L
        var worstSaveMs = 0L
        var warnings = 0
        var finalFingerprint = 0L
        val failures = ArrayList<String>()

        fun ok(): Boolean = failures.isEmpty()

        fun text(): String =
            "soak seed=$seed weeks=$weeks seasons=$seasons sackings=$sackings players=$startPlayers->$maxPlayers(max) " +
                "worstWeek=${worstWeekMs}ms worstSave=${worstSaveMs}ms warnings=$warnings failures=${failures.size}" +
                (if (failures.isEmpty()) "" else "\n" + failures.take(10).joinToString("\n"))
    }

    fun run(config: Config, scratch: File): Report {
        val report = Report(config.seed)
        val w = WorldGen.generate(config.seed, "Soak", config.humanClub)
        report.startPlayers = w.players.size
        report.maxPlayers = w.players.size
        val clubCount = w.clubs.size
        var guard = 0
        val weekLimit = if (config.maxWeeks == Int.MAX_VALUE) config.seasons * 40 + 10 else config.maxWeeks
        while ((config.maxWeeks != Int.MAX_VALUE || report.seasons < config.seasons) && report.weeks < weekLimit && guard < weekLimit + 5) {
            guard++
            try {
                val yearBefore = w.year
                botManage(w)
                val started = System.nanoTime()
                PerfMonitor.time(PerfMonitor.WEEK) {
                    val fixture = Game.startWeek(w)
                    if (fixture != null) {
                        val engine = Game.humanEngine(w, fixture)
                        engine.simToEnd()
                        Game.finishHuman(w, fixture, engine)
                    }
                    Game.completeWeek(w)
                }
                report.worstWeekMs = max(report.worstWeekMs, (System.nanoTime() - started) / 1_000_000L)
                report.weeks++
                if (w.sacked) { takeNewJob(w); report.sackings++ }
                checkInvariants(w, report, clubCount, "week ${report.weeks} (${w.seasonLabel()} wk ${w.week})")
                if (w.year != yearBefore) {
                    report.seasons++
                    if (config.roundTripEverySeason) roundTrip(w, report, File(scratch, "career-${config.seed}"))
                }
            } catch (e: Throwable) {
                report.failures += "exception after ${report.weeks} weeks: ${e.javaClass.simpleName}: ${e.message}"
                report.failures += Diagnostics.render(Diagnostics.capture(w, "headless-week", e))
                break
            }
            if (report.failures.size >= 20) break
        }
        report.finalFingerprint = StateFingerprint.of(w)
        return report
    }

    /** Keeps the human club legal and playable; deliberately simple and deterministic. */
    private fun botManage(w: World) {
        val me = w.human()
        val rnd = Random(w.seed + w.year * 31L + w.week)
        if (w.week == 0 || w.week == 20) {
            for (p in w.squad(me).toList()) {
                if (p.contractEnd <= w.year + 1 && Loans.loanFor(w, p.id) == null) Transfers.renew(w, p, p.wageDemand(), 3)
            }
        }
        if (me.balance < 0L) me.balance = me.revenue / 10L
        var guard = 0
        while (guard++ < 20) {
            val keepers = w.squad(me).count { it.pos == Pos.GK }
            if (me.players.size >= 22 && keepers >= 2) break
            val needKeeper = keepers < 2
            val candidate = w.players.values
                .filter { it.clubId < 0 && it.age >= 18 && (!needKeeper || it.pos == Pos.GK) }
                .maxByOrNull { it.ovr } ?: break
            Transfers.signFree(w, candidate, me, rnd)
        }
    }

    private fun takeNewJob(w: World) {
        val old = w.human()
        val next = w.clubs.firstOrNull { it.id != old.id && !it.human && it.rep <= old.rep + 6 } ?: w.clubs.first { it.id != old.id }
        old.human = false
        old.autoPick = true
        next.human = true
        next.autoPick = true
        next.boardConfidence = 60f
        w.humanClubId = next.id
        w.sacked = false
        w.sackReason = ""
        w.offers.clear()
        Lineups.autoPick(w, next)
    }

    private fun checkInvariants(w: World, report: Report, clubCount: Int, label: String) {
        val audit = WorldIntegrity.audit(w)
        report.warnings += audit.warningCount
        if (!audit.ok) report.failures += "$label: " + audit.summary(6)
        report.maxPlayers = max(report.maxPlayers, w.players.size)
        if (w.players.size > report.startPlayers * 3 / 2 + 2_000) report.failures += "$label: player count is growing without bound (${w.players.size})"
        if (w.clubs.size != clubCount) report.failures += "$label: club count changed from $clubCount to ${w.clubs.size}"
        for (c in w.clubs) {
            if (c.players.isEmpty()) report.failures += "$label: ${c.name} has an empty squad"
            if (c.balance < -50_000_000_000L || c.balance > 5_000_000_000_000L) report.failures += "$label: ${c.name} balance ${c.balance} is implausible"
            if (c.revenue <= 0L) report.failures += "$label: ${c.name} has non-positive revenue"
        }
    }

    private fun roundTrip(w: World, report: Report, dir: File) {
        val before = StateFingerprint.of(w)
        val outcome = SaveCoordinator.save(dir, w)
        report.worstSaveMs = max(report.worstSaveMs, outcome.serializeMillis + outcome.writeMillis)
        if (!outcome.ok) { report.failures += "season ${report.seasons}: save failed"; return }
        val loaded = (Persistence.load(dir) as? LoadResult.Loaded)?.world
        if (loaded == null) { report.failures += "season ${report.seasons}: reload failed"; return }
        if (StateFingerprint.of(loaded) != before) report.failures += "season ${report.seasons}: save/load changed the career state"
        val audit = WorldIntegrity.audit(loaded)
        if (!audit.ok) report.failures += "season ${report.seasons}: reloaded career is invalid: " + audit.summary(4)
    }
}
