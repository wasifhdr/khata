# Restaurant Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A record of restaurants visited — who with, what you ate, what it cost, where it is, and photographs — plus the ones you have not been to yet.

**Architecture:** Three tables and five screens. Places, photos and companions are calls into the spine, not new schema. The wishlist and the overall verdict are derived, so there is nothing to keep in sync.

**Tech Stack:** Kotlin, Compose, Room (migration 8 → 9), Hilt, Coil 3, Robolectric + JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-04-khata-restaurants-design.md`

**Requires:** `2026-09-04-khata-shared-spine.md`, complete and merged. Every place, photo and tag here is a spine call; starting this first means building stubs you then delete.

## Global Constraints

- **This module adds no satellite tables.** Companions are `tag_links`, photos are `media_links`, location is `places.id`. If you find yourself adding a `restaurant_photos` table, the spine already has it.
- **`SCHEMA_VERSION` in `core/backup/BackupRepository.kt` goes 8 → 9** with the Room version. It is stamped into every archive and compared on restore; leaving it behind stamps a lie and disables the "refuse a backup newer than this app" guard for one version.
- **Nothing links a visit to a transaction.** `restaurant_visits` has no `transactionId` — R6, and spine §3.1 records why the wallet spec's promised pattern is not validated here.
- Colour literals stay in `core/ui/theme` (DESIGN.md §1.1). Every Material role set explicitly (§1.6) — an unset text colour renders near-black on Khata's ground, which has caused this exact bug twice.
- Reuse `core/ui/component`: `FieldScaffold`, `CollapsingTopBar`, `SectionLabel`, `Pill`, `MoneyText`, `KhataIcons`. Do not write new primitives for things that exist.
- Money is `Long` paisa (D7). `visitedAt` is UTC epoch millis, displayed in `Asia/Dhaka` via `core/time/KhataClock.kt`'s `DHAKA`.
- Ponytail: shortest diff that works, no abstraction with one implementation, stdlib before dependencies.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `core/data/entity/{Restaurant,RestaurantVisit,VisitDish}Entity.kt` | The three tables |
| `core/data/dao/RestaurantDao.kt` | Queries, including the derived wishlist and dish average |
| `core/data/repository/RestaurantRepository.kt` | `findOrCreate`, saving a visit and its dishes together |
| `core/search/RestaurantIndexSource.kt` | Name, note, place, every dish, every companion |
| `feature/restaurants/RestaurantsScreen.kt` + `UiState` + `ViewModel` | Been, and want to try |
| `feature/restaurants/VisitEditorScreen.kt` + `UiState` + `ViewModel` | The screen that has to be quick |
| `feature/restaurants/RestaurantScreen.kt` + `UiState` + `ViewModel` | One restaurant, its visits, its cover |
| `feature/search/SearchScreen.kt` + `UiState` + `ViewModel` | Grouped global search |

**Modified**

| File | Change |
|---|---|
| `core/data/KhataDatabase.kt` | Three entities, one DAO, `version = 9` |
| `core/data/migration/Migrations.kt` | `MIGRATION_8_9` |
| `core/data/di/DatabaseModule.kt` | Register it |
| `core/data/di/RepositoryModule.kt` | `@IntoSet` for `RestaurantIndexSource` |
| `core/backup/BackupRepository.kt` | `SCHEMA_VERSION = 9` |
| `navigation/KhataNavHost.kt` | Five routes |
| `feature/hub/ModulesScreen.kt:116` | Restaurants stops being a `DormantRow` |
| `AndroidManifest.xml` | `ACTION_SEND` / `text/plain` share target |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Coil 3 |

---

### Task 1: Three tables

Same method as the spine's Task 1: entities first, let Room export `schemas/9.json`, copy the exact `createSql`. Hand-written migration SQL fails Room's identity check at runtime rather than at compile time.

**Files:**
- Create: `core/data/entity/{Restaurant,RestaurantVisit,VisitDish}Entity.kt`, `core/data/dao/RestaurantDao.kt`
- Modify: `core/data/KhataDatabase.kt`, `core/data/migration/Migrations.kt`, `core/data/di/DatabaseModule.kt`, `core/backup/BackupRepository.kt:15`
- Test: `app/src/test/java/com/wasif/khata/core/data/migration/Migration8To9Test.kt`

**Interfaces:**
- Consumes: `PlaceEntity`, `MediaEntity` (spine Task 1) — by id only, no foreign keys.
- Produces: the three entities; `RestaurantDao`; `MIGRATION_8_9`; `KhataDatabase` at version 9 with `restaurantDao()`.

- [ ] **Step 1: Write the entities**

```kotlin
@Entity(tableName = "restaurants", indices = [Index(value = ["uuid"], unique = true)])
data class RestaurantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    /**
     * Matched case-insensitively by findByName, exactly as tags are: without it
     * "Sultans Dine" and "sultans dine" become two restaurants and the autocomplete
     * starts suggesting duplicates of itself.
     */
    val name: String,
    val placeId: Long? = null,
    /** A pointer into media, never a second copy of the file. */
    val coverMediaId: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "restaurant_visits",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["restaurantId"]),
        Index(value = ["visitedAt"]),
    ],
)
data class RestaurantVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val restaurantId: Long,
    val visitedAt: Long,
    /** 1..5, or null. A visit you did not rate is still a visit. */
    val ambianceRating: Int? = null,
    /** Paisa, and owed nothing to the wallet: a meal someone else paid for has a cost. */
    val costMinor: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "visit_dishes", indices = [
    Index(value = ["uuid"], unique = true),
    Index(value = ["visitId"]),
])
data class VisitDishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val visitId: Long,
    /** Free text, no catalogue: maintaining a menu is friction on the quickest screen. */
    val name: String,
    val rating: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

