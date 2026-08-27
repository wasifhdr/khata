# Khata Plan 1 — Foundation & Ledger

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A working, installable Khata app with the shared design system, the wallet
data foundation, and manual transaction entry with a paged, day-grouped ledger.

**Architecture:** Single Gradle module, package-per-feature. Room is the source of
truth, exposed as Flows through repositories that map platform exceptions to a
domain `DataError` at the boundary. UI is Compose + MVVM with an MVI-style
state/effect split. No SMS, AI, charts, or backup in this plan — those are Plans 2–6.

**Tech Stack:** Kotlin · Jetpack Compose · Material 3 · Room + KSP · Hilt · Paging 3 ·
Coroutines/Flow · JUnit4 + Robolectric + kotlinx-coroutines-test + Turbine.

**Spec:** `docs/superpowers/specs/2026-08-26-khata-wallet-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 30`. `compileSdk` / `targetSdk` at the current stable level. Target device: Pixel 6a, Android 17.
- Package and namespace: `com.wasif.khata`.
- Money is always `Long` **paisa**. Never `Double`, never `Float`, anywhere.
- Currency is BDT only. No multi-currency handling.
- Every table carries `uuid TEXT NOT NULL`, `createdAt INTEGER NOT NULL`, `updatedAt INTEGER NOT NULL`, `deletedAt INTEGER` (nullable). Deletes are soft.
- Instants are stored as UTC epoch millis. All display and all period boundaries are computed in `Asia/Dhaka`.
- No database, parsing, AI, or backup work on the main thread, ever.
- The ledger uses Paging 3. It is never loaded whole.
- Charts and dashboard figures read precomputed aggregates. Do not introduce aggregate-on-scroll queries here.
- DI is Hilt + KSP. Async is Coroutines/Flow — no `LiveData` in new code. Images are Coil. JSON is `kotlinx.serialization`.
- Repositories map platform exceptions to a domain error type at the boundary. Platform exceptions never leak past a repository.
- Tests never hardcode a magic epoch-millis literal. Build instants with `Instant.parse("…Z").toEpochMilli()` so the test states its own intent.
- Comments earn their place: a comment carries a non-obvious *why*, never a restatement of *what*. No section-divider banners, no KDoc restating a name.

---

### Task 1: Project scaffold, dependencies, and test infrastructure

Creates the Android project and proves the JVM test cycle works end to end. Every
later task depends on being able to run a failing test.

