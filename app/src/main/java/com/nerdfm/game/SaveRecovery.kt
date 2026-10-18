package com.nerdfm.game

import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Read-only classification of one save-related file; computing it never moves or deletes anything. */
sealed class SaveFileStatus {
    object Missing : SaveFileStatus()
    data class Healthy(val version: Int, val sizeBytes: Long, val season: String, val week: Int) : SaveFileStatus()
    data class Truncated(val sizeBytes: Long) : SaveFileStatus()
    data class Damaged(val reason: String, val sizeBytes: Long) : SaveFileStatus()
    data class Unsupported(val reason: String, val sizeBytes: Long) : SaveFileStatus()
    data class Unreadable(val reason: String) : SaveFileStatus()

    fun label(): String = when (this) {
        Missing -> "missing"
        is Healthy -> "healthy (format $version, season $season, week $week, $sizeBytes bytes)"
        is Truncated -> "truncated ($sizeBytes bytes)"
        is Damaged -> "damaged: $reason"
        is Unsupported -> "unsupported: $reason"
        is Unreadable -> "unreadable: $reason"
    }
}

/** Diagnoses the files in a save directory without modifying them. */
object SaveInspector {
    class Entry(val name: String, val status: SaveFileStatus)

    fun inspectFile(file: File): SaveFileStatus {
        if (!file.isFile) return SaveFileStatus.Missing
        val size = file.length()
        return when (val result = Persistence.readFile(file)) {
            is LoadResult.Loaded -> SaveFileStatus.Healthy(result.world.saveVersion, size, result.world.seasonLabel(), result.world.week)
            is LoadResult.Corrupt ->
                if (result.reason.contains("EOF") || result.reason.contains("ended unexpectedly")) SaveFileStatus.Truncated(size)
                else SaveFileStatus.Damaged(result.reason, size)
            is LoadResult.Incompatible -> SaveFileStatus.Unsupported(result.reason, size)
            is LoadResult.Unreadable -> SaveFileStatus.Unreadable(result.reason)
            LoadResult.NoSave -> SaveFileStatus.Missing
        }
    }

    /** Every career file in [directory]: the main save, the last-good backup and any preserved failed/retired copies. */
    fun scan(directory: File): List<Entry> {
        val names = ArrayList<String>()
        names += Persistence.SAVE_NAME
        names += Persistence.BACKUP_NAME
        val extra = directory.listFiles { f -> f.isFile && f.name.startsWith("career1.") && f.name.endsWith(".bak") }
        if (extra != null) {
            for (f in extra.sortedBy { it.name }) if (f.name !in names) names += f.name
        }
        return names.map { Entry(it, inspectFile(File(directory, it))) }
    }

    /** A plain-text listing that is safe to include in a bug report (file names and statuses only). */
    fun describe(directory: File): String =
        scan(directory).joinToString("\n") { "${it.name}: ${it.status.label()}" }
}

/** Result of [SaveRecovery.loadWithRecovery]; [message] is user-presentable when non-null. */
class Recovery(val result: LoadResult, val restoredFromBackup: Boolean, val message: String?)

object SaveRecovery {
    /**
     * Loads the career and, when the main save is missing or proven corrupt, restores the last verified backup.
     * Incompatible saves are never auto-replaced (the user may simply need a newer build), and an unexpected read
     * failure never triggers a restore: the original may be fine on a later attempt.
     */
    fun loadWithRecovery(directory: File, restoreWhenMissing: Boolean = true): Recovery {
        val first = Persistence.load(directory)
        return when (first) {
            is LoadResult.Loaded -> Recovery(first, false, null)
            LoadResult.NoSave ->
                if (restoreWhenMissing && Persistence.backupExists(directory)) {
                    restore(directory, first, "The main save was missing, so the last verified backup was restored.")
                } else Recovery(first, false, null)
            is LoadResult.Corrupt ->
                if (Persistence.backupExists(directory)) {
                    restore(directory, first, "The latest save was damaged. It was kept aside and the last verified backup was restored.")
                } else Recovery(first, false, "The save is damaged and no backup exists. The damaged file was preserved.")
            is LoadResult.Incompatible -> Recovery(first, false, first.reason)
            is LoadResult.Unreadable -> Recovery(first, false, first.reason)
        }
    }

    private fun restore(directory: File, original: LoadResult, success: String): Recovery {
        val restored = Persistence.restoreBackup(directory)
        return if (restored is LoadResult.Loaded) Recovery(restored, true, success)
        else Recovery(original, false, "Automatic restore did not complete: " + describeFailure(restored))
    }

    private fun describeFailure(result: LoadResult): String = when (result) {
        is LoadResult.Loaded -> "restored"
        LoadResult.NoSave -> "no backup found"
        is LoadResult.Incompatible -> result.reason
        is LoadResult.Corrupt -> result.reason
        is LoadResult.Unreadable -> result.reason
    }
}

/**
 * Serialises world access between the weekly simulation, UI-triggered edits that run off the main thread, and saves.
 * A save takes the lock only to audit and serialise; disk I/O happens afterwards from the immutable byte snapshot.
 */
object SaveCoordinator {
    private val lock = ReentrantLock()

    fun <T> withWorld(block: () -> T): T = lock.withLock(block)

    class Outcome(
        val ok: Boolean,
        val snapshotBytes: Int,
        val serializeMillis: Long,
        val writeMillis: Long,
        val audit: WorldIntegrity.Report?
    )

    /**
     * Audits (never blocking the save), snapshots under the world lock, then writes with the verified-temp protocol.
     * A world that fails the audit is still saved: refusing to save would lose progress to a possible false alarm,
     * and the previous good save is archived as the backup either way.
     */
    fun save(directory: File, world: World, audit: Boolean = true): Outcome {
        val begin = System.nanoTime()
        var report: WorldIntegrity.Report? = null
        val bytes = withWorld {
            if (audit) report = try { WorldIntegrity.audit(world) } catch (_: Exception) { null }
            Persistence.snapshot(world)
        }
        val serialized = System.nanoTime()
        val ok = Persistence.saveSnapshot(directory, bytes)
        val end = System.nanoTime()
        PerfMonitor.record(PerfMonitor.SAVE, (end - begin) / 1_000_000L)
        if (!ok) Diagnostics.note("save", "write or verification failed")
        else if (report?.ok == false) Diagnostics.note("save", report?.summary(3) ?: "integrity errors")
        return Outcome(ok, bytes.size, (serialized - begin) / 1_000_000L, (end - serialized) / 1_000_000L, report)
    }
}