- [ ] **Step 2: Write `RestaurantDao`**

The two derived queries are the ones worth getting right:

```kotlin
    @Query("SELECT * FROM restaurants WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): RestaurantEntity?

    /**
     * Been: has at least one live visit. Want to try: has none. Derived rather than
     * flagged, so logging the first visit promotes it with nothing to flip and
     * nothing to drift.
     */
    @Query(
        """
        SELECT r.* FROM restaurants r
        WHERE r.deletedAt IS NULL
          AND (SELECT COUNT(*) FROM restaurant_visits v
               WHERE v.restaurantId = r.id AND v.deletedAt IS NULL) = :visits
        """,
    )
    fun observeByVisitCount(visits: Int): Flow<List<RestaurantEntity>>

    /**
     * AVG ignores NULL, which is the behaviour wanted: an unrated dish is unrated,
     * not zero out of five.
     */
    @Query(
        """
        SELECT AVG(d.rating) FROM visit_dishes d
        JOIN restaurant_visits v ON v.id = d.visitId
        WHERE v.restaurantId = :restaurantId AND d.deletedAt IS NULL AND v.deletedAt IS NULL
        """,
    )
    suspend fun dishAverage(restaurantId: Long): Double?
```

`observeByVisitCount(0)` is the wishlist. "Been" needs `> 0`, so write it as a second query rather than bending this one.

Plus: `upsert`, `upsertVisit`, `upsertDishes`, `observeAllBeen()` ordered by last visit descending, `visitsFor(restaurantId)`, `dishesFor(visitId)`, `namesLike(prefix)` for autocomplete.

- [ ] **Step 3: Register, build, copy the schema, write the migration**

`KhataDatabase`: three entities, `version = 9`, `abstract fun restaurantDao()`. Then:

```bash
./gradlew :app:assembleDebug
python -c "
import json
d=json.load(open('app/schemas/com.wasif.khata.core.data.KhataDatabase/9.json'))['database']
new={'restaurants','restaurant_visits','visit_dishes'}
for e in d['entities']:
    if e['tableName'] in new:
        print(e['createSql'].replace('\${TABLE_NAME}', e['tableName']) + ';')
        for i in e.get('indices', []):
            print(i['createSql'].replace('\${TABLE_NAME}', e['tableName']) + ';')
"
```

Write `MIGRATION_8_9` from that output, register it in `DatabaseModule`, and set `SCHEMA_VERSION = 9`.

- [ ] **Step 4: Write `Migration8To9Test`**

