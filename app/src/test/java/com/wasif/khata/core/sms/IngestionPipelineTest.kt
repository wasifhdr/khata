package com.wasif.khata.core.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.RawMessageStatus
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val BKASH_PAYMENT =
    "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01"
private const val BKASH_OTP =
    "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min."
private const val UBER_RESERVED =
    "Payment of Tk 564.11 is being reserved for Uber Bangladesh Ltd-Uber. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11"
private const val UBER_SUCCESSFUL =
    "Payment of Tk 564.11 to Uber Bangladesh Ltd-Uber is successful. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11"
private const val EBL_DEBIT_352 =
    "AC 115***352 is debited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 58.56 Thanks. EBL Helpline 16230"
private const val EBL_PURCHASE_352 =
    "Purchase txn BDT 1101 from TOUR DE CYCLIST Ut.Card 539280**3432 on 31-Aug-26 06:27:44 PM BST.Your A/C 115**9352 Balance BDT 118.56. EBL Helpline 16230"
private const val BKASH_LOAN =
    "You have received Digital Loan Tk 900.00 from City Bank. Balance Tk 897.98. TrxID DHV31FH6VD at 31/08/2026 19:00."

@RunWith(RobolectricTestRunner::class)
class IngestionPipelineTest {

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
            pairing = TransferPairing(db.transactionDao(), clock),
            clock = clock,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun transactions() = db.transactionDao().allActive()

    private suspend fun accountNamed(name: String) =
        db.accountDao().getAll().first { it.name == name }

    @Test
    fun `a bKash payment records one debit against the bKash account`() = runTest {
        val result = pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)

