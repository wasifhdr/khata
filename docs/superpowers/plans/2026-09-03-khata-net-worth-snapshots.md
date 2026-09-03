# Khata Plan — Net Worth Snapshots

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Wallet chart reads a stored net-worth series instead of walking backwards from today's balance, so historic values stop drifting by whatever the ledger cannot explain.

**Architecture:** A `balance_snapshots` table at schema v5 holds one closing balance per account per Dhaka day. A single idempotent routine fills every day that has no row yet, which serves the one-time backfill, the nightly job, and a phone that was off for a week identically — so a missed night costs nothing and the backfill needs no flag. The derived walk is deleted rather than kept as a fallback. Four tasks: schema, the fill routine, the worker and its scheduling, then the chart.

**Tech Stack:** Kotlin · Room + KSP · WorkManager 2.11.2 · Hilt · Coroutines/Flow · Jetpack Compose · JUnit4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-03-khata-net-worth-snapshots-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`, `compileSdk` / `targetSdk 37`, package `com.wasif.khata`.
- Money is always `Long` **paisa**. Never `Double`, never `Float`.
- Every table carries `uuid TEXT NOT NULL`, `createdAt`, `updatedAt`, `deletedAt` (nullable). Deletes are soft.
- Instants are UTC epoch millis. Every day boundary is Dhaka, via the existing `Long.toDhakaDayIndex()` — never a new day-arithmetic expression.
- **A written snapshot is never rewritten.** The routine writes only days that have no row.
- No aggregate-on-scroll queries; the chart reads rows, it does not compute them.
- **No constraints on any work request** — charging or network would only delay a local write.
- Derived UI values are getters on `UiState`, never constructor parameters.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- Tests never hardcode a magic epoch-millis literal — build them with `Instant.parse("…Z").toEpochMilli()`.
- **Ponytail is in force.** Reuse before writing: `toDhakaDayIndex()` exists, `signedMinor()` exists, `IngestionScheduler` is the shape a scheduler takes here.
- Run unit tests with: `./gradlew :app:testDebugUnitTest`
- Build with: `./gradlew :app:assembleDebug`
- `export JAVA_HOME="/e/Android/Android Studio/jbr"` and `export PATH="$JAVA_HOME/bin:$PATH"` per shell.

## File Structure

**Create:**
- `app/src/main/java/com/wasif/khata/core/data/entity/BalanceSnapshotEntity.kt`
- `app/src/main/java/com/wasif/khata/core/data/dao/BalanceSnapshotDao.kt`
- `app/src/main/java/com/wasif/khata/core/data/repository/SnapshotWriter.kt` — the fill routine, the only place that decides what a day's balance was.
- `app/src/main/java/com/wasif/khata/core/data/SnapshotWorker.kt` — the worker and its scheduling.
- Tests: `app/src/test/java/com/wasif/khata/core/data/migration/Migration4To5Test.kt`, `app/src/test/java/com/wasif/khata/core/data/repository/SnapshotWriterTest.kt`

**Modify:**
- `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt` — v5, the entity, the DAO.
- `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt` — `MIGRATION_4_5`.
- `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt` — provide the new DAO.
- `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt` — daily movement per account; delete `observeDailyNet`.
- `app/src/main/java/com/wasif/khata/KhataApplication.kt` — schedule the nightly job.
- `app/src/main/java/com/wasif/khata/feature/wallet/WalletViewModel.kt` — read snapshots; delete `netWorthTrend`.
- `app/src/test/java/com/wasif/khata/feature/wallet/NetWorthTrendTest.kt` — replaced by the snapshot mapping test.

---

### Task 1: The table, at schema v5

Spec §1. The table lands empty; Task 2 fills it.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/BalanceSnapshotEntity.kt`, `app/src/main/java/com/wasif/khata/core/data/dao/BalanceSnapshotDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt`, `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt`, `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/migration/Migration4To5Test.kt`

**Interfaces:**
- Consumes: `Long.toDhakaDayIndex()` from `core/time/KhataClock.kt`.
- Produces: `BalanceSnapshotEntity(id, uuid, accountId, dayIndex, balanceMinor, createdAt, updatedAt, deletedAt)`; `BalanceSnapshotDao` with `suspend fun insertAll(rows: List<BalanceSnapshotEntity>)`, `suspend fun existingDayIndices(accountId: Long): List<Long>`, `fun observeTotalsFrom(fromDayIndex: Long): Flow<List<DayTotal>>`, and `data class DayTotal(val dayIndex: Long, val totalMinor: Long)`. Tasks 2 and 4 consume these.

