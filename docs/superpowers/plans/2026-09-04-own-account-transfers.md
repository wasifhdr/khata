# Own-Account Transfers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A movement between the owner's own accounts stops counting as spending, settled by asking the owner about the rare row the messages cannot explain.

**Architecture:** Pairing already recognises a movement that produced two messages. This plan handles the one-message case: a transfer-shaped row that is still unpaired three minutes later flags itself for review, a notification asks, and answering "yes" writes the mirror row on a chosen account so net worth stays correct. Nothing existing is re-classified — the feature starts from the day it ships.

**Tech Stack:** Kotlin, Room 2.8.4, Hilt, WorkManager, `NotificationCompat`, Compose, Robolectric + JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-04-own-account-transfers-design.md`

## Global Constraints

- **Forward-only. No backfill, ever.** The 101 transfer-shaped unpaired rows already in the ledger stay exactly as they are (spec §11). If you find yourself writing an `UPDATE` over history, stop — that is not this plan.
- **The prompt is gated on merchant text.** Prompting on every unpaired row is 10.7 questions/month against 1.4 for transfer-shaped rows only (spec §4). A change that widens the gate has to re-justify that number.
- **The 15-minute pairing window stays wider than the 3-minute prompt** (spec §5). A partner arriving at minute five must still pair, clear the flag, and retract the notification.
- **`SCHEMA_VERSION` in `core/backup/BackupRepository.kt` goes 9 → 10** alongside the Room version.
- Money is `Long` paisa. Times are UTC epoch millis, bucketed in `Asia/Dhaka` via `core/time/KhataClock.kt`'s `DHAKA`.
- Colour literals stay in `core/ui/theme` (DESIGN.md §1.1). Every Material role set explicitly (§1.6).
- Ponytail: shortest diff that works, no abstraction with one implementation, stdlib before dependencies.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `core/sms/TransferReview.kt` | `isTransferShaped` — the pure gate on which rows may ask |
| `core/sms/TransferReviewWorker.kt` | The three-minute check, and the scheduler that enqueues it |
| `core/notify/TransferNotifier.kt` | Channel, post, retract |
| `core/notify/TransferReviewReceiver.kt` | The notification's "No", answered without opening the app |
| `feature/settle/SettleTransferSheet.kt` | The sheet the "Yes" path opens |
| `feature/settle/SettleTransferViewModel.kt` | Its state and its two actions |
| `docs/superpowers/specs/transfer-shapes.md` | Corpus of merchant strings that must and must not ask |

**Modified**

| File | Change |
|---|---|
| `core/data/entity/TransactionEntity.kt` | `transferReviewPending` column |
| `core/data/KhataDatabase.kt` | `version = 10` |
| `core/data/migration/Migrations.kt` | `MIGRATION_9_10` |
| `core/data/di/DatabaseModule.kt` | Register it |
| `core/data/dao/TransactionDao.kt` | Pending queries; `markAsTransfer` clears the flag |
| `core/backup/BackupRepository.kt` | `SCHEMA_VERSION = 10` |
| `core/sms/IngestionPipeline.kt` | Schedule a review after writing a transfer-shaped row |
| `core/data/repository/TransactionRepositoryImpl.kt` | `settleAsOwnTransfer`, `dismissTransferReview` |
| `domain/repository/TransactionRepository.kt` | Their declarations |
| `feature/hub/ModulesUiState.kt`, `ModulesScreen.kt`, `ModulesViewModel.kt` | The dot on the Wallet card |
| `AndroidManifest.xml` | `POST_NOTIFICATIONS`, the receiver |

---

### Task 1: The column, and the queries that read it

Room generates the table; the migration must produce the same column or Room's identity check rejects the database at open time.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/data/entity/TransactionEntity.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/backup/BackupRepository.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/migration/Migration9To10Test.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `TransactionEntity.transferReviewPending: Boolean`; `TransactionDao.setReviewPending(id: Long, pending: Boolean, updatedAt: Long)`, `observePendingReviews(): Flow<List<TransactionEntity>>`, `observePendingReviewCount(): Flow<Int>`; `MIGRATION_9_10`.

- [ ] **Step 1: Add the column to the entity**

In `TransactionEntity.kt`, after the `counterparty` property:

```kotlin
    /**
     * Set when a transfer-shaped row went three minutes without a partner message and
     * the owner has not yet said whether it left their accounts. Cleared by either
     * answer, and by a partner arriving late.
     *
     * Not nullable and defaulted false, which is what makes the feature forward-only:
     * every row already in the database is silent the moment the migration runs.
     */
    val transferReviewPending: Boolean = false,
```

- [ ] **Step 2: Bump the Room version**

In `core/data/KhataDatabase.kt`, change `version = 9` to `version = 10`.

- [ ] **Step 3: Write the migration**

In `core/data/migration/Migrations.kt`, above `MIGRATION_8_9`:

```kotlin
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // DEFAULT 0 is the whole of "forward-only": every row that already exists is
        // settled as far as this feature is concerned, and none of them will ever ask.
        db.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `transferReviewPending` " +
                "INTEGER NOT NULL DEFAULT 0",
        )
    }
}

