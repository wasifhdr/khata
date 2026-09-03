# Khata Plan — Category Management

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add, rename, recolour and delete categories from Settings, with a deleted category still labelling the history it was used for.

**Architecture:** The single `observeAll()` that every consumer shares splits in two — live categories for pickers, all-including-deleted for labelling rows that already exist — because a soft delete that goes unread by the label lookup silently rewrites history as "Uncategorised". `isSystem` narrows by migration from all sixteen seeded rows to Uncategorised alone. A repository owns the delete rule, which also has to close the category's open budget. Four tasks: the data layer and migration, the rules, rewiring the label lookups, then the screen.

**Tech Stack:** Kotlin · Room + KSP · Hilt · Coroutines/Flow · Jetpack Compose · Material 3 · JUnit4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-03-khata-category-management-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`, `compileSdk` / `targetSdk 37`, package `com.wasif.khata`.
- Deletes are soft: `deletedAt` is set, rows are never removed.
- **A deleted category must still label its old transactions.** Any lookup that turns a `categoryId` into a name or colour reads the including-deleted query; any lookup that offers a choice reads the live one.
- **Category colours come from `KhataPalette.categories`** and are never free-form: they encode data, not taste (`DESIGN.md` §3.4), and a free colour could fail the contrast floor on the field.
- **No colour literal outside `core/ui/theme`** (`DESIGN.md` §1.1, test-enforced).
- Money is always `Long` **paisa**. Month boundaries are Dhaka.
- Derived UI values are getters on `UiState`, never constructor parameters.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- Tests never hardcode a magic epoch-millis literal — build them with `Instant.parse("…Z").toEpochMilli()`.
- **Ponytail is in force.** Reuse before writing: `ActionRow` is the Settings row, `SectionLabel`/`CollapsingTopBar`/`FieldScaffold` are how a secondary screen is built (`OwedScreen`), `CategoryDot` already renders a category's colour, `BudgetRepository.setLimit` already closes a budget row.
- Run unit tests with: `./gradlew :app:testDebugUnitTest`
- Build with: `./gradlew :app:assembleDebug`
- `export JAVA_HOME="/e/Android/Android Studio/jbr"` and `export PATH="$JAVA_HOME/bin:$PATH"` per shell.

## File Structure

**Create:**
- `app/src/main/java/com/wasif/khata/core/data/repository/CategoryRepository.kt` — add, rename, recolour, delete, and the rules around them.
- `app/src/main/java/com/wasif/khata/feature/categories/CategoriesUiState.kt`, `CategoriesViewModel.kt`, `CategoriesScreen.kt`
- Tests: `Migration6To7Test.kt`, `CategoryRepositoryTest.kt`, `CategoriesViewModelTest.kt`

**Modify:**
- `dao/CategoryDao.kt` — the second read, plus soft delete.
- `KhataDatabase.kt` (v7), `Migrations.kt`, `DatabaseModule.kt`
- `domain/repository/ReferenceDataRepository.kt`, `repository/ReferenceDataRepositoryImpl.kt` — expose both reads.
- `feature/wallet/WalletViewModel.kt`, `feature/insights/InsightsViewModel.kt`, `feature/ledger/LedgerViewModel.kt` — label lookups move to the including-deleted read.
- `feature/settings/SettingsScreen.kt`, `navigation/KhataNavHost.kt` — the entry and route.

---

### Task 1: Two reads, and a migration that narrows `isSystem`

Spec §1, §2.

**Files:**
- Modify: `dao/CategoryDao.kt`, `KhataDatabase.kt`, `migration/Migrations.kt`, `di/DatabaseModule.kt`, `domain/repository/ReferenceDataRepository.kt`, `repository/ReferenceDataRepositoryImpl.kt`
- Test: `test/.../migration/Migration6To7Test.kt`

**Interfaces:**
- Produces: `CategoryDao.observeAllIncludingDeleted(): Flow<List<CategoryEntity>>`, `CategoryDao.findById(id: Long): CategoryEntity?`, `CategoryDao.softDelete(id: Long, deletedAt: Long)`; `ReferenceDataRepository.observeCategoriesIncludingDeleted(): Flow<List<Category>>`. Tasks 2–4 consume these.

- [ ] **Step 1: Add the second read and a soft delete**

In `CategoryDao.kt`:

```kotlin
    /**
     * Every category, deleted ones included. This is the read for turning a stored
     * categoryId into a name or colour: a transaction filed last March under a
     * category since deleted still says what it said in March. `observeAll` is the
     * read for *offering* a choice, and the two are deliberately different questions.
     */
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAllIncludingDeleted(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    @Query("UPDATE categories SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)
```

In `ReferenceDataRepository.kt` add `fun observeCategoriesIncludingDeleted(): Flow<List<Category>>`, and in `ReferenceDataRepositoryImpl.kt`:

```kotlin
    override fun observeCategoriesIncludingDeleted(): Flow<List<Category>> =
        categoryDao.observeAllIncludingDeleted().map { entities -> entities.map { it.toDomain() } }
```

Four test files implement `ReferenceDataRepository` anonymously — `WalletViewModelTest`, `QuickEntryViewModelTest`, and any others the compiler names. Add the new override to each, returning the same flow their `observeCategories` returns.

- [ ] **Step 2: Bump to v7 and narrow `isSystem`**

In `KhataDatabase.kt`, `version = 6` becomes `version = 7`. In `Migrations.kt`, above `MIGRATION_5_6`:

```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // isSystem was written true on all sixteen seeded categories and read by
        // nothing. It now means "cannot be deleted", which is true of exactly one:
        // Uncategorised is found by uuid in BudgetCarryOver and is the fallback label
        // for a transaction with no category at all.
        db.execSQL("UPDATE categories SET isSystem = 0 WHERE uuid != 'seed-cat-uncategorized'")
    }
}
```

In `DatabaseModule.kt`, import it and extend the list to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)`.

