package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: CategoryRepository
    private lateinit var budgets: BudgetRepository

    private val now = Instant.parse("2026-09-14T06:00:00Z").toEpochMilli()
    private val clock = object : KhataClock { override fun now(): Long = now }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        budgets = BudgetRepository(db.categoryBudgetDao(), clock)
        repository = CategoryRepository(db.categoryDao(), budgets, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedUncategorised(): Long = db.categoryDao().upsert(
        CategoryEntity(
            uuid = "seed-cat-uncategorized",
            name = "Uncategorized",
            icon = "help_outline",
            colorToken = "category_neutral",
            parentId = null,
            isSystem = true,
            createdAt = 1,
            updatedAt = 1,
        ),
    )

    @Test
    fun `a deleted category still labels the history it was used for`() = runTest {
        val id = repository.add("Car & Maintenance", "category_bronze")

        repository.delete(id)

        // The whole claim of the design: it stops being offered, it does not stop
        // being readable.
        assertNull(db.categoryDao().observeAll().first().firstOrNull { it.id == id })
        assertNotNull(
            db.categoryDao().observeAllIncludingDeleted().first().firstOrNull { it.id == id },
        )
    }

    @Test
    fun `Uncategorised cannot be deleted`() = runTest {
        val id = seedUncategorised()

        val deleted = repository.delete(id)

        assertFalse(deleted)
        assertNotNull(db.categoryDao().observeAll().first().firstOrNull { it.id == id })
    }

    @Test
    fun `an ordinary seeded category can be deleted`() = runTest {
        val id = db.categoryDao().upsert(
            CategoryEntity(
                uuid = "seed-cat-fuel",
                name = "Fuel",
                icon = "local_gas_station",
                colorToken = "category_slate",
                parentId = null,
                isSystem = false,
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        assertTrue(repository.delete(id))
    }

    @Test
    fun `renaming carries to the rows already filed under it`() = runTest {
        val id = repository.add("Eating Out", "category_orange")

        repository.rename(id, "Restaurants")

        // Transactions hold a categoryId, so a rename needs nothing else to follow.
        assertEquals("Restaurants", db.categoryDao().findById(id)!!.name)
    }

    @Test
    fun `deleting a category stops its budget counting forward`() = runTest {
        val id = repository.add("Fuel", "category_slate")
        budgets.setLimit(id, 500_00)

        repository.delete(id)

        // Left open, it would keep adding to the hub ring's total for a category
        // that is no longer offered.
        assertNull(budgets.limitFor(id, now.dhakaMonthStart()))
    }

    @Test
    fun `a new category is live, named and coloured as asked`() = runTest {
        val id = repository.add("Gym", "category_teal")

        val row = db.categoryDao().findById(id)!!
        assertEquals("Gym", row.name)
        assertEquals("category_teal", row.colorToken)
        assertFalse(row.isSystem)
        assertNull(row.deletedAt)
    }
}