```

- [ ] **Step 4: Register the migration**

In `core/data/di/DatabaseModule.kt`, add the import
`import com.wasif.khata.core.data.migration.MIGRATION_9_10` and append `, MIGRATION_9_10`
to the `addMigrations(...)` call.

- [ ] **Step 5: Bump the backup stamp**

In `core/backup/BackupRepository.kt`: `const val SCHEMA_VERSION = 10`.

It is stamped into every archive and compared on restore, so leaving it at 9 makes a
v10 backup claim to be v9 and disables the "refuse a backup newer than this app" guard.

- [ ] **Step 6: Add the queries**

In `core/data/dao/TransactionDao.kt`, add:

```kotlin
    @Query(
        "UPDATE transactions SET transferReviewPending = :pending, updatedAt = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun setReviewPending(id: Long, pending: Boolean, updatedAt: Long)

    @Query(
        "SELECT * FROM transactions WHERE transferReviewPending = 1 AND deletedAt IS NULL " +
            "ORDER BY occurredAt DESC, id DESC",
    )
    fun observePendingReviews(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE transferReviewPending = 1 AND deletedAt IS NULL")
    fun observePendingReviewCount(): Flow<Int>
```

- [ ] **Step 7: Make a late pairing clear the flag**

Still in `TransactionDao.kt`, replace the body of `markAsTransfer`:

```kotlin
    // Clears the review flag as well as marking the pair. A partner arriving at minute
    // five answers the question that was asked at minute three, and a stale question
    // the owner can no longer answer correctly is worse than never asking.
    @Query(
        "UPDATE transactions SET transferGroupId = :groupId, kind = 'TRANSFER', " +
            "transferReviewPending = 0, updatedAt = :updatedAt WHERE id IN (:ids)"
    )
    suspend fun markAsTransfer(ids: List<Long>, groupId: String, updatedAt: Long)
```

- [ ] **Step 8: Build so Room exports the schema**

```bash
./gradlew :app:assembleDebug
```

This writes `app/schemas/com.wasif.khata.core.data.KhataDatabase/10.json`. If it fails
with an identity-hash complaint, Step 3's SQL disagrees with Step 1's entity.

- [ ] **Step 9: Write the migration test**

Copy `app/src/test/java/com/wasif/khata/core/data/migration/Migration8To9Test.kt` to
`Migration9To10Test.kt` and change: `TEST_DB` to `"migration-9-10-test.db"`, the class
name to `Migration9To10Test`, `createV8Database` to `createV9Database`, `schemaJson(8)`
to `schemaJson(9)`, `Callback(8)` to `Callback(9)`, `db.version = 8` to `db.version = 9`,
and `addMigrations(MIGRATION_8_9)` to `addMigrations(MIGRATION_9_10)`. Then replace the
test body:

```kotlin
    @Test
    fun `every existing row starts settled, because history is not backfilled`() = runTest {
        createV9Database().use { db ->
            db.execSQL(
                "INSERT INTO transactions (uuid, accountId, amountMinor, direction, kind, " +
                    "occurredAt, merchantRaw, note, counterparty, source, confidence, " +
                    "createdAt, updatedAt) VALUES " +
                    "('t1', 1, 500000, 'DEBIT', 'NORMAL', 1000, 'EBL Account Transfer', NULL, " +
                    "NULL, 'SMS', 'HIGH', 1000, 1000)",
            )
        }

        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()

        // Transfer-shaped, unpaired, and still silent: the ledger's past is left alone
        // on purpose, so nothing arrives asking about a transfer from four years ago.
        assertFalse(db.transactionDao().findById(1)!!.transferReviewPending)
        assertEquals(0, db.transactionDao().observePendingReviewCount().first())
        db.close()
    }
}
```

Add `import kotlinx.coroutines.flow.first`, `import org.junit.Assert.assertFalse` and
`import org.junit.Assert.assertEquals` if the copied file lacks them.

- [ ] **Step 10: Run it**

```bash
./gradlew :app:testDebugUnitTest --tests "*Migration9To10Test*"
```

Expected: PASS.

- [ ] **Step 11: Add the new migration to every older chain test**

`Migration1To2Test`, `Migration4To5Test`, `Migration5To6Test`, `Migration6To7Test`,
`Migration7To8Test` and `Migration8To9Test` each open the database through Room, which
now targets version 10. Append `, MIGRATION_9_10` to every `addMigrations(...)` in those
files, change `SupportSQLiteOpenHelper.Callback(9)` to `Callback(10)` in
`Migration1To2Test`, and add `MIGRATION_9_10.migrate(db)` after `MIGRATION_8_9.migrate(db)`
in its `onUpgrade`. The compiler will not catch these; the tests will.

- [ ] **Step 12: Run the whole suite**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: PASS. If Robolectric reports "Unable to load native runtime library", the
machine's temp drive is full — that is an environment fault, not a code one.

- [ ] **Step 13: Commit**

```bash
git add app/src app/schemas
git commit -m "feat(transfers): a column for the question not yet asked

transferReviewPending marks a transfer-shaped row that went three minutes
without a partner message and has not been settled by the owner. Defaulted
false and not backfilled, which is what keeps the feature forward-only: every
row already in the ledger is silent from the moment the migration runs.

markAsTransfer clears it, so a partner arriving after the question was asked
answers it rather than leaving it standing.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Which rows are allowed to ask

The gate that keeps this at 1.4 questions a month instead of 10.7. It is a pure
function over the merchant text, so it gets a corpus rather than opinions.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/TransferReview.kt`
- Create: `docs/superpowers/specs/transfer-shapes.md`
- Test: `app/src/test/java/com/wasif/khata/core/sms/TransferShapeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `fun isTransferShaped(merchantRaw: String?): Boolean`.

- [ ] **Step 1: Write the corpus**

`docs/superpowers/specs/transfer-shapes.md`:

```markdown
# Transfer shapes

Which merchant strings may raise a review, and which must never. `TransferShapeTest`
reads nothing from this file — the strings are repeated there verbatim, the way
`CorpusTest` repeats `sms-corpus.md` — but this is where the reasoning lives.

A string is transfer-shaped when the movement it names *could* have been between two
accounts the owner holds. It says nothing about whether it was; that is the question
being asked.

## Must ask

| String | Why |
|---|---|
| `EBL Account Transfer` | The ATM withdrawal and the EBL-to-EBL move both read exactly this |
| `EBL Skybanking MFS Transfer-bKash` | The owner's bKash, or a friend's |
| `Own Account Transfer` | Usually pairs on its own; asks when the partner never arrives |
| `NPSB FUND TRANSFER` | Inter-bank, either direction |
| `AC TRANSFER THROUGH EBL CONNECT` | Same |
| `bKash Cash Out` | Money to the Cash account, no second message |
| `ATM Withdrawal` | The canonical no-second-message movement |
| `Send Money` | bKash wallet to wallet |

## Must never ask

| String | Why |
|---|---|
| `UBER BANGLADESH LTD-UBER` | A ride is not a movement between accounts |
| `FOODPANDA BANGLADESH LIMITED` | Nor is dinner |
| `EBL Skybanking Mobile Recharge` | Contains neither shape; a recharge is spending |
| `North South University` | A fee is spending |
| `CINEPLEXBD` | Ditto |
| `null` / blank | Nothing to match on, so nothing to ask about |

The second table is the load-bearing one. Ten questions a month about dinner gets the
notification muted, and a muted question settles nothing.
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/wasif/khata/core/sms/TransferShapeTest.kt`:

```kotlin
package com.wasif.khata.core.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every literal here is copied verbatim from docs/superpowers/specs/transfer-shapes.md.
 */
class TransferShapeTest {

    @Test
    fun `a movement that could have been between my own accounts asks`() {
        listOf(
            "EBL Account Transfer",
            "EBL Skybanking MFS Transfer-bKash",
            "Own Account Transfer",
            "NPSB FUND TRANSFER",
            "AC TRANSFER THROUGH EBL CONNECT",
            "bKash Cash Out",
            "ATM Withdrawal",
            "Send Money",
        ).forEach { assertTrue("should ask about: $it", isTransferShaped(it)) }
    }

    @Test
    fun `an ordinary purchase never asks`() {
        // The load-bearing half. Ten questions a month about dinner gets the whole
        // feature muted, and a muted question settles nothing.
        listOf(
            "UBER BANGLADESH LTD-UBER",
            "FOODPANDA BANGLADESH LIMITED",
            "EBL Skybanking Mobile Recharge",
            "North South University",
            "CINEPLEXBD",
        ).forEach { assertFalse("should stay quiet about: $it", isTransferShaped(it)) }
    }

    @Test
    fun `nothing to match on is nothing to ask about`() {
        assertFalse(isTransferShaped(null))
        assertFalse(isTransferShaped(""))
        assertFalse(isTransferShaped("   "))
    }
}
```

- [ ] **Step 3: Run it and watch it fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*TransferShapeTest*"
```

Expected: FAIL, unresolved reference `isTransferShaped`.

- [ ] **Step 4: Write it**

`app/src/main/java/com/wasif/khata/core/sms/TransferReview.kt`:

```kotlin
package com.wasif.khata.core.sms

/**
 * The words a bank uses when money moves rather than is spent. Matched case-
 * insensitively against the merchant text.
 *
 * "Transfer" alone would be too wide if merchants used it, and in the measured corpus
 * they do not -- every string carrying it is a movement between accounts somewhere.
 * "Recharge" is the near miss the list must not catch: EBL Skybanking Mobile Recharge
 * is spending and contains none of these.
 */
private val TRANSFER_SHAPES = listOf(
    "transfer",
    "npsb",
    "atm",
    "withdraw",
    "cash out",
    "send money",
)

/**
 * Whether a row is worth asking the owner about when no partner message arrives.
 *
 * Deliberately narrow. Asking about every unpaired row is 10.7 questions a month
 * against 1.4 for these shapes alone, and the difference is whether the notification
 * survives its first week.
 */
fun isTransferShaped(merchantRaw: String?): Boolean {
    val text = merchantRaw?.lowercase() ?: return false
    return TRANSFER_SHAPES.any { it in text }
}
```

- [ ] **Step 5: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests "*TransferShapeTest*"
```

Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src docs
git commit -m "feat(transfers): which rows are allowed to ask

A pure matcher over the merchant text, with a corpus, because the list is a
product decision rather than a detail: prompting on every unpaired row is 10.7
questions a month, prompting on these shapes alone is 1.4, and the difference
decides whether the notification survives its first week.

The second corpus table is the load-bearing one. Dinner must never ask.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: The three-minute wait

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/TransferReviewWorker.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/sms/IngestionPipeline.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/wasif/khata/core/sms/TransferReviewWorkerTest.kt`

**Interfaces:**
- Consumes: `isTransferShaped` (Task 2), `TransactionDao.setReviewPending` (Task 1).
- Produces: `class TransferReviewWorker`; `fun interface ScheduleTransferReview { operator fun invoke(transactionId: Long) }`; `TransferReviewScheduler.schedule(transactionId: Long)`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/wasif/khata/core/sms/TransferReviewWorkerTest.kt`. Follow
`ReparseUseCaseTest.kt`'s `setUp` for the in-memory database fixture.

```kotlin
package com.wasif.khata.core.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferReviewWorkerTest {

    private lateinit var db: KhataDatabase
    private lateinit var review: TransferReview

    private val clock = object : KhataClock { override fun now(): Long = 9_000L }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        review = TransferReview(db.transactionDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(
        merchantRaw: String?,
        transferGroupId: String? = null,
    ): Long = db.transactionDao().upsert(
        TransactionEntity(
            uuid = UUID.randomUUID().toString(),
            accountId = 1,
            amountMinor = 500_000,
            direction = TransactionDirection.DEBIT,
            occurredAt = 1_000,
            merchantRaw = merchantRaw,
            merchantId = null,
            categoryId = null,
            note = null,
            counterparty = null,
            source = TransactionSource.SMS,
            confidence = Confidence.HIGH,
            kind = TransactionKind.NORMAL,
            rawMessageId = null,
            transferGroupId = transferGroupId,
            feeMinor = null,
            referenceNumber = null,
            createdAt = 1_000,
            updatedAt = 1_000,
        ),
    )

    @Test
    fun `a transfer-shaped row with no partner asks`() = runTest {
        val id = insert("EBL Account Transfer")

        review.reviewIfUnpaired(id)

        assertTrue(db.transactionDao().findById(id)!!.transferReviewPending)
    }

    @Test
    fun `a row that paired in the meantime asks nothing`() = runTest {
        // The partner message arrived inside the three minutes, so pairing already
        // answered the question and nothing should interrupt anybody.
        val id = insert("EBL Account Transfer", transferGroupId = "group-1")

        review.reviewIfUnpaired(id)

        assertFalse(db.transactionDao().findById(id)!!.transferReviewPending)
    }

    @Test
    fun `an ordinary purchase asks nothing`() = runTest {
        val id = insert("UBER BANGLADESH LTD-UBER")

        review.reviewIfUnpaired(id)

        assertFalse(db.transactionDao().findById(id)!!.transferReviewPending)
    }

    @Test
    fun `a deleted row asks nothing`() = runTest {
        val id = insert("EBL Account Transfer")
        db.transactionDao().softDelete(id, 2_000)

        review.reviewIfUnpaired(id)

        assertFalse(db.transactionDao().findById(id)?.transferReviewPending ?: false)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*TransferReviewWorkerTest*"
```

Expected: FAIL, unresolved reference `TransferReview`.

- [ ] **Step 3: Write the decision, separately from the worker**

Append to `app/src/main/java/com/wasif/khata/core/sms/TransferReview.kt`:

```kotlin
/**
 * The three-minute check itself, kept out of the worker so it can be tested without
 * WorkManager. The worker is then only a way of arriving here late.
 */
@Singleton
class TransferReview @Inject constructor(
    private val transactionDao: TransactionDao,
    private val clock: KhataClock,
) {
    /**
     * Flags the row for review unless something already answered the question: it
     * paired, it was deleted, or it was never transfer-shaped to begin with.
     */
    suspend fun reviewIfUnpaired(transactionId: Long) {
        val row = transactionDao.findById(transactionId) ?: return
        if (row.transferGroupId != null) return
        if (row.kind != TransactionKind.NORMAL) return
        if (!isTransferShaped(row.merchantRaw)) return

        transactionDao.setReviewPending(transactionId, pending = true, updatedAt = clock.now())
    }
}
```

Add the imports `com.wasif.khata.core.data.dao.TransactionDao`,
`com.wasif.khata.core.model.TransactionKind`, `com.wasif.khata.core.time.KhataClock`,
`javax.inject.Inject`, `javax.inject.Singleton`.

`findById` already filters `deletedAt IS NULL`, which is what makes the deleted-row
test pass without a second check.

- [ ] **Step 4: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests "*TransferReviewWorkerTest*"
```

Expected: PASS, 4 tests.

- [ ] **Step 5: Write the worker and its scheduler**

`app/src/main/java/com/wasif/khata/core/sms/TransferReviewWorker.kt`:

```kotlin
package com.wasif.khata.core.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_TRANSACTION_ID = "transactionId"

/**
 * Three minutes, measured rather than guessed: 137 of the 140 pairs in a real ledger
 * formed inside it (median 23s). The pairing window stays at fifteen, so a partner
 * arriving after this has run still pairs and clears the flag.
 */
internal const val REVIEW_DELAY_MINUTES = 3L

@HiltWorker
class TransferReviewWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val review: TransferReview,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_TRANSACTION_ID, -1L)
        if (id <= 0L) return Result.success()
        review.reviewIfUnpaired(id)
        return Result.success()
    }
}

