# Khata Plan — Budgets and Insights

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Per-category monthly budgets that keep past months honest, and an Insights screen that answers "where did my money go, and is that more than last month".

**Architecture:** `category_budgets` at schema v6 holds one row per category per period, closed and reopened on edit so a limit change never rewrites a finished month. A repository owns which row applies to a month, because that is the only place the rule can be got wrong quietly. The global budget preference retires into a row and the hub ring becomes the sum of active limits. Insights is a new screen off Wallet, three sections and no new chart. Six tasks: the table, the budget rules, retiring the preference, the insight queries, the ViewModel, then the screen.

**Tech Stack:** Kotlin · Room + KSP · Hilt · Coroutines/Flow · Jetpack Compose · Material 3 · JUnit4 + Robolectric + Turbine.

**Spec:** `docs/superpowers/specs/2026-09-03-khata-budgets-and-insights-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`, `compileSdk` / `targetSdk 37`, package `com.wasif.khata`.
- Money is always `Long` **paisa**. Never `Double`, never `Float`.
- Every table carries `uuid TEXT NOT NULL`, `createdAt`, `updatedAt`, `deletedAt` (nullable). Deletes are soft.
- Instants are UTC epoch millis; every month boundary is Dhaka, via the existing `dhakaMonthStart()` / `dhakaNextMonthStart()`.
- **Budget progress counts what `observeSpendByCategory` counts** — `DEBIT`, no transfers, excluding `LENT`, `BORROWED_RETURNED`, `LOAN_REPAYMENT`, `COVERED_FOR_SOMEONE`. Never a second definition of spending (spec §3).
- **No colour literal outside `core/ui/theme`** (`DESIGN.md` §1.1, test-enforced). **Colour is never the only signal** (§1.3) — over-budget carries a word, not just a hue.
- Amounts render through `MoneyText` or a style carrying `fontFeatureSettings = "tnum"`.
- Derived UI values are getters on `UiState`, never constructor parameters.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- Tests never hardcode a magic epoch-millis literal — build them with `Instant.parse("…Z").toEpochMilli()`.
- **Ponytail is in force.** Reuse before writing: the category bar on `WalletScreen` is the bar Insights needs, `SectionLabel`/`CollapsingTopBar`/`FieldScaffold` are how a secondary screen is built (see `OwedScreen`), `observeSpendByCategory` already defines spending.
- Run unit tests with: `./gradlew :app:testDebugUnitTest`
- Build with: `./gradlew :app:assembleDebug`
- `export JAVA_HOME="/e/Android/Android Studio/jbr"` and `export PATH="$JAVA_HOME/bin:$PATH"` per shell.

## File Structure

**Create:**
- `app/src/main/java/com/wasif/khata/core/data/entity/CategoryBudgetEntity.kt`
- `app/src/main/java/com/wasif/khata/core/data/dao/CategoryBudgetDao.kt`
- `app/src/main/java/com/wasif/khata/core/data/repository/BudgetRepository.kt` — the only place that decides which limit applies and how an edit is recorded.
- `app/src/main/java/com/wasif/khata/feature/insights/InsightsUiState.kt`, `InsightsViewModel.kt`, `InsightsScreen.kt`
- Tests: `Migration5To6Test.kt`, `BudgetRepositoryTest.kt`, `InsightsQueriesTest.kt`, `InsightsViewModelTest.kt`

**Modify:**
- `KhataDatabase.kt` (v6), `Migrations.kt`, `DatabaseModule.kt`
- `TransactionDao.kt` — month-over-month and top-merchant queries.
- `KhataPreferences.kt`, `PreferencesRepository.kt`, `PreferencesRepositoryImpl.kt` — drop `monthlyBudgetMinor`.
- `SettingsScreen.kt`, `SettingsViewModel.kt` — remove the monthly-budget field.
- `ModulesViewModel.kt` — the ring reads budget rows.
- `WalletScreen.kt`, `KhataNavHost.kt` — the Insights link and route.

---

### Task 1: The table, at schema v6

Spec §1. Structure only; the rules are Task 2.

**Files:**
- Create: `entity/CategoryBudgetEntity.kt`, `dao/CategoryBudgetDao.kt`
- Modify: `KhataDatabase.kt`, `migration/Migrations.kt`, `di/DatabaseModule.kt`
- Test: `test/.../migration/Migration5To6Test.kt`

**Interfaces:**
- Produces: `CategoryBudgetEntity(id, uuid, categoryId, limitMinor, effectiveFrom, effectiveTo, createdAt, updatedAt, deletedAt)`; `CategoryBudgetDao` with `suspend fun upsert(row: CategoryBudgetEntity): Long`, `suspend fun activeAt(atMillis: Long): List<CategoryBudgetEntity>`, `suspend fun rowFor(categoryId: Long, atMillis: Long): CategoryBudgetEntity?`, `fun observeActiveAt(atMillis: Long): Flow<List<CategoryBudgetEntity>>`, `suspend fun allFor(categoryId: Long): List<CategoryBudgetEntity>`. Tasks 2–5 consume these.

