# Watchlist Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A queue of what to watch, a log of what was watched, and a verdict on it — with TMDB
as a fast path for titles, posters, and public ratings, and typing as the path that always works.

**Architecture:** Two tables (`titles`, `watches`) on migration 12 → 13, a `WatchlistRepository`
whose queue and verdict are derived exactly as the restaurant wishlist is, a `TmdbClient`
following `GeminiClient`'s HTTP shape with a pure parser tested against a fixture corpus, and a
`feature/watchlist` package of three screens and one sheet. Posters land in the spine's media
store; recommenders are spine tags.

**Tech Stack:** Kotlin · Room · Hilt · Compose · Coil · Robolectric. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-05-khata-watchlist-design.md`

## Global Constraints

- Every table carries the house quartet: `uuid` (unique index), `createdAt`, `updatedAt`,
  `deletedAt` nullable. Deletes are soft.
- `watchedAt` is UTC epoch millis; all bucketing and display is `Asia/Dhaka`.
- The network is an enhancement, never a dependency (principle 4). No key, no connection,
  a non-200, or malformed JSON all degrade to the manual fields with no error surface.
- The TMDB key lives in DataStore beside `geminiKey` and is entered in Settings. Never compiled in.
- `tmdbRating` is TMDB's own 0–10 scale, stored and shown as given. It is never averaged with,
  compared to, or converted into the user's 1–5 stars.
- English interface. Bengali script must render in titles and notes.
- `BackupRepository.SCHEMA_VERSION` must equal the Room `version` at all times.
- Migration SQL is copied **verbatim** from `app/schemas/com.wasif.khata.core.data.KhataDatabase/13.json`
  after a build, never hand-written.
- **Depends on the car servicing plan having landed** — it takes migration 11 → 12 and this one
  takes 12 → 13.

---

### Task 1: Schema — titles, watches, and the migration

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/TitleEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/WatchEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/dao/WatchlistDao.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/KhataDatabase.kt` (entities, `version = 13`, `watchlistDao()`)
- Modify: `app/src/main/java/com/wasif/khata/core/data/migration/Migrations.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/data/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/backup/BackupRepository.kt` (`SCHEMA_VERSION = 13`)
- Test: `app/src/test/java/com/wasif/khata/core/data/migration/Migration12To13Test.kt`

**Interfaces:**
- Produces: `TitleKind` enum (`FILM`, `SERIES`), `TitleEntity`, `WatchEntity`, `TitleSummary`,
  and `WatchlistDao` with `upsert(TitleEntity): Long`, `upsertWatch(WatchEntity): Long`,
  `findTitle(id: Long): TitleEntity?`, `findByTmdbId(tmdbId: Int): TitleEntity?`,
  `findByName(name: String): TitleEntity?`, `observeQueue(): Flow<List<TitleSummary>>`,
  `observeWatched(): Flow<List<TitleSummary>>`, `observeSummary(id: Long): Flow<TitleSummary?>`,
  `summariesByIds(ids: List<Long>): List<TitleSummary>`,
  `observeWatches(titleId: Long): Flow<List<WatchEntity>>`, `allIdsForIndex(): List<Long>`,
  `softDeleteWatch(id: Long, now: Long)`.

- [ ] **Step 1: Write the entities**

```kotlin
package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TitleKind { FILM, SERIES }

/**
 * A film or a series. There is no season or episode model: the user declined progress
 * tracking (W3), which makes a series a title watched on some dates.
 */
@Entity(
    tableName = "titles",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["tmdbId"], unique = true),
        Index(value = ["name"]),
    ],
)
data class TitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val year: Int? = null,
    val kind: TitleKind,
    /** Unique when present, so a hand-typed title later matched to TMDB cannot become
     *  a second row, and the same TMDB entry cannot be added twice. */
    val tmdbId: Int? = null,
    /** TMDB's own 0-10 scale, stored as given. Never blended with the user's stars. */
    val tmdbRating: Double? = null,
    /** When that number was taken. A dated number from elsewhere, never posing as current. */
    val tmdbRatingAt: Long? = null,
    /** A pointer into media, never a second copy of the file. */
    val posterMediaId: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

```kotlin
@Entity(
    tableName = "watches",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["titleId"]),
        Index(value = ["watchedAt"]),
    ],
)
data class WatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val titleId: Long,
    val watchedAt: Long,
    /** 1-5, nullable. A watch you did not rate is still a watch. */
    val rating: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

