package com.nerdfm.game

/** Everything needed to reproduce a failure, with personal data scrubbed. */
class FailureReport(
    val operation: String,
    val gameVersion: String,
    val saveVersion: Int,
    val seed: Long?,
    val season: String,
    val week: Int?,
    val errorType: String?,
    val errorMessage: String?,
    val stackTop: List<String>,
    val integritySummary: String,
    val fingerprint: Long?,
    val recentOperations: List<String>,
    val device: String
)

/**
 * Stable 64-bit digest of the persisted career state. Two runs with the same seed and the same inputs must produce the
 * same fingerprint (the determinism contract), and a save/load round trip must not change it.
 * Only deterministic, persisted fields are hashed; players, table rows and shortlist entries are visited in sorted order
 * so Java's HashMap iteration order can never leak into the result.
 */
object StateFingerprint {
    private const val BASIS = -3750763034362895579L
    private const val PRIME = 1099511628211L

    private class Hasher {
        var h = BASIS
        fun long(v: Long) { h = (h xor v) * PRIME }
        fun int(v: Int) = long(v.toLong())
        fun float(v: Float) = int(v.toBits())
        fun double(v: Double) = long(v.toBits())
        fun bool(v: Boolean) = int(if (v) 1 else 0)
        fun text(v: String) { for (c in v) int(c.code); int(-1) }
    }

    fun of(world: World): Long {
        val h = Hasher()
        h.long(world.seed); h.int(world.year); h.int(world.week); h.int(world.humanClubId); h.int(world.nextPid)
        h.int(world.trophies); h.int(world.seasonsManaged); h.float(world.managerReputation); h.bool(world.sacked)
        for (club in world.clubs) {
            h.int(club.id); h.int(club.tier); h.int(club.rep); h.long(club.balance); h.long(club.revenue)
            h.long(club.wageBudget); h.long(club.transferBudget); h.int(club.facilities); h.int(club.youth); h.int(club.scout)
            h.float(club.boardConfidence); h.float(club.teamCohesion); h.bool(club.human)
            h.int(club.tactics.formation); h.int(club.tactics.mentality)
            h.int(club.players.size)
            for (id in club.players) h.int(id)
        }
        for (id in world.players.keys.sorted()) {
            val p = world.players.getValue(id)
            h.int(p.id); h.int(p.age); h.int(p.pos.ordinal); h.int(p.clubId); h.int(p.wage); h.int(p.contractEnd); h.int(p.pa)
            for (a in p.attrs) h.int(a)
            h.float(p.condition); h.float(p.morale); h.float(p.sharpness); h.float(p.form)
            h.int(p.injWeeks); h.int(p.suspWeeks); h.int(p.apps); h.int(p.goals); h.int(p.assists); h.int(p.minutes)
            h.float(p.ratingSum); h.double(p.seasonXg)
        }
        for (league in world.leagues) {
            for (f in league.fixtures) { h.int(f.round); h.int(f.home); h.int(f.away); h.bool(f.played); h.int(f.hg); h.int(f.ag) }
            for (id in league.table.keys.sorted()) {
                val r = league.table.getValue(id)
                h.int(r.clubId); h.int(r.p); h.int(r.w); h.int(r.d); h.int(r.l); h.int(r.gf); h.int(r.ga); h.int(r.pts)
                h.text(r.form.toString())
            }
        }
        for (o in world.offers) { h.int(o.playerId); h.int(o.fromClub); h.long(o.fee); h.int(o.expiresWeek) }
        for (id in world.shortlist.sorted()) h.int(id)
        for (id in world.scouted.sorted()) h.int(id)
        return h.h
    }
}

/** Failure capture and a short in-memory operation trail (no files, no network). */
object Diagnostics {
    /** Keep in sync with versionName in app/build.gradle.kts (a unit test enforces it). */
    const val GAME_VERSION = "0.1"
    const val MAX_RECENT = 40

    private val recent = ArrayDeque<String>()

    @Synchronized
    fun note(operation: String, detail: String = "") {
        recent.addLast(if (detail.isEmpty()) operation else "$operation: ${scrub(detail)}")
        while (recent.size > MAX_RECENT) recent.removeFirst()
    }

    @Synchronized
    fun recentOperations(): List<String> = recent.toList()