- [ ] **Step 1: Write the entity**

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One account's closing balance for one Dhaka day.
 *
 * A row records what the balance was understood to be that night and is never
 * rewritten -- editing a transaction from last March does not silently redraw last
 * March. The ledger says what happened; this says what was believed.
 */
@Entity(
    tableName = "balance_snapshots",
    indices = [
        Index(value = ["uuid"], unique = true),
        // A day has one closing balance per account. The uniqueness is what lets the
        // fill routine be idempotent rather than careful.
        Index(value = ["accountId", "dayIndex"], unique = true),
        Index(value = ["dayIndex"]),
    ],
)
data class BalanceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val accountId: Long,
    /** Dhaka day, as `Long.toDhakaDayIndex()` computes it. */
    val dayIndex: Long,
    val balanceMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

- [ ] **Step 2: Write the DAO**

```kotlin
package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasif.khata.core.data.entity.BalanceSnapshotEntity
import kotlinx.coroutines.flow.Flow

/** A day's net worth: every included account's closing balance, summed. */
data class DayTotal(val dayIndex: Long, val totalMinor: Long)

@Dao
interface BalanceSnapshotDao {

    /**
     * IGNORE, not REPLACE: a row that already exists is the balance as it was
     * believed that night, and the fill routine must not overwrite it.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<BalanceSnapshotEntity>)

    @Query("SELECT dayIndex FROM balance_snapshots WHERE accountId = :accountId AND deletedAt IS NULL")
    suspend fun existingDayIndices(accountId: Long): List<Long>

    // Only accounts that count toward net worth, which is why this joins rather than
    // summing the table alone.
    @Query(
        """
        SELECT s.dayIndex AS dayIndex, SUM(s.balanceMinor) AS totalMinor
        FROM balance_snapshots s
        JOIN accounts a ON a.id = s.accountId
        WHERE s.deletedAt IS NULL AND a.deletedAt IS NULL AND a.includeInNetWorth = 1
          AND s.dayIndex >= :fromDayIndex
        GROUP BY s.dayIndex
        ORDER BY s.dayIndex
        """,
    )
    fun observeTotalsFrom(fromDayIndex: Long): Flow<List<DayTotal>>
}
```

- [ ] **Step 3: Register it and bump the version**

In `KhataDatabase.kt`, add `BalanceSnapshotEntity::class` to `entities`, change `version = 4` to `version = 5`, and add `abstract fun balanceSnapshotDao(): BalanceSnapshotDao`, with the two imports.

In `Migrations.kt`, above `MIGRATION_3_4`:

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `balance_snapshots` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `accountId` INTEGER NOT NULL, " +
                "`dayIndex` INTEGER NOT NULL, `balanceMinor` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_balance_snapshots_uuid` ON `balance_snapshots` (`uuid`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_balance_snapshots_accountId_dayIndex` " +
                "ON `balance_snapshots` (`accountId`, `dayIndex`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_balance_snapshots_dayIndex` ON `balance_snapshots` (`dayIndex`)")
    }
}
```

In `DatabaseModule.kt`, add the import `com.wasif.khata.core.data.migration.MIGRATION_4_5` and `com.wasif.khata.core.data.dao.BalanceSnapshotDao`, extend the migration list, and add the DAO provider beside the others:

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

```kotlin
    @Provides fun provideBalanceSnapshotDao(db: KhataDatabase): BalanceSnapshotDao = db.balanceSnapshotDao()
```

- [ ] **Step 4: Write the migration test**

`Migration1To2Test` builds a real database from the exported schema rather than using `MigrationTestHelper`, which cannot agree with Robolectric about database paths. This mirrors it for v4 → v5.

```kotlin
package com.wasif.khata.core.data.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.BalanceSnapshotEntity
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TEST_DB = "migration-4-5-test.db"
private const val SCHEMA_DIR = "schemas/com.wasif.khata.core.data.KhataDatabase"

