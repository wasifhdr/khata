package com.wasif.khata.core.drive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.backup.BackupFile
import com.wasif.khata.core.backup.SCHEMA_VERSION
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.MediaEntity
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.media.sha256
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.ui.theme.ThemeSpec
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A real 32-byte key and 16-byte salt: BackupFile hands these to AES-256-GCM. */
private const val KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
private const val SALT = "AAECAwQFBgcICQoLDA0ODw=="

@RunWith(RobolectricTestRunner::class)
class MediaSyncTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = object : KhataClock { override fun now(): Long = 1_000L }

    private lateinit var db: KhataDatabase
    private lateinit var store: MediaStore
    private lateinit var drive: FakeDriveMedia

    /** Drive as a map of name to bytes. No network, and no account. */
    private class FakeDriveMedia : DriveMedia {
        val blobs = mutableMapOf<String, ByteArray>()
        var puts = 0

        override suspend fun blobNames(): List<String> = blobs.keys.toList()

        override suspend fun putBlob(name: String, bytes: ByteArray): Boolean {
            puts++
            blobs[name] = bytes
            return true
        }

        override suspend fun getBlob(name: String): ByteArray? = blobs[name]
    }

    private class FakePreferences(private val key: String?, private val salt: String?) :
        PreferencesRepository {
        override val preferences: Flow<KhataPreferences> =
            flowOf(KhataPreferences.Default.copy(backupKey = key, backupSalt = salt))

        override suspend fun setTheme(spec: ThemeSpec) = Unit
        override suspend fun resetTheme() = Unit
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
        override suspend fun setSmsPermissionRequested() = Unit
        override suspend fun setBackfilled() = Unit
        override suspend fun setGeminiKey(key: String?) = Unit
        override suspend fun setBackupPassphrase(passphrase: String?) = Unit
        override suspend fun setDriveConnected(connected: Boolean) = Unit
        override suspend fun setDriveFolderId(id: String?) = Unit
        override suspend fun setDriveUploaded(at: Long) = Unit
        override suspend fun setDriveNeedsReconnect() = Unit
        override suspend fun setSearchIndexVersion(version: Int) = Unit
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java)
            .allowMainThreadQueries().build()
        store = MediaStore(context, db.mediaDao(), clock)
        drive = FakeDriveMedia()
        store.dir().deleteRecursively()
    }

    @After
    fun tearDown() {
        db.close()
        store.dir().deleteRecursively()
    }

    private fun sync(key: String? = KEY, salt: String? = SALT) =
        MediaSync(drive, store, db.mediaDao(), FakePreferences(key, salt))

    /** A photo on disk with a row pointing at it, which is what push uploads from. */
    private suspend fun givenPhoto(bytes: ByteArray): String {
        val hash = sha256(bytes)
        store.fileFor(hash).writeBytes(bytes)
        db.mediaDao().upsert(
            MediaEntity(
                uuid = UUID.randomUUID().toString(),
                sha256 = hash,
                mimeType = "image/jpeg",
                widthPx = 100,
                heightPx = 100,
                byteSize = bytes.size.toLong(),
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        return hash
    }

    @Test
    fun `a blob already in Drive is not uploaded again`() = runTest {
        val hash = givenPhoto(byteArrayOf(1, 2, 3))

        assertEquals(1, sync().push())
        assertEquals(1, drive.puts)

        // The name is the plaintext hash, so the second run sees it and skips.
        assertEquals(0, sync().push())
        assertEquals(1, drive.puts)
    }

    @Test
    fun `a blob round-trips through the envelope`() = runTest {
        val bytes = byteArrayOf(9, 8, 7, 6, 5)
        val hash = givenPhoto(bytes)
        sync().push()

        // As if on a fresh phone: the row's file is gone, the blob is not.
        store.fileFor(hash).delete()

        assertEquals(1, sync().pull(listOf(hash)))
        assertArrayEquals(bytes, store.fileFor(hash).readBytes())
    }

    @Test
    fun `a blob whose plaintext does not match its name is refused`() = runTest {
        val bytes = byteArrayOf(1, 1, 1)
        val hash = givenPhoto(bytes)
        sync().push()
        store.fileFor(hash).delete()

        // Same envelope, same key, different contents: only the re-hash catches this,
        // and without it a corrupt file lands under a name everything trusts.
        val key = android.util.Base64.decode(KEY, android.util.Base64.NO_WRAP)
        val salt = android.util.Base64.decode(SALT, android.util.Base64.NO_WRAP)
        drive.blobs["$hash.kbm"] =
            BackupFile.write(byteArrayOf(4, 4, 4), key, SCHEMA_VERSION, salt)

        assertEquals(0, sync().pull(listOf(hash)))
        assertFalse(store.fileFor(hash).exists())
    }

    @Test
    fun `no passphrase means no media upload, and no error`() = runTest {
        givenPhoto(byteArrayOf(1, 2, 3))

        assertEquals(0, sync(key = null).push())
        assertEquals(0, drive.puts)
    }

    @Test
    fun `pull fetches only hashes this phone lacks`() = runTest {
        val kept = givenPhoto(byteArrayOf(1, 2, 3))
        val missing = givenPhoto(byteArrayOf(4, 5, 6))
        sync().push()
        store.fileFor(missing).delete()

        assertEquals(1, sync().pull(listOf(kept, missing)))
        assertTrue(store.fileFor(kept).exists())
        assertTrue(store.fileFor(missing).exists())
    }
}
