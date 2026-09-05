package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.MediaEntity
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.tag.TagRepository
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestaurantRepositoryTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: RestaurantRepository

    private val clock = object : KhataClock {
        override fun now(): Long = 1_000L
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        val index = searchIndex(db)
        repository = RestaurantRepository(
            dao = db.restaurantDao(),
            mediaDao = db.mediaDao(),
            tags = TagRepository(db.tagDao(), index, clock),
            searchIndex = index,
            clock = clock,
        )
    }

    @After
    fun tearDown() = db.close()

    private fun visit(restaurantId: Long) = VisitDraft(restaurantId = restaurantId, visitedAt = 5_000L)

    private fun dish(name: String, rating: Int?) = DishDraft(name = name, rating = rating)

    private suspend fun insertMedia(): Long = db.mediaDao().upsert(
        MediaEntity(
            uuid = "m-1",
            sha256 = "abc",
            mimeType = "image/jpeg",
            widthPx = 10,
            heightPx = 10,
            byteSize = 100,
            createdAt = 1,
            updatedAt = 1,
        ),
    )

    @Test
    fun `a differently cased name finds the existing restaurant`() = runTest {
        val first = repository.findOrCreate("Sultans Dine")
        assertEquals(first, repository.findOrCreate("sultans dine"))
    }

    @Test
    fun `a restaurant with no visits is on the wishlist, and a visit moves it`() = runTest {
        val id = repository.findOrCreate("Kacchi Bhai")
        assertEquals(listOf(id), repository.observeWishlist().first().map { it.id })

        repository.saveVisit(visit(restaurantId = id), dishes = emptyList(), companions = emptyList())

        assertTrue(repository.observeWishlist().first().isEmpty())
        assertEquals(listOf(id), repository.observeBeen().first().map { it.id })
    }

    @Test
    fun `soft-deleting the only visit puts it back on the wishlist`() = runTest {
        // Derived means it goes both ways for free. A stored flag would need code for
        // this direction and would eventually be missing it.
        val id = repository.findOrCreate("Kacchi Bhai")
        val visitId = repository.saveVisit(visit(id), emptyList(), emptyList())

        repository.deleteVisit(visitId)

        assertEquals(listOf(id), repository.observeWishlist().first().map { it.id })
    }

    @Test
    fun `the dish average ignores unrated dishes`() = runTest {
        val id = repository.findOrCreate("Sultans Dine")
        repository.saveVisit(
            visit(id),
            dishes = listOf(dish("kacchi", 5), dish("borhani", null), dish("firni", 3)),
            companions = emptyList(),
        )

        // 4.0, not 2.67: an unrated dish is unrated, not zero out of five.
        assertEquals(4.0, repository.verdict(id)!!, 0.001)
    }

    @Test
    fun `setting a cover points at the existing media row`() = runTest {
        // "Set any photo from that visit as the cover" is a reference, never a copy.
        val existingMediaId = insertMedia()
        val id = repository.findOrCreate("Sultans Dine")
        repository.setCover(id, mediaId = existingMediaId)

        assertEquals(existingMediaId, repository.find(id)!!.coverMediaId)
        assertEquals(1, db.mediaDao().allLive().size)
    }

    @Test
    fun `companions are tag links on the visit, not a column on it`() = runTest {
        val id = repository.findOrCreate("Sultans Dine")
        val visitId = repository.saveVisit(visit(id), emptyList(), companions = listOf("Rafi", "rafi"))

        // Case-folded by TagRepository, so the same person twice is one tag.
        assertEquals(listOf("Rafi"), repository.companionsFor(visitId).map { it.name })
    }

    @Test
    fun `editing a visit replaces its dishes rather than accumulating them`() = runTest {
        val id = repository.findOrCreate("Sultans Dine")
        val visitId = repository.saveVisit(visit(id), listOf(dish("kacchi", 5)), emptyList())

        repository.saveVisit(
            visit(id).copy(id = visitId),
            dishes = listOf(dish("kacchi", 4)),
            companions = emptyList(),
        )

        assertEquals(listOf("kacchi"), repository.dishesFor(visitId).map { it.name })
        assertEquals(4.0, repository.verdict(id)!!, 0.001)
    }
}
