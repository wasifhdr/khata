# Khata (খাতা) — Design Spec: The Watchlist Module

**Date:** 2026-09-05
**Status:** Design decided. Implementation plan not yet written.
**Requires:** `2026-09-04-khata-shared-spine-design.md` (media, tags, search) and
`2026-09-04-khata-restaurants-design.md` — `StarRating`, Coil, and the global search screen ship
there. Builds on the restaurants branch, not on `main`.
**Paired with:** `2026-09-05-khata-vehicle-design.md`. Designed in the same session, shipped
separately. This module ships second, on migration **12 → 13**.

---

## 1. What this is

The fourth module, and the hub tile currently reading "Watchlist". Three things, confirmed with
the user:

1. **A queue** — what to watch next, and who recommended it.
2. **A log** — what was watched and when, so "have I seen this" has an answer.
3. **A verdict** — a rating and a note, so a recommendation to someone else has something behind
   it.

It is the first module to reach the network for its data. It is also the first whose subject
already exists in a public database, which is why it does.

---

## 2. Decisions taken

Settled during design. Not open for re-litigation during implementation.

| # | Decision | Rationale |
|---|---|---|
| W1 | Two levels: a title, with watches under it | A rewatch four years later is a new watch, not an edit — the same reasoning as R1 |
| W2 | **The queue is derived** — titles with no watches | R3 exactly. Adding a title queues it, logging a watch promotes it, nothing to flip and nothing to drift |
| W3 | `kind` is `film` or `series`, and there is no season or episode model | The user declined progress tracking. Without it a series is a title watched on some dates, and the one piece of this module with no existing analogue disappears |
| W4 | The rating lives on the **watch**; the title's verdict is the average across its watches | R5. A rewatch you enjoyed less is a fact worth keeping, and a derived verdict cannot contradict the watches it summarises |
| W5 | `tmdbRating` is a snapshot stored beside `tmdbRatingAt`, the moment it was taken | A number from somewhere else, dated, so it never poses as the user's own or as current |
| W6 | Opening a title refetches its rating **only when online and the snapshot is over 7 days old** | The user asked for refresh-on-open. Ungated, that is a network call every time they glance at a title; the gate makes it a call they will rarely notice |
| W7 | The poster is a `posterMediaId` pointer into the spine's media store | R7's pattern. Content-addressed, so the same poster fetched twice is one file and one row |
| W8 | "Where you heard about it" is a spine tag, not a column | Identical to the restaurant wishlist's recommender. `tag_links(entityType = 'title')` |
| W9 | No photos beyond the poster | There is nothing to photograph. The gallery picker is not wired into this module |
| W10 | TMDB is the fast path, never the only path. Manual fields are always on screen | Principle 4. No key, no network, and nothing-found all land in the same place, and a film TMDB has never heard of is still recordable |

---

## 3. Data model

Migration **12 → 13**, hand-written SQL beside the existing ones, with a `Migration12To13Test`.
`BackupRepository.SCHEMA_VERSION` goes to 13 with it, for the reason the vehicle spec §3 gives.

House quartet on every table. Deletes are soft.

```sql
titles  (id, uuid, name, year?, kind, tmdbId?, tmdbRating?, tmdbRatingAt?,
         posterMediaId?, note, …)
watches (id, uuid, titleId, watchedAt, rating?, note, …)
```

Indices beyond `uuid`: `UNIQUE titles(tmdbId)` where not null, `titles(name)`, `watches(titleId)`,
`watches(watchedAt)`.

| Concept | Where it lives |
|---|---|
| Who recommended it | `tag_links(entityType = 'title')` |
| The poster image | `media.id`, via `titles.posterMediaId` |

**`kind`** is a stored `TEXT` of `film` or `series`, mapped to an enum in Kotlin the way
`RuleKind` and `TransactionDirection` already are.

**`year`** is a nullable `Int`. TMDB supplies it; a manually added title may not have it.

**`tmdbId`** is nullable and unique when present, so a title added by hand and later matched to
TMDB cannot become a second row, and the same TMDB entry cannot be added twice.

**`tmdbRating`** is a nullable `REAL` on TMDB's own 0–10 scale, stored as given and displayed as
given. It is never averaged with, compared to, or converted into the user's 1–5 stars — two
scales from two sources, shown side by side and never blended.

**`watches.rating`** is a nullable `Int` 1–5, the same shape and the same `StarRating` control the
restaurant module uses. A watch you did not rate is still a watch.

**`watchedAt`** is UTC epoch millis, displayed and bucketed in `Asia/Dhaka`.

### The derived pieces

**The queue** — titles with no non-deleted watches. Not stored (W2).

**The verdict** — `AVG(watches.rating)` across the title's non-deleted watches, ignoring unrated
ones rather than counting them as zero. Not stored.

The consequence, accepted knowingly and identically to R3: a film you have already seen can never
appear in the queue, so "want to rewatch this" has no flag. A good rating and a note are what
already say so.

---

## 4. The TMDB client

`core/watch/TmdbClient.kt`, following `GeminiClient` and `DriveWire` exactly: one
`HttpURLConnection`, `org.json` for parsing, `Dispatchers.IO`, no new dependency and no second
HTTP approach in the app.

