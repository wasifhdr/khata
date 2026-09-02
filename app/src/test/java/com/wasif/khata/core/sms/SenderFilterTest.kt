package com.wasif.khata.core.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A real 6,018-message inbox was 84% messages from senders no rule claims: the
 * mobile operator, the university, a pizza chain, and friends. Every one of them
 * was being stored in full and shown as something to review.
 */
@RunWith(RobolectricTestRunner::class)
class SenderFilterTest {

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

    @Test
    fun `a friend's text is never stored`() = runTest {
        val result = pipeline.ingest("+8801700000001", "are you coming tonight", 1000L)

        assertEquals(IngestResult.NotMine, result)
        // The privacy claim the settings row makes -- "read bKash and EBL
        // messages" -- has to be literally true of what lands in the database.
        assertEquals(0, db.rawMessageDao().allForReparse().size)
    }

    @Test
    fun `an operator promo is not something to teach Khata to read`() = runTest {
        pipeline.ingest("skitto", "Your 10GB pack is expiring. Recharge now!", 1000L)

        assertEquals(0, db.rawMessageDao().allForReparse().size)
    }

    @Test
    fun `a bKash message is still taken`() = runTest {
        val result = pipeline.ingest(
            "bKash",
            "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. " +
                "Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01",
            1000L,
        )

        assertTrue(result is IngestResult.Recorded)
    }

    @Test
    fun `a bKash message no rule can read is still kept for review`() = runTest {
        // The sender is claimed even though no body pattern matches, which is
        // exactly the case the rule editor exists for. Filtering by sender must
        // not take that away.
        val result = pipeline.ingest("bKash", "Some entirely new bKash wording", 1000L)

        assertEquals(IngestResult.Unmatched, result)
        assertEquals(1, db.rawMessageDao().allForReparse().size)
    }

    @Test
    fun `the skipped messages are counted rather than silently vanishing`() = runTest {
        val summary = IngestSummary()
            .plus(pipeline.ingest("skitto", "promo", 1000L))
            .plus(pipeline.ingest("NSU", "class cancelled", 1001L))

        assertEquals(2, summary.notMine)
        assertEquals(2, summary.total)
        assertEquals(0, summary.unmatched)
    }

    @Test
    fun `sender matching agrees with what the parser would do`() = runTest {
        val engine = RuleEngine()
        val rules = db.parsingRuleDao().enabled()

        // A sender the engine claims but cannot parse must be stored; a sender it
        // does not claim must not be. If these two disagreed, a message could be
        // dropped despite matching a rule.
        assertTrue(engine.claimsSender("bKash", rules))
        assertTrue(engine.claimsSender("EBL", rules))
        assertFalse(engine.claimsSender("skitto", rules))
        assertFalse(engine.claimsSender("+8801700000001", rules))
    }
}
