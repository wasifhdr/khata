package com.wasif.khata.core.sms.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuleDrafterTest {

    private lateinit var db: KhataDatabase
    private lateinit var drafter: RuleDrafter

    private val clock = object : KhataClock { override fun now(): Long = 9_000L }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        DatabaseSeeder(db.accountDao(), db.categoryDao(), db.parsingRuleDao(), clock).seedIfEmpty()
        drafter = RuleDrafter(db.parsingRuleDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a rule that matches nothing is discarded`() = runTest {
        val drafted = DraftedRule(
            name = "wrong",
            senderPattern = "bKash",
            bodyPattern = "this will never appear",
            direction = null,
            kind = RuleKind.NORMAL,
        )

        assertFalse(drafter.store(drafted, "bKash", "Payment of Tk 5.00 to SHOP"))
        assertTrue(db.parsingRuleDao().allIncludingDisabled().none { it.origin == "AI" })
    }

    @Test
    fun `a stored AI rule ranks below every hand-written one`() = runTest {
        val drafted = DraftedRule(
            name = "ok",
            senderPattern = "bKash",
            bodyPattern = "Payment of Tk (?<amount>[0-9.]+)",
            direction = null,
            kind = RuleKind.NORMAL,
        )

        assertTrue(drafter.store(drafted, "bKash", "Payment of Tk 5.00 to SHOP"))

        val stored = db.parsingRuleDao().allIncludingDisabled().single { it.origin == "AI" }
        val handWritten = db.parsingRuleDao().allIncludingDisabled().filter { it.origin != "AI" }
        // Lower number wins in RuleEngine, so every hand-written rule must sort first.
        assertTrue(handWritten.all { it.priority < stored.priority })
    }

    @Test
    fun `a sender the pattern does not claim is discarded`() = runTest {
        val drafted = DraftedRule(
            name = "wrong sender",
            senderPattern = "EBL",
            bodyPattern = "Payment of Tk (?<amount>[0-9.]+)",
            direction = null,
            kind = RuleKind.NORMAL,
        )

        // A rule whose sender pattern misses would never fire for the message that
        // prompted it, which is the one message we know it must handle.
        assertFalse(drafter.store(drafted, "bKash", "Payment of Tk 5.00 to SHOP"))
    }
}
