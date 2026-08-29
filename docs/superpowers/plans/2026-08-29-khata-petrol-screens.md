# Khata Plan C — Petrol Module Screens

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Wallet, Ledger and Editor to the petrol design, and close the loop that lets a module be the app's home.

**Architecture:** Three screens, one shape: a back circle in the corner, a centred heading floating in open space, content anchored to the bottom. The Ledger is the exception the design already accounts for — a list of 3,000 rows has no bottom, so it gets a fixed headspace instead. Aggregates land first because every screen here is defined by the numbers it carries.

**Tech Stack:** Kotlin · Jetpack Compose · Material 3 · Room · Hilt · Paging 3 · DataStore · JUnit4 + Robolectric + Turbine.

**Spec:** `docs/superpowers/specs/2026-08-28-khata-petrol-design.md`
**Depends on:** Plan A (`2026-08-28-khata-petrol-system.md`) and Plan B (`2026-08-29-khata-petrol-shell.md`), both complete.

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`, `compileSdk` / `targetSdk` `37`. Target device: Pixel 6a, Android 17.
- Package and namespace: `com.wasif.khata`.
- **No hardcoded colours.** Every colour resolves through `KhataPalette` or `MaterialTheme.colorScheme`.
- Money is always `Long` **paisa**. Never `Double`, never `Float`.
- All day, month and period boundaries computed in `Asia/Dhaka`.
- **Amounts always use `AmountTextStyle` or a style carrying `fontFeatureSettings = "tnum"`**, and are right-aligned in the ledger column.
- **Category colour is never the sole signal** — the category name or a pattern always accompanies it.
- The ledger is Paging 3 and never loaded whole. **No glass on ledger rows** — one blur pass per row per frame.
- No aggregate-on-scroll queries. Aggregates are separate queries, not per-row work.
- Repositories map platform exceptions to `DataError` at the boundary.
- **The wordmark খাতা appears on the Modules hub and nowhere else.** Module pages carry context, not the app name.
- Ledger order stays **newest-first**.
- Tests never hardcode a magic epoch-millis literal. Build instants with `Instant.parse("…Z").toEpochMilli()`.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- `export JAVA_HOME="/e/Android/Android Studio/jbr"` and `export PATH="$JAVA_HOME/bin:$PATH"` per shell.
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Instrumented: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` — this AGP does **not** accept `--tests`.
- Emulator: `ANDROID_AVD_HOME="D:\android-avd" $ANDROID_HOME/emulator/emulator.exe -avd khata_test -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect`

---

## What exists, so nothing here is re-derived

- `Transaction` carries `categoryId: Long?` and `confidence: Confidence` (`HIGH`/`MEDIUM`/`LOW`), so rows can resolve a dot colour and a low-confidence marker without new columns.
- `Category` carries `colorToken: String`, which keys into `KhataPalette.categories`.
- `Account` carries `currentBalance`, `reportedBalance` and a derived `hasBalanceDrift` — the reconciliation gap the Wallet screen shows is already modelled.
- `ReferenceDataRepository` exposes `observeAccounts()` and `observeCategories()`.
- `TransactionRepository` already has `observeSpentBetween(from, to)` and `observeMostRecent()` from Plan B.
- `LedgerItem` is `Row(transaction)` / `DayHeader(date)`; `DayHeader` gains a total in Task 3.
- `ContextHeader(heading, subline)` and `CategoryDot(token, lowConfidence)` exist from Plan A and are used unchanged.

## Two places this plan knowingly diverges from the spec

Both are recorded rather than left silent, so a later reader does not treat them as oversights.

**The ledger search field is a plain outlined field, not glass.** Spec §8 draws it as glass, but
§5's own placement table marks "search bar over the scrolling ledger" as **Measure** — a small
blurred area over a backdrop that changes every frame is the one case the spec refuses to approve
in advance. Shipping it flat is the conservative reading of the spec's own caution. Turning it to
glass later is a one-line change once someone has measured it on a Pixel 6a.

**The editor uses the system decimal keyboard.** The spec's §8 lists a custom decimal keypad
occupying the lower half as "not drawn yet", and it stays out of scope here. `KeyboardType.Decimal`
gives the right keys today; the custom keypad is a later pass, along with the three-tap widget.

## One design decision this plan makes

**Day totals are computed by a grouped query, not by accumulating during paging.** `insertSeparators` only sees adjacent items, so it cannot sum a day that spans a page boundary — accumulating there would silently under-report the first day of every page.

The query groups on a **Dhaka day index**: `(occurredAt + 21600000) / 86400000`. Dhaka is UTC+6 with no DST, so adding six hours in milliseconds before integer-dividing by a day yields the local calendar day directly, with no timezone function in SQLite. One row per day with any activity — a few hundred rows over years, read once, not per scroll.

---

### Task 1: Net worth, day totals, and month income