@Singleton
class TransferReviewScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Named per transaction and KEEP rather than REPLACE: a reparse can write the same
     * row twice, and the second pass must not restart the clock on a question already
     * counting down.
     */
    fun schedule(transactionId: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "transfer-review-$transactionId",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TransferReviewWorker>()
                .setInitialDelay(REVIEW_DELAY_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_TRANSACTION_ID to transactionId))
                .build(),
        )
    }
}
```

- [ ] **Step 6: Schedule from ingestion**

`IngestionPipeline` cannot depend on the scheduler that runs the worker that calls the
pipeline, so it takes a function type — the same shape as `TeachRequest` beside it.

In `core/sms/IngestionScheduler.kt`, beside `TeachRequest`:

```kotlin
/**
 * Asking for a transfer-shaped row to be reviewed in three minutes, as a function
 * type, for the same reason TeachRequest is one: the pipeline must not hold the
 * scheduler that runs the worker that calls the pipeline.
 */
fun interface ScheduleTransferReview {
    operator fun invoke(transactionId: Long)
}
```

In `core/data/di/RepositoryModule.kt`, inside `companion object`:

```kotlin
        @Provides
        fun provideScheduleTransferReview(scheduler: TransferReviewScheduler) =
            ScheduleTransferReview { id -> scheduler.schedule(id) }