**Also extend the two existing migration tests**: `Migration1To2Test` walks a v1 database all the way up, so its raw-helper `Callback(6)` becomes `Callback(7)` with `MIGRATION_6_7.migrate(db)` appended, and its Room builder gains `MIGRATION_6_7`; `Migration5To6Test`'s Room builder gains it too. Every version bump has broken these; expect it.

Also update `DatabaseSeeder` so newly seeded rows carry the right flag:

```kotlin
                        isSystem = seed.slug == "uncategorized",
```

- [ ] **Step 3: Write the migration test**

Copy `Migration5To6Test.kt`, renaming to `Migration6To7Test`, `TEST_DB` to `"migration-6-7-test.db"`, `createV5Database`/`schemaJson(5)`/`Callback(5)`/`db.version = 5` to their `6` equivalents, and the Room builder's migration to `MIGRATION_6_7`. The body:

```kotlin
    @Test
    fun `only Uncategorised survives as a system category`() = runTest {
        createV6Database().use { db ->
            db.execSQL(
                "INSERT INTO categories (uuid, name, icon, colorToken, parentId, isSystem, " +
                    "createdAt, updatedAt, deletedAt) VALUES " +
                    "('seed-cat-uncategorized','Uncategorized','help_outline','category_neutral'," +
                    "NULL,1,1,1,NULL), " +
                    "('seed-cat-fuel','Fuel','local_gas_station','category_slate',NULL,1,1,1,NULL)",
            )
        }

        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        val byUuid = db.categoryDao().observeAllIncludingDeleted().first().associateBy { it.uuid }
        assertTrue(byUuid.getValue("seed-cat-uncategorized").isSystem)
        // Fuel was seeded system for no reason; a user with no car must be able to
        // delete it.
        assertFalse(byUuid.getValue("seed-cat-fuel").isSystem)
        db.close()
    }
```

- [ ] **Step 4: Build and run the migration tests**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest --tests "*Migration*"`
Expected: BUILD SUCCESSFUL, all four migration tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(data): categories readable after deletion, and only one is permanent"
```

---

### Task 2: The rules

Spec §1, §2, §4. Delete is the only action here that changes how existing history reads, so it is the one with rules.

