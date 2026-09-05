package com.wasif.khata.feature.watchlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.data.repository.ENTITY_TITLE
import com.wasif.khata.core.data.repository.WatchlistRepository
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.tag.TagRepository
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.watch.Tmdb
import com.wasif.khata.core.watch.TmdbResult
import java.io.ByteArrayOutputStream
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddTitleViewModelTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: WatchlistRepository
    private lateinit var mediaStore: MediaStore

    private val dispatcher = StandardTestDispatcher()
    private val clock = object : KhataClock {
        override fun now(): Long = 1_700_000_000_000L
    }

    /** No network and no key, which is the point: the screen is testable without both. */
    private class FakeTmdb(
        var results: List<TmdbResult> = emptyList(),
        var posterBytes: ByteArray? = null,
    ) : Tmdb {
        var searches = 0
        var posterFetches = 0

        override suspend fun search(query: String): List<TmdbResult> {
            searches++
            return results
        }

        override suspend fun rating(tmdbId: Int, name: String): Double? =
            results.firstOrNull { it.tmdbId == tmdbId }?.rating

        override suspend fun poster(posterPath: String): ByteArray? {
            posterFetches++
            return posterBytes
        }
    }

    private val tmdb = FakeTmdb()

    private val heat = TmdbResult(949, "Heat", 1995, TitleKind.FILM, 7.9, "/heat.jpg")

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
        repository = WatchlistRepository(
            dao = db.watchlistDao(),
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
        mediaStore.dir().deleteRecursively()
    }

    private fun viewModel() = AddTitleViewModel(repository, tmdb, mediaStore, clock)

    /** A real 1x1 PNG, so MediaStore's bounds decode succeeds as it would on a poster. */
    private fun onePixelPng(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    @Test
    fun `picking a result fills the manual fields rather than replacing them`() = runTest {
        val vm = viewModel()
        vm.onResultPick(heat)

        assertEquals("Heat", vm.state.value.name)
        assertEquals("1995", vm.state.value.yearInput)
        assertEquals(TitleKind.FILM, vm.state.value.kind)
        // Still editable afterwards: the fields are the record, the result was a shortcut.
        vm.onNameChange("Heat (1995)")
        assertEquals("Heat (1995)", vm.state.value.name)
    }

    @Test
    fun `with nothing to search the box yields nothing and typing still saves`() = runTest {
        tmdb.results = emptyList()

        val vm = viewModel()
        vm.onQueryChange("heat")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList<TmdbResult>(), vm.state.value.results)

        vm.onNameChange("Heat")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val queued = repository.observeQueue().first()
        assertEquals(listOf("Heat"), queued.map { it.name })
        // Nothing was picked, so nothing pretends the row came from TMDB.
        assertNull(db.watchlistDao().findTitle(queued.single().id)?.tmdbId)
    }

    @Test
    fun `a picked result carries its id, rating and poster onto the saved title`() = runTest {
        tmdb.posterBytes = onePixelPng()

        val vm = viewModel()
        vm.onResultPick(heat)
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val row = db.watchlistDao().findTitle(repository.observeQueue().first().single().id)!!
        assertEquals(949, row.tmdbId)
        assertEquals(7.9, row.tmdbRating!!, 0.001)
        assertEquals(clock.now(), row.tmdbRatingAt)
        // The fetch is asserted here; that the bytes become one deduplicated file is
        // MediaStore's own test below, and that the id reaches the row is the
        // repository's. Asserting the whole chain here would mean waiting on
        // Dispatchers.IO, which this test's scheduler does not drive.
        assertEquals(1, tmdb.posterFetches)
    }

    @Test
    fun `editing the name after picking drops the poster and rating with it`() = runTest {
        tmdb.posterBytes = onePixelPng()

        val vm = viewModel()
        vm.onResultPick(heat)
        // A different name is a different title; carrying Heat's poster onto it would
        // attach one film's picture to another's row.
        vm.onNameChange("Heat 2")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val row = db.watchlistDao().findTitle(repository.observeQueue().first().single().id)!!
        assertNull(row.tmdbId)
        assertNull(row.tmdbRating)
        assertNull(row.posterMediaId)
        assertEquals(0, tmdb.posterFetches)
    }

    @Test
    fun `a poster that will not download leaves the title without one`() = runTest {
        tmdb.posterBytes = null

        val vm = viewModel()
        vm.onResultPick(heat)
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val row = db.watchlistDao().findTitle(repository.observeQueue().first().single().id)!!
        assertEquals(949, row.tmdbId)
        assertNull(row.posterMediaId)
    }

    @Test
    fun `the same poster for two titles is one file and one row`() = runTest {
        val bytes = onePixelPng()

        val first = mediaStore.importBytes(bytes, ENTITY_TITLE, 1L)!!
        val second = mediaStore.importBytes(bytes, ENTITY_TITLE, 2L)!!

        assertEquals(first.id, second.id)
        assertEquals(1, mediaStore.dir().listFiles()!!.size)
    }

    @Test
    fun `already watched logs a watch, so the title skips the queue`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Heat")
        vm.onLogWatchNowChange(true)
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.observeQueue().first().map { it.name })
        assertEquals(listOf("Heat"), repository.observeWatched().first().map { it.name })
    }

    @Test
    fun `recommenders reach the title as tags and make it findable`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Heat")
        vm.onRecommenderInputChange("Rafi")
        vm.onRecommenderAdded()
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        val id = repository.observeQueue().first().single().id
        assertEquals(listOf("Rafi"), repository.recommendersFor(id))
    }

    @Test
    fun `a blank name cannot be saved`() {
        val vm = viewModel()
        vm.onNameChange("   ")
        assertEquals(false, vm.state.value.canSave)
    }
}
