package com.wasif.khata.feature.unmatched

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.model.RawMessageStatus
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.sms.IngestionPipeline
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.sms.IngestionScheduler
import com.wasif.khata.core.sms.ReparseUseCase
import com.wasif.khata.core.sms.RuleEngine
import com.wasif.khata.core.sms.TransferPairing
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnmatchedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: KhataDatabase
    private lateinit var pipeline: IngestionPipeline

    private val clock = object : KhataClock {
        override fun now(): Long = 9_000L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Room delivers Flow query results on its own executor. Left as the default
        // that is a real background thread, which advanceUntilIdle() cannot drive, so
        // every assertion would read the initial value.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
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
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun insert(
        uuid: String,
        body: String,
        receivedAt: Long,
        status: RawMessageStatus,
        sender: String = "bKash",
    ) = db.rawMessageDao().insertIgnoringDuplicate(
        RawMessageEntity(
            uuid = uuid,
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            bodyHash = uuid,
            status = status,
            matchedRuleId = null,
            createdAt = receivedAt,
            updatedAt = receivedAt,
        )
    )

    /** Flipped by the test that cares whether the Ask Gemini action is offered. */
    private var geminiKey: String? = null

    private class FakePreferences(private val key: String?) : PreferencesRepository {
        override val preferences: Flow<KhataPreferences> =
            flowOf(KhataPreferences.Default.copy(geminiKey = key))

        override suspend fun setTheme(spec: ThemeSpec) = Unit
        override suspend fun resetTheme() = Unit
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
        override suspend fun setSmsPermissionRequested() = Unit
        override suspend fun setBackfilled() = Unit
        override suspend fun setGeminiKey(key: String?) = Unit
        override suspend fun setBackupPassphrase(passphrase: String?) = Unit
        override suspend fun setDriveConnected(connected: Boolean) = Unit
        override suspend fun setDriveFolderId(id: String?) = Unit
        override suspend fun setDriveUploaded(at: Long) = Unit
        override suspend fun setDriveNeedsReconnect() = Unit
    }

    /**
     * state is WhileSubscribed, so without a collector it never leaves its initial
     * value and every assertion below would read the empty default.
     */
    private fun TestScope.startedViewModel(): UnmatchedViewModel =
        UnmatchedViewModel(
            rawMessageDao = db.rawMessageDao(),
            ruleDao = db.parsingRuleDao(),
            reparse = ReparseUseCase(db.rawMessageDao(), pipeline, clock),
            clock = clock,
            scheduler = IngestionScheduler(ApplicationProvider.getApplicationContext()),
            preferences = FakePreferences(geminiKey),
        ).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
        }

    @Test
    fun `only unmatched messages appear`() = runTest(dispatcher) {
        insert("a", "unknown format", 3000, RawMessageStatus.UNMATCHED)
        insert("b", "an otp", 2000, RawMessageStatus.IGNORED)
        insert("c", "a payment", 1000, RawMessageStatus.PARSED)

        val vm = startedViewModel()
        advanceUntilIdle()

        assertEquals(listOf("unknown format"), vm.state.value.messages.map { it.body })
    }

    @Test
    fun `newest first, because a format that broke recently is the one worth a rule`() = runTest(dispatcher) {
        insert("old", "older", 1000, RawMessageStatus.UNMATCHED)
        insert("new", "newer", 3000, RawMessageStatus.UNMATCHED)

        val vm = startedViewModel()
        advanceUntilIdle()

        assertEquals(listOf("newer", "older"), vm.state.value.messages.map { it.body })
    }

    @Test
    fun `an empty list is distinguishable from not yet loaded`() = runTest(dispatcher) {
        val vm = startedViewModel()

        assertFalse("must not claim loaded before the query returns", vm.state.value.isLoaded)

        advanceUntilIdle()

        assertTrue(vm.state.value.isLoaded)
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun `sender and received time survive to the UI model`() = runTest(dispatcher) {
        insert("a", "unknown", 4242, RawMessageStatus.UNMATCHED, sender = "EBL")

        val vm = startedViewModel()
        advanceUntilIdle()

        val message = vm.state.value.messages.single()
        assertEquals("EBL", message.sender)
        assertEquals(4242L, message.receivedAt)
    }

    @Test
    fun `a Bengali body is flagged so it gets a Bengali type style`() = runTest(dispatcher) {
        insert("bn", "আপনার হিসাব", 2000, RawMessageStatus.UNMATCHED)
        insert("en", "Your account", 1000, RawMessageStatus.UNMATCHED)

        val vm = startedViewModel()
        advanceUntilIdle()

        assertEquals(
            mapOf("আপনার হিসাব" to true, "Your account" to false),
            vm.state.value.messages.associate { it.body to it.isBengali },
        )
    }

    @Test
    fun `a message that becomes parsed leaves the list`() = runTest(dispatcher) {
        val id = insert("a", "unknown", 1000, RawMessageStatus.UNMATCHED)
        val vm = startedViewModel()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.messages.size)

        db.rawMessageDao().markStatus(id, RawMessageStatus.PARSED, ruleId = 1, updatedAt = 2000)
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }

    // --- "Not a transaction" ------------------------------------------------

    private val CODE = "Your bKash verification code is %s. The code will expire in 2 minutes."

    @Test
    fun `hiding one verification code hides every one of them`() = runTest(dispatcher) {
        insert("a", CODE.format("350404"), 3000, RawMessageStatus.UNMATCHED)
        insert("b", CODE.format("525524"), 2000, RawMessageStatus.UNMATCHED)
        insert("c", CODE.format("118822"), 1000, RawMessageStatus.UNMATCHED)
        val vm = startedViewModel()
        advanceUntilIdle()

        vm.onNotATransaction(vm.state.value.messages.first().id)
        advanceUntilIdle()

        // The whole point: the list can actually be finished.
        assertTrue(vm.state.value.isEmpty)
        assertEquals(3, db.rawMessageDao().countByStatus(RawMessageStatus.IGNORED))
    }

    @Test
    fun `it says how many it hid rather than doing it silently`() = runTest(dispatcher) {
        insert("a", CODE.format("350404"), 3000, RawMessageStatus.UNMATCHED)
        insert("b", CODE.format("525524"), 2000, RawMessageStatus.UNMATCHED)
        val vm = startedViewModel()
        advanceUntilIdle()

        vm.onNotATransaction(vm.state.value.messages.first().id)
        advanceUntilIdle()

        assertEquals("Hidden, along with 2 others like it.", vm.state.value.notice)
    }

    @Test
    fun `a message from another sender is left alone`() = runTest(dispatcher) {
        insert("a", CODE.format("350404"), 3000, RawMessageStatus.UNMATCHED)
        insert("b", CODE.format("525524"), 2000, RawMessageStatus.UNMATCHED, sender = "EBL")
        val vm = startedViewModel()
        advanceUntilIdle()

        vm.onNotATransaction(vm.state.value.messages.first { it.sender == "bKash" }.id)
        advanceUntilIdle()

        assertEquals(listOf("EBL"), vm.state.value.messages.map { it.sender })
    }

    @Test
    fun `an unrelated message is not swept up`() = runTest(dispatcher) {
        insert("a", CODE.format("350404"), 3000, RawMessageStatus.UNMATCHED)
        insert("b", "Some new bKash wording about a payment", 2000, RawMessageStatus.UNMATCHED)
        val vm = startedViewModel()
        advanceUntilIdle()

        vm.onNotATransaction(vm.state.value.messages.first { it.body.contains("verification") }.id)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.messages.size)
        assertTrue(vm.state.value.messages.single().body.contains("payment"))
    }

    @Test
    fun `too short an opening is refused with a reason, and nothing is written`() = runTest(dispatcher) {
        insert("a", "Tk 500 received", 3000, RawMessageStatus.UNMATCHED)
        val vm = startedViewModel()
        advanceUntilIdle()

        vm.onNotATransaction(vm.state.value.messages.single().id)
        advanceUntilIdle()

        assertNotNull(vm.state.value.notice)
        assertTrue(vm.state.value.notice!!.contains("too few words"))
        assertEquals(0, db.parsingRuleDao().allIncludingDisabled().count { it.origin == "USER" })
        assertEquals(1, vm.state.value.messages.size)
    }

    @Test
    fun `the rule it writes cannot outrank a rule that reads the message`() = runTest(dispatcher) {
        DatabaseSeeder(db.accountDao(), db.categoryDao(), db.parsingRuleDao(), clock).seedIfEmpty()
        insert("a", CODE.format("350404"), 3000, RawMessageStatus.UNMATCHED)
        val vm = startedViewModel()
        advanceUntilIdle()

        vm.onNotATransaction(vm.state.value.messages.single().id)
        advanceUntilIdle()

        val rules = db.parsingRuleDao().allIncludingDisabled()
        val written = rules.single { it.origin == "USER" }
        assertEquals(RuleKind.IGNORE, written.kind)
        // Evaluated last, so at worst it silences something nothing else could read.
        assertEquals(rules.maxOf { it.priority }, written.priority)
    }

    @Test
    fun `progress replaces the notice while the re-read runs`() = runTest(dispatcher) {
        insert("a", CODE.format("350404"), 3000, RawMessageStatus.UNMATCHED)
        insert("b", CODE.format("525524"), 2000, RawMessageStatus.UNMATCHED)
        val vm = startedViewModel()
        advanceUntilIdle()

        val seen = mutableListOf<String?>()
        backgroundScope.launch { vm.state.collect { seen += it.line } }
        vm.onNotATransaction(vm.state.value.messages.first().id)
        advanceUntilIdle()

        assertTrue("expected a counting line, saw $seen", seen.any { it?.startsWith("Re-reading ") == true })
        // And the outcome is what is left standing.
        assertEquals("Hidden, along with 2 others like it.", vm.state.value.line)
    }
}
