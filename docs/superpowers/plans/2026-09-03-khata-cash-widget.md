# Khata Plan — Cash Widget and Quick Entry

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A home-screen widget with `−` and `+` targets that opens a quick-entry sheet already knowing the direction, so a cash spend is recorded in three taps past the digits.

**Architecture:** The widget face is `RemoteViews` — no Glance, no new dependency — and follows the runtime tuner because `composeScheme(spec)` is a plain function the provider can call. Tapping a target launches a transparent Hilt activity hosting Compose, which gets the petrol system unchanged. The keypad lands in `core/ui/component` so the editor can adopt it later. Six tasks: three independent leaves (`source` on the draft, the keypad, the recency query), then the ViewModel, the sheet, and the widget.

**Tech Stack:** Kotlin · Jetpack Compose · Material 3 · Room · Hilt · RemoteViews / `AppWidgetProvider` · DataStore · JUnit4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-03-khata-cash-widget-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`, `compileSdk` / `targetSdk 37`, package `com.wasif.khata`. Target device: Pixel 6a, Android 17.
- Money is always `Long` **paisa**. Never `Double`, never `Float`, anywhere.
- **No colour literal outside `core/ui/theme`** — `DESIGN.md` §1.1, enforced by a test. This includes layout XML: `res/layout/widget_cash.xml` carries no colour, every fill and text colour is pushed by the provider at runtime.
- **Colour is never the only signal** (`DESIGN.md` §1.3). The widget's two targets each carry a word, not just a glyph.
- Amounts render through `MoneyText` or a style carrying `fontFeatureSettings = "tnum"`.
- Touch targets ≥ `spacing.minTouchTarget` (48.dp). Routine controls sit in the bottom third — the keypad satisfies this by construction.
- Derived UI values are getters on `UiState`, never constructor parameters.
- Durable state (an error still true after rotation) lives in `UiState`; one-shot imperatives go through an effects `Channel(BUFFERED).receiveAsFlow()`.
- Instants are UTC epoch millis. Tests never hardcode a magic epoch-millis literal — build them with `Instant.parse("…Z").toEpochMilli()`.
- Comments carry a non-obvious *why*, never a restatement of *what*. No KDoc restating a name.
- **Ponytail is in force.** Reuse before writing: `Pill` is the category chip, `Money.parse` is the only parser, `String.dropLast(1)` is backspace. No new dependency is added by this plan.
- Run unit tests with: `./gradlew :app:testDebugUnitTest`
- Build with: `./gradlew :app:assembleDebug`
- `export JAVA_HOME="/e/Android/Android Studio/jbr"` and `export PATH="$JAVA_HOME/bin:$PATH"` per shell — `gradle.properties` alone does not bootstrap `gradlew`.

## File Structure

**Create:**
- `app/src/main/java/com/wasif/khata/core/ui/component/AmountKeypad.kt` — `appendAmountKey` (the grammar) and the `AmountKeypad` composable. In `core/ui` because the editor adopts it later.
- `app/src/main/java/com/wasif/khata/feature/widget/QuickEntryUiState.kt` — state, effects, actions.
- `app/src/main/java/com/wasif/khata/feature/widget/QuickEntryViewModel.kt`
- `app/src/main/java/com/wasif/khata/feature/widget/QuickEntryScreen.kt` — the sheet composable.
- `app/src/main/java/com/wasif/khata/feature/widget/QuickEntryActivity.kt`
- `app/src/main/java/com/wasif/khata/feature/widget/CashWidgetProvider.kt` — provider, view building, and the intent factory.
- `app/src/main/res/layout/widget_cash.xml`, `app/src/main/res/drawable/widget_surface.xml`, `app/src/main/res/xml/cash_widget_info.xml`
- Tests: `app/src/test/java/com/wasif/khata/core/ui/component/AmountKeypadTest.kt`, `app/src/test/java/com/wasif/khata/feature/widget/QuickEntryViewModelTest.kt`, `app/src/test/java/com/wasif/khata/feature/widget/CashWidgetIntentTest.kt`

**Modify:**
- `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt` — `TransactionDraft` gains `source`.
- `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt:118` — honour it.
- `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt` — recency query.
- `app/src/main/java/com/wasif/khata/KhataApplication.kt` — push the widget on theme change.
- `app/src/main/res/values/themes.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/AndroidManifest.xml`
- `PRODUCT.md` — the stack line names Glance and is now wrong.

---

### Task 1: A widget entry is distinguishable from a hand-typed one

`save()` writes `TransactionSource.MANUAL` for every new row, so `TransactionSource.WIDGET` has been an unused enum constant since Plan 1. Spec §8.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt:118`
- Test: `app/src/test/java/com/wasif/khata/core/data/repository/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `TransactionDraft(..., source: TransactionSource = TransactionSource.MANUAL)`. Task 4 sets `source = TransactionSource.WIDGET`.

- [ ] **Step 1: Write the failing tests**

Append to `TransactionRepositoryImplTest`. The file already has `account()`, `draft(accountId, amount, direction)` and `parsedRow(...)` helpers — use them.

```kotlin
@Test
fun `a draft carrying a source records it`() = runTest {
    val accountId = account()

    repository.save(
        draft(accountId, Money(5_000), TransactionDirection.DEBIT)
            .copy(source = TransactionSource.WIDGET),
    )

    val saved = db.transactionDao().allActive().single()
    assertEquals(TransactionSource.WIDGET, saved.source)
}

