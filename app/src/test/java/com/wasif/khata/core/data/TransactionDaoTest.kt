package com.wasif.khata.core.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
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
class TransactionDaoTest {

    private lateinit var db: KhataDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertAccount(): Long = db.accountDao().upsert(
        AccountEntity(
            uuid = "acc-1",
            name = "bKash",
            type = AccountType.MFS,
            openingBalanceMinor = 0,
            currentBalanceMinor = 0,
            reportedBalanceMinor = null,
            reportedBalanceAt = null,
            includeInNetWorth = true,
            smsIdentifiers = "bKash",
            createdAt = 1000,
            updatedAt = 1000,
        )
    )

    private fun transaction(accountId: Long, occurredAt: Long, uuid: String) =
        TransactionEntity(
            uuid = uuid,
            accountId = accountId,
            amountMinor = 10_000,
            direction = TransactionDirection.DEBIT,
            occurredAt = occurredAt,
            merchantRaw = "SHWAPNO",
            merchantId = null,
            categoryId = null,
            note = null,
            source = TransactionSource.MANUAL,
            confidence = Confidence.HIGH,
            rawMessageId = null,
            transferGroupId = null,
            feeMinor = null,
            referenceNumber = null,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        )

    private suspend fun pageUuids(): List<String> {
        val pager = TestPager(PagingConfig(pageSize = 10), db.transactionDao().pagingSource())
        val page = pager.refresh() as PagingSource.LoadResult.Page
        return page.data.map { it.uuid }
    }

    @Test
    fun `paging source returns transactions newest first`() = runTest {
        val accountId = insertAccount()
        db.transactionDao().upsert(transaction(accountId, occurredAt = 1000, uuid = "t-old"))
        db.transactionDao().upsert(transaction(accountId, occurredAt = 3000, uuid = "t-new"))
        db.transactionDao().upsert(transaction(accountId, occurredAt = 2000, uuid = "t-mid"))

        assertEquals(listOf("t-new", "t-mid", "t-old"), pageUuids())
    }

    @Test
    fun `transactions sharing a timestamp get a stable order`() = runTest {
        val accountId = insertAccount()
        val firstId = db.transactionDao().upsert(transaction(accountId, occurredAt = 5000, uuid = "t-a"))
        val secondId = db.transactionDao().upsert(transaction(accountId, occurredAt = 5000, uuid = "t-b"))

        // Higher rowid first, so backfilled batches never shuffle between page loads.
        assertEquals(listOf("t-b", "t-a"), pageUuids())
        assertEquals(true, secondId > firstId)
    }

    @Test
    fun `soft deleted transactions are excluded from paging`() = runTest {
        val accountId = insertAccount()
        val id = db.transactionDao().upsert(transaction(accountId, occurredAt = 1000, uuid = "t-1"))
        db.transactionDao().upsert(transaction(accountId, occurredAt = 2000, uuid = "t-2"))

        db.transactionDao().softDelete(id, deletedAt = 9999)

        assertEquals(listOf("t-2"), pageUuids())
    }

    @Test
    fun `observeById emits null after soft delete`() = runTest {
        val accountId = insertAccount()
        val id = db.transactionDao().upsert(transaction(accountId, occurredAt = 1000, uuid = "t-1"))

        assertEquals("t-1", db.transactionDao().observeById(id).first()?.uuid)

        db.transactionDao().softDelete(id, deletedAt = 9999)
        assertNull(db.transactionDao().observeById(id).first())
    }

    @Test
    fun `adjustBalance applies a signed delta to the account`() = runTest {
        val accountId = insertAccount()

        db.accountDao().adjustBalance(accountId, deltaMinor = -5000, updatedAt = 2000)
        assertEquals(-5000, db.accountDao().observeAll().first().single().currentBalanceMinor)

        db.accountDao().adjustBalance(accountId, deltaMinor = 8000, updatedAt = 3000)
        assertEquals(3000, db.accountDao().observeAll().first().single().currentBalanceMinor)
    }
}
