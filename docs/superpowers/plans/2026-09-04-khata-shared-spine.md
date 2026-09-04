# Shared Spine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Places, media, tags and full-text search exist as shared tables, the ledger's search stops missing things it should find, and photos reach Drive.

**Architecture:** One migration (7 → 8) adds six tables. A Kotlin-maintained FTS4 index replaces the ledger's `LIKE` query and is fed by per-module `IndexSource` bindings. Media is content-addressed and write-once; each blob goes to Drive inside the existing `BackupFile` envelope, so no new crypto and no change to the nightly archive.

**Tech Stack:** Kotlin, Room 2.8.4 (`@Fts4`), Hilt multibinding, Paging 3, `HttpURLConnection`, `javax.crypto` via `BackupFile`, Robolectric + JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-04-khata-shared-spine-design.md`

**Paired with:** `2026-09-04-khata-restaurants.md`. This plan ships no screens; restaurants is where every table here becomes touchable. Build this one first.

## Global Constraints

- **No new dependency in this plan.** Coil is specced for media display (spine §4) but nothing here displays an image — it lands in the restaurants plan, with the first screen that needs it.
- **The nightly archive does not change.** `BackupFile` stays format v1, `BackupRepository.backUp`/`restore` keep their `ByteArray` signatures and one-shot crypto. Media travels beside the archive, never inside it (spine §8). If you find yourself editing `BackupFile.write`/`read`, stop — that is not this plan.
- **`SCHEMA_VERSION` in `core/backup/BackupRepository.kt` goes 7 → 8** alongside the Room version.
- **Soft deletes everywhere.** Every table carries `uuid` (unique index), `createdAt`, `updatedAt`, `deletedAt` nullable.
- **`unicode61` tokenizer, never `simple`** (spine S4). `simple` is ASCII-only and would silently never match Bengali.
- Colour literals stay in `core/ui/theme` (DESIGN.md §1.1, convention — not test-enforced). Every Material role set explicitly (§1.6).
- Money is `Long` paisa. Times are UTC epoch millis, bucketed in `Asia/Dhaka` via `core/time/KhataClock.kt`'s `DHAKA`.
- Ponytail: shortest diff that works, no abstraction with one implementation, stdlib before dependencies.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `core/data/entity/PlaceEntity.kt`, `MediaEntity.kt`, `MediaLinkEntity.kt`, `TagEntity.kt`, `TagLinkEntity.kt`, `SearchFtsEntity.kt` | The six tables |
| `core/data/dao/PlaceDao.kt`, `MediaDao.kt`, `TagDao.kt`, `SearchDao.kt` | Their queries. Link tables are served by the DAO of the thing they link |
| `core/search/SearchIndex.kt` | `IndexSource`, `SearchIndex`, and the pure query builder |
| `core/search/TransactionIndexSource.kt` | The ledger's contribution to the index |
| `core/media/MediaStore.kt` | Hash, downscale decision, write-once file store |
| `core/place/MapsUrl.kt` | Pure parser for shared Maps text |
| `core/place/PlaceRepository.kt` | Save, and the one redirect for short links |
| `core/drive/MediaSync.kt` | Blobs up, blobs back down |
| `docs/superpowers/specs/maps-urls.md` | Corpus, tested the way `sms-corpus.md` is |

**Modified**

| File | Change |
|---|---|
| `core/data/KhataDatabase.kt` | Six entities, four DAOs, `version = 8` |
| `core/data/migration/Migrations.kt` | `MIGRATION_7_8` |
| `core/data/di/DatabaseModule.kt` | Register it |
| `core/data/dao/TransactionDao.kt` | The paged search query joins `search_fts` |
| `core/data/repository/TransactionRepositoryImpl.kt` | Drops `pagingSourceMatching`, calls the FTS one |
| `core/data/di/RepositoryModule.kt` | `@IntoSet` binding for `TransactionIndexSource` |
| `core/backup/BackupRepository.kt` | `SCHEMA_VERSION = 8` |
| `core/backup/BackupWorker.kt` | Media sync after the archive upload |

---

### Task 1: The schema, and a migration that matches it

Room generates the tables; the migration has to produce byte-identical SQL or Room's identity check rejects the result at runtime. Writing that SQL by hand is guesswork, so this task builds the entities first, lets Room export `schemas/8.json`, and copies the exact `createSql` out of it.

**Files:**
- Create: `core/data/entity/{Place,Media,MediaLink,Tag,TagLink,SearchFts}Entity.kt`
- Create: `core/data/dao/{Place,Media,Tag,Search}Dao.kt`
- Modify: `core/data/KhataDatabase.kt`
- Modify: `core/data/migration/Migrations.kt`
- Modify: `core/data/di/DatabaseModule.kt:38`
- Modify: `core/backup/BackupRepository.kt:15`
- Test: `app/src/test/java/com/wasif/khata/core/data/migration/Migration7To8Test.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: the six entities above; `PlaceDao`, `MediaDao`, `TagDao`, `SearchDao`; `MIGRATION_7_8`; `KhataDatabase` at version 8 exposing `placeDao()`, `mediaDao()`, `tagDao()`, `searchDao()`.

- [ ] **Step 1: Write the entities**

