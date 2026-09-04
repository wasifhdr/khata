package com.wasif.khata.core.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.MerchantAliasEntity
import com.wasif.khata.core.data.entity.MerchantEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The three things the LIKE query missed, plus the tokenizer guard. Each inserts a
 * row, indexes it, and searches for something the old query had the data for and
 * still could not find.
 */
@RunWith(RobolectricTestRunner::class)
class SearchIndexTest {

    private lateinit var db: KhataDatabase
    private lateinit var index: SearchIndex

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        index = SearchIndex(
            sources = setOf(
                TransactionIndexSource(db.transactionDao(), db.merchantDao(), db.tagDao()),
            ),
            dao = db.searchDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertTransaction(
        merchantRaw: String? = null,
        note: String? = null,
        counterparty: String? = null,
        merchantId: Long? = null,
    ): Long = db.transactionDao().upsert(
        TransactionEntity(
            uuid = UUID.randomUUID().toString(),
            accountId = 1,
            amountMinor = 8560,
            direction = TransactionDirection.DEBIT,
            occurredAt = 1000,
            merchantRaw = merchantRaw,
            merchantId = merchantId,
            categoryId = null,
            note = note,
            counterparty = counterparty,
            source = TransactionSource.SMS,
            confidence = Confidence.HIGH,
            kind = TransactionKind.NORMAL,
            rawMessageId = null,
            transferGroupId = null,
            feeMinor = null,
            referenceNumber = null,
            createdAt = 1000,
            updatedAt = 1000,
        ),
    )

    private suspend fun search(term: String): List<Long> =
        db.searchDao().idsMatching("transaction", ftsQuery(term)!!)

    @Test
    fun `a merchant alias finds the transaction`() = runTest {
        val merchantId = db.merchantDao().upsert(
            MerchantEntity(
                uuid = UUID.randomUUID().toString(),
                canonicalName = "Foodpanda",
                categoryId = null,
                placeId = null,
                isUserConfirmed = true,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        db.merchantDao().upsertAlias(
            MerchantAliasEntity(
                uuid = UUID.randomUUID().toString(),
                merchantId = merchantId,
                rawText = "FP*8823",
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        // The raw text says FP*8823 and nothing else; only the resolved name and its
        // aliases can answer "foodpanda", and the LIKE query saw neither.
        val id = insertTransaction(merchantRaw = "FP*8823", merchantId = merchantId)
        index.reindex("transaction", id)

        assertTrue(search("foodpanda").contains(id))
    }

    @Test
    fun `a multi-word query does not require adjacency`() = runTest {
        val id = insertTransaction(merchantRaw = "Sultans Dine Dhanmondi")
        index.reindex("transaction", id)

        // LIKE '%sultans dhanmondi%' matches nothing here: the words are not
        // adjacent, and the old query could only look for one contiguous run.
        assertTrue(search("sultans dhanmondi").contains(id))
    }

    @Test
    fun `counterparty is searchable`() = runTest {
        // The owed-money feature writes names there, and the LIKE query read only
        // merchantRaw and note.
        val id = insertTransaction(merchantRaw = "Cash", counterparty = "Rafi")
        index.reindex("transaction", id)

        assertTrue(search("rafi").contains(id))
    }

    @Test
    fun `a Bengali term matches`() = runTest {
        // Fails under the simple tokenizer, which is ASCII-only. This is what pins
        // unicode61 in place.
        val id = insertTransaction(merchantRaw = "সুলতান্স ডাইন")
        index.reindex("transaction", id)

        assertTrue(search("ডাইন").contains(id))
    }

    @Test
    fun `reindexing twice leaves one row, not two`() = runTest {
        // FTS4 has no upsert, so reindex deletes before inserting. One row per entity
        // is what keeps the ledger join from returning a transaction twice.
        val id = insertTransaction(merchantRaw = "Shwapno")
        index.reindex("transaction", id)
        index.reindex("transaction", id)

        assertTrue(search("shwapno") == listOf(id))
    }
}
