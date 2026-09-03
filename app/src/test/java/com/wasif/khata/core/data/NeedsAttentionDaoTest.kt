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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NeedsAttentionDaoTest {

    private lateinit var db: KhataDatabase

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        db.accountDao().upsert(
            AccountEntity(
                uuid = "acc", name = "bKash", type = AccountType.MFS,
                openingBalanceMinor = 0, currentBalanceMinor = 0,
                reportedBalanceMinor = null, reportedBalanceAt = null,
                includeInNetWorth = true, smsIdentifiers = "bKash",
                createdAt = 1, updatedAt = 1,
            )
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(uuid: String, confidence: Confidence, merchant: String = "SHOP") =
        db.transactionDao().upsert(
            TransactionEntity(
                uuid = uuid, accountId = 1, amountMinor = 100,
                direction = TransactionDirection.DEBIT, occurredAt = 1000,
                merchantRaw = merchant, merchantId = null, categoryId = null, note = null,
                source = TransactionSource.SMS, confidence = confidence,
                rawMessageId = null, transferGroupId = null, feeMinor = null,
                referenceNumber = null, createdAt = 1, updatedAt = 1,
            )
        )

    private suspend fun uuids(source: PagingSource<Int, TransactionEntity>): List<String> {
        val page = TestPager(PagingConfig(pageSize = 20), source).refresh() as PagingSource.LoadResult.Page
        return page.data.map { it.uuid }
    }

    @Test
    fun `needs attention includes MEDIUM, not only LOW`() = runTest {
        insert("high", Confidence.HIGH)
        insert("medium", Confidence.MEDIUM)
        insert("low", Confidence.LOW)

        // MEDIUM is what the pipeline records for every newly-seen merchant, which is
        // the common case for an SMS transaction. Excluding it leaves most rows unmarked.
        assertEquals(setOf("medium", "low"), uuids(db.transactionDao().pagingSourceNeedsAttention()).toSet())
    }

    @Test
    fun `needs attention excludes soft-deleted rows`() = runTest {
        val id = insert("medium", Confidence.MEDIUM)
        db.transactionDao().softDelete(id, deletedAt = 5000)

        assertEquals(emptyList<String>(), uuids(db.transactionDao().pagingSourceNeedsAttention()))
    }

    @Test
    fun `the needs-attention count matches the query`() = runTest {
        insert("high", Confidence.HIGH)
        insert("medium", Confidence.MEDIUM)
        insert("low", Confidence.LOW)

        assertEquals(2, db.transactionDao().observeNeedsAttentionCount().first())
    }

    @Test
    fun `counting ignores soft-deleted rows`() = runTest {
        val id = insert("medium", Confidence.MEDIUM)
        db.transactionDao().softDelete(id, deletedAt = 5000)

        assertEquals(0, db.transactionDao().observeNeedsAttentionCount().first())
    }
}
