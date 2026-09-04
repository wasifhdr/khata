package com.wasif.khata.feature.restaurants

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.repository.RestaurantRepository
import com.wasif.khata.core.place.PlaceRepository
import com.wasif.khata.core.search.ftsQuery
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.tag.TagRepository
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A place shared out of Maps: a name line, then a link line. */
private const val MAPS_SHARE = """Sultan's Dine
https://maps.app.goo.gl/abc123"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddToWishlistViewModelTest {

    private lateinit var db: KhataDatabase
    private lateinit var restaurants: RestaurantRepository
    private lateinit var places: PlaceRepository

    // Unconfined, unlike the visit editor's test: saving a place hops to
    // Dispatchers.IO inside PlaceRepository, which no test scheduler owns, so
    // these tests await the saved effect rather than advancing virtual time.
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = object : KhataClock {
        override fun now(): Long = 1_000L
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
        val index = searchIndex(db)
        restaurants = RestaurantRepository(
            dao = db.restaurantDao(),
            mediaDao = db.mediaDao(),
            tags = TagRepository(db.tagDao(), index, clock),
            searchIndex = index,
            clock = clock,
        )
        places = PlaceRepository(db.placeDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = AddToWishlistViewModel(restaurants, places)

    @Test
    fun `a shared place prefills the name and keeps the link`() = runTest {
        val vm = viewModel()
        vm.onSharedText(MAPS_SHARE)

        assertEquals("Sultan's Dine", vm.state.value.nameInput)
        assertEquals("https://maps.app.goo.gl/abc123", vm.state.value.place?.url)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `a second application does not overwrite a corrected name`() = runTest {
        // The screen re-runs its LaunchedEffect on a configuration change, and the
        // user may already have fixed what Maps called the place.
        val vm = viewModel()
        vm.onSharedText(MAPS_SHARE)
        vm.onNameChange("Sultans Dine Dhanmondi")
        vm.onSharedText(MAPS_SHARE)

        assertEquals("Sultans Dine Dhanmondi", vm.state.value.nameInput)
    }

    @Test
    fun `text that is neither a link nor coordinates prefills nothing`() = runTest {
        val vm = viewModel()
        vm.onSharedText("call me when you get there")

        assertEquals("", vm.state.value.nameInput)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `saving lands it on the wishlist, not among the places you have been`() = runTest {
        val vm = viewModel()
        vm.onSharedText(MAPS_SHARE)
        vm.onNoteChange("Rafi says the kacchi is the point")
        vm.onRecommenderInputChange("Rafi")
        vm.onRecommenderAdded()
        vm.onSave()
        vm.saved.first()

        val wishlist = restaurants.observeWishlist().first()
        assertEquals(listOf("Sultan's Dine"), wishlist.map { it.name })
        assertTrue(restaurants.observeBeen().first().isEmpty())

        val id = wishlist.single().id
        assertEquals("Rafi says the kacchi is the point", restaurants.find(id)!!.note)
        assertEquals(listOf("Rafi"), restaurants.recommendersFor(id).map { it.name })
        // The place row was written and pointed at, rather than the URL being copied
        // onto the restaurant.
        assertEquals("Sultan's Dine", wishlist.single().placeName)
    }

    @Test
    fun `a wishlist entry is findable by who recommended it`() = runTest {
        val vm = viewModel()
        vm.onSharedText(MAPS_SHARE)
        vm.onRecommenderInputChange("Rafi")
        vm.onRecommenderAdded()
        vm.onSave()
        vm.saved.first()

        val id = restaurants.observeWishlist().first().single().id
        assertTrue(db.searchDao().idsMatching("restaurant", ftsQuery("rafi")!!).contains(id))
    }
}