        assertTrue("expected Recorded, got $result", result is IngestResult.Recorded)
        val all = transactions()
        assertEquals(1, all.size)
        assertEquals(Money(85600), Money(all.single().amountMinor))
        assertEquals(TransactionDirection.DEBIT, all.single().direction)
        assertEquals("FOODPANDA BANGLADESH LIMITED", all.single().merchantRaw)
        // The message says "Balance Tk 41.98", so that is the balance. Accumulating
        // from zero instead would only ever be right for an account whose whole life
        // is in the inbox.
        assertEquals(4198L, accountNamed("bKash").currentBalanceMinor)
    }

    @Test
    fun `an OTP message records nothing at all`() = runTest {
        val result = pipeline.ingest("bKash", BKASH_OTP, receivedAt = 1000)

        assertEquals(IngestResult.Ignored, result)
        assertEquals(0, transactions().size)
        assertEquals(1, db.rawMessageDao().countByStatus(RawMessageStatus.IGNORED))
    }

    @Test
    fun `the reserved and successful twins produce exactly one transaction`() = runTest {
        pipeline.ingest("bKash", UBER_RESERVED, receivedAt = 1000)
        val second = pipeline.ingest("bKash", UBER_SUCCESSFUL, receivedAt = 2000)

        assertTrue("expected Updated, got $second", second is IngestResult.Updated)
        val all = transactions()
        assertEquals(1, all.size)
        assertEquals("DHA3BH4G8R", all.single().providerTxnId)
        // The "successful" wording arrives second and is the more authoritative one.
        assertEquals("Uber Bangladesh Ltd-Uber", all.single().merchantRaw)
        assertEquals(21409L, accountNamed("bKash").currentBalanceMinor)
    }

    @Test
    fun `re-ingesting the identical message is a Duplicate and moves no balance`() = runTest {
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)
        val second = pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)

        assertEquals(IngestResult.Duplicate, second)
        assertEquals(1, transactions().size)
        assertEquals(4198L, accountNamed("bKash").currentBalanceMinor)
    }

    @Test
    fun `an EBL debit routes to EBL Salary by account tail`() = runTest {
        pipeline.ingest("EBL", EBL_DEBIT_352, receivedAt = 1000)

        val salary = accountNamed("EBL Salary")
        assertEquals(salary.id, transactions().single().accountId)
        assertEquals(5856L, salary.currentBalanceMinor)
    }

    @Test
    fun `an EBL card purchase routes to the same account despite a different mask`() = runTest {
        pipeline.ingest("EBL", EBL_DEBIT_352, receivedAt = 1000)
        pipeline.ingest("EBL", EBL_PURCHASE_352, receivedAt = 2000)

        val salary = accountNamed("EBL Salary")
        assertEquals(listOf(salary.id, salary.id), transactions().map { it.accountId })
    }

    @Test
    fun `an unknown format is marked UNMATCHED and records no transaction`() = runTest {
        val result = pipeline.ingest("bKash", "Some entirely new wording nobody planned for", receivedAt = 1000)

        assertEquals(IngestResult.Unmatched, result)
        assertEquals(0, transactions().size)
        assertEquals(1, db.rawMessageDao().countByStatus(RawMessageStatus.UNMATCHED))
    }

    @Test
    fun `a new merchant is created with MEDIUM confidence and no category`() = runTest {
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)

        val txn = transactions().single()
        assertEquals(Confidence.MEDIUM, txn.confidence)
        assertNull(txn.categoryId)
        assertNotNull(txn.merchantId)
        assertNotNull(db.merchantDao().findByAlias("FOODPANDA BANGLADESH LIMITED"))
    }

    @Test
    fun `a known merchant reuses its category and records HIGH confidence`() = runTest {
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)
        val groceries = db.categoryDao().observeAll().first().first { it.name == "Groceries" }
        val merchant = db.merchantDao().findByAlias("FOODPANDA BANGLADESH LIMITED")!!
        db.merchantDao().upsert(merchant.copy(categoryId = groceries.id, isUserConfirmed = true))

        pipeline.ingest("bKash", BKASH_PAYMENT.replace("DHV41FIPGY", "DIFFERENTID"), receivedAt = 3000)

        val latest = transactions().first { it.providerTxnId == "DIFFERENTID" }
        assertEquals(Confidence.HIGH, latest.confidence)
        assertEquals(groceries.id, latest.categoryId)
    }

    @Test
    fun `a digital loan is recorded as LOAN_DISBURSEMENT not plain income`() = runTest {
        pipeline.ingest("bKash", BKASH_LOAN, receivedAt = 1000)

        val txn = transactions().single()
        assertEquals(TransactionKind.LOAN_DISBURSEMENT, txn.kind)
        assertEquals(TransactionDirection.CREDIT, txn.direction)
        // "Balance Tk 897.98" -- the bank's figure, not the Tk 900 credit summed
        // from an assumed empty account.
        assertEquals(89798L, accountNamed("bKash").currentBalanceMinor)
    }

    @Test
    fun `the reported balance from the message is written to the account`() = runTest {
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)

        val bkash = accountNamed("bKash")
        assertEquals(4198L, bkash.reportedBalanceMinor)
        // The message's own time, not the moment it was read: a backfill reads six
        // years in one pass, and stamping them all "now" makes ordering meaningless.
        assertEquals(transactions().single().occurredAt, bkash.reportedBalanceAt)
    }

    @Test
    fun `the real Own Account Transfer pair is grouped end to end`() = runTest {
        pipeline.ingest(
            "EBL",
            "AC 112***286 is debited with BDT 6500 as Own Account Transfer on 18-AUG-26 01:20:19 PM Balance is BDT 34.2 Thanks. EBL Helpline 16230",
            receivedAt = 1000,
        )
        pipeline.ingest(
            "EBL",
            "AC 115***352 is credited with BDT 6500 as Own Account Transfer on 18-AUG-26 01:20:20 PM Balance is BDT 6516.56 Thanks. EBL Helpline 16230",
            receivedAt = 2000,
        )

        val all = transactions()
        assertEquals(2, all.size)
        val groups = all.mapNotNull { it.transferGroupId }.distinct()
        assertEquals("both legs must share one group", 1, groups.size)
        assertTrue("neither leg may count as spending", all.all { it.kind == TransactionKind.TRANSFER })
    }

    @Test
    fun `an EBL ATM withdrawal is recorded as a transfer kind`() = runTest {
        pipeline.ingest(
            "EBL",
            "Cash WD BDT5000 from North South Universi. Card 539280**3432 on 19-Aug-26 06:00:37 PM BST.Your A/C 115**9352 Balance BDT 1501.56. EBL Helpline 16230",
            receivedAt = 1000,
        )

        assertEquals(TransactionKind.TRANSFER, transactions().single().kind)
    }

    // --- The balance is what the bank last said it was ----------------------

    @Test
    fun `a later statement overrules the running total`() = runTest {
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)

        // Ingested second but stamped earlier in its own text, so it must not drag
        // the balance back to an older figure.
        pipeline.ingest(
            "bKash",
            "Payment of Tk 10.00 to SOMEWHERE ELSE is successful. Balance Tk 999.00. " +
                "TrxID OLDER00001 at 01/01/2020 09:00",
            receivedAt = 2000,
        )

        assertEquals(4198L, accountNamed("bKash").currentBalanceMinor)
    }

    @Test
    fun `money that moved with no message is banked rather than absorbed`() = runTest {
        // Two payments whose stated balances cannot both follow from the amounts:
        // Tk 856 leaves and the balance lands at 41.98, then Tk 10 leaves and the
        // balance is 20.00 -- so Tk 11.98 went somewhere no message described.
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)
        pipeline.ingest(
            "bKash",
            "Payment of Tk 10.00 to SOMEWHERE ELSE is successful. Balance Tk 20.00. " +
                "TrxID LATER000001 at 31/08/2026 20:00",
            receivedAt = 2000,
        )

        val bkash = accountNamed("bKash")
        assertEquals(2000L, bkash.currentBalanceMinor)
        // 41.98 less the Tk 10 that left is 31.98, but the bank says 20.00, so
        // Tk 11.98 moved with no message describing it.
        assertEquals(-1198L, bkash.unexplainedMinor)
    }
}
