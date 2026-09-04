package com.wasif.khata.core.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.PlaceEntity
import com.wasif.khata.core.data.repository.ENTITY_VEHICLE_SERVICE
import com.wasif.khata.core.data.repository.ItemDraft
import com.wasif.khata.core.data.repository.ServiceDraft
import com.wasif.khata.core.data.repository.VehicleRepository
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VehicleServiceIndexSourceTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: VehicleRepository
    private lateinit var source: VehicleServiceIndexSource

    private val clock = object : KhataClock {
        override fun now(): Long = 1_000L
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = VehicleRepository(
            db.vehicleDao(),
            db.placeDao(),
            db.mediaDao(),
            searchIndex(db),
            clock,
        )
        source = VehicleServiceIndexSource(db.vehicleDao(), db.placeDao(), db.tagDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun workshop(name: String): Long = db.placeDao().upsert(
        PlaceEntity(uuid = "p-1", name = name, createdAt = 1, updatedAt = 1),
    )

    @Test
    fun `indexed text carries item names, the note, and the workshop`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val placeId = workshop("Navana Workshop")
        val id = repository.saveService(
            ServiceDraft(
                vehicleId = vehicleId,
                servicedAt = 1L,
                placeId = placeId,
                note = "rattling over speed bumps",
            ),
            listOf(ItemDraft("Front brake pads", 300_00L)),
        )

        val text = source.textFor(id)!!
        assertTrue(text.contains("Front brake pads"))
        assertTrue(text.contains("Navana Workshop"))
        assertTrue(text.contains("rattling"))
    }

    @Test
    fun `an item name finds its service through the index`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L),
            listOf(ItemDraft("Front brake pads", 300_00L)),
        )

        val hits = db.searchDao().idsMatching(ENTITY_VEHICLE_SERVICE, ftsQuery("brake")!!)
        assertEquals(listOf(id), hits)
    }

    @Test
    fun `a deleted service indexes as null and leaves the index`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L),
            listOf(ItemDraft("Front brake pads", 300_00L)),
        )
        repository.deleteService(id)

        assertNull(source.textFor(id))
        assertEquals(emptyList<Long>(), db.searchDao().idsMatching(ENTITY_VEHICLE_SERVICE, ftsQuery("brake")!!))
    }
}
