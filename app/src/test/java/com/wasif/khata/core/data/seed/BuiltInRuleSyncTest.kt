package com.wasif.khata.core.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.sms.BUILT_IN_RULES
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
 * The built-in rules used to be written once, on a fresh install, and then frozen.
 * Widening them was therefore worthless to anyone who had already opened the app —
 * exactly the people with history to re-read.
 */
@RunWith(RobolectricTestRunner::class)
class BuiltInRuleSyncTest {

    private lateinit var db: KhataDatabase
    private lateinit var seeder: DatabaseSeeder
    private var now = 1_000L

    private val clock = object : KhataClock {
        override fun now(): Long = now
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        seeder = DatabaseSeeder(db.accountDao(), db.categoryDao(), db.parsingRuleDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a second launch updates the built-ins rather than duplicating them`() = runTest {
        seeder.seedIfEmpty()
        val first = db.parsingRuleDao().allIncludingDisabled()

        now = 2_000L
        seeder.seedIfEmpty()

        val second = db.parsingRuleDao().allIncludingDisabled()
        assertEquals(first.size, second.size)
        assertEquals(BUILT_IN_RULES.size, second.size)
    }

    @Test
    fun `an install seeded with an older rule set picks up a widened pattern`() = runTest {
        // Stand in for a phone that seeded before the rules were widened.
        db.parsingRuleDao().upsertAll(
            listOf(
                ParsingRuleEntity(
                    uuid = "builtin-bkash-payment-success",
                    name = "bKash payment",
                    senderPattern = "bKash",
                    bodyPattern = """Payment of (?<amount>Tk [\d,]+) to (?<merchant>.+?) is successful""",
                    direction = TransactionDirection.DEBIT,
                    kind = RuleKind.NORMAL,
                    priority = 24,
                    origin = "BUILTIN",
                    isEnabled = true,
                    sampleMessage = "",
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )

        seeder.seedIfEmpty()

        val payment = db.parsingRuleDao().allIncludingDisabled()
            .single { it.uuid == "builtin-bkash-payment-success" }
        assertEquals(
            BUILT_IN_RULES.single { it.uuid == "builtin-bkash-payment-success" }.bodyPattern,
            payment.bodyPattern,
        )
        assertEquals("the row is replaced, not added alongside", 1, db.parsingRuleDao().allIncludingDisabled().count { it.uuid == "builtin-bkash-payment-success" })
    }

    @Test
    fun `a built-in the user switched off stays off`() = runTest {
        seeder.seedIfEmpty()
        val rule = db.parsingRuleDao().allIncludingDisabled().first { it.origin == "BUILTIN" }
        db.parsingRuleDao().upsertAll(listOf(rule.copy(isEnabled = false)))

        seeder.seedIfEmpty()

        assertFalse(
            "syncing must not undo a decision the user made",
            db.parsingRuleDao().allIncludingDisabled().single { it.uuid == rule.uuid }.isEnabled,
        )
    }

    @Test
    fun `rules the user wrote are left alone`() = runTest {
        seeder.seedIfEmpty()
        db.parsingRuleDao().upsertAll(
            listOf(
                ParsingRuleEntity(
                    uuid = "user-12345",
                    name = "Mine",
                    senderPattern = "EBL",
                    bodyPattern = """something (?<amount>Tk 5)""",
                    direction = TransactionDirection.DEBIT,
                    kind = RuleKind.NORMAL,
                    priority = 99,
                    origin = "USER",
                    isEnabled = true,
                    sampleMessage = "",
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )

        seeder.seedIfEmpty()

        val mine = db.parsingRuleDao().allIncludingDisabled().single { it.origin == "USER" }
        assertEquals("Mine", mine.name)
        assertEquals(99, mine.priority)
    }

    @Test
    fun `seeding twice does not duplicate accounts or categories`() = runTest {
        seeder.seedIfEmpty()
        val accounts = db.accountDao().getAll().size

        seeder.seedIfEmpty()

        assertEquals(accounts, db.accountDao().getAll().size)
        assertTrue(accounts > 0)
    }
}
