# Khata Plan — Ingestion as Durable Work

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backfill, reparse, and single-message ingestion run as WorkManager jobs that survive the screen dying, with progress the Settings screen can read whether or not it started the pass.

**Architecture:** One `IngestionWorker` with a `mode` input rather than three workers of the same collect-and-publish loop. Whole-inbox passes go under one unique name with `KEEP`, which replaces an in-memory concurrency guard with a structural one. Progress travels as `Data` through `setProgress`, and the final summary as the worker's output. Five tasks: the dependency and the `Data` round-trip, the worker, the scheduler and the Settings rewiring, the first-launch backfill, and the receiver.

**Tech Stack:** Kotlin · WorkManager 2.11.2 · Hilt + `androidx.hilt:hilt-work` 1.4.0 · Room · Coroutines/Flow · JUnit4 + Robolectric + `work-testing`.

**Spec:** `docs/superpowers/specs/2026-09-03-khata-background-work-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`, `compileSdk` / `targetSdk 37`, package `com.wasif.khata`. Target device: Pixel 6a, Android 17.
- Money is always `Long` **paisa**. Instants are UTC epoch millis; boundaries in `Asia/Dhaka`.
- **Raw messages are never deleted. Parsing stays idempotent and re-runnable** — this is what makes `Result.retry()` safe on a half-finished pass.
- Async is Coroutines/Flow — no `LiveData`. DI is Hilt + KSP.
- Derived UI values are getters on `UiState`, never constructor parameters.
- Durable state lives in `UiState`; one-shot imperatives go through an effects `Channel`.
- No colour literal outside `core/ui/theme` (`DESIGN.md` §1.1) — this plan touches no colour.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- Tests never hardcode a magic epoch-millis literal — build them with `Instant.parse("…Z").toEpochMilli()`.
- **No constraints on any work request.** No network, charging, or battery requirements — all of this is local and should run when asked (spec §9).
- **Ponytail is in force.** Reuse before writing: `BackfillUseCase`/`ReparseUseCase` already emit `Flow<IngestProgress>` and are not rewritten; `IngestSummary.inWords()` already exists for the summary line.
- Run unit tests with: `./gradlew :app:testDebugUnitTest`
- Build with: `./gradlew :app:assembleDebug`
- `export JAVA_HOME="/e/Android/Android Studio/jbr"` and `export PATH="$JAVA_HOME/bin:$PATH"` per shell.

## File Structure

**Create:**
- `app/src/main/java/com/wasif/khata/core/sms/IngestionWork.kt` — the `IngestionMode` enum, the `Data` keys, and the `IngestProgress` ↔ `Data` pair. Pure; no Android beyond `Data`.
- `app/src/main/java/com/wasif/khata/core/sms/IngestionWorker.kt` — the worker.
- `app/src/main/java/com/wasif/khata/core/sms/IngestionScheduler.kt` — the only thing that talks to `WorkManager`, plus `PassState`.
- Tests: `app/src/test/java/com/wasif/khata/core/sms/IngestionWorkTest.kt`, `app/src/test/java/com/wasif/khata/core/sms/IngestionWorkerTest.kt`

**Modify:**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — the dependencies.
- `app/src/main/java/com/wasif/khata/KhataApplication.kt` — `Configuration.Provider`, and the first-launch collector.
- `app/src/main/AndroidManifest.xml` — remove WorkManager's default initializer.
- `app/src/main/java/com/wasif/khata/feature/settings/SettingsViewModel.kt` — read `WorkInfo` instead of running the pass itself.
- `app/src/main/java/com/wasif/khata/core/prefs/KhataPreferences.kt`, `PreferencesRepository.kt`, `PreferencesRepositoryImpl.kt` — the `hasBackfilled` flag.
- `app/src/main/java/com/wasif/khata/core/sms/SmsReceiver.kt` — enqueue instead of parse.

---

### Task 1: The dependency, and the `Data` round-trip