Copy `Migration7To8Test.kt`, change the version numbers, and assert the three tables exist by inserting a restaurant and reading it back through Room — which runs the identity check.

- [ ] **Step 5: Run everything**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

`Migration1To2Test` walks every version and needs `MIGRATION_8_9` in its chain and its `Callback(n)`.

- [ ] **Step 6: Commit**

```bash
git add app/src app/schemas
git commit -m "feat(restaurants): three tables, and nothing the spine already has

Restaurants, visits, dishes. Companions are tag_links, photos are media_links,
location is a places id -- this module adds no satellite tables of its own,
which is the claim the spine was built to make true.

The wishlist and the dish average are queries, not columns: a restaurant with
no visits is one you have not been to, and logging a visit promotes it with
nothing to flip. AVG ignores NULL, so an unrated dish is unrated rather than
zero out of five.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: The repository, and the four behaviours worth pinning

**Files:**
- Create: `core/data/repository/RestaurantRepository.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/repository/RestaurantRepositoryTest.kt`

**Interfaces:**
- Consumes: `RestaurantDao` (Task 1), `SearchIndex`, `TagRepository`, `MediaStore` (spine).
- Produces: `RestaurantRepository` with `findOrCreate(name): Long`, `saveVisit(visit, dishes, companions): Long`, `observeBeen()`, `observeWishlist()`, `verdict(restaurantId): Double?`, `setCover(restaurantId, mediaId)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `a differently cased name finds the existing restaurant`() = runTest {
        val first = repository.findOrCreate("Sultans Dine")
        assertEquals(first, repository.findOrCreate("sultans dine"))
    }

    @Test
    fun `a restaurant with no visits is on the wishlist, and a visit moves it`() = runTest {
        val id = repository.findOrCreate("Kacchi Bhai")
        assertEquals(listOf(id), repository.observeWishlist().first().map { it.id })

        repository.saveVisit(visit(restaurantId = id), dishes = emptyList(), companions = emptyList())

        assertTrue(repository.observeWishlist().first().isEmpty())
        assertEquals(listOf(id), repository.observeBeen().first().map { it.id })
    }

    @Test
    fun `soft-deleting the only visit puts it back on the wishlist`() = runTest {
        // Derived means it goes both ways for free. A stored flag would need code for
        // this direction and would eventually be missing it.
        val id = repository.findOrCreate("Kacchi Bhai")
        val visitId = repository.saveVisit(visit(id), emptyList(), emptyList())

        repository.deleteVisit(visitId)

        assertEquals(listOf(id), repository.observeWishlist().first().map { it.id })
    }

    @Test
    fun `the dish average ignores unrated dishes`() = runTest {
        val id = repository.findOrCreate("Sultans Dine")
        repository.saveVisit(
            visit(id),
            dishes = listOf(dish("kacchi", 5), dish("borhani", null), dish("firni", 3)),
            companions = emptyList(),
        )

        // 4.0, not 2.67: an unrated dish is unrated, not zero out of five.
        assertEquals(4.0, repository.verdict(id)!!, 0.001)
    }

    @Test
    fun `setting a cover points at the existing media row`() = runTest {
        // "Set any photo from that visit as the cover" is a reference, never a copy.
        val id = repository.findOrCreate("Sultans Dine")
        repository.setCover(id, mediaId = existingMediaId)

        assertEquals(existingMediaId, repository.find(id)!!.coverMediaId)
        assertEquals(1, mediaDao.count())
    }
