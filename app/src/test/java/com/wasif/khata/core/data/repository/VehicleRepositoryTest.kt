package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.dao.CostTotals
import com.wasif.khata.core.data.dao.ServiceSummary
import com.wasif.khata.core.data.dao.VehicleDao
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VehicleRepositoryTest {

    private lateinit var db: KhataDatabase
    private lateinit var dao: VehicleDao
    private lateinit var repository: VehicleRepository

    private val clock = object : KhataClock {
        override fun now(): Long = 1_000L
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.vehicleDao()
        repository = VehicleRepository(
            dao = dao,
            places = db.placeDao(),
            mediaDao = db.mediaDao(),
            searchIndex = searchIndex(db),
            clock = clock,
        )
    }

    @After
    fun tearDown() = db.close()

    private fun summary(costMinor: Long?, itemCostTotal: Long?, unpriced: Int, itemCount: Int) =
        ServiceSummary(
            id = 1,
            servicedAt = 1,
            odometerKm = null,
            costMinor = costMinor,
            note = null,
            placeId = null,
            placeName = null,
            mapsUrl = null,
            itemNames = null,
            itemCount = itemCount,
            itemCostTotal = itemCostTotal,
            unpricedItemCount = unpriced,
        )

    @Test
    fun `findOrCreateVehicle returns the same row on a second call`() = runTest {
        val first = repository.findOrCreateVehicle()
        val second = repository.findOrCreateVehicle()
        assertEquals(first, second)
        assertEquals("Car", dao.firstVehicle()?.name)
    }

    @Test
    fun `cost per km needs two readings and a span`() {
        assertNull(costPerKm(CostTotals(500_00L, 1, 42_000, 42_000)))
        assertNull(costPerKm(CostTotals(500_00L, 2, 42_000, 42_000)))
        assertNull(costPerKm(CostTotals(500_00L, 2, null, null)))
        assertNull(costPerKm(CostTotals(null, 2, 42_000, 42_050)))
        assertEquals(100.0, costPerKm(CostTotals(500_00L, 2, 42_000, 42_500))!!, 0.001)
    }

    @Test
    fun `other is the remainder only when every item is priced`() {
        assertEquals(
            120_00L,
            otherMinor(summary(costMinor = 500_00L, itemCostTotal = 380_00L, unpriced = 0, itemCount = 2)),
        )
        assertNull(otherMinor(summary(costMinor = 500_00L, itemCostTotal = 380_00L, unpriced = 1, itemCount = 2)))
        assertNull(otherMinor(summary(costMinor = 500_00L, itemCostTotal = null, unpriced = 0, itemCount = 0)))
        assertNull(otherMinor(summary(costMinor = null, itemCostTotal = 380_00L, unpriced = 0, itemCount = 2)))
        // Items adding up to more than the bill leaves nothing to call "other".
        assertNull(otherMinor(summary(costMinor = 300_00L, itemCostTotal = 380_00L, unpriced = 0, itemCount = 2)))
    }

    @Test
    fun `saving a service twice leaves the items it holds, not two copies`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L, costMinor = 500_00L),
            listOf(ItemDraft("Oil filter", 120_00L), ItemDraft("Brake pads", null)),
        )
        repository.saveService(
            ServiceDraft(id = id, vehicleId = vehicleId, servicedAt = 1L, costMinor = 500_00L),
            listOf(ItemDraft("Oil filter", 120_00L)),
        )
        assertEquals(listOf("Oil filter"), dao.itemsFor(id).map { it.name })
    }

    @Test
    fun `blank item rows are dropped rather than stored as empty names`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L),
            listOf(ItemDraft("   ", null), ItemDraft("Wiper", null)),
        )
        assertEquals(listOf("Wiper"), dao.itemsFor(id).map { it.name })
    }

    @Test
    fun `spend by item folds case and skips unpriced rows`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L),
            listOf(ItemDraft("Oil Filter", 100_00L)),
        )
        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 2L),
            listOf(ItemDraft("oil filter", 150_00L), ItemDraft("Wiper", null)),
        )
        val spend = dao.spendByItem(vehicleId).first()
        assertEquals(1, spend.size)
        assertEquals(250_00L, spend.single().totalMinor)
        assertEquals(2, spend.single().occurrences)
    }

    @Test
    fun `yearly spend buckets in Dhaka, not UTC`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        // 2025-12-31 19:00 UTC is 2026-01-01 01:00 in Dhaka. Bucketed in UTC this
        // service lands in the wrong year, which is the whole point of the +6.
        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1_767_207_600_000L, costMinor = 100_00L),
            emptyList(),
        )
        assertEquals("2026", dao.spendByYear(vehicleId).first().single().year)
    }

    @Test
    fun `a soft-deleted service leaves the totals and its items behind`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L, costMinor = 500_00L),
            listOf(ItemDraft("Oil filter", 120_00L)),
        )
        repository.deleteService(id)

        assertNull(dao.costTotals(vehicleId).first().totalMinor)
        assertEquals(emptyList<Long>(), dao.allServiceIdsForIndex())
        assertEquals(0, dao.spendByItem(vehicleId).first().size)
        assertEquals(0, dao.observeServices(vehicleId).first().size)
    }

    @Test
    fun `the summary counts unpriced items so the other line knows to stay away`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L, costMinor = 500_00L),
            listOf(ItemDraft("Oil filter", 120_00L), ItemDraft("Labour", null)),
        )
        val row = dao.serviceSummary(id).first()!!
        assertEquals(2, row.itemCount)
        assertEquals(1, row.unpricedItemCount)
        assertEquals("Oil filter, Labour", row.itemNames)
        assertNull(otherMinor(row))
    }
}
