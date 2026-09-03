# Khata (খাতা) — Design Spec: The Shared Spine

**Date:** 2026-09-04
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-08-26-khata-wallet-design.md` §6 (satellites), §12 (places, media, search),
`2026-09-03-khata-backup-design.md` (the format this changes).
**Paired with:** `2026-09-04-khata-restaurants-design.md` — the first consumer, built in the
same run. Neither spec ships alone.

---

## 1. What this is, and why now

`PRODUCT.md` principle 5 promises "one spine, many modules": shared places, media, tags, and
search, so later modules are UI work rather than new applications. Nothing of it exists. The
database is at version 7 with nine entities and none of them are satellites.
`merchants.placeId` has been a column pointing at a table that was never created since the
foundation commit.

The reason to build it now is that the second module has been named: **restaurants**. Until
this design, the spine's requirements were guesses about an unchosen module. They are no
longer. Every table here exists because a restaurant record concretely needs it:

| Spine piece | What restaurants needs it for |
|---|---|
| `places` | A location tag or a Google Maps link on a restaurant |
| `media` | Photos from the visit, imported from the gallery |
| `tags` | Who you went with — and who recommended a place you haven't been |
| `search_fts` | Finding a restaurant by name, dish, or companion |

**The wallet needs none of it.** There is no receipt photo on a transaction and no place on a
merchant. That was considered and declined by the user: the wallet's job is money, and
attaching a gallery picker to it would be building a feature to justify a table.

The consequence is that this spec is almost entirely data and logic. The only user-visible
changes it makes on its own are that ledger search gets better (§7) and Settings gains
export/import (§8). The screens that make places, media, and tags touchable live in the
restaurant spec, deliberately.

---

## 2. Decisions taken

Settled during design. Not open for re-litigation during implementation.

| # | Decision | Rationale |
|---|---|---|
| S1 | The spine is built against restaurants, not a hypothetical module | The requirements are real and checkable. The alternative was designing interfaces for an imagined caller |
| S2 | The search **index** ships here; the search **screen** ships with restaurants | A grouped-results screen showing exactly one group is UI built for an empty room |
| S3 | The index is maintained by Kotlin (`SearchIndex`), not SQLite triggers | Testable in the existing Robolectric suite. Triggers are invisible to Room's schema validation, still need hand-written SQL per module, and make soft deletes fiddly |
| S4 | `unicode61` tokenizer, never `simple` | `simple` is ASCII-only. Bengali merchant names and notes would silently never match, and Bengali rendering is a hard product requirement |
| S5 | Media is content-addressed (`<sha256>.jpg`) and write-once | Deduplication is free, an absolute path cannot survive a restore onto another phone, and immutability is what makes §8 hold |
| S6 | Media rows are tombstoned; media **files** are never deleted | Principle 3, and it makes the live store a guaranteed superset of what any retained backup references |
| S7 | Media backup is in scope, via a full **export**, not by fattening the nightly backup | `KEEP = 7` rotation × a whole photo library is gigabytes to protect bytes that never change |
| S8 | Photos are downscaled to 2048px / q85, as `2026-08-26` D10 specified | ~400 KB vs ~4 MB, indistinguishable on a phone, and the difference between a shareable export and an impossible one |
| S9 | Three specced satellite columns are dropped (§3) | Nothing can write them. Each is a one-line migration if that changes |

---

## 3. Data model

Migration **7 → 8**, hand-written SQL in `core/data/migration/Migrations.kt` matching the four
migrations already there, with a `Migration7To8Test` alongside the existing ones.

Every table carries the house quartet: `uuid TEXT` with a unique index, `createdAt`,
`updatedAt`, `deletedAt` nullable. Deletes are soft.

```sql
places      (id, uuid, name, address, lat REAL, lng REAL, mapsUrl, note, …)
media       (id, uuid, sha256 UNIQUE, mimeType, widthPx, heightPx,
             byteSize, capturedAt, originalUri, …)