```

- [ ] **Step 2: Run, fail, implement, pass**

`saveVisit` writes the visit, its dishes, attaches companion tags via `TagRepository.attach`, and calls `searchIndex.reindex("restaurant", restaurantId)` — the restaurant, not the visit, because §6 indexes one row per restaurant.

```bash
./gradlew :app:testDebugUnitTest --tests "*RestaurantRepositoryTest*"
```

- [ ] **Step 3: Commit**

---

### Task 3: Search, indexed and grouped

**Files:**
- Create: `core/search/RestaurantIndexSource.kt`, `feature/search/SearchScreen.kt`, `SearchUiState.kt`, `SearchViewModel.kt`
- Modify: `core/data/di/RepositoryModule.kt`, `navigation/KhataNavHost.kt`
- Test: `app/src/test/java/com/wasif/khata/core/search/RestaurantSearchTest.kt`

**Interfaces:**
- Consumes: `IndexSource`, `SearchIndex`, `ftsQuery`, `SearchDao` (spine Task 2).
- Produces: `RestaurantIndexSource`; `SearchUiState(transactions, restaurants, places)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `a dish name finds the restaurant, not a visit buried inside it`() = runTest {
        // One row per restaurant: searching "kacchi" should land you on the place,
        // not on an evening.
        assertTrue(searchDao.idsMatching("restaurant", "kacchi*").contains(restaurantId))
    }

    @Test
    fun `a companion tag finds everywhere you went with them`() = runTest {
        assertTrue(searchDao.idsMatching("restaurant", "rafi*").contains(restaurantId))
    }

    @Test
    fun `a visit added later is reflected without a rebuild`() = runTest {
        repository.saveVisit(visit(restaurantId), listOf(dish("borhani", 4)), emptyList())

        assertTrue(searchDao.idsMatching("restaurant", "borhani*").contains(restaurantId))
    }
```

- [ ] **Step 2: Write `RestaurantIndexSource`**

`textFor` joins: the restaurant name, its note, its place's name, **every dish across every visit**, and every companion tag across every visit. Bind it `@IntoSet` beside `TransactionIndexSource`.

- [ ] **Step 3: Build the search screen**

One box off the hub. Results grouped `Transactions · Restaurants · Places`, recency within each group — FTS4 has no relevance ranking and none is invented (spine §7). An empty box shows nothing, not everything.

`SearchViewModel` calls `ftsQuery(raw)` and, when it is null, emits an empty state without touching the database.

- [ ] **Step 4: Run, then commit**

---

### Task 4: The visit editor

The screen that has to be quick. If this is slow to fill in, the module is abandoned (principle 1).

**Files:**
- Create: `feature/restaurants/VisitEditorScreen.kt`, `VisitEditorUiState.kt`, `VisitEditorViewModel.kt`
- Modify: `navigation/KhataNavHost.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Test: `app/src/test/java/com/wasif/khata/feature/restaurants/VisitEditorViewModelTest.kt`

- [ ] **Step 1: Add Coil**

`gradle/libs.versions.toml`: `coil = "3.0.4"` and `coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }`. `app/build.gradle.kts`: `implementation(libs.coil.compose)`.

It earns the dependency on downsampling, not on async loading: a 100dp thumbnail must not decode a 2048px bitmap, and hand-rolling that is a worse LRU cache than Coil's.

- [ ] **Step 2: Build the editor**

Fields in order: restaurant name (autocomplete, creates if new) · date · ambiance stars · cost · dish rows (name + stars) · companion chips · photos · notes.

**Autocomplete uses `RestaurantDao.namesLike(prefix)` ordered by most recently visited — not FTS.** Prefix-matching a short list wants predictable ordering; FTS would return fuzzier results in a field where the user is trying to hit one specific row.

Photos: `ActivityResultContracts.PickMultipleVisualMedia`, which on `minSdk 33` needs **no permission at all**. Each picked URI goes to `MediaStore.import(uri, "restaurant_visit", visitId)`.

Two controls need design attention rather than reuse (spec §7):

- **The five-star control** must be comfortably thumb-hittable and sit in the bottom third of the screen per the one-handed requirement — which rules out a row of small targets near the top of a form.
- **A photo grid over blurred glass.** Every existing surface sits over flat colour or a gradient; contrast over an arbitrary photograph cannot be assumed. Check it against a bright photo and a dark one.

- [ ] **Step 3: Test the view model**

```kotlin
    @Test
    fun `typing a new name creates the restaurant when the visit is saved`()
    @Test
    fun `picking an existing name attaches to it rather than creating a second`()
    @Test
    fun `a visit with no rating and no dishes still saves`()
```

- [ ] **Step 4: Run, then commit**

---

### Task 5: The list and the restaurant screen

**Files:**
- Create: `feature/restaurants/RestaurantsScreen.kt` + state + view model, `RestaurantScreen.kt` + state + view model
- Modify: `navigation/KhataNavHost.kt`, `feature/hub/ModulesScreen.kt:116`

