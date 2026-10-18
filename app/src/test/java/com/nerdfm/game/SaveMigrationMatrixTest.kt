package com.nerdfm.game

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The M8 save-migration matrix, one test per row of the plan. */
class SaveMigrationMatrixTest {
    @get:Rule val temp = TemporaryFolder()

    private fun gzipObject(file: File, value: Any) {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(GZIPOutputStream(bytes)).use { it.writeObject(value) }
        file.writeBytes(bytes.toByteArray())
    }

    private fun smallWorld(year: Int): World = World().apply { this.year = year; manager = "Matrix" }

    // 1. current-version save
    @Test fun currentVersionSaveLoadsAndKeepsItsState() {
        val dir = temp.newFolder()
        val w = smallWorld(2041).apply { week = 9 }
        assertTrue(Persistence.save(dir, w))
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        assertEquals(2041, loaded.year)
        assertEquals(9, loaded.week)
        assertEquals(Persistence.CURRENT_SAVE_VERSION, loaded.saveVersion)
    }

    // 2. valid older-version saves, including the real checked-in format-3 file
    @Test fun everyOlderFormatMigratesToCurrent() {
        for (v in 1 until Persistence.CURRENT_SAVE_VERSION) {
            val dir = temp.newFolder()
            gzipObject(File(dir, "career1.dat"), smallWorld(2030 + v).apply { saveVersion = v })
            val result = Persistence.load(dir)
            assertTrue("format $v: $result", result is LoadResult.Loaded)
            assertEquals(Persistence.CURRENT_SAVE_VERSION, (result as LoadResult.Loaded).world.saveVersion)
            assertEquals(2030 + v, result.world.year)
            assertTrue("a successful migration must leave the file in place", File(dir, "career1.dat").isFile)
        }
    }

    @Test fun formatOneMigrationResetsRelationshipFieldsExactlyAsBefore() {
        val dir = temp.newFolder()
        val w = smallWorld(2030).apply { saveVersion = 1; managerReputation = 91f }
        val club = Club(0, "Old FC", 0, 50, 9000, 0L, 0L).apply { teamCohesion = 1f; boardObjective = 9 }
        w.clubs.add(club)
        val p = Player(1, "Old Timer", 0, 30, Pos.CM, null, IntArray(NATTR) { 10 }, 60, 10, 10, 10, 10, 10)
        p.playingTimePromise = 3; p.promiseWeeksRemaining = 5; p.promiseBrokenWeeks = 2
        w.players[1] = p
        gzipObject(File(dir, "career1.dat"), w)
        val loaded = (Persistence.load(dir) as LoadResult.Loaded).world
        assertEquals(50f, loaded.managerReputation, 0.001f)
        assertEquals(55f, loaded.clubs[0].teamCohesion, 0.001f)
        assertEquals(4, loaded.clubs[0].boardObjective)
        assertEquals(0, loaded.players.getValue(1).playingTimePromise)
        assertEquals(0, loaded.players.getValue(1).promiseWeeksRemaining)
    }

    @Test fun theCheckedInFormatThreeFileStillLoads() {
        val dir = temp.newFolder()
        val bytes = javaClass.classLoader!!.getResourceAsStream("saves/career_v3_pre_ecosystem.dat")!!.use { it.readBytes() }
        File(dir, "career1.dat").writeBytes(bytes)
        val status = SaveInspector.inspectFile(File(dir, "career1.dat"))
        assertTrue(status.label(), status is SaveFileStatus.Healthy)
        assertTrue(Persistence.load(dir) is LoadResult.Loaded)
    }

