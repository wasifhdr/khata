# Khata (খাতা) — Design Spec: The Car Servicing Module

**Date:** 2026-09-05
**Status:** Design decided. Implementation plan not yet written.
**Requires:** `2026-09-04-khata-shared-spine-design.md` (places, media, tags, search) and
`2026-09-04-khata-restaurants-design.md` — this module builds on the restaurants branch, not on
`main`. `StarRating`, the photo-picker wiring, and the global search screen all ship there.
**Settles:** `2026-09-04-khata-shared-spine-design.md` §3.1 — the deferred cross-module
`transactionId` link. It is **dropped**, not pending. See C3.
**Paired with:** `2026-09-05-khata-watchlist-design.md`. Designed in the same session, shipped
separately.

---

## 1. What this is

The third module. A permanent record of what has been done to the car: when, at what odometer
reading, by which workshop, what it cost, and what was actually replaced or repaired.

Two questions it exists to answer, both confirmed with the user and both unanswerable today:

1. **History** — what was done, when, at what odometer. "When were the brake pads last changed"
   and "has this been fixed before" are the questions a paper folder in the glovebox answers
   badly.
2. **Cost** — what this car costs to run. Total, per year, per kilometre, and by item.

It is deliberately not a maintenance reminder. See §9.

---

## 2. Decisions taken

Settled during design. Not open for re-litigation during implementation.

| # | Decision | Rationale |
|---|---|---|
| C1 | A `vehicles` table exists, but the UI assumes exactly one and shows no picker | A second car is plausible; a migration that has to invent a parent row for orphaned services is not. One small table now, no selector with one option in it |
| C2 | `services.costMinor` is the bill. `service_items.costMinor` is optional detail beneath it | A workshop bill is a few priced lines plus labour and VAT that do not decompose. Deriving the total from items would make an un-itemised bill unrecordable and force a fake "labour" line |
| C3 | **No `transactionId`.** The spine's cross-module link is dropped | The user declined it. This is the second module in a row to do so — restaurants for the reason in spine §3.1, car servicing by direct choice — so the pattern now has no caller anywhere and stops being cited as future work. Recorded here so a fourth module does not rediscover it as an open question. Re-addable as a one-line migration the day something wants it |
| C4 | Odometer is a column on `services`, plus a current value on `vehicles`. **No readings table** | Cost-per-kilometre needs two numbers and every service already carries one. A separate readings table is a screen the user has to remember to visit, and principle 1 says that gets abandoned |
| C5 | The workshop is a spine `place`, via `services.placeId` | The same column restaurants uses. Maps tap-through, autocomplete, and search indexing all come free |
| C6 | Items are free text with no service-type catalogue | R4's reasoning unchanged: "what have brake pads cost me" is a `GROUP BY`, and a catalogue is friction on the screen that most needs to be quick. The one thing a catalogue would have served — service intervals — is out of scope |
| C7 | Every cost view is derived and nothing is stored | Totals, yearly spend, cost per km, and spend by item name are all queries. Nothing to keep in sync and nothing that can drift |
| C8 | No vehicle screen. Name, registration, and odometer are edited inline from the hub tile header | Three fields on one row, opened twice a year. A screen for it is a screen for nobody |

---

## 3. Data model

Migration **11 → 12**, hand-written SQL in `core/data/migration/Migrations.kt` alongside the
existing ones, with a `Migration11To12Test` beside them.
`BackupRepository.SCHEMA_VERSION` goes to 12 with it: it is stamped into every archive and
compared on restore, so leaving it behind stamps a lie and disables the "refuse a backup newer
than this app" guard for a version.

House quartet on every table (`uuid` with a unique index, `createdAt`, `updatedAt`, `deletedAt`).
Deletes are soft.

```sql
vehicles      (id, uuid, name, registration?, odometerKm?, note, …)
services      (id, uuid, vehicleId, servicedAt, odometerKm?, placeId?,
               costMinor?, note, …)
service_items (id, uuid, serviceId, name, costMinor?, sortOrder, …)
```

Indices beyond `uuid`: `services(vehicleId)`, `services(servicedAt)`, `service_items(serviceId)`.

Everything else comes from the spine and adds no columns:

| Concept | Where it lives |
|---|---|
| The workshop, and its Maps link | `places.id`, via `services.placeId` |
| Photos of the bill or the damage | `media_links(entityType = 'vehicle_service')` |
| Anything worth tagging a job with | `tag_links(entityType = 'vehicle_service')` |

**`costMinor`** is `Long` paisa (D7), nullable on both tables. A service whose cost you never
learned is still a service.

**`odometerKm`** is a nullable `Int` in kilometres. A job where you forgot to read the dash is
recorded without it, and every derived figure that needs it skips that row rather than treating
it as zero.

**`servicedAt`** is UTC epoch millis, displayed and bucketed in `Asia/Dhaka` — the house rule,
unchanged.

**The single vehicle** is created lazily by the repository — `findOrCreateVehicle()` returns the
first live row or inserts one named "Car" — so no screen ever has to handle an empty parent. Not
seeded by the migration: a fresh install creates its schema from the entities and runs no
migration at all, so seeding there would need the same row written in two places and would
eventually be written in only one.

