package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.MediaEntity
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.tag.TagRepository
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchlistRepositoryTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: WatchlistRepository

    private val clock = object : KhataClock {
        override fun now(): Long = 1_000_000L
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        val index = searchIndex(db)
        repository = WatchlistRepository(
            dao = db.watchlistDao(),
            tags = TagRepository(db.tagDao(), index, clock),
            searchIndex = index,
            clock = clock,
        )
    }

    @After
    fun tearDown() = db.close()

    private fun film(name: String, tmdbId: Int? = null) =
        TitleDraft(name = name, kind = TitleKind.FILM, tmdbId = tmdbId)

    @Test
    fun `a title with no watches is queued, logging one promotes it, deleting it returns it`() = runTest {
        val id = repository.saveTitle(film("Heat"))
        assertEquals(listOf("Heat"), repository.observeQueue().first().map { it.name })
        assertTrue(repository.observeWatched().first().isEmpty())

        val watchId = repository.logWatch(WatchDraft(titleId = id, watchedAt = 1L, rating = 5))
        assertTrue(repository.observeQueue().first().isEmpty())
        assertEquals(listOf("Heat"), repository.observeWatched().first().map { it.name })

        repository.deleteWatch(watchId, id)
        assertEquals(listOf("Heat"), repository.observeQueue().first().map { it.name })
    }

    @Test
    fun `the verdict averages rated watches and ignores unrated ones`() = runTest {
        val id = repository.saveTitle(film("Heat"))
        repository.logWatch(WatchDraft(titleId = id, watchedAt = 1L, rating = 5))
        repository.logWatch(WatchDraft(titleId = id, watchedAt = 2L, rating = null))
        repository.logWatch(WatchDraft(titleId = id, watchedAt = 3L, rating = 3))

        assertEquals(4.0, repository.observeSummary(id).first()!!.verdict!!, 0.001)
        assertEquals(3, repository.observeSummary(id).first()!!.watchCount)
    }

    @Test
    fun `a title whose watches are all unrated has no verdict rather than a zero`() = runTest {
        val id = repository.saveTitle(film("Heat"))
        repository.logWatch(WatchDraft(titleId = id, watchedAt = 1L, rating = null))

        assertNull(repository.observeSummary(id).first()!!.verdict)
    }

    @Test
    fun `the same tmdb entry saved twice is one row`() = runTest {
        val first = repository.saveTitle(film("Heat", tmdbId = 949))
        val second = repository.saveTitle(film("Heat", tmdbId = 949))
        assertEquals(first, second)
        assertEquals(1, db.watchlistDao().allIdsForIndex().size)
    }

    @Test
    fun `a hand-typed title later matched to TMDB stays one row`() = runTest {
        val typed = repository.saveTitle(film("Heat"))
        val matched = repository.saveTitle(film("heat", tmdbId = 949))

        assertEquals(typed, matched)
        assertEquals(949, db.watchlistDao().findTitle(typed)?.tmdbId)
    }

    @Test
    fun `a stored rating survives a later save that carries none`() = runTest {
        val id = repository.saveTitle(
            TitleDraft(name = "Heat", kind = TitleKind.FILM, tmdbId = 949, tmdbRating = 7.9, tmdbRatingAt = 500L),
        )
        repository.saveTitle(TitleDraft(id = id, name = "Heat", kind = TitleKind.FILM))

        val row = db.watchlistDao().findTitle(id)!!
        assertEquals(7.9, row.tmdbRating!!, 0.001)
        assertEquals(500L, row.tmdbRatingAt)
    }

    @Test
    fun `recommenders attach as spine tags and fold by case`() = runTest {
        val id = repository.saveTitle(film("Heat"), recommenders = listOf("Rafi"))
        repository.saveTitle(TitleDraft(id = id, name = "Heat", kind = TitleKind.FILM), recommenders = listOf("rafi"))

        assertEquals(listOf("Rafi"), repository.recommendersFor(id))
    }

    @Test
    fun `the refresh gate waits seven days, and never fires without a tmdb id`() {
        val now = 10_000_000_000L
        val week = 7L * 24 * 60 * 60 * 1000

        assertFalse(needsRefresh(tmdbId = null, ratingAt = null, now = now))
        assertFalse(needsRefresh(tmdbId = 949, ratingAt = now - week + 1, now = now))
        assertTrue(needsRefresh(tmdbId = 949, ratingAt = now - week - 1, now = now))
        // Never fetched, but it has an id to fetch by.
        assertTrue(needsRefresh(tmdbId = 949, ratingAt = null, now = now))
    }

    @Test
    fun `setting a poster points the title at an existing media row`() = runTest {
        val id = repository.saveTitle(film("Heat", tmdbId = 949))
        val mediaId = db.mediaDao().upsert(
            MediaEntity(
                uuid = "m-1",
                sha256 = "abc",
                mimeType = "image/jpeg",
                widthPx = 500,
                heightPx = 750,
                byteSize = 1_000,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        repository.setPoster(id, mediaId)

        assertEquals(mediaId, db.watchlistDao().findTitle(id)?.posterMediaId)
        // The summary joins the hash, so a grid of titles is one query.
        assertEquals("abc", repository.observeSummary(id).first()!!.posterSha)
    }

    @Test
    fun `refreshing a rating stamps the date it was taken`() = runTest {
        val id = repository.saveTitle(
            TitleDraft(name = "Heat", kind = TitleKind.FILM, tmdbId = 949, tmdbRating = 7.9, tmdbRatingAt = 500L),
        )
        repository.refreshRating(id, 8.1)

        val row = db.watchlistDao().findTitle(id)!!
        assertEquals(8.1, row.tmdbRating!!, 0.001)
        assertEquals(clock.now(), row.tmdbRatingAt)
    }
}