Every screen in this plan is defined by a number it carries. They land first, with their own tests, so a reviewer can reject the data shape without rejecting three screens.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/AccountDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`
- Modify: `app/src/main/java/com/wasif/khata/domain/repository/ReferenceDataRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/repository/ReferenceDataRepositoryImpl.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/AccountDaoTest.kt`

**Interfaces:**
- Consumes: `dhakaMonthStart()` / `dhakaNextMonthStart()` from Plan B.
- Produces:
  - `AccountDao.observeNetWorthMinor(): Flow<Long>`
  - `TransactionDao.observeDayTotals(): Flow<List<DayTotalRow>>` where `data class DayTotalRow(val dhakaDayIndex: Long, val spentMinor: Long)`
  - `TransactionRepository.observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money>`
  - `TransactionRepository.observeDayTotals(): Flow<Map<LocalDate, Money>>`
  - `ReferenceDataRepository.observeNetWorth(): Flow<Money>`
  - `fun Long.toDhakaDayIndex(): Long` and `fun Long.dhakaDayIndexToLocalDate(): LocalDate` in `core/time/KhataClock.kt`

- [ ] **Step 1: Write the failing day-index tests**

Append inside the existing class in `app/src/test/java/com/wasif/khata/core/time/KhataClockTest.kt`:

```kotlin
    @Test
    fun `the Dhaka day index rolls at Dhaka midnight, not UTC midnight`() {
        // 23:59 Dhaka on 28 August is 17:59 UTC the same day.
        val lateEvening = Instant.parse("2026-08-28T17:59:00Z").toEpochMilli()
        // 00:01 Dhaka on 29 August is 18:01 UTC on the 28th.
        val justAfterMidnight = Instant.parse("2026-08-28T18:01:00Z").toEpochMilli()

        assertEquals(
            lateEvening.toDhakaDayIndex() + 1,
            justAfterMidnight.toDhakaDayIndex(),
        )
    }

    @Test
    fun `the day index round-trips to the Dhaka calendar date`() {
        val millis = Instant.parse("2026-08-28T18:01:00Z").toEpochMilli()

        assertEquals(
            LocalDate.of(2026, 8, 29),
            millis.toDhakaDayIndex().dhakaDayIndexToLocalDate(),
        )
    }

    @Test
    fun `two instants on the same Dhaka day share an index`() {
        val morning = Instant.parse("2026-08-29T02:00:00Z").toEpochMilli()
        val evening = Instant.parse("2026-08-29T17:00:00Z").toEpochMilli()

        assertEquals(morning.toDhakaDayIndex(), evening.toDhakaDayIndex())
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.time.KhataClockTest"`
Expected: compilation failure — `Unresolved reference: toDhakaDayIndex`.

- [ ] **Step 3: Add the day-index helpers**

Append to `app/src/main/java/com/wasif/khata/core/time/KhataClock.kt`:

```kotlin
/**
 * Dhaka is UTC+6 with no daylight saving, so shifting by six hours before
 * integer-dividing by a day yields the local calendar day directly. SQLite gets
 * the same arithmetic inline, which is why the grouped day-total query needs no
 * timezone function.
 */
const val DHAKA_OFFSET_MILLIS: Long = 6 * 60 * 60 * 1000L
private const val DAY_MILLIS: Long = 24 * 60 * 60 * 1000L

fun Long.toDhakaDayIndex(): Long = (this + DHAKA_OFFSET_MILLIS) / DAY_MILLIS

fun Long.dhakaDayIndexToLocalDate(): LocalDate =
    LocalDate.ofEpochDay(this)
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.time.KhataClockTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing DAO tests**

Append inside the existing class in `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`:

```kotlin
    @Test
    fun `day totals group debits by Dhaka day and ignore credits`() = runTest {
        val accountId = insertAccount()
        val dao = db.transactionDao()
        // Both are 29 August in Dhaka: 18:01 UTC on the 28th is 00:01 on the 29th.
        val justAfterDhakaMidnight = Instant.parse("2026-08-28T18:01:00Z").toEpochMilli()
        val laterSameDhakaDay = Instant.parse("2026-08-29T10:00:00Z").toEpochMilli()
        // 17:59 UTC on the 28th is still 28 August in Dhaka.
        val previousDhakaDay = Instant.parse("2026-08-28T17:59:00Z").toEpochMilli()

        dao.upsert(transaction(accountId, justAfterDhakaMidnight, "a", amountMinor = 100_00))
        dao.upsert(transaction(accountId, laterSameDhakaDay, "b", amountMinor = 250_00))
        dao.upsert(transaction(accountId, previousDhakaDay, "c", amountMinor = 900_00))
        dao.upsert(
            transaction(
                accountId,
                laterSameDhakaDay,
                "d",
                amountMinor = 5_000_00,
                direction = TransactionDirection.CREDIT,
            ),
        )

        val totals = dao.observeDayTotals().first().associate { it.dhakaDayIndex to it.spentMinor }

        assertEquals(100_00L + 250_00L, totals[justAfterDhakaMidnight.toDhakaDayIndex()])
        assertEquals(900_00L, totals[previousDhakaDay.toDhakaDayIndex()])
    }

    @Test
    fun `day totals exclude soft-deleted rows`() = runTest {
        val accountId = insertAccount()
        val dao = db.transactionDao()
        val day = Instant.parse("2026-08-29T10:00:00Z").toEpochMilli()

        dao.upsert(transaction(accountId, day, "keep", amountMinor = 100_00))
        val goneId = dao.upsert(transaction(accountId, day, "gone", amountMinor = 700_00))
        dao.softDelete(goneId, deletedAt = day)

        val totals = dao.observeDayTotals().first().associate { it.dhakaDayIndex to it.spentMinor }

        assertEquals(100_00L, totals[day.toDhakaDayIndex()])
    }
```

Ensure the file imports `com.wasif.khata.core.time.toDhakaDayIndex`.

And append inside the existing class in `app/src/test/java/com/wasif/khata/core/data/AccountDaoTest.kt`:

```kotlin
    @Test
    fun `net worth sums only accounts flagged for inclusion`() = runTest {
        val dao = db.accountDao()
        dao.upsert(account(uuid = "in-1", name = "bKash", balanceMinor = 8_214_30, include = true))
        dao.upsert(account(uuid = "in-2", name = "EBL", balanceMinor = 2_98_606_00, include = true))
        // A credit card or a tracked-but-excluded pot must not inflate net worth.
        dao.upsert(account(uuid = "out", name = "Excluded", balanceMinor = 99_999_00, include = false))

        assertEquals(8_214_30L + 2_98_606_00L, dao.observeNetWorthMinor().first())
    }

    @Test
    fun `net worth is zero rather than null on an empty database`() = runTest {
        // Runs on every first launch, before any account exists.
        assertEquals(0L, db.accountDao().observeNetWorthMinor().first())
    }
```

If `AccountDaoTest` has no `account(...)` helper, add this one above the tests, adapting nothing else:

```kotlin
    private fun account(uuid: String, name: String, balanceMinor: Long, include: Boolean) =
        AccountEntity(
            uuid = uuid,
            name = name,
            type = AccountType.MFS,
            openingBalanceMinor = 0,
            currentBalanceMinor = balanceMinor,
            reportedBalanceMinor = null,
            reportedBalanceAt = null,
            includeInNetWorth = include,
            smsIdentifiers = name,
            createdAt = 1000,
            updatedAt = 1000,
        )
```

- [ ] **Step 6: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.data.TransactionDaoTest" --tests "com.wasif.khata.core.data.AccountDaoTest"`
Expected: compilation failure — `Unresolved reference: observeDayTotals`.

- [ ] **Step 7: Add the queries**

Add to `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`, above the interface declaration:

```kotlin
/** One row per Dhaka day that has any spending. */
data class DayTotalRow(
    val dhakaDayIndex: Long,
    val spentMinor: Long,
)
```

And inside the interface:

```kotlin
    // Grouped rather than accumulated during paging: insertSeparators only sees
    // adjacent items, so a day spanning a page boundary would be under-reported.
    // The +21600000 shifts UTC to Dhaka before the day division, which is exact
    // because Dhaka is UTC+6 year-round.
    @Query(
        """
        SELECT ((occurredAt + 21600000) / 86400000) AS dhakaDayIndex,
               SUM(amountMinor) AS spentMinor
        FROM transactions
        WHERE deletedAt IS NULL AND direction = 'DEBIT'
        GROUP BY dhakaDayIndex
        """,
    )
    fun observeDayTotals(): Flow<List<DayTotalRow>>
```

Add to `app/src/main/java/com/wasif/khata/core/data/dao/AccountDao.kt`:

```kotlin
    @Query(
        """
        SELECT COALESCE(SUM(currentBalanceMinor), 0) FROM accounts
        WHERE deletedAt IS NULL AND includeInNetWorth = 1
        """,
    )
    fun observeNetWorthMinor(): Flow<Long>
```

- [ ] **Step 8: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.data.TransactionDaoTest" --tests "com.wasif.khata.core.data.AccountDaoTest"`
Expected: PASS.

- [ ] **Step 9: Extend the repositories**

Add to the `TransactionRepository` interface:

```kotlin
    /** Credits only, over a half-open window. */
    fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money>

    /** Spending per Dhaka calendar day, for ledger day headers. */
    fun observeDayTotals(): Flow<Map<LocalDate, Money>>
```

Implement in `TransactionRepositoryImpl`:

```kotlin
    override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
        transactionDao.observeTotalMinorBetween(
            direction = TransactionDirection.CREDIT,
            fromInclusive = fromInclusive,
            toExclusive = toExclusive,
        ).map { Money(it) }

    override fun observeDayTotals(): Flow<Map<LocalDate, Money>> =
        transactionDao.observeDayTotals().map { rows ->
            rows.associate { it.dhakaDayIndex.dhakaDayIndexToLocalDate() to Money(it.spentMinor) }
        }
```

Add `java.time.LocalDate` and `com.wasif.khata.core.time.dhakaDayIndexToLocalDate` imports to both files.

Add to the `ReferenceDataRepository` interface:

```kotlin
    fun observeNetWorth(): Flow<Money>
```

And to `ReferenceDataRepositoryImpl`, matching whatever DAO field name that class already holds:

```kotlin
    override fun observeNetWorth(): Flow<Money> =
        accountDao.observeNetWorthMinor().map { Money(it) }
```

- [ ] **Step 10: Update the existing repository fakes**

Adding interface methods breaks every hand-written fake. These four files each need the new overrides — the previous plan's commit was broken by exactly this, so fix them before running the suite:

In `app/src/test/java/com/wasif/khata/feature/ledger/LedgerViewModelTest.kt`, `app/src/test/java/com/wasif/khata/feature/editor/TransactionEditorViewModelTest.kt` and `app/src/test/java/com/wasif/khata/feature/hub/ModulesViewModelTest.kt`, add to each `TransactionRepository` fake:

```kotlin
        override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = flowOf(emptyMap())
```

Add `java.time.LocalDate` to those files' imports.

- [ ] **Step 11: Run the whole suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all green.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/time/KhataClock.kt \
        app/src/main/java/com/wasif/khata/core/data/dao/ \
        app/src/main/java/com/wasif/khata/domain/repository/ \
        app/src/main/java/com/wasif/khata/core/data/repository/ \
        app/src/test/java/com/wasif/khata/
git commit -m "feat: net worth, day totals, and month income

Day totals are grouped in SQL rather than accumulated during paging:
insertSeparators only sees adjacent items, so a day spanning a page
boundary would be silently under-reported.

The grouping key shifts UTC by six hours before dividing by a day, which
is exact because Dhaka is UTC+6 with no daylight saving -- so SQLite
needs no timezone function.

Net worth sums only accounts flagged for inclusion; a credit card must
not inflate it."
```

---

### Task 2: The Wallet dashboard

The module's own home. Net worth on petrol, the month's two figures, and the account list where a reconciliation gap is visible without going looking.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/wallet/WalletUiState.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/wallet/WalletViewModel.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/wallet/WalletScreen.kt`
- Modify: `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/hub/ModulesScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/wallet/WalletViewModelTest.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/wallet/WalletScreenTest.kt`

**Interfaces:**
- Consumes: `ReferenceDataRepository.observeNetWorth()`, `observeAccounts()`; `TransactionRepository.observeSpentBetween`, `observeReceivedBetween`; `ContextHeader` from Plan A.
- Produces:
  - `data class WalletUiState(netWorth: Money, monthSpend: Money, monthReceived: Money, accounts: List<Account>)`
  - `WalletScreen(onBack: () -> Unit, onOpenLedger: () -> Unit)`
  - `KhataRoutes.Wallet = "wallet"`

- [ ] **Step 1: Write the failing ViewModel test**

Create `app/src/test/java/com/wasif/khata/feature/wallet/WalletViewModelTest.kt`:

```kotlin
package com.wasif.khata.feature.wallet

import androidx.paging.PagingData
import app.cash.turbine.test
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val netWorth = MutableStateFlow(Money.ZERO)
    private val accounts = MutableStateFlow(emptyList<Account>())
    private val spend = MutableStateFlow(Money.ZERO)
    private val received = MutableStateFlow(Money.ZERO)

    private val clock = object : KhataClock {
        override fun now(): Long = Instant.parse("2026-08-28T09:41:00Z").toEpochMilli()
    }

    private val transactions = object : TransactionRepository {
        override fun pagedTransactions(): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: TransactionDraft) = Result.success(0L)
        override suspend fun delete(id: Long) = Result.success(Unit)
        override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> = spend
        override fun observeMostRecent(): Flow<Transaction?> = flowOf(null)
        override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> = received
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = flowOf(emptyMap())
    }

    private val reference = object : ReferenceDataRepository {
        override fun observeAccounts(): Flow<List<Account>> = accounts
        override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeNetWorth(): Flow<Money> = netWorth
    }

    private fun account(name: String, balance: Long, reported: Long?) = Account(
        id = name.hashCode().toLong(),
        uuid = name,
        name = name,
        type = AccountType.MFS,
        currentBalance = Money(balance),
        reportedBalance = reported?.let { Money(it) },
        includeInNetWorth = true,
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the month figures come from the Dhaka month window`() = runTest(dispatcher) {
        spend.value = Money(47_382_50)
        received.value = Money(85_000_00)
        netWorth.value = Money(3_14_820_00)

        WalletViewModel(transactions, reference, clock).state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(Money(47_382_50), s.monthSpend)
            assertEquals(Money(85_000_00), s.monthReceived)
            assertEquals(Money(3_14_820_00), s.netWorth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an account whose reported balance disagrees is flagged as drifting`() = runTest(dispatcher) {
        // Product principle #2: the app proves itself by reconciling against
        // reported balances rather than asking the user to notice.
        accounts.value = listOf(
            account("bKash", balance = 8_214_30, reported = 8_214_30),
            account("EBL", balance = 2_98_606_00, reported = 2_98_846_00),
        )

        WalletViewModel(transactions, reference, clock).state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertTrue(s.accounts.single { it.name == "EBL" }.hasBalanceDrift)
            assertTrue(!s.accounts.single { it.name == "bKash" }.hasBalanceDrift)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an account with no reported balance never reads as drifting`() = runTest(dispatcher) {
        // Cash has no upstream to reconcile against. Absence of a reported
        // balance is not a discrepancy.
        accounts.value = listOf(account("Cash", balance = 8_000_00, reported = null))

        WalletViewModel(transactions, reference, clock).state.test {
            advanceUntilIdle()
            assertTrue(!expectMostRecentItem().accounts.single().hasBalanceDrift)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.feature.wallet.WalletViewModelTest"`
Expected: compilation failure — `Unresolved reference: WalletViewModel`.

- [ ] **Step 3: Write the state and ViewModel**

Create `app/src/main/java/com/wasif/khata/feature/wallet/WalletUiState.kt`:

```kotlin
package com.wasif.khata.feature.wallet

import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Account

data class WalletUiState(
    val netWorth: Money = Money.ZERO,
    val monthSpend: Money = Money.ZERO,
    val monthReceived: Money = Money.ZERO,
    val accounts: List<Account> = emptyList(),
) {
    val driftingAccounts: Int get() = accounts.count { it.hasBalanceDrift }
}
```

Create `app/src/main/java/com/wasif/khata/feature/wallet/WalletViewModel.kt`:

```kotlin
package com.wasif.khata.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WalletViewModel @Inject constructor(
    transactions: TransactionRepository,
    reference: ReferenceDataRepository,
    clock: KhataClock,
) : ViewModel() {

    private val now = clock.now()

    val state: StateFlow<WalletUiState> = combine(
        reference.observeNetWorth(),
        transactions.observeSpentBetween(now.dhakaMonthStart(), now.dhakaNextMonthStart()),
        transactions.observeReceivedBetween(now.dhakaMonthStart(), now.dhakaNextMonthStart()),
        reference.observeAccounts(),
    ) { netWorth, spend, received, accounts ->
        WalletUiState(
            netWorth = netWorth,
            monthSpend = spend,
            monthReceived = received,
            accounts = accounts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WalletUiState(),
    )
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.feature.wallet.WalletViewModelTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Write the screen**

Create `app/src/main/java/com/wasif/khata/feature/wallet/WalletScreen.kt`:

```kotlin
package com.wasif.khata.feature.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun WalletScreen(
    onBack: () -> Unit,
    onOpenLedger: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    WalletContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onOpenLedger = onOpenLedger,
    )
}

@Composable
fun WalletContent(
    state: WalletUiState,
    onBack: () -> Unit,
    onOpenLedger: () -> Unit,
) {
    // Task 6 widens this to (state, onBack: (() -> Unit)?, onOpenHub, onOpenLedger)
    // when a module can be the app's root. Kept narrow here so this task stands
    // on its own.
    val spacing = LocalSpacing.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // The back circle is the one thing deliberately outside the thumb arc:
        // gesture-back is the primary way out, so this is an affordance rather
        // than a control anyone should have to stretch for.
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
            Box(
                Modifier
                    .size(spacing.minTouchTarget)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Heading centred in open space; content anchored to the bottom. Same
        // shape as home, which is what makes the two read as one app.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ContextHeader(
                heading = "Wallet",
                subline = walletSubline(state),
            )
        }

        Column(
            Modifier.fillMaxWidth().padding(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            NetWorthCard(state = state)
            MonthPair(state = state)
            AccountList(state = state, onOpenLedger = onOpenLedger)
        }
    }
}

private fun walletSubline(state: WalletUiState): String {
    val accounts = "${state.accounts.size} accounts"
    // The subline answers "is this current?" -- the question the glance job is
    // actually asking -- rather than restating the heading.
    return if (state.driftingAccounts > 0) {
        "$accounts · ${state.driftingAccounts} need checking"
    } else {
        "$accounts · all reconciled"
    }
}

@Composable
private fun NetWorthCard(state: WalletUiState) {
    val spacing = LocalSpacing.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(KhataPalette.heroStops))
            .padding(spacing.md),
    ) {
        Column {
            Text(
                text = "NET WORTH",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(
                money = state.netWorth,
                direction = null,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = spacing.sm),
            )
        }
    }
}

@Composable
private fun MonthPair(state: WalletUiState) {
    val spacing = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        MonthFigure(
            label = "SPENT",
            money = state.monthSpend,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MonthFigure(
            label = "RECEIVED",
            money = state.monthReceived,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthFigure(
    label: String,
    money: com.wasif.khata.core.model.Money,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(spacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = money.format(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = tint,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

@Composable
private fun AccountList(state: WalletUiState, onOpenLedger: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal)
                .padding(top = spacing.md, bottom = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "ACCOUNTS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "Ledger →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenLedger),
            )
        }

        state.accounts.forEach { account ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        // The gap is stated in words, not colour alone.
                        text = if (account.hasBalanceDrift) "Balance disagrees · check" else "Reconciled",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (account.hasBalanceDrift) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
                Text(
                    text = account.currentBalance.format(),
                    style = AmountTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
```

- [ ] **Step 6: Wire the route and rewire the hub**

In `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`, add to `KhataRoutes`:

```kotlin
    const val Wallet = "wallet"
```

Change the start-destination mapping so a Wallet home actually lands on the Wallet dashboard:

```kotlin
    val start = when (homeView) {
        HomeView.Modules -> KhataRoutes.Modules
        HomeView.Wallet -> KhataRoutes.Wallet
    }
```

Point the hub at Wallet rather than Ledger:

```kotlin
        composable(KhataRoutes.Modules) {
            ModulesScreen(
                onOpenWallet = { navController.navigate(KhataRoutes.Wallet) },
                onOpenSettings = { navController.navigate(KhataRoutes.Settings) },
            )
        }
```

And add the destination:

```kotlin
        composable(KhataRoutes.Wallet) {
            WalletScreen(
                onBack = { navController.popBackStack() },
                onOpenLedger = { navController.navigate(KhataRoutes.Ledger) },
            )
        }
```

Add `import com.wasif.khata.feature.wallet.WalletScreen`.

- [ ] **Step 7: Write the screen test**

Create `app/src/androidTest/java/com/wasif/khata/feature/wallet/WalletScreenTest.kt`:

```kotlin
package com.wasif.khata.feature.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.domain.model.Account
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun account(name: String, balance: Long, reported: Long?) = Account(
        id = name.hashCode().toLong(),
        uuid = name,
        name = name,
        type = AccountType.MFS,
        currentBalance = Money(balance),
        reportedBalance = reported?.let { Money(it) },
        includeInNetWorth = true,
    )

    @Test
    fun theSublineSaysWhetherAnythingNeedsChecking() {
        compose.setContent {
            KhataTheme {
                WalletContent(
                    WalletUiState(
                        accounts = listOf(
                            account("bKash", 8_214_30, 8_214_30),
                            account("EBL", 2_98_606_00, 2_98_846_00),
                        ),
                    ),
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithText("Wallet").assertIsDisplayed()
        compose.onNodeWithText("2 accounts · 1 need checking").assertIsDisplayed()
    }

    @Test
    fun aDriftingAccountSaysSoInWordsNotOnlyColour() {
        compose.setContent {
            KhataTheme {
                WalletContent(
                    WalletUiState(accounts = listOf(account("EBL", 2_98_606_00, 2_98_846_00))),
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithText("Balance disagrees · check").assertIsDisplayed()
    }

    @Test
    fun theAppNameNeverAppearsOnAModulePage() {
        compose.setContent {
            KhataTheme { WalletContent(WalletUiState(), {}, {}) }
        }

        val wordmark = compose.onAllNodesWithText("খাতা").fetchSemanticsNodes()
        assertTrue("the wordmark belongs to the hub alone", wordmark.isEmpty())
    }
}
```

- [ ] **Step 8: Run everything**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.wallet.WalletScreenTest
```

Expected: both `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/wallet \
        app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt \
        app/src/test/java/com/wasif/khata/feature/wallet \
        app/src/androidTest/java/com/wasif/khata/feature/wallet
git commit -m "feat: the Wallet dashboard

Net worth on petrol, the month's two figures, and the account list.
Reconciliation gaps are stated in words as well as colour, and surfaced
on the module's home rather than behind a tap -- product principle #2 is
that the app proves itself rather than asking the user to notice.

The hub now routes to Wallet rather than straight to the Ledger, so the
drill-down is hub -> module -> list as designed."
```

---

### Task 3: The Ledger rebuild, month-scoped

The screen that has to survive 3,000 rows. It shows **one month at a time**, with arrows to move
between months — the heading says "August 2026" and "6 days left", and both have to be true.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerItem.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/ledger/LedgerViewModelTest.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/ledger/LedgerScreenTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository.observeDayTotals()`, `observeSpentBetween` (Task 1); `DHAKA`, `dhakaNextMonthStart`, `toDhakaLocalDate` from `core/time/KhataClock.kt`; `CategoryDot`, `MoneyText`, `AmountTextStyle`, `PageHeadingStyle`, `PageSublineStyle`, `Spacing.headspaceLedger` from Plans A and B.
- Produces:
  - `TransactionDao.pagingSourceBetween(fromInclusive: Long, toExclusive: Long): PagingSource<Int, TransactionEntity>`
  - `TransactionRepository.pagedTransactionsBetween(fromInclusive: Long, toExclusive: Long): Flow<PagingData<Transaction>>`
  - `LedgerItem.DayHeader(date: LocalDate, total: Money)`
  - `data class LedgerHeaderState(monthLabel: String, monthSpend: Money, daysLeft: Int?)`
  - `LedgerViewModel.header`, `.categoryTokens`, `.canGoForward`, `.onPreviousMonth()`, `.onNextMonth()`

- [ ] **Step 1: Write the failing DAO test**

Append inside the existing class in `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`:

```kotlin
    @Test
    fun `paging is scoped to a half-open window`() = runTest {
        val accountId = insertAccount()
        val dao = db.transactionDao()
        val august = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()
        val july = Instant.parse("2026-07-10T06:00:00Z").toEpochMilli()

        dao.upsert(transaction(accountId, august, "aug"))
        dao.upsert(transaction(accountId, july, "jul"))

        val pager = TestPager(
            PagingConfig(pageSize = 10),
            dao.pagingSourceBetween(august.dhakaMonthStart(), august.dhakaNextMonthStart()),
        )
        val page = pager.refresh() as PagingSource.LoadResult.Page

        assertEquals(listOf("aug"), page.data.map { it.uuid })
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.data.TransactionDaoTest"`
Expected: compilation failure — `Unresolved reference: pagingSourceBetween`.

- [ ] **Step 3: Add the windowed query and repository method**

Add to `TransactionDao`:

```kotlin
    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND occurredAt >= :fromInclusive
          AND occurredAt < :toExclusive
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun pagingSourceBetween(fromInclusive: Long, toExclusive: Long): PagingSource<Int, TransactionEntity>
```

Add to the `TransactionRepository` interface:

```kotlin
    /** One month of the ledger. The half-open window is the caller's to compute in Dhaka. */
    fun pagedTransactionsBetween(fromInclusive: Long, toExclusive: Long): Flow<PagingData<Transaction>>
```

Implement in `TransactionRepositoryImpl`:

```kotlin
    override fun pagedTransactionsBetween(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<PagingData<Transaction>> =
        Pager(PagingConfig(pageSize = 50, prefetchDistance = 25, enablePlaceholders = false)) {
            transactionDao.pagingSourceBetween(fromInclusive, toExclusive)
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }
```

Then add the override to the three fakes named in Task 1 Step 10:

```kotlin
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> = pagedTransactions()
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.data.TransactionDaoTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing ViewModel tests**

Add these fields to the existing class in `app/src/test/java/com/wasif/khata/feature/ledger/LedgerViewModelTest.kt`:

```kotlin
    private val dayTotals = MutableStateFlow(emptyMap<LocalDate, Money>())
    private val spend = MutableStateFlow(Money.ZERO)
    private var requestedWindow: Pair<Long, Long>? = null

    private val clock = object : KhataClock {
        override fun now(): Long = Instant.parse("2026-08-28T09:41:00Z").toEpochMilli()
    }

    private val referenceData = object : ReferenceDataRepository {
        override fun observeAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override fun observeCategories(): Flow<List<Category>> = flowOf(
            listOf(
                Category(
                    id = 11,
                    uuid = "seed-cat-groceries",
                    name = "Groceries",
                    icon = "shopping_cart",
                    colorToken = "category_green",
                    parentId = null,
                ),
            ),
        )
        override fun observeNetWorth(): Flow<Money> = flowOf(Money.ZERO)
    }
```

Extend the existing `repositoryReturning` fake with:

```kotlin
        override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> = spend
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = dayTotals
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> {
            requestedWindow = fromInclusive to toExclusive
            return pagedTransactions()
        }
```

Then add these tests:

```kotlin
    @Test
    fun `a day header carries that day's spending total`() = runTest {
        val aug26 = Instant.parse("2026-08-26T06:00:00Z").toEpochMilli()
        dayTotals.value = mapOf(aug26.toDhakaLocalDate() to Money(3_445_50))

        val viewModel = LedgerViewModel(repositoryReturning(transaction(1, aug26)), referenceData, clock)

        val header = viewModel.items.asSnapshot().filterIsInstance<LedgerItem.DayHeader>().single()
        assertEquals(Money(3_445_50), header.total)
    }

    @Test
    fun `a day with no total entry shows zero rather than crashing`() = runTest {
        // The totals map and the paged rows are two independent queries. They can
        // disagree for a frame, and a header must not blow up when they do.
        val aug26 = Instant.parse("2026-08-26T06:00:00Z").toEpochMilli()
        dayTotals.value = emptyMap()

        val viewModel = LedgerViewModel(repositoryReturning(transaction(1, aug26)), referenceData, clock)

        val header = viewModel.items.asSnapshot().filterIsInstance<LedgerItem.DayHeader>().single()
        assertEquals(Money.ZERO, header.total)
    }

    @Test
    fun `the ledger opens on the current month and says how much of it is left`() = runTest {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        viewModel.header.test {
            advanceUntilIdle()
            val h = expectMostRecentItem()
            // "August 2026" rather than "Ledger": you know you are in the ledger
            // because you tapped to get here; what you do not know is the month.
            assertEquals("August 2026", h.monthLabel)
            // 28 August of a 31-day month.
            assertEquals(3, h.daysLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stepping back a month re-windows the query and relabels the header`() = runTest {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)
        advanceUntilIdle()

        viewModel.onPreviousMonth()
        advanceUntilIdle()
        viewModel.items.asSnapshot()

        assertEquals("July 2026", viewModel.header.value.monthLabel)
        // 1 July 00:00 Dhaka is 30 June 18:00 UTC.
        assertEquals(Instant.parse("2026-06-30T18:00:00Z").toEpochMilli(), requestedWindow?.first)
    }

    @Test
    fun `a past month has no days left, and the future is unreachable`() = runTest {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)
        advanceUntilIdle()

        // The current month is the newest that can hold anything; an empty
        // future month is a dead end.
        assertEquals(false, viewModel.canGoForward.value)

        viewModel.onPreviousMonth()
        advanceUntilIdle()

        assertEquals(null, viewModel.header.value.daysLeft)
        assertEquals(true, viewModel.canGoForward.value)
    }

    @Test
    fun `category tokens are keyed by id so a row can resolve its dot`() = runTest {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        viewModel.categoryTokens.test {
            advanceUntilIdle()
            assertEquals("category_green", expectMostRecentItem()[11L])
            cancelAndIgnoreRemainingEvents()
        }
    }
```

- [ ] **Step 6: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.feature.ledger.LedgerViewModelTest"`
Expected: failure — `LedgerViewModel` takes one argument, and `DayHeader` has no `total`.

- [ ] **Step 7: Give DayHeader a total**

Replace `app/src/main/java/com/wasif/khata/feature/ledger/LedgerItem.kt`:

```kotlin
package com.wasif.khata.feature.ledger

import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Transaction
import java.time.LocalDate

sealed interface LedgerItem {
    data class Row(val transaction: Transaction) : LedgerItem
    data class DayHeader(val date: LocalDate, val total: Money) : LedgerItem
}
```

- [ ] **Step 8: Rewrite the ViewModel**

Replace `app/src/main/java/com/wasif/khata/feature/ledger/LedgerViewModel.kt`:

```kotlin
package com.wasif.khata.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.DHAKA
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LedgerHeaderState(
    val monthLabel: String = "",
    val monthSpend: Money = Money.ZERO,
    /** Null for any month that is not the current one -- a past month has no days left. */
    val daysLeft: Int? = null,
)

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repository: TransactionRepository,
    referenceData: ReferenceDataRepository,
    private val clock: KhataClock,
) : ViewModel() {

    private val currentMonth = YearMonth.from(clock.now().toDhakaLocalDate())

    private val _viewedMonth = MutableStateFlow(currentMonth)

    /** The current month is the newest that can hold anything, so forward stops there. */
    val canGoForward: StateFlow<Boolean> = _viewedMonth
        .map { it < currentMonth }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onPreviousMonth() {
        _viewedMonth.value = _viewedMonth.value.minusMonths(1)
    }

    fun onNextMonth() {
        if (_viewedMonth.value < currentMonth) {
            _viewedMonth.value = _viewedMonth.value.plusMonths(1)
        }
    }

    /** categoryId -> colorToken, so a row resolves its dot without a per-row query. */
    val categoryTokens: StateFlow<Map<Long, String>> = referenceData.observeCategories()
        .map { categories -> categories.associate { it.id to it.colorToken } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val header: StateFlow<LedgerHeaderState> = _viewedMonth
        .flatMapLatest { month ->
            val (from, to) = month.dhakaWindow()
            repository.observeSpentBetween(from, to).map { spend ->
                LedgerHeaderState(
                    monthLabel = month.format(monthFormatter),
                    monthSpend = spend,
                    daysLeft = if (month == currentMonth) {
                        month.lengthOfMonth() - clock.now().toDhakaLocalDate().dayOfMonth
                    } else {
                        null
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerHeaderState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<LedgerItem>> = _viewedMonth
        // flatMapLatest cancels the previous month's page stream rather than
        // stacking one per tap on the arrows.
        .flatMapLatest { month ->
            val (from, to) = month.dhakaWindow()
            repository.pagedTransactionsBetween(from, to)
        }
        .combine(repository.observeDayTotals()) { paging, totals ->
            paging.map { LedgerItem.Row(it) }
                // insertSeparators<T, R> widens Row to LedgerItem, so the generator
                // receives typed Rows and needs no casts.
                .insertSeparators<LedgerItem.Row, LedgerItem> { before, after ->
                    if (after == null) {
                        null
                    } else {
                        val afterDate = after.transaction.occurredAt.toDhakaLocalDate()
                        val beforeDate = before?.transaction?.occurredAt?.toDhakaLocalDate()
                        if (beforeDate != afterDate) {
                            // The totals map and the paged rows are two queries and
                            // can disagree for a frame. Zero is a safe reading; a
                            // crash in a 3,000-row list is not.
                            LedgerItem.DayHeader(afterDate, totals[afterDate] ?: Money.ZERO)
                        } else {
                            null
                        }
                    }
                }
        }
        .cachedIn(viewModelScope)
}

/** The month's half-open bounds in epoch millis, computed in Dhaka. */
private fun YearMonth.dhakaWindow(): Pair<Long, Long> {
    val start = atDay(1).atStartOfDay(DHAKA).toInstant().toEpochMilli()
    return start to start.dhakaNextMonthStart()
}
```

- [ ] **Step 9: Run them to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.feature.ledger.LedgerViewModelTest"`
Expected: PASS.

- [ ] **Step 10: Rebuild the screen**

Replace `app/src/main/java/com/wasif/khata/feature/ledger/LedgerScreen.kt`:

```kotlin
package com.wasif.khata.feature.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.ui.component.CategoryDot
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.PageHeadingStyle
import com.wasif.khata.core.ui.theme.PageSublineStyle
import java.time.format.DateTimeFormatter

@Composable
fun LedgerScreen(
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    LedgerContent(
        items = viewModel.items.collectAsLazyPagingItems(),
        header = viewModel.header.collectAsStateWithLifecycle().value,
        categoryTokens = viewModel.categoryTokens.collectAsStateWithLifecycle().value,
        canGoForward = viewModel.canGoForward.collectAsStateWithLifecycle().value,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onBack = onBack,
        onAddTransaction = onAddTransaction,
        onOpenTransaction = onOpenTransaction,
    )
}

@Composable
fun LedgerContent(
    items: LazyPagingItems<LedgerItem>,
    header: LedgerHeaderState,
    categoryTokens: Map<Long, String>,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
                Box(
                    Modifier.size(spacing.minTouchTarget).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // A list of 3,000 rows has no bottom to anchor to, so the ledger gets
            // a fixed headspace where the other screens get a flexible one --
            // enough to read as the same family, small enough that rows stay
            // visible before scrolling.
            MonthHeader(
                header = header,
                canGoForward = canGoForward,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                modifier = Modifier.fillMaxWidth().height(spacing.headspaceLedger),
            )

            MonthStrip(header = header)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.screenHorizontal,
                    end = spacing.screenHorizontal,
                    top = spacing.sm,
                    // Clear the FAB, or the last row hides under it.
                    bottom = spacing.xxl + spacing.xl,
                ),
            ) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { item ->
                        when (item) {
                            is LedgerItem.Row -> "row-${item.transaction.id}"
                            is LedgerItem.DayHeader -> "header-${item.date}"
                        }
                    },
                    // Headers and rows are different shapes, so separate content
                    // types let LazyColumn recycle each against its own pool.
                    contentType = items.itemContentType { item ->
                        when (item) {
                            is LedgerItem.Row -> "row"
                            is LedgerItem.DayHeader -> "header"
                        }
                    },
                ) { index ->
                    when (val item = items[index]) {
                        is LedgerItem.DayHeader -> DayHeaderRow(item)
                        is LedgerItem.Row -> TransactionRow(
                            item = item,
                            token = item.transaction.categoryId?.let { categoryTokens[it] },
                            onClick = { onOpenTransaction(item.transaction.id) },
                        )
                        null -> Unit
                    }
                }
            }
        }

        if (items.itemCount == 0) {
            EmptyLedger(Modifier.align(Alignment.Center), monthLabel = header.monthLabel)
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(spacing.screenHorizontal)
                .size(58.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(KhataPalette.heroStops))
                .clickable(onClick = onAddTransaction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add transaction",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    header: LedgerHeaderState,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Row(modifier.padding(horizontal = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        MonthArrow(
            icon = Icons.Filled.ChevronLeft,
            description = "Previous month",
            enabled = true,
            onClick = onPreviousMonth,
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = header.monthLabel,
                style = PageHeadingStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                // A past month has no days left; "0 days left" would be a
                // different and wrong claim.
                text = header.daysLeft?.let { "$it days left" } ?: "Complete month",
                style = PageSublineStyle,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
        MonthArrow(
            icon = Icons.Filled.ChevronRight,
            description = "Next month",
            enabled = canGoForward,
            onClick = onNextMonth,
        )
    }
}

@Composable
private fun MonthArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Box(
        Modifier
            .size(spacing.minTouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            // Disabled rather than hidden: a control that vanishes is harder to
            // understand than one that is visibly unavailable.
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
    }
}

@Composable
private fun MonthStrip(header: LedgerHeaderState) {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(KhataPalette.heroStops))
            .padding(spacing.md),
    ) {
        Text(
            text = "SPENT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = header.monthSpend.format(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
private fun DayHeaderRow(header: LedgerItem.DayHeader) {
    val spacing = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().padding(top = spacing.md, bottom = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = header.date.format(dayFormatter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = header.total.format(),
            style = AmountTextStyle,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun TransactionRow(
    item: LedgerItem.Row,
    token: String?,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val transaction = item.transaction

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.minTouchTarget)
                .clickable(onClick = onClick)
                .padding(vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CategoryDot(
                token = token ?: "category_neutral",
                lowConfidence = transaction.confidence == Confidence.LOW,
            )
            Column(Modifier.weight(1f).padding(start = spacing.sm, end = spacing.sm)) {
                Text(
                    text = transaction.merchantRaw ?: "Uncategorized",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Colour is never the sole signal: low confidence is a ring on
                // the dot and the word here.
                val meta = buildList {
                    transaction.note?.let { add(it) }
                    if (transaction.confidence == Confidence.LOW) add("low confidence")
                }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MoneyText(money = transaction.amount, direction = transaction.direction)
        }
        // Rows separate with a hairline, never glass: one blur pass per row per
        // frame on a Paging list is not affordable.
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EmptyLedger(modifier: Modifier = Modifier, monthLabel: String) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // Names the month, so an empty past month does not read as an empty app.
            text = "Nothing in $monthLabel",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Use the arrows to look at another month, or tap + to record something.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}
```

- [ ] **Step 11: Update the navigation call**

`LedgerScreen` now takes `onBack`. In `KhataNavHost.kt`:

```kotlin
        composable(KhataRoutes.Ledger) {
            LedgerScreen(
                onBack = { navController.popBackStack() },
                onAddTransaction = { navController.navigate(KhataRoutes.EditorNew) },
                onOpenTransaction = { id -> navController.navigate(KhataRoutes.editorEdit(id)) },
            )
        }
```

- [ ] **Step 12: Write the screen tests**

Replace the test bodies in `app/src/androidTest/java/com/wasif/khata/feature/ledger/LedgerScreenTest.kt`. Keep the file's package; add imports as needed.

```kotlin
    private fun content(
        items: List<LedgerItem> = emptyList(),
        header: LedgerHeaderState = LedgerHeaderState(monthLabel = "August 2026", daysLeft = 3),
        canGoForward: Boolean = false,
        onPreviousMonth: () -> Unit = {},
        onNextMonth: () -> Unit = {},
    ): @Composable () -> Unit = {
        KhataTheme {
            LedgerContent(
                items = flowOf(PagingData.from(items)).collectAsLazyPagingItems(),
                header = header,
                categoryTokens = mapOf(11L to "category_green"),
                canGoForward = canGoForward,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onBack = {},
                onAddTransaction = {},
                onOpenTransaction = {},
            )
        }
    }

    @Test
    fun theHeadingNamesTheMonthNotTheScreen() {
        compose.setContent(content())

        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithText("3 days left").assertIsDisplayed()
        // "Ledger" as a title would spend the space telling the user what they
        // already know from having tapped to get here.
        val stale = compose.onAllNodesWithText("Ledger").fetchSemanticsNodes()
        assertTrue("the heading should be the period, not the screen name", stale.isEmpty())
    }

    @Test
    fun aPastMonthSaysCompleteRatherThanZeroDaysLeft() {
        compose.setContent(content(header = LedgerHeaderState(monthLabel = "July 2026", daysLeft = null)))

        compose.onNodeWithText("Complete month").assertIsDisplayed()
    }

    @Test
    fun theMonthArrowsAreReachable() {
        var back = 0
        compose.setContent(content(canGoForward = true, onPreviousMonth = { back++ }))

        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithContentDescription("Next month").assertIsDisplayed()

        assertEquals(1, back)
    }

    @Test
    fun anEmptyMonthNamesTheMonthRatherThanClaimingTheAppIsEmpty() {
        compose.setContent(content(header = LedgerHeaderState(monthLabel = "July 2026")))

        compose.onNodeWithText("Nothing in July 2026").assertIsDisplayed()
    }

    @Test
    fun aLowConfidenceRowSaysSoInWords() {
        val low = Transaction(
            id = 1,
            uuid = "t1",
            accountId = 1,
            amount = Money(165_00),
            direction = TransactionDirection.DEBIT,
            occurredAt = Instant.parse("2026-08-28T06:00:00Z").toEpochMilli(),
            merchantRaw = "Pathao",
            merchantId = null,
            categoryId = 11,
            note = null,
            source = TransactionSource.SMS,
            confidence = Confidence.LOW,
            transferGroupId = null,
            updatedAt = 0,
        )

        compose.setContent(content(items = listOf(LedgerItem.Row(low))))

        compose.onNodeWithText("Pathao").assertIsDisplayed()
        compose.onNodeWithText("low confidence").assertIsDisplayed()
        compose.onNodeWithContentDescription("Low confidence").assertIsDisplayed()
    }
```

- [ ] **Step 13: Run everything**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.ledger.LedgerScreenTest
```

Expected: both `BUILD SUCCESSFUL`.

- [ ] **Step 14: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt app/src/main/java/com/wasif/khata/feature/ledger app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt app/src/test/java/com/wasif/khata app/src/androidTest/java/com/wasif/khata/feature/ledger
git commit -m "feat: rebuild the Ledger as a month view

The heading is the period rather than the word Ledger: you know where
you are because you tapped to get here, but not which month. That only
works if it is true, so the list is windowed to the month shown and
arrows move between months.

Forward stops at the current month -- nothing is recorded beyond it and
an empty future month is a dead end. A past month says 'Complete month'
rather than '0 days left', which would be a different and wrong claim.

Day headers carry that day's total from a grouped query. Category dots
resolve from one id-to-token map rather than a per-row lookup, and low
confidence is a ring plus the word."
```

---

### Task 4: Ledger search

`PRODUCT.md` names "a targeted hunt for one specific past transaction" as one of four confirmed usage situations, and the spec puts the field on screen rather than behind an icon. A visible field that does nothing would be worse than none.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`

**Interfaces:**
- Consumes: everything from Task 3.
- Produces:
  - `TransactionDao.pagingSourceMatching(query: String): PagingSource<Int, TransactionEntity>`
  - `TransactionRepository.pagedTransactions(query: String): Flow<PagingData<Transaction>>`
  - `LedgerViewModel.query: StateFlow<String>` and `fun onQueryChange(value: String)`

- [ ] **Step 1: Write the failing DAO test**

Append inside the existing class in `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`:

```kotlin
    @Test
    fun `search matches merchant and note, case-insensitively`() = runTest {
        val accountId = insertAccount()
        val dao = db.transactionDao()
        val day = Instant.parse("2026-08-29T06:00:00Z").toEpochMilli()

        dao.upsert(transaction(accountId, day, "m1", merchantRaw = "North End Coffee"))
        dao.upsert(transaction(accountId, day, "m2", merchantRaw = "Chaldal"))

        val pager = TestPager(PagingConfig(pageSize = 10), dao.pagingSourceMatching("coffee"))
        val page = pager.refresh() as PagingSource.LoadResult.Page

        assertEquals(listOf("m1"), page.data.map { it.uuid })
    }

    @Test
    fun `search escapes wildcards so a literal percent finds nothing`() = runTest {
        // Without escaping, a bare % matches every row -- which would turn a
        // failed search into "here is your entire ledger".
        val accountId = insertAccount()
        val dao = db.transactionDao()
        val day = Instant.parse("2026-08-29T06:00:00Z").toEpochMilli()

        dao.upsert(transaction(accountId, day, "m1", merchantRaw = "Chaldal"))

        val pager = TestPager(PagingConfig(pageSize = 10), dao.pagingSourceMatching("%"))
        val page = pager.refresh() as PagingSource.LoadResult.Page

        assertEquals(emptyList<String>(), page.data.map { it.uuid })
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.data.TransactionDaoTest"`
Expected: compilation failure — `Unresolved reference: pagingSourceMatching`.

- [ ] **Step 3: Add the query**

Add to `TransactionDao`:

```kotlin
    // ESCAPE '\' with the caller pre-escaping % and _ : an unescaped wildcard
    // turns a search that should find nothing into one that returns the whole
    // ledger, which is the worst possible answer to "find that one thing".
    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND (merchantRaw LIKE :pattern ESCAPE '\' OR note LIKE :pattern ESCAPE '\')
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun pagingSourceMatchingPattern(pattern: String): PagingSource<Int, TransactionEntity>
```

And a wrapper on the interface's companion-free surface — add this extension in the same file, below the interface:

```kotlin
/** Escapes LIKE wildcards, then wraps in % so the term matches anywhere. */
fun TransactionDao.pagingSourceMatching(query: String): PagingSource<Int, TransactionEntity> {
    val escaped = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return pagingSourceMatchingPattern("%$escaped%")
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.data.TransactionDaoTest"`
Expected: PASS.

- [ ] **Step 5: Extend the repository**

Add to `TransactionRepository`:

```kotlin
    /** Blank query returns everything, so the ledger has one code path. */
    fun pagedTransactions(query: String): Flow<PagingData<Transaction>>
```

Implement in `TransactionRepositoryImpl`:

```kotlin
    override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> =
        Pager(PagingConfig(pageSize = 50, prefetchDistance = 25, enablePlaceholders = false)) {
            if (query.isBlank()) {
                transactionDao.pagingSource()
            } else {
                transactionDao.pagingSourceMatching(query)
            }
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }
```

Add `import com.wasif.khata.core.data.dao.pagingSourceMatching`.

Then add the override to the three fakes named in Task 1 Step 10:

```kotlin
        override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> =
            pagedTransactions()
```

- [ ] **Step 6: Wire the ViewModel**

In `LedgerViewModel`, add the query state and replace the `items` declaration so a search
**escapes the month window**:

```kotlin
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** True while a search is showing results from outside the viewed month. */
    val isSearching: StateFlow<Boolean> = _query
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<LedgerItem>> = combine(_viewedMonth, _query) { month, q -> month to q }
        // Search spans all time: hunting for one past transaction is a distinct
        // job from reviewing a month, and confining it to the viewed month would
        // make the common case -- "I know I bought it, I forget when" -- fail.
        // flatMapLatest cancels the previous stream rather than stacking one page
        // load per keystroke.
        .flatMapLatest { (month, q) ->
            if (q.isBlank()) {
                val (from, to) = month.dhakaWindow()
                repository.pagedTransactionsBetween(from, to)
            } else {
                repository.pagedTransactions(q)
            }
        }
        .combine(repository.observeDayTotals()) { paging, totals ->
            paging.map { LedgerItem.Row(it) }
                .insertSeparators<LedgerItem.Row, LedgerItem> { before, after ->
                    if (after == null) {
                        null
                    } else {
                        val afterDate = after.transaction.occurredAt.toDhakaLocalDate()
                        val beforeDate = before?.transaction?.occurredAt?.toDhakaLocalDate()
                        if (beforeDate != afterDate) {
                            LedgerItem.DayHeader(afterDate, totals[afterDate] ?: Money.ZERO)
                        } else {
                            null
                        }
                    }
                }
        }.cachedIn(viewModelScope)
```

Add imports: `kotlinx.coroutines.flow.MutableStateFlow`, `asStateFlow`, `flatMapLatest`.

- [ ] **Step 7: Add the field to the screen**

In `LedgerScreen.kt`, extend `LedgerScreen` and `LedgerContent` with `query: String` and `onQueryChange: (String) -> Unit`, pass `viewModel.query.collectAsStateWithLifecycle().value` and `viewModel::onQueryChange`, and insert this between `MonthStrip` and the `LazyColumn`:

```kotlin
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search merchants and notes") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            )
```

Add `import androidx.compose.material3.OutlinedTextField`.

Update the empty state so a search that finds nothing does not make a claim about the month:

```kotlin
        if (items.itemCount == 0) {
            EmptyLedger(
                modifier = Modifier.align(Alignment.Center),
                monthLabel = header.monthLabel,
                isSearching = query.isNotBlank(),
            )
        }
```

And replace `EmptyLedger`:

```kotlin
@Composable
private fun EmptyLedger(
    modifier: Modifier = Modifier,
    monthLabel: String,
    isSearching: Boolean,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // A failed search is not an empty month, and saying so would be a
            // claim about the wrong thing.
            text = if (isSearching) "Nothing matches that" else "Nothing in $monthLabel",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (isSearching) {
                "Search covers every month, so try a shorter word."
            } else {
                "Use the arrows to look at another month, or tap + to record something."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}
```

Because search escapes the month window, the month arrows would be misleading while a search is
active. Disable both while `query` is non-blank — pass `enabled = !isSearching` through
`MonthHeader` to each `MonthArrow`, alongside the existing `canGoForward` rule for the right-hand
one.

- [ ] **Step 8: Update the Task 3 screen tests for the new signature**

`LedgerContent` gained `query` and `onQueryChange`, which breaks every call in
`app/src/androidTest/java/com/wasif/khata/feature/ledger/LedgerScreenTest.kt`. Task 3 routed
them all through one `content(...)` helper, so add two parameters there and pass them through:

```kotlin
    private fun content(
        items: List<LedgerItem> = emptyList(),
        header: LedgerHeaderState = LedgerHeaderState(monthLabel = "August 2026", daysLeft = 3),
        canGoForward: Boolean = false,
        query: String = "",
        onPreviousMonth: () -> Unit = {},
        onNextMonth: () -> Unit = {},
    ): @Composable () -> Unit = {
        KhataTheme {
            LedgerContent(
                items = flowOf(PagingData.from(items)).collectAsLazyPagingItems(),
                header = header,
                categoryTokens = mapOf(11L to "category_green"),
                canGoForward = canGoForward,
                query = query,
                onQueryChange = {},
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onBack = {},
                onAddTransaction = {},
                onOpenTransaction = {},
            )
        }
    }
```

Then add one test for the case the new empty state exists for:

```kotlin
    @Test
    fun aSearchWithNoMatchesDoesNotClaimTheMonthIsEmpty() {
        // Without this branch the screen would say "Nothing in August 2026",
        // which is a claim about the month rather than about the search.
        compose.setContent(content(query = "zzzz"))

        compose.onNodeWithText("Nothing matches that").assertIsDisplayed()
    }
```

- [ ] **Step 9: Run everything**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Expected: both `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt \
        app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt \
        app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt \
        app/src/main/java/com/wasif/khata/feature/ledger \
        app/src/test/java/com/wasif/khata/
git commit -m "feat: ledger search over merchants and notes

One of the four confirmed usage situations is hunting for a single past
transaction, and the design puts the field on screen rather than behind
an icon -- so it had to actually work.

LIKE wildcards are escaped. Unescaped, a search for '%' returns the
entire ledger, which is the worst possible answer to 'find that one
thing'.

flatMapLatest cancels the previous page stream per keystroke rather than
stacking one load per character, and an empty result now says nothing
matched instead of claiming the app is empty."
```

---

### Task 5: The Editor rebuild

Amount first and largest. The single change that most directly answers "everything is the same size".

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/editor/TransactionEditorScreenTest.kt`

**Interfaces:**
- Consumes: `TransactionEditorUiState`, `TransactionEditorActions` (unchanged); `CategoryDot`, `ContextHeader` from Plan A.
- Produces: no new types. `TransactionEditorScreen` keeps its existing signature.

- [ ] **Step 1: Write the failing screen test**

Append to `app/src/androidTest/java/com/wasif/khata/feature/editor/TransactionEditorScreenTest.kt`:

```kotlin
    @Test
    fun theAmountIsTheLargestThingOnScreen() {
        // The old build made the amount a labelled OutlinedTextField the same
        // size as everything else, which is most of what "everything is the same
        // size" was pointing at.
        compose.setContent {
            KhataTheme {
                TransactionEditorContent(
                    state = TransactionEditorUiState(amountInput = "540", accountId = 1),
                    actions = NoopActions,
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("৳540").assertIsDisplayed()
    }

    @Test
    fun directionIsTwoLabelledStatesRatherThanASwitch() {
        // A switch hides which state is which.
        compose.setContent {
            KhataTheme {
                TransactionEditorContent(
                    state = TransactionEditorUiState(accountId = 1),
                    actions = NoopActions,
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Spent").assertIsDisplayed()
        compose.onNodeWithText("Received").assertIsDisplayed()
    }

    @Test
    fun theContextLineStatesWhatTheEditorAssumed() {
        compose.setContent {
            KhataTheme {
                TransactionEditorContent(
                    state = TransactionEditorUiState(accountId = 1),
                    actions = NoopActions,
                    onBack = {},
                )
            }
        }

        // Defaults the editor picked on the user's behalf, stated where they can
        // be corrected rather than left invisible.
        compose.onNodeWithText("New entry").assertIsDisplayed()
    }
```

If the file has no `NoopActions`, add it above the tests:

```kotlin
    private object NoopActions : TransactionEditorActions {
        override fun onAmountChange(value: String) = Unit
        override fun onMerchantChange(value: String) = Unit
        override fun onNoteChange(value: String) = Unit
        override fun onDirectionChange(direction: TransactionDirection) = Unit
        override fun onAccountSelected(id: Long) = Unit
        override fun onCategorySelected(id: Long) = Unit
        override fun onSave() = Unit
        override fun onDelete() = Unit
    }
```

Match the member names to the real `TransactionEditorActions` interface — read it rather than assuming.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.editor.TransactionEditorScreenTest`
Expected: failure — the amount renders as a labelled field, not as `৳540` at display size.

- [ ] **Step 3: Rebuild the screen body**

Replace the `TransactionEditorContent` composable in `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt` with:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorContent(
    state: TransactionEditorUiState,
    actions: TransactionEditorActions,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(spacing.minTouchTarget).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (state.isEditing) {
                Box(
                    Modifier
                        .size(spacing.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = actions::onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete transaction",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ContextHeader(
                heading = if (state.isEditing) "Edit entry" else "New entry",
                // The defaults the editor already assumed, stated where they can
                // be corrected rather than left invisible.
                subline = state.accounts.firstOrNull { it.id == state.accountId }?.name
                    ?: "No account",
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = spacing.lg),
        ) {
            // Amount first and largest. This is the single change that most
            // directly answers "everything is the same size".
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal)
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(KhataPalette.heroStops))
                    .padding(spacing.md),
            ) {
                Text(
                    text = "AMOUNT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = state.amountInput,
                    onValueChange = actions::onAmountChange,
                    textStyle = MaterialTheme.typography.displayLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontFeatureSettings = "tnum",
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "৳",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Box(Modifier.weight(1f)) { inner() }
                        }
                    },
                )
                if (state.amountHasError) {
                    // Durable text under the field, never a transient toast.
                    Text(
                        text = "Enter an amount like 1234.56",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
            }

            // Two visible states, never a switch: a switch hides which state is
            // which.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                TransactionDirection.entries.forEach { direction ->
                    val selected = state.direction == direction
                    Box(
                        Modifier
                            .weight(1f)
                            .height(spacing.minTouchTarget)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            )
                            .clickable { actions.onDirectionChange(direction) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (direction == TransactionDirection.DEBIT) "Spent" else "Received",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            FieldLabel("Account")
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                state.accounts.forEach { account ->
                    EditorChip(
                        label = account.name,
                        selected = state.accountId == account.id,
                        token = null,
                        onClick = { actions.onAccountSelected(account.id) },
                    )
                }
            }

            FieldLabel("Category")
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                state.categories.forEach { category ->
                    EditorChip(
                        label = category.name,
                        selected = state.categoryId == category.id,
                        token = category.colorToken,
                        onClick = { actions.onCategorySelected(category.id) },
                    )
                }
            }

            FieldLabel("Merchant")
            OutlinedTextField(
                value = state.merchantInput,
                onValueChange = actions::onMerchantChange,
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
            )

            FieldLabel("Note")
            OutlinedTextField(
                value = state.noteInput,
                onValueChange = actions::onNoteChange,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
            )

            state.saveError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.sm,
                    ),
                )
            }

            // Full-width, in the thumb arc, disabled until genuinely saveable.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.screenHorizontal)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (state.canSave) {
                            Brush.linearGradient(KhataPalette.heroStops)
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            )
                        },
                    )
                    .clickable(enabled = state.canSave, onClick = actions::onSave)
                    .padding(vertical = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Save entry",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.canSave) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    val spacing = LocalSpacing.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(
            start = spacing.screenHorizontal,
            end = spacing.screenHorizontal,
            top = spacing.md,
            bottom = spacing.sm,
        ),
    )
}

@Composable
private fun EditorChip(
    label: String,
    selected: Boolean,
    token: String?,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // Colour quarantined to a dot; the name always carries the meaning.
        token?.let { CategoryDot(token = it) }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
```

Update the file's imports to cover: `BasicTextField`, `SolidColor`, `Brush`, `Icons.Filled.Close`, `Icons.Filled.Delete`, `CircleShape`, `WindowInsets`, `systemBars`, `windowInsetsPadding`, `CategoryDot`, `ContextHeader`, `KhataPalette`, `LocalSpacing`, `height`, `size`, `clip`, `background`, `clickable`.

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.editor.TransactionEditorScreenTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/editor app/src/androidTest/java/com/wasif/khata/feature/editor
git commit -m "feat: rebuild the Editor amount-first

The amount is now the screen: displayLarge on petrol with a tabular
figure and its own currency symbol, instead of a labelled OutlinedTextField
the same size as everything else.

Direction is two labelled states rather than a switch, because a switch
hides which state is which. Category chips carry a quarantined dot with
the name always present. Save is full-width in the thumb arc and stays
disabled until the form is genuinely saveable, and errors are durable
text under the field rather than a toast."
```

---

### Task 6: Module as root, and the home-view setting

Plan B built the home-view preference and deliberately withheld its control: with no way back to the hub from a module, choosing Wallet as home would have locked the user out of Settings permanently. This closes that.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/wallet/WalletScreen.kt`
- Modify: `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/settings/SettingsScreen.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `PreferencesRepository.setHomeView`, `HomeView`, `SettingsViewModel.onHomeViewSelected` (all built in Plan B); `WalletScreen` from Task 2.
- Produces: `WalletScreen(onBack: (() -> Unit)?, onOpenHub: (() -> Unit)?, onOpenLedger: () -> Unit)` — a null `onBack` means this screen is the root.

- [ ] **Step 1: Give the Wallet screen a root mode**

In `WalletScreen.kt`, change both signatures so the nav row adapts:

```kotlin
@Composable
fun WalletScreen(
    onBack: (() -> Unit)?,
    onOpenHub: (() -> Unit)?,
    onOpenLedger: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    WalletContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onOpenHub = onOpenHub,
        onOpenLedger = onOpenLedger,
    )
}
```

And replace the nav `Row` in `WalletContent` with:

```kotlin
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
            // Back when this screen was pushed; a hub glyph when it is the root.
            // Without the second case, choosing Wallet as home would strand the
            // user: the settings gear lives only on the hub, and back from a
            // root exits the app.
            when {
                onBack != null -> NavCircle(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Back",
                    onClick = onBack,
                )
                onOpenHub != null -> NavCircle(
                    icon = Icons.Filled.GridView,
                    description = "All modules",
                    onClick = onOpenHub,
                )
            }
        }
```

Add this helper at the bottom of the file:

```kotlin
@Composable
private fun NavCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Box(
        Modifier.size(spacing.minTouchTarget).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

Add `import androidx.compose.material.icons.filled.GridView`.

- [ ] **Step 2: Wire both cases in navigation**

In `KhataNavHost.kt`, replace the Wallet destination:

```kotlin
        composable(KhataRoutes.Wallet) {
            val isRoot = homeView == HomeView.Wallet
            WalletScreen(
                // Back only exists when something pushed this screen. At the
                // root it would exit the app, which is not what a back arrow
                // promises.
                onBack = if (isRoot) null else ({ navController.popBackStack() }),
                onOpenHub = if (isRoot) ({ navController.navigate(KhataRoutes.Modules) }) else null,
                onOpenLedger = { navController.navigate(KhataRoutes.Ledger) },
            )
        }
```

- [ ] **Step 3: Write the failing settings test**

Replace the `thereIsNoHomeViewControlYet` test in `app/src/androidTest/java/com/wasif/khata/feature/settings/SettingsScreenTest.kt` with:

```kotlin
    @Test
    fun theHomeViewControlOffersBothRoots() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("HOME VIEW").assertIsDisplayed()
        compose.onNodeWithText("Modules").assertIsDisplayed()
        compose.onNodeWithText("Wallet").assertIsDisplayed()
    }
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.settings.SettingsScreenTest`
Expected: failure — no "HOME VIEW" section exists.

- [ ] **Step 5: Add the control**

In `SettingsScreen.kt`, insert directly above `Section("Monthly budget")`:

```kotlin
            Section("Home view")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                HomeView.entries.forEach { view ->
                    val selected = prefs.homeView == view
                    Box(
                        Modifier
                            .weight(1f)
                            .height(spacing.minTouchTarget)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            )
                            .clickable { viewModel.onHomeViewSelected(view) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = view.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
```

Change the header subline to match what the screen now offers:

```kotlin
            ContextHeader(heading = "Settings", subline = "Home · theme · budget")
```

Add `import com.wasif.khata.core.prefs.HomeView`.

- [ ] **Step 6: Update the Task 2 Wallet tests for the new signature**

`WalletContent` gained `onOpenHub`, so every call in
`app/src/androidTest/java/com/wasif/khata/feature/wallet/WalletScreenTest.kt` needs a third
lambda. Change each `WalletContent(state, {}, {})` to `WalletContent(state, {}, {}, {})`, and add
the test for the case this task exists to prevent:

```kotlin
    @Test
    fun asTheRootItOffersTheHubRatherThanBack() {
        compose.setContent {
            KhataTheme {
                // onBack null means this screen is the root. Without a hub glyph
                // here the user cannot reach Settings again, because the gear
                // lives only on the hub.
                WalletContent(WalletUiState(), null, {}, {})
            }
        }

        compose.onNodeWithContentDescription("All modules").assertIsDisplayed()
        val back = compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes()
        assertTrue("a root screen must not show a back arrow", back.isEmpty())
    }
```

Add `import androidx.compose.ui.test.onAllNodesWithContentDescription`. Note the argument order
is `(state, onBack, onOpenHub, onOpenLedger)`.

- [ ] **Step 7: Verify the loop by hand**

Install and check the thing the tests cannot: that choosing Wallet as home does not strand you.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.wasif.khata/.MainActivity
```

Then, on the device: Settings → Home view → **Wallet** → back → force-stop and relaunch. The app must open on Wallet, show a **grid glyph** rather than a back arrow, and that glyph must reach the hub. From the hub, Settings must be reachable again. **If any step fails, the user can be locked out of their own settings — stop and report rather than proceeding.**

```bash
adb shell am force-stop com.wasif.khata && adb shell am start -n com.wasif.khata/.MainActivity
```

- [ ] **Step 8: Run everything**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Expected: both `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/wallet \
        app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt \
        app/src/main/java/com/wasif/khata/feature/settings \
        app/src/androidTest/java/com/wasif/khata/feature/settings
git commit -m "feat: a module can be the app's root

Closes the item Plan B deliberately withheld. The home-view preference
existed and was tested there, but its control did not ship: with no way
back to the hub from a module, choosing Wallet as home would have locked
the user out of Settings permanently.

Wallet now shows a hub glyph instead of a back arrow when it is the
root -- back from a root exits the app, which is not what a back arrow
promises."
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` green.
- `./gradlew :app:connectedDebugAndroidTest` green.
- The Wallet dashboard shows net worth, the month's two figures, and account rows that name a reconciliation gap in words.
- The Ledger heading is the month, day headers carry totals, rows carry category dots, and low confidence reads as a ring plus a word.
- Searching filters the list; a search with no matches says so rather than claiming the app is empty.
- The Editor's amount is the largest thing on screen.
- Setting Wallet as home opens there, shows a hub glyph, and Settings stays reachable.

## What remains after this

Everything in this plan is the wallet module. The design spec's remaining screens — **Insights**, **Budgets**, **Accounts** and the full **Settings** surface (rule editor, categories, API key, backup, reparse, unmatched messages) — are Plan D and beyond, as are SMS ingestion and the Glance widget from the original product plan. The theme, navigation and component system they will be built on is now settled and proven.
