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
import com.wasif.khata.domain.repository.StatedBalance
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Starting over is the one operation that is allowed to disagree with the ledger,
 * so what it leaves behind has to be checkable: no transactions, and a balance that
 * came from a message rather than from adding those transactions up.
 */
@RunWith(RobolectricTestRunner::class)
class StartOverTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: TransactionRepositoryImpl
    private var bankId = 0L
    private var cashId = 0L

    private val clock = object : KhataClock { override fun now(): Long = 5_000L }

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
        bankId = account("bank", "EBL", AccountType.BANK, balance = 12_345)
        cashId = account("cash", "Cash", AccountType.CASH, balance = 99_999)
        transaction(bankId, 5_000)
        transaction(cashId, 2_500)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(uuid: String, name: String, type: AccountType, balance: Long) =
        db.accountDao().upsert(
            AccountEntity(
                uuid = uuid, name = name, type = type,
                openingBalanceMinor = 0, currentBalanceMinor = balance,
                reportedBalanceMinor = balance, reportedBalanceAt = 1_000L,
                unexplainedMinor = 7_777, includeInNetWorth = true, smsIdentifiers = "",
                createdAt = 1, updatedAt = 1,
            )
        )

    private suspend fun transaction(accountId: Long, minor: Long) = db.transactionDao().upsert(
        TransactionEntity(
            uuid = UUID.randomUUID().toString(),
            accountId = accountId,
            amountMinor = minor,
            direction = TransactionDirection.DEBIT,
            occurredAt = 2_000L,
            merchantRaw = "SHWAPNO",
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
            createdAt = 1,
            updatedAt = 1,
        )
    )

    @Test
    fun `every transaction goes and each balance comes from its own source`() = runTest {
        repository.startOver(
            statedBalances = mapOf(bankId to StatedBalance(minor = 80_000, at = 4_000L)),
            cashMinor = 1_500,
        ).getOrThrow()

        assertTrue(db.transactionDao().allActive().isEmpty())

        val bank = db.accountDao().findById(bankId)!!
        assertEquals(80_000L, bank.currentBalanceMinor)
        // Nothing is left for the balance to be the sum of, so it is also the
        // opening figure -- and the statement it came from, so an older message
        // cannot drag it back.
        assertEquals(80_000L, bank.openingBalanceMinor)
        assertEquals(4_000L, bank.reportedBalanceAt)
        assertEquals(0L, bank.unexplainedMinor)

        val cash = db.accountDao().findById(cashId)!!
        // Cash has no message; the figure is the one the person entered, as of now.
        assertEquals(1_500L, cash.currentBalanceMinor)
        assertEquals(5_000L, cash.reportedBalanceAt)
    }

    @Test
    fun `an account no message spoke for is zero rather than a guess`() = runTest {
        repository.startOver(statedBalances = emptyMap(), cashMinor = 0).getOrThrow()

        assertEquals(0L, db.accountDao().findById(bankId)!!.currentBalanceMinor)
    }

    @Test
    fun `setting a balance moves it by the difference, and says nothing moved when it did not`() =
        runTest {
            repository.setBalance(cashId, targetMinor = 40_000, at = 9_000L).getOrThrow()
            assertEquals(40_000L, db.accountDao().findById(cashId)!!.currentBalanceMinor)

            // The adjustment is a row in the ledger, not an edited balance.
            val adjustment = db.transactionDao().allActive().single { it.accountId == cashId && it.kind == TransactionKind.ADJUSTMENT }
            assertEquals(Money(59_999).minor, adjustment.amountMinor)
            assertEquals(TransactionDirection.DEBIT, adjustment.direction)

            assertNull(repository.setBalance(cashId, targetMinor = 40_000, at = 9_000L).getOrThrow())
        }
}
