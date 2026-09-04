package com.wasif.khata.core.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.notify.TransferNotifier
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferReviewWorkerTest {

    private lateinit var db: KhataDatabase
    private lateinit var review: TransferReview

    private val clock = object : KhataClock { override fun now(): Long = 9_000L }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        review = TransferReview(
            db.transactionDao(),
            // The real one: Robolectric's notification manager is a shadow, so this
            // posts nowhere and the test still exercises the path that posts.
            TransferNotifier(ApplicationProvider.getApplicationContext()),
            clock,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(
        merchantRaw: String?,
        transferGroupId: String? = null,
    ): Long = db.transactionDao().upsert(
        TransactionEntity(
            uuid = UUID.randomUUID().toString(),
            accountId = 1,
            amountMinor = 500_000,
            direction = TransactionDirection.DEBIT,
            occurredAt = 1_000,
            merchantRaw = merchantRaw,
            merchantId = null,
            categoryId = null,
            note = null,
            counterparty = null,
            source = TransactionSource.SMS,
            confidence = Confidence.HIGH,
            kind = TransactionKind.NORMAL,
            rawMessageId = null,
            transferGroupId = transferGroupId,
            feeMinor = null,
            referenceNumber = null,
            createdAt = 1_000,
            updatedAt = 1_000,
        ),
    )

    @Test
    fun `a transfer-shaped row with no partner asks`() = runTest {
        val id = insert("EBL Account Transfer")

        review.reviewIfUnpaired(id)

        assertTrue(db.transactionDao().findById(id)!!.transferReviewPending)
    }

    @Test
    fun `a row that paired in the meantime asks nothing`() = runTest {
        // The partner message arrived inside the three minutes, so pairing already
        // answered the question and nothing should interrupt anybody.
        val id = insert("EBL Account Transfer", transferGroupId = "group-1")

        review.reviewIfUnpaired(id)

        assertFalse(db.transactionDao().findById(id)!!.transferReviewPending)
    }

    @Test
    fun `an ordinary purchase asks nothing`() = runTest {
        val id = insert("UBER BANGLADESH LTD-UBER")

        review.reviewIfUnpaired(id)

        assertFalse(db.transactionDao().findById(id)!!.transferReviewPending)
    }

    @Test
    fun `a deleted row asks nothing`() = runTest {
        val id = insert("EBL Account Transfer")
        db.transactionDao().softDelete(id, 2_000)

        review.reviewIfUnpaired(id)

        assertFalse(db.transactionDao().findById(id)?.transferReviewPending ?: false)
    }
}