**Files:**
- Create: `core/data/repository/CategoryRepository.kt`
- Test: `test/.../repository/CategoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `CategoryDao` (Task 1), `BudgetRepository.setLimit` (budgets plan), `KhataClock.now()`.
- Produces: `CategoryRepository` with `suspend fun add(name: String, colorToken: String): Long`, `suspend fun rename(id: Long, name: String)`, `suspend fun recolour(id: Long, colorToken: String)`, `suspend fun delete(id: Long): Boolean`. Task 3 consumes them.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.time.KhataClock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: CategoryRepository

    private val now = Instant.parse("2026-09-14T06:00:00Z").toEpochMilli()
    private val clock = object : KhataClock { override fun now(): Long = now }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = CategoryRepository(
            db.categoryDao(),
            BudgetRepository(db.categoryBudgetDao(), clock),
            clock,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedUncategorised(): Long = db.categoryDao().upsert(
        CategoryEntity(
            uuid = "seed-cat-uncategorized",
            name = "Uncategorized",
            icon = "help_outline",
            colorToken = "category_neutral",
            parentId = null,
            isSystem = true,
            createdAt = 1,
            updatedAt = 1,
        ),
    )

    @Test
    fun `a deleted category still labels the history it was used for`() = runTest {
        val id = repository.add("Car & Maintenance", "category_bronze")

        repository.delete(id)

        // The whole claim of the design: it stops being offered, it does not stop
        // being readable.
        assertNull(db.categoryDao().observeAll().first().firstOrNull { it.id == id })
        assertNotNull(db.categoryDao().observeAllIncludingDeleted().first().firstOrNull { it.id == id })
    }

    @Test
    fun `Uncategorised cannot be deleted`() = runTest {
        val id = seedUncategorised()

        val deleted = repository.delete(id)

        assertFalse(deleted)
        assertNotNull(db.categoryDao().observeAll().first().firstOrNull { it.id == id })
    }

    @Test
    fun `an ordinary seeded category can be deleted`() = runTest {
        val id = db.categoryDao().upsert(
            CategoryEntity(
                uuid = "seed-cat-fuel", name = "Fuel", icon = "local_gas_station",
                colorToken = "category_slate", parentId = null, isSystem = false,
                createdAt = 1, updatedAt = 1,
            ),
        )

        assertTrue(repository.delete(id))
    }

    @Test
    fun `renaming carries to the rows already filed under it`() = runTest {
        val id = repository.add("Eating Out", "category_orange")

        repository.rename(id, "Restaurants")

        // Transactions hold a categoryId, so a rename needs nothing else to follow.
        assertEquals("Restaurants", db.categoryDao().findById(id)!!.name)
    }

    @Test
    fun `deleting a category stops its budget counting forward`() = runTest {
        val id = repository.add("Fuel", "category_slate")
        val budgets = BudgetRepository(db.categoryBudgetDao(), clock)
        budgets.setLimit(id, 500_00)

        repository.delete(id)

        // Left open, it would keep adding to the hub ring's total for a category
        // that is no longer offered.
        assertNull(budgets.limitFor(id, now.dhakaMonthStart()))
    }

    @Test
    fun `a new category is live, named and coloured as asked`() = runTest {
        val id = repository.add("Gym", "category_teal")

        val row = db.categoryDao().findById(id)!!
        assertEquals("Gym", row.name)
        assertEquals("category_teal", row.colorToken)
        assertFalse(row.isSystem)
        assertNull(row.deletedAt)
    }
}
```

Add the import `com.wasif.khata.core.time.dhakaMonthStart` for the budget assertion.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CategoryRepositoryTest*"`
Expected: FAIL — "Unresolved reference 'CategoryRepository'".

- [ ] **Step 3: Write the repository**

