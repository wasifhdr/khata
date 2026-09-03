package com.wasif.khata.core.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.sms.ai.DraftedRule
import com.wasif.khata.core.sms.ai.RuleDrafter
import com.wasif.khata.core.sms.ai.RuleSuggester
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val UNTAUGHT = "Paid Tk 55.00 to NEW SHOP LIMITED"

private const val PAYMENT =
    "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01"

@RunWith(RobolectricTestRunner::class)
class IngestionWorkerTest {

    private lateinit var db: KhataDatabase
    private lateinit var pipeline: IngestionPipeline
    private lateinit var backfill: BackfillUseCase
    private lateinit var reparse: ReparseUseCase
    private lateinit var drafter: RuleDrafter

    /** Swapped per test; no test here opens a socket. */
    private var suggester = RuleSuggester { _, _ -> null }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = object : KhataClock { override fun now(): Long = 9_000L }

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
        backfill = BackfillUseCase(FakeSource(listOf(IncomingMessage("bKash", PAYMENT, 1_000L))), pipeline)
        reparse = ReparseUseCase(db.rawMessageDao(), pipeline, clock)
        drafter = RuleDrafter(db.parsingRuleDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private fun worker(vararg input: Pair<String, Any?>): IngestionWorker =
        TestListenableWorkerBuilder<IngestionWorker>(context)
            .setInputData(workDataOf(*input))
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        IngestionWorker(
                            appContext,
                            workerParameters,
                            backfill,
                            reparse,
                            pipeline,
                            db.rawMessageDao(),
                            suggester,
                            drafter,
                        )
                },
            )
            .build()

    @Test
    fun `a backfill pass records what the inbox holds`() = runTest {
        val result = worker(KEY_MODE to IngestionMode.BACKFILL.name).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, db.transactionDao().allActive().size)
    }

    @Test
    fun `the final summary comes back as the worker's output`() = runTest {
        val result = worker(KEY_MODE to IngestionMode.BACKFILL.name).doWork()

        val progress = (result as ListenableWorker.Result.Success).outputData.toIngestProgress()!!
        assertEquals(1, progress.summary.recorded)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `a single message is ingested on its own`() = runTest {
        val result = worker(
            KEY_MODE to IngestionMode.MESSAGE.name,
            KEY_SENDER to "bKash",
            KEY_BODY to PAYMENT,
            KEY_RECEIVED_AT to 1_000L,
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(TransactionSource.SMS, db.transactionDao().allActive().single().source)
    }

    // Retry, not failure: parsing is idempotent and re-runnable, so a pass that died
    // halfway can simply be run again. Failure would discard the work.
    @Test
    fun `a pass that throws asks to be retried`() = runTest {
        db.close()

        val result = worker(KEY_MODE to IngestionMode.BACKFILL.name).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `an unmatched message becomes a rule, and then parses`() = runTest {
        // A shape the seeded rules do not match, from a sender they do claim.
        pipeline.ingest("bKash", UNTAUGHT, 2_000L)
        val rawId = db.rawMessageDao().allForReparse().single { it.body == UNTAUGHT }.id
        suggester = RuleSuggester { _, _ ->
            DraftedRule(
                name = "learned",
                senderPattern = "bKash",
                bodyPattern = "Paid Tk (?<amount>[0-9.]+) to (?<merchant>.+)",
                direction = TransactionDirection.DEBIT,
                kind = RuleKind.NORMAL,
            )
        }

        val result = worker(
            KEY_MODE to IngestionMode.TEACH.name,
            KEY_RAW_ID to rawId,
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        // The payoff: the rule is stored, and the reparse it triggers has turned the
        // message into a transaction through the ordinary engine.
        assertTrue(db.parsingRuleDao().allIncludingDisabled().any { it.origin == "AI" })
        assertEquals(1, db.transactionDao().allActive().size)
    }

    @Test
    fun `a suggester that returns nothing asks to be retried`() = runTest {
        pipeline.ingest("bKash", UNTAUGHT, 2_000L)
        val rawId = db.rawMessageDao().allForReparse().single { it.body == UNTAUGHT }.id
        suggester = RuleSuggester { _, _ -> null }

        val result = worker(
            KEY_MODE to IngestionMode.TEACH.name,
            KEY_RAW_ID to rawId,
        ).doWork()

        // Retry, not failure: a rate limit or a flaky connection should come back, and
        // the message stays unmatched until it does.
        assertTrue(result is ListenableWorker.Result.Retry)
        assertTrue(db.parsingRuleDao().allIncludingDisabled().none { it.origin == "AI" })
    }

    @Test
    fun `a rule that does not match the message it came from is not stored`() = runTest {
        pipeline.ingest("bKash", UNTAUGHT, 2_000L)
        val rawId = db.rawMessageDao().allForReparse().single { it.body == UNTAUGHT }.id
        suggester = RuleSuggester { _, _ ->
            DraftedRule(
                name = "useless",
                senderPattern = "bKash",
                bodyPattern = "nothing like the message",
                direction = null,
                kind = RuleKind.NORMAL,
            )
        }

        val result = worker(
            KEY_MODE to IngestionMode.TEACH.name,
            KEY_RAW_ID to rawId,
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertTrue(db.parsingRuleDao().allIncludingDisabled().none { it.origin == "AI" })
    }
}
