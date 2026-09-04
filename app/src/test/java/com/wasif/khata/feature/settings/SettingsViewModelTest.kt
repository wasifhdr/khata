package com.wasif.khata.feature.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.backup.BackupRepository
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.data.repository.TransactionRepositoryImpl
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.permission.SmsPermissionChecker
import com.wasif.khata.core.permission.SmsPermissionRepository
import com.wasif.khata.core.permission.SmsPermissionState
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.drive.DriveAuth
import com.wasif.khata.core.drive.DriveBackups
import com.wasif.khata.core.drive.DriveFile
import com.wasif.khata.core.drive.UploadOutcome
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.sms.IngestSummary
import com.wasif.khata.core.sms.IngestionPipeline
import com.wasif.khata.core.sms.IngestionScheduler
import com.wasif.khata.core.sms.RuleEngine
import com.wasif.khata.core.sms.TransferPairing
import com.wasif.khata.core.notify.TransferNotifier
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import androidx.work.testing.WorkManagerTestInitHelper
import org.robolectric.RobolectricTestRunner

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
        override suspend fun setBackfilled() = Unit
        override suspend fun setGeminiKey(key: String?) = Unit
        override suspend fun setBackupPassphrase(passphrase: String?) = Unit
        override suspend fun setDriveConnected(connected: Boolean) = Unit
        override suspend fun setDriveFolderId(id: String?) = Unit
        override suspend fun setDriveUploaded(at: Long) = Unit
        override suspend fun setDriveNeedsReconnect() = Unit
        override suspend fun setSearchIndexVersion(version: Int) = Unit
    }

    private lateinit var db: KhataDatabase
    private lateinit var pipeline: IngestionPipeline
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
        // The app removes WorkManager's own initializer, so a test that builds the
        // ViewModel has to stand one up itself -- observePass() asks for the
        // instance the moment the state flow is assembled.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
        )
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
            pairing = TransferPairing(
                db.transactionDao(),
                TransferNotifier(ApplicationProvider.getApplicationContext()),
                clock,
            ),
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
        smsPermission = SmsPermissionRepository(checker, repo),
        scheduler = IngestionScheduler(ApplicationProvider.getApplicationContext()),
        backups = BackupRepository(ApplicationProvider.getApplicationContext(), db, clock),
        rawMessageDao = db.rawMessageDao(),
        accountDao = db.accountDao(),
        transactions = TransactionRepositoryImpl(
            db, db.transactionDao(), db.accountDao(), db.merchantDao(), searchIndex(db), clock,
        ),
        clock = clock,
        driveAuth = DriveAuth(ApplicationProvider.getApplicationContext(), repo),
        // No test here reaches Drive; a fake keeps Play Services out of the JVM.
        driveBackups = object : DriveBackups {
            override suspend fun list(): List<DriveFile> = emptyList()
            override suspend fun download(id: String): ByteArray? = null
        },
        driveUploader = { UploadOutcome.SKIPPED },
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

    // Backfill's own behaviour -- progress, classification, and that re-running
    // reports duplicates rather than doubling the ledger -- is BackfillProgressTest's,
    // and the worker that now carries it is IngestionWorkerTest's. Driving it through
    // this ViewModel tested the same thing through a longer pipe, and the ViewModel
    // no longer runs the pass at all: it relays what the work reports.

    @Test
    fun `a finished run is put in words a person can act on`() {
        val summary = IngestSummary(total = 4, recorded = 1, updated = 2, ignored = 1)

        // The words are what the MESSAGES row shows when a pass finishes, and a
        // zero must not appear as "0 updated" noise.
        assertEquals("1 recorded · 2 updated · 1 ignored", summary.inWords())
    }

    @Test
    fun `an unreadable message is counted so the row can say how many are waiting`() = runTest(dispatcher) {
        pipeline.ingest("bKash", "Some entirely new wording nobody planned for", 1000L)
        val vm = viewModel()
        backgroundScope.launch { vm.ingestion.collect { } }
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