`PlaceEntity.kt`:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "places", indices = [Index(value = ["uuid"], unique = true)])
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val address: String? = null,
    /** Null is normal: a short link that would not resolve offline still stores its URL. */
    val lat: Double? = null,
    val lng: Double? = null,
    val mapsUrl: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`MediaEntity.kt` — note `sha256` is unique and there is no path column; the path is derived (spine S5):

```kotlin
@Entity(
    tableName = "media",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["sha256"], unique = true)],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    /** Of the stored bytes. Names the file, so an absolute path never has to be stored. */
    val sha256: String,
    val mimeType: String,
    val widthPx: Int,
    val heightPx: Int,
    val byteSize: Long,
    val capturedAt: Long? = null,
    /** A link back to the gallery, never a dependency. Dead links cost nothing. */
    val originalUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`MediaLinkEntity.kt`:

```kotlin
@Entity(
    tableName = "media_links",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["mediaId"]),
        Index(value = ["entityType", "entityId"]),
    ],
)
data class MediaLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val mediaId: Long,
    val entityType: String,
    val entityId: Long,
    /** Ordering by id breaks the first time a photo is removed and re-added. */
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`TagEntity.kt` — the `COLLATE NOCASE` is the whole point (spine §6):

```kotlin
@Entity(tableName = "tags", indices = [Index(value = ["uuid"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    /**
     * Matched case-insensitively by TagDao.findByName. Without that, autocomplete
     * starts offering three spellings of one person and "everywhere I went with
     * Rafi" returns a third of the answer.
     */
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`TagLinkEntity.kt` — the compound unique index makes attaching twice a no-op:

```kotlin
@Entity(
    tableName = "tag_links",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["tagId"]),
        Index(value = ["entityType", "entityId"]),
        Index(value = ["tagId", "entityType", "entityId"], unique = true),
    ],
)
data class TagLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val tagId: Long,
    val entityType: String,
    val entityId: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`SearchFtsEntity.kt` — no house quartet, because an FTS table is an index rather than a record:

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * unicode61, never simple: simple is ASCII-only, so a Bengali merchant name would be
 * silently unfindable. entityType and entityId are filters and join keys, not text
 * anyone searches for, so they are excluded from the index itself.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["entityType", "entityId"])
@Entity(tableName = "search_fts")
data class SearchFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val entityType: String,
    val entityId: Long,
    val text: String,
)
```

- [ ] **Step 2: Write the DAOs**

`SearchDao.kt`:

```kotlin
package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wasif.khata.core.data.entity.SearchFtsEntity

@Dao
interface SearchDao {
    @Insert
    suspend fun insert(row: SearchFtsEntity)