Room needs a converter for `TitleKind`. Add it to the existing converters file beside the
`TransactionDirection` and `RuleKind` ones, in the same style.

- [ ] **Step 2: Write `WatchlistDao`**

```kotlin
data class TitleSummary(
    val id: Long,
    val name: String,
    val year: Int?,
    val kind: TitleKind,
    val tmdbId: Int?,
    val tmdbRating: Double?,
    val tmdbRatingAt: Long?,
    val posterMediaId: Long?,
    /** The poster's content hash, which is its file name. Joined so a grid of twenty
     *  titles is one query rather than twenty-one. */
    val posterSha: String?,
    val note: String?,
    /** Null on the queue, which is what "not watched" means. */
    val lastWatchedAt: Long?,
    val watchCount: Int,
    /** AVG over rated watches only; null when none of them carry a rating. */
    val verdict: Double?,
)

private const val SUMMARY_COLUMNS = """
    SELECT t.id, t.name, t.year, t.kind, t.tmdbId, t.tmdbRating, t.tmdbRatingAt,
      t.posterMediaId,
      (SELECT m.sha256 FROM media m WHERE m.id = t.posterMediaId AND m.deletedAt IS NULL)
        AS posterSha,
      t.note,
      (SELECT MAX(w.watchedAt) FROM watches w
       WHERE w.titleId = t.id AND w.deletedAt IS NULL) AS lastWatchedAt,
      (SELECT COUNT(*) FROM watches w
       WHERE w.titleId = t.id AND w.deletedAt IS NULL) AS watchCount,
      (SELECT AVG(w.rating) FROM watches w
       WHERE w.titleId = t.id AND w.deletedAt IS NULL AND w.rating IS NOT NULL) AS verdict
    FROM titles t
"""
```

Queries: `observeQueue()` is `WHERE t.deletedAt IS NULL AND NOT EXISTS (live watch)` ordered by
`t.createdAt DESC`; `observeWatched()` is the same with the `EXISTS` un-negated, ordered by
`lastWatchedAt DESC`. Plus `findTitle`, `findByTmdbId`, `findByName` (`COLLATE NOCASE`),
`observeSummary`, `summariesByIds`, `observeWatches`, `allIdsForIndex`, `softDeleteWatch`,
and `updateTmdbRating(id, rating, at)`.

- [ ] **Step 3: Register, build, copy the schema, write the migration**

Add both entities and `watchlistDao()` to `KhataDatabase`, set `version = 13`.

Run: `./gradlew :app:kspDebugKotlin`
Expected: `app/schemas/.../13.json` written.

Add `MIGRATION_12_13` with statements copied verbatim from `13.json`, register it in
`DatabaseModule`, and set `BackupRepository.SCHEMA_VERSION = 13`.

- [ ] **Step 4: Write `Migration12To13Test`**

Copy `Migration11To12Test`, changing the version numbers and asserting that a title and a
watch insert, and that a second title with the same `tmdbId` fails:

```kotlin
@Test
fun `tmdbId is unique so the same entry cannot be added twice`() = runTest {
    dao.upsert(TitleEntity(uuid = "t1", name = "Heat", kind = TitleKind.FILM, tmdbId = 949, createdAt = 1, updatedAt = 1))
    assertThrows(SQLiteConstraintException::class.java) {
        runBlocking {
            dao.upsert(TitleEntity(uuid = "t2", name = "Heat again", kind = TitleKind.FILM, tmdbId = 949, createdAt = 1, updatedAt = 1))
        }
    }
}
```

- [ ] **Step 5: Run, then commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*Migration*'`
Expected: PASS.

```bash
git add app/src/main app/src/test app/schemas
git commit -m "feat(watchlist): two tables, and a queue with nothing to flip"
```

---

