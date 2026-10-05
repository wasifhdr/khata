# Owed & Loans Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken 6-kind Owed dropdown with a single-table `owedMinor` + `IOU` model that supports bill splitting, pure IOUs where someone else paid, an interactive person-by-person `OwedScreen` with one-tap settlement, and settling unpaired SMS transfers directly as Owed.

**Architecture:** `transactions` remains the sole table. Adding `owedMinor: Long = 0` alongside `counterparty: String?` lets any `NORMAL` transaction carry an owed share (`0 <= owedMinor <= amountMinor`) while spending/income queries count only `MAX(amountMinor - owedMinor, 0)`. Pure IOUs (where someone else paid a bill and no account of yours moved) use `TransactionKind.IOU` (`accountId = 0L`), bypassing account-balance adjustments while counting `amountMinor` as spending when incurred and `-owedMinor` on the person's Owed tab.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Haze (`KhataGlass`), Room 2.8.4, Hilt, Coroutines/Flow, Robolectric + JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-30-khata-owed-design.md`

## Global Constraints

- **`SCHEMA_VERSION` in `core/backup/BackupRepository.kt` goes 14 → 15** alongside `KhataDatabase` (`version = 15`).
- **Single `transactions` table.** Do not introduce a second table for debts or splits.
- **Account balance isolation for `TransactionKind.IOU`.** Saving or deleting an `IOU` row must never call `accountDao.adjustBalance()`, and `dailyMovementByAccount()` / `allForAccount()` must exclude `kind = 'IOU'`.
- **Reclassifying an existing row must persist `draft.kind` and `draft.owedMinor`.** Only system-paired/reconciled kinds (`TRANSFER`, `ADJUSTMENT`, `LOAN_DISBURSEMENT`, `LOAN_REPAYMENT`, `FEE`) preserve their existing `kind` when edited in the generic editor.
- **Petrol + Glass UI discipline (`2026-08-28-khata-petrol-design.md`).** Use `FieldScaffold`, `CollapsingTopBar`, `KhataGlass`, `GlassChoice`, `AmountKeypadDialog` for money entry, tabular Fraunces numerals (`AmountTextStyle`), and words—never colour alone—to state direction (`"owes you"`, `"you owe"`).
- **Ponytail:** Shortest working diff, no single-implementation interfaces, stdlib before dependencies.

## Review Focus

1. **Editing an existing `NORMAL` SMS row to add a counterparty and owed share:** Must save `owedMinor` and `counterparty`, reduce monthly spending by `owedMinor`, and surface the person in `observeOwedByPerson()`.
2. **Changing a split bill's total amount after setting a custom split share:** If `owedMinor` exceeds the new `amountMinor`, clamp `owedMinor` to `amountMinor` so `amountMinor - owedMinor` never goes negative.
3. **Editing or deleting an `IOU` row, or switching an existing row between `NORMAL` and `IOU`:** Must reverse/apply `accountDao.adjustBalance()` only for the side whose `kind != TransactionKind.IOU`.
4. **Settling a person with mixed-case / whitespace name variants (`"Rafi"` vs `" rafi "`):** `observeOwedEntriesForPerson` and `settleUp` must match case-insensitively with `TRIM`, so settling zeroes out the entire group.
5. **Settling an unpaired transfer as Owed with blank or whitespace-only counterparty name:** Must reject blank names and keep `transferReviewPending` untouched until a non-blank name is provided.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `app/src/test/java/com/wasif/khata/core/data/migration/Migration14To15Test.kt` | Verifies schema v14 → v15 migration and conversion of legacy 6 debt kinds |
| `app/src/test/java/com/wasif/khata/feature/owed/OwedViewModelTest.kt` | Verifies person detail selection, entry history, and one-tap settle up |

**Modified**

| File | Change |
|---|---|
| `app/src/main/java/com/wasif/khata/core/model/Enums.kt` | Replace 6 legacy debt kinds on `TransactionKind` with `IOU` |
| `app/src/main/java/com/wasif/khata/core/data/entity/TransactionEntity.kt` | Add `owedMinor: Long = 0` |
| `app/src/main/java/com/wasif/khata/domain/model/Transaction.kt` | Add `owed: Money = Money.ZERO` |
| `app/src/main/java/com/wasif/khata/core/data/repository/Mappers.kt` | Map `owedMinor` to `Money(owedMinor)` |
| `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt` | Bump `version = 15` |
| `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt` | Add `MIGRATION_14_15` |
| `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt` | Register `MIGRATION_14_15` |
| `app/src/main/java/com/wasif/khata/core/backup/BackupRepository.kt` | Bump `SCHEMA_VERSION = 15` |
| `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt` | Update spending/income/owed SQL to use `owedMinor` and `IOU`; add `observeOwedEntriesForPerson`, `observeRecentCounterparties`, `settleAsOwed` |
| `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt` | Add `owed: Money = Money.ZERO` to `TransactionDraft`; add `settleAsOwed` |
| `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt` | Fix `save()` kind/owed handling, isolate `IOU` from account balance, implement `settleAsOwed` |
| `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorUiState.kt` | Mode choices (`Spent`, `Received`, `They paid`), split share state, recent people chips |
| `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorViewModel.kt` | Load/save `owed`, handle split share keypad and `IOU` mode |
| `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt` | Render 3-mode selector, person chips, and `All of it` / `Split` selector |
| `app/src/main/java/com/wasif/khata/feature/owed/OwedViewModel.kt` | Person detail state, history entries, accounts list, and `settlePerson` action |
| `app/src/main/java/com/wasif/khata/feature/owed/OwedScreen.kt` | Clickable person rows, `PersonOwedSheet`, and bottom-right `+` FAB |
| `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt` | Wire `onAddTransaction` on `OwedScreen` to `KhataRoutes.EditorNew` |
| `app/src/main/java/com/wasif/khata/feature/settle/SettleTransferViewModel.kt` | Add `recentPeople`, `counterpartyInput`, and `onSettleAsOwed` |
| `app/src/main/java/com/wasif/khata/feature/settle/SettleTransferSheet.kt` | Render `"With a person (Owed)"` chips, field, and confirm button |

