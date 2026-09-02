# Khata Plan 2a — SMS Ingestion (headless)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline) or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khata reads the bKash and EBL messages already on the phone and turns them
into a ledger the user never had to keep — offline, rules-only, with no UI work.

**Architecture:** Messages arrive through a `MessageSource` interface, are stored raw
and permanently, then run through a priority-ordered rule engine whose IGNORE rules
sort above every extracting rule. Parsed results are deduplicated on the provider's
own transaction id, resolved against a merchant memory, paired into transfers, and
written inside one transaction alongside a balance reconciliation.

**Tech Stack:** Kotlin · Room + KSP · Hilt · Coroutines/Flow · WorkManager ·
JUnit4 + Robolectric + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-02-plan2a-sms-ingestion-design.md`
**Corpus:** `docs/superpowers/specs/sms-corpus.md` — 40 real messages

## Global Constraints

Every task's requirements implicitly include this section.

- **This plan is headless.** It touches **no** Compose file, no file under `core/ui/`, no theme token, and no screen. A concurrent session owns the design system. If a task seems to need UI, stop and report — it belongs to Plan 2b.
- `minSdk 33`, `compileSdk` / `targetSdk 37`, package `com.wasif.khata`.
- Money is always `Long` **paisa**. Never `Double`, never `Float`, anywhere.
- Currency is BDT only.
- Every table carries `uuid TEXT NOT NULL`, `createdAt INTEGER NOT NULL`, `updatedAt INTEGER NOT NULL`, `deletedAt INTEGER` (nullable). Deletes are soft.
- Instants are UTC epoch millis. Every day, month, and period boundary is computed in `Asia/Dhaka`.
- **Raw messages are never deleted.** Parsing must stay idempotent and re-runnable.
- **IGNORE rules evaluate before every extracting rule.** An OTP message carries a real amount and a real merchant; recording it double-counts spending with nothing to catch it.
- No database, parsing, or background work on the main thread, ever.
- DI is Hilt + KSP. Async is Coroutines/Flow — no `LiveData`.
- Repositories map platform exceptions to `DataError` at the boundary. Platform exceptions never leak past a repository.
- Tests never hardcode a magic epoch-millis literal. Build instants with `Instant.parse("…Z").toEpochMilli()`.
- Corpus test messages are copied **verbatim** from `sms-corpus.md`. Never paraphrase, retype from memory, or "clean up" a sample — the whitespace and punctuation are the thing under test.
- Comments earn their place: a non-obvious *why*, never a restatement of *what*. No section-divider banners, no KDoc restating a name.
- No network calls. No Gemini. Plan 2a is rules-only; unmatched messages wait.

## Environment

- Worktree: `D:\Khata\.claude\worktrees\plan2a-sms-ingestion`, branch `plan2a/sms-ingestion`.
- JDK: `E:\Android\Android Studio\jbr`. Export `JAVA_HOME` before Gradle in every shell.
- Robolectric is pinned to `sdk=35` in `app/src/test/resources/robolectric.properties`. Leave it.
- Unit tests only: `./gradlew testDebugUnitTest`. No instrumented tests in this plan.

---

### Task 1: Schema v2 — new tables, new columns, real migration

The first genuine migration in this project. It is written against the exported
`1.json` and tested, not waved through with `fallbackToDestructiveMigration`.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/RawMessageEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/ParsingRuleEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/model/Enums.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/entity/TransactionEntity.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/seed/DefaultData.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/migration/Migration1To2Test.kt`

**Interfaces:**
- Consumes: existing schema v1 (`accounts`, `categories`, `transactions`, `merchants`, `merchant_aliases`).
- Produces:
  - `enum class RawMessageStatus { PENDING, PARSED, UNMATCHED, IGNORED }`
  - `enum class RuleKind { NORMAL, TRANSFER_OUT, TRANSFER_IN, ATM_WITHDRAWAL, FEE, LOAN_DISBURSEMENT, IGNORE }`
  - `enum class TransactionKind { NORMAL, TRANSFER, LOAN_DISBURSEMENT, LOAN_REPAYMENT, FEE, ADJUSTMENT }`
  - `RawMessageEntity`, `ParsingRuleEntity`
  - `TransactionEntity.providerTxnId: String?`, `TransactionEntity.kind: TransactionKind`
  - `MIGRATION_1_2: Migration`
  - `KhataDatabase` at `version = 2`

- [ ] **Step 1: Write the failing migration test**

Room's `MigrationTestHelper` normally runs instrumented. It works under Robolectric
when given an explicit `SupportSQLiteOpenHelper.Factory`.

Create `app/src/test/java/com/wasif/khata/core/data/migration/Migration1To2Test.kt`:

```kotlin
package com.wasif.khata.core.data.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TEST_DB = "migration-test.db"

@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        KhataDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migrating v1 to v2 preserves existing transactions`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO accounts (uuid, name, type, openingBalanceMinor, currentBalanceMinor, " +
                    "reportedBalanceMinor, reportedBalanceAt, includeInNetWorth, smsIdentifiers, " +
                    "createdAt, updatedAt, deletedAt) VALUES " +
                    "('seed-acc-ebl','EBL','BANK',0,10000,NULL,NULL,1,'EBL,EBLBANK',1,1,NULL)"
            )
            db.execSQL(
                "INSERT INTO transactions (uuid, accountId, amountMinor, direction, occurredAt, " +
                    "merchantRaw, merchantId, categoryId, note, source, confidence, rawMessageId, " +
                    "transferGroupId, feeMinor, referenceNumber, createdAt, updatedAt, deletedAt) VALUES " +
                    "('t-1',1,25000,'DEBIT',1000,'SHWAPNO',NULL,NULL,NULL,'MANUAL','HIGH',NULL," +
                    "NULL,NULL,NULL,1,1,NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT uuid, amountMinor, kind, providerTxnId FROM transactions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("t-1", c.getString(0))
            assertEquals(25000L, c.getLong(1))
            assertEquals("NORMAL", c.getString(2))
            assertTrue(c.isNull(3))
        }
    }

    @Test
    fun `migrating renames the single EBL account rather than orphaning its transactions`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO accounts (uuid, name, type, openingBalanceMinor, currentBalanceMinor, " +
                    "reportedBalanceMinor, reportedBalanceAt, includeInNetWorth, smsIdentifiers, " +
                    "createdAt, updatedAt, deletedAt) VALUES " +
                    "('seed-acc-ebl','EBL','BANK',0,0,NULL,NULL,1,'EBL,EBLBANK',1,1,NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT uuid, name, smsIdentifiers FROM accounts WHERE uuid = 'seed-acc-ebl'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("EBL Salary", c.getString(1))
            assertEquals("352", c.getString(2))
        }
        db.query("SELECT name, smsIdentifiers FROM accounts WHERE uuid = 'seed-acc-ebl-student'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("EBL Student", c.getString(0))
            assertEquals("286", c.getString(1))
        }
    }

    @Test
    fun `migration creates raw_messages and parsing_rules`() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            val tables = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertTrue("raw_messages missing", tables.contains("raw_messages"))
            assertTrue("parsing_rules missing", tables.contains("parsing_rules"))
        }
    }

    @Test
    fun `providerTxnId is unique but permits many nulls`() {
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.execSQL(
            "INSERT INTO accounts (uuid, name, type, openingBalanceMinor, currentBalanceMinor, " +
                "reportedBalanceMinor, reportedBalanceAt, includeInNetWorth, smsIdentifiers, " +
                "createdAt, updatedAt, deletedAt) VALUES ('a','A','CASH',0,0,NULL,NULL,1,'',1,1,NULL)"
        )
        repeat(2) { i ->
            db.execSQL(
                "INSERT INTO transactions (uuid, accountId, amountMinor, direction, occurredAt, " +
                    "merchantRaw, merchantId, categoryId, note, source, confidence, rawMessageId, " +
                    "transferGroupId, feeMinor, referenceNumber, providerTxnId, kind, " +
                    "createdAt, updatedAt, deletedAt) VALUES " +
                    "('n-$i',1,1,'DEBIT',1,NULL,NULL,NULL,NULL,'SMS','HIGH',NULL,NULL,NULL,NULL," +
                    "NULL,'NORMAL',1,1,NULL)"
            )
        }

        db.query("SELECT COUNT(*) FROM transactions").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="E:/Android/Android Studio/jbr"
./gradlew testDebugUnitTest --tests "*Migration1To2Test*"
```

Expected: FAIL — `Unresolved reference: MIGRATION_1_2`.

- [ ] **Step 3: Add the enums**

Append to `app/src/main/java/com/wasif/khata/core/model/Enums.kt`:

```kotlin
enum class RawMessageStatus { PENDING, PARSED, UNMATCHED, IGNORED }

enum class RuleKind { NORMAL, TRANSFER_OUT, TRANSFER_IN, ATM_WITHDRAWAL, FEE, LOAN_DISBURSEMENT, IGNORE }

enum class TransactionKind { NORMAL, TRANSFER, LOAN_DISBURSEMENT, LOAN_REPAYMENT, FEE, ADJUSTMENT }
```

`TransactionKind` is schema-level rather than a category because categories are
user-editable; a renamed or deleted category must not be able to break net-worth
arithmetic.

- [ ] **Step 4: Create the new entities**

Create `app/src/main/java/com/wasif/khata/core/data/entity/RawMessageEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wasif.khata.core.model.RawMessageStatus

@Entity(
    tableName = "raw_messages",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["sender", "bodyHash", "receivedAt"], unique = true),
        Index(value = ["status"]),
    ],
)
data class RawMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val bodyHash: String,
    val status: RawMessageStatus,
    val matchedRuleId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