    // 3. missing primary save with an available backup
    @Test fun missingPrimaryWithBackupIsRecoveredWithoutConsumingTheBackup() {
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, smallWorld(2050)))
        assertTrue(Persistence.save(dir, smallWorld(2051)))
        assertTrue(File(dir, "career1.dat").delete())
        val recovery = SaveRecovery.loadWithRecovery(dir)
        assertTrue(recovery.restoredFromBackup)
        assertEquals(2050, (recovery.result as LoadResult.Loaded).world.year)
        assertTrue(File(dir, "career1.bak").isFile)
        assertTrue(File(dir, "career1.dat").isFile)
    }

    @Test fun missingPrimaryWithoutBackupIsJustNoSave() {
        val recovery = SaveRecovery.loadWithRecovery(temp.newFolder())
        assertTrue(recovery.result is LoadResult.NoSave)
        assertFalse(recovery.restoredFromBackup)
    }

    // 4. truncated save
    @Test fun aTruncatedSaveIsCorruptPreservedAndRecoverableFromBackup() {
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, smallWorld(2060)))
        assertTrue(Persistence.save(dir, smallWorld(2061)))
        val main = File(dir, "career1.dat")
        val whole = main.readBytes()
        main.writeBytes(whole.copyOf(whole.size / 2))
        val status = SaveInspector.inspectFile(main)
        assertTrue(status.label(), status is SaveFileStatus.Truncated || status is SaveFileStatus.Damaged)
        val recovery = SaveRecovery.loadWithRecovery(dir)
        assertTrue(recovery.restoredFromBackup)
        assertEquals(2060, (recovery.result as LoadResult.Loaded).world.year)
        val kept = dir.listFiles()!!.filter { it.name.startsWith("career1.failed-load") }
        assertEquals("the damaged file must be preserved, not deleted", 1, kept.size)
        assertEquals(whole.size / 2, kept[0].length().toInt())
    }

    @Test fun aTruncatedSaveWithNoBackupIsReportedAndKept() {
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, smallWorld(2062)))
        val main = File(dir, "career1.dat")
        main.writeBytes(main.readBytes().copyOf(40))
        val recovery = SaveRecovery.loadWithRecovery(dir)
        assertTrue(recovery.result is LoadResult.Corrupt)
        assertNotNull(recovery.message)
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("career1.failed-load") })
    }

    // 5. unsupported version
    @Test fun anUnsupportedVersionIsRejectedWithoutDestroyingTheFile() {
        val dir = temp.newFolder()
        gzipObject(File(dir, "career1.dat"), smallWorld(2070).apply { saveVersion = Persistence.CURRENT_SAVE_VERSION + 5 })
        val result = Persistence.load(dir)
        assertTrue(result is LoadResult.Incompatible)
        val kept = (result as LoadResult.Incompatible).backupFileName
        assertNotNull(kept)
        assertTrue("the unsupported save must be preserved under its backup name", File(dir, kept!!).isFile)
    }

    @Test fun anIncompatibleSaveIsNeverAutoReplacedByAnOlderBackup() {
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, smallWorld(2071)))
        assertTrue(Persistence.save(dir, smallWorld(2072)))
        gzipObject(File(dir, "career1.dat"), smallWorld(2099).apply { saveVersion = 99 })
        val recovery = SaveRecovery.loadWithRecovery(dir)
        assertTrue(recovery.result is LoadResult.Incompatible)
        assertFalse(recovery.restoredFromBackup)
    }

    // 6. failed migration
    @Test fun aFailingMigrationStepLeavesTheOriginalFileUntouchedAndUnreadable() {
        val dir = temp.newFolder()
        val file = File(dir, "career1.dat")
        gzipObject(file, smallWorld(2080).apply { saveVersion = 3 })
        val before = file.readBytes()
        val broken = listOf(SaveMigrations.Step(3, 4, "deliberately broken") { throw IllegalStateException("boom") })
        val result = SaveMigrations.withSteps(broken) { Persistence.load(dir) }
        assertTrue(result.toString(), result is LoadResult.Unreadable)
        assertTrue((result as LoadResult.Unreadable).reason.contains("boom"))
        assertTrue(file.isFile)
        assertTrue("the bytes must not change", before.contentEquals(file.readBytes()))
        assertFalse(File(dir, "career1.failed-load.bak").exists())
        // with the real steps restored the very same file loads
        assertTrue(Persistence.load(dir) is LoadResult.Loaded)
    }

    @Test fun aBrokenMigrationChainIsAnErrorNotASilentJump() {
        val w = smallWorld(2081).apply { saveVersion = 1 }
        try {
            SaveMigrations.withSteps(listOf(SaveMigrations.Step(2, 3, "gap") { })) { SaveMigrations.migrate(w) }
            org.junit.Assert.fail("expected MigrationFailedException")
        } catch (e: MigrationFailedException) {
            assertEquals(1, e.fromVersion)
        }
        assertEquals("a failed migration must not claim the new version", 1, w.saveVersion)
        assertTrue(SaveMigrations.pathFrom(1)!!.size == 3)
        assertTrue(SaveMigrations.withSteps(emptyList()) { SaveMigrations.pathFrom(1) } == null)
    }

    // 7. failed write followed by loading the previous save
    @Test fun aWriteThatCannotStartLeavesThePreviousSaveLoadable() {
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, smallWorld(2090)))
        val blocker = File(dir, "career1.tmp")
        assertTrue(blocker.mkdir())
        File(blocker, "keep").writeText("x") // a non-empty directory cannot be deleted, so the temp file cannot be created
        assertFalse(Persistence.save(dir, smallWorld(2091)))
        assertEquals(2090, (Persistence.load(dir) as LoadResult.Loaded).world.year)
    }

    @Test fun anInterruptedSnapshotWriteIsRejectedByReadBackVerification() {
        val dir = temp.newFolder()
        assertTrue(Persistence.save(dir, smallWorld(2092)))
        val snapshot = Persistence.snapshot(smallWorld(2093))
        assertFalse(Persistence.saveSnapshot(dir, snapshot.copyOf(snapshot.size / 3)))
        assertEquals(2092, (Persistence.load(dir) as LoadResult.Loaded).world.year)
        assertFalse("no stray temp file may remain", File(dir, "career1.tmp").exists())
        assertTrue(Persistence.saveSnapshot(dir, snapshot))
        assertEquals(2093, (Persistence.load(dir) as LoadResult.Loaded).world.year)
    }

    @Test fun anUnexpectedReadFailureIsNotTreatedAsCorruption() {
        val dir = temp.newFolder()
        assertTrue(File(dir, "career1.dat").mkdir())
        val recovery = SaveRecovery.loadWithRecovery(dir)
        assertTrue(recovery.result is LoadResult.Unreadable)
        assertTrue(File(dir, "career1.dat").isDirectory)
    }
}
