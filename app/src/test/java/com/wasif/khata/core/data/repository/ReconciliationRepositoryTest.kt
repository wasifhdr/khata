package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
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
class ReconciliationRepositoryTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: ReconciliationRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ReconciliationRepository(db.accountDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(
        name: String,
        current: Long,
        reported: Long?,
        reportedAt: Long?,
        unexplained: Long = 0,
    ) = db.accountDao().upsert(
        AccountEntity(
            uuid = "acc-$name",
            name = name,
            type = AccountType.BANK,
            openingBalanceMinor = 0,
            currentBalanceMinor = current,
            reportedBalanceMinor = reported,
            reportedBalanceAt = reportedAt,
            unexplainedMinor = unexplained,
            includeInNetWorth = true,
            smsIdentifiers = "",
            createdAt = 1,
            updatedAt = 1,
        )
    )

    @Test
    fun `an account whose transactions account for every taka reports nothing`() = runTest {
        account("Matching", current = 5000, reported = 5000, reportedAt = 100, unexplained = 0)

        assertEquals(emptyList<BalanceDrift>(), repository.observeDrift().first())
    }

    @Test
    fun `money that arrived with no message is reported against the account`() = runTest {
        // The balance is the bank's own figure and is not in question. What is
        // reported is how much of it the recorded transactions cannot account for.
        account("Drifting", current = 5000, reported = 5000, reportedAt = 100, unexplained = 340)

        val drift = repository.observeDrift().first().single()

        assertEquals("Drifting", drift.accountName)
        assertEquals(Money(340), drift.gap)
        assertEquals(Money(5000), drift.reported)
        assertEquals("what the transactions do add up to", Money(4660), drift.computed)
        assertEquals(100L, drift.reportedAt)
    }

    @Test
    fun `a negative gap means money left with no message`() = runTest {
        account("Overcounted", current = 4660, reported = 4660, reportedAt = 100, unexplained = -340)

        assertEquals(Money(-340), repository.observeDrift().first().single().gap)
    }

    @Test
    fun `an account with no reported balance never drifts`() = runTest {
        account("Manual", current = 9999, reported = null, reportedAt = null)

        assertTrue(repository.observeDrift().first().isEmpty())
    }
}
