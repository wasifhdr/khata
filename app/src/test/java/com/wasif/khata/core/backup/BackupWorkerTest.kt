package com.wasif.khata.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.drive.DriveMedia
import com.wasif.khata.core.drive.DriveUploader
import com.wasif.khata.core.drive.MediaSync
import com.wasif.khata.core.drive.UploadOutcome
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.ui.theme.ThemeSpec
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The worker decodes these and hands them to AES-256-GCM, so they have to be a real
 * 32-byte key and 16-byte salt -- anything shorter throws InvalidKeyException inside
 * backUp, and the worker reports that as a retry rather than a failed assertion.
 */
private const val KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
private const val SALT = "AAECAwQFBgcICQoLDA0ODw=="

@RunWith(RobolectricTestRunner::class)
class BackupWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = object : KhataClock { override fun now(): Long = 1_000L }

    private var uploads = 0
    private var outcome = UploadOutcome.UPLOADED
    private val uploader = DriveUploader { _ -> uploads++; outcome }

    private lateinit var db: KhataDatabase
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        deleteAll()
        // File-backed, not in-memory: backUp copies the database file, and an
        // in-memory one has none to copy.
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

    private class FakePreferences(
        private val key: String?,
        private val salt: String?,
    ) : PreferencesRepository {
        override val preferences: Flow<KhataPreferences> =
            flowOf(KhataPreferences.Default.copy(backupKey = key, backupSalt = salt))

        override suspend fun setTheme(spec: ThemeSpec) = Unit
        override suspend fun resetTheme() = Unit
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
        override suspend fun setSmsPermissionRequested() = Unit
        override suspend fun setBackfilled() = Unit
        override suspend fun setGeminiKey(key: String?) = Unit
        override suspend fun setTmdbKey(key: String?) = Unit
        override suspend fun setBackupPassphrase(passphrase: String?) = Unit
        override suspend fun setDriveConnected(connected: Boolean) = Unit
        override suspend fun setDriveFolderId(id: String?) = Unit
        override suspend fun setDriveUploaded(at: Long) = Unit
        override suspend fun setDriveNeedsReconnect() = Unit
        override suspend fun setSearchIndexVersion(version: Int) = Unit
    }

    /** runTest returns a TestResult, not the block's value, so the result comes out
     *  through a var rather than as an expression body. */
    private fun run(key: String? = KEY, salt: String? = SALT): ListenableWorker.Result {
        lateinit var result: ListenableWorker.Result
        runTest {
            result = TestListenableWorkerBuilder<BackupWorker>(context)
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = BackupWorker(
                        appContext,
                        workerParameters,
                        repository,
                        FakePreferences(key, salt),
                        uploader,
                        MediaSync(
                            drive = object : DriveMedia {
                                override suspend fun blobNames() = emptyList<String>()
                                override suspend fun putBlob(name: String, bytes: ByteArray) = true
                                override suspend fun getBlob(name: String): ByteArray? = null
                            },
                            store = MediaStore(appContext, db.mediaDao(), clock),
                            dao = db.mediaDao(),
                            preferences = FakePreferences(key, salt),
                        ),
                    )
                })
                .build()
                .doWork()
        }
        return result
    }

    @Test
    fun `no passphrase means no backup and no upload`() {
        assertEquals(ListenableWorker.Result.success(), run(key = null))

        assertEquals(0, uploads)
        assertFalse(File(context.filesDir, "backups").exists())
    }

    @Test
    fun `the backup that was just written is the one uploaded`() {
        assertEquals(ListenableWorker.Result.success(), run())

        assertEquals(1, uploads)
        assertEquals(1, repository.backupDir().listFiles()!!.size)
    }

    @Test
    fun `Drive not being connected is not a failure`() {
        // Otherwise WorkManager would retry with backoff forever on a phone that has
        // deliberately never connected Drive.
        outcome = UploadOutcome.SKIPPED

        assertEquals(ListenableWorker.Result.success(), run())
    }

    @Test
    fun `a failed upload retries, and the local copy survives it`() {
        outcome = UploadOutcome.FAILED

        assertEquals(ListenableWorker.Result.retry(), run())

        // The whole point of writing locally first: Drive being down must not cost
        // tonight's backup.
        assertEquals(1, repository.backupDir().listFiles()!!.size)
    }
}