**Files:**
- Create: the generated project tree at repo root (`app/`, `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`)
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `.gitignore`
- Test: `app/src/test/java/com/wasif/khata/ScaffoldSmokeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: a buildable Gradle project with `namespace = "com.wasif.khata"`, a version catalog aliasing Room, Hilt, Paging, Compose, Robolectric, Turbine, and coroutines-test, and a working `./gradlew testDebugUnitTest` cycle.

- [ ] **Step 1: Verify the Android CLI is installed**

Run from `D:\Khata`:

```bash
android --version
```

Expected: a version string. If the command is not found, install it first:

```bash
curl -fsSL https://dl.google.com/android/cli/latest/windows_x86_64/install.cmd -o "%TEMP%\i.cmd" && "%TEMP%\i.cmd"
```

- [ ] **Step 2: Generate the project into the repo root**

```bash
android create empty-activity --name="Khata" --minSdk=30 -o .
```

Expected: `app/`, `gradle/`, `settings.gradle.kts`, and `gradlew` appear alongside the existing `docs/` and `.git/`.

- [ ] **Step 3: Rewrite the generated package to `com.wasif.khata`**

Find what the template used:

```bash
grep -rn "namespace\|applicationId" app/build.gradle.kts
```

Rewrite the Gradle coordinates and move the source tree to match:

```bash
sed -i 's/com\.example\.khata/com.wasif.khata/g' app/build.gradle.kts
mkdir -p app/src/main/java/com/wasif/khata
mv app/src/main/java/com/example/khata/* app/src/main/java/com/wasif/khata/
rm -rf app/src/main/java/com/example
grep -rl "com.example.khata" app/src | xargs sed -i 's/com\.example\.khata/com.wasif.khata/g'
```

- [ ] **Step 4: Confirm no `com.example` references remain**

```bash
grep -rn "com.example" app/ || echo "CLEAN"
```

Expected: `CLEAN`

- [ ] **Step 5: Look up current dependency versions**

Do not guess version numbers. Query them:

```bash
android studio version-lookup androidx.room:room-runtime androidx.paging:paging-compose com.google.dagger:hilt-android androidx.hilt:hilt-navigation-compose androidx.navigation:navigation-compose com.google.devtools.ksp org.robolectric:robolectric org.jetbrains.kotlinx:kotlinx-coroutines-test app.cash.turbine:turbine
```

Expected: a latest-version line per artifact. Use these values in the next step.

- [ ] **Step 6: Add dependencies to the version catalog**

Add to `gradle/libs.versions.toml`, filling the `[versions]` values from Step 5:

```toml
[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-paging = { group = "androidx.room", name = "room-paging", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
androidx-paging-testing = { group = "androidx.paging", name = "paging-testing", version.ref = "paging" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNav" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTest" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 7: Wire the plugins and dependencies into `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.wasif.khata"
    defaultConfig {
        applicationId = "com.wasif.khata"
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // Room's KSP processor cannot generate a PagingSource return type without this.
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.turbine)
}
```

The `room` block matters: exported schema JSON is what makes Plan 2's migration
reviewable instead of guesswork.

- [ ] **Step 8: Write the smoke test**

Create `app/src/test/java/com/wasif/khata/ScaffoldSmokeTest.kt`:

```kotlin
package com.wasif.khata

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldSmokeTest {
    @Test
    fun `test infrastructure runs`() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 9: Run the test**

```bash
./gradlew testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 10: Add `.gitignore` and commit**

Create `.gitignore`:

```
*.iml
.gradle/
local.properties
.idea/
.DS_Store
build/
captures/
.externalNativeBuild
.cxx
*.apk
*.keystore
```

```bash
git add -A
git commit -m "chore: scaffold Khata Android project with Room, Hilt, Paging, and test infra"
```

---

### Task 2: `Money` value type

Pure Kotlin, zero Android dependencies. Every amount in the app flows through this
type, so it is worth getting exactly right before anything consumes it.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/model/Money.kt`
- Test: `app/src/test/java/com/wasif/khata/core/model/MoneyTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `Money(val minor: Long)` with `plus`, `minus`, `unaryMinus`, `compareTo`, `isZero`, `abs`; `Money.ZERO`; `Money.SYMBOL`; `Money.ofTaka(taka: Long, paisa: Int = 0): Money`; `Money.parse(input: String): Money?`; `Money.format(withSymbol: Boolean = true): String`.

**Decision — grouping style:** amounts are grouped Western-style (`৳1,234.56`), not
lakh/crore (`৳12,34,567.89`), because bKash and EBL write their SMS amounts that
way and the ledger should read the same as its source. This is isolated to
`groupIntegerPart` and is a one-function change if you want it flipped.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/wasif/khata/core/model/MoneyTest.kt`:

```kotlin
package com.wasif.khata.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `ofTaka combines taka and paisa into minor units`() {
        assertEquals(123456L, Money.ofTaka(1234, 56).minor)
        assertEquals(500L, Money.ofTaka(5).minor)
    }

    @Test
    fun `addition and subtraction operate on minor units`() {
        assertEquals(Money(300), Money(100) + Money(200))
        assertEquals(Money(-100), Money(100) - Money(200))
    }

    @Test
    fun `negation and abs`() {
        assertEquals(Money(-250), -Money(250))
        assertEquals(Money(250), Money(-250).abs())
    }

    @Test
    fun `comparison orders by minor units`() {
        assertTrue(Money(100) < Money(200))
        assertEquals(Money(0), Money.ZERO)
        assertTrue(Money.ZERO.isZero)
    }

    @Test
    fun `format renders two decimal places with symbol and grouping`() {
        assertEquals("৳1,234.56", Money(123456).format())
        assertEquals("৳0.00", Money.ZERO.format())
        assertEquals("৳5.00", Money(500).format())
        assertEquals("৳12,345,678.90", Money(1234567890).format())
    }

    @Test
    fun `format renders negatives with the sign before the symbol`() {
        assertEquals("-৳1,234.56", Money(-123456).format())
    }

    @Test
    fun `format can omit the symbol`() {
        assertEquals("1,234.56", Money(123456).format(withSymbol = false))
    }

    @Test
    fun `parse accepts plain digits, decimals, commas, and the symbol`() {
        assertEquals(Money(123456), Money.parse("1234.56"))
        assertEquals(Money(123456), Money.parse("1,234.56"))
        assertEquals(Money(123456), Money.parse("৳1,234.56"))
        assertEquals(Money(123456), Money.parse("Tk 1,234.56"))
        assertEquals(Money(50000), Money.parse("500"))
    }

    @Test
    fun `parse pads a single decimal digit rather than truncating`() {
        assertEquals(Money(1250), Money.parse("12.5"))
    }

    @Test
    fun `parse rejects malformed input instead of guessing`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test
    fun `format and parse round-trip`() {
        val original = Money(987654321)
        assertEquals(original, Money.parse(original.format()))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "*MoneyTest*"
```

Expected: FAIL — `Unresolved reference: Money`.

- [ ] **Step 3: Implement `Money`**

Create `app/src/main/java/com/wasif/khata/core/model/Money.kt`:

```kotlin
package com.wasif.khata.core.model

import kotlin.math.absoluteValue

@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L

    operator fun plus(other: Money) = Money(minor + other.minor)

    operator fun minus(other: Money) = Money(minor - other.minor)

    operator fun unaryMinus() = Money(-minor)

    fun abs() = Money(minor.absoluteValue)

    override fun compareTo(other: Money) = minor.compareTo(other.minor)

    fun format(withSymbol: Boolean = true): String {
        val sign = if (minor < 0) "-" else ""
        val magnitude = minor.absoluteValue
        val paisa = (magnitude % 100).toString().padStart(2, '0')
        val body = "${groupIntegerPart(magnitude / 100)}.$paisa"
        return if (withSymbol) "$sign$SYMBOL$body" else "$sign$body"
    }

    companion object {
        const val SYMBOL = "৳"

        val ZERO = Money(0)

        // Overflow throws here but returns null in parse(): a bad argument is a
        // programming error worth failing fast on, a bad string is untrusted input.
        fun ofTaka(taka: Long, paisa: Int = 0) =
            Money(Math.addExact(Math.multiplyExact(taka, 100L), paisa.toLong()))

        fun parse(input: String): Money? {
            val cleaned = input.replace(SYMBOL, "")
                .replace("Tk", "", ignoreCase = true)
                .replace(",", "")
                .replace(" ", "")
                .trim()
            if (cleaned.isEmpty()) return null

            val negative = cleaned.startsWith("-")
            val unsigned = cleaned.removePrefix("-")

            val parts = unsigned.split(".")
            if (parts.size > 2) return null

            val takaPart = parts[0]
            if (takaPart.isEmpty() || !takaPart.all { it.isDigit() }) return null

            val paisaPart = when {
                parts.size == 1 -> "00"
                // A single decimal digit means tenths: "12.5" is 12.50, never 12.05.
                parts[1].length == 1 -> parts[1] + "0"
                parts[1].length == 2 -> parts[1]
                else -> return null
            }
            if (!paisaPart.all { it.isDigit() }) return null

            val taka = takaPart.toLongOrNull() ?: return null
            val magnitude = try {
                Math.addExact(Math.multiplyExact(taka, 100L), paisaPart.toLong())
            } catch (e: ArithmeticException) {
                return null
            }
            return Money(if (negative) -magnitude else magnitude)
        }

        private fun groupIntegerPart(value: Long): String {
            val digits = value.toString()
            if (digits.length <= 3) return digits
            return digits.reversed().chunked(3).joinToString(",").reversed()
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*MoneyTest*"
```

Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/model/Money.kt app/src/test/java/com/wasif/khata/core/model/MoneyTest.kt
git commit -m "feat: add Money value type backed by Long paisa"
```

---

### Task 3: Core enums and time helpers

Small, dependency-free types that the schema and UI both reference. Bundled into one
task because none of them justifies its own review gate.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/model/Enums.kt`
- Create: `app/src/main/java/com/wasif/khata/core/time/KhataClock.kt`
- Test: `app/src/test/java/com/wasif/khata/core/time/KhataClockTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: enums `AccountType`, `TransactionDirection`, `TransactionSource`, `Confidence`; `interface KhataClock { fun now(): Long }`; `class SystemKhataClock @Inject constructor() : KhataClock`; `val DHAKA: ZoneId`; `fun Long.toDhakaLocalDate(): LocalDate`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/core/time/KhataClockTest.kt`:

```kotlin
package com.wasif.khata.core.time

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class KhataClockTest {

    @Test
    fun `an evening UTC instant is already the next calendar day in Dhaka`() {
        val millis = Instant.parse("2026-08-26T20:30:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 8, 27), millis.toDhakaLocalDate())
    }

    @Test
    fun `a morning UTC instant is the same calendar day in Dhaka`() {
        val millis = Instant.parse("2026-08-26T06:00:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 8, 26), millis.toDhakaLocalDate())
    }

    @Test
    fun `a fixed clock returns exactly what it was given`() {
        val clock = object : KhataClock {
            override fun now(): Long = 42L
        }

        assertEquals(42L, clock.now())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "*KhataClockTest*"
```

Expected: FAIL — `Unresolved reference: toDhakaLocalDate`.

- [ ] **Step 3: Implement the enums**

Create `app/src/main/java/com/wasif/khata/core/model/Enums.kt`:

```kotlin
package com.wasif.khata.core.model

enum class AccountType { MFS, BANK, CARD, CASH, MANUAL_ASSET }

enum class TransactionDirection { DEBIT, CREDIT }

enum class TransactionSource { SMS, NOTIFICATION, WIDGET, MANUAL }

enum class Confidence { HIGH, MEDIUM, LOW }
```

- [ ] **Step 4: Implement the clock**

Create `app/src/main/java/com/wasif/khata/core/time/KhataClock.kt`:

```kotlin
package com.wasif.khata.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

interface KhataClock {
    fun now(): Long
}

class SystemKhataClock @Inject constructor() : KhataClock {
    override fun now(): Long = System.currentTimeMillis()
}

val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")

fun Long.toDhakaLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(DHAKA).toLocalDate()
```

`KhataClock` is an interface rather than direct `System.currentTimeMillis()` calls
so day-boundary and month-boundary tests can pin time instead of being flaky near
midnight.

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew testDebugUnitTest --tests "*KhataClockTest*"
```

Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ app/src/test/java/com/wasif/khata/core/
git commit -m "feat: add core enums and Dhaka-anchored clock helpers"
```

---
### Task 4: Room database — entities, DAOs, schema v1

Five tables. `raw_messages` and `parsing_rules` are deliberately absent; Plan 2 adds
them via a migration, which is also how the migration path gets proven while there
is no data worth losing.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/AccountEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/CategoryEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/TransactionEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/MerchantEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/dao/AccountDao.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/dao/CategoryDao.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/dao/MerchantDao.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`

**Interfaces:**
- Consumes: `AccountType`, `TransactionDirection`, `TransactionSource`, `Confidence` (Task 3).
- Produces: `KhataDatabase` (abstract, version 1) exposing `accountDao()`, `categoryDao()`, `transactionDao()`, `merchantDao()`.
  - `TransactionDao`: `upsert(entity): Long`, `pagingSource(): PagingSource<Int, TransactionEntity>`, `observeById(id): Flow<TransactionEntity?>`, `findById(id): TransactionEntity?`, `softDelete(id, deletedAt)`.
  - `AccountDao`: `upsert(entity): Long`, `observeAll(): Flow<List<AccountEntity>>`, `getAll(): List<AccountEntity>`, `count(): Int`, `adjustBalance(accountId, deltaMinor, updatedAt)`.
  - `CategoryDao`: `upsert(entity): Long`, `upsertAll(entities)`, `observeAll(): Flow<List<CategoryEntity>>`, `count(): Int`.
  - `MerchantDao`: `upsert(entity): Long`, `upsertAlias(entity): Long`, `findById(id)`, `findByAlias(rawText)`.

- [ ] **Step 1: Write the failing DAO test**

`TestPager` is the correct harness here. `asSnapshot()` is an extension on
`Flow<PagingData<T>>` and cannot be called on a bare `PagingSource`.

Create `app/src/test/java/com/wasif/khata/core/data/TransactionDaoTest.kt`:

```kotlin
package com.wasif.khata.core.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
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
class TransactionDaoTest {

    private lateinit var db: KhataDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertAccount(): Long = db.accountDao().upsert(
        AccountEntity(
            uuid = "acc-1",
            name = "bKash",
            type = AccountType.MFS,
            openingBalanceMinor = 0,
            currentBalanceMinor = 0,
            reportedBalanceMinor = null,
            reportedBalanceAt = null,
            includeInNetWorth = true,
            smsIdentifiers = "bKash",
            createdAt = 1000,
            updatedAt = 1000,
        )
    )

    private fun transaction(accountId: Long, occurredAt: Long, uuid: String) =
        TransactionEntity(
            uuid = uuid,
            accountId = accountId,
            amountMinor = 10_000,
            direction = TransactionDirection.DEBIT,
            occurredAt = occurredAt,
            merchantRaw = "SHWAPNO",
            merchantId = null,
            categoryId = null,
            note = null,
            source = TransactionSource.MANUAL,
            confidence = Confidence.HIGH,
            rawMessageId = null,
            transferGroupId = null,
            feeMinor = null,
            referenceNumber = null,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        )

    private suspend fun pageUuids(): List<String> {
        val pager = TestPager(PagingConfig(pageSize = 10), db.transactionDao().pagingSource())
        val page = pager.refresh() as PagingSource.LoadResult.Page
        return page.data.map { it.uuid }
    }

    @Test
    fun `paging source returns transactions newest first`() = runTest {
        val accountId = insertAccount()
        db.transactionDao().upsert(transaction(accountId, occurredAt = 1000, uuid = "t-old"))
        db.transactionDao().upsert(transaction(accountId, occurredAt = 3000, uuid = "t-new"))
        db.transactionDao().upsert(transaction(accountId, occurredAt = 2000, uuid = "t-mid"))

        assertEquals(listOf("t-new", "t-mid", "t-old"), pageUuids())
    }

    @Test
    fun `transactions sharing a timestamp get a stable order`() = runTest {
        val accountId = insertAccount()
        val firstId = db.transactionDao().upsert(transaction(accountId, occurredAt = 5000, uuid = "t-a"))
        val secondId = db.transactionDao().upsert(transaction(accountId, occurredAt = 5000, uuid = "t-b"))

        // Higher rowid first, so backfilled batches never shuffle between page loads.
        assertEquals(listOf("t-b", "t-a"), pageUuids())
        assertEquals(true, secondId > firstId)
    }

    @Test
    fun `soft deleted transactions are excluded from paging`() = runTest {
        val accountId = insertAccount()
        val id = db.transactionDao().upsert(transaction(accountId, occurredAt = 1000, uuid = "t-1"))
        db.transactionDao().upsert(transaction(accountId, occurredAt = 2000, uuid = "t-2"))

        db.transactionDao().softDelete(id, deletedAt = 9999)

        assertEquals(listOf("t-2"), pageUuids())
    }

    @Test
    fun `observeById emits null after soft delete`() = runTest {
        val accountId = insertAccount()
        val id = db.transactionDao().upsert(transaction(accountId, occurredAt = 1000, uuid = "t-1"))

        assertEquals("t-1", db.transactionDao().observeById(id).first()?.uuid)

        db.transactionDao().softDelete(id, deletedAt = 9999)
        assertNull(db.transactionDao().observeById(id).first())
    }

    @Test
    fun `adjustBalance applies a signed delta to the account`() = runTest {
        val accountId = insertAccount()

        db.accountDao().adjustBalance(accountId, deltaMinor = -5000, updatedAt = 2000)
        assertEquals(-5000, db.accountDao().observeAll().first().single().currentBalanceMinor)

        db.accountDao().adjustBalance(accountId, deltaMinor = 8000, updatedAt = 3000)
        assertEquals(3000, db.accountDao().observeAll().first().single().currentBalanceMinor)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "*TransactionDaoTest*"
```

Expected: FAIL — `Unresolved reference: KhataDatabase`.

- [ ] **Step 3: Create the account and category entities**

Create `app/src/main/java/com/wasif/khata/core/data/entity/AccountEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wasif.khata.core.model.AccountType

@Entity(tableName = "accounts", indices = [Index(value = ["uuid"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val type: AccountType,
    val openingBalanceMinor: Long,
    val currentBalanceMinor: Long,
    val reportedBalanceMinor: Long?,
    val reportedBalanceAt: Long?,
    val includeInNetWorth: Boolean,
    val smsIdentifiers: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

Create `app/src/main/java/com/wasif/khata/core/data/entity/CategoryEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories", indices = [Index(value = ["uuid"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val icon: String,
    val colorToken: String,
    val parentId: Long?,
    val isSystem: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

- [ ] **Step 4: Create the merchant and transaction entities**

Create `app/src/main/java/com/wasif/khata/core/data/entity/MerchantEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "merchants", indices = [Index(value = ["uuid"], unique = true)])
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val canonicalName: String,
    val categoryId: Long?,
    val placeId: Long?,
    val isUserConfirmed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "merchant_aliases",
    indices = [Index(value = ["rawText"], unique = true), Index(value = ["merchantId"])],
)
data class MerchantAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val merchantId: Long,
    val rawText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

Create `app/src/main/java/com/wasif/khata/core/data/entity/TransactionEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["transferGroupId"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val accountId: Long,
    val amountMinor: Long,
    val direction: TransactionDirection,
    val occurredAt: Long,
    val merchantRaw: String?,
    val merchantId: Long?,
    val categoryId: Long?,
    val note: String?,
    val source: TransactionSource,
    val confidence: Confidence,
    val rawMessageId: Long?,
    val transferGroupId: String?,
    val feeMinor: Long?,
    val referenceNumber: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`amountMinor` is always positive; `direction` carries the sign. Storing a signed
amount *and* a direction gives two sources of truth that can disagree.

- [ ] **Step 5: Create the DAOs**

Create `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`:

```kotlin
package com.wasif.khata.core.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Upsert
    suspend fun upsert(entity: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC")
    fun pagingSource(): PagingSource<Int, TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)
}
```

The paging query ties `id DESC` to `occurredAt DESC`. Without it, transactions
sharing a timestamp — which SMS backfill produces routinely — have no stable order
and rows visibly shuffle between page loads.

Create `app/src/main/java/com/wasif/khata/core/data/dao/AccountDao.kt`:

```kotlin
package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Upsert
    suspend fun upsert(entity: AccountEntity): Long

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY name")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun countIncludingDeleted(): Int

    @Query(
        "UPDATE accounts SET currentBalanceMinor = currentBalanceMinor + :deltaMinor, " +
            "updatedAt = :updatedAt WHERE id = :accountId"
    )
    suspend fun adjustBalance(accountId: Long, deltaMinor: Long, updatedAt: Long)
}
```

Create `app/src/main/java/com/wasif/khata/core/data/dao/CategoryDao.kt`:

```kotlin
package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Upsert
    suspend fun upsert(entity: CategoryEntity): Long

    @Upsert
    suspend fun upsertAll(entities: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countIncludingDeleted(): Int
}
```

Create `app/src/main/java/com/wasif/khata/core/data/dao/MerchantDao.kt`:

```kotlin
package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.MerchantAliasEntity
import com.wasif.khata.core.data.entity.MerchantEntity

@Dao
interface MerchantDao {

    @Upsert
    suspend fun upsert(entity: MerchantEntity): Long

    @Upsert
    suspend fun upsertAlias(entity: MerchantAliasEntity): Long

    @Query("SELECT * FROM merchants WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: Long): MerchantEntity?

    @Query(
        "SELECT m.* FROM merchants m JOIN merchant_aliases a ON a.merchantId = m.id " +
            "WHERE a.rawText = :rawText AND m.deletedAt IS NULL AND a.deletedAt IS NULL LIMIT 1"
    )
    suspend fun findByAlias(rawText: String): MerchantEntity?
}
```

- [ ] **Step 6: Create the database and Hilt module**

Create `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt`:

```kotlin
package com.wasif.khata.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.data.dao.MerchantDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.data.entity.MerchantAliasEntity
import com.wasif.khata.core.data.entity.MerchantEntity
import com.wasif.khata.core.data.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MerchantEntity::class,
        MerchantAliasEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantDao(): MerchantDao
}
```

Room converts enums to strings automatically, so no `TypeConverter` is needed for
`AccountType`, `TransactionDirection`, `TransactionSource`, or `Confidence`.

Create `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt`:

```kotlin
package com.wasif.khata.core.data.di

import android.content.Context
import androidx.room.Room
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.data.dao.MerchantDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.SystemKhataClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KhataDatabase =
        Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db").build()

    @Provides fun provideAccountDao(db: KhataDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: KhataDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: KhataDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideMerchantDao(db: KhataDatabase): MerchantDao = db.merchantDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    abstract fun bindClock(impl: SystemKhataClock): KhataClock
}
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*TransactionDaoTest*"
```

Expected: PASS, 5 tests.

- [ ] **Step 8: Confirm the schema was exported**

```bash
ls app/schemas/com.wasif.khata.core.data.KhataDatabase/
```

Expected: `1.json`

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/data/ app/src/test/java/com/wasif/khata/core/data/ app/schemas/
git commit -m "feat: add Room schema v1 with accounts, categories, transactions, and merchants"
```

---

### Task 5: Repository layer and the `DataError` boundary

Platform exceptions stop here. Everything above this layer sees domain types only.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/domain/error/DataError.kt`
- Create: `app/src/main/java/com/wasif/khata/domain/model/Transaction.kt`
- Create: `app/src/main/java/com/wasif/khata/domain/model/Account.kt`
- Create: `app/src/main/java/com/wasif/khata/domain/model/Category.kt`
- Create: `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/repository/Mappers.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/repository/TransactionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: all DAOs and entities (Task 4), `Money` (Task 2), `KhataClock` (Task 3).
- Produces:
  - `sealed class DataError : Throwable()` with `NotFound`, `Storage`, `Unknown(cause)`.
  - `data class Transaction(id, uuid, accountId, amount: Money, direction, occurredAt, merchantRaw, merchantId, categoryId, note, source, confidence, transferGroupId, updatedAt)` with `signedAmount` and `isTransfer` getters.
  - `data class Account(id, uuid, name, type, currentBalance: Money, reportedBalance: Money?, includeInNetWorth)` with a `hasBalanceDrift` getter.
  - `data class Category(id, uuid, name, icon, colorToken, parentId)`.
  - `data class TransactionDraft(id: Long?, accountId, amount: Money, direction, occurredAt, merchantRaw, categoryId, note)`.
  - `interface TransactionRepository`: `pagedTransactions(): Flow<PagingData<Transaction>>`, `observe(id): Flow<Transaction?>`, `save(draft): Result<Long>`, `delete(id): Result<Unit>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/core/data/repository/TransactionRepositoryImplTest.kt`:

```kotlin
package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.error.DataError
import com.wasif.khata.domain.repository.TransactionDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionRepositoryImplTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: TransactionRepositoryImpl

    private val clock = object : KhataClock {
        override fun now(): Long = 5_000L
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TransactionRepositoryImpl(db, db.transactionDao(), db.accountDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(opening: Long = 0L): Long = db.accountDao().upsert(
        AccountEntity(
            uuid = "acc-1",
            name = "bKash",
            type = AccountType.MFS,
            openingBalanceMinor = opening,
            currentBalanceMinor = opening,
            reportedBalanceMinor = null,
            reportedBalanceAt = null,
            includeInNetWorth = true,
            smsIdentifiers = "bKash",
            createdAt = 1000,
            updatedAt = 1000,
        )
    )

    private suspend fun balance(): Long =
        db.accountDao().observeAll().first().single().currentBalanceMinor

    private fun draft(accountId: Long, amount: Money, direction: TransactionDirection) =
        TransactionDraft(
            id = null,
            accountId = accountId,
            amount = amount,
            direction = direction,
            occurredAt = 4_000L,
            merchantRaw = "SHWAPNO",
            categoryId = null,
            note = null,
        )

    @Test
    fun `saving a debit decreases the account balance`() = runTest {
        val accountId = account(opening = 100_000)

        repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT))

        assertEquals(75_000, balance())
    }

    @Test
    fun `saving a credit increases the account balance`() = runTest {
        val accountId = account(opening = 100_000)

        repository.save(draft(accountId, Money(25_000), TransactionDirection.CREDIT))

        assertEquals(125_000, balance())
    }

    @Test
    fun `editing an amount reverses the old effect before applying the new one`() = runTest {
        val accountId = account(opening = 100_000)
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        repository.save(draft(accountId, Money(10_000), TransactionDirection.DEBIT).copy(id = id))

        // 100,000 - 10,000. Not 100,000 - 25,000 - 10,000.
        assertEquals(90_000, balance())
    }

    @Test
    fun `editing a direction reverses the old effect before applying the new one`() = runTest {
        val accountId = account(opening = 100_000)
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        repository.save(draft(accountId, Money(25_000), TransactionDirection.CREDIT).copy(id = id))

        assertEquals(125_000, balance())
    }

    @Test
    fun `editing does not create a second row`() = runTest {
        val accountId = account(opening = 100_000)
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        val sameId = repository.save(
            draft(accountId, Money(10_000), TransactionDirection.DEBIT).copy(id = id)
        ).getOrThrow()

        assertEquals(id, sameId)
    }

    @Test
    fun `deleting a transaction restores the account balance`() = runTest {
        val accountId = account(opening = 100_000)
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        repository.delete(id)

        assertEquals(100_000, balance())
        assertNull(repository.observe(id).first())
    }

    @Test
    fun `deleting a missing transaction fails with NotFound rather than throwing`() = runTest {
        val result = repository.delete(9_999)

        assertTrue(result.isFailure)
        assertEquals(DataError.NotFound, result.exceptionOrNull())
    }

    @Test
    fun `saving against a missing transaction id fails with NotFound`() = runTest {
        val accountId = account()

        val result = repository.save(
            draft(accountId, Money(1_000), TransactionDirection.DEBIT).copy(id = 9_999)
        )

        assertTrue(result.isFailure)
        assertEquals(DataError.NotFound, result.exceptionOrNull())
    }

    @Test
    fun `saved transactions map back to domain models with Money amounts`() = runTest {
        val accountId = account()
        val id = repository.save(draft(accountId, Money(25_000), TransactionDirection.DEBIT)).getOrThrow()

        val saved = repository.observe(id).first()

        assertEquals(Money(25_000), saved?.amount)
        assertEquals("SHWAPNO", saved?.merchantRaw)
        assertEquals(5_000L, saved?.updatedAt)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "*TransactionRepositoryImplTest*"
```

Expected: FAIL — `Unresolved reference: TransactionRepositoryImpl`.

- [ ] **Step 3: Create the domain error type and models**

Create `app/src/main/java/com/wasif/khata/domain/error/DataError.kt`:

```kotlin
package com.wasif.khata.domain.error

sealed class DataError : Throwable() {
    data object NotFound : DataError()
    data object Storage : DataError()
    data class Unknown(override val cause: Throwable) : DataError()
}
```

`DataError` extends `Throwable` so it travels inside `Result` without a second
wrapper type, while still being an exhaustive `when` at the call site.

Create `app/src/main/java/com/wasif/khata/domain/model/Transaction.kt`:

```kotlin
package com.wasif.khata.domain.model

import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource

data class Transaction(
    val id: Long,
    val uuid: String,
    val accountId: Long,
    val amount: Money,
    val direction: TransactionDirection,
    val occurredAt: Long,
    val merchantRaw: String?,
    val merchantId: Long?,
    val categoryId: Long?,
    val note: String?,
    val source: TransactionSource,
    val confidence: Confidence,
    val transferGroupId: String?,
    val updatedAt: Long,
) {
    val signedAmount: Money
        get() = if (direction == TransactionDirection.DEBIT) -amount else amount

    val isTransfer: Boolean get() = transferGroupId != null
}
```

Create `app/src/main/java/com/wasif/khata/domain/model/Account.kt`:

```kotlin
package com.wasif.khata.domain.model

import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money

data class Account(
    val id: Long,
    val uuid: String,
    val name: String,
    val type: AccountType,
    val currentBalance: Money,
    val reportedBalance: Money?,
    val includeInNetWorth: Boolean,
) {
    val hasBalanceDrift: Boolean
        get() = reportedBalance != null && reportedBalance != currentBalance
}
```

Create `app/src/main/java/com/wasif/khata/domain/model/Category.kt`:

```kotlin
package com.wasif.khata.domain.model

data class Category(
    val id: Long,
    val uuid: String,
    val name: String,
    val icon: String,
    val colorToken: String,
    val parentId: Long?,
)
```

- [ ] **Step 4: Create the repository interface**

Create `app/src/main/java/com/wasif/khata/domain/repository/TransactionRepository.kt`:

```kotlin
package com.wasif.khata.domain.repository

import androidx.paging.PagingData
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

data class TransactionDraft(
    val id: Long?,
    val accountId: Long,
    val amount: Money,
    val direction: TransactionDirection,
    val occurredAt: Long,
    val merchantRaw: String?,
    val categoryId: Long?,
    val note: String?,
)

interface TransactionRepository {
    fun pagedTransactions(): Flow<PagingData<Transaction>>
    fun observe(id: Long): Flow<Transaction?>
    suspend fun save(draft: TransactionDraft): Result<Long>
    suspend fun delete(id: Long): Result<Unit>
}
```

- [ ] **Step 5: Create the mappers**

Create `app/src/main/java/com/wasif/khata/core/data/repository/Mappers.kt`:

```kotlin
package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.model.Transaction

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    uuid = uuid,
    accountId = accountId,
    amount = Money(amountMinor),
    direction = direction,
    occurredAt = occurredAt,
    merchantRaw = merchantRaw,
    merchantId = merchantId,
    categoryId = categoryId,
    note = note,
    source = source,
    confidence = confidence,
    transferGroupId = transferGroupId,
    updatedAt = updatedAt,
)

fun AccountEntity.toDomain() = Account(
    id = id,
    uuid = uuid,
    name = name,
    type = type,
    currentBalance = Money(currentBalanceMinor),
    reportedBalance = reportedBalanceMinor?.let { Money(it) },
    includeInNetWorth = includeInNetWorth,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    uuid = uuid,
    name = name,
    icon = icon,
    colorToken = colorToken,
    parentId = parentId,
)
```

- [ ] **Step 6: Implement the repository**

Create `app/src/main/java/com/wasif/khata/core/data/repository/TransactionRepositoryImpl.kt`:

```kotlin
package com.wasif.khata.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import androidx.room.withTransaction
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.domain.error.DataError
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl @Inject constructor(
    private val db: KhataDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val clock: KhataClock,
) : TransactionRepository {

    override fun pagedTransactions(): Flow<PagingData<Transaction>> =
        Pager(PagingConfig(pageSize = 50, prefetchDistance = 25, enablePlaceholders = false)) {
            transactionDao.pagingSource()
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Transaction?> =
        transactionDao.observeById(id).map { it?.toDomain() }

    override suspend fun save(draft: TransactionDraft): Result<Long> = runCatchingData {
        db.withTransaction {
            val now = clock.now()
            val existing = draft.id?.let { transactionDao.findById(it) }
            if (draft.id != null && existing == null) throw DataError.NotFound

            // Reverse the previous effect before applying the new one, or an edit
            // compounds onto the balance instead of replacing.
            if (existing != null) {
                accountDao.adjustBalance(existing.accountId, -existing.signedMinor(), now)
            }

            val rowId = transactionDao.upsert(
                TransactionEntity(
                    id = existing?.id ?: 0,
                    uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                    accountId = draft.accountId,
                    amountMinor = draft.amount.minor,
                    direction = draft.direction,
                    occurredAt = draft.occurredAt,
                    merchantRaw = draft.merchantRaw,
                    merchantId = existing?.merchantId,
                    categoryId = draft.categoryId,
                    note = draft.note,
                    source = existing?.source ?: TransactionSource.MANUAL,
                    confidence = existing?.confidence ?: Confidence.HIGH,
                    rawMessageId = existing?.rawMessageId,
                    transferGroupId = existing?.transferGroupId,
                    feeMinor = existing?.feeMinor,
                    referenceNumber = existing?.referenceNumber,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )

            accountDao.adjustBalance(draft.accountId, signedMinor(draft.amount.minor, draft.direction), now)

            // @Upsert returns -1 when it updated rather than inserted.
            if (rowId == -1L) existing!!.id else rowId
        }
    }

    override suspend fun delete(id: Long): Result<Unit> = runCatchingData {
        db.withTransaction {
            val existing = transactionDao.findById(id) ?: throw DataError.NotFound
            val now = clock.now()
            accountDao.adjustBalance(existing.accountId, -existing.signedMinor(), now)
            transactionDao.softDelete(id, now)
        }
    }
}

private fun signedMinor(amountMinor: Long, direction: TransactionDirection): Long =
    if (direction == TransactionDirection.DEBIT) -amountMinor else amountMinor

private fun TransactionEntity.signedMinor(): Long = signedMinor(amountMinor, direction)

private inline fun <T> runCatchingData(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    // Rethrow first — a broad catch would swallow structured-concurrency cancellation.
    throw e
} catch (e: DataError) {
    Result.failure(e)
} catch (e: Throwable) {
    Result.failure(DataError.Unknown(e))
}
```

- [ ] **Step 7: Bind the repository in Hilt**

Create `app/src/main/java/com/wasif/khata/core/data/di/RepositoryModule.kt`:

```kotlin
package com.wasif.khata.core.data.di

import com.wasif.khata.core.data.repository.TransactionRepositoryImpl
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository
}
```

- [ ] **Step 8: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*TransactionRepositoryImplTest*"
```

Expected: PASS, 9 tests.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/wasif/khata/domain/ app/src/main/java/com/wasif/khata/core/data/ app/src/test/java/com/wasif/khata/core/data/repository/
git commit -m "feat: add transaction repository with balance-consistent writes and DataError boundary"
```

---
### Task 6: First-run seed data

Without seeded accounts and categories the app opens to an unusable empty state,
and the editor has nothing to select.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/seed/DefaultData.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/seed/DatabaseSeeder.kt`
- Create: `app/src/main/java/com/wasif/khata/KhataApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/wasif/khata/core/data/seed/DatabaseSeederTest.kt`

**Interfaces:**
- Consumes: `AccountDao`, `CategoryDao` (Task 4), `KhataClock` (Task 3).
- Produces: `DEFAULT_ACCOUNTS: List<SeedAccount>`, `DEFAULT_CATEGORIES: List<SeedCategory>`, and `DatabaseSeeder.seedIfEmpty()` — suspend, idempotent.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/core/data/seed/DatabaseSeederTest.kt`:

```kotlin
package com.wasif.khata.core.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseSeederTest {

    private lateinit var db: KhataDatabase
    private lateinit var seeder: DatabaseSeeder

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        seeder = DatabaseSeeder(
            db.accountDao(),
            db.categoryDao(),
            object : KhataClock { override fun now(): Long = 1L },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seeding an empty database creates the default accounts and categories`() = runTest {
        seeder.seedIfEmpty()

        val accounts = db.accountDao().observeAll().first()
        val categories = db.categoryDao().observeAll().first()

        assertEquals(DEFAULT_ACCOUNTS.size, accounts.size)
        assertEquals(DEFAULT_CATEGORIES.size, categories.size)
        assertTrue(accounts.any { it.type == AccountType.CASH })
    }

    @Test
    fun `seeding twice does not duplicate rows`() = runTest {
        seeder.seedIfEmpty()
        seeder.seedIfEmpty()

        assertEquals(DEFAULT_ACCOUNTS.size, db.accountDao().observeAll().first().size)
        assertEquals(DEFAULT_CATEGORIES.size, db.categoryDao().observeAll().first().size)
    }

    @Test
    fun `every seeded category has a stable, unique uuid so later syncs can match them`() = runTest {
        seeder.seedIfEmpty()

        val uuids = db.categoryDao().observeAll().first().map { it.uuid }

        assertEquals(uuids.size, uuids.toSet().size)
        assertTrue(uuids.all { it.startsWith("seed-cat-") })
    }

    @Test
    fun `every seeded category colour token has a palette entry`() = runTest {
        // Guards the token contract between DefaultData and the design system.
        val tokens = DEFAULT_CATEGORIES.map { it.colorToken }.toSet()

        assertTrue(tokens.all { it.startsWith("category_") })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "*DatabaseSeederTest*"
```

Expected: FAIL — `Unresolved reference: DatabaseSeeder`.

- [ ] **Step 3: Define the default data**

Create `app/src/main/java/com/wasif/khata/core/data/seed/DefaultData.kt`:

```kotlin
package com.wasif.khata.core.data.seed

import com.wasif.khata.core.model.AccountType

data class SeedAccount(
    val slug: String,
    val name: String,
    val type: AccountType,
    val smsIdentifiers: String,
)

data class SeedCategory(
    val slug: String,
    val name: String,
    val icon: String,
    val colorToken: String,
)

val DEFAULT_ACCOUNTS = listOf(
    SeedAccount("bkash", "bKash", AccountType.MFS, "bKash"),
    SeedAccount("ebl", "EBL", AccountType.BANK, "EBL,EBLBANK"),
    SeedAccount("cash", "Cash", AccountType.CASH, ""),
)

val DEFAULT_CATEGORIES = listOf(
    SeedCategory("groceries", "Groceries", "shopping_cart", "category_green"),
    SeedCategory("eating_out", "Eating Out", "restaurant", "category_orange"),
    SeedCategory("transport", "Transport", "directions_car", "category_blue"),
    SeedCategory("fuel", "Fuel", "local_gas_station", "category_slate"),
    SeedCategory("bills", "Bills & Utilities", "receipt_long", "category_amber"),
    SeedCategory("mobile", "Mobile & Internet", "wifi", "category_teal"),
    SeedCategory("health", "Health", "medical_services", "category_red"),
    SeedCategory("shopping", "Shopping", "shopping_bag", "category_violet"),
    SeedCategory("entertainment", "Entertainment", "movie", "category_pink"),
    SeedCategory("education", "Education", "school", "category_indigo"),
    SeedCategory("family", "Family & Gifts", "volunteer_activism", "category_rose"),
    SeedCategory("car", "Car & Maintenance", "build", "category_bronze"),
    SeedCategory("fees", "Fees & Charges", "account_balance", "category_grey"),
    SeedCategory("income", "Income", "payments", "category_emerald"),
    SeedCategory("transfer", "Transfer", "swap_horiz", "category_neutral"),
    SeedCategory("uncategorized", "Uncategorized", "help_outline", "category_neutral"),
)
```

Seed rows use deterministic `seed-*` uuids rather than random ones so the same
logical category on two devices can be matched when sync is added.

- [ ] **Step 4: Implement the seeder**

Create `app/src/main/java/com/wasif/khata/core/data/seed/DatabaseSeeder.kt`:

```kotlin
package com.wasif.khata.core.data.seed

import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.time.KhataClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val clock: KhataClock,
) {

    suspend fun seedIfEmpty() {
        val now = clock.now()

        if (accountDao.countIncludingDeleted() == 0) {
            DEFAULT_ACCOUNTS.forEach { seed ->
                accountDao.upsert(
                    AccountEntity(
                        uuid = "seed-acc-${seed.slug}",
                        name = seed.name,
                        type = seed.type,
                        openingBalanceMinor = 0,
                        currentBalanceMinor = 0,
                        reportedBalanceMinor = null,
                        reportedBalanceAt = null,
                        includeInNetWorth = true,
                        smsIdentifiers = seed.smsIdentifiers,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }

        if (categoryDao.countIncludingDeleted() == 0) {
            categoryDao.upsertAll(
                DEFAULT_CATEGORIES.map { seed ->
                    CategoryEntity(
                        uuid = "seed-cat-${seed.slug}",
                        name = seed.name,
                        icon = seed.icon,
                        colorToken = seed.colorToken,
                        parentId = null,
                        isSystem = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            )
        }
    }
}
```

- [ ] **Step 5: Create the Application class**

Create `app/src/main/java/com/wasif/khata/KhataApplication.kt`:

```kotlin
package com.wasif.khata

import android.app.Application
import com.wasif.khata.core.data.seed.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class KhataApplication : Application() {

    @Inject lateinit var seeder: DatabaseSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { seeder.seedIfEmpty() }
    }
}
```

- [ ] **Step 6: Register the Application in the manifest**

In `app/src/main/AndroidManifest.xml`, add to the `<application>` tag:

```xml
android:name=".KhataApplication"
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*DatabaseSeederTest*"
```

Expected: PASS, 4 tests.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/wasif/khata/ app/src/main/AndroidManifest.xml app/src/test/java/com/wasif/khata/core/data/seed/
git commit -m "feat: seed default accounts and categories on first run"
```

---

## Design Checkpoint — RESOLVED

The visual direction is decided and recorded in
**`docs/superpowers/specs/khata-design-tokens.md`**. Task 7 transcribes that
document; it is not a placeholder and must not be re-derived.

**Direction:** warm editorial, restrained colour strategy, single ink-orange
accent. User-pinned, which overrides the direction roll (seed `24ecd5ef`).

Four decisions from that document bind the tasks below:

- **Hairline rules, not elevation.** Rows and sections separate with a 1dp
  `outlineVariant` line. Tonal elevation is reserved for the FAB, bottom sheets,
  and dialogs. No drop shadows on content.
- **Material You dynamic colour is not wired up at all.** Not wired and overridden
  — simply absent. A wallpaper-derived scheme cannot guarantee the debit/credit
  distinction or the accent survive, and the brief pins the palette.
- **One font family, the platform stack.** Bengali must render correctly in
  merchant names and notes; the system stack falls back to bundled Noto Sans
  Bengali. A bundled Latin display face would break that fallback exactly where it
  matters. Editorial character comes from the scale, not a second face.
- **Colour is quarantined.** Saturation appears only inside a chip, dot, or filled
  button, and is never the sole signal for any state.

---

### Task 7: Design system — tokens and theme

Every module consumes this; no module defines ad-hoc styling.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/ui/theme/Color.kt`
- Create: `app/src/main/java/com/wasif/khata/core/ui/theme/Type.kt`
- Create: `app/src/main/java/com/wasif/khata/core/ui/theme/Dimens.kt`
- Create: `app/src/main/java/com/wasif/khata/core/ui/theme/Motion.kt`
- Create: `app/src/main/java/com/wasif/khata/core/ui/theme/KhataTheme.kt`
- Create: `app/src/main/java/com/wasif/khata/core/ui/component/MoneyText.kt`
- Test: `app/src/test/java/com/wasif/khata/core/ui/theme/ContrastTest.kt`

**Interfaces:**
- Consumes: `Money` (Task 2), `TransactionDirection` (Task 3), `DEFAULT_CATEGORIES` (Task 6).
- Produces: `LightColors`/`DarkColors: ColorScheme`; `CategoryColorsLight`/`CategoryColorsDark: Map<String, Color>`; `contrastRatio(a: Color, b: Color): Double`; `KhataTypography: Typography`; `AmountTextStyle: TextStyle`; `Spacing`/`LocalSpacing`; `Motion`/`LocalMotion`; `KhataTheme(darkTheme, content)`; `MoneyText(money, modifier, direction, style)`.

- [ ] **Step 1: Write the failing contrast test**

Contrast is the one part of a design system that is objectively checkable, so it
gets a test rather than a reviewer's eye.

Create `app/src/test/java/com/wasif/khata/core/ui/theme/ContrastTest.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.wasif.khata.core.data.seed.DEFAULT_CATEGORIES
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    private fun assertAA(name: String, ratio: Double) {
        assertTrue("$name contrast was $ratio, below the 4.5:1 AA floor", ratio >= 4.5)
    }

    @Test
    fun `light palette text pairs meet WCAG AA`() {
        assertAA("onSurface", contrastRatio(LightColors.onSurface, LightColors.surface))
        assertAA("onBackground", contrastRatio(LightColors.onBackground, LightColors.background))
        assertAA("onPrimary", contrastRatio(LightColors.onPrimary, LightColors.primary))
        assertAA("onSurfaceVariant", contrastRatio(LightColors.onSurfaceVariant, LightColors.surfaceVariant))
        assertAA("onError", contrastRatio(LightColors.onError, LightColors.error))
    }

    @Test
    fun `dark palette text pairs meet WCAG AA`() {
        assertAA("onSurface", contrastRatio(DarkColors.onSurface, DarkColors.surface))
        assertAA("onBackground", contrastRatio(DarkColors.onBackground, DarkColors.background))
        assertAA("onPrimary", contrastRatio(DarkColors.onPrimary, DarkColors.primary))
        assertAA("onSurfaceVariant", contrastRatio(DarkColors.onSurfaceVariant, DarkColors.surfaceVariant))
        assertAA("onError", contrastRatio(DarkColors.onError, DarkColors.error))
    }

    @Test
    fun `every seeded category token resolves in both palettes`() {
        DEFAULT_CATEGORIES.map { it.colorToken }.distinct().forEach { token ->
            assertTrue("$token missing from CategoryColorsLight", CategoryColorsLight.containsKey(token))
            assertTrue("$token missing from CategoryColorsDark", CategoryColorsDark.containsKey(token))
        }
    }

    @Test
    fun `category colours are legible on their own surface`() {
        DEFAULT_CATEGORIES.map { it.colorToken }.distinct().forEach { token ->
            // 3:1 is the AA floor for graphical objects and large text, which is what
            // category dots and chip labels are.
            val light = contrastRatio(CategoryColorsLight.getValue(token), LightColors.surface)
            val dark = contrastRatio(CategoryColorsDark.getValue(token), DarkColors.surface)
            assertTrue("$token light contrast $light", light >= 3.0)
            assertTrue("$token dark contrast $dark", dark >= 3.0)
        }
    }

    @Test
    fun `contrast ratio is symmetric and bounded`() {
        assertTrue(contrastRatio(Color.White, Color.Black) in 20.9..21.1)
        assertTrue(contrastRatio(Color.Black, Color.White) in 20.9..21.1)
        assertTrue(contrastRatio(Color.White, Color.White) in 0.99..1.01)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "*ContrastTest*"
```

Expected: FAIL — `Unresolved reference: LightColors`.

- [ ] **Step 3: Create the colour tokens**

Warm bone grounds with real chroma, warm-black ink, one ink-orange accent. Values
transcribed from `khata-design-tokens.md`; do not substitute your own.

A brighter orange (`#E8590C`) was tested and rejected: white on it lands at 3.6:1
and warm-black at 4.6:1. Neither is enough for a screen read in direct sunlight,
which is a confirmed requirement. `#B23E06` is the brightest orange that carries
white text safely.

Create `app/src/main/java/com/wasif/khata/core/ui/theme/Color.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFB23E06),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE0CC),
    onPrimaryContainer = Color(0xFF431300),
    secondary = Color(0xFF5C5445),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F0E8),
    onBackground = Color(0xFF241E17),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF241E17),
    surfaceVariant = Color(0xFFE8E0D2),
    onSurfaceVariant = Color(0xFF554B3D),
    outline = Color(0xFF8A7D6B),
    outlineVariant = Color(0xFFD6CCBB),
    error = Color(0xFFA32116),
    onError = Color(0xFFFFFFFF),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFF9A62),
    onPrimary = Color(0xFF4A1600),
    primaryContainer = Color(0xFF8A2F03),
    onPrimaryContainer = Color(0xFFFFE0CC),
    secondary = Color(0xFFD6C7AC),
    onSecondary = Color(0xFF3A3223),
    background = Color(0xFF16130F),
    onBackground = Color(0xFFF0E8DA),
    surface = Color(0xFF1E1A15),
    onSurface = Color(0xFFF0E8DA),
    surfaceVariant = Color(0xFF332D25),
    onSurfaceVariant = Color(0xFFD3C8B6),
    outline = Color(0xFF9A8D7B),
    outlineVariant = Color(0xFF4A4237),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690006),
)

val CategoryColorsLight: Map<String, Color> = mapOf(
    "category_green" to Color(0xFF4A7C36),
    "category_orange" to Color(0xFFC2611F),
    "category_blue" to Color(0xFF2A5D8F),
    "category_slate" to Color(0xFF4F5D68),
    "category_amber" to Color(0xFF9C6F0A),
    "category_teal" to Color(0xFF1C6E63),
    "category_red" to Color(0xFFA32116),
    "category_violet" to Color(0xFF6B4A9E),
    "category_pink" to Color(0xFFA63A6B),
    "category_indigo" to Color(0xFF3B4A8F),
    "category_rose" to Color(0xFF9E3450),
    "category_bronze" to Color(0xFF7D5522),
    "category_grey" to Color(0xFF6B6255),
    "category_emerald" to Color(0xFF17694E),
    "category_neutral" to Color(0xFF756B5C),
)

val CategoryColorsDark: Map<String, Color> = mapOf(
    "category_green" to Color(0xFF93C47D),
    "category_orange" to Color(0xFFE9A06A),
    "category_blue" to Color(0xFF8CB4DC),
    "category_slate" to Color(0xFFA7B4BE),
    "category_amber" to Color(0xFFDDB55E),
    "category_teal" to Color(0xFF6FBDB0),
    "category_red" to Color(0xFFEC9C93),
    "category_violet" to Color(0xFFBCA3E0),
    "category_pink" to Color(0xFFE29BBB),
    "category_indigo" to Color(0xFFA3AEE0),
    "category_rose" to Color(0xFFE09CAB),
    "category_bronze" to Color(0xFFC9A472),
    "category_grey" to Color(0xFFB9B1A3),
    "category_emerald" to Color(0xFF6FC0A0),
    "category_neutral" to Color(0xFFB3AA9B),
)

fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) +
        0.7152 * channel(color.green) +
        0.0722 * channel(color.blue)
}
```

- [ ] **Step 4: Create the typography**

Create `app/src/main/java/com/wasif/khata/core/ui/theme/Type.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val KhataTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
    ),
)

// Tabular figures, so amounts align on the decimal down a ledger column instead of
// drifting with each digit's width. Not Monospace: that reads as a terminal, and
// this is a page.
val AmountTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    fontFeatureSettings = "tnum",
)
```

- [ ] **Step 5: Create the spacing and motion tokens**

Create `app/src/main/java/com/wasif/khata/core/ui/theme/Dimens.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val screenHorizontal: Dp = 16.dp,
    val minTouchTarget: Dp = 48.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
```

Create `app/src/main/java/com/wasif/khata/core/ui/theme/Motion.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.staticCompositionLocalOf

data class Motion(
    val quick: Int = 150,
    val standard: Int = 250,
    val emphasized: Int = 400,
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
)

val LocalMotion = staticCompositionLocalOf { Motion() }
```

- [ ] **Step 6: Create the theme**

Create `app/src/main/java/com/wasif/khata/core/ui/theme/KhataTheme.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalCategoryColors = staticCompositionLocalOf<Map<String, Color>> { emptyMap() }

@Composable
fun KhataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalMotion provides Motion(),
        LocalCategoryColors provides if (darkTheme) CategoryColorsDark else CategoryColorsLight,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = KhataTypography,
            content = content,
        )
    }
}
```

Khata uses its own palette rather than `dynamicColorScheme`. A wallpaper-derived
scheme cannot guarantee the debit/credit distinction stays legible, and that
distinction is load-bearing in a ledger.

- [ ] **Step 7: Create `MoneyText`**

Create `app/src/main/java/com/wasif/khata/core/ui/component/MoneyText.kt`:

```kotlin
package com.wasif.khata.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.ui.theme.AmountTextStyle

@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    direction: TransactionDirection? = null,
    style: TextStyle = AmountTextStyle,
) {
    val prefix = when (direction) {
        TransactionDirection.DEBIT -> "−"
        TransactionDirection.CREDIT -> "+"
        null -> ""
    }
    val color = when (direction) {
        TransactionDirection.CREDIT -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = prefix + money.format(),
        style = style,
        color = color,
        modifier = modifier,
    )
}
```

Direction is carried by a sign character as well as colour, so the ledger stays
readable in greyscale and for colour-blind users.

- [ ] **Step 8: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*ContrastTest*"
```

Expected: PASS, 5 tests. If a contrast assertion fails, the palette is wrong — fix
the hex values, never the threshold.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/ app/src/test/java/com/wasif/khata/core/ui/
git commit -m "feat: add Khata design system tokens, theme, and contrast tests"
```

---
### Task 8: Ledger ViewModel with day separators

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerItem.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerViewModel.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/ledger/LedgerViewModelTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository`, `TransactionDraft`, `Transaction` (Task 5), `toDhakaLocalDate` (Task 3).
- Produces: `sealed interface LedgerItem` with `Row(transaction: Transaction)` and `DayHeader(date: LocalDate)`; `LedgerViewModel.items: Flow<PagingData<LedgerItem>>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/feature/ledger/LedgerViewModelTest.kt`:

```kotlin
package com.wasif.khata.feature.ledger

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerViewModelTest {

    // Dhaka is UTC+6, so these two land on 26 August and the third on 25 August.
    private val aug26Midday = Instant.parse("2026-08-26T09:00:00Z").toEpochMilli()
    private val aug26Morning = Instant.parse("2026-08-26T04:00:00Z").toEpochMilli()
    private val aug25 = Instant.parse("2026-08-25T04:00:00Z").toEpochMilli()

    private fun transaction(id: Long, occurredAt: Long) = Transaction(
        id = id,
        uuid = "t-$id",
        accountId = 1,
        amount = Money(10_000),
        direction = TransactionDirection.DEBIT,
        occurredAt = occurredAt,
        merchantRaw = "SHWAPNO",
        merchantId = null,
        categoryId = null,
        note = null,
        source = TransactionSource.MANUAL,
        confidence = Confidence.HIGH,
        transferGroupId = null,
        updatedAt = occurredAt,
    )

    // cachedIn() turns this into a non-completable shared flow, so asSnapshot() can only
    // learn loading is done from LoadState, not from flowOf's own completion — hence
    // explicit end-of-pagination states. Without this the test hangs forever.
    private val endOfPagination = LoadStates(
        refresh = LoadState.NotLoading(endOfPaginationReached = true),
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true),
    )

    private fun repositoryReturning(vararg transactions: Transaction) = object : TransactionRepository {
        override fun pagedTransactions(): Flow<PagingData<Transaction>> =
            flowOf(PagingData.from(transactions.toList(), sourceLoadStates = endOfPagination))
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: TransactionDraft) = Result.success(0L)
        override suspend fun delete(id: Long) = Result.success(Unit)
    }

    @Test
    fun `a day header is inserted before the first transaction of each day`() = runTest {
        val viewModel = LedgerViewModel(
            repositoryReturning(
                transaction(1, aug26Midday),
                transaction(2, aug26Morning),
                transaction(3, aug25),
            )
        )

        val items = viewModel.items.asSnapshot()

        assertEquals(
            listOf(
                LedgerItem.DayHeader(LocalDate.of(2026, 8, 26)),
                LedgerItem.Row(transaction(1, aug26Midday)),
                LedgerItem.Row(transaction(2, aug26Morning)),
                LedgerItem.DayHeader(LocalDate.of(2026, 8, 25)),
                LedgerItem.Row(transaction(3, aug25)),
            ),
            items,
        )
    }

    @Test
    fun `transactions on the same Dhaka day share a single header`() = runTest {
        val viewModel = LedgerViewModel(
            repositoryReturning(transaction(1, aug26Midday), transaction(2, aug26Morning))
        )

        val headers = viewModel.items.asSnapshot().filterIsInstance<LedgerItem.DayHeader>()

        assertEquals(1, headers.size)
    }

    @Test
    fun `an evening UTC transaction is grouped under the next Dhaka day`() = runTest {
        val lateUtc = Instant.parse("2026-08-26T20:30:00Z").toEpochMilli()
        val viewModel = LedgerViewModel(repositoryReturning(transaction(1, lateUtc)))

        val header = viewModel.items.asSnapshot().first() as LedgerItem.DayHeader

        assertEquals(LocalDate.of(2026, 8, 27), header.date)
    }

    @Test
    fun `an empty ledger produces no headers`() = runTest {
        val viewModel = LedgerViewModel(repositoryReturning())

        assertEquals(emptyList<LedgerItem>(), viewModel.items.asSnapshot())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "*LedgerViewModelTest*"
```

Expected: FAIL — `Unresolved reference: LedgerViewModel`.

- [ ] **Step 3: Create `LedgerItem`**

Create `app/src/main/java/com/wasif/khata/feature/ledger/LedgerItem.kt`:

```kotlin
package com.wasif.khata.feature.ledger

import com.wasif.khata.domain.model.Transaction
import java.time.LocalDate

sealed interface LedgerItem {
    data class Row(val transaction: Transaction) : LedgerItem
    data class DayHeader(val date: LocalDate) : LedgerItem
}
```

- [ ] **Step 4: Implement the ViewModel**

Create `app/src/main/java/com/wasif/khata/feature/ledger/LedgerViewModel.kt`:

```kotlin
package com.wasif.khata.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@HiltViewModel
class LedgerViewModel @Inject constructor(
    repository: TransactionRepository,
) : ViewModel() {

    val items: Flow<PagingData<LedgerItem>> = repository.pagedTransactions()
        .map { paging ->
            paging.map { LedgerItem.Row(it) }
                // insertSeparators<T, R> widens Row to LedgerItem, so the generator
                // receives typed Rows and needs no casts.
                .insertSeparators<LedgerItem.Row, LedgerItem> { before, after ->
                    if (after == null) {
                        null
                    } else {
                        val afterDate = after.transaction.occurredAt.toDhakaLocalDate()
                        val beforeDate = before?.transaction?.occurredAt?.toDhakaLocalDate()
                        if (beforeDate != afterDate) LedgerItem.DayHeader(afterDate) else null
                    }
                }
        }
        .cachedIn(viewModelScope)
}
```

Separators are applied inside the same `Flow.map` as the item mapping so the
generator sees the already-mapped stream and can compare the last item of one page
with the first of the next.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*LedgerViewModelTest*"
```

Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/ledger/ app/src/test/java/com/wasif/khata/feature/ledger/
git commit -m "feat: add ledger view model with paged day-grouped items"
```

---

### Task 9: Ledger screen

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerScreen.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/ledger/LedgerScreenTest.kt`

**Interfaces:**
- Consumes: `LedgerViewModel`, `LedgerItem` (Task 8); `KhataTheme`, `MoneyText`, `LocalSpacing` (Task 7).
- Produces: `LedgerScreen(onAddTransaction: () -> Unit, onOpenTransaction: (Long) -> Unit, viewModel: LedgerViewModel)` and `LedgerContent(items: LazyPagingItems<LedgerItem>, onAddTransaction: () -> Unit, onOpenTransaction: (Long) -> Unit)`.

- [ ] **Step 1: Write the failing UI test**

Create `app/src/androidTest/java/com/wasif/khata/feature/ledger/LedgerScreenTest.kt`:

```kotlin
package com.wasif.khata.feature.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.domain.model.Transaction
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LedgerScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val sample = Transaction(
        id = 7,
        uuid = "t-7",
        accountId = 1,
        amount = Money(123_456),
        direction = TransactionDirection.DEBIT,
        occurredAt = Instant.parse("2026-08-26T04:00:00Z").toEpochMilli(),
        merchantRaw = "SHWAPNO",
        merchantId = null,
        categoryId = null,
        note = null,
        source = TransactionSource.MANUAL,
        confidence = Confidence.HIGH,
        transferGroupId = null,
        updatedAt = Instant.parse("2026-08-26T04:00:00Z").toEpochMilli(),
    )

    @Test
    fun `renders a day header and a transaction row`() {
        val data = flowOf(
            PagingData.from(
                listOf(
                    LedgerItem.DayHeader(LocalDate.of(2026, 8, 26)),
                    LedgerItem.Row(sample),
                )
            )
        )

        composeRule.setContent {
            KhataTheme {
                LedgerContent(
                    items = data.collectAsLazyPagingItems(),
                    onAddTransaction = {},
                    onOpenTransaction = {},
                )
            }
        }

        composeRule.onNodeWithText("SHWAPNO").assertIsDisplayed()
        composeRule.onNodeWithText("−৳1,234.56").assertIsDisplayed()
        composeRule.onNodeWithText("Wednesday, 26 August").assertIsDisplayed()
    }

    @Test
    fun `tapping a row reports the transaction id`() {
        var opened: Long? = null
        val data = flowOf(PagingData.from(listOf<LedgerItem>(LedgerItem.Row(sample))))

        composeRule.setContent {
            KhataTheme {
                LedgerContent(
                    items = data.collectAsLazyPagingItems(),
                    onAddTransaction = {},
                    onOpenTransaction = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("SHWAPNO").performClick()

        assertEquals(7L, opened)
    }

    @Test
    fun `the add button invokes its callback`() {
        var clicked = false
        val data = flowOf(PagingData.from(emptyList<LedgerItem>()))

        composeRule.setContent {
            KhataTheme {
                LedgerContent(
                    items = data.collectAsLazyPagingItems(),
                    onAddTransaction = { clicked = true },
                    onOpenTransaction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add transaction").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `an empty ledger shows guidance rather than a blank screen`() {
        val data = flowOf(PagingData.from(emptyList<LedgerItem>()))

        composeRule.setContent {
            KhataTheme {
                LedgerContent(
                    items = data.collectAsLazyPagingItems(),
                    onAddTransaction = {},
                    onOpenTransaction = {},
                )
            }
        }

        composeRule.onNodeWithText("No transactions yet").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Connect the Pixel 6a over USB with developer mode and USB debugging enabled, then:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.ledger.LedgerScreenTest
```

Expected: FAIL — `Unresolved reference: LedgerContent`.

- [ ] **Step 3: Implement the screen**

Create `app/src/main/java/com/wasif/khata/feature/ledger/LedgerScreen.kt`:

```kotlin
package com.wasif.khata.feature.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.theme.LocalSpacing
import java.time.format.DateTimeFormatter

@Composable
fun LedgerScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    LedgerContent(
        items = viewModel.items.collectAsLazyPagingItems(),
        onAddTransaction = onAddTransaction,
        onOpenTransaction = onOpenTransaction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerContent(
    items: LazyPagingItems<LedgerItem>,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ledger") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
    ) { padding ->
        if (items.itemCount == 0) {
            EmptyLedger(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                horizontal = spacing.screenHorizontal,
                vertical = spacing.sm,
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
                // Headers and rows are different shapes, so separate content types let
                // LazyColumn recycle each against its own pool rather than one mixed pool.
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
                        onClick = { onOpenTransaction(item.transaction.id) },
                    )
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun EmptyLedger(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = spacing.xl),
        ) {
            Text(
                text = "No transactions yet",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap the button below to record your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.sm),
            )
        }
    }
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
private fun DayHeaderRow(header: LedgerItem.DayHeader) {
    val spacing = LocalSpacing.current
    Text(
        text = header.date.format(dayFormatter),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.md, bottom = spacing.xs),
    )
}

@Composable
private fun TransactionRow(item: LedgerItem.Row, onClick: () -> Unit) {
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
        Column(modifier = Modifier.weight(1f).padding(end = spacing.sm)) {
            Text(
                text = transaction.merchantRaw ?: "Uncategorized",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            transaction.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MoneyText(money = transaction.amount, direction = transaction.direction)
        }
        // Editorial surfaces separate with a rule, never a shadow.
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.ledger.LedgerScreenTest
```

Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/ledger/LedgerScreen.kt app/src/androidTest/
git commit -m "feat: add paged ledger screen with day headers and empty state"
```

---
### Task 10: Transaction editor ViewModel

Four-bucket state with derived values as getters, so no `copy()` can produce an
inconsistent form. The constructor is defined in full here — including
`ReferenceDataRepository` — so that Task 11 adds a screen without changing a
signature these tests depend on.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/domain/repository/ReferenceDataRepository.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/repository/ReferenceDataRepositoryImpl.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/di/RepositoryModule.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorUiState.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorViewModel.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/editor/TransactionEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository`, `TransactionDraft`, `Account`, `Category` (Task 5); `Money` (Task 2); `KhataClock`, `TransactionDirection` (Task 3); `AccountDao`, `CategoryDao` (Task 4).
- Produces:
  - `interface ReferenceDataRepository`: `observeAccounts(): Flow<List<Account>>`, `observeCategories(): Flow<List<Category>>`.
  - `data class TransactionEditorUiState` with derived getters `amount`, `amountHasError`, `canSave`.
  - `sealed interface TransactionEditorEffect { Saved; Deleted }`.
  - `@Stable interface TransactionEditorActions` with 9 callbacks.
  - `TransactionEditorViewModel` with `@AssistedFactory interface Factory { fun create(transactionId: Long?): TransactionEditorViewModel }`, exposing `uiState: StateFlow<TransactionEditorUiState>` and `effects: Flow<TransactionEditorEffect>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/feature/editor/TransactionEditorViewModelTest.kt`:

```kotlin
package com.wasif.khata.feature.editor

import androidx.paging.PagingData
import app.cash.turbine.test
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.error.DataError
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private var savedDraft: TransactionDraft? = null
    private var saveResult: Result<Long> = Result.success(1L)

    private val repository = object : TransactionRepository {
        override fun pagedTransactions(): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: TransactionDraft): Result<Long> {
            savedDraft = draft
            return saveResult
        }
        override suspend fun delete(id: Long) = Result.success(Unit)
    }

    private val bkash = Account(
        id = 3,
        uuid = "acc-3",
        name = "bKash",
        type = AccountType.MFS,
        currentBalance = Money.ZERO,
        reportedBalance = null,
        includeInNetWorth = true,
    )

    private val groceries = Category(
        id = 11,
        uuid = "seed-cat-groceries",
        name = "Groceries",
        icon = "shopping_cart",
        colorToken = "category_green",
        parentId = null,
    )

    private val referenceData = object : ReferenceDataRepository {
        override fun observeAccounts(): Flow<List<Account>> = flowOf(listOf(bkash))
        override fun observeCategories(): Flow<List<Category>> = flowOf(listOf(groceries))
    }

    private val clock = object : KhataClock {
        override fun now(): Long = 7_000L
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(transactionId: Long? = null) =
        TransactionEditorViewModel(repository, referenceData, clock, transactionId)

    @Test
    fun `a new transaction defaults its timestamp to now`() {
        assertEquals(7_000L, viewModel().uiState.value.occurredAt)
    }

    @Test
    fun `reference data populates and preselects the first account`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(bkash), vm.uiState.value.accounts)
        assertEquals(listOf(groceries), vm.uiState.value.categories)
        assertEquals(3L, vm.uiState.value.accountId)
    }

    @Test
    fun `canSave is false until an amount is present`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.canSave)

        vm.onAmountChange("250")
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `a zero amount does not enable saving`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onAmountChange("0")

        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `an unparseable amount surfaces an error and blocks saving`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onAmountChange("12.345")

        assertFalse(vm.uiState.value.canSave)
        assertTrue(vm.uiState.value.amountHasError)
    }

    @Test
    fun `a blank amount is not an error, just incomplete`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.amountHasError)
        assertNull(vm.uiState.value.amount)
    }

    @Test
    fun `saving builds a draft from the form and reports success as an effect`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAmountChange("1,234.56")
        vm.onMerchantChange("SHWAPNO")
        vm.onCategorySelected(11L)
        vm.onDirectionChange(TransactionDirection.DEBIT)

        vm.effects.test {
            vm.onSave()
            assertEquals(TransactionEditorEffect.Saved, awaitItem())
        }

        assertEquals(Money(123_456), savedDraft?.amount)
        assertEquals(3L, savedDraft?.accountId)
        assertEquals(11L, savedDraft?.categoryId)
        assertEquals("SHWAPNO", savedDraft?.merchantRaw)
        assertEquals(TransactionDirection.DEBIT, savedDraft?.direction)
    }

    @Test
    fun `blank merchant and note are stored as null rather than empty strings`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAmountChange("250")

        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(savedDraft?.merchantRaw)
        assertNull(savedDraft?.note)
    }

    @Test
    fun `a save failure becomes durable state, not a fire-once effect`() = runTest(dispatcher) {
        saveResult = Result.failure(DataError.Storage)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAmountChange("250")

        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Could not save. Please try again.", vm.uiState.value.saveError)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `editing the amount clears a previous save error`() = runTest(dispatcher) {
        saveResult = Result.failure(DataError.Storage)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAmountChange("250")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onAmountChange("300")

        assertNull(vm.uiState.value.saveError)
    }
}
```

The failure case asserts durable state rather than an effect: an error still true
after rotation belongs in `UiState`.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "*TransactionEditorViewModelTest*"
```

Expected: FAIL — `Unresolved reference: ReferenceDataRepository`.

- [ ] **Step 3: Add the reference data repository**

Create `app/src/main/java/com/wasif/khata/domain/repository/ReferenceDataRepository.kt`:

```kotlin
package com.wasif.khata.domain.repository

import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface ReferenceDataRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeCategories(): Flow<List<Category>>
}
```

Create `app/src/main/java/com/wasif/khata/core/data/repository/ReferenceDataRepositoryImpl.kt`:

```kotlin
package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.repository.ReferenceDataRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReferenceDataRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) : ReferenceDataRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities -> entities.map { it.toDomain() } }
}
```

Add the binding to `app/src/main/java/com/wasif/khata/core/data/di/RepositoryModule.kt`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindReferenceDataRepository(
        impl: ReferenceDataRepositoryImpl,
    ): ReferenceDataRepository
```

- [ ] **Step 4: Create the UI state, effects, and actions**

Create `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorUiState.kt`:

```kotlin
package com.wasif.khata.feature.editor

import androidx.compose.runtime.Stable
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category

data class TransactionEditorUiState(
    val amountInput: String = "",
    val merchantInput: String = "",
    val noteInput: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val occurredAt: Long = 0L,

    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),

    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    // Derived as getters, never constructor params, so no copy() can leave a
    // validity flag disagreeing with the input it describes.
    val amount: Money? get() = if (amountInput.isBlank()) null else Money.parse(amountInput)

    val amountHasError: Boolean get() = amountInput.isNotBlank() && amount == null

    val canSave: Boolean
        get() = amount.let { it != null && !it.isZero } && accountId != null && !isSaving
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
    fun onDateChange(epochMillis: Long)
    fun onSave()
    fun onDelete()
}
```

- [ ] **Step 5: Implement the ViewModel**

Create `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorViewModel.kt`:

```kotlin
package com.wasif.khata.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Hilt needs the assisted factory named here to resolve hiltViewModel's
// generic <VM, VMF> overload; without it, injection silently falls back
// to a no-arg constructor and crashes at runtime.
@HiltViewModel(assistedFactory = TransactionEditorViewModel.Factory::class)
class TransactionEditorViewModel @AssistedInject constructor(
    private val repository: TransactionRepository,
    private val referenceData: ReferenceDataRepository,
    private val clock: KhataClock,
    @Assisted private val transactionId: Long?,
) : ViewModel(), TransactionEditorActions {

    @AssistedFactory
    interface Factory {
        fun create(transactionId: Long?): TransactionEditorViewModel
    }

    private val _uiState = MutableStateFlow(
        TransactionEditorUiState(occurredAt = clock.now(), isEditing = transactionId != null)
    )
    val uiState: StateFlow<TransactionEditorUiState> = _uiState.asStateFlow()

    // Channel, not SharedFlow: an effect emitted while the screen is backgrounded
    // buffers and replays on resume instead of being dropped.
    private val _effects = Channel<TransactionEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            referenceData.observeAccounts().collect { accounts ->
                _uiState.update { state ->
                    // Preselect so a new entry is one field closer to saveable.
                    state.copy(
                        accounts = accounts,
                        accountId = state.accountId ?: accounts.firstOrNull()?.id,
                    )
                }
            }
        }
        viewModelScope.launch {
            referenceData.observeCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        transactionId?.let { id ->
            viewModelScope.launch {
                repository.observe(id).collect { existing ->
                    if (existing == null) return@collect
                    _uiState.update {
                        it.copy(
                            amountInput = existing.amount.format(withSymbol = false),
                            merchantInput = existing.merchantRaw.orEmpty(),
                            noteInput = existing.note.orEmpty(),
                            accountId = existing.accountId,
                            categoryId = existing.categoryId,
                            direction = existing.direction,
                            occurredAt = existing.occurredAt,
                        )
                    }
                }
            }
        }
    }

    override fun onAmountChange(value: String) =
        _uiState.update { it.copy(amountInput = value, saveError = null) }

    override fun onMerchantChange(value: String) = _uiState.update { it.copy(merchantInput = value) }

    override fun onNoteChange(value: String) = _uiState.update { it.copy(noteInput = value) }

    override fun onAccountSelected(id: Long) = _uiState.update { it.copy(accountId = id) }

    override fun onCategorySelected(id: Long?) = _uiState.update { it.copy(categoryId = id) }

    override fun onDirectionChange(direction: TransactionDirection) =
        _uiState.update { it.copy(direction = direction) }

    override fun onDateChange(epochMillis: Long) = _uiState.update { it.copy(occurredAt = epochMillis) }

    override fun onSave() {
        val state = _uiState.value
        val amount = state.amount ?: return
        val accountId = state.accountId ?: return

        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            val result = repository.save(
                TransactionDraft(
                    id = transactionId,
                    accountId = accountId,
                    amount = amount,
                    direction = state.direction,
                    occurredAt = state.occurredAt,
                    merchantRaw = state.merchantInput.takeIf { it.isNotBlank() },
                    categoryId = state.categoryId,
                    note = state.noteInput.takeIf { it.isNotBlank() },
                )
            )
            _uiState.update { it.copy(isSaving = false) }
            result.fold(
                onSuccess = { _effects.trySend(TransactionEditorEffect.Saved) },
                onFailure = {
                    _uiState.update { s -> s.copy(saveError = "Could not save. Please try again.") }
                },
            )
        }
    }

    override fun onDelete() {
        val id = transactionId ?: return
        viewModelScope.launch {
            repository.delete(id).fold(
                onSuccess = { _effects.trySend(TransactionEditorEffect.Deleted) },
                onFailure = {
                    _uiState.update { it.copy(saveError = "Could not delete. Please try again.") }
                },
            )
        }
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*TransactionEditorViewModelTest*"
```

Expected: PASS, 10 tests.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/editor/ app/src/main/java/com/wasif/khata/domain/repository/ app/src/main/java/com/wasif/khata/core/data/ app/src/test/java/com/wasif/khata/feature/editor/
git commit -m "feat: add transaction editor view model with four-bucket state"
```

---

### Task 11: Transaction editor screen

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/editor/TransactionEditorScreenTest.kt`

**Interfaces:**
- Consumes: `TransactionEditorUiState`, `TransactionEditorActions`, `TransactionEditorEffect`, `TransactionEditorViewModel` (Task 10); `KhataTheme`, `LocalSpacing` (Task 7).
- Produces: `TransactionEditorScreen(onDone: () -> Unit, viewModel: TransactionEditorViewModel)` and `TransactionEditorContent(state: TransactionEditorUiState, actions: TransactionEditorActions, onBack: () -> Unit)`.

- [ ] **Step 1: Write the failing UI test**

Create `app/src/androidTest/java/com/wasif/khata/feature/editor/TransactionEditorScreenTest.kt`:

```kotlin
package com.wasif.khata.feature.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionEditorScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val bkash = Account(
        id = 3,
        uuid = "acc-3",
        name = "bKash",
        type = AccountType.MFS,
        currentBalance = Money.ZERO,
        reportedBalance = null,
        includeInNetWorth = true,
    )

    private val groceries = Category(
        id = 11,
        uuid = "seed-cat-groceries",
        name = "Groceries",
        icon = "shopping_cart",
        colorToken = "category_green",
        parentId = null,
    )

    private class RecordingActions : TransactionEditorActions {
        var amount: String? = null
        var selectedCategory: Long? = null
        var saved = false
        override fun onAmountChange(value: String) { amount = value }
        override fun onMerchantChange(value: String) = Unit
        override fun onNoteChange(value: String) = Unit
        override fun onAccountSelected(id: Long) = Unit
        override fun onCategorySelected(id: Long?) { selectedCategory = id }
        override fun onDirectionChange(direction: TransactionDirection) = Unit
        override fun onDateChange(epochMillis: Long) = Unit
        override fun onSave() { saved = true }
        override fun onDelete() = Unit
    }

    private fun setContent(state: TransactionEditorUiState, actions: TransactionEditorActions) {
        composeRule.setContent {
            KhataTheme {
                TransactionEditorContent(state = state, actions = actions, onBack = {})
            }
        }
    }

    @Test
    fun `save is disabled while the form is incomplete`() {
        setContent(
            TransactionEditorUiState(accounts = listOf(bkash), categories = listOf(groceries)),
            RecordingActions(),
        )

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `save is enabled once an amount and account are set`() {
        setContent(
            TransactionEditorUiState(
                amountInput = "250",
                accountId = 3,
                accounts = listOf(bkash),
                categories = listOf(groceries),
            ),
            RecordingActions(),
        )

        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `typing an amount reports the change`() {
        val actions = RecordingActions()
        setContent(TransactionEditorUiState(accounts = listOf(bkash)), actions)

        composeRule.onNodeWithText("Amount").performTextInput("250")

        assertEquals("250", actions.amount)
    }

    @Test
    fun `an invalid amount shows guidance`() {
        setContent(
            TransactionEditorUiState(amountInput = "12.345", accountId = 3, accounts = listOf(bkash)),
            RecordingActions(),
        )

        composeRule.onNodeWithText("Enter an amount like 1234.56").assertExists()
    }

    @Test
    fun `tapping a category reports its id`() {
        val actions = RecordingActions()
        setContent(
            TransactionEditorUiState(accounts = listOf(bkash), categories = listOf(groceries)),
            actions,
        )

        composeRule.onNodeWithText("Groceries").performClick()

        assertEquals(11L, actions.selectedCategory)
    }

    @Test
    fun `tapping save invokes the action`() {
        val actions = RecordingActions()
        setContent(
            TransactionEditorUiState(amountInput = "250", accountId = 3, accounts = listOf(bkash)),
            actions,
        )

        composeRule.onNodeWithText("Save").performClick()

        assertTrue(actions.saved)
    }

    @Test
    fun `a save error is displayed`() {
        setContent(
            TransactionEditorUiState(
                accounts = listOf(bkash),
                saveError = "Could not save. Please try again.",
            ),
            RecordingActions(),
        )

        composeRule.onNodeWithText("Could not save. Please try again.").assertExists()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.editor.TransactionEditorScreenTest
```

Expected: FAIL — `Unresolved reference: TransactionEditorContent`.

- [ ] **Step 3: Implement the screen**

Create `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt`:

```kotlin
package com.wasif.khata.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun TransactionEditorScreen(
    onDone: () -> Unit,
    viewModel: TransactionEditorViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    TransactionEditorEffect.Saved, TransactionEditorEffect.Deleted -> onDone()
                }
            }
        }
    }

    TransactionEditorContent(state = state, actions = viewModel, onBack = onDone)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorContent(
    state: TransactionEditorUiState,
    actions: TransactionEditorActions,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit transaction" else "New transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = actions::onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete transaction")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(spacing.screenHorizontal),
        ) {
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = actions::onAmountChange,
                label = { Text("Amount") },
                isError = state.amountHasError,
                supportingText = if (state.amountHasError) {
                    { Text("Enter an amount like 1234.56") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ChipSection(label = "Direction", topPadding = spacing.md) {
                TransactionDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = state.direction == direction,
                        onClick = { actions.onDirectionChange(direction) },
                        label = {
                            Text(if (direction == TransactionDirection.DEBIT) "Spent" else "Received")
                        },
                        modifier = Modifier.padding(end = spacing.sm),
                    )
                }
            }

            ChipSection(label = "Account", topPadding = spacing.md) {
                state.accounts.forEach { account ->
                    FilterChip(
                        selected = state.accountId == account.id,
                        onClick = { actions.onAccountSelected(account.id) },
                        label = { Text(account.name) },
                        modifier = Modifier.padding(end = spacing.sm),
                    )
                }
            }

            ChipSection(label = "Category", topPadding = spacing.md) {
                state.categories.forEach { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = { actions.onCategorySelected(category.id) },
                        label = { Text(category.name) },
                        modifier = Modifier.padding(end = spacing.sm),
                    )
                }
            }

            OutlinedTextField(
                value = state.merchantInput,
                onValueChange = actions::onMerchantChange,
                label = { Text("Merchant") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
            )

            OutlinedTextField(
                value = state.noteInput,
                onValueChange = actions::onNoteChange,
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
            )

            state.saveError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = spacing.md),
                )
            }

            Button(
                onClick = actions::onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.lg, bottom = spacing.xl),
            ) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(
    label: String,
    topPadding: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val spacing = LocalSpacing.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = topPadding, bottom = spacing.xs),
    )
    FlowRow(modifier = Modifier.fillMaxWidth()) { content() }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.editor.TransactionEditorScreenTest
```

Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt app/src/androidTest/
git commit -m "feat: add transaction editor screen"
```

---

### Task 12: Navigation, app wiring, and on-device verification

**Files:**
- Create: `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`
- Modify: `app/src/main/java/com/wasif/khata/MainActivity.kt`
- Test: manual on-device verification (checklist in Step 5)

**Interfaces:**
- Consumes: `LedgerScreen` (Task 9), `TransactionEditorScreen`, `TransactionEditorViewModel.Factory` (Tasks 10–11), `KhataTheme` (Task 7).
- Produces: `KhataNavHost()` — a running app.

- [ ] **Step 1: Create the nav host**

Two distinct destinations rather than a `-1` sentinel: "create" and "edit" are
different intents, and encoding one as a magic number invites a silent bug the day
an id of `-1` means something else.

Create `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`:

```kotlin
package com.wasif.khata.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wasif.khata.feature.editor.TransactionEditorScreen
import com.wasif.khata.feature.editor.TransactionEditorViewModel
import com.wasif.khata.feature.ledger.LedgerScreen

private const val ROUTE_LEDGER = "ledger"
private const val ROUTE_EDITOR_NEW = "editor/new"
private const val ROUTE_EDITOR_EDIT = "editor/edit/{transactionId}"
private const val ARG_TRANSACTION_ID = "transactionId"

@Composable
fun KhataNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_LEDGER) {
        composable(ROUTE_LEDGER) {
            LedgerScreen(
                onAddTransaction = { navController.navigate(ROUTE_EDITOR_NEW) },
                onOpenTransaction = { id -> navController.navigate("editor/edit/$id") },
            )
        }

        composable(ROUTE_EDITOR_NEW) {
            TransactionEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = editorViewModel(transactionId = null),
            )
        }

        composable(
            route = ROUTE_EDITOR_EDIT,
            arguments = listOf(navArgument(ARG_TRANSACTION_ID) { type = NavType.LongType }),
        ) { entry ->
            TransactionEditorScreen(
                onDone = { navController.popBackStack() },
                viewModel = editorViewModel(entry.arguments?.getLong(ARG_TRANSACTION_ID)),
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

- [ ] **Step 2: Wire `MainActivity`**

Replace the body of `app/src/main/java/com/wasif/khata/MainActivity.kt`:

```kotlin
package com.wasif.khata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.navigation.KhataNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KhataTheme {
                KhataNavHost()
            }
        }
    }
}
```

- [ ] **Step 3: Run the full unit test suite**

```bash
./gradlew testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. Every unit test from Tasks 1–11 passes.

- [ ] **Step 4: Install and launch on the Pixel 6a**

```bash
android run
```

Expected: the app builds, installs, and opens to the Ledger screen.

- [ ] **Step 5: Verify on device**

Confirm each behaviour before ticking:

- [ ] The Ledger opens showing "No transactions yet" and a visible add button.
- [ ] Tapping add opens the editor titled "New transaction", with today's date and bKash preselected.
- [ ] Save is disabled until an amount is entered.
- [ ] Entering `12.345` shows "Enter an amount like 1234.56" and keeps Save disabled.
- [ ] Entering `0` keeps Save disabled.
- [ ] Entering `1234.56`, choosing Spent, bKash, and Groceries, then saving returns to the Ledger.
- [ ] The transaction appears under a day header showing `−৳1,234.56`.
- [ ] Tapping the row opens it titled "Edit transaction" with every field populated.
- [ ] Changing the amount to `500` and saving updates the existing row rather than adding a second.
- [ ] Deleting from the editor removes the row and returns to the Ledger.
- [ ] Adding a transaction dated yesterday produces a second, correctly ordered day header.
- [ ] Rotating the device on the editor preserves typed input.
- [ ] Switching the system to dark mode restyles both screens with no unreadable text.
- [ ] Scrolling a list of 50+ transactions stays smooth with no visible stutter.

- [ ] **Step 6: Capture a screenshot for the record**

```bash
android screenshot -o docs/screenshots/plan1-ledger.png
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/navigation/ app/src/main/java/com/wasif/khata/MainActivity.kt docs/screenshots/
git commit -m "feat: wire navigation between ledger and transaction editor"
```

---

## Done when

- `./gradlew testDebugUnitTest` passes — 51 unit tests across Tasks 1–11.
- `./gradlew connectedDebugAndroidTest` passes — 11 UI tests across Tasks 9 and 11.
- The app installs on the Pixel 6a and every item in Task 12 Step 5 is verified.
- `app/schemas/com.wasif.khata.core.data.KhataDatabase/1.json` is committed. Plan 2's migration depends on it.

## Explicitly deferred to later plans

Tables not created here — `raw_messages`, `parsing_rules`, `places`, `media`,
`tags`, `search_fts`, `budgets`, `balance_snapshots` — are added by the plan that
first needs them, via migrations from schema v1.

Features: SMS ingestion (Plan 2) · AI fallback (Plan 3) · insights, budgets, and
net worth (Plan 4) · Glance widget and cash flow (Plan 5) · backup (Plan 6).
