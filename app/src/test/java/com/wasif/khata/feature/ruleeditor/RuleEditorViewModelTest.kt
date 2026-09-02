package com.wasif.khata.feature.ruleeditor

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.model.RawMessageStatus
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.sms.FieldKind
import com.wasif.khata.core.sms.IngestionPipeline
import com.wasif.khata.core.sms.ReparseUseCase
import com.wasif.khata.core.sms.RuleEngine
import com.wasif.khata.core.sms.TransferPairing
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A shape no built-in rule matches, so it lands as UNMATCHED and needs a rule. */
private const val NOVEL =
    "Bill payment of Tk 1,240.50 to DESCO METER 88117 done. New balance Tk 302.10. Ref QX9911ZK on 02/09/2026 14:05"

@RunWith(RobolectricTestRunner::class)
class RuleEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: KhataDatabase
    private lateinit var pipeline: IngestionPipeline
    private var rawId: Long = 0

    private val clock = object : KhataClock {
        override fun now(): Long = 9_000L
    }

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()

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
        rawId = db.rawMessageDao().insertIgnoringDuplicate(
            RawMessageEntity(
                uuid = "novel", sender = "bKash", body = NOVEL, receivedAt = 1000,
                bodyHash = "novel", status = RawMessageStatus.UNMATCHED, matchedRuleId = null,
                createdAt = 1000, updatedAt = 1000,
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = RuleEditorViewModel(
        rawMessageDao = db.rawMessageDao(),
        ruleDao = db.parsingRuleDao(),
        reparse = ReparseUseCase(db.rawMessageDao(), pipeline, clock),
        clock = clock,
        rawMessageId = rawId,
    )

    /** Index of the first token whose text contains [needle]. */
    private fun RuleEditorViewModel.tokenIndex(needle: String): Int =
        state.value.tokens.indexOfFirst { it.text.contains(needle) }

    @Test
    fun `the message is tokenised so a tap maps back to the body`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Bill", vm.state.value.tokens.first().text)
        assertTrue(vm.state.value.tokens.any { it.text == "DESCO" })
    }

    @Test
    fun `nothing selected asks for the amount rather than showing an empty pattern`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Tap the amount in the message, then say what it is.", vm.state.value.derived.error)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `tapping two words selects the run between them`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTokenTapped(vm.tokenIndex("Tk"))
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))

        assertTrue(vm.state.value.awaitingLabel)
        assertEquals(3..4, vm.state.value.pending)
    }

    @Test
    fun `labelling the run captures exactly the text that was tapped`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTokenTapped(vm.tokenIndex("Tk"))
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))
        vm.onFieldChosen(FieldKind.AMOUNT)

        assertNull(vm.state.value.derived.error)
        assertEquals("Tk 1,240.50", vm.state.value.derived.captures["amount"])
        assertFalse(vm.state.value.awaitingLabel)
    }

    @Test
    fun `tapping inside a labelled span clears it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val amountIndex = vm.tokenIndex("1,240.50")
        vm.onTokenTapped(amountIndex)
        vm.onFieldChosen(FieldKind.AMOUNT)
        assertEquals(1, vm.state.value.spans.size)

        vm.onTokenTapped(amountIndex)

        assertTrue(vm.state.value.spans.isEmpty())
    }

    @Test
    fun `a token reports the field it belongs to, so the UI can colour it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val index = vm.tokenIndex("1,240.50")
        vm.onTokenTapped(index)
        vm.onFieldChosen(FieldKind.AMOUNT)

        assertEquals(FieldKind.AMOUNT, vm.state.value.kindOfToken(index))
        assertNull(vm.state.value.kindOfToken(0))
    }

    @Test
    fun `saving is blocked while a run is selected but unlabelled`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))
        vm.onFieldChosen(FieldKind.AMOUNT)
        assertTrue(vm.state.value.canSave)

        vm.onTokenTapped(vm.tokenIndex("DESCO"))

        assertFalse("an unlabelled selection is an unfinished thought", vm.state.value.canSave)
    }

    @Test
    fun `a saved rule is appended below every existing rule so it cannot outrank an IGNORE`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTokenTapped(vm.tokenIndex("Tk"))
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))
        vm.onFieldChosen(FieldKind.AMOUNT)

        vm.onSave()
        advanceUntilIdle()

        val rules = db.parsingRuleDao().allIncludingDisabled()
        val saved = rules.single { it.origin == "USER" }
        val highestIgnore = rules.filter { it.kind == RuleKind.IGNORE }.maxOf { it.priority }
        assertTrue("a user rule must never outrank an OTP rule", saved.priority > highestIgnore)
    }

    @Test
    fun `saving the rule retroactively records the message it was written for`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTokenTapped(vm.tokenIndex("Tk"))
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))
        vm.onFieldChosen(FieldKind.AMOUNT)
        vm.onTokenTapped(vm.tokenIndex("DESCO"))
        vm.onTokenTapped(vm.tokenIndex("88117"))
        vm.onFieldChosen(FieldKind.MERCHANT)

        vm.onSave()
        advanceUntilIdle()

        // The whole point of the flow: a rule written now fixes history.
        val recorded = db.transactionDao().allActive()
        assertEquals(1, recorded.size)
        assertEquals(124050L, recorded.single().amountMinor)
        assertEquals("DESCO METER 88117", recorded.single().merchantRaw)
        assertEquals(0, db.rawMessageDao().countByStatus(RawMessageStatus.UNMATCHED))
    }

    @Test
    fun `the direction chosen is the direction recorded`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))
        vm.onFieldChosen(FieldKind.AMOUNT)
        vm.onDirectionChanged(TransactionDirection.CREDIT)

        vm.onSave()
        advanceUntilIdle()

        assertEquals(TransactionDirection.CREDIT, db.transactionDao().allActive().single().direction)
    }

    @Test
    fun `the reparse outcome is reported back rather than happening silently`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))
        vm.onFieldChosen(FieldKind.AMOUNT)

        vm.onSave()
        advanceUntilIdle()

        assertNotNull(vm.state.value.reparseSummary)
        assertTrue(vm.state.value.reparseSummary!!.contains("recorded"))
    }

    @Test
    fun `a blank name blocks saving`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))
        vm.onFieldChosen(FieldKind.AMOUNT)

        vm.onNameChanged("   ")

        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `the save button says what it is doing rather than sitting on Saving`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Save and re-read history", vm.state.value.saveLabel)
    }

    @Test
    fun `re-reading history reports its progress`() = runTest(dispatcher) {
        // Re-reading every stored message takes the better part of a minute on a
        // real inbox; a button stuck on "Saving..." for that long reads as a hang.
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))
        vm.onFieldChosen(FieldKind.AMOUNT)

        val seen = mutableListOf<String>()
        backgroundScope.launch { vm.state.collect { seen += it.saveLabel } }
        vm.onSave()
        advanceUntilIdle()

        assertTrue(
            "expected a counting label, saw $seen",
            seen.any { it.startsWith("Re-reading ") },
        )
    }

    @Test
    fun `progress is cleared once the run is done`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTokenTapped(vm.tokenIndex("1,240.50"))
        vm.onFieldChosen(FieldKind.AMOUNT)

        vm.onSave()
        advanceUntilIdle()

        assertNull(vm.state.value.progress)
    }
}
