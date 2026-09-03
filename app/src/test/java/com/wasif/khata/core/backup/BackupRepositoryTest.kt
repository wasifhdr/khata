package com.wasif.khata.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.time.KhataClock
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A file-backed database rather than an in-memory one: the checkpoint and the file copy
 * are the things under test, and neither exists in memory.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: KhataDatabase
    private lateinit var repository: BackupRepository

    private var nowMillis = 1_000L
    private val clock = object : KhataClock { override fun now(): Long = nowMillis }

    private val saltForKey = ByteArray(16)
    private val key = BackupFile.deriveKey("a passphrase", saltForKey)

    @Before
    fun setUp() {
        deleteAll()
        db = Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db")
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(context, db, clock)
    }

    @After
    fun tearDown() {
        db.close()
        deleteAll()
    }

    private fun deleteAll() {
        context.getDatabasePath("khata.db").let { f ->
            f.delete()
            File(f.path + "-wal").delete()
            File(f.path + "-shm").delete()
        }
        File(context.filesDir, "backups").deleteRecursively()
    }

    private fun account(uuid: String) = AccountEntity(
        uuid = uuid,
        name = uuid,
        type = AccountType.CASH,
        openingBalanceMinor = 0,
        currentBalanceMinor = 0,
        reportedBalanceMinor = null,
        reportedBalanceAt = null,
        includeInNetWorth = true,
        smsIdentifiers = "",
        createdAt = 1000,
        updatedAt = 1000,
    )

    @Test
    fun `a row written moments before the backup is in it`() = runTest {
        // Room runs in WAL mode: without a checkpoint this row is still in khata.db-wal
        // and the backup silently omits it. Nothing notices until a restore, by which
        // time the row is gone.
        db.accountDao().upsert(account("acc-late"))

        val file = repository.backUp(key, saltForKey)!!
        val restored = BackupFile.read(file.readBytes(), key, SCHEMA_VERSION)

        // SQLite stores text inline, so the uuid is findable in the raw page bytes.
        // Crude, and exactly right for this: it fails if and only if the checkpoint
        // did not run.
        val bytes = (restored as BackupResult.Restored).plain
        assertTrue(bytes.toString(Charsets.ISO_8859_1).contains("acc-late"))
    }

    @Test
    fun `seven are kept and the older ones pruned`() = runTest {
        repeat(9) {
            nowMillis += 1_000
            repository.backUp(key, saltForKey)
        }

        assertEquals(7, repository.backupDir().listFiles()!!.size)
    }

    @Test
    fun `a database backed up and restored still holds its rows`() = runTest {
        db.accountDao().upsert(account("acc-survivor"))
        val file = repository.backUp(key, saltForKey)!!

        // Everything gone, as on a replacement phone.
        db.clearAllTables()
        assertTrue(db.accountDao().getAll().none { it.uuid == "acc-survivor" })

        val result = repository.restore(file.readBytes(), key)

        assertTrue(result is BackupResult.Restored)

        // Opened under a fresh name rather than khata.db: Robolectric caches SQLite
        // connections per path, so reopening the same one would answer from the old
        // connection rather than from the bytes just written. Copying to a new path
        // and opening that proves the same thing -- these bytes are a database
        // containing the row -- without the artifact.
        val restoredFile = context.getDatabasePath("khata.db")
        val fresh = File(restoredFile.parentFile, "restored-check.db")
        restoredFile.copyTo(fresh, overwrite = true)

        val reopened = Room.databaseBuilder(context, KhataDatabase::class.java, "restored-check.db")
            .allowMainThreadQueries()
            .build()
        assertTrue(reopened.accountDao().getAll().any { it.uuid == "acc-survivor" })
        reopened.close()
        fresh.delete()
    }

    @Test
    fun `restoring keeps the database it replaced`() = runTest {
        db.accountDao().upsert(account("acc-before"))
        val file = repository.backUp(key, saltForKey)!!

        repository.restore(file.readBytes(), key)

        // A restore that goes wrong must not be the thing that loses the data.
        assertTrue(File(context.getDatabasePath("khata.db").parentFile, "khata.db.replaced").exists())
    }

    @Test
    fun `a wrong passphrase changes nothing on disk`() = runTest {
        db.accountDao().upsert(account("acc-untouched"))
        val file = repository.backUp(key, saltForKey)!!
        val wrong = BackupFile.deriveKey("wrong", ByteArray(16))

        val result = repository.restore(file.readBytes(), wrong)

        assertTrue(result is BackupResult.WrongPassphrase)
        // The refusal happens before anything is written, so the live database is
        // still open and still correct.
        assertTrue(db.accountDao().getAll().any { it.uuid == "acc-untouched" })
    }

    @Test
    fun `a backup can be restored from the passphrase alone`() = runTest {
        // The real path, and the one the other tests missed: they carried a key object
        // from write to read, so the header's salt was never used. Here the key is
        // re-derived from the file, exactly as a restore on another phone must.
        val salt = BackupFile.newSalt()
        val writeKey = BackupFile.deriveKey("the phrase", salt)
        db.accountDao().upsert(account("acc-passphrase"))

        val file = repository.backUp(writeKey, salt)!!

        val bytes = file.readBytes()
        val readKey = BackupFile.deriveKey("the phrase", BackupFile.saltOf(bytes)!!)
        assertTrue(repository.restore(bytes, readKey) is BackupResult.Restored)
    }
}
