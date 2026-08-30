# Khata Plan B — Petrol App Shell

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Khata into a hub-and-back app — a Modules home carrying live wallet data, a settings screen with the full theme tuner, and preferences that load before the first frame.

**Architecture:** Preferences become the app's first async dependency, so the splash is held until DataStore's first emission — that solves the palette flash and the start-destination problem with one mechanism. The hub is a live status screen, not a launcher, so it needs real aggregates; those land first. The Ledger and Editor are deliberately left alone and rebuilt in Plan C.

**Tech Stack:** Kotlin · Jetpack Compose · Material 3 · Room · Hilt · DataStore Preferences · androidx.core splashscreen · JUnit4 + Robolectric + Turbine.

**Spec:** `docs/superpowers/specs/2026-08-28-khata-petrol-design.md`
**Depends on:** Plan A (`2026-08-28-khata-petrol-system.md`), complete.

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`, `compileSdk` / `targetSdk` `37`. Target device: Pixel 6a, Android 17.
- Package and namespace: `com.wasif.khata`.
- **No hardcoded colours.** Every colour resolves through a token from `KhataPalette` or `MaterialTheme.colorScheme`.
- Money is always `Long` **paisa**. Never `Double`, never `Float`.
- All day, month and period boundaries computed in `Asia/Dhaka`.
- No database access on the main thread. Repositories expose Flows.
- Repositories map platform exceptions to `DataError` at the boundary. Platform exceptions never leak.
- Amounts always render through `MoneyText` or `AmountTextStyle`.
- **The wordmark is খাতা only** — no Latin "Khata" anywhere in the interface.
- **The tagline is `সব হিসাব, এক খাতায়`** — the only other Bengali UI string permitted.
- Tests never hardcode a magic epoch-millis literal. Build instants with `Instant.parse("…Z").toEpochMilli()`.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- `export JAVA_HOME="/e/Android/Android Studio/jbr"` and `export PATH="$JAVA_HOME/bin:$PATH"` per shell.
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Instrumented: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` — this AGP does **not** accept `--tests`.
- Emulator: `ANDROID_AVD_HOME="D:\android-avd" $ANDROID_HOME/emulator/emulator.exe -avd khata_test -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect`

---

## Why this is Plan B and not all of the remaining work

The spec's §12 split the work in two. Scoping the second half against the code showed it is really three, because the module screens need data that does not exist yet:

- **This plan (B) — the shell.** Preferences, navigation, the hub, settings. Ends with an app you open onto খাতা and a working theme tuner.
- **Plan C — the module screens.** Wallet dashboard, Ledger rebuild, Editor rebuild, plus the net-worth and day-total aggregates they need.

Splitting here is not arbitrary: everything in B is reachable and testable without touching `feature/ledger` or `feature/editor`, and B is what makes the app *feel* like the design even while those two screens still look like Plan A left them.

## Three things the spec assumed that the code does not have

Found while scoping. All three are resolved in this plan rather than sent back.

**1. No aggregate queries exist.** `TransactionDao` has `pagingSource`, `observeById`, `findById`, `softDelete` and nothing else. The hub's wallet card needs month-to-date spend and the most recent transaction. Task 1 adds both.

**2. Budgets do not exist.** The concept shows a 63% budget ring, but there is no budget feature and building one is Plan D at the earliest. **Resolution:** one `monthlyBudgetMinor` value in preferences, editable in settings. That is a single number, not the Budgets feature — it makes the ring real without pretending to be more than it is. When the ring has no budget set, it is not drawn at all rather than showing a fake number.

**3. Restaurants and Watchlist do not exist.** The concept drew them live with "14 saved" and "31 items" to show the target state. Only Wallet exists. The hub ships **Wallet live plus four dormant cards**, per the design decision that unbuilt modules read as unbuilt rather than being hidden or faked.

## One control this plan deliberately does not ship

**The home-view setting is built but not exposed.** The preference, the repository method and the navigation all land here and are tested. The *control* waits for Plan C.

Spec §7 says that when a module is the root, its nav row carries a hub glyph top-left so the Modules hub stays reachable. That glyph belongs to the Ledger, and this plan does not touch `feature/ledger`. Shipping the setting without the glyph would let the user choose Wallet as their home and then be **permanently unable to reach Settings again** — the gear lives only on the hub, and back from a root exits the app. That is not a rough edge, it is a one-way door out of the app's own settings.

So `setHomeView` exists, works and is covered by tests; nothing in the UI calls it until the Ledger can carry the way back.

---

### Task 1: Month-to-date and most-recent aggregates

The hub is a live status screen rather than a launcher, which is the whole design argument for it. That requires real numbers, so they land before the screen does.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/time/KhataClock.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`
- Test: `app/src/test/java/com/wasif/khata/core/time/KhataClockTest.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`

**Interfaces:**
- Consumes: nothing from this plan.
- Produces:
  - `fun Long.dhakaMonthStart(): Long`
  - `fun Long.dhakaNextMonthStart(): Long`
  - `TransactionDao.observeTotalMinorBetween(direction: TransactionDirection, fromInclusive: Long, toExclusive: Long): Flow<Long>`
  - `TransactionDao.observeMostRecent(): Flow<TransactionEntity?>`
  - `TransactionRepository.observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money>`
  - `TransactionRepository.observeMostRecent(): Flow<Transaction?>`

- [ ] **Step 1: Write the failing clock test**

Append to `app/src/test/java/com/wasif/khata/core/time/KhataClockTest.kt`, inside the existing test class:

```kotlin
    @Test
    fun `month start is midnight Dhaka on the first`() {
        val mid = Instant.parse("2026-08-28T09:41:00Z").toEpochMilli()
        // Dhaka is UTC+6, so 1 August 00:00 Dhaka is 31 July 18:00 UTC.
        val expected = Instant.parse("2026-07-31T18:00:00Z").toEpochMilli()
        assertEquals(expected, mid.dhakaMonthStart())
    }

    @Test
    fun `next month start is exclusive and rolls the year`() {
        val dec = Instant.parse("2026-12-15T00:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-12-31T18:00:00Z").toEpochMilli()
        assertEquals(expected, dec.dhakaNextMonthStart())
    }

    @Test
    fun `an instant just before Dhaka midnight belongs to the previous month`() {
        // 31 August 23:59 Dhaka is 31 August 17:59 UTC -- still August, not
        // September. Computing this in UTC would put it in the wrong month.
        val lateAug = Instant.parse("2026-08-31T17:59:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-07-31T18:00:00Z").toEpochMilli(), lateAug.dhakaMonthStart())
    }
