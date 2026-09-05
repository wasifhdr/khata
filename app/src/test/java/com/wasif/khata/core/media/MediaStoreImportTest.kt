package com.wasif.khata.core.media

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.time.KhataClock
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The bytes half of pasting a picture into a note. Tested here rather than through the editor's
 * view model, because importBytes runs on Dispatchers.IO and a scheduler-driven test cannot wait
 * for it -- a test asserting "nothing was inserted" would pass while the import was still running.
 */
@RunWith(RobolectricTestRunner::class)
class MediaStoreImportTest {

    private lateinit var db: KhataDatabase
    private lateinit var store: MediaStore

    private val clock = object : KhataClock {
        override fun now(): Long = 1_000L
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = MediaStore(ApplicationProvider.getApplicationContext(), db.mediaDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
        store.dir().deleteRecursively()
    }

    private fun onePixelPng(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    @Test
    fun `empty bytes import as nothing, leaving no file behind`() = runTest {
        assertNull(store.importBytes(ByteArray(0), "note", 1L))

        assertEquals(0, store.dir().listFiles()?.size ?: 0)
    }

    // Bytes that are non-empty but undecodable cannot be tested here: Robolectric's
    // BitmapFactory shadow invents 100x100 bounds for anything, so the guard that rejects
    // them on a device never trips under test. Asserting it would be asserting the shadow.

    @Test
    fun `the same picture pasted into two notes is one file and one row`() = runTest {
        val bytes = onePixelPng()

        val first = store.importBytes(bytes, "note", 1L)!!
        val second = store.importBytes(bytes, "note", 2L)!!

        assertEquals(first.id, second.id)
        assertEquals(1, store.dir().listFiles()!!.size)
    }
}