- [ ] **Step 1: Write the entity**

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One category's spending limit for one period.
 *
 * Versioned rather than editable in place: a single mutable limit would judge every
 * past month against today's number, so tightening groceries in September would
 * retroactively make last March a month you overspent.
 */
@Entity(
    tableName = "category_budgets",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["effectiveFrom"]),
    ],
)
data class CategoryBudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val categoryId: Long,
    val limitMinor: Long,
    /** Dhaka month start, inclusive. */
    val effectiveFrom: Long,
    /** Dhaka month start, exclusive. Null means still in force. */
    val effectiveTo: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

- [ ] **Step 2: Write the DAO**

```kotlin
package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.CategoryBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {

    @Upsert
    suspend fun upsert(row: CategoryBudgetEntity): Long

    // "In force at this instant": started on or before it, and not yet closed or
    // closed after it. The same predicate serves one category and all of them, so
    // the rule cannot drift between the two.
    @Query(
        """
        SELECT * FROM category_budgets
        WHERE deletedAt IS NULL AND effectiveFrom <= :atMillis
          AND (effectiveTo IS NULL OR effectiveTo > :atMillis)
        """,
    )
    suspend fun activeAt(atMillis: Long): List<CategoryBudgetEntity>

    @Query(
        """
        SELECT * FROM category_budgets
        WHERE deletedAt IS NULL AND effectiveFrom <= :atMillis
          AND (effectiveTo IS NULL OR effectiveTo > :atMillis)
        """,
    )
    fun observeActiveAt(atMillis: Long): Flow<List<CategoryBudgetEntity>>

    @Query(
        """
        SELECT * FROM category_budgets
        WHERE deletedAt IS NULL AND categoryId = :categoryId AND effectiveFrom <= :atMillis
          AND (effectiveTo IS NULL OR effectiveTo > :atMillis)
        LIMIT 1
        """,
    )
    suspend fun rowFor(categoryId: Long, atMillis: Long): CategoryBudgetEntity?

    @Query("SELECT * FROM category_budgets WHERE categoryId = :categoryId ORDER BY effectiveFrom")
    suspend fun allFor(categoryId: Long): List<CategoryBudgetEntity>
}
```

- [ ] **Step 3: Register it, bump to v6, add the migration**

In `KhataDatabase.kt`: add `CategoryBudgetEntity::class` to `entities`, `version = 5` becomes `version = 6`, add `abstract fun categoryBudgetDao(): CategoryBudgetDao`, plus the two imports.

In `Migrations.kt`, above `MIGRATION_4_5`:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category_budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                "`limitMinor` INTEGER NOT NULL, `effectiveFrom` INTEGER NOT NULL, " +
                "`effectiveTo` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_budgets_uuid` ON `category_budgets` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_budgets_categoryId` ON `category_budgets` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_budgets_effectiveFrom` ON `category_budgets` (`effectiveFrom`)")
    }
}
```

In `DatabaseModule.kt`: import `MIGRATION_5_6` and `CategoryBudgetDao`, extend the list to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)`, and add:

```kotlin
    @Provides fun provideCategoryBudgetDao(db: KhataDatabase): CategoryBudgetDao = db.categoryBudgetDao()
```

**Also extend `Migration1To2Test`**: it migrates a v1 database all the way up, so its raw-helper `Callback(5)` becomes `Callback(6)` with `MIGRATION_5_6.migrate(db)` appended, and its Room builder gains `MIGRATION_5_6`. The same applies to `Migration4To5Test`'s Room builder. Bumping the version without this breaks both.

- [ ] **Step 4: Write the migration test**

Copy `Migration4To5Test.kt` and change four things: `TEST_DB` to `"migration-5-6-test.db"`, the class name, `createV4Database`/`schemaJson(4)`/`Callback(4)`/`db.version = 4` to their `5` equivalents, and the Room builder's migrations to `MIGRATION_5_6`. The body becomes:

```kotlin
    @Test
    fun `the budgets table arrives and Room accepts the result`() = runTest {
        createV5Database().use { }

        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        db.categoryBudgetDao().upsert(
            CategoryBudgetEntity(
                uuid = "b-1", categoryId = 1, limitMinor = 500_00,
                effectiveFrom = 1_000, effectiveTo = null, createdAt = 1, updatedAt = 1,
            ),
        )

        assertEquals(1, db.categoryBudgetDao().allFor(categoryId = 1).size)
        db.close()
    }
```

