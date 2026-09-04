package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.repository.TransactionDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Money handed to a friend and money spent on dinner both leave the account. Only
 * one of them is gone.
 */
@RunWith(RobolectricTestRunner::class)
class OwedMoneyTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: TransactionRepositoryImpl
    private var accountId = 0L

    private val clock = object : KhataClock {
        override fun now(): Long = 5_000L
    }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TransactionRepositoryImpl(
            db, db.transactionDao(), db.accountDao(), db.merchantDao(), searchIndex(db), clock,
        )
        accountId = db.accountDao().upsert(
            AccountEntity(
                uuid = "cash", name = "Cash", type = AccountType.CASH,
                openingBalanceMinor = 0, currentBalanceMinor = 0,
                reportedBalanceMinor = null, reportedBalanceAt = null,
                includeInNetWorth = true, smsIdentifiers = "", createdAt = 1, updatedAt = 1,
            )
        )
    }

    @After
    fun tearDown() = db.close()

    private fun draft(
        amount: Long,
        direction: TransactionDirection,
        kind: TransactionKind,
        who: String? = null,
    ) = TransactionDraft(
        id = null,
        accountId = accountId,
        amount = Money(amount),
        direction = direction,
        occurredAt = 4_000L,
        merchantRaw = null,
        categoryId = null,
        note = null,
        counterparty = who,
        kind = kind,
    )

    private suspend fun spent(): Long =
        repository.observeSpentBetween(0, Long.MAX_VALUE).first().minor

    @Test
    fun `buying dinner is spending`() = runTest {
        repository.save(draft(50_000, TransactionDirection.DEBIT, TransactionKind.NORMAL)).getOrThrow()

        assertEquals(50_000L, spent())
    }

    @Test
    fun `lending a friend money is not spending`() = runTest {
        repository.save(
            draft(50_000, TransactionDirection.DEBIT, TransactionKind.LENT, who = "Rafi")
        ).getOrThrow()

        assertEquals(0L, spent())
    }

    @Test
    fun `paying back what you borrowed is not spending`() = runTest {
        repository.save(
            draft(50_000, TransactionDirection.DEBIT, TransactionKind.BORROWED_RETURNED, who = "Rafi")
        ).getOrThrow()

        assertEquals(0L, spent())
    }

    @Test
    fun `who the money is owed to is kept`() = runTest {
        val id = repository.save(
            draft(50_000, TransactionDirection.DEBIT, TransactionKind.LENT, who = "Rafi")
        ).getOrThrow()

        val saved = repository.observe(id).first()!!
        assertEquals("Rafi", saved.counterparty)
        assertEquals(TransactionKind.LENT, saved.kind)
    }

    @Test
    fun `a plain purchase needs no counterparty`() = runTest {
        val id = repository.save(draft(50_000, TransactionDirection.DEBIT, TransactionKind.NORMAL)).getOrThrow()

        assertNull(repository.observe(id).first()!!.counterparty)
    }

    @Test
    fun `every kind of owed money still moves the balance`() = runTest {
        // Not spending is not the same as not happening: the cash really left.
        repository.save(
            draft(50_000, TransactionDirection.DEBIT, TransactionKind.LENT, who = "Rafi")
        ).getOrThrow()

        assertEquals(-50_000L, db.accountDao().getAll().single().currentBalanceMinor)
    }

    @Test
    fun `getting the loan back restores the balance and is still not income`() = runTest {
        repository.save(draft(50_000, TransactionDirection.DEBIT, TransactionKind.LENT, "Rafi")).getOrThrow()
        repository.save(draft(50_000, TransactionDirection.CREDIT, TransactionKind.LENT_RETURNED, "Rafi")).getOrThrow()

        assertEquals(0L, db.accountDao().getAll().single().currentBalanceMinor)
        assertEquals(0L, spent())
    }

    @Test
    fun `resetting an account to zero writes a dated adjustment rather than editing the balance`() = runTest {
        repository.save(draft(162_100_00, TransactionDirection.CREDIT, TransactionKind.NORMAL)).getOrThrow()

        repository.resetToZero(accountId, at = 9_000L).getOrThrow()

        assertEquals(0L, db.accountDao().getAll().single().currentBalanceMinor)
        val writeOff = db.transactionDao().allActive().last()
        assertEquals(TransactionKind.ADJUSTMENT, writeOff.kind)
        assertEquals(TransactionDirection.DEBIT, writeOff.direction)
        assertEquals(9_000L, writeOff.occurredAt)
        // And it is not spending, or writing it off would look like a shopping spree.
        assertEquals(0L, spent())
    }

    @Test
    fun `resetting an account that is already zero writes nothing`() = runTest {
        assertNull(repository.resetToZero(accountId, at = 9_000L).getOrThrow())
        assertEquals(0, db.transactionDao().allActive().size)
    }
}