The unique index on (`sender`, `bodyHash`, `receivedAt`) is what makes backfill
idempotent — re-scanning the inbox inserts nothing new.

Create `app/src/main/java/com/wasif/khata/core/data/entity/ParsingRuleEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection

@Entity(
    tableName = "parsing_rules",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["priority"])],
)
data class ParsingRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val senderPattern: String,
    val bodyPattern: String,
    val direction: TransactionDirection?,
    val kind: RuleKind,
    val priority: Int,
    val origin: String,
    val isEnabled: Boolean,
    val sampleMessage: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`direction` is nullable because IGNORE rules have none.

- [ ] **Step 5: Extend `TransactionEntity`**

In `app/src/main/java/com/wasif/khata/core/data/entity/TransactionEntity.kt`, add two
fields before the audit columns, and add the unique index:

```kotlin
    val providerTxnId: String?,
    val kind: TransactionKind,
```

and in the `indices` list:

```kotlin
        Index(value = ["providerTxnId"], unique = true),
```

SQLite treats NULLs as distinct in a unique index, so manual and EBL transactions —
which have no provider id — coexist freely.

- [ ] **Step 6: Write the migration**

Create `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt`:

```kotlin
package com.wasif.khata.core.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `raw_messages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `sender` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                "`receivedAt` INTEGER NOT NULL, `bodyHash` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `matchedRuleId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_messages_uuid` ON `raw_messages` (`uuid`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_messages_sender_bodyHash_receivedAt` " +
                "ON `raw_messages` (`sender`, `bodyHash`, `receivedAt`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_messages_status` ON `raw_messages` (`status`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `parsing_rules` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `senderPattern` TEXT NOT NULL, " +
                "`bodyPattern` TEXT NOT NULL, `direction` TEXT, `kind` TEXT NOT NULL, " +
                "`priority` INTEGER NOT NULL, `origin` TEXT NOT NULL, " +
                "`isEnabled` INTEGER NOT NULL, `sampleMessage` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_parsing_rules_uuid` ON `parsing_rules` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parsing_rules_priority` ON `parsing_rules` (`priority`)")

        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `providerTxnId` TEXT")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'NORMAL'")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_providerTxnId` " +
                "ON `transactions` (`providerTxnId`)"
        )

        // Rename rather than insert: existing transactions point at seed-acc-ebl by id,
        // and a fresh row would orphan every one of them.
        db.execSQL(
            "UPDATE `accounts` SET `name` = 'EBL Salary', `smsIdentifiers` = '352' " +
                "WHERE `uuid` = 'seed-acc-ebl'"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `accounts` (`uuid`, `name`, `type`, `openingBalanceMinor`, " +
                "`currentBalanceMinor`, `reportedBalanceMinor`, `reportedBalanceAt`, " +
                "`includeInNetWorth`, `smsIdentifiers`, `createdAt`, `updatedAt`, `deletedAt`) " +
                "VALUES ('seed-acc-ebl-student', 'EBL Student', 'BANK', 0, 0, NULL, NULL, 1, " +
                "'286', 0, 0, NULL)"
        )
    }
}
```

- [ ] **Step 7: Register the entities and migration**

In `KhataDatabase.kt`, add `RawMessageEntity::class` and `ParsingRuleEntity::class`
to `entities`, and change `version = 1` to `version = 2`.

In `DatabaseModule.kt`, add the migration to the builder:

```kotlin
        Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db")
            .addMigrations(MIGRATION_1_2)
            .build()
```

- [ ] **Step 8: Update the seed for fresh installs**

In `app/src/main/java/com/wasif/khata/core/data/seed/DefaultData.kt`, replace the
single EBL entry so a fresh install matches what the migration produces:

```kotlin
val DEFAULT_ACCOUNTS = listOf(
    SeedAccount("bkash", "bKash", AccountType.MFS, "bKash"),
    SeedAccount("ebl", "EBL Salary", AccountType.BANK, "352"),
    SeedAccount("ebl-student", "EBL Student", AccountType.BANK, "286"),
    SeedAccount("cash", "Cash", AccountType.CASH, ""),
)
```

The `ebl` slug is unchanged so a fresh install and a migrated install agree on
`seed-acc-ebl`.

- [ ] **Step 9: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*Migration1To2Test*"
```

Expected: PASS, 4 tests. Then run the whole suite — the seeder test asserts an
account count and will need its expectation updated from 3 to 4:

```bash
./gradlew testDebugUnitTest
```

- [ ] **Step 10: Confirm schema v2 was exported**

```bash
ls app/schemas/com.wasif.khata.core.data.KhataDatabase/
```

Expected: `1.json` and `2.json`. If `2.json` is absent, stop — Plan 3's migration
will depend on it.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: schema v2 with raw_messages, parsing_rules, and a tested migration"
```

---
### Task 2: Normalisers — amounts, datetimes, account masks

Pure Kotlin, no Android, no Room. Every rule depends on these three, and each one
has more variation in the real corpus than the spec assumed.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/Normalisers.kt`
- Test: `app/src/test/java/com/wasif/khata/core/sms/NormalisersTest.kt`

**Interfaces:**
- Consumes: `Money` (`core/model/Money.kt`), `DHAKA` (`core/time/KhataClock.kt`).
- Produces:
  - `fun parseAmount(text: String): Money?`
  - `fun parseEblDateTime(text: String): Long?`
  - `fun parseBkashDateTime(text: String): Long?`
  - `fun accountTail(masked: String): String?`

- [ ] **Step 1: Write the failing tests**

Every literal below is copied verbatim from `sms-corpus.md`.

Create `app/src/test/java/com/wasif/khata/core/sms/NormalisersTest.kt`:

```kotlin
package com.wasif.khata.core.sms

