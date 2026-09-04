# Khata (খাতা) — Design Spec: The Restaurant Module

**Date:** 2026-09-04
**Status:** Design decided. Implementation plan not yet written.
**Requires:** `2026-09-04-khata-shared-spine-design.md`. Every place, photo, and companion in
this module comes from there; this spec adds no satellite tables of its own.
**Settles:** `2026-08-26-khata-wallet-design.md` D11 — the Phase 2 module order, deliberately
deferred until the wallet shipped, is restaurants.

---

## 1. What this is

The second module, and the first real consumer of the spine. It is a record of restaurants
visited: where they are, who you went with, what you ate and whether it was any good, what it
cost, photographs, and notes. It also holds the places you have not been to yet.

It exists in this run rather than later because a spine with no consumer is a set of interfaces
designed against imagination. Building both together means every satellite table is exercised by
real screens before it is called finished.

Three of the module's four "hard" features are not built here at all — location, photos, and
companions are spine calls. What remains is genuinely restaurant-shaped: visits, dishes, and
ratings.

---

## 2. Decisions taken

| # | Decision | Rationale |
|---|---|---|
| R1 | Two levels: a restaurant, with visits under it | Going back six months later with different people and different food is a new visit, not an edit. Makes "how many times, and is it getting worse" answerable |
| R2 | **Visit-first entry.** Typing a restaurant name creates it if new | Being made to register a restaurant before recording dinner is the kind of chore principle 1 says gets abandoned |
| R3 | The wishlist is **derived** — restaurants with no visits — not a flag | Zero new schema, and promotion happens automatically when the first visit is logged. Nothing to flip, nothing to drift |
| R4 | Dishes are free text per visit with a rating; there is no dish catalogue | Same reasoning that made `transactions.counterparty` free text: "is their kacchi consistently good" is a GROUP BY, and maintaining a menu is friction on the screen that most needs to be quick |
| R5 | The overall verdict is derived from the dish average | Nothing extra to fill in, and it cannot contradict the items it summarises |
| R6 | `costMinor` is independent of the wallet — no `transactionId` | A meal someone else paid for has a cost and no transaction of yours. See spine spec §3.1 |
| R7 | Photos attach to both visits and restaurants; the cover is a `coverMediaId` **pointer** | Most photos belong to an evening, but a storefront belongs to the place. "Set any photo from that visit as the cover" is a reference to an existing `media` row, never a second copy |
| R8 | The global search screen ships here | It was deferred only because there was one kind of thing to find. Now there are three |

---

## 3. Data model

Migration **8 → 9**, separate from the spine's 7 → 8 so each spec owns its own migration and its
own test. `BackupRepository.SCHEMA_VERSION` goes to 9 with it: it is stamped into every archive
and compared on restore, so leaving it at 8 would stamp a lie and quietly disable the "refuse a
backup newer than this app" guard for one version. House quartet on every table (`uuid`, `createdAt`, `updatedAt`, `deletedAt`).

```sql
restaurants       (id, uuid, name UNIQUE COLLATE NOCASE, placeId?,
                   coverMediaId?, note, …)
restaurant_visits (id, uuid, restaurantId, visitedAt, ambianceRating?,
                   costMinor?, note, …)
visit_dishes      (id, uuid, visitId, name, rating?, sortOrder, …)
```

Indices: `restaurant_visits(restaurantId)`, `restaurant_visits(visitedAt)`,
`visit_dishes(visitId)`.

Everything else comes from the spine and adds no columns:

| Concept | Where it lives |
|---|---|
| Companions, and who recommended a wishlist place | `tag_links(entityType = 'restaurant_visit' \| 'restaurant')` |
| Photos | `media_links(entityType = 'restaurant_visit' \| 'restaurant')` |
| Location | `places.id`, via `restaurants.placeId` |
| Cover image | `media.id`, via `restaurants.coverMediaId` |

**Ratings** are nullable integers 1–5. Both `ambianceRating` and `visit_dishes.rating` may be
left empty; a visit you did not rate is still a visit.

**`costMinor`** is `Long` paisa like every other amount in the app (D7), nullable, and owes
nothing to the wallet.

**`visitedAt`** is UTC epoch millis, displayed and bucketed in `Asia/Dhaka` — the house time
rule, unchanged.

### The derived pieces

**Overall verdict** — `AVG(rating)` across the restaurant's `visit_dishes`, shown beside
ambiance. Not stored.

**Wishlist** — restaurants with no non-deleted `restaurant_visits`. Not stored. A restaurant you
have been to has visits; one you want to try has none, and once R2 makes visits create their own
restaurants, a restaurant with zero visits can only be something added deliberately.

