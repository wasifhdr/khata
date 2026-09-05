package com.wasif.khata.core.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.dao.SearchDao
import com.wasif.khata.core.data.entity.PlaceEntity
import com.wasif.khata.core.data.repository.DishDraft
import com.wasif.khata.core.data.repository.RestaurantRepository
import com.wasif.khata.core.data.repository.VisitDraft
import com.wasif.khata.core.tag.TagRepository
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One row per restaurant, and everything about it folded into that row: the dishes
 * from every visit, the people who were there, and where it is.
 */
@RunWith(RobolectricTestRunner::class)
class RestaurantSearchTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: RestaurantRepository
    private lateinit var searchDao: SearchDao
    private var restaurantId: Long = 0

    private val clock = object : KhataClock {
        override fun now(): Long = 1_000L
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        searchDao = db.searchDao()
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

    private suspend fun seed() {
        restaurantId = repository.findOrCreate("Sultans Dine")
        repository.saveVisit(
            VisitDraft(restaurantId = restaurantId, visitedAt = 5_000L),
            dishes = listOf(DishDraft("Kacchi", 5), DishDraft("Borhani", 4)),
            companions = listOf("Rafi"),
        )
    }

    private suspend fun search(term: String): List<Long> =
        searchDao.idsMatching("restaurant", ftsQuery(term)!!)

    @Test
    fun `a dish name finds the restaurant, not a visit buried inside it`() = runTest {
        seed()
        // Searching "kacchi" should land you on the place, not on an evening.
        assertTrue(search("kacchi").contains(restaurantId))
    }

    @Test
    fun `a companion tag finds everywhere you went with them`() = runTest {
        seed()
        assertTrue(search("rafi").contains(restaurantId))
    }

    @Test
    fun `a visit added later is reflected without a rebuild`() = runTest {
        seed()
        repository.saveVisit(
            VisitDraft(restaurantId = restaurantId, visitedAt = 9_000L),
            dishes = listOf(DishDraft("Firni", 4)),
            companions = emptyList(),
        )

        assertTrue(search("firni").contains(restaurantId))
        // And the earlier visit is still in the same row: reindex rewrites the row
        // from the whole restaurant, it does not append to it.
        assertTrue(search("kacchi").contains(restaurantId))
    }

    @Test
    fun `a place name finds the restaurant`() = runTest {
        seed()
        val placeId = db.placeDao().upsert(
            PlaceEntity(uuid = "p-1", name = "Dhanmondi 27", createdAt = 1, updatedAt = 1),
        )
        repository.setDetails(restaurantId, placeId = placeId, note = null)

        assertTrue(search("dhanmondi").contains(restaurantId))
    }

    @Test
    fun `a Bengali restaurant name matches`() = runTest {
        // unicode61, the same tokenizer guard the ledger already carries.
        val id = repository.findOrCreate("সুলতান্স ডাইন")

        assertTrue(search("ডাইন").contains(id))
    }

    @Test
    fun `a rebuild reconstructs the same row from nothing`() = runTest {
        seed()
        val index = searchIndex(db)
        db.searchDao().deleteAll("restaurant")
        assertFalse(search("kacchi").contains(restaurantId))

        index.reindexAll()

        assertTrue(search("kacchi").contains(restaurantId))
        assertTrue(search("rafi").contains(restaurantId))
    }
}
