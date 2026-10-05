package com.wasif.khata.core.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.notify.TransferNotifier
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PAYMENT =
    "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01"
private const val OTP =
    "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min."
private const val UNKNOWN = "Some entirely new wording nobody planned for"

@RunWith(RobolectricTestRunner::class)
class BackfillProgressTest {

    private lateinit var db: KhataDatabase
    private lateinit var pipeline: IngestionPipeline

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

    private fun useCase(vararg bodies: String) = BackfillUseCase(
        FakeSource(bodies.mapIndexed { i, body -> IncomingMessage("bKash", body, 1000L + i) }),
        pipeline,
    )

    @Test
    fun `progress rises monotonically and ends at the total`() = runTest {
        val emissions = useCase(PAYMENT, OTP, UNKNOWN).run().toList()

        // One priming emission at zero plus one per message.
        assertEquals(listOf(0, 1, 2, 3), emissions.map { it.processed })
        assertTrue(emissions.all { it.total == 3 })
        assertTrue(emissions.last().isComplete)
    }

    @Test
    fun `the final emission classifies every message`() = runTest {
        val final = useCase(PAYMENT, OTP, UNKNOWN).run().toList().last().summary

        assertEquals(3, final.total)
        assertEquals(1, final.recorded)
        assertEquals(1, final.ignored)
        assertEquals(1, final.unmatched)
    }

    @Test
    fun `an empty inbox completes immediately rather than reporting nothing`() = runTest {
        val emissions = useCase().run().toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions.single().isComplete)
        assertEquals(1f, emissions.single().fraction, 0.001f)
    }

    @Test
    fun `re-running reports duplicates and records nothing new`() = runTest {
        useCase(PAYMENT).run().toList()

        val second = useCase(PAYMENT).run().toList().last().summary

        assertEquals(1, second.duplicates)
        assertEquals(0, second.recorded)
        assertEquals(1, db.transactionDao().allActive().size)
    }

    @Test
    fun `stopping partway leaves the messages already ingested intact`() = runTest {
        // Each message is written in its own database transaction, so an interrupted
        // backfill is a shorter backfill rather than a corrupt one.
        val emissions = mutableListOf<IngestProgress>()
        useCase(PAYMENT, UNKNOWN).run().collect { progress ->
            emissions += progress
            if (progress.processed == 1) return@collect
        }

        assertEquals(1, db.transactionDao().allActive().size)
        assertEquals(4198L, db.accountDao().getAll().first { it.name == "bKash" }.currentBalanceMinor)
    }

    @Test
    fun `sequential historical rule caching calls AI once and matches later messages locally`() = runTest {
        var aiCalls = 0
        val suggester = com.wasif.khata.core.sms.ai.RuleSuggester { sender, _ ->
            aiCalls++
            com.wasif.khata.core.sms.ai.DraftedRule(
                name = "New bKash Promo Debit",
                senderPattern = sender,
                bodyPattern = "Instant Pay BDT (?<amount>[0-9.]+) at (?<merchant>[A-Za-z0-9 ]+)\\. Ref (?<refId>[A-Z0-9]+)",
                direction = com.wasif.khata.core.model.TransactionDirection.DEBIT,
                kind = com.wasif.khata.core.model.RuleKind.NORMAL,
            )
        }
        val drafter = com.wasif.khata.core.sms.ai.RuleDrafter(db.parsingRuleDao(), clock)
        val messages = listOf(
            IncomingMessage("bKash", "Instant Pay BDT 120.00 at KACCHI BHAI. Ref TX101", 1000L),
            IncomingMessage("bKash", "Instant Pay BDT 250.00 at STAR KABAB. Ref TX102", 1001L),
            IncomingMessage("bKash", "Instant Pay BDT 90.00 at NORTH END. Ref TX103", 1002L),
        )

        val summary = BackfillUseCase(
            FakeSource(messages),
            pipeline,
            suggester,
            drafter,
            db.rawMessageDao(),
        )
            .run()
            .toList()
            .last()
            .summary

        assertEquals(1, aiCalls)
        assertEquals(3, summary.recorded)
        assertEquals(0, summary.unmatched)
        assertEquals(3, db.transactionDao().allActive().size)
    }

    @Test
    fun `messages older than smsStartFrom cutoff are skipped during backfill`() = runTest {
        val messages = listOf(
            IncomingMessage(
                "bKash",
                "Payment of Tk 100.00 to OLD SHOP is successful. Balance Tk 900.00. TrxID OLD1 at 01/05/2026 10:00",
                1_000L,
            ),
            IncomingMessage(
                "bKash",
                "Payment of Tk 200.00 to NEW SHOP is successful. Balance Tk 700.00. TrxID NEW1 at 01/06/2026 10:00",
                2_000L,
            ),
        )

        val summary = BackfillUseCase(
            source = FakeSource(messages),
            pipeline = pipeline,
            smsStartFrom = SmsStartFrom { 1_500L },
        )
            .run()
            .toList()
            .last()
            .summary

        assertEquals(1, summary.total)
        assertEquals(1, summary.recorded)
        assertEquals(1, db.transactionDao().allActive().size)
        assertEquals(700_00L, db.accountDao().getAll().first { it.name == "bKash" }.currentBalanceMinor)
    }
}