```kotlin
package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categories: CategoryDao,
    private val budgets: BudgetRepository,
    private val clock: KhataClock,
) {
    suspend fun add(name: String, colorToken: String): Long {
        val now = clock.now()
        return categories.upsert(
            CategoryEntity(
                uuid = UUID.randomUUID().toString(),
                name = name.trim(),
                // The entity carries an icon and no screen renders one, so a new
                // category gets the same placeholder the seed uses rather than an
                // empty string that would look like a missing value later.
                icon = "help_outline",
                colorToken = colorToken,
                parentId = null,
                isSystem = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun rename(id: Long, name: String) {
        val row = categories.findById(id) ?: return
        categories.upsert(row.copy(name = name.trim(), updatedAt = clock.now()))
    }

    suspend fun recolour(id: Long, colorToken: String) {
        val row = categories.findById(id) ?: return
        categories.upsert(row.copy(colorToken = colorToken, updatedAt = clock.now()))
    }

    /**
     * Soft-deletes, and reports whether it did. False means the category is the one
     * that cannot go: Uncategorised is found by uuid in BudgetCarryOver and is the
     * fallback label for a transaction with no category at all.
     *
     * Its budget is closed at the same time. Left open it would keep adding to the
     * hub ring's total for a category no longer on offer.
     */
    suspend fun delete(id: Long): Boolean {
        val row = categories.findById(id) ?: return false
        if (row.isSystem) return false

        budgets.setLimit(id, null)
        categories.softDelete(id, clock.now())
        return true
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CategoryRepositoryTest*"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/repository/CategoryRepository.kt \
        app/src/test/java/com/wasif/khata/core/data/repository/CategoryRepositoryTest.kt
git commit -m "feat: categories that can be added, renamed and retired"
```

---

### Task 3: Labels read the deleted ones too

Spec §1. Without this, a soft delete rewrites history as "Uncategorised" — the exact outcome the design exists to prevent.

**Files:**
- Modify: `feature/wallet/WalletViewModel.kt`, `feature/insights/InsightsViewModel.kt`, `feature/ledger/LedgerViewModel.kt`
- Test: `test/.../feature/wallet/WalletViewModelTest.kt`

**Interfaces:**
- Consumes: `ReferenceDataRepository.observeCategoriesIncludingDeleted()` (Task 1).

- [ ] **Step 1: Write the failing test**

In `WalletViewModelTest`, whose fake `ReferenceDataRepository` you extended in Task 1. Make its two category flows differ, so reading the wrong one fails:

```kotlin
    @Test
    fun `a deleted category still names its own spending`() = runTest(dispatcher) {
        val accountId = insertAccount("acc-labels", includeInNetWorth = true)
        val at = clock.now()
        db.transactionDao().upsert(
            TransactionEntity(
                uuid = "t-labelled", accountId = accountId, amountMinor = 1_000,
                direction = TransactionDirection.DEBIT, occurredAt = at,
                merchantRaw = null, merchantId = null, categoryId = 7, note = null,
                source = TransactionSource.MANUAL, confidence = Confidence.HIGH,
                rawMessageId = null, transferGroupId = null, feeMinor = null,
                referenceNumber = null, createdAt = at, updatedAt = at,
            ),
        )
        // Live categories no longer include it; the including-deleted list does.
        liveCategories.value = emptyList()
        allCategories.value = listOf(
            Category(
                id = 7, uuid = "c-7", name = "Car & Maintenance",
                colorToken = "category_bronze", parentId = null, isSystem = false,
            ),
        )

        WalletViewModel(
            transactions, reference, db.transactionDao(), db.balanceSnapshotDao(), clock,
        ).state.test {
            advanceUntilIdle()
            // Falling back to "Uncategorised" here is the bug this whole task exists
            // to prevent: the category is gone from the picker, not from the past.
            assertEquals("Car & Maintenance", expectMostRecentItem().categories.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

Give the fake two backing `MutableStateFlow`s — `liveCategories` for `observeCategories()` and `allCategories` for `observeCategoriesIncludingDeleted()` — so the test can make them disagree. Follow the file's existing insertion helpers for the account and transaction.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*WalletViewModelTest*"`
Expected: FAIL — the category reads "Uncategorised".

- [ ] **Step 3: Point the three label lookups at the new read**

In each of `WalletViewModel`, `InsightsViewModel`, `LedgerViewModel`, replace `reference.observeCategories()` with `reference.observeCategoriesIncludingDeleted()`. All three use it only to turn an id into a name and colour.

**Leave `TransactionEditorViewModel` and `QuickEntryViewModel` alone.** They offer a choice, and a deleted category must not be offered. That asymmetry is the design.

Add a comment at each of the three call sites, once, in the form:

```kotlin
        // Including deleted: this maps a stored categoryId to its label, and a
        // category deleted last week still named last March's spending.
        reference.observeCategoriesIncludingDeleted(),
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/ app/src/test/java/com/wasif/khata/feature/
git commit -m "feat: a deleted category still names the spending it was used for"
```