---

### Task 1: Schema v15 (`owedMinor`), `TransactionKind.IOU`, and `MIGRATION_14_15`

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/model/Enums.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/entity/TransactionEntity.kt`
- Modify: `app/src/main/java/com/wasif/khata/domain/model/Transaction.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/repository/Mappers.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/backup/BackupRepository.kt`
- Modify: `app/src/test/java/com/wasif/khata/core/model/TransactionKindTest.kt`
- Create: `app/src/test/java/com/wasif/khata/core/data/migration/Migration14To15Test.kt`
- Modify: `app/src/test/java/com/wasif/khata/core/data/migration/Migration1To2Test.kt`, `Migration4To5Test.kt`, `Migration5To6Test.kt`, `Migration6To7Test.kt`, `Migration7To8Test.kt`, `Migration8To9Test.kt`, `Migration9To10Test.kt`, `Migration10To11Test.kt`, `Migration11To12Test.kt`, `Migration12To13Test.kt`, `Migration13To14Test.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `TransactionKind.IOU`, `TransactionEntity.owedMinor: Long`, `Transaction.owed: Money`, `MIGRATION_14_15`, `SCHEMA_VERSION = 15`.

- [ ] **Step 1: Update `TransactionKind` in `Enums.kt`**

Replace `TransactionKind` in `app/src/main/java/com/wasif/khata/core/model/Enums.kt`:

```kotlin
// Schema-level rather than a category: categories are user-editable, and a renamed
// or deleted one must not be able to break net-worth arithmetic.
enum class TransactionKind {
    NORMAL,
    TRANSFER,

    /** A loan from an institution, as bKash's digital loan. Not person-to-person. */
    LOAN_DISBURSEMENT,
    LOAN_REPAYMENT,
    FEE,
    ADJUSTMENT,

    /**
     * A bill or loan someone else covered on your behalf where no money moved in or
     * out of your own accounts yet. Counts as spending when incurred and sits on the
     * person's Owed tab until settled.
     */
    IOU,
    ;

    val countsAsSpending: Boolean
        get() = this !in setOf(
            TRANSFER, ADJUSTMENT, LOAN_REPAYMENT,
        )

    val countsAsIncome: Boolean
        get() = this !in setOf(
            TRANSFER, ADJUSTMENT, LOAN_DISBURSEMENT, IOU,
        )
}
```

- [ ] **Step 2: Add `owedMinor` to `TransactionEntity`, `Transaction`, and `Mappers.kt`**

In `app/src/main/java/com/wasif/khata/core/data/entity/TransactionEntity.kt`, after `counterparty`:

```kotlin
    val counterparty: String? = null,
    /**
     * How much of this transaction moves the tab with [counterparty]. Zero on an
     * ordinary purchase; equal to [amountMinor] on a full loan, repayment, or IOU;
     * between the two when splitting a bill.
     */
    val owedMinor: Long = 0,
```

In `app/src/main/java/com/wasif/khata/domain/model/Transaction.kt`, after `counterparty`:

```kotlin
    val counterparty: String? = null,
    val owed: Money = Money.ZERO,
```

In `app/src/main/java/com/wasif/khata/core/data/repository/Mappers.kt`, inside `TransactionEntity.toDomain()`:

```kotlin
    counterparty = counterparty,
    owed = Money(owedMinor),
```

- [ ] **Step 3: Bump Room version, Backup `SCHEMA_VERSION`, and write `MIGRATION_14_15`**

In `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt`, change `version = 14` to `version = 15`.

In `app/src/main/java/com/wasif/khata/core/backup/BackupRepository.kt`, change `const val SCHEMA_VERSION = 14` to `const val SCHEMA_VERSION = 15`.

In `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt`, add `MIGRATION_14_15` at the top:

