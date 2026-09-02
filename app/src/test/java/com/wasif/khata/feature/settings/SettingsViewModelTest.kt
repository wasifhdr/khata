package com.wasif.khata.feature.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.repository.TransactionRepositoryImpl
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.permission.SmsPermissionChecker
import com.wasif.khata.core.permission.SmsPermissionRepository
import com.wasif.khata.core.permission.SmsPermissionState
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.sms.BackfillUseCase
import com.wasif.khata.core.sms.IncomingMessage
import com.wasif.khata.core.sms.IngestionPipeline
import com.wasif.khata.core.sms.MessageSource
import com.wasif.khata.core.sms.ReparseUseCase
import com.wasif.khata.core.sms.RuleEngine
import com.wasif.khata.core.sms.TransferPairing
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A shape the seeded bKash rules do match, so backfill records rather than shrugs. */
private const val PAYMENT =
    "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. " +
        "Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01"

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val prefs = MutableStateFlow(KhataPreferences.Default)
    private var lastSaved: ThemeSpec? = null
    private var resetCalled = false

    private val repo = object : PreferencesRepository {
        override val preferences: Flow<KhataPreferences> = prefs
        override suspend fun setTheme(spec: ThemeSpec) {
            lastSaved = spec
        }
        override suspend fun resetTheme() {
            resetCalled = true
        }
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
        override suspend fun setSmsPermissionRequested() = Unit
    }

    private lateinit var db: KhataDatabase
    private lateinit var pipeline: IngestionPipeline
    private var inbox: List<IncomingMessage> = emptyList()
    private var granted = true
    private var rationale = false

    private val clock = object : KhataClock {
        override fun now(): Long = 9_000L
    }

    private val checker = object : SmsPermissionChecker {
        override fun isGranted(): Boolean = granted
        override fun shouldShowRationale(): Boolean = rationale
    }

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        )
            // Room otherwise delivers Flow results on its own executor, which
            // advanceUntilIdle() cannot drive.
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
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(
        repository = repo,
        smsPermission = SmsPermissionRepository(
            checker = checker,
            hasRequested = prefs.map { it.hasRequestedSmsPermission },
            markRequested = { },
        ),
        backfill = BackfillUseCase(
            source = object : MessageSource {
                override suspend fun readAll(): List<IncomingMessage> = inbox
            },
            pipeline = pipeline,
        ),
        reparse = ReparseUseCase(db.rawMessageDao(), pipeline, clock),
        rawMessageDao = db.rawMessageDao(),
        accountDao = db.accountDao(),
        transactions = TransactionRepositoryImpl(
            db, db.transactionDao(), db.accountDao(), db.merchantDao(), clock,
        ),
        clock = clock,
    )

    @Test
    fun `changing one axis preserves the other three`() = runTest(dispatcher) {
        prefs.value = KhataPreferences.Default.copy(
            themeSpec = ThemeSpec.Default.copy(intensity = FieldIntensity.Dim),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAccentSelected(KhataPalette.accents[2].color)
        advanceUntilIdle()

        assertEquals(KhataPalette.accents[2].color, lastSaved?.accent)
        // The tuner edits one axis at a time; the rest must survive untouched.
        assertEquals(FieldIntensity.Dim, lastSaved?.intensity)
        assertEquals(ThemeSpec.Default.field, lastSaved?.field)
    }

    @Test
    fun `reset delegates rather than writing the default itself`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onResetTheme()
        advanceUntilIdle()

        // Reset must clear the stored keys, not write today's default as an
        // explicit value -- otherwise a later change of default is invisible to
        // anyone who ever pressed reset.
        assertTrue(resetCalled)
    }

    // --- The MESSAGES section ---------------------------------------------

    @Test
    fun `backfill reports progress rather than only a final answer`() = runTest(dispatcher) {
        inbox = List(3) { IncomingMessage("bKash", PAYMENT.replace("DHV41FIPGY", "TRX0000$it"), 1000L + it) }
        val vm = viewModel()
        backgroundScope.launch { vm.ingestion.collect { } }
        advanceUntilIdle()

        vm.onBackfill()
        advanceUntilIdle()

        // A multi-year inbox blocking silently reads as a hang, so the count is
        // what the screen has to be able to show while it works.
        val done = vm.ingestion.value.backfill!!
        assertEquals(3, done.total)
        assertEquals(3, done.processed)
        assertTrue(done.isComplete)
    }

    @Test
    fun `the run is reported in words a person can act on`() = runTest(dispatcher) {
        inbox = listOf(IncomingMessage("bKash", PAYMENT, 1000L))
        val vm = viewModel()
        backgroundScope.launch { vm.ingestion.collect { } }
        advanceUntilIdle()

        vm.onBackfill()
        advanceUntilIdle()

        assertEquals("1 recorded", vm.ingestion.value.lastRun)
    }

    @Test
    fun `a second backfill reports duplicates rather than doubling the ledger`() = runTest(dispatcher) {
        inbox = listOf(IncomingMessage("bKash", PAYMENT, 1000L))
        val vm = viewModel()
        backgroundScope.launch { vm.ingestion.collect { } }
        advanceUntilIdle()
        vm.onBackfill()
        advanceUntilIdle()

        vm.onBackfill()
        advanceUntilIdle()

        // The row it says "safe to run twice" under has to actually be safe.
        assertEquals(1, db.transactionDao().allActive().size)
    }

    @Test
    fun `an unreadable message is counted so the row can say how many are waiting`() = runTest(dispatcher) {
        inbox = listOf(IncomingMessage("bKash", "Some entirely new wording nobody planned for", 1000L))
        val vm = viewModel()
        backgroundScope.launch { vm.ingestion.collect { } }
        advanceUntilIdle()

        vm.onBackfill()
        advanceUntilIdle()

        assertEquals(1, vm.ingestion.value.unmatchedCount)
    }

    @Test
    fun `permission state reaches the screen`() = runTest(dispatcher) {
        granted = false
        prefs.value = KhataPreferences.Default.copy(hasRequestedSmsPermission = true)
        rationale = true
        val vm = viewModel()
        backgroundScope.launch { vm.ingestion.collect { } }
        advanceUntilIdle()

        assertEquals(SmsPermissionState.DENIED, vm.ingestion.value.permission)
    }

    @Test
    fun `nothing is in flight before a run starts`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.ingestion.collect { } }
        advanceUntilIdle()

        assertFalse(vm.ingestion.value.isWorking)
    }
}
