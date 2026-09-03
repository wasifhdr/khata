package com.wasif.khata.core.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.model.RawMessageStatus
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val BKASH_PAYMENT =
    "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01"
private const val BKASH_OTP =
    "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min."

@RunWith(RobolectricTestRunner::class)
class ReparseUseCaseTest {

    private lateinit var db: KhataDatabase
    private lateinit var pipeline: IngestionPipeline
    private lateinit var reparse: ReparseUseCase
    private lateinit var backfill: BackfillUseCase

    private val clock = object : KhataClock {
        override fun now(): Long = 9_000L
    }

    private class FakeSource(private val messages: List<IncomingMessage>) : MessageSource {
        override suspend fun readAll(): List<IncomingMessage> = messages
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
        reparse = ReparseUseCase(db.rawMessageDao(), pipeline, clock)
        backfill = BackfillUseCase(FakeSource(emptyList()), pipeline)
    }

    @After
    fun tearDown() = db.close()

    /** Disables every rule, leaving the engine unable to match anything. */
    private suspend fun disableAllRules() {
        val disabled = db.parsingRuleDao().enabled().map { it.copy(isEnabled = false) }
        db.parsingRuleDao().upsertAll(disabled)
    }

    private suspend fun enableAllRules() {
        val rules = db.parsingRuleDao().allIncludingDisabled()
        db.parsingRuleDao().upsertAll(rules.map { it.copy(isEnabled = true) })
    }

    @Test
    fun `reparse with no rules marks everything UNMATCHED and records nothing`() = runTest {
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)
        disableAllRules()

        val summary = reparse()

        assertEquals(1, summary.total)
        assertEquals(0, summary.recorded)
        assertEquals(1, db.rawMessageDao().countByStatus(RawMessageStatus.UNMATCHED))
    }

    @Test
    fun `adding a rule back and reparsing retroactively records the message`() = runTest {
        disableAllRules()
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)
        assertEquals(0, db.transactionDao().allActive().size)

        enableAllRules()
        val summary = reparse()

        assertEquals(1, summary.recorded)
        assertEquals(1, db.transactionDao().allActive().size)
    }

    @Test
    fun `reparsing twice does not duplicate a transaction`() = runTest {
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)

        reparse()
        reparse()

        assertEquals(1, db.transactionDao().allActive().size)
        assertEquals(4198L, db.accountDao().getAll().first { it.name == "bKash" }.currentBalanceMinor)
    }

    @Test
    fun `reparsing an EBL message twice does not duplicate it despite having no TrxID`() = runTest {
        pipeline.ingest(
            "EBL",
            "AC 115***352 is debited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 58.56 Thanks. EBL Helpline 16230",
            receivedAt = 1000,
        )

        reparse()
        reparse()

        assertEquals(1, db.transactionDao().allActive().size)
        assertEquals(5856L, db.accountDao().getAll().first { it.name == "EBL Salary" }.currentBalanceMinor)
    }

    @Test
    fun `reparse leaves ignored messages ignored and records nothing for them`() = runTest {
        pipeline.ingest("bKash", BKASH_OTP, receivedAt = 1000)

        val summary = reparse()

        assertEquals(1, summary.ignored)
        assertEquals(0, db.transactionDao().allActive().size)
    }

    @Test
    fun `backfill ingests every message from its source and is idempotent`() = runTest {
        val source = FakeSource(
            listOf(
                IncomingMessage("bKash", BKASH_PAYMENT, 1000),
                IncomingMessage("bKash", BKASH_OTP, 2000),
            )
        )
        val useCase = BackfillUseCase(source, pipeline)

        val first = useCase()
        val second = useCase()

        assertEquals(2, first.total)
        assertEquals(1, first.recorded)
        assertEquals(1, first.ignored)
        assertEquals(2, second.duplicates)
        assertEquals(1, db.transactionDao().allActive().size)
    }

    @Test
    fun `the unmatched pass reads only unmatched messages`() = runTest {
        // Hiding one message used to re-read the whole inbox: thousands of messages on
        // a real phone, to apply a rule that takes max+1 and so cannot outrank
        // anything already matched. The pass has to be the size of the work.
        pipeline.ingest("bKash", BKASH_PAYMENT, receivedAt = 1000)
        pipeline.ingest("bKash", "an unrecognised format", receivedAt = 2000)

        assertEquals(1, db.rawMessageDao().countByStatus(RawMessageStatus.PARSED))
        assertEquals(1, db.rawMessageDao().countByStatus(RawMessageStatus.UNMATCHED))

        val whole = reparse.run().last()
        val narrow = reparse.runUnmatched().last()

        assertEquals(2, whole.total)
        assertEquals(1, narrow.total)
    }
}