media_links (id, uuid, mediaId, entityType, entityId, sortOrder, …)
tags        (id, uuid, name UNIQUE COLLATE NOCASE, …)
tag_links   (id, uuid, tagId, entityType, entityId, …)
search_fts  (entityType, entityId, text)
```

Indices beyond `uuid`: `media_links(mediaId)`, `media_links(entityType, entityId)`,
`tag_links(tagId)`, `tag_links(entityType, entityId)`, and
`UNIQUE tag_links(tagId, entityType, entityId)` so attaching a tag twice is a no-op rather
than a duplicate.

### Departures from the original satellite list

**`media.sha256` replaces `media.localPath`.** This is not a rename. An absolute path is wrong
the moment it is read on a different install — `filesDir` differs — so a restored database
would point at nothing. A path derived from the hash is correct everywhere, and deduplication
comes free: the same photo imported twice is one file and one row.

**`media_links.sortOrder` is new.** A photo gallery has an order, and ordering by `id` breaks
the first time a photo is removed and re-added.

**Three columns are dropped**, each re-addable as a one-line migration the day something can
write it:

- `places.googlePlaceId` — the Places API is explicitly out of Phase 1 (`2026-08-26` §12), so
  no code path can ever populate this.
- `places.type` — a place's type is already implied by the module linking to it. A restaurant
  module's places are restaurants.
- `tags.kind` — one kind exists. Add it the day topical tags start polluting the "who with"
  picker, not in anticipation of them.

A fourth specced column, `restaurant_visits.transactionId`, is also not built. It belongs to the
restaurant module rather than to this satellite list, and it is dropped for a different and more
interesting reason — §3.1.

### 3.1 The cross-module link, and why it is not built

`2026-08-26` §6 stated that a restaurant visit "will carry a nullable `transactionId`", so
that a ৳1,200 dinner is one record appearing in both modules, and that "this pattern is
established now and validated when the second module is built."

**It will not be validated, because the second module does not want it.** A visit's cost is a
fact about the meal, not about the user's wallet — a meal someone else paid for has a cost and
no transaction. Forcing a link would either be wrong or be left null in exactly the cases that
motivated it. `restaurant_visits.costMinor` is therefore independent
(`2026-09-04-khata-restaurants-design.md` §2).

This is recorded rather than quietly skipped: the pattern remains available and unproven, and a
later module that genuinely spends money — car servicing is the obvious candidate — is where it
gets its first real test.

---

## 4. Media

### Capture

`ActivityResultContracts.PickMultipleVisualMedia`. On `minSdk 33` the photo picker requires
**no permission at all** — a quiet dividend of the floor having been raised to 33 for the glass
design system.

### The downscale rule

> If the source is already JPEG **and** its long edge is ≤ 2048 — copy the bytes verbatim.
> Otherwise decode, scale to 2048 on the long edge, re-encode at q85.

The branch exists because re-encoding an already-small JPEG spends quality to save nothing.

Decoding goes through `ImageDecoder`, not `BitmapFactory`: `ImageDecoder` applies EXIF
orientation automatically and `BitmapFactory` does not, and the failure mode of getting that
wrong is every food photo lying on its side.

`capturedAt` is read from `MediaStore.DATE_TAKEN` on the picked URI when it is readable and
left null otherwise. Deliberately **not** `androidx.exifinterface` — a whole dependency for one
nullable convenience, when the visit already knows its own date.

`originalUri` is retained as a nullable link back to the gallery, never a dependency. It lets a
photo offer "open the original" when the original still exists; when it does not, the link is
dead and nothing else is affected. This is D10 (`2026-08-26` §4) unchanged: the app owns its
copy, and clearing the camera roll cannot break a record.

### Write path

```
pick → decode → scale → encode → sha256(final bytes)
     → write temp → atomic rename to filesDir/media/<sha256>.jpg
     → media row (reuse if the hash already exists) → media_links row
```

If the file exists, the work is already done. If a `media` row already carries that hash, only
a link row is written. Importing the same photo into two visits costs one row.

### Deletion

Detaching a photo removes the `media_links` row. Removing a photo entirely tombstones the
`media` row. **Neither ever deletes the file.** That is S6, and §8 depends on it completely.

### Display

Adds **Coil 3** (`io.coil-kt.coil3:coil-compose`), which `PRODUCT.md` already names in the stack
but the version catalog does not yet carry. It earns the dependency on a point beyond async
loading and caching: it downsamples to the target size, so a 100dp thumbnail does not decode a
2048px bitmap. Hand-rolling that is a worse LRU cache than Coil's.

---

## 5. Places

The substance is a parser, and it takes the same shape as the SMS work: a pure function plus a
corpus file at `docs/superpowers/specs/maps-urls.md`, tested the way `sms-corpus.md` is tested
by `CorpusTest`.

A Google Maps share arrives as plain text with the name and the URL on separate lines:

```
Sultan's Dine
https://maps.app.goo.gl/AbCdEf
```

| Form | Yields |
|---|---|
| `/maps/place/Name/@23.74,90.37,17z/data=…!3d23.74!4d90.37` | name + coordinates |
| `?q=23.74,90.37` · `/maps/search/?api=1&query=23.74,90.37` | coordinates |
| `geo:23.74,90.37?q=Name` | name + coordinates |
| `maps.app.goo.gl/AbCdEf` | nothing — requires a redirect |

**Prefer `!3d`/`!4d` over `@lat,lng`.** The `@` is the camera position — where the map view
happened to be centred — while `!3d!4d` is the place itself. They disagree whenever the user
panned before sharing, and the camera position is the wrong answer.

Short links are resolved with one `HttpURLConnection`, `instanceFollowRedirects = false`,
reading the `Location` header — the same 30-line pattern as `GeminiClient` and `DriveWire`,
which is the house approach to HTTP and does not warrant a second one.

`lat` and `lng` are nullable and nothing depends on them. As `2026-08-26` §12 already decided:
on resolution failure the URL is still stored and the place stays openable in Maps. Offline is
a normal outcome, not an error.

---

## 6. Tags

`tags.name` is `UNIQUE COLLATE NOCASE`, so "Rafi", "rafi", and "RAFI" are one tag. That is the
entire point — without it, autocomplete starts suggesting three spellings of the same person and
"everywhere I went with Rafi" quietly returns a third of the answer.

Repository surface: `findOrCreate(name)`, `attach`, `detach`, `tagsFor(entityType, entityId)`,
`observeAll()` for autocomplete. `findOrCreate` is an insert-or-select against the NOCASE index.

Tags are free-form and cross-module by construction: `tag_links` carries `entityType` and
`entityId`, so nothing about the table knows what a restaurant visit is.

---

## 7. Search

### The index

`search_fts` is a Room `@Fts4` entity with `tokenizer = FtsOptions.TOKENIZER_UNICODE61` and
`notIndexed = ["entityType", "entityId"]` — those two are filters and join keys, not searchable
text.

### Maintenance

Repositories contribute an `IndexSource`, bound with Hilt `@IntoSet`:

```kotlin
interface IndexSource {
    val entityType: String
    suspend fun textFor(entityId: Long): String?   // null when deleted or unindexable
    suspend fun allIds(): List<Long>
}