The consequence, accepted knowingly: somewhere you have already been can never appear on the
wishlist, so "want to go back and try their biryani" has no flag. Wanting to return is what a
good rating and a visit note already tell you.

---

## 4. Entry, and why it is visit-first

The first field of the visit editor is the restaurant name, autocompleting as you type. Pick an
existing restaurant and the visit attaches to it. Type a name that matches nothing and
`findOrCreate(name)` creates the restaurant when the visit is saved.

This is the identical pattern to the spine's `TagRepository.findOrCreate`, and it takes the same
`UNIQUE COLLATE NOCASE` treatment on `restaurants.name`. Without it, "Sultans Dine" and "sultans
dine" become two restaurants and the autocomplete begins suggesting duplicates of itself.

Two genuinely different restaurants sharing a name would collide. For one user in Dhaka this is
not worth a compound key against the place; renaming one is the answer if it ever happens.

**Autocomplete queries `restaurants.name` directly with a `LIKE` prefix match, ordered by most
recently visited — not the FTS index.** Prefix-matching a short list of names wants predictable
ordering; FTS would return fuzzier results in a field where the user is trying to hit one
specific row.

---

## 5. Screens

Package `feature/restaurants`, following package-per-feature (D2).

| Screen | Content |
|---|---|
| **Restaurants** (hub tile) | **Been** — cover, name, dish average + ambiance, last visited, ordered by recency. **Want to try** — name, place, note, recommender. Primary action: **Log a visit** |
| **Visit editor** | Restaurant name (autocomplete, creates if new) · date · ambiance stars · cost · dish rows (name + stars) · companion chips · photo picker · notes |
| **Restaurant** | Cover, place (tap opens Maps), both ratings, list of visits. Set the cover from any photo across any of its visits, and attach photos to the restaurant itself — the storefront or the room, which belong to the place rather than to one evening |
| **Add to wishlist** | Name, place, note, recommender tags — a restaurant with no visit |
| **Search** (global) | One box off the hub, results grouped: Transactions · Restaurants · Places |

**The Maps share target.** Khata registers for `ACTION_SEND` / `text/plain` and lands on **Add
to wishlist**, prefilled with the parsed name and link, with "log a visit instead" available. A
place someone sends you is usually somewhere you have not been yet. This is the capture path
`2026-08-26` §12 called primary, and it was unbuildable until places had somewhere to land.

---

## 6. Search

One `IndexSource` with `entityType = "restaurant"`. Its text is the restaurant name, its note,
its place name, **every dish across every visit**, and every companion tag.

So "kacchi" finds the restaurant rather than a visit buried inside it, and "Rafi" finds
everywhere you went with him. Visits are not indexed separately: one row per restaurant,
refreshed whenever any of its visits change.

The global search screen groups by kind and orders by recency within each group, per the spine
spec §7 — FTS4 has no relevance ranking and none is invented here.

---

## 7. Design system

Inherited, not reopened: `KhataTheme`, the existing `core/ui/component` primitives, petrol on
verdigris over teal-black, pale-aqua accent, serif numerals, Haze for the glass.

Two things are genuinely new and need design attention rather than reuse:

- **A photo grid over a blurred glass surface.** Every existing surface in the app sits over
  flat colour or a gradient; photographs behind glass behave differently, and contrast over an
  arbitrary image cannot be assumed.
- **A five-star control.** It must be comfortably thumb-hittable and sit in the bottom third of
  the screen per the one-handed requirement, which rules out a row of small tap targets near the
  top of a form.

Bengali script must render correctly in restaurant names, dish names, and notes — the same
requirement the ledger already carries.

---

## 8. Testing

Matching the existing suite's shape — DAO, repository, and Compose tests in `app/src/test` under
Robolectric.

- **`Migration8To9Test`** — tables and indices created.
- **`findOrCreate`** — a differently-cased name returns the existing restaurant rather than
  creating a second.
- **Wishlist derivation** — a restaurant with no visits appears; logging a visit moves it; soft-
  deleting its only visit moves it back.
- **Dish average** — ignores unrated dishes rather than counting them as zero.
- **Cover** — setting a cover from a visit photo points at the existing `media` row and creates
  no second file.
- **Search** — a dish name finds its restaurant; a companion tag finds every restaurant visited
  with that person.
- **Screens** — visit editor and restaurant list, following `LedgerScreenTest` and
  `TransactionEditorScreenTest`.

---

## 9. Out of scope

- **A "want to go back" flag.** See R3.
- **A dish catalogue or per-restaurant menu.** See R4.
- **Cuisine types, price bands, and any other classification.** Nothing asks for them.
- **Sharing a restaurant out of the app**, and any map view of places.
- **Linking a visit to a wallet transaction.** See R6 and spine spec §3.1.
- **Reservations, opening hours, or anything requiring a live data source.**