```

Ensure the file imports `java.time.Instant` and `org.junit.Assert.assertEquals`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.time.KhataClockTest"`
Expected: compilation failure — `Unresolved reference: dhakaMonthStart`.

- [ ] **Step 3: Add the month boundary helpers**

Append to `app/src/main/java/com/wasif/khata/core/time/KhataClock.kt`:

```kotlin
// Month boundaries are computed in Dhaka, never UTC. An instant at 23:59 Dhaka
// on the last of the month is 17:59 UTC the same day -- computing in UTC would
// still land in the right month here, but at 00:30 Dhaka on the first it would
// land in the previous one, and every month-to-date figure would be wrong for
// six hours a month.
fun Long.dhakaMonthStart(): Long =
    Instant.ofEpochMilli(this)
        .atZone(DHAKA)
        .toLocalDate()
        .withDayOfMonth(1)
        .atStartOfDay(DHAKA)
        .toInstant()
        .toEpochMilli()

fun Long.dhakaNextMonthStart(): Long =
    Instant.ofEpochMilli(this)
        .atZone(DHAKA)
        .toLocalDate()
        .withDayOfMonth(1)
        .plusMonths(1)
        .atStartOfDay(DHAKA)
        .toInstant()
        .toEpochMilli()
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.time.KhataClockTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing DAO test**

Append to the existing class in `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`. It already has a helper for building entities; if the helper is named differently, adapt the two calls below rather than duplicating it.

```kotlin
    @Test
    fun `observeTotalMinorBetween sums only debits inside the window`() = runTest {
        val inWindow = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()
        val before = Instant.parse("2026-07-10T06:00:00Z").toEpochMilli()

        dao.upsert(transaction(amountMinor = 2_340_50, direction = TransactionDirection.DEBIT, occurredAt = inWindow))
        dao.upsert(transaction(amountMinor = 42_000, direction = TransactionDirection.DEBIT, occurredAt = inWindow))
        // A credit in the window and a debit outside it must both be excluded.
        dao.upsert(transaction(amountMinor = 85_000_00, direction = TransactionDirection.CREDIT, occurredAt = inWindow))
        dao.upsert(transaction(amountMinor = 999_00, direction = TransactionDirection.DEBIT, occurredAt = before))

        val total = dao.observeTotalMinorBetween(
            direction = TransactionDirection.DEBIT,
            fromInclusive = inWindow.dhakaMonthStart(),
            toExclusive = inWindow.dhakaNextMonthStart(),
        ).first()

        assertEquals(2_340_50L + 42_000L, total)
    }

    @Test
    fun `observeTotalMinorBetween returns zero rather than null when empty`() = runTest {
        // COALESCE matters: a null here would crash Money.ofMinor at the
        // repository boundary on a brand new install, which is the one moment
        // the hub is guaranteed to be shown.
        val now = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()
        val total = dao.observeTotalMinorBetween(
            direction = TransactionDirection.DEBIT,
            fromInclusive = now.dhakaMonthStart(),
            toExclusive = now.dhakaNextMonthStart(),
        ).first()
        assertEquals(0L, total)
    }

    @Test
    fun `observeMostRecent ignores soft-deleted rows`() = runTest {
        val older = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()
        val newer = Instant.parse("2026-08-20T06:00:00Z").toEpochMilli()
        dao.upsert(transaction(merchantRaw = "Chaldal", occurredAt = older))
        val newestId = dao.upsert(transaction(merchantRaw = "Daraz", occurredAt = newer))

        assertEquals("Daraz", dao.observeMostRecent().first()?.merchantRaw)

        dao.softDelete(newestId, deletedAt = newer)
        assertEquals("Chaldal", dao.observeMostRecent().first()?.merchantRaw)
    }
```

Ensure the file imports `com.wasif.khata.core.time.dhakaMonthStart`, `com.wasif.khata.core.time.dhakaNextMonthStart`, `kotlinx.coroutines.flow.first` and `java.time.Instant`.

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.data.TransactionDaoTest"`
Expected: compilation failure — `Unresolved reference: observeTotalMinorBetween`.

- [ ] **Step 7: Add the DAO queries**

Add to `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`:

```kotlin
    // COALESCE, because SUM over no rows is NULL and this runs on an empty
    // database every first launch.
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE deletedAt IS NULL
          AND direction = :direction
          AND occurredAt >= :fromInclusive
          AND occurredAt < :toExclusive
        """,
    )
    fun observeTotalMinorBetween(
        direction: TransactionDirection,
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<Long>

    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC LIMIT 1",
    )
    fun observeMostRecent(): Flow<TransactionEntity?>
```

Ensure the file imports `com.wasif.khata.core.model.TransactionDirection`. Room stores the enum as TEXT natively, so it binds without a converter.

- [ ] **Step 8: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.data.TransactionDaoTest"`
Expected: PASS.

- [ ] **Step 9: Extend the repository**

Add to the `TransactionRepository` interface in `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`:

```kotlin
    fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money>
    fun observeMostRecent(): Flow<Transaction?>
```

And implement in `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`, alongside the existing methods:

```kotlin
    override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
        transactionDao.observeTotalMinorBetween(
            direction = TransactionDirection.DEBIT,
            fromInclusive = fromInclusive,
            toExclusive = toExclusive,
        ).map { Money(it) }

    override fun observeMostRecent(): Flow<Transaction?> =
        transactionDao.observeMostRecent().map { it?.toDomain() }
```

`Money` is a value class over `minor: Long`, so `Money(it)` is correct. The file already imports `map` and the `toDomain()` mapper used by `observe(id)`.

- [ ] **Step 10: Run the whole suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all green.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/time/KhataClock.kt \
        app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt \
        app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt \
        app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt \
        app/src/test/java/com/wasif/khata/core/time/KhataClockTest.kt \
        app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt
git commit -m "feat: month-to-date and most-recent aggregates

The hub is a live status screen rather than a launcher, which is the
whole design argument for it -- so it needs real numbers before it
needs a layout.

Month boundaries are computed in Dhaka. Computing them in UTC would put
the first six hours of every month in the previous one."
```

---

### Task 2: Preferences on DataStore

Three values, one store: the four theme axes, the start destination, and a monthly budget. Indices are clamped on read, because a stored index outliving a palette edit must degrade to the default rather than crash on first launch.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/wasif/khata/core/prefs/KhataPreferences.kt`
- Create: `app/src/main/java/com/wasif/khata/core/prefs/PreferencesRepository.kt`
- Create: `app/src/main/java/com/wasif/khata/core/prefs/PreferencesRepositoryImpl.kt`
- Create: `app/src/main/java/com/wasif/khata/core/prefs/di/PreferencesModule.kt`
- Test: `app/src/test/java/com/wasif/khata/core/prefs/PreferencesRepositoryTest.kt`