```

with the imports `com.wasif.khata.core.sms.ScheduleTransferReview` and
`com.wasif.khata.core.sms.TransferReviewScheduler`.

In `core/sms/IngestionPipeline.kt`, add the constructor parameter
`private val scheduleTransferReview: ScheduleTransferReview,` and, immediately after
the `pairing.pair(transactionId)` call site in `write(...)`:

```kotlin
            // Only when pairing did not already answer it. Scheduling regardless would
            // wake a worker three minutes later to discover it had nothing to do.
            if (isTransferShaped(parsed.merchant)) {
                scheduleTransferReview(transactionId)
            }
```

- [ ] **Step 7: Run everything**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: PASS. `IngestionPipelineTest` constructs the pipeline directly and will need
`ScheduleTransferReview {}` — a no-op lambda — added to its constructor call.

- [ ] **Step 8: Commit**

```bash
git add app/src
git commit -m "feat(transfers): ask three minutes later, if nobody answered first

Three minutes is measured: 137 of the 140 pairs in a real ledger formed inside
it, median 23 seconds. The pairing window stays at fifteen, so a partner
arriving after the question was asked still pairs and clears it.

The decision lives outside the worker so it can be tested without WorkManager,
and the pipeline reaches the scheduler through a function type for the same
reason TeachRequest is one -- it cannot depend on the thing that runs it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Settling it, and the arithmetic that keeps net worth right

The core of the feature. "Yes" must move money rather than delete it.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/repository/SettleTransferTest.kt`

**Interfaces:**
- Consumes: `TransactionDao.setReviewPending`, `markAsTransfer` (Task 1).
- Produces: `TransactionRepository.settleAsOwnTransfer(transactionId: Long, otherAccountId: Long): Result<Unit>` and `TransactionRepository.dismissTransferReview(transactionId: Long): Result<Unit>`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/wasif/khata/core/data/repository/SettleTransferTest.kt`. Follow
`OwedMoneyTest.kt`'s `setUp` for the fixture, including `searchIndex(db)`.

