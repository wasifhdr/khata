package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BudgetRepositoryTest {

    private lateinit var db: KhataDatabase

    private val march = Instant.parse("2026-03-14T06:00:00Z").toEpochMilli()
    private val september = Instant.parse("2026-09-14T06:00:00Z").toEpochMilli()

    private var nowMillis = march
    private val clock = object : KhataClock { override fun now(): Long = nowMillis }

    private fun repository() = BudgetRepository(db.categoryBudgetDao(), clock)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a limit set mid-month applies to the whole of that month`() = runTest {
        nowMillis = march
        repository().setLimit(categoryId = 1, limitMinor = 500_00)

        // "My March budget" means March, not the fortnight after the 14th.
        assertEquals(500_00L, repository().limitFor(1, march.dhakaMonthStart()))
    }

    @Test
    fun `changing a limit later leaves the earlier month reading its own number`() = runTest {
        nowMillis = march
        repository().setLimit(categoryId = 1, limitMinor = 500_00)

        nowMillis = september
        repository().setLimit(categoryId = 1, limitMinor = 300_00)

        // This is the whole reason the table is versioned: March must not become a
        // month you overspent because September is stricter.
        assertEquals(500_00L, repository().limitFor(1, march.dhakaMonthStart()))
        assertEquals(300_00L, repository().limitFor(1, september.dhakaMonthStart()))
    }

    @Test
    fun `revising within the same month replaces rather than stacks`() = runTest {
        nowMillis = march
        val repo = repository()
        repo.setLimit(categoryId = 1, limitMinor = 500_00)
        repo.setLimit(categoryId = 1, limitMinor = 650_00)

        assertEquals(650_00L, repo.limitFor(1, march.dhakaMonthStart()))
        // One row, not two overlapping ones -- "which applies" must stay a lookup.
        assertEquals(1, db.categoryBudgetDao().allFor(1).size)
    }

    @Test
    fun `an unset category has no limit, which is not a limit of zero`() = runTest {
        // Zero would read as "you may spend nothing" and show every category as over
        // budget the moment it is used.
        assertNull(repository().limitFor(categoryId = 99, march.dhakaMonthStart()))
    }

    @Test
    fun `clearing a limit closes it without erasing what it was`() = runTest {
        nowMillis = march
        repository().setLimit(categoryId = 1, limitMinor = 500_00)

        nowMillis = september
        repository().setLimit(categoryId = 1, limitMinor = null)

        assertNull(repository().limitFor(1, september.dhakaMonthStart()))
        // March still knows what it was judged against.
        assertEquals(500_00L, repository().limitFor(1, march.dhakaMonthStart()))
    }

    @Test
    fun `the month's limits come back keyed by category`() = runTest {
        nowMillis = march
        val repo = repository()
        repo.setLimit(categoryId = 1, limitMinor = 500_00)
        repo.setLimit(categoryId = 2, limitMinor = 250_00)

        val limits = repo.observeLimitsForMonth(march.dhakaMonthStart()).first()

        assertEquals(mapOf(1L to 500_00L, 2L to 250_00L), limits)
    }
}