### Task 2: `WatchlistRepository` — the queue and the verdict

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/repository/WatchlistRepository.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/repository/WatchlistRepositoryTest.kt`

**Interfaces:**
- Produces: `ENTITY_TITLE = "title"`, `TitleDraft`, `WatchDraft`,
  `WatchlistRepository.saveTitle(TitleDraft): Long`, `logWatch(WatchDraft): Long`,
  `deleteWatch(id: Long)`, `observeQueue()`, `observeWatched()`, `observeSummary(id)`,
  `observeWatches(titleId)`, `find(id)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `a title with no watches is on the queue, and logging one promotes it`() = runTest {
    val id = repository.saveTitle(TitleDraft(name = "Heat", kind = TitleKind.FILM))
    assertEquals(listOf("Heat"), repository.observeQueue().first().map { it.name })
    assertTrue(repository.observeWatched().first().isEmpty())

    val watchId = repository.logWatch(WatchDraft(titleId = id, watchedAt = 1L, rating = 5))
    assertTrue(repository.observeQueue().first().isEmpty())
    assertEquals(listOf("Heat"), repository.observeWatched().first().map { it.name })

    repository.deleteWatch(watchId)
    assertEquals(listOf("Heat"), repository.observeQueue().first().map { it.name })
}

@Test
fun `the verdict averages rated watches and ignores unrated ones`() = runTest {
    val id = repository.saveTitle(TitleDraft(name = "Heat", kind = TitleKind.FILM))
    repository.logWatch(WatchDraft(titleId = id, watchedAt = 1L, rating = 5))
    repository.logWatch(WatchDraft(titleId = id, watchedAt = 2L, rating = null))
    repository.logWatch(WatchDraft(titleId = id, watchedAt = 3L, rating = 3))
    assertEquals(4.0, repository.observeSummary(id).first()!!.verdict!!, 0.001)
}

@Test
fun `a title whose watches are all unrated has no verdict rather than a zero`() = runTest {
    val id = repository.saveTitle(TitleDraft(name = "Heat", kind = TitleKind.FILM))
    repository.logWatch(WatchDraft(titleId = id, watchedAt = 1L, rating = null))
    assertNull(repository.observeSummary(id).first()!!.verdict)
}

@Test
fun `saving a title that already carries the tmdb id returns the existing row`() = runTest {
    val first = repository.saveTitle(TitleDraft(name = "Heat", kind = TitleKind.FILM, tmdbId = 949))
    val second = repository.saveTitle(TitleDraft(name = "Heat", kind = TitleKind.FILM, tmdbId = 949))
    assertEquals(first, second)
}
```

- [ ] **Step 2: Run, fail, implement, pass**

```kotlin
const val ENTITY_TITLE = "title"

data class TitleDraft(
    val id: Long = 0,
    val name: String,
    val year: Int? = null,
    val kind: TitleKind,
    val tmdbId: Int? = null,
    val tmdbRating: Double? = null,
    val tmdbRatingAt: Long? = null,
    val posterMediaId: Long? = null,
    val note: String? = null,
)

data class WatchDraft(
    val id: Long = 0,
    val titleId: Long,
    val watchedAt: Long,
    val rating: Int? = null,
    val note: String? = null,
)
```

`saveTitle` resolves an existing row by `tmdbId` first and by NOCASE name second, so the same
film added twice is one row — the `findOrCreate` shape the tag and restaurant repositories
already use. Both `saveTitle` and `logWatch` call `searchIndex.reindex(ENTITY_TITLE, titleId)`;
`logWatch` reindexes the **title**, not the watch, for the reason `RestaurantIndexSource`
documents.

Run: `./gradlew :app:testDebugUnitTest --tests '*WatchlistRepositoryTest*'`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat(watchlist): the repository, and a verdict that cannot contradict its watches"
```

---