```kotlin
    @Test
    fun `saying yes moves the money instead of losing it`() = runTest {
        // A 5,000 ATM withdrawal: it left EBL and arrived as cash. Marking it a
        // transfer without writing the other side would take 5,000 off net worth,
        // which is a worse bug than the one being fixed.
        val ebl = account("EBL", opening = 20_000_00)
        val cash = account("Cash", opening = 0)
        val id = parsedDebit(ebl, amountMinor = 5_000_00, merchantRaw = "EBL Account Transfer")

        repository.settleAsOwnTransfer(id, otherAccountId = cash).getOrThrow()

        val original = db.transactionDao().findById(id)!!
        val mirror = db.transactionDao().allForAccount(cash).single()
        assertEquals(TransactionKind.TRANSFER, original.kind)
        assertEquals(TransactionKind.TRANSFER, mirror.kind)
        assertEquals(original.transferGroupId, mirror.transferGroupId)
        assertNotNull(original.transferGroupId)
        assertEquals(TransactionDirection.CREDIT, mirror.direction)
        assertEquals(5_000_00L, mirror.amountMinor)
        // The money is in the other account, not gone.
        assertEquals(5_000_00L, db.accountDao().findById(cash)!!.currentBalanceMinor)
        assertFalse(original.transferReviewPending)
    }

    @Test
    fun `a settled transfer stops counting as spending`() = runTest {
        val ebl = account("EBL", opening = 20_000_00)
        val cash = account("Cash", opening = 0)
        val id = parsedDebit(ebl, amountMinor = 5_000_00, merchantRaw = "EBL Account Transfer")

        repository.settleAsOwnTransfer(id, otherAccountId = cash).getOrThrow()

        assertEquals(emptyMap<LocalDate, Money>(), repository.observeDayTotals().first())
    }

    @Test
    fun `saying no leaves it spending and creates nothing`() = runTest {
        val ebl = account("EBL", opening = 20_000_00)
        val id = parsedDebit(ebl, amountMinor = 5_000_00, merchantRaw = "EBL Account Transfer")
        db.transactionDao().setReviewPending(id, pending = true, updatedAt = 1_000)

        repository.dismissTransferReview(id).getOrThrow()

        val row = db.transactionDao().findById(id)!!
        assertFalse(row.transferReviewPending)
        assertEquals(TransactionKind.NORMAL, row.kind)
        assertNull(row.transferGroupId)
        assertEquals(1, db.transactionDao().allIdsForIndex().size)
    }

    @Test
    fun `settling the same row twice does not write two mirrors`() = runTest {
        // The notification and the in-app list are two doors to the same question,
        // and both can be open at once.
        val ebl = account("EBL", opening = 20_000_00)
        val cash = account("Cash", opening = 0)
        val id = parsedDebit(ebl, amountMinor = 5_000_00, merchantRaw = "EBL Account Transfer")

        repository.settleAsOwnTransfer(id, otherAccountId = cash).getOrThrow()
        repository.settleAsOwnTransfer(id, otherAccountId = cash).getOrThrow()

        assertEquals(1, db.transactionDao().allForAccount(cash).size)
        assertEquals(5_000_00L, db.accountDao().findById(cash)!!.currentBalanceMinor)
    }
```

Add to `TransactionDao.kt` the read the test needs:

```kotlin
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND deletedAt IS NULL")
    suspend fun allForAccount(accountId: Long): List<TransactionEntity>
```

and to `AccountDao.kt` (it has only `getAll()` today):

```kotlin
    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?
```

- [ ] **Step 2: Run and watch them fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*SettleTransferTest*"
```

Expected: FAIL, unresolved reference `settleAsOwnTransfer`.

- [ ] **Step 3: Declare them on the repository**

In `domain/repository/TransactionRepository.kt`:

```kotlin
    /**
     * Records that a movement went to another of the owner's accounts: writes the
     * other side, pairs the two, and takes both out of spending.
     */
    suspend fun settleAsOwnTransfer(transactionId: Long, otherAccountId: Long): Result<Unit>

    /** Records that it did not: the row stays ordinary spending, and stops asking. */
    suspend fun dismissTransferReview(transactionId: Long): Result<Unit>