```kotlin
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `owedMinor` INTEGER NOT NULL DEFAULT 0",
        )
        // Convert the six legacy person-to-person kinds into owedMinor + direction on
        // NORMAL rows so existing debts keep their exact net balance.
        db.execSQL(
            "UPDATE `transactions` SET `owedMinor` = `amountMinor`, `direction` = 'DEBIT', " +
                "`kind` = 'NORMAL' WHERE `kind` IN ('LENT', 'COVERED_FOR_SOMEONE', 'BORROWED_RETURNED')",
        )
        db.execSQL(
            "UPDATE `transactions` SET `owedMinor` = `amountMinor`, `direction` = 'CREDIT', " +
                "`kind` = 'NORMAL' WHERE `kind` IN ('LENT_RETURNED', 'REIMBURSEMENT', 'BORROWED')",
        )
    }
}
```

In `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt`, import `MIGRATION_14_15` and append `, MIGRATION_14_15` to `addMigrations(...)`.

- [ ] **Step 4: Update `TransactionKindTest.kt` and write `Migration14To15Test.kt`**

Replace `app/src/test/java/com/wasif/khata/core/model/TransactionKindTest.kt`:

```kotlin
package com.wasif.khata.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionKindTest {

    @Test
    fun `buying something or incurring an IOU is spending`() {
        assertTrue(TransactionKind.NORMAL.countsAsSpending)
        assertTrue(TransactionKind.FEE.countsAsSpending)
        assertTrue(TransactionKind.IOU.countsAsSpending)
    }

    @Test
    fun `paying back an institutional loan is not spending`() {
        assertFalse(TransactionKind.LOAN_REPAYMENT.countsAsSpending)
    }

    @Test
    fun `moving your own money is never spending`() {
        assertFalse(TransactionKind.TRANSFER.countsAsSpending)
        assertFalse(TransactionKind.ADJUSTMENT.countsAsSpending)
    }

    @Test
    fun `earning is income, while loans and IOUs are not`() {
        assertTrue(TransactionKind.NORMAL.countsAsIncome)
        assertFalse(TransactionKind.LOAN_DISBURSEMENT.countsAsIncome)
        assertFalse(TransactionKind.IOU.countsAsIncome)
    }

    @Test
    fun `the SQL that totals spending lists exactly the kinds that do not count`() {
        val excludedInSql = setOf(
            TransactionKind.TRANSFER,
            TransactionKind.ADJUSTMENT,
            TransactionKind.LOAN_REPAYMENT,
        )
        assertTrue(
            TransactionKind.entries.filterNot { it.countsAsSpending }.toSet() == excludedInSql,
        )
    }
}
```

Create `app/src/test/java/com/wasif/khata/core/data/migration/Migration14To15Test.kt`:

```kotlin
package com.wasif.khata.core.data.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
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

private const val TEST_DB = "migration-14-15-test.db"
private const val SCHEMA_DIR = "schemas/com.wasif.khata.core.data.KhataDatabase"

@RunWith(RobolectricTestRunner::class)
class Migration14To15Test {

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
        assertNotNull("cannot locate $version.json", file)
        return JSONObject(file!!.readText()).getJSONObject("database")
    }

    private fun createV14Database(): SupportSQLiteDatabase {
        val database = schemaJson(14)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
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
        db.version = 14
        return db
    }

    @Test
    fun `legacy owed kinds migrate to NORMAL with owedMinor equal to amountMinor`() = runTest {
        createV14Database().use { db ->
            db.execSQL(
                "INSERT INTO transactions (uuid, accountId, amountMinor, direction, kind, " +
                    "occurredAt, merchantRaw, note, counterparty, transferReviewPending, " +
                    "source, confidence, createdAt, updatedAt) VALUES " +
                    "('t1', 1, 50000, 'DEBIT', 'LENT', 1000, NULL, NULL, 'Rafi', 0, 'MANUAL', 'HIGH', 1, 1), " +
                    "('t2', 1, 20000, 'CREDIT', 'BORROWED', 2000, NULL, NULL, 'Sadia', 0, 'MANUAL', 'HIGH', 1, 1)",
            )
        }

        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()

        val rafi = db.transactionDao().findById(1)!!
        assertEquals(TransactionKind.NORMAL, rafi.kind)
        assertEquals(TransactionDirection.DEBIT, rafi.direction)
        assertEquals(50_000L, rafi.owedMinor)

        val sadia = db.transactionDao().findById(2)!!
        assertEquals(TransactionKind.NORMAL, sadia.kind)
        assertEquals(TransactionDirection.CREDIT, sadia.direction)
        assertEquals(20_000L, sadia.owedMinor)

        db.close()
    }
}
```

Append `, MIGRATION_14_15` to `addMigrations(...)` in `Migration1To2Test.kt`, `Migration4To5Test.kt`, `Migration5To6Test.kt`, `Migration6To7Test.kt`, `Migration7To8Test.kt`, `Migration8To9Test.kt`, `Migration9To10Test.kt`, `Migration10To11Test.kt`, `Migration11To12Test.kt`, `Migration12To13Test.kt`, and `Migration13To14Test.kt`.

---

