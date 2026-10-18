package com.nerdfm.game

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistenceTest {
    @get:Rule val temp = TemporaryFolder()


    @Test fun missingMainSaveCanRestoreLastGoodBackupWithoutConsumingIt() {
        val directory = temp.newFolder("restore-last-good")
        val original = World().apply { year = 2044 }
        assertTrue(Persistence.save(directory, original))
        assertTrue(Persistence.save(directory, World().apply { year = 2045 }))
        assertTrue(File(directory, "career1.dat").delete())
        val backup = File(directory, "career1.bak")
        assertTrue(backup.isFile)

        val result = Persistence.restoreBackup(directory)

        assertTrue(result is LoadResult.Loaded)
        assertEquals(2044, (result as LoadResult.Loaded).world.year)
        assertTrue("restore must keep the backup", backup.isFile)
        assertEquals(2044, (Persistence.load(directory) as LoadResult.Loaded).world.year)
    }

    @Test fun unexpectedReadFailureLeavesTheOriginalSaveInPlace() {
        val directory = temp.newFolder("non-destructive-read-error")
        val savePath = File(directory, "career1.dat")
        assertTrue(savePath.mkdir()) // FileInputStream cannot read a directory; this is not proven corruption.

        val result = Persistence.load(directory)

        assertTrue(result is LoadResult.Unreadable)
        assertTrue("unexpected errors must not move the source", savePath.isDirectory)
        assertFalse(File(directory, "career1.failed-load.bak").exists())
    }

    @Test fun saveAndLoadRoundTripPreservesCareerState() {
        val directory = temp.newFolder("round-trip")
        val world = World().apply {
            year = 2031
            week = 12
            manager = "Persistence Tester"
            matchCalibration.chanceCreationMultiplier = 1.07f
        }

        assertTrue(Persistence.save(directory, world))
        val result = Persistence.load(directory)

        assertTrue(result is LoadResult.Loaded)
        val loaded = (result as LoadResult.Loaded).world
        assertEquals(2031, loaded.year)
        assertEquals(12, loaded.week)
        assertEquals("Persistence Tester", loaded.manager)
        assertEquals(Persistence.CURRENT_SAVE_VERSION, loaded.saveVersion)
        assertEquals(1.07f, loaded.matchCalibration.chanceCreationMultiplier, 0.0001f)
    }

    @Test fun missingSaveReturnsNoSave() {
        assertTrue(Persistence.load(temp.newFolder("missing")) is LoadResult.NoSave)
    }

    @Test fun versionOneSaveMigratesToCurrentRelationshipSchema() {
        val directory = temp.newFolder("version-one-migration")
        val oldWorld = World().apply {
            saveVersion = 1
            humanClubId = 0
        }
        writeCompressedObject(File(directory, "career1.dat"), oldWorld)

        val result = Persistence.load(directory)

        assertTrue("version 1 should migrate", result is LoadResult.Loaded)
        assertEquals(Persistence.CURRENT_SAVE_VERSION, (result as LoadResult.Loaded).world.saveVersion)
        assertEquals(50f, result.world.managerReputation, 0.001f)
        assertTrue(File(directory, "career1.dat").isFile)
    }

    @Test fun invalidClassDescriptorIsIncompatible() {
        val directory = temp.newFolder("invalid-class-descriptor")
        writeWorldWithWrongSerialUid(File(directory, "career1.dat"), World())

        val result = Persistence.load(directory)

        assertTrue(result is LoadResult.Incompatible)
        assertTrue((result as LoadResult.Incompatible).reason.contains("incompatible", ignoreCase = true))
        assertNotNull(result.backupFileName)
    }

    @Test fun wrongRootObjectIsCorruptAndPreservedSeparately() {
        val directory = temp.newFolder("wrong-class")
        writeCompressedObject(File(directory, "career1.dat"), "not a World")

        val result = Persistence.load(directory)

        assertTrue(result is LoadResult.Corrupt)
        val backupName = (result as LoadResult.Corrupt).backupFileName
        assertNotNull(backupName)
        assertTrue(File(directory, backupName!!).isFile)
        assertFalse(File(directory, "career1.dat").exists())
    }

    @Test fun eofZipAndStreamCorruptionAreClassifiedAsCorrupt() {
        val directory = temp.newFolder("corruption")
        val file = File(directory, "career1.dat")

        // Valid GZIP container with no serialized object: ObjectInputStream reports EOFException.
        GZIPOutputStream(FileOutputStream(file)).use { }
        assertTrue(Persistence.load(directory) is LoadResult.Corrupt)

        // A non-GZIP payload is rejected by GZIPInputStream with ZipException.
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertTrue(Persistence.load(directory) is LoadResult.Corrupt)

        // Valid GZIP containing bytes that are not an ObjectOutputStream header.
        GZIPOutputStream(FileOutputStream(file)).use { it.write("not-an-object-stream".toByteArray()) }
        assertTrue(Persistence.load(directory) is LoadResult.Corrupt)
    }

    @Test fun overwritingOrStartingANewCareerMovesThePreviousSaveToLastGoodBackup() {
        val directory = temp.newFolder("overwrite")
        val first = World().apply { year = 2027 }
        val second = World().apply { year = 2035 }

        assertTrue(Persistence.save(directory, first))
        assertTrue(Persistence.save(directory, second))

        val backup = File(directory, "career1.bak")
        assertTrue(backup.isFile)
        val backedUpWorld = readCompressedWorld(backup)
        assertEquals(2027, backedUpWorld.year)
        val current = Persistence.load(directory) as LoadResult.Loaded
        assertEquals(2035, current.world.year)
    }

    @Test fun deleteArchivesInsteadOfDestroyingCareer() {
        val directory = temp.newFolder("delete")
        assertTrue(Persistence.save(directory, World().apply { year = 2040 }))

        assertTrue(Persistence.delete(directory))

        assertFalse(Persistence.exists(directory))
        assertEquals(2040, readCompressedWorld(File(directory, "career1.bak")).year)
    }

    @Test fun failedLoadDoesNotReplaceLastGoodBackup() {
        val directory = temp.newFolder("failed-does-not-replace-good")
        assertTrue(Persistence.save(directory, World().apply { year = 2041 }))
        assertTrue(Persistence.save(directory, World().apply { year = 2042 }))
        val lastGoodBackup = File(directory, "career1.bak")
        val originalBackupBytes = lastGoodBackup.readBytes()

        writeCompressedObject(File(directory, "career1.dat"), "wrong class")
        val result = Persistence.load(directory)

        assertTrue(result is LoadResult.Corrupt)
        assertTrue(lastGoodBackup.isFile)
        assertTrue(originalBackupBytes.contentEquals(lastGoodBackup.readBytes()))
        assertEquals(2041, readCompressedWorld(lastGoodBackup).year)
    }

    @Test fun postLoadNormalizationRepairsOnlyMissingLearningAndAiState() {
        val directory = temp.newFolder("normalization")
        val world = World()
        val player = Player(
            id = 1, name = "Test Player", nation = 0, age = 22, pos = Pos.CM, pos2 = null,
            attrs = IntArray(NATTR), pa = 60, consistency = 50, injuryProne = 20,
            prof = 50, ambition = 50, loyalty = 50
        )
        val secondPlayer = Player(
            id = 2, name = "Learning Player", nation = 0, age = 23, pos = Pos.ST, pos2 = null,
            attrs = IntArray(NATTR), pa = 62, consistency = 55, injuryProne = 18,
            prof = 52, ambition = 48, loyalty = 51
        )
        val club = Club(0, "Test Club", 0, 50, 1000, 0L, 1L)
        val secondClub = Club(1, "Genome Club", 0, 51, 1100, 2L, 3L)
        secondPlayer.learning.update(3, PlayerAction.SHOOT, 0.8f, 4)
        secondClub.managerGenome.pressing = 1.7f
        world.players[player.id] = player
        world.players[secondPlayer.id] = secondPlayer
        world.clubs.add(club)
        world.clubs.add(secondClub)
        player.javaClass.getDeclaredField("learning").apply { isAccessible = true }.set(player, null)
        club.javaClass.getDeclaredField("managerGenome").apply { isAccessible = true }.set(club, null)
        world.javaClass.getDeclaredField("matchCalibration").apply { isAccessible = true }.set(world, null)
        writeCompressedObject(File(directory, "career1.dat"), world)

        val loaded = (Persistence.load(directory) as LoadResult.Loaded).world

        assertNotNull(loaded.players[1]!!.learning)
        assertNotNull(loaded.clubs[0].managerGenome)
        assertNotNull(loaded.matchCalibration)
        assertEquals(1L, loaded.players[2]!!.learning.decisionsObserved)
        assertEquals(1.7f, loaded.clubs[1].managerGenome.pressing, 0.0001f)
    }

    @Test fun serializableClassesExposeExplicitStableSerialVersionUid() {
        val classes = listOf(
            World::class.java, Formation::class.java, Tactics::class.java, Player::class.java,
            Club::class.java, TableRow::class.java, Fixture::class.java, League::class.java,
            News::class.java, Offer::class.java, Coach::class.java, Shortlisted::class.java,
            PlayerLearningProfile::class.java, ManagerGenome::class.java, MatchCalibration::class.java
        )
        classes.forEach { type ->
            assertEquals("${type.simpleName} serialVersionUID", 1L, ObjectStreamClass.lookup(type).serialVersionUID)
        }
    }

    @Test fun readBackVerificationCostIsMeasuredForARepresentativeGeneratedCareer() {
        val directory = temp.newFolder("verification-cost")
        val world = WorldGen.generate(987654L, "Save Cost Test", 0)
        val samples = ArrayList<Long>()
        repeat(3) {
            assertTrue(Persistence.save(directory, world))
            samples.add(Persistence.lastReadBackVerificationNanos)
        }
        assertTrue("verification timer should be populated", samples.all { it > 0L })
        assertTrue("full save timer should be populated", Persistence.lastSaveDurationNanos > 0L)
        val medianNanos = samples.sorted()[samples.size / 2]
        println("Persistence read-back verification median (3 generated-career saves): ${"%.3f".format(medianNanos / 1_000_000.0)} ms; last full save: ${"%.3f".format(Persistence.lastSaveDurationNanos / 1_000_000.0)} ms; samples=${samples.map { "%.3f".format(it / 1_000_000.0) }} ms")
    }


    private fun writeWorldWithWrongSerialUid(file: File, world: World) {
        val raw = ByteArrayOutputStream().also { bytes ->
            ObjectOutputStream(bytes).use { it.writeObject(world) }
        }.toByteArray()
        val className = "com.nerdfm.game.World".toByteArray()
        val classNameOffset = raw.indexOfSequence(className)
        check(classNameOffset >= 0) { "World class descriptor was not found" }
        val uidOffset = classNameOffset + className.size
        val wrongUid = 99L
        for (index in 0 until Long.SIZE_BYTES) {
            raw[uidOffset + index] = (wrongUid ushr ((Long.SIZE_BYTES - index - 1) * 8)).toByte()
        }
        GZIPOutputStream(FileOutputStream(file)).use { it.write(raw) }
    }

    private fun ByteArray.indexOfSequence(sequence: ByteArray): Int {
        for (start in 0..(size - sequence.size)) {
            if (sequence.indices.all { this[start + it] == sequence[it] }) return start
        }
        return -1
    }

    private fun writeCompressedObject(file: File, value: Any) {
        ObjectOutputStream(GZIPOutputStream(FileOutputStream(file))).use { it.writeObject(value) }
    }

    private fun readCompressedWorld(file: File): World =
        java.io.ObjectInputStream(java.util.zip.GZIPInputStream(file.inputStream())).use { it.readObject() as World }
}
