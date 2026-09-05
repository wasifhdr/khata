package com.wasif.khata.feature.vehicle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.repository.ItemDraft
import com.wasif.khata.core.data.repository.ServiceDraft
import com.wasif.khata.core.data.repository.VehicleRepository
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ServiceEditorViewModelTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: VehicleRepository
    private lateinit var mediaStore: MediaStore

    private val dispatcher = StandardTestDispatcher()
    private val clock = object : KhataClock {
        override fun now(): Long = 1_700_000_000_000L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        )
            // Room's suspend DAO functions otherwise run on a real background thread,
            // which advanceUntilIdle knows nothing about and never waits for.
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
        mediaStore = MediaStore(ApplicationProvider.getApplicationContext(), db.mediaDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(serviceId: Long? = null) =
        ServiceEditorViewModel(repository, mediaStore, clock, serviceId)

    private suspend fun savedService() = db.vehicleDao()
        .observeServices(db.vehicleDao().firstVehicle()!!.id)
        .first()
        .single()

    @Test
    fun `a typed workshop that matches nothing creates the place`() = runTest {
        val vm = viewModel()
        vm.onWorkshopChange("Navana Workshop")
        vm.onCostChange("500")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val service = savedService()
        assertNotNull(service.placeId)
        assertEquals("Navana Workshop", service.placeName)
        assertEquals(500_00L, service.costMinor)
    }

    @Test
    fun `a differently cased workshop lands on the one place`() = runTest {
        repository.findOrCreateWorkshop("Navana Workshop")

        val vm = viewModel()
        vm.onWorkshopChange("navana workshop")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, db.placeDao().observeAll().first().size)
    }

    @Test
    fun `picking a suggestion attaches to that place rather than creating a second`() = runTest {
        val existing = repository.findOrCreateWorkshop("Navana Workshop")

        val vm = viewModel()
        vm.onWorkshopChange("Nav")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("Navana Workshop"), vm.state.value.suggestions)

        vm.onSuggestionPicked("Navana Workshop")
        dispatcher.scheduler.advanceUntilIdle()
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(existing, savedService().placeId)
    }

    @Test
    fun `an empty odometer saves as null rather than zero`() = runTest {
        val vm = viewModel()
        vm.onOdometerChange("")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(savedService().odometerKm)
    }

    @Test
    fun `an unreadable odometer blocks the save rather than storing a guess`() {
        val vm = viewModel()
        vm.onOdometerChange("forty thousand")
        assertTrue(vm.state.value.odometerHasError)
        assertTrue(!vm.state.value.canSave)
    }

    @Test
    fun `blank item rows are dropped rather than saved as empty names`() = runTest {
        val vm = viewModel()
        vm.onItemAdded()
        vm.onItemNameChange(0, "   ")
        vm.onItemNameChange(1, "Wiper")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Wiper"), repository.items(savedService().id).map { it.name })
    }

    @Test
    fun `the reading updates the car, but an older one never winds it back`() = runTest {
        viewModel().apply {
            onOdometerChange("42000")
            onSave()
        }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(42_000, repository.vehicle()?.odometerKm)

        // A job entered later but read earlier: the car has not driven backwards.
        viewModel().apply {
            onOdometerChange("41000")
            onSave()
        }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(42_000, repository.vehicle()?.odometerKm)
    }

    @Test
    fun `editing an existing service loads its items and rewrites rather than duplicating`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(
            ServiceDraft(vehicleId = vehicleId, servicedAt = 1L, costMinor = 500_00L),
            listOf(ItemDraft("Oil filter", 120_00L)),
        )

        val vm = viewModel(serviceId = id)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("Oil filter"), vm.state.value.items.map { it.name })
        assertEquals("120.00", vm.state.value.items.single().costInput)

        vm.onItemNameChange(0, "Air filter")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Air filter"), repository.items(id).map { it.name })
        assertEquals(1, db.vehicleDao().observeServices(vehicleId).first().size)
    }

    @Test
    fun `deleting a service removes it from the history`() = runTest {
        val vehicleId = repository.findOrCreateVehicle()
        val id = repository.saveService(ServiceDraft(vehicleId = vehicleId, servicedAt = 1L), emptyList())

        val vm = viewModel(serviceId = id)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onDelete()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, db.vehicleDao().observeServices(vehicleId).first().size)
    }
}