Spec §4, §7. `Data` holds no nested objects, so `IngestProgress` is flattened into named ints. A field dropped in the flattening is silent — it shows up only as a progress bar that never fills — so this is the piece that gets a test.

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/java/com/wasif/khata/core/sms/IngestionWork.kt`
- Test: `app/src/test/java/com/wasif/khata/core/sms/IngestionWorkTest.kt`

**Interfaces:**
- Consumes: `IngestProgress(processed: Int, total: Int, summary: IngestSummary)` and `IngestSummary(total, recorded, updated, ignored, unmatched, duplicates, notMine)`, all `Int`, from `core/sms/Ingestion.kt`.
- Produces: `enum class IngestionMode { BACKFILL, REPARSE, MESSAGE }`; the constants `KEY_MODE`, `KEY_SENDER`, `KEY_BODY`, `KEY_RECEIVED_AT`; `fun IngestProgress.toData(): Data`; `fun Data.toIngestProgress(): IngestProgress?`. Tasks 2 and 3 use all of these.

- [ ] **Step 1: Add the dependencies**

In `gradle/libs.versions.toml`, under `[versions]`, add `work` and rename `hiltNav`. The rename is the point: `hilt-work`, `hilt-compiler` and `hilt-navigation-compose` are one group at one version, and three separate refs is how they drift apart.

```toml
work = "2.11.2"
```

Replace the line `hiltNav = "1.4.0"` with:

```toml
androidxHilt = "1.4.0"
```

Under `[libraries]`, replace the `androidx-hilt-navigation-compose` line with these four:

```toml
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "androidxHilt" }
androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "androidxHilt" }
androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "androidxHilt" }
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { module = "androidx.work:work-testing", version.ref = "work" }
```

In `app/build.gradle.kts`, in `dependencies`, after the Hilt block:

```kotlin
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)
  testImplementation(libs.androidx.work.testing)
