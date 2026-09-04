package com.wasif.khata.core.tag

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.dao.SearchDao
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagRepositoryTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: TagRepository
    private lateinit var searchDao: SearchDao

    private val clock = object : KhataClock {
        override fun now(): Long = 1_000L
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        searchDao = db.searchDao()
        repository = TagRepository(db.tagDao(), searchIndex(db), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertTransaction(): Long = db.transactionDao().upsert(
        TransactionEntity(
            uuid = UUID.randomUUID().toString(),
            accountId = 1,
            amountMinor = 8560,
            direction = TransactionDirection.DEBIT,
            occurredAt = 1000,
            merchantRaw = "Sultans Dine",
            merchantId = null,
            categoryId = null,
            note = null,
            counterparty = null,
            source = TransactionSource.MANUAL,
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

    @Test
    fun `a differently cased name returns the same tag`() = runTest {
        // Without this, autocomplete offers three spellings of one person and
        // "everywhere I went with Rafi" quietly returns a third of the answer.
        val first = repository.findOrCreate("Rafi")
        assertEquals(first, repository.findOrCreate("rafi"))
        assertEquals(first, repository.findOrCreate("RAFI"))
    }

    @Test
    fun `attaching twice leaves one link`() = runTest {
        val tag = repository.findOrCreate("Rafi")
        repository.attach(tag, "restaurant_visit", 1)
        repository.attach(tag, "restaurant_visit", 1)

        assertEquals(1, repository.tagsFor("restaurant_visit", 1).size)
    }

    @Test
    fun `attaching a tag reindexes what it was attached to`() = runTest {
        // This is the whole of tag search: names are part of the entity's indexed
        // text, so there is no separate tag-search path and no join at query time.
        val txId = insertTransaction()
        repository.attach(repository.findOrCreate("Rafi"), "transaction", txId)

        assertTrue(searchDao.idsMatching("transaction", "rafi*").contains(txId))
    }

    @Test
    fun `detaching a tag takes it back out of the index`() = runTest {
        val txId = insertTransaction()
        val tag = repository.findOrCreate("Rafi")
        repository.attach(tag, "transaction", txId)
        repository.detach(tag, "transaction", txId)

        assertFalse(searchDao.idsMatching("transaction", "rafi*").contains(txId))
        // The transaction itself is still findable; only the tag went away.
        assertTrue(searchDao.idsMatching("transaction", "sultans*").contains(txId))
    }
}