```

- [ ] **Step 4: Implement them**

In `core/data/repository/TransactionRepositoryImpl.kt`:

```kotlin
    override suspend fun settleAsOwnTransfer(
        transactionId: Long,
        otherAccountId: Long,
    ): Result<Unit> = runCatchingData {
        db.withTransaction {
            val original = transactionDao.findById(transactionId) ?: throw DataError.NotFound
            // Already settled -- by the other door, or by a partner arriving late.
            // Writing a second mirror would credit the account twice.
            if (original.transferGroupId != null) return@withTransaction

            val now = clock.now()
            // The mirror is the same movement seen from the other side: same amount,
            // same moment, opposite direction.
            val mirrorDirection = when (original.direction) {
                TransactionDirection.DEBIT -> TransactionDirection.CREDIT
                TransactionDirection.CREDIT -> TransactionDirection.DEBIT
            }
            val mirrorId = transactionDao.upsert(
                TransactionEntity(
                    uuid = UUID.randomUUID().toString(),
                    accountId = otherAccountId,
                    amountMinor = original.amountMinor,
                    direction = mirrorDirection,
                    occurredAt = original.occurredAt,
                    merchantRaw = original.merchantRaw,
                    merchantId = null,
                    categoryId = null,
                    note = null,
                    counterparty = null,
                    // MANUAL, because no message said this: the owner did.
                    source = TransactionSource.MANUAL,
                    confidence = Confidence.HIGH,
                    kind = TransactionKind.TRANSFER,
                    rawMessageId = null,
                    transferGroupId = null,
                    feeMinor = null,
                    referenceNumber = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            // The other account gains what this one lost. Without this the money
            // leaves net worth, which is worse than counting it as spending.
            accountDao.adjustBalance(
                otherAccountId,
                signedMinor(original.amountMinor, mirrorDirection),
                now,
            )

            // Marks both TRANSFER, joins them, and clears the review flag in one go.
            transactionDao.markAsTransfer(
                listOf(original.id, mirrorId),
                UUID.randomUUID().toString(),
                now,
            )

            searchIndex.reindex("transaction", original.id)
            searchIndex.reindex("transaction", mirrorId)
        }
    }

    override suspend fun dismissTransferReview(transactionId: Long): Result<Unit> =
        runCatchingData {
            transactionDao.setReviewPending(transactionId, pending = false, updatedAt = clock.now())
        }
```

- [ ] **Step 5: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests "*SettleTransferTest*"
```

Expected: PASS, 4 tests.

- [ ] **Step 6: Run everything**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Every fake `TransactionRepository` in the test sources needs the two new overrides:
`TransactionEditorViewModelTest`, `ModulesViewModelTest`, `LedgerViewModelTest`,
`WalletViewModelTest`, `QuickEntryViewModelTest`. Add
`override suspend fun settleAsOwnTransfer(transactionId: Long, otherAccountId: Long) = Result.success(Unit)`
and `override suspend fun dismissTransferReview(transactionId: Long) = Result.success(Unit)`
to each.

- [ ] **Step 7: Commit**

```bash
git add app/src
git commit -m "feat(transfers): saying yes moves the money rather than losing it

A 5,000 taka ATM withdrawal left EBL and arrived as cash. Marking it a transfer
without writing the other side would take 5,000 off net worth, which is a worse
bug than counting it as spending.

So settling writes the mirror row on the chosen account, credits it, joins both
under one transferGroupId and marks both TRANSFER -- in a single database
transaction, so the ledger can never hold a movement with one end. Settling
twice is a no-op, because the notification and the in-app list are two doors to
the same question and both can be open.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: The notification

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/notify/TransferNotifier.kt`
- Create: `app/src/main/java/com/wasif/khata/core/notify/TransferReviewReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/wasif/khata/core/sms/TransferReview.kt`
- Test: `app/src/test/java/com/wasif/khata/core/notify/TransferNotifierTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository.dismissTransferReview` (Task 4), `TransferReview` (Task 3).
- Produces: `TransferNotifier.ask(transaction: TransactionEntity)`, `TransferNotifier.retract(transactionId: Long)`.

- [ ] **Step 1: Declare the permission and the receiver**

In `app/src/main/AndroidManifest.xml`, beside the existing `uses-permission` lines:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

and inside `<application>`:

```xml
        <receiver
            android:name=".core.notify.TransferReviewReceiver"
            android:exported="false" />
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/wasif/khata/core/notify/TransferNotifierTest.kt`:

```kotlin
package com.wasif.khata.core.notify

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TransferNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notifier = TransferNotifier(context)

    @Test
    fun `asking posts one notification carrying the amount`() {
        notifier.ask(transactionId = 7, amountMinor = 500_000, merchantRaw = "EBL Account Transfer")

        val posted = shadowOf(manager).allNotifications.single()
        assertTrue(shadowOf(posted).contentText.toString().contains("5,000"))
    }

    @Test
    fun `retracting takes it back down`() {
        // A partner message arriving at minute five answers the question, and a stale
        // question the owner can no longer answer correctly is worse than no question.
        notifier.ask(transactionId = 7, amountMinor = 500_000, merchantRaw = "EBL Account Transfer")
        notifier.retract(transactionId = 7)

        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `two rows ask separately rather than replacing each other`() {
        notifier.ask(transactionId = 7, amountMinor = 500_000, merchantRaw = "EBL Account Transfer")
        notifier.ask(transactionId = 8, amountMinor = 100_000, merchantRaw = "NPSB FUND TRANSFER")

        assertEquals(2, shadowOf(manager).allNotifications.size)
    }
}
```

- [ ] **Step 3: Run and watch it fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*TransferNotifierTest*"
```

Expected: FAIL, unresolved reference `TransferNotifier`.

- [ ] **Step 4: Write the notifier**

`app/src/main/java/com/wasif/khata/core/notify/TransferNotifier.kt`:

```kotlin
package com.wasif.khata.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wasif.khata.MainActivity
import com.wasif.khata.R
import com.wasif.khata.core.model.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "transfer-review"

/** The transaction a notification is about, carried through both intents. */
const val EXTRA_TRANSACTION_ID = "transactionId"

@Singleton
class TransferNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * The transaction id is the notification id, so a second question never replaces
     * the first and retracting one leaves the others standing.
     */
    fun ask(transactionId: Long, amountMinor: Long, merchantRaw: String?) {
        ensureChannel()

        val yes = PendingIntent.getActivity(
            context,
            transactionId.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val no = PendingIntent.getBroadcast(
            context,
            transactionId.toInt(),
            Intent(context, TransferReviewReceiver::class.java)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        manager.notify(
            transactionId.toInt(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Was this a transfer?")
                .setContentText(
                    "${Money(amountMinor).format()} · ${merchantRaw ?: "Unknown"}",
                )
                .setAutoCancel(true)
                // "No" answers without opening anything, because it is the commoner
                // answer and an app launch to say "nothing to do here" is a tax.
                .addAction(0, "Not mine", no)
                .addAction(0, "Own transfer", yes)
                .build(),
        )
    }

    fun retract(transactionId: Long) = manager.cancel(transactionId.toInt())

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Transfers",
                // Low: this is a question that can wait, not an alarm. It must be
                // findable, never loud -- the same rule the ledger's marker follows.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
```

- [ ] **Step 5: Write the receiver**

`app/src/main/java/com/wasif/khata/core/notify/TransferReviewReceiver.kt`:

```kotlin
package com.wasif.khata.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** "Not mine", answered from the notification without opening the app. */
@AndroidEntryPoint
class TransferReviewReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TransactionRepository

    @Inject lateinit var notifier: TransferNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        if (id <= 0L) return

        // goAsync would be wrong here: the write is small, and a receiver held open
        // across a database transaction is a stall the system may kill mid-way.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.dismissTransferReview(id)
                notifier.retract(id)
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 6: Post from the review, and retract when pairing answers late**

In `core/sms/TransferReview.kt`, add `private val notifier: TransferNotifier,` to
`TransferReview`'s constructor and, after `setReviewPending`:

```kotlin
        notifier.ask(
            transactionId = transactionId,
            amountMinor = row.amountMinor,
            merchantRaw = row.merchantRaw,
        )
```

and at the top of `reviewIfUnpaired`, in the paired branch:

```kotlin
        if (row.transferGroupId != null) {
            // It answered itself while the clock ran.
            notifier.retract(transactionId)
            return
        }
```

`TransferReviewWorkerTest` now needs a `TransferNotifier` — pass the real one built on
`ApplicationProvider.getApplicationContext()`; Robolectric's notification manager is a
shadow and posts nothing anywhere.

- [ ] **Step 7: Run everything**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

- [ ] **Step 8: Commit**

```bash
git add app/src
git commit -m "feat(transfers): ask on the phone, answer without opening the app

The app had no notification code at all -- no channel, no permission -- so both
are new. The channel is IMPORTANCE_LOW: this is a question that can wait, not an
alarm, and it must be findable rather than loud.

'Not mine' resolves from the notification through a receiver, because it is the
commoner answer and launching the app to say 'nothing to do here' is a tax. The
transaction id is the notification id, so two questions coexist and retracting
one leaves the other standing.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: The in-app path

Notifications can be denied, muted, or missed. This is the path that always works.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/settle/SettleTransferViewModel.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/settle/SettleTransferSheet.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/hub/ModulesUiState.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/hub/ModulesViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/hub/ModulesScreen.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/wallet/WalletViewModel.kt`, `WalletScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/settle/SettleTransferViewModelTest.kt`

**Interfaces:**
- Consumes: `TransactionDao.observePendingReviews`, `observePendingReviewCount` (Task 1); `settleAsOwnTransfer`, `dismissTransferReview` (Task 4).
- Produces: `SettleTransferViewModel` with `state: StateFlow<SettleTransferUiState>`, `onAccountChosen(id: Long)`, `onNotMine()`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/wasif/khata/feature/settle/SettleTransferViewModelTest.kt`:

```kotlin
package com.wasif.khata.feature.settle

import com.wasif.khata.core.model.Money
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettleTransferViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private var settled: Pair<Long, Long>? = null
    private var dismissed: Long? = null

    @Test
    fun `choosing an account settles it as an own transfer`() = runTest(dispatcher) {
        val viewModel = viewModel(transactionId = 7)

        viewModel.onAccountChosen(3)
        advanceUntilIdle()

        assertEquals(7L to 3L, settled)
    }

    @Test
    fun `not mine leaves it spending`() = runTest(dispatcher) {
        val viewModel = viewModel(transactionId = 7)

        viewModel.onNotMine()
        advanceUntilIdle()

        assertEquals(7L, dismissed)
        assertEquals(null, settled)
    }

    @Test
    fun `the account it came from is not offered as the account it went to`() = runTest(dispatcher) {
        // Money cannot move from an account to itself, and offering it invites a
        // transfer that balances to nothing.
        val viewModel = viewModel(transactionId = 7)
        advanceUntilIdle()

        assertEquals(listOf(2L, 3L), viewModel.state.value.choices.map { it.id })
    }
}
```

Build the fake `TransactionRepository` the way `LedgerViewModelTest` does — every
member returning empty — with these three carrying the recording, the pending row on
account 1, and accounts 1, 2 and 3 in the fake `ReferenceDataRepository`:

```kotlin
        override fun observe(id: Long): Flow<Transaction?> = flowOf(pendingRow)

        override suspend fun settleAsOwnTransfer(
            transactionId: Long,
            otherAccountId: Long,
        ): Result<Unit> {
            settled = transactionId to otherAccountId
            return Result.success(Unit)
        }

        override suspend fun dismissTransferReview(transactionId: Long): Result<Unit> {
            dismissed = transactionId
            return Result.success(Unit)
        }
```

- [ ] **Step 2: Run and watch it fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*SettleTransferViewModelTest*"
```

Expected: FAIL, unresolved reference `SettleTransferViewModel`.

- [ ] **Step 3: Write the view model**

```kotlin
package com.wasif.khata.feature.settle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettleTransferUiState(
    val amount: Money = Money.ZERO,
    val merchantRaw: String? = null,
    val originalMessage: String? = null,
    val choices: List<Account> = emptyList(),
    val done: Boolean = false,
)

@HiltViewModel(assistedFactory = SettleTransferViewModel.Factory::class)
class SettleTransferViewModel @AssistedInject constructor(
    private val repository: TransactionRepository,
    private val referenceData: ReferenceDataRepository,
    @Assisted private val transactionId: Long,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(transactionId: Long): SettleTransferViewModel
    }

    private val _state = MutableStateFlow(SettleTransferUiState())
    val state: StateFlow<SettleTransferUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val row: Transaction = repository.observe(transactionId).first() ?: return@launch
            val accounts = referenceData.observeAccounts().first()
            _state.update {
                it.copy(
                    amount = row.amount,
                    merchantRaw = row.merchantRaw,
                    // Never the account it left: money cannot move to itself, and
                    // offering it invites a transfer that balances to nothing.
                    choices = accounts.filterNot { account -> account.id == row.accountId },
                )
            }
        }
    }

    fun onAccountChosen(accountId: Long) {
        viewModelScope.launch {
            repository.settleAsOwnTransfer(transactionId, accountId)
            _state.update { it.copy(done = true) }
        }
    }

    fun onNotMine() {
        viewModelScope.launch {
            repository.dismissTransferReview(transactionId)
            _state.update { it.copy(done = true) }
        }
    }
}
```

Add `import kotlinx.coroutines.flow.first`.

- [ ] **Step 4: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests "*SettleTransferViewModelTest*"
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Put the dot on the Wallet card**

In `feature/hub/ModulesUiState.kt`:

```kotlin
    /** Something is waiting to be settled as a transfer or not. */
    val hasPendingReview: Boolean = false,
```

In `ModulesViewModel`, combine `transactionDao.observePendingReviewCount()` into the
existing state flow and map it to `hasPendingReview = count > 0`.

In `ModulesScreen.kt`'s `WalletCard`, inside the outer `Box`, after the `Row`:

```kotlin
        if (state.hasPendingReview) {
            // A dot, not a number: the count is not the point, and a badge reading "1"
            // on a card whose other number is money invites reading it as money.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(spacing.sm)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
```

- [ ] **Step 6: List them inside Wallet**

In `WalletViewModel`, expose `pendingReviews: StateFlow<List<Transaction>>` from
`transactionDao.observePendingReviews()` mapped with `toDomain()`. In `WalletScreen`,
above `ACCOUNTS`, render the section when the list is non-empty:

```kotlin
            if (pendingReviews.isNotEmpty()) {
                SectionLabel(top = spacing.md, text = "TO SETTLE")
                pendingReviews.forEach { transaction ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSettle(transaction.id) }
                            .padding(vertical = spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = transaction.merchantRaw ?: "Transfer",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        MoneyText(money = transaction.amount, direction = transaction.direction)
                    }
                }
            }
```

- [ ] **Step 7: Write the sheet**

`app/src/main/java/com/wasif/khata/feature/settle/SettleTransferSheet.kt`:

```kotlin
package com.wasif.khata.feature.settle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleTransferSheet(
    transactionId: Long,
    onDismiss: () -> Unit,
    viewModel: SettleTransferViewModel = hiltViewModel(
        creationCallback = { factory: SettleTransferViewModel.Factory ->
            factory.create(transactionId)
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState()

    // The answer closes the sheet; nothing else does, so an accidental swipe cannot
    // look like an answer.
    LaunchedEffect(state.done) { if (state.done) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal)) {
            Text(
                text = "Where did this go?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${state.amount.format()} · ${state.merchantRaw ?: "Transfer"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs),
            )

            // The message is here because it is often the only thing that will remind
            // anybody what a three-day-old "EBL Account Transfer" actually was.
            state.originalMessage?.let { message ->
                SectionLabel(top = spacing.md, text = "Original message")
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel(top = spacing.md, text = "It moved to one of my accounts")
            state.choices.forEach { account ->
                OutlinedButton(
                    onClick = { viewModel.onAccountChosen(account.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
                ) {
                    Text(account.name)
                }
            }

            TextButton(
                onClick = viewModel::onNotMine,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm, bottom = spacing.xl),
            ) {
                Text("It left my accounts")
            }
        }
    }
}
```

Route it from wherever the Wallet's TO SETTLE row and the notification's activity extra
(`EXTRA_TRANSACTION_ID`, read in `MainActivity`) land, both opening it on the same id.

- [ ] **Step 8: Ask for the notification permission, in context**

Declaring `POST_NOTIFICATIONS` in Task 5 is not enough: on Android 13+ it is granted at
runtime, and until it is, every `notify` call is silently dropped. The spec (§9) asks for
it the first time a review is actually pending — never on first launch, where it would be
a prompt for a feature nobody has met yet.

In `ModulesScreen.kt`, alongside the dot:

```kotlin
    // Asked here rather than at launch: this composes the first time something is
    // actually waiting, which is the first moment the permission buys anything. Denied,
    // the dot and the TO SETTLE list carry the whole feature -- see the spec's §8.
    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(state.hasPendingReview) {
        if (state.hasPendingReview &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
```

Follow `core/permission/SmsPermission.kt` for the house idiom, and reuse its
"asked once already" preference shape rather than re-prompting on every launch.

- [ ] **Step 9: Run everything**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

- [ ] **Step 10: Commit**

```bash
git add app/src
git commit -m "feat(transfers): settle from the app, with or without a notification

Notifications are permission-gated, mutable and missable, so the in-app path is
the foundation and the notification only ever saves a trip. A dot on the Wallet
card while anything is pending, a list inside it, and the same sheet the
notification's 'Own transfer' opens.

A dot rather than a count: the number is not the point, and a badge reading '1'
on a card whose other number is money invites reading it as money. The account
the money left is never offered as the account it arrived in.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Hardware walkthrough

Unit tests cannot reach WorkManager's real delay, the notification shade, or the
permission dialog.

1. **A transfer that pairs asks nothing.** Move money between the two EBL accounts.
   Both messages arrive seconds apart, the row pairs, and no notification appears.
2. **A transfer that does not pair asks.** Withdraw cash at an ATM, or send money to
   somebody else's bKash. Three minutes later a low-priority notification appears
   reading "Was this a transfer?" with the amount.
3. **"Not mine" settles from the shade.** Tap it. The notification goes; the row stays
   in the ledger as spending; the Wallet dot does not appear.
4. **"Own transfer" settles in the app.** Tap it on a fresh one. The sheet opens on
   that transaction. Choose Cash. The row leaves the month's spending, a matching
   credit appears in Cash, and net worth is unchanged from before the withdrawal.
5. **The dot works without notifications.** Revoke the notification permission in
   Android settings, make another unpaired transfer, wait three minutes, open the app.
   The Wallet card carries a dot and the row is listed under TO SETTLE.
6. **Ordinary spending never asks.** Buy something with the card. No notification, no
   dot.

## Done when

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` passes.
- Walkthrough steps 1-6 pass on a real phone.
- A backup taken before this plan still restores after it.
- The ledger's historic totals are **unchanged** — if any past month moved, something
  backfilled, and that is a bug in this plan rather than a bonus.

## Deferred

Backfill of the 101 existing transfer-shaped rows. Fee handling where the two sides of
a transfer differ. Per-merchant memory of "Not mine". All three are recorded in the
spec's §11 with reasons.