```

`androidx.hilt:hilt-compiler` is a *separate* processor from Dagger's `hilt-android-compiler` already present; both are needed, and neither replaces the other.

- [ ] **Step 2: Write the failing test**

```kotlin
package com.wasif.khata.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngestionWorkTest {

    @Test
    fun `progress survives a trip through Data intact`() {
        val progress = IngestProgress(
            processed = 41,
            total = 120,
            summary = IngestSummary(
                total = 41,
                recorded = 12,
                updated = 3,
                ignored = 9,
                unmatched = 7,
                duplicates = 6,
                notMine = 4,
            ),
        )

        assertEquals(progress, progress.toData().toIngestProgress())
    }

    // The summary's own `total` and the progress's `total` are different numbers
    // that would collide under one key -- a backfill would then report its
    // denominator as its processed count and the bar would sit full from the
    // first message.
    @Test
    fun `the two totals do not collide`() {
        val progress = IngestProgress(
            processed = 1,
            total = 999,
            summary = IngestSummary(total = 1),
        )

        val restored = progress.toData().toIngestProgress()!!

        assertEquals(999, restored.total)
        assertEquals(1, restored.summary.total)
    }

    @Test
    fun `data carrying no progress reads as none rather than as zero`() {
        // A worker that has not called setProgress yet emits empty Data. Zero
        // progress and no progress are different answers: one draws a bar at the
        // start, the other draws nothing.
        assertNull(androidx.work.Data.EMPTY.toIngestProgress())
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*IngestionWorkTest*"`
Expected: FAIL — "Unresolved reference 'toData'".

- [ ] **Step 4: Write the round-trip**

Create `IngestionWork.kt`:

```kotlin
package com.wasif.khata.core.sms

import androidx.work.Data
import androidx.work.workDataOf

enum class IngestionMode { BACKFILL, REPARSE, MESSAGE }

const val KEY_MODE = "mode"
const val KEY_SENDER = "sender"
const val KEY_BODY = "body"
const val KEY_RECEIVED_AT = "received_at"

private const val KEY_PROCESSED = "processed"
private const val KEY_TOTAL = "total"
private const val KEY_SUMMARY_TOTAL = "summary_total"
private const val KEY_RECORDED = "recorded"
private const val KEY_UPDATED = "updated"
private const val KEY_IGNORED = "ignored"
private const val KEY_UNMATCHED = "unmatched"
private const val KEY_DUPLICATES = "duplicates"
private const val KEY_NOT_MINE = "not_mine"

/** Present only once a pass has reported something, which is what absence means. */
private const val KEY_PRESENT = "present"

fun IngestProgress.toData(): Data = workDataOf(
    KEY_PRESENT to true,
    KEY_PROCESSED to processed,
    KEY_TOTAL to total,
    KEY_SUMMARY_TOTAL to summary.total,
    KEY_RECORDED to summary.recorded,
    KEY_UPDATED to summary.updated,
    KEY_IGNORED to summary.ignored,
    KEY_UNMATCHED to summary.unmatched,
    KEY_DUPLICATES to summary.duplicates,
    KEY_NOT_MINE to summary.notMine,
)

/**
 * Null when the Data carries no progress at all -- a worker that has been
 * enqueued but has not yet reported. Zero progress and no progress draw
 * differently, so they must not collapse into each other here.
 */
fun Data.toIngestProgress(): IngestProgress? {
    if (!getBoolean(KEY_PRESENT, false)) return null
    return IngestProgress(
        processed = getInt(KEY_PROCESSED, 0),
        total = getInt(KEY_TOTAL, 0),
        summary = IngestSummary(
            total = getInt(KEY_SUMMARY_TOTAL, 0),
            recorded = getInt(KEY_RECORDED, 0),
            updated = getInt(KEY_UPDATED, 0),
            ignored = getInt(KEY_IGNORED, 0),
            unmatched = getInt(KEY_UNMATCHED, 0),
            duplicates = getInt(KEY_DUPLICATES, 0),
            notMine = getInt(KEY_NOT_MINE, 0),
        ),
    )
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*IngestionWorkTest*"`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/wasif/khata/core/sms/IngestionWork.kt \
        app/src/test/java/com/wasif/khata/core/sms/IngestionWorkTest.kt
git commit -m "feat: WorkManager, and progress that survives a trip through Data"
```

---

### Task 2: The worker

Spec §2, §7. One worker, three modes. The Hilt wiring lands here because the worker is the thing that needs it.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/IngestionWorker.kt`
- Modify: `app/src/main/java/com/wasif/khata/KhataApplication.kt`, `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/wasif/khata/core/sms/IngestionWorkerTest.kt`

**Interfaces:**
- Consumes: `IngestionMode`, `KEY_MODE`, `KEY_SENDER`, `KEY_BODY`, `KEY_RECEIVED_AT`, `toData()` (Task 1); `BackfillUseCase.run(): Flow<IngestProgress>`, `ReparseUseCase.run(): Flow<IngestProgress>`, `IngestionPipeline.ingest(sender: String, body: String, receivedAt: Long): IngestResult`.
- Produces: `IngestionWorker`, enqueued by name in Task 3. Its output `Data` carries the final `IngestProgress` on success.

- [ ] **Step 1: Write the failing test**

`TestListenableWorkerBuilder` needs a factory that hands the worker its dependencies. Build the pipeline exactly as `BackfillProgressTest` already does — same DAOs, same seeder, same fake clock.

```kotlin
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
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
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

@RunWith(RobolectricTestRunner::class)
class IngestionWorkerTest {

    private lateinit var db: KhataDatabase
    private lateinit var pipeline: IngestionPipeline
    private lateinit var backfill: BackfillUseCase
    private lateinit var reparse: ReparseUseCase

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
                        IngestionWorker(appContext, workerParameters, backfill, reparse, pipeline)
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

    // Retry, not failure: parsing is idempotent and re-runnable, so a pass that
    // died halfway can simply be run again. Failure would discard the work.
    @Test
    fun `a pass that throws asks to be retried`() = runTest {
        db.close()

        val result = worker(KEY_MODE to IngestionMode.BACKFILL.name).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*IngestionWorkerTest*"`
Expected: FAIL — "Unresolved reference 'IngestionWorker'".

- [ ] **Step 3: Write the worker**

```kotlin
package com.wasif.khata.core.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow

@HiltWorker
class IngestionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backfill: BackfillUseCase,
    private val reparse: ReparseUseCase,
    private val pipeline: IngestionPipeline,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        when (inputData.getString(KEY_MODE)?.let(IngestionMode::valueOf)) {
            IngestionMode.BACKFILL -> runPass(backfill.run())
            IngestionMode.REPARSE -> runPass(reparse.run())
            IngestionMode.MESSAGE -> {
                pipeline.ingest(
                    inputData.getString(KEY_SENDER).orEmpty(),
                    inputData.getString(KEY_BODY).orEmpty(),
                    inputData.getLong(KEY_RECEIVED_AT, 0L),
                )
                Data.EMPTY
            }
            // An unreadable mode is a programming error, not a transient one --
            // retrying it forever would be the wrong answer.
            null -> return Result.failure()
        }
    }.fold(
        onSuccess = { Result.success(it) },
        // Retry rather than failure: parsing is idempotent and re-runnable, so a
        // pass that died halfway is safe to run again from the top.
        onFailure = { Result.retry() },
    )

    /** Publishes as it goes, and hands the finished tally back as output. */
    private suspend fun runPass(pass: Flow<IngestProgress>): Data {
        var last: IngestProgress? = null
        pass.collect {
            last = it
            setProgress(it.toData())
        }
        return last?.toData() ?: Data.EMPTY
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*IngestionWorkerTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Wire the Hilt worker factory**

In `KhataApplication.kt`, implement `Configuration.Provider` and hand over the injected factory. Add to the class:

```kotlin
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
```

Change the declaration to `class KhataApplication : Application(), Configuration.Provider`, and add the imports `androidx.hilt.work.HiltWorkerFactory` and `androidx.work.Configuration`.

- [ ] **Step 6: Remove WorkManager's own initializer**

In `AndroidManifest.xml`, add `xmlns:tools="http://schemas.android.com/tools"` to the `<manifest>` element, then add inside `<application>`:

```xml
        <!--
          WorkManager also initialises itself through androidx.startup. Left in
          place it wins the race and initialises with the default factory, and
          every @HiltWorker then fails to construct at run time with nothing
          wrong at compile time. Only the WorkManagerInitializer meta-data is
          removed -- tools:node="remove" on the provider itself would silently
          take every other startup initializer with it.
        -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

- [ ] **Step 7: Build and run the whole suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/sms/IngestionWorker.kt \
        app/src/main/java/com/wasif/khata/KhataApplication.kt \
        app/src/main/AndroidManifest.xml \
        app/src/test/java/com/wasif/khata/core/sms/IngestionWorkerTest.kt
git commit -m "feat(sms): one worker, three modes, retried rather than failed"
```

---

### Task 3: The scheduler, and Settings reading WorkInfo

Spec §3, §4. The unique name makes the concurrency guard structural, and `SettingsViewModel` stops running the pass itself.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/IngestionScheduler.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/settings/SettingsViewModel.kt`

**Interfaces:**
- Consumes: `IngestionWorker` (Task 2), `IngestionMode`, `KEY_*`, `toIngestProgress()` (Task 1).
- Produces: `IngestionScheduler` with `backfill()`, `reparse()`, `message(sender: String, body: String, receivedAt: Long)`, and `observePass(): Flow<PassState>`; plus `data class PassState(val running: IngestProgress?, val finished: IngestSummary?)`. Task 4 calls `backfill()`; Task 5 calls `message(...)`.

- [ ] **Step 1: Write the scheduler**

There is no unit test in this step, and that is deliberate: every line here is a call into `WorkManager`, so a test would have to fake `WorkManager` and would then only assert that the fake was called. Spec §8 says this is verified by hand. The behaviour that *can* be tested — the worker, the round-trip — already is.

```kotlin
package com.wasif.khata.core.sms

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a whole-inbox pass is doing, and what the last one came to. */
data class PassState(
    val running: IngestProgress? = null,
    val finished: IngestSummary? = null,
)

@Singleton
class IngestionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun backfill() = enqueuePass(IngestionMode.BACKFILL)

    fun reparse() = enqueuePass(IngestionMode.REPARSE)

    /**
     * Deliberately not under the unique name: a message arriving during a backfill
     * must not be dropped for colliding with it, and one message contends for
     * nothing.
     */
    fun message(sender: String, body: String, receivedAt: Long) {
        workManager.enqueue(
            OneTimeWorkRequestBuilder<IngestionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_MODE to IngestionMode.MESSAGE.name,
                        KEY_SENDER to sender,
                        KEY_BODY to body,
                        KEY_RECEIVED_AT to receivedAt,
                    ),
                )
                .build(),
        )
    }

    /**
     * By unique name rather than by id, so a screen that did not start the pass can
     * still report it -- which is the whole point of moving off an in-memory flow.
     */
    fun observePass(): Flow<PassState> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_PASS).map { infos ->
            val info = infos.lastOrNull() ?: return@map PassState()
            when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED ->
                    PassState(running = info.progress.toIngestProgress())
                WorkInfo.State.SUCCEEDED ->
                    PassState(finished = info.outputData.toIngestProgress()?.summary)
                else -> PassState()
            }
        }

    /**
     * KEEP, under one name for both passes. Two whole-inbox passes would race on
     * the same tables, and the guard this replaces lived in a ViewModel -- so it
     * was forgotten the moment the screen died.
     */
    private fun enqueuePass(mode: IngestionMode) {
        workManager.enqueueUniqueWork(
            UNIQUE_PASS,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IngestionWorker>()
                .setInputData(workDataOf(KEY_MODE to mode.name))
                .build(),
        )
    }

    private companion object {
        const val UNIQUE_PASS = "ingestion-pass"
    }
}
```

- [ ] **Step 2: Rewire SettingsViewModel**

Replace the `backfill`, `reparse` constructor parameters with `private val scheduler: IngestionScheduler`, and delete the `_backfill` and `_lastRun` `MutableStateFlow`s and the whole `runPass` function.

The parameter is `scheduler`, not `ingestion`, because the public `StateFlow` is already called `ingestion` and `SettingsScreen.kt:80` reads it by that name — keeping it means the screen is not touched at all.

The `ingestion` `StateFlow` becomes:

```kotlin
    val ingestion: StateFlow<IngestionState> = combine(
        smsPermission.observe(),
        // Drives the row's own count, so it says how much is waiting before you open it.
        rawMessageDao.observeByStatus(RawMessageStatus.UNMATCHED).map { it.size },
        scheduler.observePass(),
        _lastAction,
        accountDao.observeAll().map { accounts ->
            Money(accounts.firstOrNull { it.type == AccountType.CASH }?.currentBalanceMinor ?: 0L)
        },
    ) { permission, unmatched, pass, lastAction, cash ->
        IngestionState(
            permission = permission,
            unmatchedCount = unmatched,
            backfill = pass.running,
            // A finished pass speaks for itself; _lastAction only carries what the
            // pass cannot say, like the cash reset.
            lastRun = pass.finished?.inWords() ?: lastAction,
            cashBalance = cash,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IngestionState(),
    )
```

Rename the surviving `_lastRun` to `_lastAction` (it now carries only the cash-reset messages, not pass outcomes) and update the two `onResetCash` assignments to it. Then the two triggers become one line each — the KEEP policy is the guard the old early-return used to be:

```kotlin
    fun onBackfill() = scheduler.backfill()

    fun onReparse() = scheduler.reparse()
```

Delete the now-unused imports of `BackfillUseCase`, `ReparseUseCase` and `Flow`, and keep `IngestSummary.inWords()` where it is at the bottom of the file — it is still what turns a finished pass into the row's text.

`SettingsScreen.kt` needs no change.

- [ ] **Step 3: Build and run the suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass. `SettingsViewModel` has no test of its own today, so nothing should break; if the compiler reports an unused parameter or a missing import, fix it before moving on.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/sms/IngestionScheduler.kt \
        app/src/main/java/com/wasif/khata/feature/settings/SettingsViewModel.kt \
        app/src/main/java/com/wasif/khata/feature/settings/SettingsScreen.kt
git commit -m "feat: passes run as unique work, and Settings reads what is running"
```

---

### Task 4: The first-launch backfill

Spec §5. A fresh install shows an empty app until the user finds the button.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/prefs/KhataPreferences.kt`, `PreferencesRepository.kt`, `PreferencesRepositoryImpl.kt`, `app/src/main/java/com/wasif/khata/KhataApplication.kt`
- Test: `app/src/test/java/com/wasif/khata/core/prefs/PreferencesRepositoryTest.kt`

**Interfaces:**
- Consumes: `IngestionScheduler.backfill()` (Task 3), `SmsPermissionRepository.observe(): Flow<SmsPermissionState>`.
- Produces: `KhataPreferences.hasBackfilled: Boolean` and `PreferencesRepository.setBackfilled()`.

- [ ] **Step 1: Write the failing test**

Append to `PreferencesRepositoryTest`. The repository field in that file is named `repo`, and it needs two imports it does not yet have: `org.junit.Assert.assertFalse` and `org.junit.Assert.assertTrue`.

```kotlin
    @Test
    fun `the backfill flag defaults to false and survives being set`() = runTest {
        assertFalse(repo.preferences.first().hasBackfilled)

        repo.setBackfilled()

        assertTrue(repo.preferences.first().hasBackfilled)
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*PreferencesRepositoryTest*"`
Expected: FAIL — "Unresolved reference 'hasBackfilled'".

- [ ] **Step 3: Add the flag**

In `KhataPreferences.kt`, add to the data class after `hasRequestedSmsPermission`:

```kotlin
    /**
     * Guards the one automatic backfill. Not "is the ledger empty" -- that is also
     * the honest state of a user whose inbox holds no bank messages, and testing
     * for it would rescan the whole inbox on every launch, forever.
     */
    val hasBackfilled: Boolean = false,
```

and to the `Default` companion value: `hasBackfilled = false,`.

In `PreferencesRepository.kt`, add `suspend fun setBackfilled()`.

In `PreferencesRepositoryImpl.kt`, add to `Keys`:

```kotlin
        val Backfilled = intPreferencesKey("has_backfilled")
```

to the `map` that builds `KhataPreferences`:

```kotlin
                hasBackfilled = p[Keys.Backfilled] == 1,
```

and the setter:

```kotlin
    override suspend fun setBackfilled() {
        store.edit { it[Keys.Backfilled] = 1 }
    }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*PreferencesRepositoryTest*"`
Expected: PASS.

- [ ] **Step 5: Enqueue it once, from the application**

In `KhataApplication.kt`, add `@Inject lateinit var smsPermission: SmsPermissionRepository` and `@Inject lateinit var ingestion: IngestionScheduler`, then a third launch beside the seeder and the widget push:

```kotlin
        // The first backfill has to happen somewhere that is not a screen: a fresh
        // install that never opens Settings would otherwise never run one, which is
        // the case this exists to solve.
        applicationScope.launch {
            combine(preferences.preferences, smsPermission.observe()) { prefs, permission ->
                prefs.hasBackfilled to permission
            }.first { (backfilled, permission) -> !backfilled && permission.enablesCapture }
            ingestion.backfill()
            preferences.setBackfilled()
        }
```

Imports to add: `kotlinx.coroutines.flow.combine`, `kotlinx.coroutines.flow.first`, `com.wasif.khata.core.permission.SmsPermissionRepository`, `com.wasif.khata.core.sms.IngestionScheduler`.

`first { }` suspends until both conditions hold and then completes, so the collector ends itself rather than watching forever — and the flag is written immediately after enqueueing, so a KEEP-deduplicated second call cannot happen on the next launch.

- [ ] **Step 6: Build and run the suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/prefs/ \
        app/src/main/java/com/wasif/khata/KhataApplication.kt \
        app/src/test/java/com/wasif/khata/core/prefs/PreferencesRepositoryTest.kt
git commit -m "feat: the first backfill runs itself"
```

---

### Task 5: The receiver enqueues

Spec §6. A hardening, not a bug fix — the receiver already calls `goAsync()`. This removes the ten-second ceiling and adds retry with backoff.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/sms/SmsReceiver.kt`

**Interfaces:**
- Consumes: `IngestionScheduler.message(sender, body, receivedAt)` (Task 3).
- Produces: nothing further.

- [ ] **Step 1: Replace the inline parse**

The message extraction is unchanged — only what happens with it changes. `IngestionPipeline`, the private `CoroutineScope` and `goAsync()` all go, because enqueueing is a fast synchronous call that does not need the process held open.

```kotlin
package com.wasif.khata.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var ingestion: IngestionScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A multipart message arrives as several parts of one logical message; joining
        // the bodies keeps a long bank SMS parseable as a whole.
        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }

        // Enqueued rather than parsed here. goAsync() held the process for about ten
        // seconds, which was ample -- but a parse that outran it dropped the message,
        // recoverable only by a later backfill. Work retries instead, and needs no
        // window held open, because enqueueing returns immediately.
        ingestion.message(sender, body, messages.first().timestampMillis)
    }
}
```

- [ ] **Step 2: Build and run the whole suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Verify on hardware**

The Hilt worker factory, the unique-work policy and the first-launch enqueue are all wiring, and none of them can be asserted without faking the thing under test (spec §8). Install and check by hand:

1. Fresh install with SMS permission granted. A backfill starts on its own; Settings shows it progressing without having been opened first.
2. Open Settings mid-backfill, leave, and return. Progress is still reported and still climbing — this is the behaviour the whole plan exists for.
3. Press Backfill while one is running. Nothing is queued behind it and nothing restarts.
4. Press Reparse while a backfill runs. Same — one pass at a time.
5. Kill the app from recents mid-backfill. It keeps running and completes.
6. Second launch. No backfill starts.
7. Send the device an SMS matching a rule. It appears in the ledger.

Failures at step 1 or 5 with nothing in the log point at the manifest initializer (Task 2, Step 6): with WorkManager's own initializer left in, `@HiltWorker` construction fails at run time and the build says nothing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/sms/SmsReceiver.kt
git commit -m "feat(sms): an arriving message is enqueued, not parsed in the receiver"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` passes, including `IngestionWorkTest` and `IngestionWorkerTest`.
- The seven-step hardware walkthrough in Task 5 passes.
- `SettingsViewModel` no longer collects an ingestion pass, and no longer holds a concurrency guard.
- A backfill survives leaving the Settings screen, and survives the app being killed.

## Deferred

`balance_snapshots`, the schema v5 migration, the nightly 00:05 snapshot job, and moving the Wallet chart off its derived walk. All recorded in spec §1 as the second plan on this base.
