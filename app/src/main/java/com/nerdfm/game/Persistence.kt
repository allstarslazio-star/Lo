package com.nerdfm.game

import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.StreamCorruptedException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException

/** Explicit outcomes keep a missing save distinct from a save that must not be overwritten. */
sealed class LoadResult {
    data class Loaded(val world: World) : LoadResult()
    object NoSave : LoadResult()
    data class Incompatible(val reason: String, val backupFileName: String? = null) : LoadResult()
    data class Corrupt(val reason: String, val backupFileName: String? = null) : LoadResult()
    /** A transient/unclassified I/O or runtime failure; the original save is preserved. */
    data class Unreadable(val reason: String) : LoadResult()
}

/** File-based by design so persistence can be exercised by local JVM unit tests. */
object Persistence {
    const val CURRENT_SAVE_VERSION: Int = 10
    private const val SAVE_NAME = "career1.dat"
    private const val TEMP_NAME = "career1.tmp"
    private const val BACKUP_NAME = "career1.bak"
    private const val FAILED_BACKUP_NAME = "career1.failed-load.bak"

    /** Duration of the most recent temporary-save read-back verification. */
    @Volatile
    var lastReadBackVerificationNanos: Long = 0L
        private set

    /** End-to-end save duration, including serialization, verification, archive, and rename. */
    @Volatile
    var lastSaveDurationNanos: Long = 0L
        private set

    fun exists(directory: File): Boolean = saveFile(directory).isFile

    fun backupExists(directory: File): Boolean = File(directory, BACKUP_NAME).isFile

    /** Explicitly restores the last-good backup only after validating it; the backup is kept. */
    @Synchronized
    fun restoreBackup(directory: File): LoadResult {
        val backup = File(directory, BACKUP_NAME)
        if (!backup.isFile) return LoadResult.NoSave
        if (saveFile(directory).exists()) return LoadResult.Unreadable("A current career save already exists. It was left untouched; restore is only offered when the main save is missing.")
        val validation = readFile(backup)
        if (validation !is LoadResult.Loaded) return validation
        return try {
            val target = saveFile(directory)
            val pending = File(directory, "career1.restore.tmp")
            if (pending.exists() && !pending.delete()) return LoadResult.Unreadable("Could not clear a previous restore attempt.")
            backup.copyTo(pending, overwrite = false)
            val copied = readFile(pending)
            if (copied !is LoadResult.Loaded) {
                pending.delete()
                return if (copied is LoadResult.Corrupt || copied is LoadResult.Incompatible) copied
                else LoadResult.Unreadable("The backup copy could not be verified; the original backup was preserved.")
            }
            if (target.exists()) {
                pending.delete()
                return LoadResult.Unreadable("A current save appeared during restore; it was left untouched and the backup was preserved.")
            }
            if (!pending.renameTo(target)) {
                pending.delete()
                return LoadResult.Unreadable("Could not restore the backup file; the backup was preserved.")
            }
            copied
        } catch (e: Exception) {
            LoadResult.Unreadable("Backup restore failed without deleting the backup: ${e.message ?: e.javaClass.simpleName}.")
        }
    }

    /**
     * Writes and verifies a temporary save before moving the current save out of the way.
     * A readable current save becomes career1.bak; an unreadable/incompatible one is moved
     * to a uniquely named failed-load backup, leaving the last-good backup untouched.
     */
    @Synchronized
    fun save(directory: File, world: World): Boolean {
        val saveStart = System.nanoTime()
        val temp = File(directory, TEMP_NAME)
        return try {
            if (!directory.exists() && !directory.mkdirs()) return false
            world.saveVersion = CURRENT_SAVE_VERSION
            if (temp.exists() && !temp.delete()) return false

            ObjectOutputStream(GZIPOutputStream(FileOutputStream(temp))).use { it.writeObject(world) }

            val verificationStart = System.nanoTime()
            val verification = try {
                readFile(temp)
            } finally {
                lastReadBackVerificationNanos = System.nanoTime() - verificationStart
            }
            val verifiedWorld = (verification as? LoadResult.Loaded)?.world ?: return false
            if (verifiedWorld.saveVersion != CURRENT_SAVE_VERSION) return false

            val current = saveFile(directory)
            if (current.exists() && !archiveBeforeOverwrite(directory, current)) return false
            if (!temp.renameTo(current)) return false
            true
        } catch (_: Exception) {
            false
        } finally {
            lastSaveDurationNanos = System.nanoTime() - saveStart
            if (temp.exists()) temp.delete()
        }
    }