@RunWith(RobolectricTestRunner::class)
class Migration4To5Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = deleteTestDb()

    @After fun tearDown() = deleteTestDb()

    private fun deleteTestDb() {
        context.getDatabasePath(TEST_DB).let { f ->
            f.delete()
            File(f.path + "-shm").delete()
            File(f.path + "-wal").delete()
        }
    }

    private fun schemaJson(version: Int): JSONObject {
        val candidates = listOf(File("$SCHEMA_DIR/$version.json"), File("app/$SCHEMA_DIR/$version.json"))
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("cannot locate $version.json; looked in ${candidates.map { it.absolutePath }}", file)
        return JSONObject(file!!.readText()).getJSONObject("database")
    }

    /** Recreates schema v4 exactly as Room would have, identity hash included. */
    private fun createV4Database(): SupportSQLiteDatabase {
        val database = schemaJson(4)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase

        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            entity.optJSONArray("indices")?.let { indices ->
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
        }

        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        db.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
            arrayOf(database.getString("identityHash")),
        )
        db.version = 4
        return db
    }

    private fun migrate(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {
                        MIGRATION_4_5.migrate(db)
                    }
                })
                .build(),
        )
        return helper.writableDatabase
    }

    @Test
    fun `the snapshots table arrives and Room accepts the result`() = runTest {
        createV4Database().use { it.execSQL("SELECT 1") }
        migrate().use { it.close() }

        // Reopening through Room runs its own identity-hash and column checks, which
        // is what catches a migration whose SQL disagrees with the entity.
        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        val dao = db.balanceSnapshotDao()

        val row = BalanceSnapshotEntity(
            uuid = "snap-1", accountId = 1, dayIndex = 20_000, balanceMinor = 5_000,
            createdAt = 1, updatedAt = 1,
        )
        dao.insertAll(listOf(row))
        // The same account and day twice must not produce two rows -- the fill
        // routine leans on this uniqueness rather than checking first.
        dao.insertAll(listOf(row.copy(uuid = "snap-2")))

        assertEquals(1, dao.existingDayIndices(accountId = 1).size)
        db.close()
    }
}
```

- [ ] **Step 5: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests "*Migration4To5Test*"`
Expected: PASS. If Room reports a schema mismatch, the migration's SQL disagrees with the entity — compare column by column against `app/schemas/com.wasif.khata.core.data.KhataDatabase/5.json`, which the build regenerates.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/ app/schemas \
        app/src/test/java/com/wasif/khata/core/data/migration/Migration4To5Test.kt
git commit -m "feat(data): a table for what the balance was each night"
```

---

### Task 2: The fill routine

Spec §2. The only piece with real arithmetic, and the one that gets tested hardest.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/repository/SnapshotWriter.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/repository/SnapshotWriterTest.kt`

**Interfaces:**
- Consumes: `BalanceSnapshotDao` (Task 1), `AccountDao.getAll()`, `Long.toDhakaDayIndex()`, `KhataClock.now()`.
- Produces: `SnapshotWriter` with `suspend fun fillThrough(today: Long)`. Task 3 calls it.

- [ ] **Step 1: Add the movement query**

In `TransactionDao.kt`, beside `observeSpendByCategory`:

```kotlin
    // Signed daily movement per account, which is all the walk in SnapshotWriter
    // needs. One query rather than one per day per account, which would be hundreds
    // of round trips on a multi-year ledger.
    @Query(
        """
        SELECT accountId, ((occurredAt + 21600000) / 86400000) AS dayIndex,
               SUM(CASE WHEN direction = 'CREDIT' THEN amountMinor ELSE -amountMinor END) AS netMinor
        FROM transactions
        WHERE deletedAt IS NULL
        GROUP BY accountId, dayIndex
        ORDER BY dayIndex
        """,
    )
    suspend fun dailyMovementByAccount(): List<AccountDayNet>
```

and beside the other row classes in that file:

```kotlin
data class AccountDayNet(val accountId: Long, val dayIndex: Long, val netMinor: Long)
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.toDhakaDayIndex
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnapshotWriterTest {

    private lateinit var db: KhataDatabase
    private lateinit var writer: SnapshotWriter

    private val day1 = Instant.parse("2026-09-01T06:00:00Z").toEpochMilli()
    private val day2 = Instant.parse("2026-09-02T06:00:00Z").toEpochMilli()
    private val day3 = Instant.parse("2026-09-03T06:00:00Z").toEpochMilli()

    private val clock = object : KhataClock { override fun now(): Long = day3 }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        writer = SnapshotWriter(db.balanceSnapshotDao(), db.transactionDao(), db.accountDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(opening: Long, current: Long, uuid: String = "acc-1"): Long =
        db.accountDao().upsert(
            AccountEntity(
                uuid = uuid, name = uuid, type = AccountType.CASH,
                openingBalanceMinor = opening, currentBalanceMinor = current,
                reportedBalanceMinor = null, reportedBalanceAt = null,
                includeInNetWorth = true, smsIdentifiers = "",
                createdAt = 1000, updatedAt = 1000,
            ),
        )

    private suspend fun txn(accountId: Long, at: Long, minor: Long, direction: TransactionDirection) {
        db.transactionDao().upsert(
            TransactionEntity(
                uuid = "t-$at-$minor", accountId = accountId, amountMinor = minor,
                direction = direction, occurredAt = at, merchantRaw = null, merchantId = null,
                categoryId = null, note = null, source = TransactionSource.MANUAL,
                confidence = Confidence.HIGH, rawMessageId = null, transferGroupId = null,
                feeMinor = null, referenceNumber = null, createdAt = at, updatedAt = at,
            ),
        )
    }

    @Test
    fun `each day gets the balance as it stood that night`() = runTest {
        // Opening 1000, spend 200 on day 1, receive 500 on day 2 -> current 1300.
        val id = account(opening = 1_000, current = 1_300)
        txn(id, day1, 200, TransactionDirection.DEBIT)
        txn(id, day2, 500, TransactionDirection.CREDIT)

        writer.fillThrough(day3)

        val rows = db.balanceSnapshotDao().rowsFor(id).associate { it.dayIndex to it.balanceMinor }
        assertEquals(800L, rows[day1.toDhakaDayIndex()])
        assertEquals(1_300L, rows[day2.toDhakaDayIndex()])
        assertEquals(1_300L, rows[day3.toDhakaDayIndex()])
    }

    @Test
    fun `a second run writes nothing, because nothing is missing`() = runTest {
        val id = account(opening = 0, current = 0)
        txn(id, day1, 100, TransactionDirection.DEBIT)
        writer.fillThrough(day3)
        val first = db.balanceSnapshotDao().rowsFor(id).map { it.uuid }

        writer.fillThrough(day3)

        // Same rows, same identities -- not merely the same count.
        assertEquals(first, db.balanceSnapshotDao().rowsFor(id).map { it.uuid })
    }

    @Test
    fun `a gap of several days is filled in one pass`() = runTest {
        val id = account(opening = 0, current = 0)
        txn(id, day1, 0, TransactionDirection.DEBIT)

        writer.fillThrough(day3)

        // Days 1, 2 and 3 -- a phone that was off for two nights leaves no hole.
        assertEquals(3, db.balanceSnapshotDao().rowsFor(id).size)
    }

    @Test
    fun `an account with no transactions still gets its opening balance`() = runTest {
        val id = account(opening = 7_500, current = 7_500, uuid = "acc-quiet")

        writer.fillThrough(day3)

        val rows = db.balanceSnapshotDao().rowsFor(id)
        // Skipping it would drop it out of net worth entirely on every day of the
        // chart, which is a different claim from "it did not move".
        assertEquals(7_500L, rows.single().balanceMinor)
        assertEquals(day3.toDhakaDayIndex(), rows.single().dayIndex)
    }
}
```

Add to `BalanceSnapshotDao` the query these tests read through:

```kotlin
    @Query("SELECT * FROM balance_snapshots WHERE accountId = :accountId AND deletedAt IS NULL ORDER BY dayIndex")
    suspend fun rowsFor(accountId: Long): List<BalanceSnapshotEntity>
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*SnapshotWriterTest*"`
Expected: FAIL — "Unresolved reference 'SnapshotWriter'".

- [ ] **Step 4: Write the routine**