@Test
fun `editing a parsed row does not relabel it as hand-entered`() = runTest {
    val accountId = account()
    val id = parsedRow(accountId = accountId)

    // The editor's own drafts carry the default, MANUAL. An edit must not
    // rewrite where the row came from -- the ledger's confidence markers and
    // the unmatched screen both read source.
    repository.save(draftFor(id = id, accountId = accountId, categoryId = null))

    assertEquals(TransactionSource.SMS, db.transactionDao().allActive().single().source)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*TransactionRepositoryImplTest*"`
Expected: FAIL — `copy(source = …)` does not compile, "No parameter with name 'source' found".

- [ ] **Step 3: Add the field**

In `TransactionRepository.kt`, add to `TransactionDraft` after `kind`:

```kotlin
    /**
     * Defaulted for the same reason `kind` is: every existing call site is an
     * ordinary hand-entered row and should not have to say so. The widget is the
     * first caller that is something else.
     */
    val source: TransactionSource = TransactionSource.MANUAL,
```

Add the import `com.wasif.khata.core.model.TransactionSource`.

- [ ] **Step 4: Honour it for new rows only**

In `TransactionRepositoryImpl.kt:118`, replace:

```kotlin
                    source = existing?.source ?: TransactionSource.MANUAL,
```

with:

```kotlin
                    source = existing?.source ?: draft.source,
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*TransactionRepositoryImplTest*"`
Expected: PASS, and no other test in the file breaks — the default keeps every existing call site identical.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt \
        app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt \
        app/src/test/java/com/wasif/khata/core/data/repository/TransactionRepositoryImplTest.kt
git commit -m "feat: a draft can say where it came from"
```

---

### Task 2: The amount keypad

Spec §6. The grammar is a pure function so it is testable without a Compose runtime; the composable is a dumb grid over it.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/ui/component/AmountKeypad.kt`
- Test: `app/src/test/java/com/wasif/khata/core/ui/component/AmountKeypadTest.kt`

**Interfaces:**
- Consumes: `LocalSpacing`, `MaterialTheme.colorScheme`, `AmountTextStyle`.
- Produces: `fun appendAmountKey(current: String, key: Char): String` and `@Composable fun AmountKeypad(onKey: (Char) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier)`. Task 5 calls both.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wasif.khata.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountKeypadTest {

    @Test
    fun `a leading zero is replaced rather than built on`() {
        assertEquals("5", appendAmountKey("0", '5'))
        assertEquals("0", appendAmountKey("0", '0'))
        assertEquals("100", appendAmountKey("10", '0'))
    }

    @Test
    fun `a dot on an empty field reads as a number while being typed`() {
        assertEquals("0.", appendAmountKey("", '.'))
        assertEquals("12.", appendAmountKey("12", '.'))
    }

    @Test
    fun `a second dot is ignored`() {
        assertEquals("12.5", appendAmountKey("12.5", '.'))
    }

    @Test
    fun `a third decimal has nowhere to go`() {
        assertEquals("0.5", appendAmountKey("0.", '5'))
        assertEquals("0.56", appendAmountKey("0.5", '6'))
        assertEquals("0.56", appendAmountKey("0.56", '7'))
    }

    @Test
    fun `whole taka are not limited by the decimal rule`() {
        assertEquals("123456", appendAmountKey("12345", '6'))
    }

    // Backspace is String.dropLast(1) and gets no function of its own. This pins
    // the empty case the spec calls out, so a later "improvement" that replaces
    // dropLast with index arithmetic cannot reintroduce the crash.
    @Test
    fun `backspace on an empty field is a no-op`() {
        assertEquals("", "".dropLast(1))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*AmountKeypadTest*"`
Expected: FAIL — "Unresolved reference: appendAmountKey".

- [ ] **Step 3: Write the grammar**

Create `AmountKeypad.kt` with:

```kotlin
package com.wasif.khata.core.ui.component

/**
 * The amount field's whole grammar, as a function over the string rather than
 * state inside the composable -- these are the rules worth testing, and a
 * Compose runtime is not needed to test a string.
 *
 * [key] is '0'..'9' or '.'. Backspace is `dropLast(1)`, which is already correct
 * on an empty string.
 */
fun appendAmountKey(current: String, key: Char): String {
    if (key == '.') {
        if (current.contains('.')) return current
        return if (current.isEmpty()) "0." else "$current."
    }
    val dot = current.indexOf('.')
    // Money is Long paisa. A third decimal place has nowhere to be stored.
    if (dot >= 0 && current.length - dot > 2) return current
    // "0" then "5" is 5, not 05. Only a bare zero is replaced -- "10" then "0"
    // is 100.
    if (current == "0") return key.toString()
    return current + key
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*AmountKeypadTest*"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Add the composable**

Append to the same file. A `Column` of `Row`s, not a `LazyVerticalGrid` — twelve fixed keys are not a list.

```kotlin
private val KEY_ROWS = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
    listOf('.', '0', BACKSPACE),
)

internal const val BACKSPACE = '⌫'

@Composable
fun AmountKeypad(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        KEY_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { if (key == BACKSPACE) onBackspace() else onKey(key) }
                            .semantics {
                                contentDescription =
                                    if (key == BACKSPACE) "Backspace" else key.toString()
                            }
                            .padding(vertical = spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key.toString(),
                            style = AmountTextStyle.copy(fontSize = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
```

Imports: `androidx.compose.foundation.background`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.*`, `androidx.compose.material3.MaterialTheme`, `androidx.compose.material3.Text`, `androidx.compose.runtime.Composable`, `androidx.compose.ui.Alignment`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.unit.sp`, `com.wasif.khata.core.ui.theme.AmountTextStyle`, `com.wasif.khata.core.ui.theme.LocalSpacing`.

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/component/AmountKeypad.kt \
        app/src/test/java/com/wasif/khata/core/ui/component/AmountKeypadTest.kt
git commit -m "feat(ui): a decimal keypad, and the rules for what it may type"
```

---

### Task 3: Categories in the order they were last used

Spec §7. Recency, not frequency — a single `MAX(occurredAt)`.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `fun observeRecentCategoryIds(accountId: Long, limit: Int): Flow<List<Long>>`. Task 4 consumes it.

- [ ] **Step 1: Write the failing test**

Append to `TransactionDaoTest`. The file already has `insertAccount()` and inserts `TransactionEntity` directly; follow its existing insertion style and build instants with `Instant.parse`.

```kotlin
@Test
fun `recent categories come back most recently used first`() = runTest {
    val accountId = insertAccount()
    val other = db.accountDao().upsert(
        AccountEntity(
            uuid = "acc-cash",
            name = "Cash",
            type = AccountType.CASH,
            openingBalanceMinor = 0,
            currentBalanceMinor = 0,
            reportedBalanceMinor = null,
            reportedBalanceAt = null,
            includeInNetWorth = true,
            smsIdentifiers = "",
            createdAt = 1000,
            updatedAt = 1000,
        ),
    )
    val older = Instant.parse("2026-08-01T10:00:00Z").toEpochMilli()
    val newer = Instant.parse("2026-09-01T10:00:00Z").toEpochMilli()

    insertTransaction(accountId = other, categoryId = 7L, occurredAt = older)
    insertTransaction(accountId = other, categoryId = 9L, occurredAt = newer)
    // Same category, wrong account: the cash sheet orders by cash habits.
    insertTransaction(accountId = accountId, categoryId = 3L, occurredAt = newer)

    val recent = db.transactionDao().observeRecentCategoryIds(other, limit = 10).first()

    assertEquals(listOf(9L, 7L), recent)
}
```

Add this helper alongside the file's existing ones if it does not already have an equivalent. Every field below is required — `TransactionEntity` defaults only `id`, `counterparty`, `providerTxnId`, `kind` and `deletedAt`:

```kotlin
private suspend fun insertTransaction(
    accountId: Long,
    categoryId: Long?,
    occurredAt: Long,
): Long = db.transactionDao().upsert(
    TransactionEntity(
        uuid = "txn-$occurredAt-$categoryId",
        accountId = accountId,
        amountMinor = 1_000,
        direction = TransactionDirection.DEBIT,
        occurredAt = occurredAt,
        merchantRaw = null,
        merchantId = null,
        categoryId = categoryId,
        note = null,
        source = TransactionSource.MANUAL,
        confidence = Confidence.HIGH,
        rawMessageId = null,
        transferGroupId = null,
        feeMinor = null,
        referenceNumber = null,
        createdAt = occurredAt,
        updatedAt = occurredAt,
    ),
)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*TransactionDaoTest*"`
Expected: FAIL — "Unresolved reference: observeRecentCategoryIds".

- [ ] **Step 3: Add the query**

In `TransactionDao.kt`, next to `observeSpendByCategory`:

```kotlin
    // ponytail: recency only. The spec asks for "recent and frequent"; for a
    // handful of repeated cash categories both orderings converge, and this is one
    // MAX() instead of a weighting nobody can tune. Add COUNT(*) as a tiebreaker
    // if the order ever reads wrong.
    @Query(
        """
        SELECT categoryId FROM transactions
        WHERE deletedAt IS NULL AND accountId = :accountId AND categoryId IS NOT NULL
        GROUP BY categoryId
        ORDER BY MAX(occurredAt) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentCategoryIds(accountId: Long, limit: Int): Flow<List<Long>>
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*TransactionDaoTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt \
        app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt
git commit -m "feat: the categories you last spent cash on, first"
```

---

### Task 4: The quick-entry ViewModel

Spec §1, §8, §9. Direction arrives from the intent and is never editable here.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/widget/QuickEntryUiState.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/widget/QuickEntryViewModel.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/widget/QuickEntryViewModelTest.kt`

**Interfaces:**
- Consumes: `TransactionDraft.source` (Task 1), `TransactionDao.observeRecentCategoryIds` (Task 3).
- Produces: `QuickEntryUiState`, `QuickEntryEffect.Saved`, `QuickEntryActions`, and `QuickEntryViewModel` with `state: StateFlow<QuickEntryUiState>` and `effects: Flow<QuickEntryEffect>`. Also `const val EXTRA_DIRECTION = "direction"`, which Task 6's intent factory writes and this reads. Task 5 renders it.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.wasif.khata.feature.widget

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.repository.TransactionDraft
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuickEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-09-03T09:00:00Z").toEpochMilli()
    private val clock = object : KhataClock { override fun now(): Long = now }

    private val cash = Account(
        id = 4L,
        uuid = "acc-cash",
        name = "Cash",
        type = AccountType.CASH,
        currentBalance = Money.ZERO,
        reportedBalance = null,
        includeInNetWorth = true,
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        direction: String = "DEBIT",
        transactions: FakeTransactionRepository = FakeTransactionRepository(),
    ) = QuickEntryViewModel(
        savedState = SavedStateHandle(mapOf(EXTRA_DIRECTION to direction)),
        transactions = transactions,
        reference = FakeReferenceDataRepository(accounts = listOf(cash)),
        recentCategoryIds = { _, _ -> kotlinx.coroutines.flow.flowOf(emptyList()) },
        clock = clock,
    )

    @Test
    fun `the direction comes from the intent, not from a control`() = runTest {
        assertEquals(TransactionDirection.CREDIT, viewModel(direction = "CREDIT").state.value.direction)
        assertEquals(TransactionDirection.DEBIT, viewModel(direction = "DEBIT").state.value.direction)
    }

    @Test
    fun `a saved entry is written to cash, as a widget entry, and closes the sheet`() = runTest {
        val transactions = FakeTransactionRepository()
        val vm = viewModel(transactions = transactions)
        vm.onAmountChange("50")
        vm.onCategorySelected(9L)

        vm.effects.test {
            vm.onSave()
            advanceUntilIdle()
            assertEquals(QuickEntryEffect.Saved, awaitItem())
        }

        val draft = transactions.saved.single()
        assertEquals(TransactionSource.WIDGET, draft.source)
        assertEquals(4L, draft.accountId)
        assertEquals(Money(5_000), draft.amount)
        assertEquals(TransactionDirection.DEBIT, draft.direction)
        assertEquals(9L, draft.categoryId)
        assertEquals(now, draft.occurredAt)
    }

    @Test
    fun `a failed save keeps the sheet open with the amount intact`() = runTest {
        val transactions = FakeTransactionRepository(failure = IllegalStateException("disk"))
        val vm = viewModel(transactions = transactions)
        vm.onAmountChange("50")

        vm.effects.test {
            vm.onSave()
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals("50", vm.state.value.amountInput)
        assertTrue(vm.state.value.saveError != null)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `an empty or zero amount cannot be saved`() {
        val vm = viewModel()
        assertTrue(!vm.state.value.canSave)
        vm.onAmountChange("0")
        assertTrue(!vm.state.value.canSave)
        vm.onAmountChange("0.01")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `a note is optional and travels with the draft`() = runTest {
        val transactions = FakeTransactionRepository()
        val vm = viewModel(transactions = transactions)
        vm.onAmountChange("50")
        assertNull(vm.state.value.noteInput.ifBlank { null })
        vm.onNoteChange("rickshaw to office")
        vm.onSave()
        advanceUntilIdle()
        assertEquals("rickshaw to office", transactions.saved.single().note)
    }
}
```

Add the two fakes at the bottom of the same file. Only `save` and `observeAccounts` are ever called; the rest exist to satisfy the interfaces, and `TODO()` is the honest body — a fake that silently returns empty would let a future test pass while asserting nothing.

```kotlin
private class FakeTransactionRepository(
    private val failure: Throwable? = null,
) : TransactionRepository {
    val saved = mutableListOf<TransactionDraft>()

    override suspend fun save(draft: TransactionDraft): Result<Long> {
        failure?.let { return Result.failure(DataError.Unknown(it)) }
        saved += draft
        return Result.success(saved.size.toLong())
    }

    override fun pagedTransactionsBetween(fromInclusive: Long, toExclusive: Long) = TODO()
    override fun pagedTransactions(query: String) = TODO()
    override fun observe(id: Long) = TODO()
    override suspend fun recordUnexplained(draft: TransactionDraft) = TODO()
    override suspend fun resetToZero(accountId: Long, at: Long) = TODO()
    override suspend fun delete(id: Long) = TODO()
    override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long) = TODO()
    override fun observeMostRecent() = TODO()
    override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long) = TODO()
    override fun pagedNeedsAttention() = TODO()
    override fun observeNeedsAttentionCount() = TODO()
    override fun observeDayTotals() = TODO()
}

private class FakeReferenceDataRepository(
    private val accounts: List<Account>,
) : ReferenceDataRepository {
    override fun observeAccounts() = flowOf(accounts)
    override fun observeCategories() = flowOf(emptyList<Category>())
    override fun observeNetWorth() = flowOf(Money.ZERO)
}
```

Additional imports for these: `com.wasif.khata.domain.error.DataError`, `com.wasif.khata.domain.model.Category`, `com.wasif.khata.domain.repository.ReferenceDataRepository`, `com.wasif.khata.domain.repository.TransactionRepository`, `kotlinx.coroutines.flow.flowOf`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*QuickEntryViewModelTest*"`
Expected: FAIL — "Unresolved reference: QuickEntryViewModel".

- [ ] **Step 3: Write the state**

`QuickEntryUiState.kt`:

```kotlin
package com.wasif.khata.feature.widget

import androidx.compose.runtime.Stable
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.domain.model.Category

const val EXTRA_DIRECTION = "direction"

data class QuickEntryUiState(
    val amountInput: String = "",
    val noteInput: String = "",
    val categoryId: Long? = null,
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    val amount: Money? get() = if (amountInput.isBlank()) null else Money.parse(amountInput)

    val canSave: Boolean get() = amount.let { it != null && !it.isZero } && !isSaving

    /** The word on the sheet, matching the word on the widget target that opened it. */
    val heading: String
        get() = if (direction == TransactionDirection.DEBIT) "Cash spent" else "Cash received"
}

sealed interface QuickEntryEffect {
    data object Saved : QuickEntryEffect
}

@Stable
interface QuickEntryActions {
    fun onAmountKey(key: Char)
    fun onBackspace()
    fun onAmountChange(value: String)
    fun onNoteChange(value: String)
    fun onCategorySelected(id: Long?)
    fun onSave()
}
```

- [ ] **Step 4: Write the ViewModel**

```kotlin
package com.wasif.khata.feature.widget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.ui.component.appendAmountKey
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val reference: ReferenceDataRepository,
    private val recentCategoryIds: RecentCategoryIds,
    private val clock: KhataClock,
) : ViewModel(), QuickEntryActions {

    private val inputs = MutableStateFlow(
        QuickEntryUiState(
            direction = savedState.get<String>(EXTRA_DIRECTION)
                ?.let(TransactionDirection::valueOf)
                ?: TransactionDirection.DEBIT,
        ),
    )

    private val effectChannel = Channel<QuickEntryEffect>(Channel.BUFFERED)
    val effects: Flow<QuickEntryEffect> = effectChannel.receiveAsFlow()

    private val cashAccount = reference.observeAccounts()
        .map { accounts -> accounts.firstOrNull { it.type == AccountType.CASH } }

    private val orderedCategories = cashAccount.flatMapLatest { account ->
        if (account == null) {
            reference.observeCategories()
        } else {
            combine(
                reference.observeCategories(),
                recentCategoryIds(account.id, RECENT_LIMIT),
            ) { categories, recent ->
                val rank = recent.withIndex().associate { (index, id) -> id to index }
                // sortedBy is stable, so everything unranked keeps its seeded order
                // rather than being shuffled by an arbitrary tiebreak.
                categories.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            }
        }
    }

    val state: StateFlow<QuickEntryUiState> =
        combine(inputs, orderedCategories) { input, categories ->
            input.copy(categories = categories)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = inputs.value,
        )

    override fun onAmountKey(key: Char) =
        inputs.update { it.copy(amountInput = appendAmountKey(it.amountInput, key)) }

    override fun onBackspace() =
        inputs.update { it.copy(amountInput = it.amountInput.dropLast(1)) }

    override fun onAmountChange(value: String) = inputs.update { it.copy(amountInput = value) }

    override fun onNoteChange(value: String) = inputs.update { it.copy(noteInput = value) }

    override fun onCategorySelected(id: Long?) = inputs.update {
        it.copy(categoryId = if (it.categoryId == id) null else id)
    }

    override fun onSave() {
        val amount = inputs.value.amount ?: return
        if (!inputs.value.canSave) return
        inputs.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            // Read once at save time rather than holding the id in state: the
            // account list is a Room flow that is already warm, and a second copy
            // of the id is a second thing that can be stale.
            val account = reference.observeAccounts().first()
                .firstOrNull { it.type == AccountType.CASH }
            if (account == null) {
                inputs.update { it.copy(isSaving = false, saveError = "No cash account") }
                return@launch
            }

            val current = inputs.value
            transactions.save(
                TransactionDraft(
                    id = null,
                    accountId = account.id,
                    amount = amount,
                    direction = current.direction,
                    occurredAt = clock.now(),
                    merchantRaw = null,
                    categoryId = current.categoryId,
                    note = current.noteInput.ifBlank { null },
                    source = TransactionSource.WIDGET,
                ),
            ).onSuccess {
                inputs.update { it.copy(isSaving = false) }
                effectChannel.send(QuickEntryEffect.Saved)
            }.onFailure { error ->
                // Durable, not a toast: the sheet must not close on a failed write.
                // The user is standing in a shop and the record is the whole point.
                inputs.update { it.copy(isSaving = false, saveError = "Could not save. Try again.") }
            }
        }
    }

    private companion object {
        const val RECENT_LIMIT = 8
    }
}

/**
 * The one DAO query the sheet needs, as a function type. Injecting the whole
 * `TransactionDao` here would put a Room type in a ViewModel that otherwise talks
 * only to repositories, and would make the test build a database for one list.
 */
fun interface RecentCategoryIds {
    operator fun invoke(accountId: Long, limit: Int): Flow<List<Long>>
}
```

Bind it in `app/src/main/java/com/wasif/khata/core/data/di/RepositoryModule.kt`. That module is an `abstract class` of `@Binds`, so a `@Provides` has to go in a `companion object` — add one:

```kotlin
    companion object {
        @Provides
        fun provideRecentCategoryIds(dao: TransactionDao) =
            RecentCategoryIds { accountId, limit -> dao.observeRecentCategoryIds(accountId, limit) }
    }
```

Imports to add there: `com.wasif.khata.core.data.dao.TransactionDao`, `com.wasif.khata.feature.widget.RecentCategoryIds`, `dagger.Provides`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*QuickEntryViewModelTest*"`
Expected: PASS, 5 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/widget/ \
        app/src/main/java/com/wasif/khata/core/data/di/RepositoryModule.kt \
        app/src/test/java/com/wasif/khata/feature/widget/QuickEntryViewModelTest.kt
git commit -m "feat(widget): the quick-entry view model, direction fixed by the intent"
```

---

### Task 5: The sheet

Spec §4, §5. A transparent activity hosting Compose, where focus decides the lower half.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/widget/QuickEntryScreen.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/widget/QuickEntryActivity.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AmountKeypad`, `appendAmountKey` (Task 2), `QuickEntryViewModel`, `QuickEntryUiState`, `QuickEntryActions`, `QuickEntryEffect` (Task 4), `Pill` from `core/ui/component/Controls.kt`.
- Produces: `@Composable fun QuickEntryScreen(state, actions, saved: Boolean, onClosed: () -> Unit, modifier)` and `QuickEntryActivity`, which Task 6's intent factory targets.

- [ ] **Step 1: Add the transparent theme**

In `app/src/main/res/values/themes.xml`, after `Theme.Khata`:

```xml
    <!--
      The sheet draws its own scrim in Compose so it can animate on LocalMotion
      and take the tuner's colours; the platform dim would be a second, fixed
      one underneath it. windowBackground is transparent rather than the ground,
      which is also why this window needs no splash: rendering nothing until
      DataStore arrives is invisible here.
    -->
    <style name="Theme.Khata.QuickEntry" parent="Theme.Khata">
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:backgroundDimEnabled">false</item>
        <item name="android:windowAnimationStyle">@null</item>
    </style>
```

- [ ] **Step 2: Write the sheet**

`QuickEntryScreen.kt`. A bottom-anchored column over a scrim; the lower half is the keypad while the amount has focus and the IME otherwise.

The screen owns its own enter and exit, because the window carries no activity transition (spec §4) and a sheet that slides in and then vanishes on a frame reads as a crash. It reports back through `onClosed` only once the exit has actually finished. Durations come from `LocalMotion`, which already collapses to nothing when the system's "Remove animations" is on — so honouring that setting costs no extra branch.

```kotlin
@Composable
fun QuickEntryScreen(
    state: QuickEntryUiState,
    actions: QuickEntryActions,
    saved: Boolean,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val motion = LocalMotion.current
    var noteFocused by remember { mutableStateOf(false) }
    val visible = remember { MutableTransitionState(false) }
    var closing by remember { mutableStateOf(false) }

    // Enter on first composition; leave when saved or dismissed. onClosed waits
    // for the transition to idle, or the window vanishes mid-slide. `closing`
    // gates the last effect: without it the initial state is already idle and
    // invisible, and the sheet would close itself on the first frame.
    LaunchedEffect(Unit) { visible.targetState = true }
    LaunchedEffect(saved) { if (saved) closing = true }
    LaunchedEffect(closing) { if (closing) visible.targetState = false }
    LaunchedEffect(closing, visible.isIdle) {
        if (closing && visible.isIdle && !visible.currentState) onClosed()
    }

    // Back takes the same path as the scrim rather than finish()ing straight out,
    // or the two ways of dismissing the same sheet look different.
    BackHandler { closing = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { closing = true },
            ),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(motion.quick, easing = motion.enter)),
            exit = fadeOut(tween(motion.quick, easing = motion.exit)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
            )
        }

        AnimatedVisibility(
            visibleState = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(motion.standard, easing = motion.enter)) { it },
            exit = slideOutVertically(tween(motion.standard, easing = motion.exit)) { it },
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                // Consumes the tap so a press inside the sheet does not dismiss it.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .padding(spacing.md)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(text = state.heading, style = PageSublineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(
                text = state.amountInput.ifEmpty { "0" },
                style = AmountTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            state.saveError?.let { error ->
                Text(text = error, style = PageSublineStyle, color = MaterialTheme.colorScheme.error)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                state.categories.forEach { category ->
                    Pill(
                        text = category.name,
                        selected = state.categoryId == category.id,
                        onClick = { actions.onCategorySelected(category.id) },
                    )
                }
            }

            OutlinedTextField(
                value = state.noteInput,
                onValueChange = actions::onNoteChange,
                label = { Text("Note") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { noteFocused = it.isFocused },
            )

            Pill(
                text = "Save",
                selected = true,
                enabled = state.canSave,
                onClick = actions::onSave,
                modifier = Modifier.fillMaxWidth(),
            )

            // The lower half belongs to whichever field has focus. The keypad
            // withdraws for the IME rather than stacking above it.
            if (!noteFocused) {
                AmountKeypad(onKey = actions::onAmountKey, onBackspace = actions::onBackspace)
            }
        }
        }
    }
}
```

`Pill`'s signature is `Pill(text, selected, modifier, enabled, contentDescription, onClick)` — pass `modifier`, `enabled` and `onClick` by name as above, since `onClick` is the trailing parameter and the ones before it are being skipped.

Imports: `androidx.compose.animation.AnimatedVisibility`, `androidx.compose.animation.core.MutableTransitionState`, `androidx.compose.animation.core.tween`, `androidx.compose.animation.fadeIn`, `androidx.compose.animation.fadeOut`, `androidx.compose.animation.slideInVertically`, `androidx.compose.animation.slideOutVertically`, `androidx.compose.foundation.layout.FlowRow`, `androidx.compose.foundation.layout.imePadding`, `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.material3.OutlinedTextField`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.setValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.ui.focus.onFocusChanged`, `androidx.activity.compose.BackHandler`, `com.wasif.khata.core.ui.component.AmountKeypad`, `com.wasif.khata.core.ui.component.Pill`, `com.wasif.khata.core.ui.theme.AmountTextStyle`, `com.wasif.khata.core.ui.theme.LocalMotion`, `com.wasif.khata.core.ui.theme.LocalSpacing`, `com.wasif.khata.core.ui.theme.PageSublineStyle`.

- [ ] **Step 3: Write the activity**

```kotlin
@AndroidEntryPoint
class QuickEntryActivity : ComponentActivity() {

    private val viewModel: QuickEntryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            // No splash gate, unlike MainActivity: this window is transparent, so
            // one frame of nothing is invisible rather than a flash of the wrong
            // palette.
            val main: MainViewModel = hiltViewModel()
            val prefs = main.state.collectAsStateWithLifecycle().value
            if (prefs !is MainUiState.Ready) return@setContent

            KhataTheme(spec = prefs.preferences.themeSpec) {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                // Saved is durable here rather than a direct finish(), so the sheet
                // can play its exit before the window goes.
                var saved by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            QuickEntryEffect.Saved -> saved = true
                        }
                    }
                }
                QuickEntryScreen(
                    state = state,
                    actions = viewModel,
                    saved = saved,
                    onClosed = ::finish,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Register it**

In `AndroidManifest.xml`, inside `<application>`, after `MainActivity`:

```xml
        <!--
          Not exported: the only caller is our own widget's PendingIntent.
          excludeFromRecents so a five-second cash entry does not become a card in
          the recents list. adjustResize for the same reason MainActivity needs it
          -- without it the window pans and the amount leaves the screen when the
          IME opens for a note.
        -->
        <activity
            android:name=".feature.widget.QuickEntryActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:launchMode="singleTop"
            android:theme="@style/Theme.Khata.QuickEntry"
            android:windowSoftInputMode="adjustResize" />
```

- [ ] **Step 5: Build and check the existing suite still passes**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/widget/QuickEntryScreen.kt \
        app/src/main/java/com/wasif/khata/feature/widget/QuickEntryActivity.kt \
        app/src/main/res/values/themes.xml app/src/main/AndroidManifest.xml
git commit -m "feat(widget): the quick-entry sheet, keypad or keyboard by focus"
```

---

### Task 6: The widget face

Spec §2, §3. Two targets, a word each, colours pushed from the tuner.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/widget/CashWidgetProvider.kt`
- Create: `app/src/main/res/layout/widget_cash.xml`, `app/src/main/res/drawable/widget_surface.xml`, `app/src/main/res/xml/cash_widget_info.xml`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/wasif/khata/KhataApplication.kt`, `PRODUCT.md`
- Test: `app/src/test/java/com/wasif/khata/feature/widget/CashWidgetIntentTest.kt`

**Interfaces:**
- Consumes: `QuickEntryActivity` (Task 5), `EXTRA_DIRECTION` (Task 4), `composeScheme(spec)` and `ThemeSpec` from `core/ui/theme`.
- Produces: `fun quickEntryIntent(context: Context, direction: TransactionDirection): Intent` and `CashWidgetProvider.refresh(context, spec)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wasif.khata.feature.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CashWidgetIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `each target carries its own direction to the sheet`() {
        val spent = quickEntryIntent(context, TransactionDirection.DEBIT)
        val received = quickEntryIntent(context, TransactionDirection.CREDIT)

        assertEquals("DEBIT", spent.getStringExtra(EXTRA_DIRECTION))
        assertEquals("CREDIT", received.getStringExtra(EXTRA_DIRECTION))
        assertEquals(QuickEntryActivity::class.java.name, spent.component?.className)
    }

    // Two PendingIntents that differ only in extras are "the same" to
    // PendingIntent.getActivity unless their request codes differ -- the second
    // silently replaces the first and both buttons record a spend.
    @Test
    fun `the two targets do not collapse into one pending intent`() {
        assertNotEquals(
            requestCodeFor(TransactionDirection.DEBIT),
            requestCodeFor(TransactionDirection.CREDIT),
        )
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CashWidgetIntentTest*"`
Expected: FAIL — "Unresolved reference: quickEntryIntent".

- [ ] **Step 3: Write the provider**

```kotlin
package com.wasif.khata.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.wasif.khata.R
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.ThemeSpec
import com.wasif.khata.core.ui.theme.composeScheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun requestCodeFor(direction: TransactionDirection): Int = direction.ordinal

fun quickEntryIntent(context: Context, direction: TransactionDirection): Intent =
    Intent(context, QuickEntryActivity::class.java)
        .putExtra(EXTRA_DIRECTION, direction.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@AndroidEntryPoint
class CashWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var preferences: PreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // onUpdate runs on the main thread with a short window, and the theme is
        // in DataStore. goAsync() keeps the broadcast alive across the read
        // instead of blocking the thread or painting the default palette.
        val pending = goAsync()
        scope.launch {
            try {
                val spec = preferences.preferences.first().themeSpec
                val views = buildViews(context, spec)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * Pushed by the app when the tuner changes. RemoteViews does not observe
         * DataStore the way Glance would, and the theme can only be changed from
         * Settings, so the app process is always alive when it happens.
         */
        fun refresh(context: Context, spec: ThemeSpec) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CashWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context, spec)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context, spec: ThemeSpec): RemoteViews {
            val scheme = composeScheme(spec)
            return RemoteViews(context.packageName, R.layout.widget_cash).apply {
                // Every colour arrives here. The layout carries none, because a
                // literal would be correct in one of the tuner's 512 combinations.
                setColorStateList(
                    R.id.widget_root,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(scheme.surface.toArgb()),
                )
                listOf(R.id.spent_glyph, R.id.received_glyph).forEach {
                    setTextColor(it, scheme.primary.toArgb())
                }
                listOf(R.id.spent_label, R.id.received_label).forEach {
                    setTextColor(it, scheme.onSurfaceVariant.toArgb())
                }

                setOnClickPendingIntent(
                    R.id.spent,
                    activityIntent(context, TransactionDirection.DEBIT),
                )
                setOnClickPendingIntent(
                    R.id.received,
                    activityIntent(context, TransactionDirection.CREDIT),
                )
            }
        }

        private fun activityIntent(context: Context, direction: TransactionDirection) =
            PendingIntent.getActivity(
                context,
                requestCodeFor(direction),
                quickEntryIntent(context, direction),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*CashWidgetIntentTest*"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Add the layout and metadata**

`res/drawable/widget_surface.xml` — white purely as a tint base, since `setBackgroundTintList` multiplies against it and a rounded shape cannot be produced by `setBackgroundColor`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="@android:dimen/system_app_widget_background_radius" />
    <solid android:color="@android:color/white" />
</shape>
```

`res/layout/widget_cash.xml` — no colour attributes anywhere:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_surface"
    android:orientation="horizontal"
    android:padding="8dp">

    <LinearLayout
        android:id="@+id/spent"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:contentDescription="@string/widget_spent"
        android:gravity="center"
        android:orientation="vertical">

        <TextView
            android:id="@+id/spent_glyph"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/widget_minus"
            android:textSize="28sp" />

        <TextView
            android:id="@+id/spent_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/widget_spent"
            android:textSize="12sp" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/received"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:contentDescription="@string/widget_received"
        android:gravity="center"
        android:orientation="vertical">

        <TextView
            android:id="@+id/received_glyph"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/widget_plus"
            android:textSize="28sp" />

        <TextView
            android:id="@+id/received_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/widget_received"
            android:textSize="12sp" />
    </LinearLayout>
</LinearLayout>
```

`res/xml/cash_widget_info.xml` — both target and min sizes, since a launcher may honour either:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_description"
    android:minWidth="180dp"
    android:minHeight="40dp"
    android:targetCellWidth="2"
    android:targetCellHeight="1"
    android:initialLayout="@layout/widget_cash"
    android:resizeMode="horizontal"
    android:widgetCategory="home_screen" />
```

Add to `res/values/strings.xml`:

```xml
    <string name="widget_spent">Spent</string>
    <string name="widget_received">Received</string>
    <string name="widget_minus">−</string>
    <string name="widget_plus">+</string>
    <string name="widget_description">Record cash in or out</string>
```

Register in `AndroidManifest.xml`, inside `<application>`:

```xml
        <receiver
            android:name=".feature.widget.CashWidgetProvider"
            android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/cash_widget_info" />
        </receiver>
```

- [ ] **Step 6: Push the widget when the tuner changes**

In `KhataApplication.kt`, add `@Inject lateinit var preferences: PreferencesRepository` and, beside the existing seeder launch:

```kotlin
        applicationScope.launch {
            preferences.preferences
                .map { it.themeSpec }
                .distinctUntilChanged()
                .collect { CashWidgetProvider.refresh(this@KhataApplication, it) }
        }
```

Imports: `kotlinx.coroutines.flow.distinctUntilChanged`, `kotlinx.coroutines.flow.map`, `com.wasif.khata.core.prefs.PreferencesRepository`, `com.wasif.khata.feature.widget.CashWidgetProvider`.

- [ ] **Step 7: Correct PRODUCT.md**

In the `## Stack` section, replace `Glance` in the technology list with `RemoteViews`, and add after the paragraph that ends "not delegated.":

```markdown
The home-screen widget is `RemoteViews`, not Glance (2026-09-03). Its face carries no
live data, so Glance would add a dependency and a second theming dialect to
re-render nothing — and it would not have supplied `KhataTheme` either, having its
own `ColorProviders`. The widget still follows the runtime tuner: `composeScheme` is
a plain function the provider calls directly. Rationale in
`docs/superpowers/specs/2026-09-03-khata-cash-widget-design.md` §2.
```

- [ ] **Step 8: Build, test, and verify on hardware**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

Then install and check by hand, because a launcher's rendering cannot be asserted:
1. Add the widget to the home screen. Both targets are visible and legible, each showing a glyph and a word.
2. Tap `−`. The sheet opens over the launcher, keypad live, heading "Cash spent".
3. Type `50`, tap a category, tap Save. The sheet closes; the row appears in the Ledger against Cash, on today.
4. Tap `+`. The heading reads "Cash received" and the saved row is a credit.
5. Tap the note field. The keypad withdraws, the IME appears, the amount stays on screen.
6. Tap outside the sheet. It dismisses with nothing recorded.
7. Change the accent in Settings, then return to the home screen. The widget has followed.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/widget/CashWidgetProvider.kt \
        app/src/main/java/com/wasif/khata/KhataApplication.kt \
        app/src/main/res app/src/main/AndroidManifest.xml PRODUCT.md \
        app/src/test/java/com/wasif/khata/feature/widget/CashWidgetIntentTest.kt
git commit -m "feat(widget): a cash widget that follows the tuner"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` passes, including the four new test classes.
- The hardware walkthrough in Task 6 Step 8 passes end to end.
- No colour literal was added outside `core/ui/theme` — the layout XML included.
- `TransactionSource.WIDGET` is no longer an unused enum constant.
- `PRODUCT.md` no longer claims Glance.

## Deferred

Preset amounts, live figures or recent-spend shortcuts on the widget face (the point
at which Glance is reconsidered), account switching, a merchant field, and editing
from the sheet. All recorded in spec §11.
