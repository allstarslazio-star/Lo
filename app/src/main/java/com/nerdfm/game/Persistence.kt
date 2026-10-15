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
    data class ReadError(val reason: String) : LoadResult()
}

/** File-based by design so persistence can be exercised by local JVM unit tests. */
object Persistence {
    const val CURRENT_SAVE_VERSION: Int = 1
    private const val SAVE_NAME = "career1.dat"
    private const val TEMP_NAME = "career1.tmp"
    private const val BACKUP_NAME = "career1.bak"
    private const val FAILED_BACKUP_NAME = "career1.failed-load.bak"
    private val writtenSavePaths = HashSet<String>()

    /** Duration of the most recent complete save operation, including verification and promotion. */
    @Volatile
    var lastSaveNanos: Long = 0L
        private set

    /** Duration of the most recent temporary-save read-back verification. */
    @Volatile
    var lastReadBackVerificationNanos: Long = 0L
        private set

    fun exists(directory: File): Boolean = saveFile(directory).isFile

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
            writtenSavePaths.add(current.absolutePath)
            true
        } catch (_: Exception) {
            false
        } finally {
            if (temp.exists()) temp.delete()
            lastSaveNanos = System.nanoTime() - saveStart
        }
    }

    @Synchronized
    fun load(directory: File): LoadResult {
        val file = saveFile(directory)
        if (!file.exists()) return LoadResult.NoSave

        val result = readFile(file)
        if (result is LoadResult.Incompatible || result is LoadResult.Corrupt) {
            writtenSavePaths.remove(file.absolutePath)
            val backupName = moveFailedLoadSave(directory, file)
            return when (result) {
                is LoadResult.Incompatible -> result.copy(backupFileName = backupName ?: result.backupFileName)
                is LoadResult.Corrupt -> result.copy(backupFileName = backupName ?: result.backupFileName)
                else -> result
            }
        }
        return result
    }

    /** Retiring/deleting a career archives it instead of destroying it. */
    @Synchronized
    fun delete(directory: File): Boolean {
        val file = saveFile(directory)
        if (!file.exists()) return true
        // Retiring a career is an archive operation, not a read/deserialize operation.
        val archived = moveToLastGoodBackup(directory, file)
        if (archived) writtenSavePaths.remove(file.absolutePath)
        return archived
    }

    fun hasBackup(directory: File): Boolean = File(directory, BACKUP_NAME).isFile

    @Synchronized
    fun restoreBackup(directory: File): Boolean {
        val current = saveFile(directory)
        val backup = File(directory, BACKUP_NAME)
        if (current.exists() || !backup.isFile) return false
        return backup.renameTo(current)
    }

    private fun readFile(file: File): LoadResult {
        return try {
            val world = ObjectInputStream(GZIPInputStream(FileInputStream(file))).use { input ->
                val value = input.readObject()
                value as? World ?: return LoadResult.Corrupt(
                    "The save contains ${value?.javaClass?.simpleName ?: "no World object"}, not a World."
                )
            }
            if (world.saveVersion != CURRENT_SAVE_VERSION) {
                return LoadResult.Incompatible(
                    "Save format ${world.saveVersion} is not supported; this game requires format $CURRENT_SAVE_VERSION."
                )
            }
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
            LoadResult.ReadError("The save could not be read: ${e.message ?: e.javaClass.simpleName}.")
        }
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
    }

    private fun archiveBeforeOverwrite(directory: File, current: File): Boolean {
        // A save this process just wrote was already read-back verified; don't deserialize it again.
        if (current.absolutePath in writtenSavePaths) {
            val archived = moveGoodSaveToLastGoodBackup(directory, current)
            if (archived) writtenSavePaths.remove(current.absolutePath)
            return archived
        }
        val existingResult = readFile(current)
        return if (existingResult is LoadResult.Loaded) {
            moveGoodSaveToLastGoodBackup(directory, current)
        } else {
            moveFailedLoadSave(directory, current) != null
        }
    }

    /** Move the current known-good save to career1.bak without risking the only copy. */
    private fun moveGoodSaveToLastGoodBackup(directory: File, current: File): Boolean =
        moveToLastGoodBackup(directory, current)

    /** Promote a save to the backup using rename only; never deserialize it during archival. */
    private fun moveToLastGoodBackup(directory: File, current: File): Boolean {
        val pending = uniqueFile(directory, "career1.backup-pending", ".tmp")
        if (!current.renameTo(pending)) return false
        val backup = File(directory, BACKUP_NAME)
        if (backup.exists() && !backup.delete()) {
            pending.renameTo(current)
            return false
        }
        if (pending.renameTo(backup)) return true
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