- [ ] **Step 1: The list**

Two sections. **Been** — cover, name, dish average and ambiance, last visited, ordered by recency. **Want to try** — name, place, note, recommender. Primary action: **Log a visit**.

- [ ] **Step 2: The restaurant screen**

Cover, place (tapping opens Maps via the stored `mapsUrl`), both ratings, the list of visits. Setting the cover from any photo across any visit writes `coverMediaId` — a pointer, never a copy.

- [ ] **Step 3: Wake the hub tile**

`feature/hub/ModulesScreen.kt:116` currently renders `DormantRow(haze = haze, left = "Restaurants", right = "Watchlist")`. Restaurants becomes a live tile navigating to `KhataRoutes.Restaurants`; Watchlist stays dormant. Follow how the Wallet tile is built in the same file.

- [ ] **Step 4: Add the routes**

In `navigation/KhataNavHost.kt`, beside the existing `KhataRoutes` constants:

```kotlin
    const val Restaurants = "restaurants"
    const val Restaurant = "restaurants/{restaurantId}"
    const val VisitNew = "restaurants/visit/new"
    const val Search = "search"
    const val ArgRestaurantId = "restaurantId"

    fun restaurant(id: Long): String = "restaurants/$id"
```

- [ ] **Step 5: Run, then commit**

---

### Task 6: The Maps share target

The capture path `2026-08-26` §12 called primary, unbuildable until places had somewhere to land.

**Files:**
- Modify: `AndroidManifest.xml`
- Create: `feature/restaurants/AddToWishlistScreen.kt` + view model

- [ ] **Step 1: Register the intent filter**

On `MainActivity` in `AndroidManifest.xml`:

```xml
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
```

- [ ] **Step 2: Land on Add to wishlist**

`MainActivity` reads `Intent.EXTRA_TEXT`, passes it to `parseSharedPlace` (spine Task 4), and navigates to **Add to wishlist** prefilled with the name and link, with "log a visit instead" available.

A place someone sends you is usually somewhere you have not been yet — that is why the default is the wishlist rather than the visit editor.

- [ ] **Step 3: Verify on the device**

Share a place from Google Maps to Khata. It must land on Add to wishlist with the name filled in. This cannot be unit-tested; it is the walkthrough's step 1.

- [ ] **Step 4: Commit**

---

## Hardware walkthrough

Everything below needs the Pixel 6a. The photo path especially: Robolectric's `Bitmap` is a shadow that does not really decode, scale, or encode, so downscaling and EXIF rotation have never been checked until here.

1. **Share from Maps.** Share a restaurant from Google Maps → Khata lands on Add to wishlist, name prefilled, link stored. It appears under Want to try.
2. **Log a visit.** Type a name that does not exist, add two dishes with ratings, a companion, a cost. Save. The restaurant is created and moves to Been.
3. **Photos, and the one that has never been tested.** Import a photo taken in portrait on the phone's own camera. **It must not be rotated.** Check the file: `adb shell run-as com.wasif.khata ls -l files/media/` — one `<sha256>.jpg`, a few hundred KB, not four megabytes.
4. **Import the same photo twice.** One file, one `media` row, two `media_links` rows.
5. **Set a cover** from a visit photo. `SELECT coverMediaId FROM restaurants` points at the existing row and `files/media/` still holds one file.
6. **Search.** A dish name finds the restaurant. A companion name finds every restaurant visited with them. A Bengali restaurant name matches.
7. **Backup and restore.** Back up, then restore it. Restaurants, visits, dishes and photos all survive.
8. **Media reaches Drive.** The Khata folder holds `<sha256>.kbm` blobs. Delete `files/media/` on the device, restore, and confirm the photos come back down.

## Done when

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` passes.
- The walkthrough passes, especially 3 and 8.
- The hub's Restaurants tile is live and Watchlist is still dormant.
- A backup taken before this plan still restores after it.

## Deferred

A "want to go back" flag (R3), a dish catalogue (R4), cuisine types and price bands, sharing a restaurant out of the app, a map view, and linking a visit to a wallet transaction (R6). All recorded in restaurants §9.
