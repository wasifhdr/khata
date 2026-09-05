package com.wasif.khata.feature.watchlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.data.repository.TitleDraft
import com.wasif.khata.core.data.repository.WatchDraft
import com.wasif.khata.core.data.repository.WatchlistRepository
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.tag.TagRepository
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.watch.Tmdb
import com.wasif.khata.core.watch.TmdbResult
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TitleViewModelTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: WatchlistRepository
    private lateinit var mediaStore: MediaStore

    private val dispatcher = StandardTestDispatcher()
    private val now = 1_700_000_000_000L
    private val clock = object : KhataClock {
        override fun now(): Long = now
    }

    private val week = 7L * 24 * 60 * 60 * 1000

    private class FakeTmdb : Tmdb {
        var nextRating: Double? = null
        var ratingCalls = 0

        override suspend fun search(query: String): List<TmdbResult> = emptyList()

        override suspend fun rating(tmdbId: Int, name: String): Double? {
            ratingCalls++
            return nextRating
        }

        override suspend fun poster(posterPath: String): ByteArray? = null
    }

    private val tmdb = FakeTmdb()

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
    }

    private fun viewModel(titleId: Long) =
        TitleViewModel(repository, tmdb, mediaStore, clock, titleId)

    private suspend fun title(ratingAt: Long?, tmdbId: Int? = 949): Long = repository.saveTitle(
        TitleDraft(
            name = "Heat",
            kind = TitleKind.FILM,
            tmdbId = tmdbId,
            tmdbRating = ratingAt?.let { 7.9 },
            tmdbRatingAt = ratingAt,
        ),
    )

    @Test
    fun `a snapshot under seven days old triggers no call`() = runTest {
        val id = title(ratingAt = now - week + 1_000)
        viewModel(id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, tmdb.ratingCalls)
    }

    @Test
    fun `an older snapshot refreshes and stamps the new date`() = runTest {
        val id = title(ratingAt = now - week - 1_000)
        tmdb.nextRating = 8.1

        viewModel(id)
        dispatcher.scheduler.advanceUntilIdle()

        val row = db.watchlistDao().findTitle(id)!!
        assertEquals(1, tmdb.ratingCalls)
        assertEquals(8.1, row.tmdbRating!!, 0.001)
        assertEquals(now, row.tmdbRatingAt)
    }

    @Test
    fun `a failed refresh leaves the stored rating and its date untouched`() = runTest {
        val stale = now - week - 1_000
        val id = title(ratingAt = stale)
        // Offline, no key, or a non-200: all of them arrive here as null.
        tmdb.nextRating = null

        viewModel(id)
        dispatcher.scheduler.advanceUntilIdle()

        val row = db.watchlistDao().findTitle(id)!!
        assertEquals(7.9, row.tmdbRating!!, 0.001)
        assertEquals(stale, row.tmdbRatingAt)
    }

    @Test
    fun `a title with no tmdb id never calls out`() = runTest {
        val id = title(ratingAt = null, tmdbId = null)

        viewModel(id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, tmdb.ratingCalls)
    }

    @Test
    fun `a title never fetched has an id to fetch by, so it refreshes`() = runTest {
        val id = title(ratingAt = null)
        tmdb.nextRating = 8.1

        viewModel(id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, tmdb.ratingCalls)
        assertEquals(8.1, db.watchlistDao().findTitle(id)!!.tmdbRating!!, 0.001)
    }

    @Test
    fun `logging a watch through the screen promotes the title out of the queue`() = runTest {
        val id = title(ratingAt = now)
        val vm = viewModel(id)

        vm.onLogWatch(now, rating = 4, note = "")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(4.0, db.watchlistDao().observeSummary(id).first()!!.verdict!!, 0.001)
    }

    @Test
    fun `deleting the only watch returns the title to the queue`() = runTest {
        val id = title(ratingAt = now)
        val watchId = repository.logWatch(WatchDraft(titleId = id, watchedAt = now, rating = 4))

        val vm = viewModel(id)
        vm.onDeleteWatch(watchId)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Heat"), repository.observeQueue().first().map { it.name })
    }
}