    @Synchronized
    fun clear() {
        recent.clear()
    }

    /**
     * Builds a report for [operation]. Never throws: a damaged world must still yield a report, so every probe of the
     * world is individually guarded.
     */
    fun capture(world: World?, operation: String, error: Throwable? = null, device: String = "unknown"): FailureReport {
        var integrity = "no world loaded"
        var fingerprint: Long? = null
        if (world != null) {
            integrity = try { WorldIntegrity.audit(world).summary(6) } catch (e: Throwable) { "audit failed: ${e.javaClass.simpleName}" }
            fingerprint = try { StateFingerprint.of(world) } catch (_: Throwable) { null }
        }
        val season = try { world?.seasonLabel() ?: "-" } catch (_: Throwable) { "-" }
        return FailureReport(
            operation = operation,
            gameVersion = GAME_VERSION,
            saveVersion = world?.saveVersion ?: Persistence.CURRENT_SAVE_VERSION,
            seed = world?.seed,
            season = season,
            week = world?.week,
            errorType = error?.javaClass?.name,
            errorMessage = error?.message?.let { scrub(it) },
            stackTop = error?.stackTrace?.take(8)?.map { "${it.className}.${it.methodName}:${it.lineNumber}" } ?: emptyList(),
            integritySummary = integrity,
            fingerprint = fingerprint,
            recentOperations = recentOperations(),
            device = device
        )
    }

    /** Plain `key: value` text. The manager name, club names and file paths are intentionally absent. */
    fun render(report: FailureReport): String {
        val sb = StringBuilder()
        sb.append("NerdFM failure report\n")
        sb.append("operation: ").append(report.operation).append('\n')
        sb.append("game-version: ").append(report.gameVersion).append('\n')
        sb.append("save-version: ").append(report.saveVersion).append('\n')
        sb.append("seed: ").append(report.seed?.toString() ?: "-").append('\n')
        sb.append("season: ").append(report.season).append('\n')
        sb.append("week: ").append(report.week?.toString() ?: "-").append('\n')
        sb.append("state-fingerprint: ").append(report.fingerprint?.let { java.lang.Long.toHexString(it) } ?: "-").append('\n')
        sb.append("device: ").append(report.device).append('\n')
        if (report.errorType != null) {
            sb.append("error: ").append(report.errorType).append(": ").append(report.errorMessage ?: "").append('\n')
            for (frame in report.stackTop) sb.append("  at ").append(frame).append('\n')
        }
        sb.append("integrity: ").append(report.integritySummary).append('\n')
        if (report.recentOperations.isNotEmpty()) {
            sb.append("recent operations:\n")
            for (line in report.recentOperations) sb.append("  ").append(line).append('\n')
        }
        return sb.toString()
    }

    private val PATH = Regex("(?<![A-Za-z0-9_.\\-])(?:[A-Za-z]:)?(?:[/\\\\][A-Za-z0-9_.\\-]+){2,}")
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    /** Removes absolute file paths and e-mail addresses from free text before it is stored or shared. */
    fun scrub(text: String): String = EMAIL.replace(PATH.replace(text, "<path>"), "<email>")
}

/** Keeps the newest few failure reports on disk so a bug report can be attached without any network access. */
class DiagnosticsStore(private val directory: java.io.File, private val keep: Int = 5) {
    fun write(report: FailureReport): java.io.File? {
        return try {
            if (!directory.exists() && !directory.mkdirs()) return null
            val next = (list().mapNotNull { sequenceNumber(it) }.maxOrNull() ?: 0) + 1
            val file = java.io.File(directory, "failure-%06d.txt".format(next))
            file.writeText(Diagnostics.render(report))
            prune()
            file
        } catch (_: Exception) {
            null
        }
    }

    fun list(): List<java.io.File> =
        (directory.listFiles { f -> f.isFile && f.name.startsWith("failure-") && f.name.endsWith(".txt") } ?: emptyArray())
            .sortedBy { it.name }

    private fun sequenceNumber(file: java.io.File): Int? =
        file.name.removePrefix("failure-").removeSuffix(".txt").toIntOrNull()

    private fun prune() {
        val files = list()
        for (f in files.take((files.size - keep).coerceAtLeast(0))) f.delete()
    }
}