### The derived pieces

None of these are stored.

**Total spent** — `SUM(services.costMinor)` over non-deleted services.

**Spent per year** — the same sum grouped by year in `Asia/Dhaka`, not UTC. A service on
31 December at 22:00 Dhaka time is 4 p.m. UTC the same day, but the boundary cases are real and
the house rule already exists.

**Cost per kilometre** — total spend divided by the span between the lowest and highest
`odometerKm` across services. `null` when fewer than two services carry a reading, and `null`
rather than a division by zero when the span is zero. An invented number here would be worse
than an absent one.

**Spend by item name** — `SUM(service_items.costMinor) GROUP BY name COLLATE NOCASE`, descending.
`COLLATE NOCASE` so "Oil Filter" and "oil filter" are one row rather than two halves of an answer.

**The "other" line** — on one service, `costMinor` minus the sum of its items, shown **only when
every item on that service carries a cost**. That is when the remainder means labour and VAT
rather than "some items are unpriced", and showing it in the other case would present an unknown
as a known.

---

## 4. Entry

The service editor is the screen that has to be quick, exactly as the visit editor is for
restaurants. Fields in order: date, odometer, workshop, total cost, item rows, photos, note.

**The workshop autocompletes over `places.name` with a `LIKE` prefix match, ordered by most
recent use, and creates a place when nothing matches** — the same `findOrCreate` shape as
`TagRepository` and the restaurant name field, and for the same reason: being made to register a
workshop before recording a repair is the chore principle 1 says gets abandoned.

**The cost field uses `AmountKeypad`**, the wallet's own control. It exists, it is already
thumb-sized, and money in this app has one input.

**Item rows are added one at a time** with a name and an optional cost, and reorder by
`sortOrder`. No catalogue, no autocomplete over past item names — a prefix match over free text
the user typed once is a suggestion engine for their own typos.

---

## 5. Screens

Package `feature/vehicle`, package-per-feature (D2).

| Screen | Content |
|---|---|
| **Car service** (hub tile) | Vehicle name and current odometer as an editable header · running cost: total, this year, cost per km · services newest-first with date, odometer, workshop, total, and item names on one quiet line. Primary action in the bottom third: **Log a service** |
| **Service editor** | Date · odometer · workshop (autocomplete, creates if new) · total cost · item rows (name + optional cost) · photos · note |
| **Service** | One job in full, photos as a grid, and the *other* line when §3 allows it |
| **Costs** | Spend by item name, descending, with a total and a per-year breakdown above it |

The one-handed rule (`PRODUCT.md`) governs both: **Log a service** sits in the bottom third, and
nothing routine lives in a top corner.

---

## 6. Search

One `IndexSource` with `entityType = "vehicle_service"`, registered `@IntoSet` like every other.

Indexed text is **the item names, the note, and the workshop's place name**. So "brake" finds the
job and "Navana" finds every service done there. One row per service — items are not indexed
separately, for the same reason restaurant visits are not: the answer the user wants is the job,
not a fragment inside it.

The global search screen gains a fourth group. No new query path, no ranking beyond kind then
recency (spine §7).

---

## 7. Design system

Inherited, not reopened: `KhataTheme`, `core/ui/component`, petrol on verdigris over teal-black,
pale-aqua accent, serif numerals, Haze for the glass.

Nothing here is visually new. Every surface is a list, a form, or a photo grid, all of which the
restaurant module ships. `MoneyText` and `AmountKeypad` are reused as they are. `StarRating` is
**not** used — a service has no rating, and a car that needed work is not a car that disappointed
you.

Bengali script must render in workshop names, item names, and notes, as everywhere else.

---

## 8. Testing

Matching the existing suite: DAO, repository, and Compose tests in `app/src/test` under
Robolectric.

- **`Migration11To12Test`** — tables and indices.
- **Cost per km** — two services with readings give a figure; one service gives `null`; two
  services at the same odometer give `null` rather than dividing by zero.
- **Yearly totals** bucket in `Asia/Dhaka`, proven by a service timestamped inside the hours
  where Dhaka and UTC disagree about the year.
- **Spend by item** folds case-insensitively.
- **The "other" line** appears when every item is priced and is absent when any is not.
- **Soft delete** — a deleted service leaves every total, and the search index, without it.
- **Search** — an item name finds its service; a workshop name finds every service there.
- **Screens** — service editor and the hub list, following `VisitEditorScreenTest` and
  `LedgerScreenTest`.

---

## 9. Out of scope

- **Service intervals, due-soon, and reminders.** Declined by the user. It is the only feature
  here that would predict rather than record, and it is what a service-type catalogue would have
  existed to serve (C6).
- **Documents — insurance, fitness certificate, tax token expiry.** Declined by the user. Dates
  that expire are a different module from work that was done.
- **Fuel logging and mileage.** Fuel is money leaving an account, which the wallet already sees.
- **A second vehicle in the UI.** The table holds one (C1).
- **Linking a service to a wallet transaction.** See C3.
- **Parts inventory, warranty tracking, and workshop ratings.**