class SearchIndex(sources: Set<IndexSource>, dao: SearchDao) {
    suspend fun reindex(entityType: String, entityId: Long)
    suspend fun remove(entityType: String, entityId: Long)
    suspend fun reindexAll()
}
```

A module registers one `@IntoSet` binding and is fully indexed, including full rebuilds. That is
the "module 2 is UI work" promise made concrete rather than asserted.

**This is also how tag folding works.** Tag names are part of an entity's indexed text, so
attaching "Rafi" calls `reindex(entityType, entityId)` on the entity it was attached to and the
`IndexSource` rebuilds that row. One method covers ordinary writes, tag changes, and full
rebuilds — and "Rafi" finds the dinner with no separate tag-search path and no join at query
time.

### What is indexed

- **transactions** — `merchantRaw`, `note`, `counterparty`, plus the resolved merchant's
  `canonicalName` and its aliases, plus tag names.
- **places** — `name`, `address`, `note`.
- **restaurants** — defined in the restaurant spec §6.

Category names are deliberately **not** indexed. Searching "Food" and receiving four hundred
rows is not a hunt, and the ledger already has filters for that job.

### The query builder

A pure function, and it inherits a hazard from the code it replaces. `TransactionDao` today
carries a pointed comment about unescaped `%` turning a search that should find nothing into one
that returns the whole ledger. The FTS equivalent is an unescaped `"`, which breaks `MATCH`
syntax into a SQL error rather than a bad result.

So: strip FTS operators, split on whitespace, append `*` to the final token for as-you-type
prefix matching, join (FTS4 ANDs implicitly). An empty query performs no search.

### What the ledger gains

Three failures in the current `LIKE` search are fixed, all of them present today:

1. **Merchant aliases do not match.** The query hits `merchantRaw` and `note` only. A row whose
   raw text is `FP*8823` but which resolved to merchant "Foodpanda" is invisible to a search for
   "foodpanda", even though `merchant_aliases` exists to record exactly that.
2. **Multi-word queries fail.** `LIKE '%sultan dine%'` demands adjacency, so "sultan dine"
   misses "Sultans Dine Dhanmondi".
3. **`counterparty` is unsearchable**, though the owed-money feature writes names into it.

The DAO query changes and nothing else does:

```sql
SELECT t.* FROM transactions t
JOIN search_fts f ON f.entityType = 'transaction' AND f.entityId = t.id
WHERE f.text MATCH :q AND t.deletedAt IS NULL
ORDER BY t.occurredAt DESC, t.id DESC
```

It remains a `PagingSource<Int, TransactionEntity>`, so `repository.pagedTransactions(q)` keeps
its signature and `LedgerViewModel`, its `flatMapLatest`, and the whole Paging 3 pipeline are
untouched.

The 7 → 8 migration backfills the index for existing transactions in SQL, so search works when
the app next opens rather than after a rebuild the user has to trigger.

### Ranking

By entity kind, then recency. FTS4 has no `bm25` — that is FTS5, which Room's annotations do not
cover and which would mean hand-written SQL Room cannot validate. For a personal ledger, recency
is the right order and pretending otherwise is not worth the machinery.