```kotlin
package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.BalanceSnapshotDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.entity.BalanceSnapshotEntity
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.toDhakaDayIndex
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the closing balance of every account for every Dhaka day that has no row yet,
 * up to and including [today]'s.
 *
 * One routine for three callers -- the one-time backfill over existing history, the
 * nightly job, and a phone that was off for a week -- because to this code they differ
 * only in how many days are missing. That is what makes a missed night cost nothing.
 */
@Singleton
class SnapshotWriter @Inject constructor(
    private val snapshots: BalanceSnapshotDao,
    private val transactions: TransactionDao,
    private val accounts: AccountDao,
    private val clock: KhataClock,
) {
    suspend fun fillThrough(today: Long) {
        val todayIndex = today.toDhakaDayIndex()
        val movements = transactions.dailyMovementByAccount().groupBy { it.accountId }
        val now = clock.now()

        for (account in accounts.getAll()) {
            val byDay = movements[account.id].orEmpty().associate { it.dayIndex to it.netMinor }
            // An account with no history still exists, and still holds its opening
            // balance. Starting from its first movement would drop it out of net
            // worth on every earlier day, which is a different claim.
            val firstDay = byDay.keys.minOrNull() ?: todayIndex
            if (firstDay > todayIndex) continue

            // Walk backwards from the balance we know: today's. Each step removes the
            // movement of the day after it, which is the balance at that day's close.
            val balances = HashMap<Long, Long>()
            var running = account.currentBalanceMinor
            for (day in todayIndex downTo firstDay) {
                balances[day] = running
                running -= byDay[day] ?: 0L
            }

            val existing = snapshots.existingDayIndices(account.id).toSet()
            snapshots.insertAll(
                balances.filterKeys { it !in existing }.map { (day, balance) ->
                    BalanceSnapshotEntity(
                        uuid = UUID.randomUUID().toString(),
                        accountId = account.id,
                        dayIndex = day,
                        balanceMinor = balance,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*SnapshotWriterTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/repository/SnapshotWriter.kt \
        app/src/main/java/com/wasif/khata/core/data/dao/ \
        app/src/test/java/com/wasif/khata/core/data/repository/SnapshotWriterTest.kt
git commit -m "feat(data): fill in the balance for every night that has none"
```

---

### Task 3: The nightly job

Spec §3, §4. The worker is thin because Task 2 holds the thinking.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/SnapshotWorker.kt`
- Modify: `app/src/main/java/com/wasif/khata/KhataApplication.kt`

**Interfaces:**
- Consumes: `SnapshotWriter.fillThrough(today: Long)` (Task 2), `KhataClock.now()`.
- Produces: `SnapshotWorker`, and `SnapshotScheduler.scheduleNightly()`.

- [ ] **Step 1: Write the worker and its scheduling**

No unit test here, for the reason the previous plan gave: every line is a call into `WorkManager`, so a test would fake `WorkManager` and assert the fake was called. The arithmetic it triggers is Task 2's and is already covered.

```kotlin
package com.wasif.khata.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wasif.khata.core.data.repository.SnapshotWriter
import com.wasif.khata.core.time.KhataClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SnapshotWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val writer: SnapshotWriter,
    private val clock: KhataClock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching { writer.fillThrough(clock.now()) }
        .fold(
            onSuccess = { Result.success() },
            // Retry, not failure: writing only missing days means a repeat run is
            // free, and a failure would leave a hole nothing else fills.
            onFailure = { Result.retry() },
        )
}

@Singleton
class SnapshotScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * KEEP, so relaunching does not reschedule and shift the run time. The first run
     * is also the backfill: on a fresh upgrade it fills years, and after that it finds
     * nothing missing (spec §3).
     */
    fun scheduleNightly() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NIGHTLY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SnapshotWorker>(Duration.ofDays(1))
                .setInitialDelay(untilNextRun())
                .build(),
        )
    }

    /**
     * 00:05 Dhaka. WorkManager cannot promise a wall-clock minute, and does not need
     * to: the writer fills whatever days are missing, so a run that lands late or not
     * at all costs nothing but freshness.
     */
    private fun untilNextRun(): Duration {
        val now = ZonedDateTime.now(DHAKA)
        var next = now.with(LocalTime.of(0, 5))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    private companion object {
        const val NIGHTLY = "net-worth-snapshot"
        val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")
    }
}
```

- [ ] **Step 2: Schedule it at startup**

In `KhataApplication.kt`, add `@Inject lateinit var snapshots: SnapshotScheduler` and, in `onCreate` after the widget collector:

```kotlin
        // Enqueueing touches no permission check and no platform state, so unlike the
        // first-launch backfill this is safe to do from the Application.
        snapshots.scheduleNightly()
```

with the import `com.wasif.khata.core.data.SnapshotScheduler`.

- [ ] **Step 3: Build and run the whole suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/SnapshotWorker.kt \
        app/src/main/java/com/wasif/khata/KhataApplication.kt
git commit -m "feat: a nightly job that writes down what the balance was"
```