---

### Task 4: The screen

Spec §5.

**Files:**
- Create: `feature/categories/CategoriesUiState.kt`, `CategoriesViewModel.kt`, `CategoriesScreen.kt`
- Modify: `feature/settings/SettingsScreen.kt`, `navigation/KhataNavHost.kt`

**Interfaces:**
- Consumes: `CategoryRepository` (Task 2), `CategoryDao.observeAll()`, `KhataPalette.categories`.
- Produces: `CategoriesScreen(onBack: () -> Unit)` and `KhataRoutes.Categories`.

- [ ] **Step 1: Write the state and ViewModel**

First, add `isSystem` to the domain model so the screen does not have to guess by name. In `domain/model/Category.kt` add `val isSystem: Boolean = false`, and in `Mappers.kt`'s `CategoryEntity.toDomain()` map it across. Comparing names would be a second, weaker copy of a truth the row already carries.

```kotlin
package com.wasif.khata.feature.categories

import com.wasif.khata.domain.model.Category

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    /** The row a delete is being confirmed for; null when nothing is pending. */
    val pendingDelete: Category? = null,
)
```

The ViewModel exposes `state: StateFlow<CategoriesUiState>` from `reference.observeCategories()` (the **live** read — this screen manages what is on offer), and `onAdd(name, colorToken)`, `onRename(id, name)`, `onRecolour(id, token)`, `onDeleteRequested(category)`, `onDeleteConfirmed()`, `onDeleteDismissed()`, each delegating to `CategoryRepository` in `viewModelScope`.

The screen offers a delete where `!category.isSystem`. The repository stays the real guard and returns false regardless — the UI hiding an action and the data layer refusing it are two different jobs, and only the second is a guarantee.

- [ ] **Step 2: Write the screen**

Same scaffold as `OwedScreen` — `FieldScaffold`, `CollapsingTopBar`, scrolling `Column`. Each row shows `CategoryDot(colorToken)`, the name, and a delete affordance where `canDelete`. Tapping a row opens an inline editor for its name and a colour chooser built from `KhataPalette.categories.keys` rendered as `CategoryDot`s — the same fixed set, never a free picker.

An "Add category" `ActionRow` at the top opens the same editor empty.

Delete goes through an `AlertDialog` confirming by name and saying plainly that past transactions keep the category — that sentence is the design's promise and the user should see it before agreeing.

- [ ] **Step 3: Add the route and the Settings entry**

`KhataRoutes` gains `const val Categories = "categories"`, `KhataNavHost` a `composable` rendering `CategoriesScreen(onBack = { navController.popBackStack() })`, following the `Owed` entry.

In `SettingsScreen`, add above the `SectionLabel("Messages")` block:

```kotlin
    SectionLabel("Categories")
    ActionRow(
        title = "Manage categories",
        subtitle = "Add, rename or retire. Past transactions keep whatever they were filed under.",
        onClick = onOpenCategories,
    )
```

Thread `onOpenCategories` through `SettingsScreen`/`SettingsContent` the way the other navigation callbacks are threaded, and pass it from the nav host.

- [ ] **Step 4: Build and run the whole suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Verify on hardware**

1. Settings shows "Manage categories"; opening it lists all sixteen.
2. Add one. It appears here, and in the widget's quick-entry chips.
3. Rename one that has transactions. The Ledger and Insights show the new name.
4. Delete one that has transactions, confirming the dialog. It disappears from the quick-entry chips, and **Insights still shows its old name against that spending** — the point of the whole change.
5. Uncategorised offers no delete.
6. Set a budget on a category, delete it, and check the hub ring's total drops by that limit.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/categories/ \
        app/src/main/java/com/wasif/khata/feature/settings/SettingsScreen.kt \
        app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt
git commit -m "feat(categories): add, rename and retire them from Settings"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` passes, including `CategoryRepositoryTest` and the deleted-category label case.
- The hardware walkthrough passes, especially step 4.
- `isSystem` is true for exactly one row.
- A deleted category appears in no picker and still names its own history.

## Deferred

Merging categories, bulk reassignment, sub-categories, icons and reordering. All recorded in spec §7.
