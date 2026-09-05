package com.wasif.khata.feature.restaurants

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.repository.RestaurantRepository
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.tag.TagRepository
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VisitEditorViewModelTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: RestaurantRepository
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
            // Room's suspend DAO functions otherwise run on a real background
            // thread, which advanceUntilIdle knows nothing about and never waits
            // for. Handing Room the test dispatcher puts every query on the
            // scheduler the test drives.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        val index = searchIndex(db)
        repository = RestaurantRepository(
            dao = db.restaurantDao(),
            mediaDao = db.mediaDao(),
            tags = TagRepository(db.tagDao(), index, clock),
            searchIndex = index,
            clock = clock,
        )
        mediaStore = MediaStore(ApplicationProvider.getApplicationContext(), db.mediaDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(visitId: Long? = null, restaurantId: Long? = null) =
        VisitEditorViewModel(repository, mediaStore, clock, visitId, restaurantId)

    @Test
    fun `typing a new name creates the restaurant when the visit is saved`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Kacchi Bhai")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val been = repository.observeBeen().first()
        assertEquals(listOf("Kacchi Bhai"), been.map { it.name })
        assertEquals(1, been.single().visitCount)
    }

    @Test
    fun `picking an existing name attaches to it rather than creating a second`() = runTest {
        val existing = repository.findOrCreate("Sultans Dine")

        val vm = viewModel()
        vm.onNameChange("Sult")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("Sultans Dine"), vm.state.value.suggestions)

        vm.onSuggestionPicked("Sultans Dine")
        dispatcher.scheduler.advanceUntilIdle()
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val been = repository.observeBeen().first()
        assertEquals(listOf(existing), been.map { it.id })
    }

    @Test
    fun `a differently cased name still lands on the one restaurant`() = runTest {
        val existing = repository.findOrCreate("Sultans Dine")

        // Typed out in full, never picked from the list: findOrCreate has to fold the
        // case, or the two spellings become two restaurants.
        val vm = viewModel()
        vm.onNameChange("sultans dine")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(existing), repository.observeBeen().first().map { it.id })
    }

    @Test
    fun `a visit with no rating and no dishes still saves`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Star Kabab")
        assertTrue(vm.state.value.canSave)

        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val visit = repository.observeBeen().first().single().let { summary ->
            repository.observeVisits(summary.id).first().single()
        }
        assertEquals(null, visit.ambianceRating)
        assertEquals(null, visit.costMinor)
        assertTrue(repository.dishesFor(visit.id).isEmpty())
    }

    @Test
    fun `an empty name cannot be saved`() = runTest {
        val vm = viewModel()
        assertFalse(vm.state.value.canSave)

        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.observeBeen().first().isEmpty())
    }

    @Test
    fun `a cost that is not a number blocks the save rather than being dropped`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Star Kabab")
        vm.onCostChange("twelve")

        assertTrue(vm.state.value.costHasError)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `dishes and companions are written with the visit`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Sultans Dine")
        vm.onAmbianceChange(4)
        vm.onCostChange("1250.50")
        vm.onDishNameChange(0, "Kacchi")
        vm.onDishRatingChange(0, 5)
        vm.onDishAdded()
        vm.onDishNameChange(1, "Borhani")
        vm.onCompanionInputChange("Rafi")
        vm.onCompanionAdded()
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val restaurantId = repository.observeBeen().first().single().id
        val visitId = repository.observeVisits(restaurantId).first().single().id

        assertEquals(125050L, repository.visit(visitId)!!.costMinor)
        assertEquals(4, repository.visit(visitId)!!.ambianceRating)
        assertEquals(listOf("Kacchi", "Borhani"), repository.dishesFor(visitId).map { it.name })
        assertEquals(listOf("Rafi"), repository.companionsFor(visitId).map { it.name })
        // An unrated dish is unrated, not zero: the verdict is the one rating given.
        assertEquals(5.0, repository.verdict(restaurantId)!!, 0.001)
    }

    @Test
    fun `editing a visit opens on what was saved`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Sultans Dine")
        vm.onAmbianceChange(3)
        vm.onDishNameChange(0, "Kacchi")
        vm.onCompanionInputChange("Rafi")
        vm.onCompanionAdded()
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val restaurantId = repository.observeBeen().first().single().id
        val visitId = repository.observeVisits(restaurantId).first().single().id

        val editing = viewModel(visitId = visitId)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Sultans Dine", editing.state.value.nameInput)
        assertEquals(3, editing.state.value.ambianceRating)
        assertEquals(listOf("Kacchi"), editing.state.value.dishes.map { it.name })
        assertEquals(listOf("Rafi"), editing.state.value.companions)
        assertNotNull(editing.state.value.restaurantId)
    }

    @Test
    fun `the same companion twice is one chip`() = runTest {
        val vm = viewModel()
        vm.onCompanionInputChange("Rafi")
        vm.onCompanionAdded()
        vm.onCompanionInputChange("rafi")
        vm.onCompanionAdded()

        assertEquals(listOf("Rafi"), vm.state.value.companions)
    }

    @Test
    fun `removing the last dish row leaves an empty one to type into`() = runTest {
        val vm = viewModel()
        vm.onDishNameChange(0, "Kacchi")
        vm.onDishRemoved(0)

        assertEquals(listOf(DishRow()), vm.state.value.dishes)
    }
}
