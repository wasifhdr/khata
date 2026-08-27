package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.error.DataError
import com.wasif.khata.domain.repository.TransactionDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionRepositoryImplTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: TransactionRepositoryImpl

    private val clock = object : KhataClock {
        override fun now(): Long = 5_000L
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TransactionRepositoryImpl(db.transactionDao(), db.accountDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(opening: Long = 0L): Long = db.accountDao().upsert(
        AccountEntity(
            uuid = "acc-1",
            name = "bKash",
            type = AccountType.MFS,
            openingBalanceMinor = opening,
            currentBalanceMinor = opening,
            reportedBalanceMinor = null,
            reportedBalanceAt = null,
            includeInNetWorth = true,
            smsIdentifiers = "bKash",
            createdAt = 1000,
            updatedAt = 1000,
        )
    )

    private suspend fun balance(): Long =
        db.accountDao().observeAll().first().single().currentBalanceMinor

    private fun draft(accountId: Long, amount: Money, direction: TransactionDirection) =
        TransactionDraft(
            id = null,
            accountId = accountId,
            amount = amount,
            direction = direction,
            occurredAt = 4_000L,
            merchantRaw = "SHWAPNO",
            categoryId = null,
            note = null,
        )

    @Test
    fun `saving a debit decreases the account balance`() = runTest {
        val accountId = account(opening = 100_000)

        repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT))

        assertEquals(75_000, balance())
    }

    @Test
    fun `saving a credit increases the account balance`() = runTest {
        val accountId = account(opening = 100_000)

        repository.save(draft(accountId, Money(25_000), TransactionDirection.CREDIT))

        assertEquals(125_000, balance())
    }

    @Test
    fun `editing an amount reverses the old effect before applying the new one`() = runTest {
        val accountId = account(opening = 100_000)
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        repository.save(draft(accountId, Money(10_000), TransactionDirection.DEBIT).copy(id = id))

        // 100,000 - 10,000. Not 100,000 - 25,000 - 10,000.
        assertEquals(90_000, balance())
    }

    @Test
    fun `editing a direction reverses the old effect before applying the new one`() = runTest {
        val accountId = account(opening = 100_000)
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        repository.save(draft(accountId, Money(25_000), TransactionDirection.CREDIT).copy(id = id))

        assertEquals(125_000, balance())
    }

    @Test
    fun `editing does not create a second row`() = runTest {
        val accountId = account(opening = 100_000)
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        val sameId = repository.save(
            draft(accountId, Money(10_000), TransactionDirection.DEBIT).copy(id = id)
        ).getOrThrow()

        assertEquals(id, sameId)
    }

    @Test
    fun `deleting a transaction restores the account balance`() = runTest {
        val accountId = account(opening = 100_000)
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        repository.delete(id)

        assertEquals(100_000, balance())
        assertNull(repository.observe(id).first())
    }

    @Test
    fun `deleting a missing transaction fails with NotFound rather than throwing`() = runTest {
        val result = repository.delete(9_999)

        assertTrue(result.isFailure)
        assertEquals(DataError.NotFound, result.exceptionOrNull())
    }

    @Test
    fun `saving against a missing transaction id fails with NotFound`() = runTest {
        val accountId = account()

        val result = repository.save(
            draft(accountId, Money(1_000), TransactionDirection.DEBIT).copy(id = 9_999)
        )

        assertTrue(result.isFailure)
        assertEquals(DataError.NotFound, result.exceptionOrNull())
    }

    @Test
    fun `saved transactions map back to domain models with Money amounts`() = runTest {
        val accountId = account()
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        val saved = repository.observe(id).first()

        assertEquals(Money(25_000), saved?.amount)
        assertEquals("SHWAPNO", saved?.merchantRaw)
        assertEquals(5_000L, saved?.updatedAt)
    }
}