---

### Task 4: The chart reads snapshots

Spec §5. The derived walk is deleted, not demoted.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/wallet/WalletViewModel.kt`, `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/wallet/NetWorthTrendTest.kt`

**Interfaces:**
- Consumes: `BalanceSnapshotDao.observeTotalsFrom(fromDayIndex: Long): Flow<List<DayTotal>>` (Task 1).
- Produces: `WalletUiState.netWorthTrend` unchanged in shape — still `List<Long>`, oldest first — so `WalletScreen` needs no change.

- [ ] **Step 1: Replace the test**

`NetWorthTrendTest` exists only to test `netWorthTrend`, which this task deletes. Delete that file and put the replacement in `WalletViewModelTest`, which already builds a `WalletViewModel` against a real in-memory database — follow its existing `setUp` and add `db.balanceSnapshotDao()` to the constructor call.

```kotlin
    @Test
    fun `the chart is the stored totals, oldest first, and only what counts as net worth`() = runTest {
        val counted = insertAccount(uuid = "acc-counted", includeInNetWorth = true)
        val excluded = insertAccount(uuid = "acc-excluded", includeInNetWorth = false)
        val day = Instant.parse("2026-09-01T06:00:00Z").toEpochMilli().toDhakaDayIndex()

        db.balanceSnapshotDao().insertAll(
            listOf(
                snapshot(counted, day, 1_000),
                snapshot(counted, day + 1, 1_500),
                // Excluded from net worth, so it must not move the line.
                snapshot(excluded, day, 9_999_999),
            ),
        )

        val vm = viewModel()
        vm.state.test {
            val state = awaitItem().takeIf { it.netWorthTrend.isNotEmpty() } ?: awaitItem()
            assertEquals(listOf(1_000L, 1_500L), state.netWorthTrend)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun snapshot(accountId: Long, dayIndex: Long, minor: Long) = BalanceSnapshotEntity(
        uuid = "snap-$accountId-$dayIndex",
        accountId = accountId,
        dayIndex = dayIndex,
        balanceMinor = minor,
        createdAt = 1,
        updatedAt = 1,
    )
```

If `WalletViewModelTest` has no `insertAccount` helper taking `uuid` and `includeInNetWorth`, add one modelled on the `AccountEntity` construction that file already does — the two parameters are what this test varies.

- [ ] **Step 2: Rewire the ViewModel**

In `WalletViewModel.kt`, inject `snapshotDao: BalanceSnapshotDao`, replace `transactionDao.observeDailyNet(trendFrom)` in the `combine` with `snapshotDao.observeTotalsFrom(trendFrom.toDhakaDayIndex())`, and map it to the series:

```kotlin
            netWorthTrend = daily.map { it.totalMinor },
```

Delete the `netWorthTrend` function at the bottom of the file entirely, along with its KDoc — the whole point is that the derivation is gone.

- [ ] **Step 3: Delete the query it used**

In `TransactionDao.kt`, delete `observeDailyNet` and its `DailyNetRow` row class. If the compiler reports either still in use, that use is the one thing this task exists to remove — find it and remove it too.

- [ ] **Step 4: Build and run the whole suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Verify on hardware**

1. Install over an existing build so the v4 → v5 migration actually runs. The app opens without a schema error.
2. Open Wallet. The chart draws, with the same shape it had before the upgrade — the backfill has filled history from the ledger.
3. Check the table has rows: `adb shell "run-as com.wasif.khata sqlite3 databases/khata.db 'SELECT COUNT(*) FROM balance_snapshots;'"`
4. Add a cash spend through the widget, then relaunch. The chart's last point moves; earlier points do not.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/wallet/WalletViewModel.kt \
        app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt \
        app/src/test/java/com/wasif/khata/feature/wallet/
git commit -m "feat(wallet): the chart reads what was written down, not what it can infer"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` passes, including `Migration4To5Test` and `SnapshotWriterTest`.
- The hardware walkthrough in Task 4 passes, including that the chart survives the upgrade with its history.
- `netWorthTrend` and `observeDailyNet` no longer exist.
- A second run of the writer inserts nothing.

## Deferred

Manual assets, budgets, month-over-month and the Insights screen. All recorded in spec §7.
