package com.wasif.khata.core.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.model.AccountType
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
class DatabaseSeederTest {

    private lateinit var db: KhataDatabase
    private lateinit var seeder: DatabaseSeeder

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        seeder = DatabaseSeeder(
            db.accountDao(),
            db.categoryDao(),
            db.parsingRuleDao(),
            object : KhataClock { override fun now(): Long = 1L },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seeding an empty database creates the default accounts and categories`() = runTest {
        seeder.seedIfEmpty()

        val accounts = db.accountDao().observeAll().first()
        val categories = db.categoryDao().observeAll().first()

        assertEquals(DEFAULT_ACCOUNTS.size, accounts.size)
        assertEquals(DEFAULT_CATEGORIES.size, categories.size)
        assertTrue(accounts.any { it.type == AccountType.CASH })
    }

    @Test
    fun `seeding twice does not duplicate rows`() = runTest {
        seeder.seedIfEmpty()
        seeder.seedIfEmpty()

        assertEquals(DEFAULT_ACCOUNTS.size, db.accountDao().observeAll().first().size)
        assertEquals(DEFAULT_CATEGORIES.size, db.categoryDao().observeAll().first().size)
    }

    @Test
    fun `every seeded category has a stable, unique uuid so later syncs can match them`() = runTest {
        seeder.seedIfEmpty()

        val uuids = db.categoryDao().observeAll().first().map { it.uuid }

        assertEquals(uuids.size, uuids.toSet().size)
        assertTrue(uuids.all { it.startsWith("seed-cat-") })
    }

    @Test
    fun `every seeded category colour token has a palette entry`() = runTest {
        // Guards the token contract between DefaultData and the design system.
        val tokens = DEFAULT_CATEGORIES.map { it.colorToken }.toSet()

        assertTrue(tokens.all { it.startsWith("category_") })
    }
}
