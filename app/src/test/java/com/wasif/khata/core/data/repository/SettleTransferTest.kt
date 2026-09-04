package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.time.KhataClock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A withdrawal is not a purchase. The money left one account and arrived in another,
 * and a ledger that forgets the second half is wrong by the whole amount.
 */
@RunWith(RobolectricTestRunner::class)
class SettleTransferTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: TransactionRepositoryImpl

    private val clock = object : KhataClock { override fun now(): Long = 5_000L }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TransactionRepositoryImpl(
            db, db.transactionDao(), db.accountDao(), db.merchantDao(), searchIndex(db), clock,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(name: String, opening: Long): Long =
        db.accountDao().upsert(
            AccountEntity(
                uuid = "acc-$name",
                name = name,
                type = AccountType.CASH,
                openingBalanceMinor = opening,
                currentBalanceMinor = opening,
                reportedBalanceMinor = null,
                reportedBalanceAt = null,
                includeInNetWorth = true,
                smsIdentifiers = "",
                createdAt = 1,
                updatedAt = 1,
            ),
        )

    private suspend fun parsedDebit(accountId: Long, amountMinor: Long, merchantRaw: String): Long =
        db.transactionDao().upsert(
            TransactionEntity(
                uuid = UUID.randomUUID().toString(),
                accountId = accountId,
                amountMinor = amountMinor,
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
                transferGroupId = null,
                feeMinor = null,
                referenceNumber = null,
                createdAt = 1_000,
                updatedAt = 1_000,
            ),
        )

    @Test
    fun `saying yes moves the money instead of losing it`() = runTest {
        // A 5,000 ATM withdrawal: it left EBL and arrived as cash. Marking it a
        // transfer without writing the other side would take 5,000 off net worth,
        // which is a worse bug than the one being fixed.
        val ebl = account("EBL", opening = 20_000_00)
        val cash = account("Cash", opening = 0)
        val id = parsedDebit(ebl, amountMinor = 5_000_00, merchantRaw = "EBL Account Transfer")

        repository.settleAsOwnTransfer(id, otherAccountId = cash).getOrThrow()

        val original = db.transactionDao().findById(id)!!
        val mirror = db.transactionDao().allForAccount(cash).single()
        assertEquals(TransactionKind.TRANSFER, original.kind)
        assertEquals(TransactionKind.TRANSFER, mirror.kind)
        assertEquals(original.transferGroupId, mirror.transferGroupId)
        assertNotNull(original.transferGroupId)
        assertEquals(TransactionDirection.CREDIT, mirror.direction)
        assertEquals(5_000_00L, mirror.amountMinor)
        // The money is in the other account, not gone.
        assertEquals(5_000_00L, db.accountDao().findById(cash)!!.currentBalanceMinor)
        assertFalse(original.transferReviewPending)
    }

    @Test
    fun `a settled transfer stops counting as spending`() = runTest {
        val ebl = account("EBL", opening = 20_000_00)
        val cash = account("Cash", opening = 0)
        val id = parsedDebit(ebl, amountMinor = 5_000_00, merchantRaw = "EBL Account Transfer")

        repository.settleAsOwnTransfer(id, otherAccountId = cash).getOrThrow()

        assertEquals(emptyMap<LocalDate, Money>(), repository.observeDayTotals().first())
    }

    @Test
    fun `saying no leaves it spending and creates nothing`() = runTest {
        val ebl = account("EBL", opening = 20_000_00)
        val id = parsedDebit(ebl, amountMinor = 5_000_00, merchantRaw = "EBL Account Transfer")
        db.transactionDao().setReviewPending(id, pending = true, updatedAt = 1_000)

        repository.dismissTransferReview(id).getOrThrow()

        val row = db.transactionDao().findById(id)!!
        assertFalse(row.transferReviewPending)
        assertEquals(TransactionKind.NORMAL, row.kind)
        assertNull(row.transferGroupId)
        assertEquals(1, db.transactionDao().allIdsForIndex().size)
    }

    @Test
    fun `settling the same row twice does not write two mirrors`() = runTest {
        // The notification and the in-app list are two doors to the same question,
        // and both can be open at once.
        val ebl = account("EBL", opening = 20_000_00)
        val cash = account("Cash", opening = 0)
        val id = parsedDebit(ebl, amountMinor = 5_000_00, merchantRaw = "EBL Account Transfer")

        repository.settleAsOwnTransfer(id, otherAccountId = cash).getOrThrow()
        repository.settleAsOwnTransfer(id, otherAccountId = cash).getOrThrow()

        assertEquals(1, db.transactionDao().allForAccount(cash).size)
        assertEquals(5_000_00L, db.accountDao().findById(cash)!!.currentBalanceMinor)
    }
}