**Interfaces:**
- Consumes: `ThemeSpec`, `FieldIntensity`, `KhataPalette` from Plan A.
- Produces:
  - `enum class HomeView { Modules, Wallet }`
  - `data class KhataPreferences(themeSpec: ThemeSpec, homeView: HomeView, monthlyBudgetMinor: Long?)` with `companion object { val Default: KhataPreferences }`
  - `interface PreferencesRepository` with `val preferences: Flow<KhataPreferences>`, `suspend fun setTheme(spec: ThemeSpec)`, `suspend fun resetTheme()`, `suspend fun setHomeView(view: HomeView)`, `suspend fun setMonthlyBudget(minor: Long?)`

- [ ] **Step 1: Add the DataStore dependency**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
datastore = "1.1.7"
```

To `[libraries]`:

```toml
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
```

In `app/build.gradle.kts`, add to `dependencies`:

```kotlin
  implementation(libs.androidx.datastore.preferences)
```

Verify it resolves before writing code against it:

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/wasif/khata/core/prefs/PreferencesRepositoryTest.kt`:

```kotlin
package com.wasif.khata.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PreferencesRepositoryTest {

    private lateinit var dir: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var repo: PreferencesRepository

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "khata-prefs-${System.nanoTime()}")
        dir.mkdirs()
        store = PreferenceDataStoreFactory.create(scope = TestScope()) { File(dir, "test.preferences_pb") }
        repo = PreferencesRepositoryImpl(store)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `an empty store yields the default`() = runTest {
        assertEquals(KhataPreferences.Default, repo.preferences.first())
    }

    @Test
    fun `a stored theme round-trips`() = runTest {
        val spec = ThemeSpec(
            field = KhataPalette.fields[3],
            ground = KhataPalette.grounds[2],
            accent = KhataPalette.accents[1],
            intensity = FieldIntensity.Dim,
        )
        repo.setTheme(spec)
        assertEquals(spec, repo.preferences.first().themeSpec)
    }

    @Test
    fun `reset restores the default theme without touching the other settings`() = runTest {
        repo.setTheme(ThemeSpec.Default.copy(intensity = FieldIntensity.Off))
        repo.setHomeView(HomeView.Wallet)
        repo.setMonthlyBudget(75_000_00)

        repo.resetTheme()

        val p = repo.preferences.first()
        assertEquals(ThemeSpec.Default, p.themeSpec)
        assertEquals(HomeView.Wallet, p.homeView)
        assertEquals(75_000_00L, p.monthlyBudgetMinor)
    }

    @Test
    fun `an index left over from an older palette falls back instead of crashing`() = runTest {
        // A stored index can outlive the list it pointed into -- a palette edit
        // is enough. On first launch that would throw before any UI exists to
        // report it, so it has to clamp rather than crash.
        store.edit { it[intPreferencesKey("theme_field")] = 999 }
        assertEquals(ThemeSpec.Default.field, repo.preferences.first().themeSpec.field)
    }

    @Test
    fun `no budget is null rather than zero`() = runTest {
        // Zero is a real budget the user could set; "unset" has to be a
        // different value or the ring cannot know whether to draw itself.
        assertNull(repo.preferences.first().monthlyBudgetMinor)
        repo.setMonthlyBudget(0)
        assertEquals(0L, repo.preferences.first().monthlyBudgetMinor)
        repo.setMonthlyBudget(null)
        assertNull(repo.preferences.first().monthlyBudgetMinor)
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.prefs.PreferencesRepositoryTest"`
Expected: compilation failure — `Unresolved reference: KhataPreferences`.

- [ ] **Step 4: Write the model**

Create `app/src/main/java/com/wasif/khata/core/prefs/KhataPreferences.kt`:

```kotlin
package com.wasif.khata.core.prefs

import com.wasif.khata.core.ui.theme.ThemeSpec

/** Which screen the app opens onto. The start destination is the back-stack root. */
enum class HomeView { Modules, Wallet }

data class KhataPreferences(
    val themeSpec: ThemeSpec,
    val homeView: HomeView,
    /** Null means no budget set, which is different from a budget of zero. */
    val monthlyBudgetMinor: Long?,
) {
    companion object {
        val Default = KhataPreferences(
            themeSpec = ThemeSpec.Default,
            homeView = HomeView.Modules,
            monthlyBudgetMinor = null,
        )
    }
}
```

- [ ] **Step 5: Write the repository interface**

Create `app/src/main/java/com/wasif/khata/core/prefs/PreferencesRepository.kt`:

```kotlin
package com.wasif.khata.core.prefs

import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<KhataPreferences>
    suspend fun setTheme(spec: ThemeSpec)
    suspend fun resetTheme()
    suspend fun setHomeView(view: HomeView)
    suspend fun setMonthlyBudget(minor: Long?)
}
```

- [ ] **Step 6: Write the implementation**

Create `app/src/main/java/com/wasif/khata/core/prefs/PreferencesRepositoryImpl.kt`:

```kotlin
package com.wasif.khata.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : PreferencesRepository {

    private object Keys {
        val Field = intPreferencesKey("theme_field")
        val Ground = intPreferencesKey("theme_ground")
        val Accent = intPreferencesKey("theme_accent")
        val Intensity = stringPreferencesKey("theme_intensity")
        val Home = stringPreferencesKey("home_view")
        val Budget = longPreferencesKey("monthly_budget_minor")
        val BudgetSet = intPreferencesKey("monthly_budget_set")
    }

    override val preferences: Flow<KhataPreferences> = store.data
        // A corrupt or unreadable store must not take the app down before any
        // UI exists to report it. Defaults are always a valid answer here.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            KhataPreferences(
                themeSpec = ThemeSpec(
                    field = KhataPalette.fields.getOrElse(p[Keys.Field] ?: -1) { ThemeSpec.Default.field },
                    ground = KhataPalette.grounds.getOrElse(p[Keys.Ground] ?: -1) { ThemeSpec.Default.ground },
                    accent = KhataPalette.accents.getOrElse(p[Keys.Accent] ?: -1) { ThemeSpec.Default.accent },
                    intensity = p[Keys.Intensity]
                        ?.let { name -> FieldIntensity.entries.firstOrNull { it.name == name } }
                        ?: ThemeSpec.Default.intensity,
                ),
                homeView = p[Keys.Home]
                    ?.let { name -> HomeView.entries.firstOrNull { it.name == name } }
                    ?: HomeView.Modules,
                // Two keys, because null and zero are different answers and a
                // single Long cannot carry both.
                monthlyBudgetMinor = if (p[Keys.BudgetSet] == 1) p[Keys.Budget] ?: 0L else null,
            )
        }

    override suspend fun setTheme(spec: ThemeSpec) {
        store.edit { p ->
            p[Keys.Field] = KhataPalette.fields.indexOf(spec.field)
            p[Keys.Ground] = KhataPalette.grounds.indexOf(spec.ground)
            p[Keys.Accent] = KhataPalette.accents.indexOf(spec.accent)
            p[Keys.Intensity] = spec.intensity.name
        }
    }

    override suspend fun resetTheme() {
        store.edit { p ->
            p.remove(Keys.Field)
            p.remove(Keys.Ground)
            p.remove(Keys.Accent)
            p.remove(Keys.Intensity)
        }
    }

    override suspend fun setHomeView(view: HomeView) {
        store.edit { it[Keys.Home] = view.name }
    }

    override suspend fun setMonthlyBudget(minor: Long?) {
        store.edit { p ->
            if (minor == null) {
                p.remove(Keys.Budget)
                p[Keys.BudgetSet] = 0
            } else {
                p[Keys.Budget] = minor
                p[Keys.BudgetSet] = 1
            }
        }
    }
}
```

- [ ] **Step 7: Wire Hilt**

Create `app/src/main/java/com/wasif/khata/core/prefs/di/PreferencesModule.kt`:

```kotlin
package com.wasif.khata.core.prefs.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.prefs.PreferencesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object PreferencesStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) {
            context.preferencesDataStoreFile("khata_settings")
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesBindingModule {

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.prefs.PreferencesRepositoryTest"`
Expected: PASS, 5 tests.

- [ ] **Step 9: Run the whole suite**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: both `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/wasif/khata/core/prefs app/src/test/java/com/wasif/khata/core/prefs
git commit -m "feat: preferences on DataStore

Four theme axes, a start destination, and a monthly budget. Indices are
clamped on read: a stored index can outlive the list it pointed into,
and on first launch that would throw before any UI exists to report it.

The budget uses two keys because null and zero are different answers --
zero is a real budget a user could set, and the ring needs to know
which it is looking at."
```

---

### Task 3: Hold the splash until preferences load

One mechanism solves two problems: the palette must not flash a default, and the start destination *is* the back-stack root so it must be known before `NavHost` composes.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/themes.xml` (create if absent)
- Create: `app/src/main/java/com/wasif/khata/MainViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/MainActivity.kt`
- Test: `app/src/test/java/com/wasif/khata/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `PreferencesRepository`, `KhataPreferences` (Task 2).
- Produces: `MainViewModel` exposing `val state: StateFlow<MainUiState>`; `sealed interface MainUiState { data object Loading; data class Ready(val preferences: KhataPreferences) }`

- [ ] **Step 1: Add the splashscreen dependency**

In `gradle/libs.versions.toml`, `[versions]`:

```toml
coreSplashscreen = "1.0.1"
```

`[libraries]`:

```toml
androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "coreSplashscreen" }
```

`app/build.gradle.kts` dependencies:

```kotlin
  implementation(libs.androidx.core.splashscreen)
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/wasif/khata/MainViewModelTest.kt`:

```kotlin
package com.wasif.khata

import app.cash.turbine.test
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val flow = MutableStateFlow<KhataPreferences?>(null)

    private val repo = object : PreferencesRepository {
        override val preferences: Flow<KhataPreferences> =
            kotlinx.coroutines.flow.flow { flow.collect { if (it != null) emit(it) } }
        override suspend fun setTheme(spec: ThemeSpec) = Unit
        override suspend fun resetTheme() = Unit
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts loading and becomes ready on the first emission`() = runTest(dispatcher) {
        val vm = MainViewModel(repo)
        vm.state.test {
            assertEquals(MainUiState.Loading, awaitItem())

            val prefs = KhataPreferences.Default.copy(
                themeSpec = ThemeSpec.Default.copy(intensity = FieldIntensity.Dim),
                homeView = HomeView.Wallet,
            )
            flow.value = prefs

            assertEquals(MainUiState.Ready(prefs), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.MainViewModelTest"`
Expected: compilation failure — `Unresolved reference: MainViewModel`.

- [ ] **Step 4: Write the ViewModel**

Create `app/src/main/java/com/wasif/khata/MainViewModel.kt`:

```kotlin
package com.wasif.khata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val preferences: KhataPreferences) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    repository: PreferencesRepository,
) : ViewModel() {

    // Loading is a real state, not a formality. The theme and the start
    // destination both come from DataStore, and painting either one wrong for
    // a frame is visible: the palette flashes, and the start destination is
    // the back-stack root so it cannot be corrected after the fact.
    val state: StateFlow<MainUiState> = repository.preferences
        .map<KhataPreferences, MainUiState> { MainUiState.Ready(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MainUiState.Loading,
        )
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.MainViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Add the splash theme**

Create or edit `app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- The splash window paints the app's ground, so the hold between process
         start and the first composed frame is invisible rather than a flash of
         white. -->
    <style name="Theme.Khata.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#061214</item>
        <item name="postSplashScreenTheme">@style/Theme.Khata</item>
    </style>

    <style name="Theme.Khata" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">#061214</item>
    </style>
</resources>
```

Then point the manifest at it. In `app/src/main/AndroidManifest.xml`, set the activity (or application) `android:theme` to `@style/Theme.Khata.Starting`.

- [ ] **Step 7: Wire MainActivity**

Replace `app/src/main/java/com/wasif/khata/MainActivity.kt`:

```kotlin
package com.wasif.khata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.navigation.KhataNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Held until the first DataStore emission. Both the palette and the
        // start destination come from there, and the start destination is the
        // back-stack root -- it cannot be corrected once NavHost has composed.
        splash.setKeepOnScreenCondition { viewModel.state.value is MainUiState.Loading }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            when (val state = viewModel.state.collectAsStateWithLifecycle().value) {
                MainUiState.Loading -> Unit
                is MainUiState.Ready -> KhataTheme(spec = state.preferences.themeSpec) {
                    KhataNavHost(homeView = state.preferences.homeView)
                }
            }
        }
    }
}
```

`KhataNavHost` does not take `homeView` yet — Task 4 adds it, and the build stays red between these two tasks. That is deliberate: splitting them lets a reviewer reject the navigation model without rejecting the splash fix.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/res/values/themes.xml \
        app/src/main/AndroidManifest.xml app/src/main/java/com/wasif/khata/MainViewModel.kt \
        app/src/main/java/com/wasif/khata/MainActivity.kt \
        app/src/test/java/com/wasif/khata/MainViewModelTest.kt
git commit -m "feat: hold the splash until preferences load

One mechanism, two problems. The palette must not flash a default, and
the start destination is the back-stack root so it has to be known
before NavHost composes rather than corrected afterwards.

The splash window paints the app's ground, so the hold is invisible
rather than a flash of white.

Build is red until Task 4 gives KhataNavHost its homeView parameter."
```