- [ ] **Step 5: Build and run the migration tests**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest --tests "*Migration*"`
Expected: BUILD SUCCESSFUL, all three migration tests pass. A schema-mismatch error means the migration SQL disagrees with the entity — compare against `app/schemas/com.wasif.khata.core.data.KhataDatabase/6.json`, which the build regenerates.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/ app/schemas \
        app/src/test/java/com/wasif/khata/core/data/migration/
git commit -m "feat(data): a table for what each category was allowed to cost"
```

---

### Task 2: The budget rules

Spec §1, §2. The only place the "which row applies" rule lives, and the piece that would produce plausible-but-wrong answers if it were wrong.

**Files:**
- Create: `core/data/repository/BudgetRepository.kt`
- Test: `test/.../repository/BudgetRepositoryTest.kt`

**Interfaces:**
- Consumes: `CategoryBudgetDao` (Task 1), `KhataClock.now()`, `dhakaMonthStart()`, `dhakaNextMonthStart()`.
- Produces: `BudgetRepository` with `suspend fun setLimit(categoryId: Long, limitMinor: Long?)`, `suspend fun limitFor(categoryId: Long, monthStart: Long): Long?`, `fun observeLimitsForMonth(monthStart: Long): Flow<Map<Long, Long>>`. Tasks 3, 5 consume these.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BudgetRepositoryTest {

    private lateinit var db: KhataDatabase

    private val march = Instant.parse("2026-03-14T06:00:00Z").toEpochMilli()
    private val september = Instant.parse("2026-09-14T06:00:00Z").toEpochMilli()

    private var nowMillis = march
    private val clock = object : KhataClock { override fun now(): Long = nowMillis }

    private fun repository() = BudgetRepository(db.categoryBudgetDao(), clock)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a limit set mid-month applies to the whole of that month`() = runTest {
        nowMillis = march
        repository().setLimit(categoryId = 1, limitMinor = 500_00)

        // "My March budget" means March, not the fortnight after the 14th.
        assertEquals(500_00L, repository().limitFor(1, march.dhakaMonthStart()))
    }

    @Test
    fun `changing a limit later leaves the earlier month reading its own number`() = runTest {
        nowMillis = march
        repository().setLimit(categoryId = 1, limitMinor = 500_00)

        nowMillis = september
        repository().setLimit(categoryId = 1, limitMinor = 300_00)

        // This is the whole reason the table is versioned: March must not become a
        // month you overspent because September is stricter.
        assertEquals(500_00L, repository().limitFor(1, march.dhakaMonthStart()))
        assertEquals(300_00L, repository().limitFor(1, september.dhakaMonthStart()))
    }

    @Test
    fun `revising within the same month replaces rather than stacks`() = runTest {
        nowMillis = march
        val repo = repository()
        repo.setLimit(categoryId = 1, limitMinor = 500_00)
        repo.setLimit(categoryId = 1, limitMinor = 650_00)

        assertEquals(650_00L, repo.limitFor(1, march.dhakaMonthStart()))
        // One row, not two overlapping ones -- "which applies" must stay a lookup.
        assertEquals(1, db.categoryBudgetDao().allFor(1).size)
    }

    @Test
    fun `an unset category has no limit, which is not a limit of zero`() = runTest {
        // Zero would read as "you may spend nothing" and show every category as
        // over budget the moment it is used.
        assertNull(repository().limitFor(categoryId = 99, march.dhakaMonthStart()))
    }

    @Test
    fun `clearing a limit closes it without erasing what it was`() = runTest {
        nowMillis = march
        repository().setLimit(categoryId = 1, limitMinor = 500_00)

        nowMillis = september
        repository().setLimit(categoryId = 1, limitMinor = null)

        assertNull(repository().limitFor(1, september.dhakaMonthStart()))
        // March still knows what it was judged against.
        assertEquals(500_00L, repository().limitFor(1, march.dhakaMonthStart()))
    }

    @Test
    fun `the month's limits come back keyed by category`() = runTest {
        nowMillis = march
        val repo = repository()
        repo.setLimit(categoryId = 1, limitMinor = 500_00)
        repo.setLimit(categoryId = 2, limitMinor = 250_00)

        val limits = repo.observeLimitsForMonth(march.dhakaMonthStart()).first()

        assertEquals(mapOf(1L to 500_00L, 2L to 250_00L), limits)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*BudgetRepositoryTest*"`
Expected: FAIL — "Unresolved reference 'BudgetRepository'".

- [ ] **Step 3: Write the repository**

```kotlin
package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.CategoryBudgetDao
import com.wasif.khata.core.data.entity.CategoryBudgetEntity
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Which limit applied to a month, and how a change is recorded.
 *
 * One place, because the rule is easy to get quietly wrong: a reader that decided
 * for itself which of two overlapping rows applied could decide differently from
 * the writer, and both answers would look reasonable.
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val budgets: CategoryBudgetDao,
    private val clock: KhataClock,
) {
    /**
     * Sets this month's limit, or clears it when [limitMinor] is null.
     *
     * A row is keyed to the month it starts, so revising within the same month
     * updates that row rather than opening a second one that overlaps it. A change
     * in a later month closes the old row at that month's start, leaving the
     * earlier months reading exactly what they were judged against.
     */
    suspend fun setLimit(categoryId: Long, limitMinor: Long?) {
        val monthStart = clock.now().dhakaMonthStart()
        val now = clock.now()
        val current = budgets.rowFor(categoryId, monthStart)

        if (current != null && current.effectiveFrom == monthStart) {
            // Started this month: this is a revision of the same period.
            if (limitMinor == null) {
                budgets.upsert(current.copy(deletedAt = now, updatedAt = now))
            } else {
                budgets.upsert(current.copy(limitMinor = limitMinor, updatedAt = now))
            }
            return
        }

        // Carried in from an earlier month: close it here rather than edit it, or
        // that earlier month loses the number it was judged against.
        if (current != null) {
            budgets.upsert(current.copy(effectiveTo = monthStart, updatedAt = now))
        }
        if (limitMinor == null) return

        budgets.upsert(
            CategoryBudgetEntity(
                uuid = UUID.randomUUID().toString(),
                categoryId = categoryId,
                limitMinor = limitMinor,
                effectiveFrom = monthStart,
                effectiveTo = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun limitFor(categoryId: Long, monthStart: Long): Long? =
        budgets.rowFor(categoryId, monthStart)?.limitMinor

    fun observeLimitsForMonth(monthStart: Long): Flow<Map<Long, Long>> =
        budgets.observeActiveAt(monthStart).map { rows ->
            rows.associate { it.categoryId to it.limitMinor }
        }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*BudgetRepositoryTest*"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/repository/BudgetRepository.kt \
        app/src/test/java/com/wasif/khata/core/data/repository/BudgetRepositoryTest.kt
git commit -m "feat: limits that remember what a finished month was judged against"
```

---

### Task 3: Retire the global preference

Spec §1. Two numbers that can disagree about the same month become one.

**Files:**
- Modify: `KhataPreferences.kt`, `PreferencesRepository.kt`, `PreferencesRepositoryImpl.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `ModulesViewModel.kt`, `ModulesUiState.kt`
- Test: `ModulesViewModelTest.kt`, `PreferencesRepositoryTest.kt`

**Interfaces:**
- Consumes: `BudgetRepository.observeLimitsForMonth` (Task 2).
- Produces: `ModulesUiState.budgetFraction` unchanged in meaning — still `Float?`, still null when nothing is set.

- [ ] **Step 1: Move the stored value into a row**

Add `MIGRATION_5_6`-adjacent one-time carry-over. Rather than a database migration reading DataStore — which it cannot — do this in `KhataApplication`'s existing application scope, once:

```kotlin
        // The global monthly budget becomes a row against Uncategorized so nothing
        // the user set is silently dropped. Guarded by the preference still having a
        // value: once carried it is cleared, so this runs at most once.
        applicationScope.launch { budgetCarryOver.runIfNeeded() }
```

Create `core/data/repository/BudgetCarryOver.kt`:

```kotlin
package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.prefs.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Carries the retired global monthly budget into a category row, once.
 *
 * Without this, upgrading silently discards a number the user set and the hub ring
 * disappears with no explanation.
 */
@Singleton
class BudgetCarryOver @Inject constructor(
    private val preferences: PreferencesRepository,
    private val budgets: BudgetRepository,
    private val categories: CategoryDao,
) {
    suspend fun runIfNeeded() {
        val legacy = preferences.preferences.first().monthlyBudgetMinor ?: return
        // Seeded categories are identified by uuid, not a slug column -- the slug
        // exists only in DefaultData and never reaches the table.
        val uncategorized = categories.observeAll().first()
            .firstOrNull { it.uuid == "seed-cat-uncategorized" } ?: return
        budgets.setLimit(uncategorized.id, legacy)
        preferences.setMonthlyBudget(null)
    }
}
```

- [ ] **Step 2: Remove the preference from the interface, not from storage**

`BudgetCarryOver` reads the preference and then clears it, so the field and its setter both have to survive. What goes is the *user-facing* half:

- `SettingsScreen.kt`: delete the `SectionLabel("Monthly budget")` block and the `MonthlyBudgetField` composable.
- `SettingsViewModel.kt`: delete `onMonthlyBudgetChanged`, and the `onMonthlyBudgetChanged = viewModel::onMonthlyBudgetChanged` argument at the `SettingsContent` call site.

Leave `monthlyBudgetMinor` and `setMonthlyBudget` in place, with a comment on the field saying why:

```kotlin
    /**
     * Retired as a user-facing setting; budgets are per category now. It survives
     * only so BudgetCarryOver can find a value set before the change and move it into
     * a row. Nothing else reads it, and nothing writes it but that carry-over
     * clearing itself.
     */
    val monthlyBudgetMinor: Long?,
```

Keeping them costs two dead-ish declarations and avoids editing four anonymous `PreferencesRepository` implementations in tests to delete a method the carry-over still needs. When the carry-over is eventually dropped, both go together.

- [ ] **Step 3: Rewire the hub ring**

`ModulesViewModel` currently divides `spend` by `prefs.monthlyBudgetMinor`. It now sums the month's limits:

```kotlin
    val state: StateFlow<ModulesUiState> = combine(
        transactions.observeSpentBetween(now.dhakaMonthStart(), now.dhakaNextMonthStart()),
        transactions.observeMostRecent(),
        budgets.observeLimitsForMonth(now.dhakaMonthStart()),
    ) { spend, last, limits ->
        val total = limits.values.sum().takeIf { limits.isNotEmpty() }
        ModulesUiState(
            monthSpend = spend,
            budgetFraction = when {
                // Absent, not zero: no limits set means no ring, exactly as before.
                total == null -> null
                // Any spend against a zero total is over it. Dividing would produce
                // infinity and the ring would refuse to draw.
                total <= 0L -> 1f
                // Clamped, because overspending is real and must read as a full ring
                // rather than 150% of a circle.
                else -> (spend.minor.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            },
            lastTransaction = last,
        )
    }
```

Update `ModulesViewModelTest`'s construction and its budget assertions to set limits through `BudgetRepository` rather than through the preference.

- [ ] **Step 4: Build and run the whole suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: one budget, per category, and the ring sums it"
```

---

### Task 4: The insight queries

Spec §4. Two queries, both over data that already exists.

**Files:**
- Modify: `core/data/dao/TransactionDao.kt`
- Test: `test/.../core/data/InsightsQueriesTest.kt`

**Interfaces:**
- Produces: `data class CategoryComparisonRow(val categoryId: Long?, val thisMonthMinor: Long, val lastMonthMinor: Long)` and `data class MerchantTotalRow(val merchantName: String, val totalMinor: Long)`; `fun compareCategorySpend(thisFrom: Long, thisTo: Long, lastFrom: Long, lastTo: Long): Flow<List<CategoryComparisonRow>>` and `fun observeTopMerchants(fromInclusive: Long, toExclusive: Long, limit: Int): Flow<List<MerchantTotalRow>>`. Task 5 consumes both.

- [ ] **Step 1: Write the failing test**

Follow `TransactionDaoTest`'s existing `insertAccount()` and `transaction()` helpers — it already builds rows with a `categoryId` parameter added in an earlier plan.

```kotlin
    @Test
    fun `a category present in only one month still appears, with zero for the other`() = runTest {
        val accountId = insertAccount()
        val thisMonth = Instant.parse("2026-09-10T06:00:00Z").toEpochMilli()
        val lastMonth = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()
        val dao = db.transactionDao()

        dao.upsert(transaction(accountId, thisMonth, "t-new", amountMinor = 500, categoryId = 1))
        dao.upsert(transaction(accountId, lastMonth, "t-gone", amountMinor = 900, categoryId = 2))
        dao.upsert(transaction(accountId, thisMonth, "t-both-a", amountMinor = 100, categoryId = 3))
        dao.upsert(transaction(accountId, lastMonth, "t-both-b", amountMinor = 300, categoryId = 3))

        val rows = dao.compareCategorySpend(
            thisFrom = thisMonth.dhakaMonthStart(),
            thisTo = thisMonth.dhakaNextMonthStart(),
            lastFrom = lastMonth.dhakaMonthStart(),
            lastTo = lastMonth.dhakaNextMonthStart(),
        ).first().associateBy { it.categoryId }

        // A category that appeared and one that vanished are both real answers; two
        // separate queries subtracted in the UI would drop whichever month lacked it.
        assertEquals(500L to 0L, rows[1]!!.let { it.thisMonthMinor to it.lastMonthMinor })
        assertEquals(0L to 900L, rows[2]!!.let { it.thisMonthMinor to it.lastMonthMinor })
        assertEquals(100L to 300L, rows[3]!!.let { it.thisMonthMinor to it.lastMonthMinor })
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*TransactionDaoTest*"`
Expected: FAIL — "Unresolved reference 'compareCategorySpend'".

- [ ] **Step 3: Write the queries**

In `TransactionDao.kt`, using the same spending predicate `observeSpendByCategory` uses:

```kotlin
    // Both months in one pass. Two queries subtracted in the UI would silently drop a
    // category that exists in only one of them, which is exactly the interesting case.
    @Query(
        """
        SELECT categoryId,
               COALESCE(SUM(CASE WHEN occurredAt >= :thisFrom AND occurredAt < :thisTo
                                 THEN amountMinor ELSE 0 END), 0) AS thisMonthMinor,
               COALESCE(SUM(CASE WHEN occurredAt >= :lastFrom AND occurredAt < :lastTo
                                 THEN amountMinor ELSE 0 END), 0) AS lastMonthMinor
        FROM transactions
        WHERE deletedAt IS NULL AND direction = 'DEBIT' AND transferGroupId IS NULL
          AND kind NOT IN ('TRANSFER', 'ADJUSTMENT', 'LENT', 'BORROWED_RETURNED', 'LOAN_REPAYMENT', 'COVERED_FOR_SOMEONE')
          AND occurredAt >= :lastFrom AND occurredAt < :thisTo
        GROUP BY categoryId
        """,
    )
    fun compareCategorySpend(
        thisFrom: Long,
        thisTo: Long,
        lastFrom: Long,
        lastTo: Long,
    ): Flow<List<CategoryComparisonRow>>

    @Query(
        """
        SELECT COALESCE(m.canonicalName, t.merchantRaw) AS merchantName,
               SUM(t.amountMinor) AS totalMinor
        FROM transactions t
        LEFT JOIN merchants m ON m.id = t.merchantId
        WHERE t.deletedAt IS NULL AND t.direction = 'DEBIT' AND t.transferGroupId IS NULL
          AND t.kind NOT IN ('TRANSFER', 'ADJUSTMENT', 'LENT', 'BORROWED_RETURNED', 'LOAN_REPAYMENT', 'COVERED_FOR_SOMEONE')
          AND t.occurredAt >= :fromInclusive AND t.occurredAt < :toExclusive
          AND COALESCE(m.canonicalName, t.merchantRaw) IS NOT NULL
        GROUP BY merchantName
        ORDER BY totalMinor DESC
        LIMIT :limit
        """,
    )
    fun observeTopMerchants(fromInclusive: Long, toExclusive: Long, limit: Int): Flow<List<MerchantTotalRow>>
```

and beside the other row classes:

```kotlin
data class CategoryComparisonRow(
    val categoryId: Long?,
    val thisMonthMinor: Long,
    val lastMonthMinor: Long,
)

data class MerchantTotalRow(val merchantName: String, val totalMinor: Long)
```

- [ ] **Step 4: Add the merchant test and run both**

```kotlin
    @Test
    fun `top merchants are ordered by spend and cut to the limit`() = runTest {
        val accountId = insertAccount()
        val at = Instant.parse("2026-09-10T06:00:00Z").toEpochMilli()
        val dao = db.transactionDao()

        dao.upsert(transaction(accountId, at, "m-1", amountMinor = 100, merchantRaw = "SMALL"))
        dao.upsert(transaction(accountId, at, "m-2", amountMinor = 900, merchantRaw = "BIG"))
        dao.upsert(transaction(accountId, at, "m-3", amountMinor = 400, merchantRaw = "MID"))

        val top = dao.observeTopMerchants(
            at.dhakaMonthStart(), at.dhakaNextMonthStart(), limit = 2,
        ).first()

        assertEquals(listOf("BIG", "MID"), top.map { it.merchantName })
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests "*TransactionDaoTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt \
        app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt
git commit -m "feat(data): this month against last, and where the money went"
```

---

### Task 5: The Insights ViewModel

Spec §4. The month arithmetic, including the first-month case.

**Files:**
- Create: `feature/insights/InsightsUiState.kt`, `feature/insights/InsightsViewModel.kt`
- Test: `test/.../feature/insights/InsightsViewModelTest.kt`

**Interfaces:**
- Consumes: Task 4's queries, `BudgetRepository.observeLimitsForMonth` (Task 2), `ReferenceDataRepository.observeCategories()`.
- Produces: `InsightsUiState` with `monthSpend`, `lastMonthSpend`, `categories: List<CategoryInsight>`, `merchants: List<MerchantSpend>`; `CategoryInsight(name, colorToken, amount, lastAmount, limit)` with derived getters `changeFraction: Float?`, `budgetFraction: Float?`, `isOverBudget: Boolean`. Task 6 renders it.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `a first month has no comparison rather than a hundred percent rise`() {
        // Subtracting from zero yields +100%, which reads as a fact about spending
        // instead of an absence of data.
        val insight = CategoryInsight(
            name = "Groceries", colorToken = "category_green",
            amount = Money(500_00), lastAmount = Money.ZERO, limit = null,
        )

        assertNull(insight.changeFraction)
    }

    @Test
    fun `a category over its limit says so, and the bar does not exceed full`() {
        val insight = CategoryInsight(
            name = "Eating Out", colorToken = "category_orange",
            amount = Money(750_00), lastAmount = Money(500_00), limit = Money(500_00),
        )

        assertTrue(insight.isOverBudget)
        assertEquals(1f, insight.budgetFraction!!, 0.001f)
    }

    @Test
    fun `a category with no limit has no budget bar at all`() {
        val insight = CategoryInsight(
            name = "Fuel", colorToken = "category_slate",
            amount = Money(100_00), lastAmount = Money.ZERO, limit = null,
        )

        assertNull(insight.budgetFraction)
        assertTrue(!insight.isOverBudget)
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*InsightsViewModelTest*"`
Expected: FAIL — "Unresolved reference 'CategoryInsight'".

- [ ] **Step 3: Write the state**

```kotlin
package com.wasif.khata.feature.insights

import com.wasif.khata.core.model.Money

data class CategoryInsight(
    val name: String,
    val colorToken: String,
    val amount: Money,
    val lastAmount: Money,
    val limit: Money?,
) {
    /**
     * Null when there is nothing to compare against. A rise from zero is +100% by
     * arithmetic and nonsense by meaning -- it says something about missing data,
     * not about spending.
     */
    val changeFraction: Float?
        get() = if (lastAmount.isZero) null
        else (amount.minor - lastAmount.minor).toFloat() / lastAmount.minor.toFloat()

    /** Null when no limit is set: absent is not a limit of zero. */
    val budgetFraction: Float?
        get() = limit?.let {
            if (it.isZero) 1f else (amount.minor.toFloat() / it.minor.toFloat()).coerceIn(0f, 1f)
        }

    val isOverBudget: Boolean get() = limit != null && amount > limit
}

data class MerchantSpend(val name: String, val amount: Money)

data class InsightsUiState(
    val monthSpend: Money = Money.ZERO,
    val lastMonthSpend: Money = Money.ZERO,
    val categories: List<CategoryInsight> = emptyList(),
    val merchants: List<MerchantSpend> = emptyList(),
) {
    val changeFraction: Float?
        get() = if (lastMonthSpend.isZero) null
        else (monthSpend.minor - lastMonthSpend.minor).toFloat() / lastMonthSpend.minor.toFloat()

    val spendsMore: Boolean get() = monthSpend > lastMonthSpend
}
```

- [ ] **Step 4: Write the ViewModel**

```kotlin
package com.wasif.khata.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.repository.BudgetRepository
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.domain.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val TOP_MERCHANTS = 5

@HiltViewModel
class InsightsViewModel @Inject constructor(
    transactionDao: TransactionDao,
    reference: ReferenceDataRepository,
    budgets: BudgetRepository,
    clock: KhataClock,
) : ViewModel() {

    private val now = clock.now()
    private val thisFrom = now.dhakaMonthStart()
    private val thisTo = now.dhakaNextMonthStart()
    // One month back is the previous month's start, which dhakaMonthStart gives from
    // any instant inside it -- a millisecond before this month began.
    private val lastFrom = (thisFrom - 1).dhakaMonthStart()

    val state: StateFlow<InsightsUiState> = combine(
        transactionDao.compareCategorySpend(thisFrom, thisTo, lastFrom, thisFrom),
        reference.observeCategories(),
        budgets.observeLimitsForMonth(thisFrom),
        transactionDao.observeTopMerchants(thisFrom, thisTo, TOP_MERCHANTS),
    ) { comparison, categories, limits, merchants ->
        val names = categories.associateBy { it.id }
        InsightsUiState(
            monthSpend = Money(comparison.sumOf { it.thisMonthMinor }),
            lastMonthSpend = Money(comparison.sumOf { it.lastMonthMinor }),
            categories = comparison
                .sortedByDescending { it.thisMonthMinor }
                .map { row ->
                    val category = row.categoryId?.let(names::get)
                    CategoryInsight(
                        name = category?.name ?: "Uncategorised",
                        colorToken = category?.colorToken ?: "category_neutral",
                        amount = Money(row.thisMonthMinor),
                        lastAmount = Money(row.lastMonthMinor),
                        limit = row.categoryId?.let { limits[it] }?.let(::Money),
                    )
                },
            merchants = merchants.map { MerchantSpend(it.merchantName, Money(it.totalMinor)) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InsightsUiState(),
    )
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*InsightsViewModelTest*"`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/insights/ \
        app/src/test/java/com/wasif/khata/feature/insights/
git commit -m "feat(insights): this month against last, without inventing a comparison"
```

---

### Task 6: The Insights screen

Spec §4, §5. Three sections, no new chart.

**Files:**
- Create: `feature/insights/InsightsScreen.kt`
- Modify: `navigation/KhataNavHost.kt`, `feature/wallet/WalletScreen.kt`

**Interfaces:**
- Consumes: `InsightsUiState` (Task 5), `FieldScaffold`, `CollapsingTopBar`, `SectionLabel`, `MoneyText`, `CategoryDot`.
- Produces: `InsightsScreen(onBack: () -> Unit)` and `KhataRoutes.Insights`.

- [ ] **Step 1: Write the screen**

Same shape as `OwedScreen` — `FieldScaffold`, `CollapsingTopBar`, a scrolling `Column` — so read that file for the exact scaffold imports and reuse them verbatim rather than inventing a second arrangement.

```kotlin
package com.wasif.khata.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.LocalSpacing
import kotlin.math.roundToInt

/** "up 24% on last month", or the honest absence of a comparison. */
private fun changeInWords(fraction: Float?, spendsMore: Boolean): String = when {
    fraction == null -> "No comparison yet"
    else -> {
        val percent = (kotlin.math.abs(fraction) * 100).roundToInt()
        if (spendsMore) "Up $percent% on last month" else "Down $percent% on last month"
    }
}

@Composable
fun InsightsSections(state: InsightsUiState, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.lg)) {

        Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
            SectionLabel("This month")
            MoneyText(money = state.monthSpend, style = AmountTextStyle)
            Text(
                text = changeInWords(state.changeFraction, state.spendsMore),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column {
            SectionLabel("By category")
            state.categories.forEach { CategoryRow(it) }
        }

        if (state.merchants.isNotEmpty()) {
            Column {
                SectionLabel("Top merchants")
                state.merchants.forEach { merchant ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(merchant.name, style = MaterialTheme.typography.bodyLarge)
                        MoneyText(money = merchant.amount, direction = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(insight: CategoryInsight) {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(insight.name, style = MaterialTheme.typography.bodyLarge)
                // The word, not just a colour: DESIGN.md 1.3 forbids colour as the
                // only signal, and "over" is the whole point of setting a limit.
                if (insight.isOverBudget) {
                    Text(
                        text = "  over",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            MoneyText(money = insight.amount, direction = null)
        }

        // Against the limit where one is set, and against nothing where none is --
        // an absent limit draws no bar rather than an empty one.
        insight.budgetFraction?.let { fraction ->
            Box(
                Modifier
                    .padding(top = spacing.xs)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (insight.isOverBudget) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }

        insight.changeFraction?.let { change ->
            Text(
                text = changeInWords(change, insight.amount > insight.lastAmount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}
```

Add `androidx.compose.ui.unit.dp` to the imports for the `4.dp` bar height.

Then wrap it in the screen itself, copying `OwedScreen`'s `FieldScaffold` + `CollapsingTopBar` + `verticalScroll` structure exactly, with the heading "Insights", a subline of the month name, and `InsightsSections(state)` as the body. `InsightsScreen(onBack:)` reads its state from `hiltViewModel<InsightsViewModel>()` and `collectAsStateWithLifecycle()`, exactly as `OwedScreen` does.

- [ ] **Step 2: Add the route and the link**

In `KhataRoutes`, add `const val Insights = "insights"`. In `KhataNavHost`, add a `composable(KhataRoutes.Insights)` rendering `InsightsScreen(onBack = { navController.popBackStack() })`, following the `Owed` entry exactly.

In `WalletScreen`, `WalletLinks` gains a third link. It currently takes `onOpenOwed` and `onOpenLedger`; add `onOpenInsights` and a third `Text` reading `"Insights →"`, before Owed. Thread the callback through `WalletScreen`/`WalletContent` the way the other two are threaded, and pass it from the nav host.

- [ ] **Step 3: Build and run the whole suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Verify on hardware**

1. Open Wallet; the Insights link is there beside Owed and Ledger.
2. Open Insights. The month total shows, with "no comparison yet" on a first month.
3. Set a limit for a category, return to Insights: its bar is against the limit, and exceeding it shows the word "over".
4. The hub ring reflects the sum of limits, and disappears when every limit is cleared.
5. Change a limit, then check a previous month still reads its old number — via `adb shell "run-as com.wasif.khata sqlite3 databases/khata.db 'SELECT categoryId, limitMinor, effectiveFrom, effectiveTo FROM category_budgets;'"`, which should show one closed row and one open.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/insights/InsightsScreen.kt \
        app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt \
        app/src/main/java/com/wasif/khata/feature/wallet/WalletScreen.kt
git commit -m "feat(insights): a screen for the sitting-down review"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` passes, including `BudgetRepositoryTest` and the new DAO cases.
- The hardware walkthrough in Task 6 passes, including that a changed limit leaves the earlier month's number intact.
- `monthlyBudgetMinor` no longer has a setter or a Settings field, and the hub ring sums category limits.
- A first month shows no comparison rather than +100%.

## Deferred

Budget notifications, rollover, and budgets on anything but categories. All recorded in spec §7, along with the shared spine, AI, and backup.