### Task 2: Owed, Spending, and Income Queries in `TransactionDao` + Repository Arithmetic

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`
- Modify: `app/src/test/java/com/wasif/khata/feature/owed/OwedLedgerTest.kt`
- Modify: `app/src/test/java/com/wasif/khata/core/data/repository/OwedMoneyTest.kt`
- Modify: `app/src/test/java/com/wasif/khata/core/data/repository/SettleTransferTest.kt`

**Interfaces:**
- Consumes: `TransactionEntity.owedMinor`, `TransactionKind.IOU` (Task 1).
- Produces:
  - `TransactionDao.observeOwedByPerson(): Flow<List<OwedRow>>`
  - `TransactionDao.observeUnnamedOwed(): Flow<List<TransactionEntity>>`
  - `TransactionDao.observeOwedEntriesForPerson(name: String): Flow<List<TransactionEntity>>`
  - `TransactionDao.observeRecentCounterparties(limit: Int): Flow<List<String>>`
  - `TransactionDao.settleAsOwed(id: Long, counterparty: String, updatedAt: Long)`
  - `TransactionDraft.owed: Money = Money.ZERO`
  - `TransactionRepository.settleAsOwed(transactionId: Long, counterparty: String): Result<Unit>`

- [ ] **Step 1: Write the failing tests in `OwedLedgerTest.kt`, `OwedMoneyTest.kt`, and `SettleTransferTest.kt`**

In `app/src/test/java/com/wasif/khata/feature/owed/OwedLedgerTest.kt`, update the helper `row(...)` to use `owedMinor` + `TransactionKind.NORMAL` / `TransactionKind.IOU` and add tests for bill splitting, `IOU`, `observeOwedEntriesForPerson`, and `observeRecentCounterparties`:

```kotlin
    private suspend fun row(
        amount: Long,
        who: String?,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        owed: Long = amount,
        kind: TransactionKind = TransactionKind.NORMAL,
    ) = db.transactionDao().upsert(
        TransactionEntity(
            uuid = "t${seq++}",
            accountId = if (kind == TransactionKind.IOU) 0 else 1,
            amountMinor = amount,
            direction = direction,
            occurredAt = 1_000L + seq,
            merchantRaw = null,
            merchantId = null,
            categoryId = null,
            note = null,
            counterparty = who,
            owedMinor = owed,
            source = TransactionSource.MANUAL,
            confidence = Confidence.HIGH,
            rawMessageId = null,
            transferGroupId = null,
            feeMinor = null,
            referenceNumber = null,
            kind = kind,
            createdAt = 1,
            updatedAt = 1,
        )
    )
```

In `app/src/test/java/com/wasif/khata/core/data/repository/OwedMoneyTest.kt`, add tests pinning:
1. Reclassifying an existing `NORMAL` row (`id != null`) to have `counterparty = "Rafi"` and `owed = Money(50_000)` updates `owedMinor`, removes the owed portion from spending, and surfaces Rafi in `observeOwedByPerson()`.
2. Splitting a ৳1,000 purchase (`amount = 100_000`, `owed = 40_000`, `who = "Rafi"`) deducts ৳1,000 from the account balance, counts ৳600 as spending, and puts `+40_000` on Rafi's Owed tab.
3. Recording a pure `IOU` (`kind = TransactionKind.IOU`, `amount = 50_000`, `owed = 50_000`, `who = "Rafi"`) leaves the account balance at `0`, counts `50_000` in spending, and puts `-50_000` on Rafi's Owed tab; settling it with a ৳500 `DEBIT` (`owed = 50_000`) moves the account balance by `-50_000`, adds `0` to spending, and nets Rafi out of `observeOwedByPerson()`.

In `app/src/test/java/com/wasif/khata/core/data/repository/SettleTransferTest.kt`, add:

```kotlin
    @Test
    fun `settling a transfer as owed records the person and removes it from spending`() = runTest {
        val ebl = account("EBL", opening = 20_000_00)
        val id = parsedDebit(ebl, amountMinor = 5_000_00, merchantRaw = "EBL Account Transfer")

        repository.settleAsOwed(id, counterparty = "Rafi").getOrThrow()

        val updated = db.transactionDao().findById(id)!!
        assertEquals("Rafi", updated.counterparty)
        assertEquals(5_000_00L, updated.owedMinor)
        assertFalse(updated.transferReviewPending)
        assertEquals(0L, repository.observeSpentBetween(0, Long.MAX_VALUE).first().minor)
        assertEquals(5_000_00L, db.transactionDao().observeOwedByPerson().first().single().netMinor)
    }