---

### Task 4: Hub-and-back navigation

No bottom bar. The start destination is a preference, and when a module is the root the hub becomes reachable by a glyph rather than by climbing back.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/navigation/KhataNavHostTest.kt`

**Interfaces:**
- Consumes: `HomeView` (Task 2).
- Produces:
  - `object KhataRoutes { const val Modules; const val Wallet; const val Ledger; const val Settings; const val EditorNew; const val EditorEdit; fun editorEdit(id: Long): String }`
  - `KhataNavHost(homeView: HomeView)`

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/wasif/khata/navigation/KhataNavHostTest.kt`:

```kotlin
package com.wasif.khata.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class KhataNavHostTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensOnTheModulesHubByDefault() {
        compose.onNodeWithText("খাতা").assertIsDisplayed()
    }

    @Test
    fun theSettingsGearReachesSettingsAndBackReturns() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("খাতা").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.navigation.KhataNavHostTest`
Expected: failure — the hub does not exist and `KhataNavHost` does not accept `homeView`.

- [ ] **Step 3: Write the navigation host**

Replace `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`:

```kotlin
package com.wasif.khata.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.feature.editor.TransactionEditorScreen
import com.wasif.khata.feature.editor.TransactionEditorViewModel
import com.wasif.khata.feature.hub.ModulesScreen
import com.wasif.khata.feature.ledger.LedgerScreen
import com.wasif.khata.feature.settings.SettingsScreen

object KhataRoutes {
    const val Modules = "modules"
    const val Ledger = "ledger"
    const val Settings = "settings"
    const val EditorNew = "editor/new"
    const val EditorEdit = "editor/edit/{transactionId}"
    const val ArgTransactionId = "transactionId"

    fun editorEdit(id: Long): String = "editor/edit/$id"
}

@Composable
fun KhataNavHost(homeView: HomeView) {
    val navController = rememberNavController()

    // The preference IS the back-stack root, which is why it has to be resolved
    // before this composes: back from the root exits the app, and that cannot
    // be changed after the graph is built.
    val start = when (homeView) {
        HomeView.Modules -> KhataRoutes.Modules
        HomeView.Wallet -> KhataRoutes.Ledger
    }

    NavHost(navController = navController, startDestination = start) {
        composable(KhataRoutes.Modules) {
            ModulesScreen(
                onOpenWallet = { navController.navigate(KhataRoutes.Ledger) },
                onOpenSettings = { navController.navigate(KhataRoutes.Settings) },
            )
        }

        composable(KhataRoutes.Ledger) {
            LedgerScreen(
                onAddTransaction = { navController.navigate(KhataRoutes.EditorNew) },
                onOpenTransaction = { id -> navController.navigate(KhataRoutes.editorEdit(id)) },
            )
        }

        composable(KhataRoutes.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(KhataRoutes.EditorNew) {
            TransactionEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = editorViewModel(transactionId = null),
            )
        }

        composable(
            route = KhataRoutes.EditorEdit,
            arguments = listOf(navArgument(KhataRoutes.ArgTransactionId) { type = NavType.LongType }),
        ) { entry ->
            TransactionEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = editorViewModel(entry.arguments?.getLong(KhataRoutes.ArgTransactionId)),
            )
        }
    }
}

@Composable
private fun editorViewModel(transactionId: Long?): TransactionEditorViewModel =
    hiltViewModel<TransactionEditorViewModel, TransactionEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(transactionId) },
    )
```

`HomeView.Wallet` routes to the Ledger for now, because the Wallet dashboard is Plan C. The preference and the routing are correct; only the destination screen is provisional.

- [ ] **Step 4: Commit**

The test stays red until Tasks 5 and 6 supply `ModulesScreen` and `SettingsScreen`. Commit the routing on its own so the navigation model is reviewable separately.

```bash
git add app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt app/src/androidTest/java/com/wasif/khata/navigation/KhataNavHostTest.kt
git commit -m "feat: hub-and-back navigation with a configurable root

No bottom bar. The start destination comes from preferences and IS the
back-stack root -- back from it exits the app, which is why it has to be
resolved before the graph is built.

HomeView.Wallet routes to the Ledger until Plan C builds the Wallet
dashboard. The preference and routing are correct; the destination is
provisional."
```

---

### Task 5: The Modules hub

The default home. Air at the top with খাতা centred in it, module cards anchored into the thumb arc, and a settings gear in the one top corner a rare control is allowed to occupy.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/hub/ModulesUiState.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/hub/ModulesViewModel.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/hub/ModulesScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/hub/ModulesViewModelTest.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/hub/ModulesScreenTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository.observeSpentBetween`, `observeMostRecent` (Task 1); `PreferencesRepository` (Task 2); `KhataGlass`, `ContextHeader`, `MoneyText`, `WordmarkTextStyle` (Plan A).
- Produces: `ModulesScreen(onOpenWallet: () -> Unit, onOpenSettings: () -> Unit)`; `data class ModulesUiState(monthSpend: Money, budgetFraction: Float?, lastTransaction: Transaction?)`

- [ ] **Step 1: Write the failing ViewModel test**

Create `app/src/test/java/com/wasif/khata/feature/hub/ModulesViewModelTest.kt`:

```kotlin
package com.wasif.khata.feature.hub

