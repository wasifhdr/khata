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
            db, db.transactionDao(), db.accountDao(), db.merchantDao(),
            db.balanceSnapshotDao(), db.tagDao(), db.mediaDao(), searchIndex(db), clock,
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
        kind: TransactionKind = TransactionKind.NORMAL,
        who: String? = null,
        owed: Long = if (who != null || kind == TransactionKind.IOU) amount else 0L,
        id: Long? = null,
    ) = TransactionDraft(
        id = id,
        accountId = accountId,
        amount = Money(amount),
        direction = direction,
        occurredAt = 4_000L,
        merchantRaw = null,
        categoryId = null,
        note = null,
        counterparty = who,
        owed = Money(owed),
        kind = kind,
    )

    private suspend fun spent(): Long =
        repository.observeSpentBetween(0, Long.MAX_VALUE).first().minor

    @Test
    fun `buying dinner is spending`() = runTest {
        repository.save(draft(50_000, TransactionDirection.DEBIT)).getOrThrow()

        assertEquals(50_000L, spent())
    }

    @Test
    fun `lending a friend money is not spending`() = runTest {
        repository.save(
            draft(50_000, TransactionDirection.DEBIT, who = "Rafi")
        ).getOrThrow()

        assertEquals(0L, spent())
    }

    @Test
    fun `paying back what you borrowed is not spending`() = runTest {
        repository.save(
            draft(50_000, TransactionDirection.DEBIT, who = "Rafi")
        ).getOrThrow()

        assertEquals(0L, spent())
    }

    @Test
    fun `who the money is owed to and how much is kept`() = runTest {
        val id = repository.save(
            draft(50_000, TransactionDirection.DEBIT, who = "Rafi")
        ).getOrThrow()

        val saved = repository.observe(id).first()!!
        assertEquals("Rafi", saved.counterparty)
        assertEquals(Money(50_000), saved.owed)
        assertEquals(TransactionKind.NORMAL, saved.kind)
    }

    @Test
    fun `a plain purchase needs no counterparty`() = runTest {
        val id = repository.save(draft(50_000, TransactionDirection.DEBIT)).getOrThrow()

        assertNull(repository.observe(id).first()!!.counterparty)
        assertEquals(Money.ZERO, repository.observe(id).first()!!.owed)
    }

    @Test
    fun `owed money from an account still moves the balance`() = runTest {
        repository.save(
            draft(50_000, TransactionDirection.DEBIT, who = "Rafi")
        ).getOrThrow()

        assertEquals(-50_000L, db.accountDao().getAll().single().currentBalanceMinor)
    }

    @Test
    fun `getting the loan back restores the balance and is still not income`() = runTest {
        repository.save(draft(50_000, TransactionDirection.DEBIT, who = "Rafi")).getOrThrow()
        repository.save(draft(50_000, TransactionDirection.CREDIT, who = "Rafi")).getOrThrow()

        assertEquals(0L, db.accountDao().getAll().single().currentBalanceMinor)
        assertEquals(0L, spent())
        assertEquals(0L, repository.observeReceivedBetween(0, Long.MAX_VALUE).first().minor)
    }

    @Test
    fun `reclassifying an existing NORMAL row to owed updates owedMinor and removes it from spending`() = runTest {
        val id = repository.save(draft(50_000, TransactionDirection.DEBIT)).getOrThrow()
        assertEquals(50_000L, spent())

        repository.save(
            draft(50_000, TransactionDirection.DEBIT, who = "Rafi", owed = 50_000, id = id)
        ).getOrThrow()

        assertEquals(0L, spent())
        val rafi = db.transactionDao().observeOwedByPerson().first().single()
        assertEquals("Rafi", rafi.name)
        assertEquals(50_000L, rafi.netMinor)
    }

    @Test
    fun `splitting a purchase deducts full balance, counts your share as spending, and puts their share on Owed`() = runTest {
        repository.save(
            draft(100_000, TransactionDirection.DEBIT, who = "Rafi", owed = 40_000)
        ).getOrThrow()

        assertEquals(-100_000L, db.accountDao().getAll().single().currentBalanceMinor)
        assertEquals(60_000L, spent())
        val rafi = db.transactionDao().observeOwedByPerson().first().single()
        assertEquals("Rafi", rafi.name)
        assertEquals(40_000L, rafi.netMinor)
    }

    @Test
    fun `an IOU leaves account balance untouched, counts as spending when incurred, and settles cleanly`() = runTest {
        val iouId = repository.save(
            draft(50_000, TransactionDirection.DEBIT, kind = TransactionKind.IOU, who = "Rafi", owed = 50_000)
        ).getOrThrow()

        assertEquals(0L, db.accountDao().getAll().single().currentBalanceMinor)
        assertEquals(50_000L, spent())
        assertEquals(-50_000L, db.transactionDao().observeOwedByPerson().first().single().netMinor)

        // Settling it by paying Rafi from Cash moves the account balance without double-counting spending.
        repository.save(
            draft(50_000, TransactionDirection.DEBIT, kind = TransactionKind.NORMAL, who = "Rafi", owed = 50_000)
        ).getOrThrow()

        assertEquals(-50_000L, db.accountDao().getAll().single().currentBalanceMinor)
        assertEquals(50_000L, spent())
        assertEquals(0, db.transactionDao().observeOwedByPerson().first().size)

        // Deleting an IOU row must also leave the account balance untouched.
        repository.delete(iouId).getOrThrow()
        assertEquals(-50_000L, db.accountDao().getAll().single().currentBalanceMinor)
    }

    @Test
    fun `resetting an account to zero writes a dated adjustment rather than editing the balance`() = runTest {
        repository.save(draft(162_100_00, TransactionDirection.CREDIT)).getOrThrow()

        repository.setBalance(accountId, targetMinor = 0L, at = 9_000L).getOrThrow()

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
        assertNull(repository.setBalance(accountId, targetMinor = 0L, at = 9_000L).getOrThrow())
        assertEquals(0, db.transactionDao().allActive().size)
    }
}