---

## 8. Backup, including media

### The problem with the obvious answer

The obvious answer is to zip the database and the photos together and bump the format version.
`BackupRepository` keeps `KEEP = 7` rolling backups, and the comment beside it reads "a few
hundred kilobytes each." Fold photos into that archive and seven nightly copies of the entire
photo library sit on the phone — gigabytes, to protect bytes that by S5 can never change.

### What makes the small answer correct

Because media files are content-addressed (S5) and never hard-deleted (S6), **the live media
store is always a superset of what any retained backup references.** A nightly archive therefore
does not need to carry photos: restoring last Tuesday's database re-points at files that never
went anywhere.

| Path | Entries | Size |
|---|---|---|
| Nightly local | `db` only | unchanged — kilobytes, `KEEP = 7` stays honest |
| **Export / share** | `db` + every non-tombstoned media file | full |
| Drive (later) | `db` nightly, each media blob uploaded **once** | incremental by construction |

Media leaves the device completely, today, through export — no Drive dependency and no new
library. And when the Drive plan resumes, content addressing makes "have I uploaded this photo
already?" a filename comparison, so the incremental media sync `2026-08-26` §14 wanted is a
consequence of the design rather than machinery bolted onto it.

### Format v2

One format with variable contents: a zip inside the existing encrypted envelope, holding a `db`
entry and zero or more `media/<sha256>.jpg` entries. The reader does not branch on which it
received — it unpacks what is present and **merges** media into the store. Never wipes: an
orphaned file costs disk, and a wipe costs photos.

`db` is deflated normally; media entries are written with
`ZipOutputStream.setLevel(Deflater.NO_COMPRESSION)`. JPEG does not deflate, so the default level
burns CPU across hundreds of megabytes for nothing. That is simpler than `ZipEntry.STORED`,
which would require computing sizes and CRCs by hand.

**v1 files must still restore.** Backups written by the current build exist on the phone, so the
reader branches on the header's format version: v1 is raw database bytes, v2 is the zip. That is
what the version field was put there for.

`SCHEMA_VERSION` goes 7 → 8.

### Streaming, and one banned class

`backUp` currently does `source.readBytes()` and `restore` takes `bytes: ByteArray` — the whole
file in memory. Fine for a 2 MB database, fatal for a 300 MB export. Both become streams:
`FileOutputStream` → plaintext header → `CipherOutputStream` → `ZipOutputStream`.

**`CipherInputStream` must not be used on the read side.** It swallows `AEADBadTagException` and
reports a clean EOF, so a tampered or truncated archive would present as a successful restore —
the worst possible outcome for this feature. The reader drives `cipher.update` / `doFinal`
itself and maps `AEADBadTagException` onto the existing `BackupResult.WrongPassphrase`.

The current one-shot code is safe only because `doFinal(bytes)` cannot behave that way. The
hazard is created by the move to streams, not inherited.

### Restore ordering

Everything unpacks into a staging directory first. The GCM tag is verified before
`khata.db.replaced` is written or a single media file is touched — the same stage-then-swap
instinct the existing restore already encodes, extended to cover media.

---

## 9. Testing

Robolectric 4.16.1 already runs the migration, DAO, repository, and Compose suites in
`app/src/test`, so most of this lands there.

- **`Migration7To8Test`** — tables and indices created; FTS backfilled from existing
  transactions.
- **Search** — query-builder hazards (quote injection, prefix matching, empty input); alias
  matching; multi-word; `counterparty`; and a Bengali-script case that fails under the `simple`
  tokenizer, so S4 cannot be silently regressed.
- **Backup** — a v1 archive still restores; a v2 round-trip carrying real media files; and a
  flipped-byte archive returning `WrongPassphrase` rather than a truncated success.
- **Places** — the Maps-URL corpus, including a case where `@` and `!3d!4d` disagree.
- **Media** — hashing, deduplication, path derivation, and which downscale branch is taken.

**One honest limit.** Robolectric's `Bitmap` is a shadow that does not really decode, scale, or
encode. The decision logic above is tested behind an interface; actual downscaling and EXIF
rotation are verified on the Pixel 6a. This is stated so it does not surprise anyone
mid-implementation.

---

## 10. Out of scope

- **The global search screen.** It ships in the restaurant spec, where there are three kinds of
  thing to group instead of one.
- **Any screen for places, media, or tags.** They ship as the restaurant module's UI.
- **The wallet gaining receipt photos or merchant places.** Declined by the user.
- **The Google Places API.** Unchanged from `2026-08-26` §12 — it needs a billing account for a
  handful of lookups.
- **FTS5 and relevance ranking.** See §7.
- **Drive upload of media.** The design makes it cheap; the Drive plan owns the work.
