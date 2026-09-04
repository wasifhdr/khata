package com.wasif.khata.core.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.notify.TransferNotifier
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ATM_WITHDRAWAL =
    "Cash WD BDT500 from EBL UTTARA LAKE DRIV. Card 452017**9386 on 24-Jul-25 04:49:02 PM BST." +
        "Your A/C 115***352 Balance BDT 3000.00. EBL Helpline 16230"

private const val BKASH_CASH_OUT =
    "Cash Out Tk 100.00 to 01700000001 successful. Fee Tk 1.85. Balance Tk 108.85. " +
        "TrxID 8BN6NN8P8O at 23/02/2021 16:12"

private const val BKASH_PAYMENT =
    "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. " +
        "TrxID DHV41FIPGY at 31/08/2026 19:01"

/**
 * Cash out of an ATM is the same money in a different pocket. It used to debit the
 * bank and arrive nowhere: on a real phone, 31 withdrawals worth Tk 162,100 left the
 * ledger entirely, and every one was counted as money spent.
 */
@RunWith(RobolectricTestRunner::class)
class CashWithdrawalTest {

    private lateinit var db: KhataDatabase
    private lateinit var pipeline: IngestionPipeline

    private val clock = object : KhataClock {
        override fun now(): Long = 9_000L
    }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        DatabaseSeeder(db.accountDao(), db.categoryDao(), db.parsingRuleDao(), clock).seedIfEmpty()
        pipeline = IngestionPipeline(
            db = db,
            rawMessageDao = db.rawMessageDao(),
            parsingRuleDao = db.parsingRuleDao(),
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            merchantDao = db.merchantDao(),
            engine = RuleEngine(),
            pairing = TransferPairing(
                db.transactionDao(),
                TransferNotifier(ApplicationProvider.getApplicationContext()),
                clock,
            ),
            clock = clock,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun cash() = db.accountDao().getAll().first { it.type == AccountType.CASH }

    @Test
    fun `an ATM withdrawal puts the money in the cash account`() = runTest {
        pipeline.ingest("EBL", ATM_WITHDRAWAL, receivedAt = 1000)

        assertEquals(50000L, cash().currentBalanceMinor)
        val credit = db.transactionDao().allActive().single { it.accountId == cash().id }
        assertEquals(TransactionDirection.CREDIT, credit.direction)
        assertEquals(50000L, credit.amountMinor)
    }

    @Test
    fun `a bKash cash out does the same`() = runTest {
        pipeline.ingest("bKash", BKASH_CASH_OUT, receivedAt = 1000)

        assertEquals(10000L, cash().currentBalanceMinor)
    }

    @Test
    fun `the two halves are paired, so neither reads as spending`() = runTest {
        pipeline.ingest("EBL", ATM_WITHDRAWAL, receivedAt = 1000)

        val all = db.transactionDao().allActive()
        assertEquals(2, all.size)
        val groups = all.mapNotNull { it.transferGroupId }.toSet()
        assertEquals("both halves belong to one transfer", 1, groups.size)
        assertTrue(all.all { it.transferGroupId != null })
    }

    @Test
    fun `withdrawing cash is not spending`() = runTest {
        pipeline.ingest("EBL", ATM_WITHDRAWAL, receivedAt = 1000)

        val spent = db.transactionDao()
            .observeTotalMinorBetween(TransactionDirection.DEBIT, 0, Long.MAX_VALUE).first()
        assertEquals(0L, spent)
    }

    @Test
    fun `buying something still is`() = runTest {
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)

        val spent = db.transactionDao()
            .observeTotalMinorBetween(TransactionDirection.DEBIT, 0, Long.MAX_VALUE).first()
        assertEquals(85600L, spent)
    }

    @Test
    fun `re-reading the same withdrawal does not pay the cash in twice`() = runTest {
        pipeline.ingest("EBL", ATM_WITHDRAWAL, receivedAt = 1000)
        val raw = db.rawMessageDao().allForReparse().single()

        pipeline.process(raw.id, raw.sender, raw.body, raw.receivedAt)
        pipeline.process(raw.id, raw.sender, raw.body, raw.receivedAt)

        assertEquals(2, db.transactionDao().allActive().size)
        assertEquals(50000L, cash().currentBalanceMinor)
    }

    @Test
    fun `the cash row says where the money came from`() = runTest {
        pipeline.ingest("EBL", ATM_WITHDRAWAL, receivedAt = 1000)

        val credit = db.transactionDao().allActive().single { it.accountId == cash().id }
        assertNotNull(credit.note)
        assertTrue("expected the source account in: ${credit.note}", credit.note!!.contains("EBL"))
    }
}