**The key** lives in DataStore beside `geminiKey` — `PreferencesRepository.setTmdbKey` — and is
entered on the existing Settings screen next to the Gemini key. It is never compiled in.

**Two calls, and no more.** Search (`/3/search/multi`, filtered to `movie` and `tv` results) and
the poster image from `image.tmdb.org`. Nothing else on the API is used, because nothing else is
stored: no overview, no cast, no genres, no runtime.

**Parsing is a pure function** — `parseSearchResults(json): List<TmdbResult>` — tested against a
fixture file at `docs/superpowers/specs/tmdb-responses.md`, the way `sms-corpus.md` is tested by
`CorpusTest`. The fixtures must include a result with no poster, one with no release date, and a
`person` result that has to be filtered out, because all three occur in real responses.

**Failure is silent and total.** No key, no network, a non-200, or malformed JSON all yield an
empty result list. The manual fields are already on screen beneath the search box, so there is
nothing to recover from and nothing to explain — the user simply types, exactly as they would
have with no key at all.

**The poster** is downloaded on pick, downscaled and stored by the spine's existing media path
(§4 of the spine spec: 2048px / q85, content-addressed, write-once). It rides to Drive with
every other photo. That is mildly wasteful for an image that could be refetched, and it is not
worth a second backup path to avoid.

**The refresh** (W6) happens in the title screen's view model on open: if `tmdbId` is present,
`tmdbRatingAt` is over seven days old, and a search call succeeds, `tmdbRating` and
`tmdbRatingAt` are updated. Anything else leaves the stored snapshot alone. The poster is never
refetched — a poster does not change, and the stored one is already correct.

---

## 5. Screens

Package `feature/watchlist`, package-per-feature (D2).

| Screen | Content |
|---|---|
| **Watchlist** (hub tile) | **Up next** — poster, name, year, kind. **Watched** — the same, plus your verdict and when you last watched it. Primary action in the bottom third: **Add a title** |
| **Add a title** | Search box → results with poster, title, year, kind, TMDB rating · manual fields beneath, always present · recommender tags · note · optionally log a watch immediately |
| **Title** | Poster, year, kind, TMDB rating with the date it was taken, the user's verdict, the list of watches, note. **Log a watch** lives here |
| **Log a watch** | Date, `StarRating`, note. A bottom sheet, not a screen — three fields do not earn a destination |

Posters render through Coil, which the restaurant module adds for its own photos.

---

## 6. Search

One `IndexSource` with `entityType = "title"`. Indexed text is the title's **name, its note, and
its recommender tags** — so "Rafi" finds everything he told you to watch, through exactly the tag
folding the spine already built.

Not indexed: the TMDB rating, the year, or the kind. Numbers and enums are filters, not text, and
none of them is what a search box is for.

The global search screen gains a fifth group. Kind then recency, as everywhere.

---

## 7. Design system

Inherited: `KhataTheme`, `core/ui/component`, `StarRating`, Coil, Haze.

One thing needs design attention rather than reuse: **a poster is a tall image with its own
colours**. The restaurant module has already solved photographs behind glass; this is the same
problem at a different aspect ratio, and the answer there is the answer here. If a title has no
poster, the card shows the name's initials in the poster's 2:3 shape — an absent poster is normal,
not a broken image, and a placeholder graphic would be inventing an asset the product does not
have.

**Two decisions taken while building, recorded here rather than left as drift:**

- **Rows with poster thumbnails, not a poster grid.** Every other list in the app is a row, and a
  grid would show fewer titles per screen while carrying less of each one. The poster is the
  thumbnail at the row's leading edge.
- **The recommender appears on the title screen, not in the list row.** Recommenders are spine
  tags rather than summary columns, so putting them in a row would mean one tag query per visible
  title — an N+1 against a list, to repeat what the title screen already says.

Bengali script must render in titles and notes, as everywhere else.

---

## 8. Testing

- **`Migration12To13Test`** — tables and indices, including the partial unique index on `tmdbId`.
- **Queue derivation** — a title with no watches appears; logging a watch promotes it;
  soft-deleting its only watch returns it.
- **Verdict** — averages only rated watches; a title whose watches are all unrated has no verdict
  rather than a zero.
- **TMDB parsing** — against the fixture corpus: a normal result, one with no poster, one with no
  release date, and a `person` result that must be filtered out.
- **Failure paths** — no key, non-200, and malformed JSON each yield an empty list and no throw.
- **The refresh gate** — a snapshot under seven days old triggers no call; an older one does; a
  failed call leaves the stored rating and its date untouched.
- **Poster dedup** — the same poster fetched for two titles yields one file and one `media` row.
- **Search** — a recommender tag finds every title it is attached to.
- **Screens** — add-a-title and the two lists, following `VisitEditorScreenTest`.

---

## 9. Out of scope

- **Season and episode progress.** Declined by the user (W3).
- **Streaming availability, where-to-watch, and providers.** A live data source per title, and a
  regional one at that.
- **Cast, crew, genres, runtime, and overviews.** Not stored, so not fetched (§4).
- **Trailers and any embedded video.**
- **A "want to rewatch" flag.** See §3, and R3 before it.
- **Sharing a title out of the app**, and importing a list from anywhere.
- **Refreshing posters, or any background sync.** The refresh is on-open and rate-limited (W6).