```

- [ ] **Step 2: Update `TransactionDao.kt`**

In `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`:
- Update `allForAccount`:
  ```kotlin
  @Query("SELECT * FROM transactions WHERE accountId = :accountId AND kind != 'IOU' AND deletedAt IS NULL")
  suspend fun allForAccount(accountId: Long): List<TransactionEntity>
  ```
- Add `settleAsOwed`:
  ```kotlin
  @Query(
      "UPDATE transactions SET counterparty = :counterparty, owedMinor = amountMinor, " +
          "transferReviewPending = 0, updatedAt = :updatedAt WHERE id = :id",
  )
  suspend fun settleAsOwed(id: Long, counterparty: String, updatedAt: Long)
  ```
- Update `observeTotalMinorBetween`:
  ```kotlin
  @Query(
      """
      SELECT COALESCE(SUM(
          CASE WHEN kind = 'IOU' THEN amountMinor
               WHEN amountMinor > owedMinor THEN amountMinor - owedMinor
               ELSE 0 END
      ), 0) FROM transactions
      WHERE deletedAt IS NULL
        AND direction = :direction
        AND transferGroupId IS NULL
        AND kind NOT IN ('TRANSFER', 'ADJUSTMENT', 'LOAN_REPAYMENT')
        AND (:direction = 'DEBIT' OR kind NOT IN ('LOAN_DISBURSEMENT', 'IOU'))
        AND occurredAt >= :fromInclusive
        AND occurredAt < :toExclusive
      """,
  )
  fun observeTotalMinorBetween(
      direction: TransactionDirection,
      fromInclusive: Long,
      toExclusive: Long,
  ): Flow<Long>
  ```
- Update `observeOwedByPerson`, `observeUnnamedOwed`, and add `observeOwedEntriesForPerson` and `observeRecentCounterparties`:
  ```kotlin
  @Query(
      """
      SELECT TRIM(counterparty) AS name,
             SUM(CASE WHEN kind = 'IOU' THEN -owedMinor
                      WHEN direction = 'DEBIT' THEN owedMinor
                      ELSE -owedMinor END) AS netMinor,
             COUNT(*) AS entries
      FROM transactions
      WHERE deletedAt IS NULL
        AND counterparty IS NOT NULL AND TRIM(counterparty) != ''
        AND owedMinor > 0
      GROUP BY LOWER(TRIM(counterparty))
      HAVING netMinor != 0
      ORDER BY ABS(netMinor) DESC
      """,
  )
  fun observeOwedByPerson(): Flow<List<OwedRow>>

  @Query(
      """
      SELECT * FROM transactions
      WHERE deletedAt IS NULL
        AND (counterparty IS NULL OR TRIM(counterparty) = '')
        AND owedMinor > 0
      ORDER BY occurredAt DESC, id DESC
      """,
  )
  fun observeUnnamedOwed(): Flow<List<TransactionEntity>>

  @Query(
      """
      SELECT * FROM transactions
      WHERE deletedAt IS NULL
        AND counterparty IS NOT NULL
        AND LOWER(TRIM(counterparty)) = LOWER(TRIM(:name))
        AND owedMinor > 0
      ORDER BY occurredAt DESC, id DESC
      """,
  )
  fun observeOwedEntriesForPerson(name: String): Flow<List<TransactionEntity>>

  @Query(
      """
      SELECT TRIM(counterparty) FROM transactions
      WHERE deletedAt IS NULL
        AND counterparty IS NOT NULL AND TRIM(counterparty) != ''
      GROUP BY LOWER(TRIM(counterparty))
      ORDER BY MAX(occurredAt) DESC
      LIMIT :limit
      """,
  )
  fun observeRecentCounterparties(limit: Int): Flow<List<String>>
  ```
- Update `observeSpendByCategory`, `compareCategorySpend`, `observeTopMerchants`, and `observeDayTotals` so they sum `CASE WHEN kind = 'IOU' THEN amountMinor ELSE MAX(amountMinor - owedMinor, 0) END` over `kind NOT IN ('TRANSFER', 'ADJUSTMENT', 'LOAN_REPAYMENT')` and filter out rows whose net spend is `0` (`(kind = 'IOU' OR amountMinor > owedMinor)`).
- Update `dailyMovementByAccount()` to include `WHERE deletedAt IS NULL AND kind != 'IOU'`.

- [ ] **Step 3: Update `TransactionRepository.kt` and `TransactionRepositoryImpl.kt`**

In `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`:
- Add `val owed: Money = Money.ZERO,` to `TransactionDraft`.
- Add `suspend fun settleAsOwed(transactionId: Long, counterparty: String): Result<Unit> = Result.success(Unit)` to `TransactionRepository`.

In `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`:
- In `save(draft: TransactionDraft)`:
  ```kotlin
            if (existing != null && existing.kind != TransactionKind.IOU) {
                accountDao.adjustBalance(existing.accountId, -existing.signedMinor(), now)
            }

            val resolvedKind = if (
                existing != null &&
                existing.kind !in setOf(TransactionKind.NORMAL, TransactionKind.IOU) &&
                draft.kind == TransactionKind.NORMAL
            ) {
                existing.kind
            } else {
                draft.kind
            }
            val clampedOwedMinor = draft.owed.minor.coerceIn(0L, draft.amount.minor)

            val rowId = transactionDao.upsert(
                TransactionEntity(
                    id = existing?.id ?: 0,
                    uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                    accountId = if (resolvedKind == TransactionKind.IOU) 0L else draft.accountId,
                    amountMinor = draft.amount.minor,
                    direction = draft.direction,
                    occurredAt = draft.occurredAt,
                    merchantRaw = draft.merchantRaw,
                    merchantId = existing?.merchantId,
                    categoryId = draft.categoryId,
                    note = draft.note,
                    counterparty = draft.counterparty,
                    owedMinor = clampedOwedMinor,
                    source = existing?.source ?: draft.source,
                    confidence = Confidence.HIGH,
                    kind = resolvedKind,
                    rawMessageId = existing?.rawMessageId,
                    transferGroupId = existing?.transferGroupId,
                    feeMinor = existing?.feeMinor,
                    referenceNumber = existing?.referenceNumber,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )

            if (resolvedKind != TransactionKind.IOU) {
                accountDao.adjustBalance(draft.accountId, signedMinor(draft.amount.minor, draft.direction), now)
            }
  ```
- In `recordUnexplained(draft: TransactionDraft)`: pass `owedMinor = draft.owed.minor.coerceIn(0L, draft.amount.minor)`.
- In `delete(id: Long)`:
  ```kotlin
            if (existing.kind != TransactionKind.IOU) {
                accountDao.adjustBalance(existing.accountId, -existing.signedMinor(), now)
            }
  ```
- Implement `settleAsOwed`:
  ```kotlin
    override suspend fun settleAsOwed(
        transactionId: Long,
        counterparty: String,
    ): Result<Unit> = runCatchingData {
        val trimmed = counterparty.trim()
        require(trimmed.isNotEmpty()) { "Counterparty name cannot be blank" }
        db.withTransaction {
            transactionDao.findById(transactionId) ?: throw DataError.NotFound
            transactionDao.settleAsOwed(transactionId, trimmed, clock.now())
            searchIndex.reindex("transaction", transactionId)
        }
    }
  ```

- [ ] **Step 4: Run the unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*OwedLedgerTest*" --tests "*OwedMoneyTest*" --tests "*SettleTransferTest*" --tests "*TransactionRepositoryImplTest*"`
Expected: PASS.

- [ ] **Step 5: Commit Tasks 1 & 2**

```bash
git add app/src app/schemas
git commit -m "feat(owed): single-table owedMinor and IOU model with split arithmetic"
```

---

### Task 3: `TransactionEditorViewModel` & `TransactionEditorScreen` ("With someone", Splits, and "They paid" IOUs)

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorUiState.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt`
- Modify: `app/src/test/java/com/wasif/khata/feature/editor/TransactionEditorViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/wasif/khata/feature/editor/TransactionEditorScreenTest.kt`

**Interfaces:**
- Consumes: `TransactionDraft.owed`, `TransactionDao.observeRecentCounterparties` (Task 2).
- Produces: `EditorMode` (`SPENT`, `RECEIVED`, `THEY_PAID`), `TransactionEditorUiState`, `TransactionEditorActions`, `TransactionEditorScreen`.

- [ ] **Step 1: Update `TransactionEditorUiState.kt`**

Replace `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorUiState.kt`:

```kotlin
package com.wasif.khata.feature.editor

import androidx.compose.runtime.Stable
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category

enum class EditorMode(val label: String) {
    SPENT("Spent"),
    RECEIVED("Received"),
    THEY_PAID("They paid"),
}

data class TransactionEditorUiState(
    val amountInput: String = "",
    val merchantInput: String = "",
    val noteInput: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val kind: TransactionKind = TransactionKind.NORMAL,
    val counterpartyInput: String = "",
    /** Null means "All of it" (full amount) when a person is attached; non-null is a custom split share. */
    val customOwedInput: String? = null,
    val occurredAt: Long = 0L,

    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recentPeople: List<String> = emptyList(),

    /** The SMS this row was parsed from. Null for a row typed by hand. */
    val originalMessage: String? = null,

    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    val amount: Money? get() = if (amountInput.isBlank()) null else Money.parse(amountInput)

    val amountHasError: Boolean get() = amountInput.isNotBlank() && amount == null

    val mode: EditorMode
        get() = when {
            kind == TransactionKind.IOU -> EditorMode.THEY_PAID
            direction == TransactionDirection.CREDIT -> EditorMode.RECEIVED
            else -> EditorMode.SPENT
        }

    /** An SMS-parsed row already moved a real account, so it cannot become a no-account IOU. */
    val availableModes: List<EditorMode>
        get() = if (isEditing && originalMessage != null && kind != TransactionKind.IOU) {
            listOf(EditorMode.SPENT, EditorMode.RECEIVED)
        } else {
            EditorMode.entries
        }

    val isIou: Boolean get() = kind == TransactionKind.IOU

    val hasPerson: Boolean get() = counterpartyInput.isNotBlank() || isIou || customOwedInput != null

    val isSplit: Boolean get() = mode == EditorMode.SPENT && customOwedInput != null

    val owedAmount: Money
        get() {
            val total = amount ?: return Money.ZERO
            if (!hasPerson) return Money.ZERO
            if (mode != EditorMode.SPENT || customOwedInput == null) return total
            val parsed = Money.parse(customOwedInput) ?: return Money.ZERO
            return Money(parsed.minor.coerceIn(0L, total.minor))
        }

    val wantsAccount: Boolean get() = !isIou

    val wantsMerchant: Boolean
        get() = direction == TransactionDirection.DEBIT

    val canSave: Boolean
        get() {
            val validAmount = amount.let { it != null && !it.isZero }
            val validAccount = isIou || accountId != null
            val validPerson = !isIou || counterpartyInput.isNotBlank()
            val validSplit = !isSplit || owedAmount.let { !it.isZero && it.minor <= (amount?.minor ?: 0L) }
            return validAmount && validAccount && validPerson && validSplit && !isSaving
        }
}

sealed interface TransactionEditorEffect {
    data object Saved : TransactionEditorEffect
    data object Deleted : TransactionEditorEffect
}

@Stable
interface TransactionEditorActions {
    fun onAmountChange(value: String)
    fun onMerchantChange(value: String)
    fun onNoteChange(value: String)
    fun onAccountSelected(id: Long)
    fun onCategorySelected(id: Long?)
    fun onDirectionChange(direction: TransactionDirection)
    fun onModeChange(mode: EditorMode)
    fun onKindChange(kind: TransactionKind)
    fun onCounterpartyChange(value: String)
    fun onSelectAllOwed()
    fun onSelectSplitOwed()
    fun onCustomOwedChange(value: String)
    fun onSave()
    fun onDelete()
}
```

- [ ] **Step 2: Update `TransactionEditorViewModel.kt`**

Update `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorViewModel.kt` to load `existing.owed` (setting `customOwedInput` when `0 < existing.owed.minor < existing.amount.minor`), implement `onModeChange`, `onSelectAllOwed`, `onSelectSplitOwed`, `onCustomOwedChange`, and pass `owed = state.owedAmount` in `TransactionDraft` when saving.

- [ ] **Step 3: Update `TransactionEditorScreen.kt`**

In `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt`:
- Replace the 2-way `TransactionDirection` row with `state.availableModes` (`Spent`, `Received`, `They paid`).
- Hide the `Account` section when `!state.wantsAccount` (`isIou`), and set `ContextHeader` subline to `"Owed only · no account moved"` when `state.isIou`.
- Replace the `"What kind"` dropdown with the `"With someone"` section:
  - If `state.recentPeople.isNotEmpty()`, show a `FlowRow` of `EditorChip`s for each person (tapping toggles `counterpartyInput`).
  - Show the `OutlinedTextField` for `state.counterpartyInput` (`placeholder = { Text(if (state.isIou) "Required" else "Optional") }`).
  - When `state.mode == EditorMode.SPENT && state.counterpartyInput.isNotBlank()`, show a two-pill `GlassChoice` row:
    - `"All of it"` (`selected = !state.isSplit`, `onClick = actions::onSelectAllOwed`)
    - `"Split · ${state.owedAmount.format()}"` (`selected = state.isSplit`, `onClick` selects split and opens `AmountKeypadDialog(title = "Their share")` to edit `customOwedInput`).

- [ ] **Step 4: Update `TransactionEditorViewModelTest.kt` and `TransactionEditorScreenTest.kt`**

Add tests for:
- `onModeChange(EditorMode.THEY_PAID)` requires a counterparty name before `canSave` becomes true, and saves `kind = TransactionKind.IOU` with `owed = amount`.
- `onSelectSplitOwed()` defaults the split share to half the amount and `onCustomOwedChange` updates `owedAmount` (clamped to `amount`).
- Update `RecordingActions` and `NoopActions` in `TransactionEditorScreenTest.kt` to implement the new `TransactionEditorActions` methods.

- [ ] **Step 5: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*TransactionEditorViewModelTest*"`
Expected: PASS.

- [ ] **Step 6: Commit Task 3**

```bash
git add app/src
git commit -m "feat(editor): replace 6-kind dropdown with person chips, splits, and IOU mode"
```

---

### Task 4: Interactive `OwedScreen`, Person Detail Sheet, and One-Tap Settle Up

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/owed/OwedViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/owed/OwedScreen.kt`
- Modify: `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`
- Create: `app/src/test/java/com/wasif/khata/feature/owed/OwedViewModelTest.kt`

**Interfaces:**
- Consumes: `TransactionDao.observeOwedByPerson`, `observeUnnamedOwed`, `observeOwedEntriesForPerson`, `TransactionRepository.save`, `ReferenceDataRepository.observeAccounts`.
- Produces: `OwedViewModel.onSelectPerson(name: String?)`, `OwedViewModel.onSelectSettleAccount(accountId: Long)`, `OwedViewModel.onSettleSelectedPerson()`, `OwedScreen(onBack, onOpenTransaction, onAddTransaction)`.

- [ ] **Step 1: Write the failing test in `OwedViewModelTest.kt`**

Create `app/src/test/java/com/wasif/khata/feature/owed/OwedViewModelTest.kt` testing:
1. Selecting a person (`onSelectPerson("Rafi")`) loads their active owed entries (`PersonOwedDetail`) and preselects the `Cash` account for settling.
2. Calling `onSettleSelectedPerson()` when Rafi owes you ৳500 writes a `CREDIT` `TransactionDraft` of ৳500 (`owed = Money(50_000)`, `counterparty = "Rafi"`, `note = "Settled up"`) to the selected account and closes the sheet (`selectedPerson == null`).
3. Calling `onSettleSelectedPerson()` when you owe Sadia ৳200 writes a `DEBIT` `TransactionDraft` of ৳200 (`owed = Money(20_000)`, `counterparty = "Sadia"`, `note = "Settled up"`) to the selected account.

- [ ] **Step 2: Update `OwedViewModel.kt`**

Update `app/src/main/java/com/wasif/khata/feature/owed/OwedViewModel.kt` to inject `TransactionDao`, `TransactionRepository`, `ReferenceDataRepository`, and `KhataClock`:
- Expose `selectedPerson: PersonOwedDetail?` in `OwedUiState` (containing `person: Person`, `entries: List<PersonOwedEntry>`, `accounts: List<Account>`, `selectedAccountId: Long?`).
- Implement `onSelectPerson(name: String?)`, `onSelectSettleAccount(accountId: Long)`, and `onSettleSelectedPerson()`.

- [ ] **Step 3: Update `OwedScreen.kt` and `KhataNavHost.kt`**

In `app/src/main/java/com/wasif/khata/feature/owed/OwedScreen.kt`:
- Add `onAddTransaction: () -> Unit = {}` parameter to `OwedScreen` and `OwedContent`.
- Make `PersonRow` clickable (`onClick = { onSelectPerson(person.name) }`).
- Add the bottom-right `+` FAB in the thumb arc (matching `LedgerScreen.kt`: 58.dp circle on `Brush.linearGradient(KhataPalette.heroStops)` with `Icons.Filled.Add`, `contentDescription = "Add entry"`).
- When `state.selectedPerson != null`, show `PersonOwedSheet` (`ModalBottomSheet`) listing the person's entries (tapping an entry invokes `onOpenTransaction(entry.id)`), account `EditorChip`s, and the full-width Petrol gradient settle pill (`"Receive ${detail.person.amount.format()}"` or `"Pay ${detail.person.amount.format()}"`).

In `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`:
- Pass `onAddTransaction = { navController.navigate(KhataRoutes.EditorNew) }` to `OwedScreen`.

- [ ] **Step 4: Run `OwedViewModelTest`**

Run: `./gradlew :app:testDebugUnitTest --tests "*OwedViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```bash
git add app/src
git commit -m "feat(owed): interactive person sheet with history, settle up, and quick-add FAB"
```

---

### Task 5: `SettleTransferSheet` & `SettleTransferViewModel` ("With a person" Owed Settlement)

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/settle/SettleTransferViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/settle/SettleTransferSheet.kt`
- Modify: `app/src/test/java/com/wasif/khata/feature/settle/SettleTransferViewModelTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository.settleAsOwed`, `TransactionDao.observeRecentCounterparties` (Task 2).
- Produces: `SettleTransferUiState.recentPeople`, `SettleTransferUiState.counterpartyInput`, `SettleTransferViewModel.onCounterpartyChange(String)`, `SettleTransferViewModel.onSettleAsOwed()`.

- [ ] **Step 1: Write the failing tests in `SettleTransferViewModelTest.kt`**

In `app/src/test/java/com/wasif/khata/feature/settle/SettleTransferViewModelTest.kt`, add tests for:
1. Typing a person's name (`onCounterpartyChange("Rafi")`) and calling `onSettleAsOwed()` calls `repository.settleAsOwed(7L, "Rafi")` and marks `done = true`.
2. Calling `onSettleAsOwed()` with a blank or whitespace-only name does not call `repository.settleAsOwed` and leaves `done = false`.

- [ ] **Step 2: Update `SettleTransferViewModel.kt` and `SettleTransferSheet.kt`**

In `app/src/main/java/com/wasif/khata/feature/settle/SettleTransferViewModel.kt`:
- Add `val recentPeople: List<String> = emptyList()` and `val counterpartyInput: String = ""` to `SettleTransferUiState`.
- Add `fun onCounterpartyChange(name: String)` and `fun onSettleAsOwed()`.

In `app/src/main/java/com/wasif/khata/feature/settle/SettleTransferSheet.kt`:
- Below `"It moved to one of my accounts"`, add `SectionLabel(top = spacing.md, text = "With a person (Owed)")`:
  - Show `FlowRow` of recent person chips if `state.recentPeople.isNotEmpty()`.
  - Show an `OutlinedTextField` for `state.counterpartyInput` (`placeholder = { Text("Person's name") }`).
  - Show an `OutlinedButton` enabled when `state.counterpartyInput.isNotBlank()`: `"Record as owed with ${state.counterpartyInput.trim()}"`, calling `viewModel::onSettleAsOwed`.

- [ ] **Step 3: Run unit tests across the entire project**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit Task 5**

```bash
git add app/src
git commit -m "feat(settle): settle unpaired transfers directly to a person's Owed tab"
```