    @Synchronized
    fun load(directory: File): LoadResult {
        val file = saveFile(directory)
        if (!file.exists()) return LoadResult.NoSave

        val result = readFile(file)
        if (result is LoadResult.Incompatible || result is LoadResult.Corrupt) {
            val backupName = moveFailedLoadSave(directory, file)
            return when (result) {
                is LoadResult.Incompatible -> result.copy(backupFileName = backupName ?: result.backupFileName)
                is LoadResult.Corrupt -> result.copy(backupFileName = backupName ?: result.backupFileName)
                else -> result
            }
        }
        // Do not move or overwrite the save for an unexpected exception. It may be
        // recoverable on a later attempt or after a filesystem issue is resolved.
        return result
    }

    /** Retiring/deleting a career archives it instead of destroying it. */
    @Synchronized
    fun delete(directory: File): Boolean {
        // Retirement is an archive operation, not a validation/load operation. Never
        // deserialize a save merely to retire it; preserve the existing last-good backup.
        val file = saveFile(directory)
        if (!file.exists()) return true
        val backup = File(directory, BACKUP_NAME)
        val destination = if (!backup.exists()) backup else uniqueFile(directory, "career1.retired", ".bak")
        return file.renameTo(destination)
    }

    private fun readFile(file: File): LoadResult {
        return try {
            val world = ObjectInputStream(GZIPInputStream(FileInputStream(file))).use { input ->
                val value = input.readObject()
                value as? World ?: return LoadResult.Corrupt(
                    "The save contains ${value?.javaClass?.simpleName ?: "no World object"}, not a World."
                )
            }
            if (world.saveVersion !in 1..CURRENT_SAVE_VERSION) {
                return LoadResult.Incompatible(
                    "Save format ${world.saveVersion} is not supported; this game requires format $CURRENT_SAVE_VERSION or a supported older version."
                )
            }
            migrateSave(world)
            normalizeAfterLoad(world)
            LoadResult.Loaded(world)
        } catch (e: InvalidClassException) {
            LoadResult.Incompatible("A saved class is incompatible with this build: ${e.message ?: e.javaClass.simpleName}.")
        } catch (e: ClassNotFoundException) {
            LoadResult.Incompatible("A class required by this save is unavailable: ${e.message ?: e.javaClass.simpleName}.")
        } catch (e: EOFException) {
            LoadResult.Corrupt("The save ended unexpectedly (EOF).")
        } catch (e: ZipException) {
            LoadResult.Corrupt("The save's compressed data is invalid: ${e.message ?: "invalid GZIP data"}.")
        } catch (e: StreamCorruptedException) {
            LoadResult.Corrupt("The save's object stream is corrupted: ${e.message ?: "invalid object stream"}.")
        } catch (e: Exception) {
            LoadResult.Unreadable("The save could not be read safely: ${e.message ?: e.javaClass.simpleName}. The file was left untouched.")
        }
    }