### Task 3: The TMDB client, its corpus, and the key in Settings

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/watch/TmdbClient.kt`
- Create: `docs/superpowers/specs/tmdb-responses.md`
- Modify: `app/src/main/java/com/wasif/khata/core/prefs/PreferencesRepository.kt` (`setTmdbKey`)
- Modify: `app/src/main/java/com/wasif/khata/core/prefs/PreferencesRepositoryImpl.kt` (`TmdbKey`, `tmdbKey` on `KhataPreferences`)
- Modify: `app/src/main/java/com/wasif/khata/feature/settings/SettingsScreen.kt` + `SettingsViewModel.kt`
- Test: `app/src/test/java/com/wasif/khata/core/watch/TmdbParseTest.kt`

**Interfaces:**
- Produces: `data class TmdbResult(tmdbId, name, year, kind, rating, posterPath)`,
  `fun parseSearchResults(json: String): List<TmdbResult>`,
  `fun posterUrl(posterPath: String): String`,
  `TmdbClient.search(query: String): List<TmdbResult>`,
  `TmdbClient.rating(tmdbId: Int, kind: TitleKind): Double?`.

- [ ] **Step 1: Write the fixture corpus**

`docs/superpowers/specs/tmdb-responses.md` holds real-shaped `/3/search/multi` JSON, one
fenced block per case, in the style of `sms-corpus.md`: a film result, a series result (whose
title field is `name` and date field is `first_air_date`, not `title` / `release_date`), a
result with `poster_path: null`, a result with an empty release date, and a `person` result
that must be filtered out.

- [ ] **Step 2: Write the failing parser tests**

```kotlin
@Test
fun `a film result parses title, year, kind and rating`() {
    val results = parseSearchResults(corpus("film"))
    val heat = results.single()
    assertEquals(949, heat.tmdbId)
    assertEquals("Heat", heat.name)
    assertEquals(1995, heat.year)
    assertEquals(TitleKind.FILM, heat.kind)
    assertEquals(7.9, heat.rating!!, 0.001)
}

@Test
fun `a series result reads name and first_air_date rather than title and release_date`() {
    val show = parseSearchResults(corpus("series")).single()
    assertEquals("The Wire", show.name)
    assertEquals(2002, show.year)
    assertEquals(TitleKind.SERIES, show.kind)
}

@Test
fun `a person result is dropped`() {
    assertTrue(parseSearchResults(corpus("person")).isEmpty())
}

@Test
fun `a missing poster and a blank date are nulls, not failures`() {
    val row = parseSearchResults(corpus("no-poster")).single()
    assertNull(row.posterPath)
    assertNull(row.year)
}

@Test
fun `malformed json is an empty list rather than a throw`() {
    assertEquals(emptyList<TmdbResult>(), parseSearchResults("{"))
    assertEquals(emptyList<TmdbResult>(), parseSearchResults(""))
}
```

- [ ] **Step 3: Write the client**

Pure parser plus a thin `HttpURLConnection` call, following `GeminiClient` exactly:

```kotlin
private const val SEARCH_ENDPOINT = "https://api.themoviedb.org/3/search/multi"
private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

fun posterUrl(posterPath: String): String = IMAGE_BASE + posterPath

/**
 * Empty on anything that is not a usable list: no key, no network, a non-200, or
 * malformed JSON. The manual fields are already on screen beneath the search box, so
 * there is nothing to recover from and nothing to explain (W10).
 */