import app.cash.turbine.test
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.ui.theme.ThemeSpec
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ModulesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val spend = MutableStateFlow(Money(0))
    private val prefs = MutableStateFlow(KhataPreferences.Default)

    private val clock = object : KhataClock {
        override fun now(): Long = Instant.parse("2026-08-28T09:41:00Z").toEpochMilli()
    }

    private val transactions = object : TransactionRepository {
        override fun pagedTransactions() = throw UnsupportedOperationException()
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: com.wasif.khata.domain.repository.TransactionDraft) =
            throw UnsupportedOperationException()
        override suspend fun delete(id: Long) = throw UnsupportedOperationException()
        override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> = spend
        override fun observeMostRecent(): Flow<Transaction?> = flowOf(null)
    }

    private val preferences = object : PreferencesRepository {
        override val preferences: Flow<KhataPreferences> = prefs
        override suspend fun setTheme(spec: ThemeSpec) = Unit
        override suspend fun resetTheme() = Unit
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `month spend comes from the Dhaka month window`() = runTest(dispatcher) {
        spend.value = Money(47_382_50)
        val vm = ModulesViewModel(transactions, preferences, clock)
        vm.state.test {
            assertEquals(Money(47_382_50), awaitItem().monthSpend)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the budget ring is absent when no budget is set`() = runTest(dispatcher) {
        // A ring drawn against an unset budget would be showing a number the
        // user never entered. Absent is the honest state.
        spend.value = Money(47_382_50)
        val vm = ModulesViewModel(transactions, preferences, clock)
        vm.state.test {
            assertNull(awaitItem().budgetFraction)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the budget fraction is spend over budget, clamped at one`() = runTest(dispatcher) {
        spend.value = Money(75_000_00)
        prefs.value = KhataPreferences.Default.copy(monthlyBudgetMinor = 50_000_00)
        val vm = ModulesViewModel(transactions, preferences, clock)
        vm.state.test {
            // Overspending is real and must show as a full ring, never as 150%
            // of a circle.
            assertEquals(1f, awaitItem().budgetFraction)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a zero budget does not divide by zero`() = runTest(dispatcher) {
        spend.value = Money(1_000_00)
        prefs.value = KhataPreferences.Default.copy(monthlyBudgetMinor = 0)
        val vm = ModulesViewModel(transactions, preferences, clock)
        vm.state.test {
            assertEquals(1f, awaitItem().budgetFraction)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.feature.hub.ModulesViewModelTest"`
Expected: compilation failure — `Unresolved reference: ModulesViewModel`.

- [ ] **Step 3: Write the state**

Create `app/src/main/java/com/wasif/khata/feature/hub/ModulesUiState.kt`:

```kotlin
package com.wasif.khata.feature.hub

import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Transaction

data class ModulesUiState(
    val monthSpend: Money = Money(0),
    /** Null when no budget is set — the ring is not drawn rather than drawn at zero. */
    val budgetFraction: Float? = null,
    val lastTransaction: Transaction? = null,
)
```

- [ ] **Step 4: Write the ViewModel**

Create `app/src/main/java/com/wasif/khata/feature/hub/ModulesViewModel.kt`:

```kotlin
package com.wasif.khata.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ModulesViewModel @Inject constructor(
    transactions: TransactionRepository,
    preferences: PreferencesRepository,
    clock: KhataClock,
) : ViewModel() {

    private val now = clock.now()

    val state: StateFlow<ModulesUiState> = combine(
        transactions.observeSpentBetween(now.dhakaMonthStart(), now.dhakaNextMonthStart()),
        transactions.observeMostRecent(),
        preferences.preferences,
    ) { spend, last, prefs ->
        val budget = prefs.monthlyBudgetMinor
        ModulesUiState(
            monthSpend = spend,
            budgetFraction = when {
                budget == null -> null
                // Any spend against a zero budget is over it. Dividing would
                // produce infinity and the ring would refuse to draw.
                budget <= 0L -> 1f
                else -> (spend.minor.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
            },
            lastTransaction = last,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModulesUiState(),
    )
}
```

`Money` is a value class over `minor: Long`, so `spend.minor` is correct.

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.feature.hub.ModulesViewModelTest"`
Expected: PASS, 4 tests.

- [ ] **Step 6: Write the screen**

Create `app/src/main/java/com/wasif/khata/feature/hub/ModulesScreen.kt`:

```kotlin
package com.wasif.khata.feature.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.khataFieldSource
import com.wasif.khata.core.ui.component.rememberKhataHazeState
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.LocalThemeSpec
import com.wasif.khata.core.ui.theme.WordmarkTextStyle

@Composable
fun ModulesScreen(
    onOpenWallet: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ModulesViewModel = hiltViewModel(),
) {
    ModulesContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onOpenWallet = onOpenWallet,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun ModulesContent(
    state: ModulesUiState,
    onOpenWallet: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val spec = LocalThemeSpec.current
    val haze = rememberKhataHazeState()

    Box(Modifier.fillMaxSize().background(spec.ground)) {
        // The field is the backdrop the glass samples. It is a static surface,
        // which is why glass is affordable here and forbidden on a Paging list.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            spec.field.keyStop.copy(alpha = spec.intensity.alpha),
                            spec.ground,
                        ),
                    ),
                )
                .khataFieldSource(haze),
        )

        // enableEdgeToEdge draws behind the system bars, so the content insets
        // itself or the wordmark sits under the status bar.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            // Settings earns a top corner because the thumb-arc rule governs
            // routine controls, and this is the least-used destination there is.
            Row(Modifier.fillMaxWidth().padding(spacing.md), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .size(spacing.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Air at the top with the wordmark centred in it; the modules
            // anchored below. On a taller device the air grows, never the card.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "খাতা",
                    style = WordmarkTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "সব হিসাব, এক খাতায়",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = spacing.md),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal)
                    .padding(bottom = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                WalletCard(state = state, onClick = onOpenWallet)
                DormantRow(haze = haze, left = "Restaurants", right = "Watchlist")
                DormantRow(haze = haze, left = "Notes", right = "Car service")
            }
        }
    }
}

@Composable
private fun WalletCard(state: ModulesUiState, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(KhataPalette.heroStops))
            .clickable(onClick = onClick)
            .padding(spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "WALLET",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyText(
                    money = state.monthSpend,
                    direction = null,
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(top = spacing.sm),
                )
                Text(
                    text = "spent this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                state.lastTransaction?.let { last ->
                    Text(
                        text = "Last · ${last.merchantRaw ?: "Uncategorized"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                }
            }

            // Absent, not zero, when no budget is set. A ring drawn against a
            // budget the user never entered is a number the app invented.
            state.budgetFraction?.let { fraction -> BudgetRing(fraction = fraction) }
        }
    }
}

@Composable
private fun BudgetRing(fraction: Float) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val value = MaterialTheme.colorScheme.primary
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = value,
                // -90 so the ring starts at twelve o'clock rather than three.
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${(fraction * 100).toInt()}",
            style = MaterialTheme.typography.labelLarge,
            color = value,
        )
    }
}

@Composable
private fun DormantRow(
    haze: dev.chrisbanes.haze.HazeState,
    left: String,
    right: String,
) {
    val spacing = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        listOf(left, right).forEach { name ->
            KhataGlass(
                hazeState = haze,
                modifier = Modifier.weight(1f).height(96.dp),
            ) {
                // Unbuilt modules read as unbuilt. Hiding them would make the
                // hub a launcher with one tile; faking data would be worse.
                Column(Modifier.fillMaxSize().padding(spacing.md), verticalArrangement = Arrangement.Bottom) {
                    Text(
                        text = name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = "Not built",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

```

- [ ] **Step 7: Write the screen test**

Create `app/src/androidTest/java/com/wasif/khata/feature/hub/ModulesScreenTest.kt`:

```kotlin
package com.wasif.khata.feature.hub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModulesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theWordmarkIsBengaliAndNeverTransliterated() {
        compose.setContent {
            KhataTheme {
                ModulesContent(ModulesUiState(monthSpend = Money(47_382_50)), {}, {})
            }
        }
        compose.onNodeWithText("খাতা").assertIsDisplayed()
        compose.onNodeWithText("সব হিসাব, এক খাতায়").assertIsDisplayed()
        // "Khata" in Latin must appear nowhere in the interface.
        compose.onAllNodesWithText("Khata").fetchSemanticsNodes().let {
            assertTrue("Latin 'Khata' is on screen", it.isEmpty())
        }
    }

    @Test
    fun unbuiltModulesSaySoRatherThanShowingFakeData() {
        compose.setContent {
            KhataTheme { ModulesContent(ModulesUiState(), {}, {}) }
        }
        compose.onNodeWithText("RESTAURANTS").assertIsDisplayed()
        compose.onAllNodesWithText("Not built").fetchSemanticsNodes().let {
            assertTrue("dormant modules must be labelled", it.isNotEmpty())
        }
    }

    @Test
    fun theSettingsGearIsReachable() {
        var opened = false
        compose.setContent {
            KhataTheme { ModulesContent(ModulesUiState(), {}, { opened = true }) }
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        assertTrue(opened)
    }
}
```

Add `import androidx.compose.ui.test.onAllNodesWithText` to the file.

- [ ] **Step 8: Run the screen test**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.hub.ModulesScreenTest`
Expected: PASS, 3 tests.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/hub app/src/test/java/com/wasif/khata/feature/hub app/src/androidTest/java/com/wasif/khata/feature/hub
git commit -m "feat: the Modules hub

Air at the top with খাতা centred in it, module cards anchored into the
thumb arc. The wallet card carries live month-to-date, so the glance job
is answered without entering anything -- which is the difference between
a status screen and a launcher.

Unbuilt modules read as unbuilt. The budget ring is absent rather than
zero when no budget is set: drawing it would show a number the user
never entered."
```

---

### Task 6: Settings and the theme tuner

Four free axes and a reset. The user chose this over a preset list knowing the trade; the pairwise contrast suite from Plan A is what makes it safe.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/settings/SettingsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `PreferencesRepository` (Task 2); `KhataPalette`, `ThemeSpec`, `FieldIntensity` (Plan A).
- Produces: `SettingsScreen(onBack: () -> Unit)`

- [ ] **Step 1: Write the failing ViewModel test**

Create `app/src/test/java/com/wasif/khata/feature/settings/SettingsViewModelTest.kt`:

```kotlin
package com.wasif.khata.feature.settings

import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val prefs = MutableStateFlow(KhataPreferences.Default)
    private var lastSaved: ThemeSpec? = null
    private var resetCalled = false

    private val repo = object : PreferencesRepository {
        override val preferences: Flow<KhataPreferences> = prefs
        override suspend fun setTheme(spec: ThemeSpec) { lastSaved = spec }
        override suspend fun resetTheme() { resetCalled = true }
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `changing one axis preserves the other three`() = runTest(dispatcher) {
        prefs.value = KhataPreferences.Default.copy(
            themeSpec = ThemeSpec.Default.copy(intensity = FieldIntensity.Dim),
        )
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()

        vm.onAccentSelected(KhataPalette.accents[2])
        advanceUntilIdle()

        assertEquals(KhataPalette.accents[2], lastSaved?.accent)
        // The tuner edits one axis at a time; the rest must survive untouched.
        assertEquals(FieldIntensity.Dim, lastSaved?.intensity)
        assertEquals(ThemeSpec.Default.field, lastSaved?.field)
    }

    @Test
    fun `reset delegates rather than writing the default itself`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.onResetTheme()
        advanceUntilIdle()
        // Reset must clear the stored keys, not write today's default as an
        // explicit value -- otherwise a later change of default is invisible to
        // anyone who ever pressed reset.
        assertEquals(true, resetCalled)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.feature.settings.SettingsViewModelTest"`
Expected: compilation failure — `Unresolved reference: SettingsViewModel`.

- [ ] **Step 3: Write the ViewModel**

Create `app/src/main/java/com/wasif/khata/feature/settings/SettingsViewModel.kt`:

```kotlin
package com.wasif.khata.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.FieldPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PreferencesRepository,
) : ViewModel() {

    val state: StateFlow<KhataPreferences> = repository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KhataPreferences.Default,
    )

    // Each setter edits one axis and carries the other three forward. The
    // tuner is four independent controls over one value.
    fun onFieldSelected(field: FieldPalette) = save { it.copy(field = field) }
    fun onGroundSelected(ground: Color) = save { it.copy(ground = ground) }
    fun onAccentSelected(accent: Color) = save { it.copy(accent = accent) }
    fun onIntensitySelected(intensity: FieldIntensity) = save { it.copy(intensity = intensity) }

    fun onResetTheme() = viewModelScope.launch { repository.resetTheme() }

    fun onHomeViewSelected(view: HomeView) = viewModelScope.launch { repository.setHomeView(view) }

    fun onMonthlyBudgetChanged(minor: Long?) = viewModelScope.launch {
        repository.setMonthlyBudget(minor)
    }

    private fun save(edit: (com.wasif.khata.core.ui.theme.ThemeSpec) -> com.wasif.khata.core.ui.theme.ThemeSpec) =
        viewModelScope.launch { repository.setTheme(edit(state.value.themeSpec)) }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.feature.settings.SettingsViewModelTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Write the screen**

Create `app/src/main/java/com/wasif/khata/feature/settings/SettingsScreen.kt`:

```kotlin
package com.wasif.khata.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs = viewModel.state.collectAsStateWithLifecycle().value
    val spacing = LocalSpacing.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(Modifier.fillMaxWidth().padding(spacing.md)) {
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

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = spacing.xxl),
        ) {
            ContextHeader(heading = "Settings", subline = "Theme and budget")

            Section("Monthly budget")
            MonthlyBudgetField(
                current = prefs.monthlyBudgetMinor,
                onChange = viewModel::onMonthlyBudgetChanged,
            )

            Section("Field")
            SwatchGrid(
                colours = KhataPalette.fields.map { it.keyStop },
                selectedIndex = KhataPalette.fields.indexOf(prefs.themeSpec.field),
                onSelect = { viewModel.onFieldSelected(KhataPalette.fields[it]) },
            )

            Section("Ground")
            SwatchGrid(
                colours = KhataPalette.grounds,
                selectedIndex = KhataPalette.grounds.indexOf(prefs.themeSpec.ground),
                onSelect = { viewModel.onGroundSelected(KhataPalette.grounds[it]) },
            )

            Section("Accent")
            SwatchGrid(
                colours = KhataPalette.accents,
                selectedIndex = KhataPalette.accents.indexOf(prefs.themeSpec.accent),
                onSelect = { viewModel.onAccentSelected(KhataPalette.accents[it]) },
            )

            Section("Field intensity")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                FieldIntensity.entries.forEach { level ->
                    val selected = prefs.themeSpec.intensity == level
                    Box(
                        Modifier
                            .weight(1f)
                            .height(spacing.minTouchTarget)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                            )
                            .clickable { viewModel.onIntensitySelected(level) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = level.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Box(
                Modifier
                    .padding(spacing.screenHorizontal)
                    .fillMaxWidth()
                    .height(spacing.minTouchTarget)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable { viewModel.onResetTheme() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Reset to default",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MonthlyBudgetField(current: Long?, onChange: (Long?) -> Unit) {
    val spacing = LocalSpacing.current
    // Held locally so a half-typed number is not written on every keystroke,
    // and so clearing the field reads as "unset" rather than as zero.
    var text by rememberSaveable(current) {
        mutableStateOf(current?.let { (it / 100).toString() } ?: "")
    }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(9)
            text = digits
            onChange(if (digits.isEmpty()) null else digits.toLong() * 100)
        },
        label = { Text("Taka per month") },
        supportingText = {
            Text(
                if (text.isEmpty()) "No budget — the ring is hidden" else "Shown as a ring on the wallet card",
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
    )
}

@Composable
private fun Section(title: String) {
    val spacing = LocalSpacing.current
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(
            start = spacing.screenHorizontal,
            end = spacing.screenHorizontal,
            top = spacing.lg,
            bottom = spacing.sm,
        ),
    )
}

@Composable
private fun SwatchGrid(
    colours: List<Color>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        colours.chunked(4).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEachIndexed { colIndex, colour ->
                    val index = rowIndex * 4 + colIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(colour)
                            .border(
                                width = if (index == selectedIndex) 2.dp else 1.dp,
                                color = if (index == selectedIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small,
                            )
                            .clickable { onSelect(index) },
                    )
                }
                // Keep the last row's cells the same width as a full row's.
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
```

- [ ] **Step 6: Write the screen test**

Create `app/src/androidTest/java/com/wasif/khata/feature/settings/SettingsScreenTest.kt`:

```kotlin
package com.wasif.khata.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theTunerShowsAllFourAxes() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("FIELD").assertIsDisplayed()
        compose.onNodeWithText("GROUND").assertIsDisplayed()
        compose.onNodeWithText("ACCENT").assertIsDisplayed()
        compose.onNodeWithText("FIELD INTENSITY").assertIsDisplayed()
        compose.onNodeWithText("Reset to default").assertIsDisplayed()
    }

    @Test
    fun theBudgetFieldSaysWhatAnEmptyValueMeans() {
        // An empty budget field is not an error state, and the copy has to say
        // so or it reads as something the user forgot to fill in.
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("MONTHLY BUDGET").assertIsDisplayed()
        compose.onNodeWithText("No budget — the ring is hidden").assertIsDisplayed()
    }

    @Test
    fun thereIsNoHomeViewControlYet() {
        // Deliberate: the Ledger cannot yet carry a way back to the hub, so
        // offering this would let the user lock themselves out of Settings.
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onAllNodesWithText("Home view").fetchSemanticsNodes().let {
            assertTrue("the home-view control must wait for Plan C", it.isEmpty())
        }
    }

    @Test
    fun changingIntensityPersistsAcrossNavigation() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Dim").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        // The selection survived a round trip, which means it went through
        // DataStore rather than living in composition state.
        compose.onNodeWithText("Dim").assertIsDisplayed()
    }
}
```

- [ ] **Step 7: Run everything**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Expected: all `BUILD SUCCESSFUL`. This is the first point since Task 3 where the whole app compiles and every test runs, because Tasks 3–6 form one dependency chain.

- [ ] **Step 8: Install and look at it**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.wasif.khata/.MainActivity
```

Check by eye: no white flash on launch; খাতা centred with the tagline beneath; no Latin "Khata" anywhere; module cards sitting at the bottom edge with padding; the settings gear top-right; the tuner changing the app's appearance live.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/settings app/src/test/java/com/wasif/khata/feature/settings app/src/androidTest/java/com/wasif/khata/feature/settings
git commit -m "feat: settings with the four-axis theme tuner

Free-form rather than a preset list, chosen by the user over the
alternative. Plan A's pairwise contrast suite is what makes it safe:
512 combinations proven legible by 139 assertions.

Reset clears the stored keys rather than writing today's default as an
explicit value, so a later change of default still reaches anyone who
has pressed reset."
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` green.
- `./gradlew :app:connectedDebugAndroidTest` green, including the untouched Ledger and Editor tests.
- The app opens onto খাতা with no white flash and no Latin "Khata" anywhere.
- The settings tuner changes the app's appearance and the change survives a restart.
- A monthly budget can be set, and the ring appears on the wallet card only once it is.
- **No home-view control appears in Settings.** It ships in Plan C with the Ledger's hub glyph.
- **No file under `feature/ledger` or `feature/editor` was modified.**

## What Plan C picks up

The Wallet dashboard (net worth, account list with reconciliation gaps), the Ledger rebuild (month strip, search, day totals, category dots, the fixed 132dp headspace) and the Editor rebuild (amount-first, segmented direction pill, context line). Plus the aggregates those need: net worth across accounts, and per-day totals.