    @Query("DELETE FROM search_fts WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun delete(entityType: String, entityId: Long)

    @Query("DELETE FROM search_fts WHERE entityType = :entityType")
    suspend fun deleteAll(entityType: String)

    @Query("SELECT entityId FROM search_fts WHERE entityType = :entityType AND text MATCH :query")
    suspend fun idsMatching(entityType: String, query: String): List<Long>
}
```

`TagDao.kt` — `findByName` is where NOCASE lives:

```kotlin
@Dao
interface TagDao {
    @Upsert
    suspend fun upsert(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Upsert
    suspend fun upsertLink(link: TagLinkEntity)

    @Query(
        "UPDATE tag_links SET deletedAt = :now WHERE tagId = :tagId " +
            "AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun detach(tagId: Long, entityType: String, entityId: Long, now: Long)

    @Query(
        "SELECT t.* FROM tags t JOIN tag_links l ON l.tagId = t.id " +
            "WHERE l.entityType = :entityType AND l.entityId = :entityId " +
            "AND l.deletedAt IS NULL AND t.deletedAt IS NULL ORDER BY t.name",
    )
    suspend fun tagsFor(entityType: String, entityId: Long): List<TagEntity>
}
```

`MediaDao.kt` needs `findByHash`, `upsert`, `upsertLink`, `mediaFor(entityType, entityId)` ordered by `sortOrder`, `detachLink`, and `allLive()` returning every non-tombstoned `MediaEntity` (Task 6 uploads from it). `PlaceDao.kt` needs `upsert`, `findById`, `observeAll()`. Follow `CategoryDao.kt` for style; use `@Upsert` as the other DAOs do.

- [ ] **Step 3: Register everything with Room and bump the version**

In `core/data/KhataDatabase.kt`, add the six entities to the `entities = [...]` array, `version = 8`, and four abstract DAO getters.

- [ ] **Step 4: Build, and read the schema Room exports**

```bash
./gradlew :app:assembleDebug
```

This writes `app/schemas/com.wasif.khata.core.data.KhataDatabase/8.json`. Do not hand-write the migration SQL — take it from there:

```bash
python -c "
import json
d=json.load(open('app/schemas/com.wasif.khata.core.data.KhataDatabase/8.json'))['database']
new={'places','media','media_links','tags','tag_links','search_fts'}
for e in d['entities']:
    if e['tableName'] in new:
        print(e['createSql'].replace('\${TABLE_NAME}', e['tableName']) + ';')
        for i in e.get('indices', []):
            print(i['createSql'].replace('\${TABLE_NAME}', e['tableName']) + ';')
"
```

- [ ] **Step 5: Write the migration from that output**

In `core/data/migration/Migrations.kt`, above `MIGRATION_6_7`:

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Statements copied verbatim from schemas/8.json. Room compares the result
        // against its own identity hash at open time, so anything hand-adjusted here
        // fails at runtime rather than at compile time.
        // <paste each CREATE from Step 4, as db.execSQL("...")>

        // The index is backfilled in SQL so search works the next time the app opens,
        // rather than after a rebuild the user has to know to trigger. Aliases and
        // tags are left out: nothing has tags yet, and SearchIndex.reindexAll covers
        // aliases the first time a transaction is written.
        db.execSQL(
            "INSERT INTO search_fts (entityType, entityId, text) " +
                "SELECT 'transaction', id, " +
                "COALESCE(merchantRaw, '') || ' ' || COALESCE(note, '') || ' ' || " +
                "COALESCE(counterparty, '') " +
                "FROM transactions WHERE deletedAt IS NULL",
        )
    }
}
```

Register it in `core/data/di/DatabaseModule.kt:38` by appending `, MIGRATION_7_8` to `addMigrations(...)` and adding the import.

- [ ] **Step 6: Bump the backup schema stamp**

`core/backup/BackupRepository.kt:15`: `const val SCHEMA_VERSION = 8`.

It is stamped into every archive and compared on restore, so leaving it at 7 makes a v8 backup claim to be v7 and disables the "refuse a backup newer than this app" guard.

- [ ] **Step 7: Write the migration test**

Copy `app/src/test/java/com/wasif/khata/core/data/migration/Migration6To7Test.kt` to `Migration7To8Test.kt` and change: `TEST_DB` to `"migration-7-8-test.db"`, `createV6Database` to `createV7Database` with `schemaJson(7)` and `Callback(7)` and `db.version = 7`, and `addMigrations(MIGRATION_6_7)` to `addMigrations(MIGRATION_7_8)`. Then replace the test body:

```kotlin
    @Test
    fun `the spine tables arrive, and existing transactions are already indexed`() = runTest {
        createV7Database().use { db ->
            db.execSQL(
                "INSERT INTO transactions (uuid, accountId, amountMinor, direction, kind, " +
                    "occurredAt, merchantRaw, note, counterparty, source, confidence, " +
                    "createdAt, updatedAt) VALUES " +
                    "('t1', 1, 8560, 'DEBIT', 'NORMAL', 1000, 'FOODPANDA BD', 'lunch', " +
                    "NULL, 'SMS', 'HIGH', 1000, 1000)",
            )
        }

        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()

        // Opening through Room runs its identity-hash check, so a migration whose SQL
        // disagrees with the entities fails here rather than on a user's phone.
        assertEquals(listOf(1L), db.searchDao().idsMatching("transaction", "foodpanda"))
        assertEquals(listOf(1L), db.searchDao().idsMatching("transaction", "lunch"))
        db.close()
    }
```

The transaction column list must match `TransactionEntity` at v7 exactly — check `schemas/7.json` if the insert is rejected.

- [ ] **Step 8: Run it**

```bash
./gradlew :app:testDebugUnitTest --tests "*Migration7To8Test*"
```

Expected: PASS. A failure mentioning an identity hash means Step 5's SQL does not match Step 4's output.

- [ ] **Step 9: Run the whole suite**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

`Migration1To2Test` walks every version upward and needs `MIGRATION_7_8` added to its chain and its `Callback(n)`; the compiler will not catch this, the test will.

- [ ] **Step 10: Commit**

```bash
git add app/src app/schemas
git commit -m "feat(spine): six tables, and the migration Room agrees with

Places, media, tags, their link tables, and an FTS4 index. Migration SQL is
copied from the schema Room exports rather than written by hand -- Room checks
its identity hash at open time, so a hand-adjusted CREATE fails on a phone
rather than in CI.

The index is backfilled in the migration so search works the next time the app
opens rather than after a rebuild nobody knows to trigger.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Search, and the three things the ledger currently misses

The current query is `merchantRaw LIKE ? OR note LIKE ?`. Three failures follow from that and all three are fixed by the join, so they are the tests.

**Files:**
- Create: `core/search/SearchIndex.kt`, `core/search/TransactionIndexSource.kt`
- Modify: `core/data/dao/TransactionDao.kt:65-73` and `:309-316`
- Modify: `core/data/repository/TransactionRepositoryImpl.kt:55`
- Modify: `core/data/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/wasif/khata/core/search/SearchQueryTest.kt`, `SearchIndexTest.kt`

**Interfaces:**
- Consumes: `SearchDao` (Task 1).
- Produces: `fun ftsQuery(raw: String): String?`; `interface IndexSource { val entityType: String; suspend fun textFor(entityId: Long): String?; suspend fun allIds(): List<Long> }`; `class SearchIndex` with `reindex`, `remove`, `reindexAll`.

- [ ] **Step 1: Write the failing tests for the query builder**

`app/src/test/java/com/wasif/khata/core/search/SearchQueryTest.kt`:

```kotlin
package com.wasif.khata.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `a term becomes a prefix match`() {
        assertEquals("food*", ftsQuery("food"))
    }

    @Test
    fun `only the last term is a prefix, so earlier words must match whole`() {
        // FTS4 ANDs implicitly. Prefixing every term would make "sul din" match far
        // more than the user meant; prefixing only the last is as-you-type.
        assertEquals("sultan din*", ftsQuery("sultan din"))
    }

    @Test
    fun `quotes are stripped rather than escaped`() {
        // The LIKE query this replaces carries a comment about an unescaped % turning
        // a search into "return everything". The FTS equivalent of that mistake is a
        // stray quote, which breaks MATCH syntax into a SQL exception.
        assertEquals("foo*", ftsQuery("""foo"'"""))
        assertEquals("a b*", ftsQuery("a OR b"))
    }

    @Test
    fun `an empty or punctuation-only query searches for nothing`() {
        assertNull(ftsQuery(""))
        assertNull(ftsQuery("   "))
        assertNull(ftsQuery("\"*^"))
    }
}
```

`ftsQuery("a OR b")` expecting `"a b*"` is deliberate: `OR` is an FTS operator and is stripped as one, leaving an implicit AND.

- [ ] **Step 2: Run them and watch them fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*SearchQueryTest*"
```

Expected: FAIL, unresolved reference `ftsQuery`.

- [ ] **Step 3: Write `SearchIndex.kt`**

```kotlin
package com.wasif.khata.core.search

import com.wasif.khata.core.data.dao.SearchDao
import com.wasif.khata.core.data.entity.SearchFtsEntity
import javax.inject.Inject
import javax.inject.Singleton

/** FTS operators, and the quote that turns a typo into a SQL error. */
private val OPERATORS = Regex("""["'*^():]|\b(AND|OR|NOT|NEAR)\b""")

/**
 * Null when there is nothing to search for. Callers treat that as "no query" rather
 * than as "match nothing", because an empty box should show the whole ledger.
 */
fun ftsQuery(raw: String): String? {
    val terms = raw.replace(OPERATORS, " ").split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return null
    // Trailing * on the last term only: the user is still typing that one.
    return terms.dropLast(1).joinToString(" ", postfix = if (terms.size > 1) " " else "") +
        terms.last() + "*"
}

/**
 * What a module contributes to the index. One @IntoSet binding and a module is
 * searchable, including full rebuilds -- which is the "module two is UI work" promise
 * made checkable rather than asserted.
 */
interface IndexSource {
    val entityType: String

    /** Null when the row is deleted or has nothing worth indexing. */
    suspend fun textFor(entityId: Long): String?

    suspend fun allIds(): List<Long>
}

@Singleton
class SearchIndex @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards IndexSource>,
    private val dao: SearchDao,
) {
    /**
     * Delete-then-insert rather than update: FTS4 has no upsert, and one row per
     * entity is the invariant the join depends on.
     *
     * This is also how tags fold in. Tag names are part of an entity's text, so
     * attaching one calls reindex on the entity it was attached to and the source
     * rebuilds that row -- no separate tag-search path, and no join at query time.
     */
    suspend fun reindex(entityType: String, entityId: Long) {
        dao.delete(entityType, entityId)
        val text = sources.firstOrNull { it.entityType == entityType }
            ?.textFor(entityId)
            ?: return
        dao.insert(SearchFtsEntity(entityType = entityType, entityId = entityId, text = text))
    }

    suspend fun remove(entityType: String, entityId: Long) = dao.delete(entityType, entityId)

    suspend fun reindexAll() {
        sources.forEach { source ->
            dao.deleteAll(source.entityType)
            source.allIds().forEach { id ->
                source.textFor(id)?.let {
                    dao.insert(SearchFtsEntity(source.entityType, id, it))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run the query tests**

```bash
./gradlew :app:testDebugUnitTest --tests "*SearchQueryTest*"
```

Expected: PASS, 4 tests.

- [ ] **Step 5: Write the transaction source**

`core/search/TransactionIndexSource.kt`. Its text is `merchantRaw`, `note`, `counterparty`, the resolved merchant's `canonicalName`, that merchant's aliases, and any tag names — the five things spine §7 lists. Category names are deliberately excluded: searching "Food" and getting four hundred rows is not a hunt.

```kotlin
@Singleton
class TransactionIndexSource @Inject constructor(
    private val transactions: TransactionDao,
    private val merchants: MerchantDao,
    private val tags: TagDao,
) : IndexSource {
    override val entityType = "transaction"

    override suspend fun textFor(entityId: Long): String? {
        val row = transactions.findById(entityId) ?: return null
        val merchant = row.merchantId?.let { merchants.findById(it) }
        val aliases = row.merchantId?.let { merchants.aliasesFor(it).map { a -> a.alias } }.orEmpty()
        val tagNames = tags.tagsFor(entityType, entityId).map { it.name }
        return (
            listOf(row.merchantRaw, row.note, row.counterparty, merchant?.canonicalName) +
                aliases + tagNames
            ).filterNot { it.isNullOrBlank() }.joinToString(" ")
    }

    override suspend fun allIds(): List<Long> = transactions.allIdsForIndex()
}
```

Add `@Query("SELECT id FROM transactions WHERE deletedAt IS NULL") suspend fun allIdsForIndex(): List<Long>` to `TransactionDao`, and whatever `merchants.aliasesFor` / `findById` need if `MerchantDao` lacks them.

- [ ] **Step 6: Switch the ledger query**

Replace `pagingSourceMatchingPattern` in `core/data/dao/TransactionDao.kt` (currently at :65-73) with:

```kotlin
    @Query(
        """
        SELECT t.* FROM transactions t
        JOIN search_fts f ON f.entityType = 'transaction' AND f.entityId = t.id
        WHERE f.text MATCH :query AND t.deletedAt IS NULL
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun pagingSourceMatchingFts(query: String): PagingSource<Int, TransactionEntity>
```

Delete the `pagingSourceMatching` extension at the bottom of the file (:309-316) along with its escaping comment — the hazard it guarded moves into `ftsQuery`, which has its own test.

In `TransactionRepositoryImpl.kt:55`, replace `transactionDao.pagingSourceMatching(query)` with `transactionDao.pagingSourceMatchingFts(ftsQuery(query) ?: return pagingSourceAll())` — match whatever the surrounding `when`/`if` already does for the empty-query branch, so an empty box still shows everything. `pagedTransactions`'s signature does not change, so `LedgerViewModel` and the Paging pipeline are untouched.

- [ ] **Step 7: Bind the source**

In `core/data/di/RepositoryModule.kt`, in the abstract class body:

```kotlin
    @Binds
    @IntoSet
    abstract fun bindTransactionIndexSource(impl: TransactionIndexSource): IndexSource
```

with `import dagger.multibindings.IntoSet`.

- [ ] **Step 8: Write the index tests**

`app/src/test/java/com/wasif/khata/core/search/SearchIndexTest.kt`, Robolectric with an in-memory `KhataDatabase` — follow `ReparseUseCaseTest.kt`'s setUp for the fixture. Four tests, one per failure being fixed plus the tokenizer guard:

```kotlin
    @Test
    fun `a merchant alias finds the transaction`() // FP*8823 resolved to Foodpanda; searching "foodpanda" finds it
    @Test
    fun `a multi-word query does not require adjacency`() // "sultan dine" finds "Sultans Dine Dhanmondi"
    @Test
    fun `counterparty is searchable`() // the owed-money feature writes names there
    @Test
    fun `a Bengali term matches`() // fails under the simple tokenizer, so S4 cannot regress
```

Each: insert the row, call `index.reindex("transaction", id)`, assert `searchDao.idsMatching("transaction", ftsQuery(term)!!)` contains it.

- [ ] **Step 9: Run everything**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

- [ ] **Step 10: Commit**

```bash
git add app/src
git commit -m "feat(spine): the ledger finds what it should have all along

Search was merchantRaw LIKE ? OR note LIKE ?, which missed three things it had
the data for: a row resolved to merchant Foodpanda but raw-texted FP*8823, any
multi-word query, and counterparty -- which the owed-money feature writes names
into.

The index is maintained in Kotlin rather than by SQLite triggers: triggers are
invisible to Room's schema validation, need hand-written SQL per module, and
make soft deletes fiddly. A module contributes one @IntoSet binding.

The LIKE escaper is gone; ftsQuery replaces it and carries the same hazard --
an unescaped quote breaks MATCH into a SQL error the way an unescaped % turned
a search into 'return everything'. It has its own tests.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Media, write-once and content-addressed

**Files:**
- Create: `core/media/MediaStore.kt`
- Test: `app/src/test/java/com/wasif/khata/core/media/MediaStoreTest.kt`

**Interfaces:**
- Consumes: `MediaDao` (Task 1).
- Produces: `fun sha256(bytes: ByteArray): String`; `fun needsDownscale(mimeType: String, longEdgePx: Int): Boolean`; `class MediaStore` with `suspend fun import(uri: Uri, entityType: String, entityId: Long): MediaEntity?`, `fun fileFor(sha256: String): File`, `suspend fun detach(mediaId: Long, entityType: String, entityId: Long)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.wasif.khata.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreTest {

    @Test
    fun `the same bytes hash the same, and different bytes do not`() {
        assertEquals(sha256(byteArrayOf(1, 2, 3)), sha256(byteArrayOf(1, 2, 3)))
        assertTrue(sha256(byteArrayOf(1)) != sha256(byteArrayOf(2)))
        // 64 hex characters, because it names a file.
        assertEquals(64, sha256(byteArrayOf(1)).length)
    }

    @Test
    fun `a small JPEG is copied rather than re-encoded`() {
        // Re-encoding an already-small JPEG spends quality to save nothing.
        assertFalse(needsDownscale("image/jpeg", 1600))
        assertFalse(needsDownscale("image/jpeg", 2048))
    }

    @Test
    fun `a large JPEG or any other format is re-encoded`() {
        assertTrue(needsDownscale("image/jpeg", 4032))
        // PNG at any size: a screenshot of a menu is megabytes as PNG and small as JPEG.
        assertTrue(needsDownscale("image/png", 800))
        assertTrue(needsDownscale("image/heic", 1000))
    }
}
```

- [ ] **Step 2: Run and watch them fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*MediaStoreTest*"
```

- [ ] **Step 3: Write it**

```kotlin
package com.wasif.khata.core.media

import java.security.MessageDigest

/** 2048 on the long edge, per 2026-08-26 D10: ~400 KB against ~4 MB, and indistinguishable. */
const val MAX_EDGE_PX = 2048

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun needsDownscale(mimeType: String, longEdgePx: Int): Boolean =
    mimeType != "image/jpeg" || longEdgePx > MAX_EDGE_PX
```

Then `MediaStore` itself, in the same file:

```kotlin
@Singleton
class MediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaDao,
    private val clock: KhataClock,
) {
    fun dir(): File = File(context.filesDir, "media").apply { mkdirs() }

    fun fileFor(sha256: String): File = File(dir(), "$sha256.jpg")

    /**
     * pick -> decode -> scale -> encode -> hash -> write -> row -> link.
     *
     * Content-addressed and write-once: if the file is there the work is done, and if
     * a row already carries the hash only a link is written. The same photo in two
     * visits costs one file and one row.
     */
    suspend fun import(uri: Uri, entityType: String, entityId: Long): MediaEntity? =
        withContext(Dispatchers.IO) { /* Step 4 */ }
}
```

The body: read the source with `context.contentResolver`, decode with **`ImageDecoder`, never `BitmapFactory`** — `ImageDecoder` applies EXIF orientation and `BitmapFactory` does not, and the failure mode is every food photo lying on its side. Ask `needsDownscale` whether to re-encode; if not, use the source bytes verbatim. Hash the final bytes, write to a temp file and `renameTo` (atomic, so a killed process never leaves a half file under a name that claims to be complete). Reuse the row when `dao.findByHash` hits. `capturedAt` from `MediaStore.DATE_TAKEN` on the picked URI when readable, null otherwise — not `androidx.exifinterface`, which is a whole dependency for one nullable convenience.

- [ ] **Step 4: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests "*MediaStoreTest*"
```

Expected: PASS, 3 tests. **Robolectric's `Bitmap` is a shadow that does not really decode, scale or encode** — that is why the decision logic is pure and tested here while the actual pixels are verified on the device in the restaurants plan. Do not try to assert on scaled output under Robolectric; it will pass for the wrong reason.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat(spine): photos, hashed and written once

Content-addressed, so the same photo imported twice is one file and one row, and
an absolute path -- which is wrong the moment it is read on another install --
never has to be stored.

ImageDecoder rather than BitmapFactory: it applies EXIF orientation and
BitmapFactory does not, and the failure mode is every food photo on its side.
An already-small JPEG is copied verbatim rather than re-encoded, which would
spend quality to save nothing.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Places, and a Maps URL that means what it says

**Files:**
- Create: `core/place/MapsUrl.kt`, `core/place/PlaceRepository.kt`, `docs/superpowers/specs/maps-urls.md`
- Test: `app/src/test/java/com/wasif/khata/core/place/MapsUrlTest.kt`

**Interfaces:**
- Consumes: `PlaceDao` (Task 1).
- Produces: `data class ParsedPlace(val name: String?, val lat: Double?, val lng: Double?, val url: String?)`; `fun parseSharedPlace(text: String): ParsedPlace?`; `PlaceRepository.save(parsed): Long`.

- [ ] **Step 1: Write the corpus**

`docs/superpowers/specs/maps-urls.md`, one case per row, in the shape `sms-corpus.md` uses:

```markdown
| Input | name | lat | lng |
|---|---|---|---|
| `https://www.google.com/maps/place/Sultans+Dine/@23.7461,90.3742,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d23.7461!4d90.3742` | Sultans Dine | 23.7461 | 90.3742 |
| `https://maps.google.com/?q=23.7461,90.3742` |  | 23.7461 | 90.3742 |
| `https://www.google.com/maps/search/?api=1&query=23.7461,90.3742` |  | 23.7461 | 90.3742 |
| `geo:23.7461,90.3742?q=Kacchi+Bhai` | Kacchi Bhai | 23.7461 | 90.3742 |
| `https://maps.app.goo.gl/AbCdEf` |  |  |  |
| `Sultan's Dine⏎https://maps.app.goo.gl/AbCdEf` | Sultan's Dine |  |  |
| `https://www.google.com/maps/place/X/@23.80,90.40,17z/data=!3d23.7461!4d90.3742` | X | 23.7461 | 90.3742 |
```

The last row is the one that matters most: `@` and `!3d!4d` disagree, and `!3d!4d` wins.

- [ ] **Step 2: Write the failing test**

`MapsUrlTest.kt` reads the corpus and asserts each row, the way `CorpusTest.kt` reads `sms-corpus.md`. Read `app/src/test/java/com/wasif/khata/core/sms/CorpusTest.kt` first and follow it exactly — including how it locates the file from both the repo root and `app/`.

Add one hand-written test beside it:

```kotlin
    @Test
    fun `the place wins over the camera`() {
        // @lat,lng is where the map happened to be centred; !3d!4d is the place. They
        // disagree whenever the user panned before sharing, and the camera is wrong.
        val parsed = parseSharedPlace(
            "https://www.google.com/maps/place/X/@23.80,90.40,17z/data=!3d23.7461!4d90.3742",
        )!!
        assertEquals(23.7461, parsed.lat!!, 0.0001)
    }
```

- [ ] **Step 3: Run it and watch it fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*MapsUrlTest*"
```

- [ ] **Step 4: Write the parser**

Pure, no Android, no network. Regexes for `!3d(-?[\d.]+)!4d(-?[\d.]+)` first, `@(-?[\d.]+),(-?[\d.]+)` second, `[?&]q=(-?[\d.]+),(-?[\d.]+)`, `query=(-?[\d.]+),(-?[\d.]+)`, `geo:(-?[\d.]+),(-?[\d.]+)`. Name from `/maps/place/([^/@]+)/` URL-decoded with `+` as space, or from `geo:...?q=`, or from a first line that is not a URL. Return null only when there is neither a URL nor coordinates.

- [ ] **Step 5: Write `PlaceRepository`**

`save(parsed)` writes the row. When `lat` is null and the URL is a `maps.app.goo.gl` short link, resolve it with one request before saving:

```kotlin
// One redirect, read from the Location header. The same 30-line HttpURLConnection
// pattern as GeminiClient and DriveClient -- the house approach, and no reason for a
// second one.
private fun resolve(shortUrl: String): String? = runCatching {
    (URL(shortUrl).openConnection() as HttpURLConnection).run {
        instanceFollowRedirects = false
        connectTimeout = 10_000
        try { getHeaderField("Location") } finally { disconnect() }
    }
}.getOrNull()
```

**Failure is normal, not an error.** Offline, or a link that will not resolve, still stores the URL and the place stays openable in Maps — `2026-08-26` §12 decided this and it is not reopened.

- [ ] **Step 6: Run the tests, then commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*MapsUrlTest*"
git add app/src docs
git commit -m "feat(spine): places, from a Maps share or a pasted link

A pure parser plus a corpus file, tested the way sms-corpus.md is -- the shape
that has already caught more parsing bugs in this app than anything else.

!3d!4d is preferred over @lat,lng: the @ is where the map happened to be
centred, the !3d!4d is the place, and they disagree whenever the user panned
before sharing. One corpus row exists purely to pin that.

A short link is resolved with one redirect, and failing to resolve stores the
URL anyway. Offline is a normal outcome for a link, not an error.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Tags

Small, and it is the pattern the restaurant name field copies.

**Files:**
- Create: `core/tag/TagRepository.kt`
- Test: `app/src/test/java/com/wasif/khata/core/tag/TagRepositoryTest.kt`

**Interfaces:**
- Consumes: `TagDao` (Task 1), `SearchIndex` (Task 2).
- Produces: `TagRepository` with `findOrCreate(name): Long`, `attach(tagId, entityType, entityId)`, `detach(...)`, `tagsFor(entityType, entityId)`, `observeAll()`.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `a differently cased name returns the same tag`() = runTest {
        // Without this, autocomplete offers three spellings of one person and
        // "everywhere I went with Rafi" quietly returns a third of the answer.
        val first = repository.findOrCreate("Rafi")
        assertEquals(first, repository.findOrCreate("rafi"))
        assertEquals(first, repository.findOrCreate("RAFI"))
    }

    @Test
    fun `attaching twice leaves one link`() = runTest {
        val tag = repository.findOrCreate("Rafi")
        repository.attach(tag, "restaurant_visit", 1)
        repository.attach(tag, "restaurant_visit", 1)

        assertEquals(1, repository.tagsFor("restaurant_visit", 1).size)
    }

    @Test
    fun `attaching a tag reindexes what it was attached to`() = runTest {
        // This is the whole of tag search: names are part of the entity's indexed
        // text, so there is no separate tag-search path and no join at query time.
        repository.attach(repository.findOrCreate("Rafi"), "transaction", txId)

        assertTrue(searchDao.idsMatching("transaction", "rafi*").contains(txId))
    }
```

- [ ] **Step 2: Run, fail, implement, pass**

`findOrCreate` is `dao.findByName(name)?.id ?: dao.upsert(TagEntity(...))`. `attach` upserts the link and then calls `searchIndex.reindex(entityType, entityId)` — the compound unique index makes a second attach a no-op, and `INSERT OR REPLACE` via `@Upsert` handles the collision. `detach` sets `deletedAt` and reindexes too.

```bash
./gradlew :app:testDebugUnitTest --tests "*TagRepositoryTest*"
```

- [ ] **Step 3: Commit**

```bash
git add app/src
git commit -m "feat(spine): tags, folded case and folded into search

UNIQUE COLLATE NOCASE on the name, because Rafi and rafi being two people is
the failure this table exists to avoid.

Attaching reindexes the entity rather than adding a tag-search path: names are
part of an entity's indexed text, so one method covers ordinary writes, tag
changes and full rebuilds, and 'Rafi' finds the dinner with no join at query
time.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Photos reach Drive

The nightly archive is not touched. Blobs go up beside it, each once, in the envelope the archive already uses.

**Files:**
- Create: `core/drive/MediaSync.kt`
- Modify: `core/backup/BackupWorker.kt`
- Test: `app/src/test/java/com/wasif/khata/core/drive/MediaSyncTest.kt`

**Interfaces:**
- Consumes: `DriveAuth`, `DriveClient` (already built), `MediaStore` and `MediaDao` (Task 3), `BackupFile` (already built).
- Produces: `class MediaSync` with `suspend fun push(): Int` and `suspend fun pull(hashes: List<String>): Int`.

- [ ] **Step 1: Extend `DriveClient` with what a blob needs**

`DriveClient` currently lists and downloads by folder. Media needs: list the names already in the folder (to skip re-uploads), upload a named byte array, and download by name. Add to `DriveBackups` or a second narrow interface — three methods, reusing the existing private `get`/`post`/`multipartBody` helpers. Do **not** write a second HTTP client.

- [ ] **Step 2: Write `MediaSync`**

```kotlin
/**
 * Each photo once, ever. The Drive name is the sha256 of the *plaintext*, not of the
 * ciphertext -- a random IV per encryption means ciphertext differs every time, and
 * dedup depends on the name being stable.
 */
private const val BLOB_SUFFIX = ".kbm"

@Singleton
class MediaSync @Inject constructor(
    private val drive: DriveMedia,
    private val store: MediaStore,
    private val dao: MediaDao,
    private val preferences: PreferencesRepository,
) {
    suspend fun push(): Int {
        val prefs = preferences.preferences.first()
        val key = prefs.backupKey?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return 0
        val salt = prefs.backupSalt?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return 0

        val there = drive.blobNames().toSet()
        return dao.allLive()
            .filterNot { "${it.sha256}$BLOB_SUFFIX" in there }
            .count { media ->
                val plain = store.fileFor(media.sha256).takeIf { it.exists() }?.readBytes()
                plain != null && drive.putBlob(
                    "${media.sha256}$BLOB_SUFFIX",
                    BackupFile.write(plain, key, SCHEMA_VERSION, salt),
                )
            }
    }

    /** After a restore: fetch only what the restored rows reference and this phone lacks. */
    suspend fun pull(hashes: List<String>): Int = hashes
        .filterNot { store.fileFor(it).exists() }
        .count { hash -> fetch(hash) }
}
```

`fetch` downloads the blob, derives the key from the passphrase the restore already collected, `BackupFile.read`s it, **re-hashes the plaintext and refuses it if it does not match the filename** — free integrity checking — then writes it via the same atomic temp-and-rename as `MediaStore`.

- [ ] **Step 3: Call `push` from the worker**

In `BackupWorker.doWork`, after the archive upload succeeds:

```kotlin
        // After the archive, and never instead of it. A photo that fails to upload is
        // not worth retrying the database backup for -- it is already safe on disk,
        // and the next run picks it up because the name is still missing from Drive.
        runCatching { mediaSync.push() }
```

- [ ] **Step 4: Write the tests**

Against a fake `DriveMedia` — no network:

```kotlin
    @Test
    fun `a blob already in Drive is not uploaded again`()
    @Test
    fun `a blob round-trips through the envelope`()          // push then pull, bytes identical
    @Test
    fun `a blob whose plaintext does not match its name is refused`()
    @Test
    fun `no passphrase means no media upload, and no error`()
    @Test
    fun `pull fetches only hashes this phone lacks`()
```

The third is the load-bearing one: corrupt the plaintext and assert `pull` writes no file.

- [ ] **Step 5: Run everything and commit**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
git add app/src
git commit -m "feat(spine): photos leave the phone, one blob at a time

The nightly archive is untouched -- still format v1, still one-shot, still the
database alone. Folding photos into a KEEP=7 rotation would re-encrypt and
re-upload the whole library seven times a night for bytes that by design never
change.

Each blob is wrapped in the BackupFile envelope under the same
passphrase-derived key, so nothing new decides how encryption works and blobs
inherit the header salt that makes a file openable from the passphrase alone.
Named by the plaintext hash, so dedup is a filename check and re-hashing on the
way back is a free integrity check.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Hardware walkthrough

Unit tests cannot reach the photo picker, real EXIF, or Drive.

1. **Search still works and works better.** Open the ledger, search a merchant you know resolved from an odd raw string — it appears now. Search two words that are not adjacent. Search a Bengali term.
2. **The migration ran.** `adb shell run-as com.wasif.khata sqlite3 databases/khata.db ".tables"` lists the six new tables, and `SELECT COUNT(*) FROM search_fts;` matches the transaction count.
3. **Backups still restore.** Settings → Back up now, then Restore a backup → the file just written. It must still work; format v1 is unchanged and this is the check that says so.
4. **A photo imports.** There is no screen yet, so drive it from the restaurants plan instead — or skip to that plan and do this once.
5. **Blobs reach Drive.** After a backup with Drive connected, the Khata folder holds `<sha256>.kbm` files alongside the `.kbk` archives.

## Done when

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` passes.
- Walkthrough steps 1–3 and 5 pass. Step 4 lands with the restaurants plan.
- A backup taken before this plan still restores after it.

## Deferred

Coil and every screen (restaurants plan). Media display, place pickers, tag chips. Export as a shareable archive, FTS5 and relevance ranking, the Google Places API — all recorded in spine §10.