import com.wasif.khata.core.model.Money
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalisersTest {

    @Test
    fun `parseAmount accepts a space between unit and digits`() {
        assertEquals(Money(6000), parseAmount("BDT 60"))
        assertEquals(Money(1731600), parseAmount("BDT 17316"))
    }

    @Test
    fun `parseAmount accepts no space between unit and digits`() {
        assertEquals(Money(500000), parseAmount("BDT5000"))
    }

    @Test
    fun `parseAmount accepts thousands separators`() {
        assertEquals(Money(260000), parseAmount("Tk 2,600.00"))
        assertEquals(Money(102711), parseAmount("Tk 1,027.11"))
    }

    @Test
    fun `parseAmount accepts a period between unit and digits`() {
        assertEquals(Money(75000), parseAmount("Tk.750.00"))
    }

    @Test
    fun `parseAmount accepts a bare number with no unit`() {
        assertEquals(Money(5856), parseAmount("58.56"))
        assertEquals(Money(420), parseAmount("4.20"))
    }

    @Test
    fun `parseAmount handles one and two decimal places and zero`() {
        assertEquals(Money(500420), parseAmount("BDT 5004.2"))
        assertEquals(Money(0), parseAmount("Tk 0.00"))
        assertEquals(Money(290), parseAmount("Tk 2.90"))
    }

    @Test
    fun `parseAmount is case insensitive about the unit`() {
        assertEquals(Money(30928), parseAmount("TK 309.28"))
    }

    @Test
    fun `parseAmount rejects text with no number`() {
        assertNull(parseAmount("Thanks. EBL Helpline"))
        assertNull(parseAmount(""))
    }

    @Test
    fun `parseEblDateTime reads an uppercase month`() {
        assertEquals(
            Instant.parse("2026-09-01T12:46:08Z").toEpochMilli(),
            parseEblDateTime("01-SEP-26 06:46:08 PM"),
        )
    }

    @Test
    fun `parseEblDateTime reads a mixed case month`() {
        assertEquals(
            Instant.parse("2026-08-31T12:27:44Z").toEpochMilli(),
            parseEblDateTime("31-Aug-26 06:27:44 PM"),
        )
    }

    @Test
    fun `parseEblDateTime reads a midnight hour correctly`() {
        assertEquals(
            Instant.parse("2026-08-17T18:44:55Z").toEpochMilli(),
            parseEblDateTime("18-Aug-26 12:44:55 AM"),
        )
    }

    @Test
    fun `parseBkashDateTime reads a 24 hour timestamp`() {
        assertEquals(
            Instant.parse("2026-08-21T06:35:00Z").toEpochMilli(),
            parseBkashDateTime("21/08/2026 12:35"),
        )
        assertEquals(
            Instant.parse("2026-08-01T18:38:00Z").toEpochMilli(),
            parseBkashDateTime("02/08/2026 00:38"),
        )
    }

    @Test
    fun `datetime parsers reject the other providers format`() {
        assertNull(parseEblDateTime("21/08/2026 12:35"))
        assertNull(parseBkashDateTime("01-SEP-26 06:46:08 PM"))
    }

    @Test
    fun `accountTail normalises both masks of the same account`() {
        assertEquals("352", accountTail("115***352"))
        assertEquals("352", accountTail("115**9352"))
        assertEquals("286", accountTail("112***286"))
        assertEquals("286", accountTail("112**0286"))
    }

    @Test
    fun `accountTail rejects input with fewer than three digits`() {
        assertNull(accountTail("**"))
        assertNull(accountTail("A/C"))
        assertNull(accountTail(""))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
export JAVA_HOME="E:/Android/Android Studio/jbr"
./gradlew testDebugUnitTest --tests "*NormalisersTest*"
```

Expected: FAIL — `Unresolved reference: parseAmount`.

- [ ] **Step 3: Implement the normalisers**

Create `app/src/main/java/com/wasif/khata/core/sms/Normalisers.kt`:

```kotlin
package com.wasif.khata.core.sms

import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.DHAKA
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

private val AMOUNT = Regex(
    """(?:BDT|TK)?\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
    RegexOption.IGNORE_CASE,
)

fun parseAmount(text: String): Money? {
    val digits = AMOUNT.find(text)?.groupValues?.get(1) ?: return null
    return Money.parse(digits.replace(",", ""))
}

// parseCaseInsensitive because EBL writes the month as SEP in transfer messages and
// Aug in card messages, for the same field.
private val EBL_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("dd-MMM-yy hh:mm:ss a")
    .toFormatter(Locale.ENGLISH)

private val BKASH_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH)

private fun LocalDateTime.toDhakaMillis(): Long =
    atZone(DHAKA).toInstant().toEpochMilli()

fun parseEblDateTime(text: String): Long? = try {
    LocalDateTime.parse(text.trim(), EBL_FORMAT).toDhakaMillis()
} catch (e: DateTimeParseException) {
    null
}

fun parseBkashDateTime(text: String): Long? = try {
    LocalDateTime.parse(text.trim(), BKASH_FORMAT).toDhakaMillis()
} catch (e: DateTimeParseException) {
    null
}

// Both maskings of one account expose the last three digits and nothing else in
// common: 115***352 and 115**9352 are the same account.
fun accountTail(masked: String): String? =
    masked.filter { it.isDigit() }.takeIf { it.length >= 3 }?.takeLast(3)
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*NormalisersTest*"
```

Expected: PASS, 15 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/sms/ app/src/test/java/com/wasif/khata/core/sms/
git commit -m "feat: add SMS amount, datetime, and account-mask normalisers"
```

---

### Task 3: Rule model and engine

The engine's whole job is to apply rules **in priority order** and stop at the first
match. Priority is what keeps OTP messages from becoming transactions.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/ParsedMessage.kt`
- Create: `app/src/main/java/com/wasif/khata/core/sms/RuleEngine.kt`
- Test: `app/src/test/java/com/wasif/khata/core/sms/RuleEngineTest.kt`

**Interfaces:**
- Consumes: `Normalisers` (Task 2), `ParsingRuleEntity`, `RuleKind`, `TransactionDirection`.
- Produces:
  - `data class ParsedMessage(ruleId, ruleName, kind, direction, amount: Money, balance: Money?, merchant: String?, accountTail: String?, providerTxnId: String?, occurredAt: Long?, feeMinor: Long?)`
  - `sealed interface ParseOutcome { data class Parsed(val value: ParsedMessage); data class Ignored(val ruleId: Long, val ruleName: String); data object Unmatched }`
  - `class RuleEngine { fun parse(sender: String, body: String, rules: List<ParsingRuleEntity>): ParseOutcome }`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/wasif/khata/core/sms/RuleEngineTest.kt`:

```kotlin
package com.wasif.khata.core.sms

import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val engine = RuleEngine()

    private fun rule(
        id: Long,
        name: String,
        sender: String,
        body: String,
        kind: RuleKind = RuleKind.NORMAL,
        direction: TransactionDirection? = TransactionDirection.DEBIT,
        priority: Int = 100,
        enabled: Boolean = true,
    ) = ParsingRuleEntity(
        id = id,
        uuid = "r-$id",
        name = name,
        senderPattern = sender,
        bodyPattern = body,
        direction = direction,
        kind = kind,
        priority = priority,
        origin = "BUILTIN",
        isEnabled = enabled,
        sampleMessage = "",
        createdAt = 0,
        updatedAt = 0,
    )

    private val paymentRule = rule(
        id = 1,
        name = "bkash payment",
        sender = "bKash",
        body = """Payment of (?<amount>Tk [\d,.]+) to (?<merchant>.+?) is successful\. Balance (?<balance>Tk [\d,.]+)\. TrxID (?<refId>\w+)""",
    )

    private val otpRule = rule(
        id = 2,
        name = "bkash otp",
        sender = "bKash",
        body = """Do NOT share your OTP""",
        kind = RuleKind.IGNORE,
        direction = null,
        priority = 10,
    )

    @Test
    fun `a matching rule yields a parsed message`() {
        val outcome = engine.parse(
            sender = "bKash",
            body = "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01",
            rules = listOf(paymentRule),
        )

        val parsed = (outcome as ParseOutcome.Parsed).value
        assertEquals(Money(85600), parsed.amount)
        assertEquals(Money(4198), parsed.balance)
        assertEquals("FOODPANDA BANGLADESH LIMITED", parsed.merchant)
        assertEquals("DHV41FIPGY", parsed.providerTxnId)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
    }

    @Test
    fun `an IGNORE rule wins over an extracting rule regardless of list order`() {
        val body = "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min."

        // Extracting rule listed first; priority must still decide.
        val outcome = engine.parse("bKash", body, listOf(paymentRule, otpRule))

        assertTrue(outcome is ParseOutcome.Ignored)
        assertEquals("bkash otp", (outcome as ParseOutcome.Ignored).ruleName)
    }

    @Test
    fun `a rule whose sender does not match is skipped`() {
        val outcome = engine.parse(
            sender = "EBL",
            body = "Payment of Tk 856.00 to X is successful. Balance Tk 1.00. TrxID ABC",
            rules = listOf(paymentRule),
        )

        assertEquals(ParseOutcome.Unmatched, outcome)
    }

    @Test
    fun `a disabled rule never matches`() {
        val outcome = engine.parse(
            sender = "bKash",
            body = "Payment of Tk 856.00 to X is successful. Balance Tk 1.00. TrxID ABC",
            rules = listOf(paymentRule.copy(isEnabled = false)),
        )

        assertEquals(ParseOutcome.Unmatched, outcome)
    }

    @Test
    fun `no matching rule yields Unmatched rather than a guess`() {
        val outcome = engine.parse("bKash", "Some entirely new format", listOf(paymentRule, otpRule))

        assertEquals(ParseOutcome.Unmatched, outcome)
    }

    @Test
    fun `a malformed rule pattern is skipped instead of crashing the pipeline`() {
        val broken = rule(id = 3, name = "broken", sender = "bKash", body = "(?<unclosed", priority = 1)

        val outcome = engine.parse(
            sender = "bKash",
            body = "Payment of Tk 856.00 to X is successful. Balance Tk 1.00. TrxID ABC",
            rules = listOf(broken, paymentRule),
        )

        assertTrue(outcome is ParseOutcome.Parsed)
    }

    @Test
    fun `absent optional groups become null rather than empty strings`() {
        val minimal = rule(
            id = 4,
            name = "amount only",
            sender = "bKash",
            body = """Cashback (?<amount>Tk [\d,.]+)""",
            direction = TransactionDirection.CREDIT,
        )

        val parsed = (engine.parse("bKash", "Cashback Tk 2.90", listOf(minimal)) as ParseOutcome.Parsed).value

        assertEquals(Money(290), parsed.amount)
        assertEquals(null, parsed.merchant)
        assertEquals(null, parsed.balance)
        assertEquals(null, parsed.providerTxnId)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "*RuleEngineTest*"
```

Expected: FAIL — `Unresolved reference: RuleEngine`.

- [ ] **Step 3: Create the result types**

Create `app/src/main/java/com/wasif/khata/core/sms/ParsedMessage.kt`:

```kotlin
package com.wasif.khata.core.sms

import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection

data class ParsedMessage(
    val ruleId: Long,
    val ruleName: String,
    val kind: RuleKind,
    val direction: TransactionDirection,
    val amount: Money,
    val balance: Money?,
    val merchant: String?,
    val accountTail: String?,
    val providerTxnId: String?,
    val occurredAt: Long?,
    val feeMinor: Long?,
)

sealed interface ParseOutcome {
    data class Parsed(val value: ParsedMessage) : ParseOutcome
    data class Ignored(val ruleId: Long, val ruleName: String) : ParseOutcome
    data object Unmatched : ParseOutcome
}
```

- [ ] **Step 4: Implement the engine**

Create `app/src/main/java/com/wasif/khata/core/sms/RuleEngine.kt`:

```kotlin
package com.wasif.khata.core.sms

import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEngine @Inject constructor() {

    fun parse(sender: String, body: String, rules: List<ParsingRuleEntity>): ParseOutcome {
        val ordered = rules.filter { it.isEnabled && it.deletedAt == null }.sortedBy { it.priority }

        for (rule in ordered) {
            val senderRegex = rule.senderPattern.toRegexOrNull() ?: continue
            if (!senderRegex.containsMatchIn(sender)) continue

            val bodyRegex = rule.bodyPattern.toRegexOrNull() ?: continue
            val match = bodyRegex.find(body) ?: continue

            if (rule.kind == RuleKind.IGNORE) {
                return ParseOutcome.Ignored(rule.id, rule.name)
            }

            val amount = match.group("amount")?.let(::parseAmount) ?: continue
            val direction = rule.direction ?: continue

            return ParseOutcome.Parsed(
                ParsedMessage(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    kind = rule.kind,
                    direction = direction,
                    amount = amount,
                    balance = match.group("balance")?.let(::parseAmount),
                    merchant = match.group("merchant")?.trim()?.takeIf { it.isNotEmpty() },
                    accountTail = match.group("account")?.let(::accountTail),
                    providerTxnId = match.group("refId"),
                    occurredAt = match.group("datetime")?.let { parseEblDateTime(it) ?: parseBkashDateTime(it) },
                    feeMinor = match.group("fee")?.let(::parseAmount)?.minor,
                ),
            )
        }
        return ParseOutcome.Unmatched
    }
}

// A user-edited or AI-drafted rule can be syntactically invalid; one bad pattern
// must not stop every later rule from being tried.
private fun String.toRegexOrNull(): Regex? = try {
    toRegex()
} catch (e: IllegalArgumentException) {
    null
}

// Kotlin throws rather than returning null when a named group is absent from the
// pattern, which is the normal case for rules that capture only some fields.
private fun MatchResult.group(name: String): String? = try {
    groups[name]?.value
} catch (e: IllegalArgumentException) {
    null
}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*RuleEngineTest*"
```

Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/sms/ app/src/test/java/com/wasif/khata/core/sms/
git commit -m "feat: add priority-ordered SMS rule engine with IGNORE-first semantics"
```

---
### Task 4: Built-in rules for bKash and EBL, proven against the corpus

The rules are data, not code — they are seeded into `parsing_rules` so a format
change is fixed in the database rather than by shipping an APK. This task is
corpus-driven: write the corpus test first, watch it fail, then add rules until it
passes.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/BuiltInRules.kt`
- Test: `app/src/test/java/com/wasif/khata/core/sms/CorpusTest.kt`

**Interfaces:**
- Consumes: `RuleEngine`, `ParseOutcome`, `ParsedMessage` (Task 3); `ParsingRuleEntity`, `RuleKind` (Task 1).
- Produces: `val BUILT_IN_RULES: List<ParsingRuleEntity>`.

**Priority bands** — the engine sorts ascending, so lower runs first:

| Band | Purpose |
|---|---|
| 1–19 | IGNORE rules. Must outrank everything that extracts an amount. |
| 20–39 | bKash extractors, most specific first. |
| 40–59 | EBL extractors, most specific first. |

- [ ] **Step 1: Write the failing corpus test**

Every message literal below is copied verbatim from `sms-corpus.md`. Do not retype
from memory and do not tidy the punctuation — the spacing is under test.

Create `app/src/test/java/com/wasif/khata/core/sms/CorpusTest.kt`:

```kotlin
package com.wasif.khata.core.sms

import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpusTest {

    private val engine = RuleEngine()

    private fun parse(sender: String, body: String): ParseOutcome =
        engine.parse(sender, body, BUILT_IN_RULES)

    private fun parsed(sender: String, body: String): ParsedMessage {
        val outcome = parse(sender, body)
        assertTrue("expected a parse, got $outcome for: $body", outcome is ParseOutcome.Parsed)
        return (outcome as ParseOutcome.Parsed).value
    }

    // --- IGNORE: these carry a real amount and must produce nothing ---

    @Test
    fun `bKash payment OTP is ignored despite carrying an amount and a merchant`() {
        val outcome = parse(
            "bKash",
            "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min.",
        )
        assertTrue("OTP must not parse as a transaction, got $outcome", outcome is ParseOutcome.Ignored)
    }

    @Test
    fun `bKash auto-debit OTP is ignored`() {
        val outcome = parse(
            "bKash",
            "Do NOT share your OTP or PIN with anyone. Your bKash OTP to enable AUTO DEBIT in App or Website of Foodpanda Bangladesh Limited is 372914. Expires in 5 min.",
        )
        assertTrue(outcome is ParseOutcome.Ignored)
    }

    @Test
    fun `EBL OTP request is ignored despite carrying an amount`() {
        val outcome = parse(
            "EBL",
            "The OTP request is for a transaction at foodibdcom. To complete your transaction BDT 232.00 at foodibdcom with Card#539***432, use OTP: 823876 . Helpline: 16230",
        )
        assertTrue("OTP must not parse as a transaction, got $outcome", outcome is ParseOutcome.Ignored)
    }

    @Test
    fun `bKash loan terms notice is ignored so the disbursement is not double counted`() {
        val outcome = parse(
            "bKash",
            "You have received Loan of Tk 900.00 from City Bank in your bKash Account. Your first repayment of TK 309.28 is due on 28/09/2026.",
        )
        assertTrue(outcome is ParseOutcome.Ignored)
    }

    @Test
    fun `bKash account binding confirmation is ignored`() {
        val outcome = parse(
            "bKash",
            "Your Account Binding request for FOODPANDA BANGLADESH LIMITED is successful. You have authorized FOODPANDA BANGLADESH LIMITED to debit your account for future purchases. For queries, please call 16247.",
        )
        assertTrue(outcome is ParseOutcome.Ignored)
    }

    @Test
    fun `EBL standing instruction status is ignored`() {
        val outcome = parse(
            "EBL",
            "Ref no : 398595SI1764909024 , Your Standing Instruction Execution Status is: Successfully Executed . Helpline 16230.",
        )
        assertTrue(outcome is ParseOutcome.Ignored)
    }

    // --- bKash ---

    @Test
    fun `bKash payment successful`() {
        val p = parsed(
            "bKash",
            "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01",
        )
        assertEquals(Money(85600), p.amount)
        assertEquals(Money(4198), p.balance)
        assertEquals("FOODPANDA BANGLADESH LIMITED", p.merchant)
        assertEquals("DHV41FIPGY", p.providerTxnId)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `bKash payment with thousands separator`() {
        val p = parsed(
            "bKash",
            "Payment of Tk 2,600.00 to CINEPLEXBD is successful. Balance Tk 338.33. TrxID DH2118Z3S1 at 02/08/2026 00:44",
        )
        assertEquals(Money(260000), p.amount)
        assertEquals("CINEPLEXBD", p.merchant)
    }

    @Test
    fun `bKash reserved payment carries the same TrxID as its successful twin`() {
        val reserved = parsed(
            "bKash",
            "Payment of Tk 564.11 is being reserved for Uber Bangladesh Ltd-Uber. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11",
        )
        val successful = parsed(
            "bKash",
            "Payment of Tk 564.11 to Uber Bangladesh Ltd-Uber is successful. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11",
        )
        assertEquals(reserved.providerTxnId, successful.providerTxnId)
        assertEquals(reserved.amount, successful.amount)
    }

    @Test
    fun `bKash received money`() {
        val p = parsed(
            "bKash",
            "You have received Tk 325.00 from 01700000001. Fee Tk 0.00. Balance Tk 527.09. TrxID DHL8NM6BYC at 21/08/2026 12:35",
        )
        assertEquals(Money(32500), p.amount)
        assertEquals(Money(52709), p.balance)
        assertEquals("01700000001", p.merchant)
        assertEquals("DHL8NM6BYC", p.providerTxnId)
        assertEquals(TransactionDirection.CREDIT, p.direction)
        assertEquals(0L, p.feeMinor)
    }

    @Test
    fun `bKash received money with an optional Ref segment`() {
        val p = parsed(
            "bKash",
            "You have received Tk 100.00 from 01700000003. Ref A. Fee Tk 0.00. Balance Tk 238.09. TrxID DHG5IGY2ZP at 16/08/2026 18:47",
        )
        assertEquals(Money(10000), p.amount)
        assertEquals("01700000003", p.merchant)
    }

    @Test
    fun `bKash digital loan is a disbursement not plain income`() {
        val p = parsed(
            "bKash",
            "You have received Digital Loan Tk 900.00 from City Bank. Balance Tk 897.98. TrxID DHV31FH6VD at 31/08/2026 19:00.",
        )
        assertEquals(Money(90000), p.amount)
        assertEquals(RuleKind.LOAN_DISBURSEMENT, p.kind)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `bKash cashback is ordinary income`() {
        val p = parsed(
            "bKash",
            "Congratulations! You have received Cashback Tk 2.90. Balance Tk 1,027.11. TrxID DHN4PFHA3Y at 23/08/2026 10:55. Cashback on Loan!",
        )
        assertEquals(Money(290), p.amount)
        assertEquals(Money(102711), p.balance)
        assertEquals(RuleKind.NORMAL, p.kind)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `bKash bank deposit is a transfer in`() {
        val p = parsed(
            "bKash",
            "You have received deposit from iBanking of Tk 2,600.00 from Eastern Bank PLC. Internet Banking. Fee Tk 0.00. Balance Tk 2,938.33. TrxID DH2718YM43 at 02/08/2026 00:43",
        )
        assertEquals(Money(260000), p.amount)
        assertEquals(RuleKind.TRANSFER_IN, p.kind)
    }

    // --- EBL ---

    @Test
    fun `EBL account debit`() {
        val p = parsed(
            "EBL",
            "AC 115***352 is debited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 58.56 Thanks. EBL Helpline 16230",
        )
        assertEquals(Money(6000), p.amount)
        assertEquals(Money(5856), p.balance)
        assertEquals("352", p.accountTail)
        assertEquals("Own Account Transfer", p.merchant)
        assertEquals(TransactionDirection.DEBIT, p.direction)
        assertEquals(RuleKind.NORMAL, p.kind)
    }

    @Test
    fun `EBL account credit`() {
        val p = parsed(
            "EBL",
            "AC 112***286 is credited with BDT 10000 as NPSB FUND TRANSFER on 01-SEP-26 10:38:57 AM Balance is BDT 10034.2 Thanks. EBL Helpline 16230",
        )
        assertEquals(Money(1000000), p.amount)
        assertEquals(Money(1003420), p.balance)
        assertEquals("286", p.accountTail)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `EBL card purchase separates merchant from the Card token`() {
        val p = parsed(
            "EBL",
            "Purchase txn BDT 1101 from TOUR DE CYCLIST Ut.Card 539280**3432 on 31-Aug-26 06:27:44 PM BST.Your A/C 115**9352 Balance BDT 118.56. EBL Helpline 16230",
        )
        assertEquals(Money(110100), p.amount)
        assertEquals("TOUR DE CYCLIST Ut", p.merchant)
        assertEquals("352", p.accountTail)
        assertEquals(Money(11856), p.balance)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `EBL card purchase where the merchant itself contains a period`() {
        val p = parsed(
            "EBL",
            "Purchase txn BDT 232 from foodibd.com Dhaka .Card 539280**3432 on 25-Aug-26 06:27:38 PM BST.Your A/C 115**9352 Balance BDT 1269.56. EBL Helpline 16230",
        )
        assertEquals(Money(23200), p.amount)
        assertEquals("foodibd.com Dhaka", p.merchant)
    }

    @Test
    fun `EBL ATM withdrawal has no space after BDT and is a cash transfer`() {
        val p = parsed(
            "EBL",
            "Cash WD BDT5000 from North South Universi. Card 539280**3432 on 19-Aug-26 06:00:37 PM BST.Your A/C 115**9352 Balance BDT 1501.56. EBL Helpline 16230",
        )
        assertEquals(Money(500000), p.amount)
        assertEquals(RuleKind.ATM_WITHDRAWAL, p.kind)
        assertEquals("352", p.accountTail)
    }

    @Test
    fun `EBL cards NPSB transfer routes to the owning account not the card`() {
        val p = parsed(
            "EBL",
            "EBL CARDS: NPSB Fund Transfer BDT 180 using Card 452017**1835 on 18-Aug-26 12:44:55 AM.Your A/C 112**0286 Balance BDT 6534.2. Thank You. EBL Helpline 16230",
        )
        assertEquals(Money(18000), p.amount)
        assertEquals("286", p.accountTail)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `EBL MFS transfer to bKash is a transfer out`() {
        val p = parsed(
            "EBL",
            "AC 112***286 is debited with BDT 420 as EBL Skybanking MFS Transfer-bKash on 17-AUG-26 06:57:20 PM Balance is BDT 6724.19 Thanks. EBL Helpline 16230",
        )
        assertEquals(Money(42000), p.amount)
        assertEquals("286", p.accountTail)
    }

    // --- the whole corpus must be accounted for ---

    @Test
    fun `no corpus message falls through unmatched`() {
        val unmatched = CORPUS.filterNot { (sender, body) ->
            parse(sender, body) !is ParseOutcome.Unmatched
        }
        assertTrue("these messages matched no rule:\n" + unmatched.joinToString("\n") { it.second }, unmatched.isEmpty())
    }

    private companion object {
        val CORPUS: List<Pair<String, String>> = listOf(
            "EBL" to "AC 115***352 is debited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 58.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is credited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 5004.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is debited with BDT 5000 as EBL Account Transfer on 01-SEP-26 07:03:20 PM Balance is BDT 4.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is credited with BDT 17316 as AC TRANSFER THROUGH EBL CONNECT on 01-SEP-26 07:34:56 PM Balance is BDT 17374.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is debited with BDT 50 as EBL Skybanking Mobile Recharge on 30-AUG-26 04:28:45 PM Balance is BDT 1219.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is credited with BDT 10000 as NPSB FUND TRANSFER on 01-SEP-26 10:38:57 AM Balance is BDT 10034.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is debited with BDT 5090 as EBL Account Transfer on 01-SEP-26 11:07:10 AM Balance is BDT 4944.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is debited with BDT 420 as EBL Skybanking MFS Transfer-bKash on 17-AUG-26 06:57:20 PM Balance is BDT 6724.19 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is debited with BDT 6500 as Own Account Transfer on 18-AUG-26 01:20:19 PM Balance is BDT 34.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is credited with BDT 6500 as Own Account Transfer on 18-AUG-26 01:20:20 PM Balance is BDT 6516.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is debited with BDT 330 as EBL Account Transfer on 05-AUG-26 05:44:02 PM Balance is BDT 8923.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is debited with BDT 6500 as EBL Account Transfer on 06-AUG-26 12:05:07 AM Balance is BDT 2423.56 Thanks. EBL Helpline 16230",
            "EBL" to "Purchase txn BDT 1101 from TOUR DE CYCLIST Ut.Card 539280**3432 on 31-Aug-26 06:27:44 PM BST.Your A/C 115**9352 Balance BDT 118.56. EBL Helpline 16230",
            "EBL" to "Purchase txn BDT 232 from foodibd.com Dhaka .Card 539280**3432 on 25-Aug-26 06:27:38 PM BST.Your A/C 115**9352 Balance BDT 1269.56. EBL Helpline 16230",
            "EBL" to "Purchase txn BDT 2407 from TOKYO KITCHEN UTTA.Card 539280**3432 on 07-Aug-26 10:51:50 PM BST.Your A/C 115**9352 Balance BDT 118.56. EBL Helpline 16230",
            "EBL" to "Cash WD BDT5000 from North South Universi. Card 539280**3432 on 19-Aug-26 06:00:37 PM BST.Your A/C 115**9352 Balance BDT 1501.56. EBL Helpline 16230",
            "EBL" to "EBL CARDS: NPSB Fund Transfer BDT 180 using Card 452017**1835 on 18-Aug-26 12:44:55 AM.Your A/C 112**0286 Balance BDT 6534.2. Thank You. EBL Helpline 16230",
            "EBL" to "The OTP request is for a transaction at foodibdcom. To complete your transaction BDT 232.00 at foodibdcom with Card#539***432, use OTP: 823876 . Helpline: 16230",
            "EBL" to "Ref no : 398595SI1764909024 , Your Standing Instruction Execution Status is: Successfully Executed . Helpline 16230.",
            "bKash" to "You have received Tk 325.00 from 01700000001. Fee Tk 0.00. Balance Tk 527.09. TrxID DHL8NM6BYC at 21/08/2026 12:35",
            "bKash" to "You have received Tk 142.00 from 01700000001. Fee Tk 0.00. Balance Tk 356.09. TrxID DHA9BTWHE1 at 10/08/2026 22:10",
            "bKash" to "You have received Tk 142.00 from 01700000002. Fee Tk 0.00. Balance Tk 498.09. TrxID DHA5BUPNF1 at 10/08/2026 22:27",
            "bKash" to "You have received Tk 100.00 from 01700000003. Ref A. Fee Tk 0.00. Balance Tk 238.09. TrxID DHG5IGY2ZP at 16/08/2026 18:47",
            "bKash" to "You have received Tk 66.00 from 01700000004. Fee Tk 0.00. Balance Tk 131.09. TrxID DHK6MX34WC at 20/08/2026 18:39",
            "bKash" to "You have received Tk 71.00 from 01700000005. Fee Tk 0.00. Balance Tk 202.09. TrxID DHK2MX5HZA at 20/08/2026 18:39",
            "bKash" to "You have received Tk 650.00 from 01700000006. Fee Tk 0.00. Balance Tk 1,083.20. TrxID DH94A7139O at 09/08/2026 17:33",
            "bKash" to "Payment of Tk 564.11 is being reserved for Uber Bangladesh Ltd-Uber. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11",
            "bKash" to "Payment of Tk 564.11 to Uber Bangladesh Ltd-Uber is successful. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11",
            "bKash" to "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01",
            "bKash" to "Payment of Tk 750.00 to CINEPLEXBD is successful. Balance Tk 338.33. TrxID DH2918VO8R at 02/08/2026 00:38",
            "bKash" to "Payment of Tk 2,600.00 to CINEPLEXBD is successful. Balance Tk 338.33. TrxID DH2118Z3S1 at 02/08/2026 00:44",
            "bKash" to "You have received deposit from iBanking of Tk 2,600.00 from Eastern Bank PLC. Internet Banking. Fee Tk 0.00. Balance Tk 2,938.33. TrxID DH2718YM43 at 02/08/2026 00:43",
            "bKash" to "You have received Digital Loan Tk 900.00 from City Bank. Balance Tk 897.98. TrxID DHV31FH6VD at 31/08/2026 19:00.",
            "bKash" to "You have received Digital Loan Tk 500.00 from City Bank. Balance Tk 1,024.21. TrxID DHN2PFGRRC at 23/08/2026 10:55.",
            "bKash" to "Congratulations! You have received Cashback Tk 2.90. Balance Tk 1,027.11. TrxID DHN4PFHA3Y at 23/08/2026 10:55. Cashback on Loan!",
            "bKash" to "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min.",
            "bKash" to "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.2,600.00 to Software Shop Limited-RM51177 is 746654. Expires in 2 min.",
            "bKash" to "Do NOT share your OTP or PIN with anyone. Your bKash OTP to enable AUTO DEBIT in App or Website of Foodpanda Bangladesh Limited is 372914. Expires in 5 min.",
            "bKash" to "You have received Loan of Tk 900.00 from City Bank in your bKash Account. Your first repayment of TK 309.28 is due on 28/09/2026.",
            "bKash" to "Your Account Binding request for FOODPANDA BANGLADESH LIMITED is successful. You have authorized FOODPANDA BANGLADESH LIMITED to debit your account for future purchases. For queries, please call 16247.",
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="E:/Android/Android Studio/jbr"
./gradlew testDebugUnitTest --tests "*CorpusTest*"
```

Expected: FAIL — `Unresolved reference: BUILT_IN_RULES`.

- [ ] **Step 3: Write the built-in rules**

Create `app/src/main/java/com/wasif/khata/core/sms/BuiltInRules.kt`:

```kotlin
package com.wasif.khata.core.sms

import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection

private const val AMT = """(?:BDT|Tk)\.? ?[\d,]+(?:\.\d{1,2})?"""
private const val EBL_DT = """\d{2}-[A-Za-z]{3}-\d{2} \d{2}:\d{2}:\d{2} [AP]M"""
private const val BKASH_DT = """\d{2}/\d{2}/\d{4} \d{2}:\d{2}"""
private const val MASK = """[\d*]+"""
private const val TRX = """[A-Z0-9]+"""

private fun rule(
    slug: String,
    name: String,
    sender: String,
    body: String,
    priority: Int,
    kind: RuleKind = RuleKind.NORMAL,
    direction: TransactionDirection? = null,
    sample: String = "",
) = ParsingRuleEntity(
    uuid = "builtin-$slug",
    name = name,
    senderPattern = sender,
    bodyPattern = body,
    direction = direction,
    kind = kind,
    priority = priority,
    origin = "BUILTIN",
    isEnabled = true,
    sampleMessage = sample,
    createdAt = 0,
    updatedAt = 0,
)

val BUILT_IN_RULES: List<ParsingRuleEntity> = listOf(

    // 1-19: IGNORE. These outrank every extracting rule because an OTP message
    // carries a real amount and a real merchant and would otherwise parse cleanly.
    rule("ignore-bkash-otp", "bKash OTP", "bKash", """Do NOT share your OTP""", 1, RuleKind.IGNORE),
    rule("ignore-ebl-otp", "EBL OTP", "EBL", """use OTP:|The OTP request is for""", 2, RuleKind.IGNORE),
    rule("ignore-bkash-loan-terms", "bKash loan terms", "bKash", """You have received Loan of .+ in your bKash Account""", 3, RuleKind.IGNORE),
    rule("ignore-bkash-binding", "bKash account binding", "bKash", """Account Binding request for""", 4, RuleKind.IGNORE),
    rule("ignore-ebl-standing", "EBL standing instruction", "EBL", """Standing Instruction Execution Status""", 5, RuleKind.IGNORE),

    // 20-39: bKash, most specific first.
    rule(
        "bkash-loan", "bKash digital loan", "bKash",
        """You have received Digital Loan (?<amount>$AMT) from (?<merchant>.+?)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        20, RuleKind.LOAN_DISBURSEMENT, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-cashback", "bKash cashback", "bKash",
        """You have received Cashback (?<amount>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        21, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-ibanking-deposit", "bKash deposit from bank", "bKash",
        """You have received deposit from iBanking of (?<amount>$AMT) from (?<merchant>.+?)\. Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        22, RuleKind.TRANSFER_IN, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-received", "bKash received money", "bKash",
        """You have received (?<amount>$AMT) from (?<merchant>\d+)\.(?: Ref .+?\.)? Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        23, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-payment-success", "bKash payment", "bKash",
        """Payment of (?<amount>$AMT) to (?<merchant>.+?) is successful\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        24, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    rule(
        "bkash-payment-reserved", "bKash payment reserved", "bKash",
        """Payment of (?<amount>$AMT) is being reserved for (?<merchant>.+?)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        25, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),

    // 40-59: EBL, most specific first.
    rule(
        "ebl-cash-wd", "EBL ATM withdrawal", "EBL",
        """Cash WD (?<amount>$AMT) from (?<merchant>.+?)\. Card $MASK on (?<datetime>$EBL_DT) BST\.Your A/C (?<account>$MASK) Balance (?<balance>$AMT)""",
        40, RuleKind.ATM_WITHDRAWAL, TransactionDirection.DEBIT,
    ),
    rule(
        "ebl-purchase", "EBL card purchase", "EBL",
        """Purchase txn (?<amount>$AMT) from (?<merchant>.+?)\s*\.Card $MASK on (?<datetime>$EBL_DT) BST\.Your A/C (?<account>$MASK) Balance (?<balance>$AMT)""",
        41, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    rule(
        "ebl-cards-npsb", "EBL cards NPSB transfer", "EBL",
        """EBL CARDS: (?<merchant>NPSB Fund Transfer) (?<amount>$AMT) using Card $MASK on (?<datetime>$EBL_DT)\.Your A/C (?<account>$MASK) Balance (?<balance>$AMT)""",
        42, RuleKind.TRANSFER_OUT, TransactionDirection.DEBIT,
    ),
    rule(
        "ebl-debit", "EBL account debit", "EBL",
        """AC (?<account>$MASK) is debited with (?<amount>$AMT) as (?<merchant>.+?) on (?<datetime>$EBL_DT) Balance is (?<balance>$AMT)""",
        50, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    rule(
        "ebl-credit", "EBL account credit", "EBL",
        """AC (?<account>$MASK) is credited with (?<amount>$AMT) as (?<merchant>.+?) on (?<datetime>$EBL_DT) Balance is (?<balance>$AMT)""",
        51, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
)
```

`ebl-debit` and `ebl-credit` are two rules rather than one with an alternation
because `direction` is a field on the rule, not something read out of the body.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*CorpusTest*"
```

Expected: PASS, 22 tests. If a message falls through, the final test names it.
Fix the rule, never the corpus literal.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/sms/BuiltInRules.kt app/src/test/java/com/wasif/khata/core/sms/CorpusTest.kt
git commit -m "feat: add built-in bKash and EBL parsing rules, proven against the corpus"
```

---
### Task 5: DAOs and rule seeding

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/dao/RawMessageDao.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/dao/ParsingRuleDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/seed/DatabaseSeeder.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/RawMessageDaoTest.kt`

**Interfaces:**
- Produces:
  - `RawMessageDao`: `insertIgnoringDuplicate(entity): Long`, `observePending(): Flow<List<RawMessageEntity>>`, `pendingBatch(limit: Int): List<RawMessageEntity>`, `allForReparse(): List<RawMessageEntity>`, `markStatus(id, status, ruleId, updatedAt)`, `countByStatus(status): Int`
  - `ParsingRuleDao`: `upsertAll(entities)`, `enabled(): List<ParsingRuleEntity>`, `observeAll(): Flow<List<ParsingRuleEntity>>`, `countIncludingDeleted(): Int`
  - `TransactionDao.findByProviderTxnId(id: String): TransactionEntity?`
  - `TransactionDao.findPairCandidates(amountMinor, notAccountId, direction, fromMillis, toMillis): List<TransactionEntity>`
  - `DatabaseSeeder.seedIfEmpty()` also seeds `BUILT_IN_RULES`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/core/data/RawMessageDaoTest.kt`:

```kotlin
package com.wasif.khata.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.model.RawMessageStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RawMessageDaoTest {

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

    private fun message(uuid: String, body: String, receivedAt: Long, hash: String = body.hashCode().toString()) =
        RawMessageEntity(
            uuid = uuid,
            sender = "bKash",
            body = body,
            receivedAt = receivedAt,
            bodyHash = hash,
            status = RawMessageStatus.PENDING,
            matchedRuleId = null,
            createdAt = receivedAt,
            updatedAt = receivedAt,
        )

    @Test
    fun `re-inserting the same message is a no-op so backfill is idempotent`() = runTest {
        val dao = db.rawMessageDao()
        dao.insertIgnoringDuplicate(message("m-1", "hello", 1000))

        val second = dao.insertIgnoringDuplicate(message("m-2", "hello", 1000))

        assertEquals(-1L, second)
        assertEquals(1, dao.countByStatus(RawMessageStatus.PENDING))
    }

    @Test
    fun `the same body at a different timestamp is a distinct message`() = runTest {
        val dao = db.rawMessageDao()
        dao.insertIgnoringDuplicate(message("m-1", "hello", 1000))
        dao.insertIgnoringDuplicate(message("m-2", "hello", 2000))

        assertEquals(2, dao.countByStatus(RawMessageStatus.PENDING))
    }

    @Test
    fun `markStatus moves a message out of the pending batch`() = runTest {
        val dao = db.rawMessageDao()
        val id = dao.insertIgnoringDuplicate(message("m-1", "hello", 1000))

        dao.markStatus(id, RawMessageStatus.IGNORED, ruleId = 7, updatedAt = 2000)

        assertEquals(0, dao.countByStatus(RawMessageStatus.PENDING))
        assertEquals(1, dao.countByStatus(RawMessageStatus.IGNORED))
        assertEquals(emptyList<RawMessageEntity>(), dao.pendingBatch(10))
    }

    @Test
    fun `allForReparse returns every message regardless of status`() = runTest {
        val dao = db.rawMessageDao()
        val a = dao.insertIgnoringDuplicate(message("m-1", "a", 1000))
        dao.insertIgnoringDuplicate(message("m-2", "b", 2000))
        dao.markStatus(a, RawMessageStatus.UNMATCHED, ruleId = null, updatedAt = 3000)

        assertEquals(2, dao.allForReparse().size)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
export JAVA_HOME="E:/Android/Android Studio/jbr"
./gradlew testDebugUnitTest --tests "*RawMessageDaoTest*"
```

Expected: FAIL — `Unresolved reference: rawMessageDao`.

- [ ] **Step 3: Create `RawMessageDao`**

Create `app/src/main/java/com/wasif/khata/core/data/dao/RawMessageDao.kt`:

```kotlin
package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.model.RawMessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RawMessageDao {

    // IGNORE rather than REPLACE: re-scanning the inbox must not renumber rows that
    // transactions already reference by rawMessageId.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicate(entity: RawMessageEntity): Long

    @Query("SELECT * FROM raw_messages WHERE status = 'PENDING' AND deletedAt IS NULL ORDER BY receivedAt LIMIT :limit")
    suspend fun pendingBatch(limit: Int): List<RawMessageEntity>

    @Query("SELECT * FROM raw_messages WHERE status = 'PENDING' AND deletedAt IS NULL ORDER BY receivedAt")
    fun observePending(): Flow<List<RawMessageEntity>>

    @Query("SELECT * FROM raw_messages WHERE deletedAt IS NULL ORDER BY receivedAt")
    suspend fun allForReparse(): List<RawMessageEntity>

    @Query("UPDATE raw_messages SET status = :status, matchedRuleId = :ruleId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markStatus(id: Long, status: RawMessageStatus, ruleId: Long?, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM raw_messages WHERE status = :status AND deletedAt IS NULL")
    suspend fun countByStatus(status: RawMessageStatus): Int
}
```

- [ ] **Step 4: Create `ParsingRuleDao`**

Create `app/src/main/java/com/wasif/khata/core/data/dao/ParsingRuleDao.kt`:

```kotlin
package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParsingRuleDao {

    @Upsert
    suspend fun upsertAll(entities: List<ParsingRuleEntity>)

    @Query("SELECT * FROM parsing_rules WHERE isEnabled = 1 AND deletedAt IS NULL ORDER BY priority")
    suspend fun enabled(): List<ParsingRuleEntity>

    @Query("SELECT * FROM parsing_rules WHERE deletedAt IS NULL ORDER BY priority")
    fun observeAll(): Flow<List<ParsingRuleEntity>>

    @Query("SELECT COUNT(*) FROM parsing_rules")
    suspend fun countIncludingDeleted(): Int
}
```

- [ ] **Step 5: Extend `TransactionDao`**

Add to `app/src/main/java/com/wasif/khata/core/data/dao/TransactionDao.kt`:

```kotlin
    @Query("SELECT * FROM transactions WHERE providerTxnId = :providerTxnId AND deletedAt IS NULL")
    suspend fun findByProviderTxnId(providerTxnId: String): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL AND transferGroupId IS NULL " +
            "AND amountMinor = :amountMinor AND accountId != :notAccountId AND direction = :direction " +
            "AND occurredAt BETWEEN :fromMillis AND :toMillis"
    )
    suspend fun findPairCandidates(
        amountMinor: Long,
        notAccountId: Long,
        direction: TransactionDirection,
        fromMillis: Long,
        toMillis: Long,
    ): List<TransactionEntity>

    @Query("UPDATE transactions SET transferGroupId = :groupId, kind = 'TRANSFER', updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun markAsTransfer(ids: List<Long>, groupId: String, updatedAt: Long)
```

- [ ] **Step 6: Register the DAOs**

In `KhataDatabase.kt` add `abstract fun rawMessageDao(): RawMessageDao` and
`abstract fun parsingRuleDao(): ParsingRuleDao`.

In `DatabaseModule.kt` add the matching `@Provides` functions.

- [ ] **Step 7: Seed the rules**

In `DatabaseSeeder.kt`, add to `seedIfEmpty()`:

```kotlin
        if (ruleDao.countIncludingDeleted() == 0) {
            ruleDao.upsertAll(BUILT_IN_RULES.map { it.copy(createdAt = now, updatedAt = now) })
        }
```

Inject `ParsingRuleDao` as a constructor parameter. Update `DatabaseSeederTest` to
assert the rule count matches `BUILT_IN_RULES.size`.

- [ ] **Step 8: Run the tests**

```bash
./gradlew testDebugUnitTest
```

Expected: all green, including the updated seeder test.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: add raw message and parsing rule DAOs, seed built-in rules"
```

---

### Task 6: Ingestion pipeline — dedup, routing, merchant resolution, write

The orchestrator. Everything before this task produced parts; this assembles them
into "a message goes in, a correct transaction comes out, exactly once".

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/IngestionPipeline.kt`
- Test: `app/src/test/java/com/wasif/khata/core/sms/IngestionPipelineTest.kt`

**Interfaces:**
- Consumes: `RuleEngine`, `BUILT_IN_RULES`, all DAOs, `KhataClock`, `KhataDatabase`.
- Produces: `class IngestionPipeline { suspend fun ingest(sender: String, body: String, receivedAt: Long): IngestResult }` and `sealed interface IngestResult { data class Recorded(val transactionId: Long); data class Updated(val transactionId: Long); data object Ignored; data object Unmatched; data object Duplicate }`

**Behaviour, in order:**

1. Persist to `raw_messages` with `insertIgnoringDuplicate`. A `-1` return means this
   exact message was already stored — return `Duplicate` and stop.
2. Run the rule engine over the enabled rules.
3. `Ignored` → mark the raw message `IGNORED`, return `Ignored`.
4. `Unmatched` → mark `UNMATCHED`, return `Unmatched`. **Never guess.**
5. `Parsed` → resolve the account, resolve the merchant, then write.

**Account routing:**
- If `accountTail` is present, match against `accounts.smsIdentifiers` by tail.
- Otherwise match the sender against `smsIdentifiers` (bKash messages carry no account).
- No match → mark the raw message `UNMATCHED` and return `Unmatched`. A transaction
  with no account cannot be balanced, so it is worse than none.

**Merchant resolution:**
- Look up `merchant_aliases.rawText` for the parsed merchant string.
- Hit → reuse the merchant and its `categoryId`, confidence `HIGH`.
- Miss → create a merchant plus alias with a null category, confidence `MEDIUM`.
  Plan 3's AI fallback fills the category; Plan 2a leaves it null rather than guessing.

**Dedup:** if `providerTxnId` is non-null and already exists, **update** that row
instead of inserting, and return `Updated`. "is successful" supersedes "is being
reserved" because it arrives second and is more authoritative.

**Kind mapping:** `RuleKind.LOAN_DISBURSEMENT` → `TransactionKind.LOAN_DISBURSEMENT`;
`TRANSFER_OUT`/`TRANSFER_IN`/`ATM_WITHDRAWAL` → `TransactionKind.TRANSFER`;
`FEE` → `TransactionKind.FEE`; `NORMAL` → `TransactionKind.NORMAL`.

**Atomicity:** the whole write — transaction row, balance adjustment, reported
balance, raw-message status — happens inside one `db.withTransaction { }`, for the
same reason Plan 1's repository does.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/wasif/khata/core/sms/IngestionPipelineTest.kt` covering,
each as its own test with a Robolectric in-memory database seeded with the four
default accounts and `BUILT_IN_RULES`:

1. `a bKash payment records one debit against the bKash account` — asserts amount, direction, merchant, and that the bKash balance decreased.
2. `an OTP message records nothing at all` — asserts `IngestResult.Ignored` **and** that the transaction table is still empty. This is the most important test in the plan.
3. `the reserved and successful twins produce exactly one transaction` — ingest both Uber messages, assert one row, and assert its merchant came from the "successful" wording.
4. `re-ingesting the identical message is a Duplicate and changes no balance` — ingest the same message twice, assert the second returns `Duplicate` and the balance moved once.
5. `an EBL debit routes to EBL Salary by account tail` — the `115***352` message lands on the account seeded with `smsIdentifiers = "352"`.
6. `an EBL card purchase routes to the same account despite a different mask` — the `115**9352` message lands on the same account as test 5.
7. `an unknown format is marked UNMATCHED and records no transaction`.
8. `a known merchant reuses its category and records HIGH confidence` — pre-seed an alias, assert `categoryId` and `Confidence.HIGH`.
9. `a new merchant is created with MEDIUM confidence and no category`.
10. `a digital loan is recorded as LOAN_DISBURSEMENT, not plain income`.
11. `the reported balance from the message is written to the account`.

- [ ] **Step 2: Run to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "*IngestionPipelineTest*"
```

Expected: FAIL — `Unresolved reference: IngestionPipeline`.

- [ ] **Step 3: Implement the pipeline**

Create `IngestionPipeline.kt` implementing exactly the behaviour listed above.
Constructor takes `db: KhataDatabase`, `rawMessageDao`, `parsingRuleDao`,
`transactionDao`, `accountDao`, `merchantDao`, `engine: RuleEngine`, `clock: KhataClock`,
all `@Inject`ed. Mark it `@Singleton`.

Use `java.security.MessageDigest` SHA-256 over the body for `bodyHash`, hex-encoded.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "*IngestionPipelineTest*"
```

Expected: PASS, 11 tests.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add SMS ingestion pipeline with dedup, routing, and merchant memory"
```

---
### Task 7: Transfer pairing and balance reconciliation

Two correctness mechanisms that both depend on transactions already existing.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/TransferPairing.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/repository/ReconciliationRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/sms/IngestionPipeline.kt`
- Test: `app/src/test/java/com/wasif/khata/core/sms/TransferPairingTest.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/repository/ReconciliationRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `class TransferPairing { suspend fun pair(transactionId: Long): String? }` — returns the `transferGroupId` when a pair was formed.
  - `data class BalanceDrift(val accountId: Long, val accountName: String, val computed: Money, val reported: Money, val reportedAt: Long)` with `val gap: Money`.
  - `class ReconciliationRepository { fun observeDrift(): Flow<List<BalanceDrift>> }`

**Pairing rules:**
- Window is **15 minutes** either side of `occurredAt`.
- Candidate must be the opposite `direction`, an equal `amountMinor`, a *different*
  `accountId`, and not already in a transfer group.
- On a match, both rows get a shared `transferGroupId` (a UUID) and `kind = TRANSFER`.
- If several candidates qualify, take the one closest in time. Ties are broken by the
  lower `id`, so pairing is deterministic and re-running produces the same result.

The corpus proves the window is right: `Own Account Transfer` pairs land one second
apart (`01:20:19` / `01:20:20`), and cross-institution pairs (EBL debit → bKash
deposit) sit minutes apart.

**Reconciliation:** an account drifts when `reportedBalanceMinor` is non-null and
differs from `currentBalanceMinor`. `observeDrift()` emits one `BalanceDrift` per
drifting account. No UI in this plan — Plan 2b surfaces it.

- [ ] **Step 1: Write the failing pairing tests**

Cover, each as its own Robolectric test:

1. `an equal and opposite pair one second apart is grouped` — the real `Own Account Transfer` case; assert both rows share a non-null `transferGroupId` and both have `kind = TRANSFER`.
2. `neither leg of a transfer counts as spending` — assert a sum over non-transfer debits excludes them.
3. `a same-account pair is not a transfer` — equal amount, opposite direction, same account, must **not** pair.
4. `an unequal amount does not pair`.
5. `a pair outside the fifteen minute window does not pair`.
6. `an already-grouped transaction is not re-paired`.
7. `when two candidates qualify the closest in time wins`.
8. `pairing is deterministic when run twice` — run `pair()` twice, assert the same group id and no third row affected.

- [ ] **Step 2: Write the failing reconciliation tests**

1. `an account whose reported balance matches computes no drift`.
2. `an account whose reported balance is lower reports the gap and its date`.
3. `an account with no reported balance never drifts` — a manual-only account must not appear.
4. `drift is expressed in Money, not raw minor units`.

- [ ] **Step 3: Run both to verify they fail**

```bash
export JAVA_HOME="E:/Android/Android Studio/jbr"
./gradlew testDebugUnitTest --tests "*TransferPairingTest*" --tests "*ReconciliationRepositoryTest*"
```

- [ ] **Step 4: Implement `TransferPairing`**

Uses `TransactionDao.findPairCandidates` and `markAsTransfer`. Runs inside the
pipeline's existing `withTransaction` block so a half-formed pair is impossible.

- [ ] **Step 5: Implement `ReconciliationRepository`**

Maps `AccountEntity` → `BalanceDrift` where `hasBalanceDrift` (already on the domain
`Account` from Plan 1) is true.

- [ ] **Step 6: Wire pairing into the pipeline**

After a successful write in `IngestionPipeline`, call `TransferPairing.pair(id)`
inside the same database transaction.

- [ ] **Step 7: Run the tests to verify they pass**

```bash
./gradlew testDebugUnitTest
```

Expected: all green.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add transfer pairing and balance reconciliation"
```

---

### Task 8: Message sources, backfill, and reparse

The last task. Connects the pipeline to the phone.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/sms/MessageSource.kt`
- Create: `app/src/main/java/com/wasif/khata/core/sms/SmsSource.kt`
- Create: `app/src/main/java/com/wasif/khata/core/sms/SmsReceiver.kt`
- Create: `app/src/main/java/com/wasif/khata/core/sms/BackfillWorker.kt`
- Create: `app/src/main/java/com/wasif/khata/core/sms/ReparseUseCase.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts` (WorkManager + Hilt worker)
- Test: `app/src/test/java/com/wasif/khata/core/sms/ReparseUseCaseTest.kt`

**Interfaces:**
- Produces:
  - `interface MessageSource { suspend fun readAll(): List<IncomingMessage> }`
  - `data class IncomingMessage(val sender: String, val body: String, val receivedAt: Long)`
  - `class SmsSource : MessageSource` — reads the SMS inbox via `Telephony.Sms.Inbox`
  - `SmsReceiver : BroadcastReceiver` — `@AndroidEntryPoint`, handles `SMS_RECEIVED`
  - `BackfillWorker : CoroutineWorker` — chunked, resumable
  - `class ReparseUseCase { suspend operator fun invoke(): ReparseSummary }`

**Permissions.** `AndroidManifest.xml` gains `READ_SMS` and `RECEIVE_SMS`. Both are
Google-restricted and cannot ship on the Play Store for this use case — which is
exactly why ingestion sits behind `MessageSource`. A future public build supplies a
`NotificationListenerService` implementation instead and nothing downstream changes.
**Do not write the notification implementation in this plan.** No runtime permission
UI either; that is Plan 2b.

**Backfill** reads every inbox message whose sender matches a known account's
`smsIdentifiers`, feeds each through `IngestionPipeline.ingest`, and reports progress.
Idempotent by construction — `insertIgnoringDuplicate` means a second run inserts
nothing.

**Reparse** re-runs the current rule set over `allForReparse()`. This is the repair
mechanism for a system with no review gate: a rule added later retroactively fixes
history. It must **not** duplicate transactions — a message already `PARSED` whose
transaction still exists is skipped unless the rule that matched has changed.

- [ ] **Step 1: Write the failing reparse tests**

1. `reparse over an empty rule set marks everything UNMATCHED and records nothing`.
2. `adding a rule and reparsing retroactively records the previously unmatched message`.
3. `reparsing twice does not duplicate a transaction`.
4. `reparse leaves IGNORED messages ignored`.

- [ ] **Step 2: Run to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "*ReparseUseCaseTest*"
```

- [ ] **Step 3: Implement `MessageSource` and `SmsSource`**

`SmsSource` queries `Telephony.Sms.Inbox` for `ADDRESS`, `BODY`, `DATE`. It is the
only file in this plan that touches an Android content provider; keep the query in
one function so a notification-based source can replace it wholesale.

- [ ] **Step 4: Implement `SmsReceiver`**

`@AndroidEntryPoint` `BroadcastReceiver` registered for
`android.provider.Telephony.SMS_RECEIVED`. It must not do work on the main thread:
extract the message, then hand off via `goAsync()` to a coroutine that calls
`IngestionPipeline.ingest`.

- [ ] **Step 5: Implement `BackfillWorker` and `ReparseUseCase`**

`BackfillWorker` is a `@HiltWorker CoroutineWorker` processing in chunks of 200 with
`setProgress`, cancellable and resumable.

- [ ] **Step 6: Run the full suite**

```bash
./gradlew testDebugUnitTest
```

- [ ] **Step 7: Confirm the app still assembles with the new permissions and receiver**

```bash
./gradlew assembleDebug
```

A malformed manifest or a missing Hilt worker factory is not caught by unit tests.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add SMS source, broadcast receiver, backfill worker, and reparse"
```

---

## Done when

- `./gradlew testDebugUnitTest` passes, including the 22-test corpus suite.
- `./gradlew assembleDebug` succeeds.
- `app/schemas/com.wasif.khata.core.data.KhataDatabase/2.json` is committed — Plan 3's migration depends on it.
- Every IGNORE sample in the corpus has a test asserting **zero** transactions were recorded.
- No file under `core/ui/`, no Compose file, and no theme token was touched.

## Explicitly deferred to Plan 2b

Rule-editor screen · unmatched-messages screen · confidence markers in the ledger ·
reconciliation drift surfaced in the UI · runtime SMS permission request flow ·
backfill progress UI · settings entries.

## Explicitly deferred to Plan 3

AI fallback for unmatched formats, AI-drafted rules, merchant category suggestion,
redaction, the API key, and the kill switch. Plan 2a is rules-only and makes no
network call.