    /** Migrates compatible Java-serialized saves in memory, one format step at a time. */
    private fun migrateSave(world: World) {
        if (world.saveVersion == 1) {
            // Format 2 adds relationship state (M3).
            world.managerReputation = 50f
            for (club in world.clubs) {
                club.teamCohesion = 55f
                club.boardObjective = club.boardObjective.coerceIn(0, 4)
            }
            for (player in world.players.values) {
                player.playingTimePromise = 0
                player.promiseWeeksRemaining = 0
                player.promiseBrokenWeeks = 0
            }
            world.saveVersion = 2
        }
        if (world.saveVersion == 2) {
            // Format 3 adds the business layer (sponsors, clauses, loans, scouts); it is created lazily.
            world.saveVersion = 3
        }
        if (world.saveVersion == 3) {
            // Format 4 adds the Club Ecosystem holder (World.ecosystem); it is created lazily by World.eco().
            world.saveVersion = 4
        }
        if (world.saveVersion == 4) {
            // Format 5 adds staff, board and club identity sections inside EcosystemState; each is created lazily.
            world.saveVersion = 5
        }
        if (world.saveVersion == 5) {
            // Format 6 adds the medical section (workloads, injury history, rehab policy); it is created lazily.
            world.saveVersion = 6
        }
        if (world.saveVersion == 6 || world.saveVersion == 7) {
            // Format 7 is reserved for the unfinished analytics milestone (M3). Format 8 adds the people section
            // (languages, culture, relationships, events); it is created lazily and absent in older saves.
            world.saveVersion = 8
        }
        if (world.saveVersion == 8) {
            // Format 9 adds fans, stadium, pitch, facilities and media (EnvState); created lazily.
            world.saveVersion = 9
        }
        if (world.saveVersion == 9) {
            // Format 10 adds the fixture calendar (cups, continental competition, international windows); created lazily.
            world.saveVersion = 10
        }
        world.saveVersion = CURRENT_SAVE_VERSION
    }

    /** Repairs only absent optional learning/AI state; existing state is never reset. */
    @Suppress("SENSELESS_COMPARISON")
    private fun normalizeAfterLoad(world: World) {
        if (world.matchCalibration == null) world.matchCalibration = MatchCalibration()
        for (player in world.players.values) {
            if (player.learning == null) player.learning = PlayerLearningProfile()
        }
        for (club in world.clubs) {
            if (club.managerGenome == null) club.managerGenome = ManagerGenome()
        }
        // A save written mid-match may carry applied ecosystem deltas; take them off before play resumes.
        Ecosystem.endMatch(world)
        MedicalRules.repair(world)
        People.repair(world)
        ClubEnvRules.repair(world)
        FixtureCalendar.repair(world)
    }

    private fun archiveBeforeOverwrite(directory: File, current: File): Boolean {
        val existingResult = readFile(current)
        return if (existingResult is LoadResult.Loaded) {
            moveGoodSaveToLastGoodBackup(directory, current)
        } else {
            moveFailedLoadSave(directory, current) != null
        }
    }

    /** Move the current known-good save to career1.bak without risking the only copy. */
    private fun moveGoodSaveToLastGoodBackup(directory: File, current: File): Boolean {
        val pending = uniqueFile(directory, "career1.backup-pending", ".tmp")
        if (!current.renameTo(pending)) return false
        val backup = File(directory, BACKUP_NAME)
        if (backup.exists() && !backup.delete()) {
            pending.renameTo(current)
            return false
        }
        if (pending.renameTo(backup)) return true
        // If promotion fails, put the save back when possible; pending remains a recovery copy otherwise.
        pending.renameTo(current)
        return false
    }

    /** Failed saves never replace career1.bak. The backup name is unique and the source is moved. */
    private fun moveFailedLoadSave(directory: File, source: File): String? {
        if (!source.exists()) return null
        val backup = uniqueFailedBackup(directory)
        if (source.renameTo(backup)) return backup.name
        // Cross-filesystem or platform rename failure: copy to a fresh backup before leaving source intact.
        return try {
            source.copyTo(backup, overwrite = false)
            backup.name
        } catch (_: Exception) {
            null
        }
    }

    private fun uniqueFailedBackup(directory: File): File {
        val base = File(directory, FAILED_BACKUP_NAME)
        if (!base.exists()) return base
        var suffix = 1
        while (true) {
            val candidate = File(directory, "career1.failed-load-$suffix.bak")
            if (!candidate.exists()) return candidate
            suffix++
        }
    }

    private fun uniqueFile(directory: File, prefix: String, suffix: String): File {
        var index = 0
        while (true) {
            val candidate = File(directory, if (index == 0) "$prefix$suffix" else "$prefix-$index$suffix")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun saveFile(directory: File) = File(directory, SAVE_NAME)
}