fun parseSearchResults(json: String): List<TmdbResult> = runCatching {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
    (0 until results.length()).mapNotNull { i ->
        val row = results.optJSONObject(i) ?: return@mapNotNull null
        val kind = when (row.optString("media_type")) {
            "movie" -> TitleKind.FILM
            "tv" -> TitleKind.SERIES
            else -> return@mapNotNull null
        }
        val name = (if (kind == TitleKind.FILM) row.optString("title") else row.optString("name"))
            .ifBlank { return@mapNotNull null }
        val date = if (kind == TitleKind.FILM) row.optString("release_date") else row.optString("first_air_date")
        TmdbResult(
            tmdbId = row.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null,
            name = name,
            year = date.take(4).toIntOrNull(),
            kind = kind,
            rating = row.optDouble("vote_average").takeIf { !it.isNaN() && it > 0.0 },
            posterPath = row.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}.getOrDefault(emptyList())
```

- [ ] **Step 4: Add the key to preferences and Settings**

`setTmdbKey(key: String?)` on the interface, `stringPreferencesKey("tmdb_key")` in the impl and
`tmdbKey` on `KhataPreferences`, both mirroring `geminiKey` line for line. In `SettingsScreen`,
add the field directly beneath the Gemini key field, with the same masking and the same
save-on-change behaviour.

- [ ] **Step 5: Run, then commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*Tmdb*' --tests '*Settings*'`
Expected: PASS.

```bash
git add app/src/main app/src/test docs
git commit -m "feat(watchlist): asking TMDB, and working fine without it"
```

---

### Task 4: Adding a title, with the poster

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/watchlist/AddTitleScreen.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/watchlist/AddTitleUiState.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/watchlist/AddTitleViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/media/MediaStore.kt` (add `importFromUrl`)
- Test: `app/src/test/java/com/wasif/khata/feature/watchlist/AddTitleViewModelTest.kt`
- Test: `app/src/test/java/com/wasif/khata/core/media/MediaStoreUrlTest.kt`

**Interfaces:**
- Produces: `MediaStore.importFromUrl(url: String, entityType: String, entityId: Long): MediaEntity?`,
  `AddTitleUiState(query, results, searching, name, year, kind, note, recommenders, saving)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `picking a result fills the manual fields rather than replacing them`() = runTest {
    viewModel.onResultPick(TmdbResult(949, "Heat", 1995, TitleKind.FILM, 7.9, "/p.jpg"))
    assertEquals("Heat", viewModel.state.value.name)
    assertEquals(1995, viewModel.state.value.year)
    assertEquals(TitleKind.FILM, viewModel.state.value.kind)
}

@Test
fun `with no key the search box yields nothing and typing still saves`() = runTest {
    preferences.setTmdbKey(null)
    viewModel.onQueryChange("heat")
    advanceUntilIdle()
    assertEquals(emptyList<TmdbResult>(), viewModel.state.value.results)

    viewModel.onNameChange("Heat")
    viewModel.onSave()
    advanceUntilIdle()
    assertEquals(listOf("Heat"), dao.observeQueue().first().map { it.name })
}

@Test
fun `the same poster fetched for two titles is one file and one row`() = runTest {
    val a = mediaStore.importFromUrl(url, ENTITY_TITLE, 1L)
    val b = mediaStore.importFromUrl(url, ENTITY_TITLE, 2L)
    assertEquals(a!!.id, b!!.id)
    assertEquals(1, mediaStore.dir().listFiles()!!.size)
}
```

- [ ] **Step 2: Add `importFromUrl` to `MediaStore`**

It is the existing `import(uri, …)` body with the bytes read from an `HttpURLConnection`
instead of the `ContentResolver` — the hash, the write-once file, the row, and the link are the
same code path, so posters and gallery photos cannot diverge. Failure returns null.

- [ ] **Step 3: Build the screen**

Search box at the top; results as rows with poster, name, year, kind, and TMDB rating; the
manual fields — name, year, film/series — always visible beneath them; recommender tag chips
and a note; a "log a watch now" toggle. **Save** in the bottom third.

- [ ] **Step 4: Run, then commit**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

```bash
git add app/src/main app/src/test
git commit -m "feat(watchlist): adding a title, from TMDB or from the keyboard"
```

---

### Task 5: The lists, the title screen, the refresh gate, search, and the hub tile

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/watchlist/WatchlistScreen.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/watchlist/WatchlistUiState.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/watchlist/WatchlistViewModel.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/watchlist/TitleScreen.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/watchlist/TitleViewModel.kt`
- Create: `app/src/main/java/com/wasif/khata/core/search/TitleIndexSource.kt`
- Modify: `RepositoryModule.kt`, `SearchUiState.kt`, `SearchViewModel.kt`, `SearchScreen.kt`
- Modify: `KhataNavHost.kt`, `ModulesScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/watchlist/TitleViewModelTest.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/watchlist/WatchlistScreenTest.kt`

**Interfaces:**
- Produces: routes `Watchlist = "watchlist"`, `Title = "watchlist/{titleId}"`,
  `AddTitle = "watchlist/add"`; `SearchUiState.titles: List<TitleSummary>`.

- [ ] **Step 1: Write the failing refresh-gate tests**

```kotlin
@Test
fun `a snapshot under seven days old triggers no call`() = runTest {
    val id = repository.saveTitle(
        TitleDraft(name = "Heat", kind = TitleKind.FILM, tmdbId = 949, tmdbRating = 7.9, tmdbRatingAt = clock.now()),
    )
    titleViewModelFor(id); advanceUntilIdle()
    assertEquals(0, tmdb.calls)
}

@Test
fun `an older snapshot refreshes`() = runTest {
    val eightDaysAgo = clock.now() - 8 * 24 * 60 * 60 * 1000L
    val id = repository.saveTitle(
        TitleDraft(name = "Heat", kind = TitleKind.FILM, tmdbId = 949, tmdbRating = 7.9, tmdbRatingAt = eightDaysAgo),
    )
    tmdb.nextRating = 8.1
    titleViewModelFor(id); advanceUntilIdle()
    assertEquals(8.1, dao.findTitle(id)!!.tmdbRating!!, 0.001)
}

@Test
fun `a failed refresh leaves the stored rating and its date untouched`() = runTest {
    val eightDaysAgo = clock.now() - 8 * 24 * 60 * 60 * 1000L
    val id = repository.saveTitle(
        TitleDraft(name = "Heat", kind = TitleKind.FILM, tmdbId = 949, tmdbRating = 7.9, tmdbRatingAt = eightDaysAgo),
    )
    tmdb.nextRating = null      // offline, no key, or a non-200
    titleViewModelFor(id); advanceUntilIdle()
    assertEquals(7.9, dao.findTitle(id)!!.tmdbRating!!, 0.001)
    assertEquals(eightDaysAgo, dao.findTitle(id)!!.tmdbRatingAt)
}

@Test
fun `a title with no tmdbId never calls out`() = runTest {
    val id = repository.saveTitle(TitleDraft(name = "A home video", kind = TitleKind.FILM))
    titleViewModelFor(id); advanceUntilIdle()
    assertEquals(0, tmdb.calls)
}
```

The gate itself is one testable function:

```kotlin
private const val REFRESH_AFTER_MS = 7L * 24 * 60 * 60 * 1000

/** Ungated, refresh-on-open is a network call every time the user glances at a title. */
fun needsRefresh(tmdbId: Int?, ratingAt: Long?, now: Long): Boolean =
    tmdbId != null && (ratingAt == null || now - ratingAt > REFRESH_AFTER_MS)
```

- [ ] **Step 2: Build `WatchlistScreen`**

**Up next** — poster grid with name, year, recommender. **Watched** — poster, name, verdict,
last watched, newest first. A title with no poster shows its name set large; an absent poster is
normal, and a placeholder graphic would be inventing an asset the product does not have.
**Add a title** sits in the bottom third.

- [ ] **Step 3: Build `TitleScreen` and the log-a-watch sheet**

Poster, year, kind, TMDB rating with its date, the user's verdict, the list of watches, note.
The sheet is date + `StarRating` + note — three fields do not earn a destination.

- [ ] **Step 4: Write `TitleIndexSource` and add the fifth search group**

Indexed text is the name, the note, and the recommender tags. Bind `@IntoSet`, add
`titles: List<TitleSummary>` to `SearchUiState`, resolve ids through
`watchlistDao.summariesByIds`, and render the group.

- [ ] **Step 5: Wire navigation and wake the hub tile**

Three routes, three `composable` blocks, and the `Watchlist` dormant tile becomes live.
`Notes` remains the only dormant tile, so `DormantRow` is replaced by a single tile — do not
leave a two-column row with one empty cell.

- [ ] **Step 6: Run everything, then commit**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

```bash
git add app/src/main app/src/test
git commit -m "feat(watchlist): two lists, one title, and a hub tile that opens"
```

---

## Self-Review

**Spec coverage:** §3 tables and derived pieces → Tasks 1–2. §4 TMDB client, key, failure paths,
poster, refresh → Tasks 3–5. §5 screens → Tasks 4–5. §6 search → Task 5. §7 design system →
Tasks 4–5 (reuse plus the no-poster rule). §8 testing → every task's test step. §9 out of scope →
nothing here builds episodes, providers, cast, trailers, a rewatch flag, sharing, or background
sync.

**Types:** `TitleKind`, `TitleEntity`, `WatchEntity`, `TitleSummary`, `TitleDraft`, `WatchDraft`,
`ENTITY_TITLE`, `TmdbResult`, `parseSearchResults`, `posterUrl`, `needsRefresh`,
`MediaStore.importFromUrl` are defined in Tasks 1–4 and used under those exact names afterwards.
