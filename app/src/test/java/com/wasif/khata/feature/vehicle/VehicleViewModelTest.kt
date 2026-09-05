package com.wasif.khata.feature.vehicle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.repository.ItemDraft
import com.wasif.khata.core.data.repository.ServiceDraft
import com.wasif.khata.core.data.repository.VehicleRepository
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VehicleViewModelTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: VehicleRepository

    private val dispatcher = StandardTestDispatcher()

    // 2026-09-05 in Dhaka, so "this year" is 2026 for every assertion below.
    private val clock = object : KhataClock {
        override fun now(): Long = 1_788_600_000_000L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        repository = VehicleRepository(
            dao = db.vehicleDao(),
            places = db.placeDao(),
            mediaDao = db.mediaDao(),
            searchIndex = searchIndex(db),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Collected, because a WhileSubscribed StateFlow computes nothing unobserved. */
    private suspend fun stateAfterIdle(vm: VehicleViewModel): VehicleUiState {
        val job = CoroutineScope(dispatcher).launch { vm.state.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        val value = vm.state.value
        job.cancel()
        return value
    }

    @Test
    fun `an empty module shows no services and no invented totals`() = runTest {
        val state = stateAfterIdle(VehicleViewModel(repository, clock))

        assertEquals(emptyList<Any>(), state.services)
        assertNull(state.totalMinor)
        assertNull(state.thisYearMinor)
        assertNull(state.costPerKm)
        // The vehicle is created on open, so the header has a name to show.
        assertEquals("Car", state.vehicleName)
    }

    @Test
    fun `cost per km appears only once two readings exist`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L, odometerKm = 42_000, costMinor = 500_00L),
            emptyList(),
        )
        assertNull(stateAfterIdle(VehicleViewModel(repository, clock)).costPerKm)

        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 2L, odometerKm = 42_500, costMinor = 0L),
            emptyList(),
        )
        assertNotNull(stateAfterIdle(VehicleViewModel(repository, clock)).costPerKm)
    }

    @Test
    fun `this year counts only this year, and is absent when nothing was spent in it`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        // 2024-06-01, comfortably outside the clock's 2026.
        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1_717_200_000_000L, costMinor = 300_00L),
            emptyList(),
        )

        val old = stateAfterIdle(VehicleViewModel(repository, clock))
        assertEquals(300_00L, old.totalMinor)
        assertNull(old.thisYearMinor)

        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = clock.now(), costMinor = 200_00L),
            emptyList(),
        )
        val now = stateAfterIdle(VehicleViewModel(repository, clock))
        assertEquals(500_00L, now.totalMinor)
        assertEquals(200_00L, now.thisYearMinor)
    }

    @Test
    fun `spend by item reaches the state for the costs screen`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = clock.now(), costMinor = 500_00L),
            listOf(ItemDraft("Oil filter", 120_00L)),
        )

        val state = stateAfterIdle(VehicleViewModel(repository, clock))
        assertEquals(listOf("Oil filter"), state.spendByItem.map { it.name })
        assertEquals(listOf("2026"), state.spendByYear.map { it.year })
    }
}