# Khata (খাতা) — Design Spec: Application Spine + Wallet Module

**Date:** 2026-08-26
**Status:** Design approved. Implementation plan not yet written.
**Scope:** Phase 1 only — the shared application spine plus the complete wallet module.

---

## 1. Problem

Khata is a personal Android app that consolidates several unrelated pieces of
personal record-keeping into one place. Phase 1 solves money tracking.

The specific problem: digital payments in Bangladesh (bKash, EBL) already produce
a complete transaction record in the form of SMS, but that record is unqueryable.
It cannot be summed, categorized, charted, or searched. Meanwhile every manual
expense-tracking app fails for the same reason — manual entry is a chore, and the
chore gets abandoned within weeks.

Khata ingests those SMS automatically. The user is never asked to record a digital
transaction. Cash — the one category SMS cannot see — is handled by a home screen
widget fast enough to actually use.

## 2. Goals

- Automatically capture every bKash and EBL transaction from SMS with no user action.
- Make cash entry fast enough to be habitual (target: three taps from home screen).
- Answer "where did my money go?" with category breakdowns and month-over-month trends.
- Track budgets (monthly limits per category) and net worth over time.
- Work fully offline. Network and AI are enhancements, never dependencies.
- Establish a shared spine (places, media, tags, search) that later modules reuse
  rather than reinvent.

## 3. Non-goals (Phase 1)

- Savings goals, forecasting, and recurring-bill prediction. Deliberately excluded.
- Multi-currency. BDT only.
- Cloud sync. The schema is sync-ready; sync itself is not built.
- Play Store distribution. The architecture keeps that door open (§13) but this is
  a personal, sideloaded build.
- Any module other than the wallet.

## 4. Decisions taken

These were settled during design and are not open for re-litigation during
implementation:

| # | Decision | Rationale |
|---|---|---|
| D1 | Module-owned tables + shared satellite tables | Honest per-module schemas; shared places/media/tags/search; cross-module links explicit and optional (§6) |
| D2 | Single Gradle module, package-per-feature | Multi-module ceremony is not worth the iteration cost at solo scale |
| D3 | Rules-first parsing, AI only as fallback | ~95% of messages match templates; AI-per-message is slow, costly, non-deterministic, and leaks data |
| D4 | Parsing rules stored in the DB, not code | AI can write new rules; format changes are fixed in-app, not by shipping an APK |
| D5 | Auto-record everything; no review gate | Explicit user choice. Mitigated by confidence markers, reconciliation (§9), and re-runnable parsing (§7.1) |
| D6 | Merchant to category memory, AI-seeded | Converges to zero-touch; deterministic once learned, so trends stay stable |
| D7 | Money as `Long` paisa | Floating-point money produces drift that is miserable to trace |
| D8 | Sync-ready schema (uuid, timestamps, soft delete) | ~5% extra effort now; avoids a migration later |
| D9 | Local-only, optional Google sign-in for Drive backup | App must be fully functional signed out |
| D10 | Photos copied and downscaled, not referenced by URI | Gallery cleanup must not break records; makes backup feasible |
| D11 | Phase 2 module order deliberately deferred | Choose it after the wallet module ships, with real information |

## 5. Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  UI · Jetpack Compose + Material 3                           │
│  Dashboard · Ledger · Insights · Accounts · Budgets ·        │
│  Settings · Glance home-screen widget                        │
└──────────────────────────────────────────────────────────────┘
                             ↓ StateFlow
┌──────────────────────────────────────────────────────────────┐
│  Domain · use cases, aggregation, reconciliation             │
└──────────────────────────────────────────────────────────────┘
                             ↓ Flow
┌──────────────────────────────────────────────────────────────┐
│  core/data · Room                                            │
│   module-owned: transactions, accounts, categories,          │
│                 merchants, budgets, balance_snapshots        │
│   satellites:   places, media, tags, tag_links, search_fts   │
│   ingestion:    raw_messages, parsing_rules                  │
└──────────────────────────────────────────────────────────────┘
                             ↑
┌──────────────────────────────────────────────────────────────┐
│  Ingestion · MessageSource interface                         │
│    SmsSource (READ_SMS)  |  NotificationSource (listener)    │
│      → rule engine → AI fallback → merchant resolution       │
│      → transfer detection → write                            │
└──────────────────────────────────────────────────────────────┘
```

**Stack:** Kotlin · Jetpack Compose · Material 3 · Room · Coroutines/Flow ·
Paging 3 · WorkManager · Glance · Hilt · Coil · kotlinx-serialization.

**SDK targets:** `minSdk 31` (Android 12), `compileSdk` / `targetSdk` at the current
stable level (confirmed against the installed SDK at scaffold time). Target device
is a Pixel 6a on Android 17; support below Android 12 is explicitly not required.

Because `minSdk` is 31, Material You dynamic color is unconditionally available —
no version branch, no static fallback palette. The splash screen API is native, and
the storage and permission models are the modern ones throughout, so no legacy
compatibility handling appears anywhere in the media pipeline.

**Package:** `com.wasif.khata`.

**Time:** all instants stored as UTC epoch millis; all display and all period
boundaries (month, day) computed in `Asia/Dhaka`.

## 6. Data model

Every table carries `uuid TEXT`, `createdAt`, `updatedAt`, `deletedAt` (nullable).
Deletes are soft — a mis-swipe never destroys a record, and sync later needs the
tombstones.

### Module-owned

**`accounts`** — `name`, `type` (MFS / BANK / CARD / CASH / MANUAL_ASSET),
`openingBalanceMinor`, `currentBalanceMinor`, `reportedBalanceMinor`,
`reportedBalanceAt`, `includeInNetWorth`, `smsIdentifiers` (sender addresses and
account-tail fragments that map messages to this account).

The two balance fields are deliberately distinct and are the basis of §9:
`currentBalanceMinor` is Khata's own figure, maintained incrementally on every
transaction write. `reportedBalanceMinor` is the balance the bank or MFS stated in
its most recent SMS. They should agree; when they do not, something is missing.

`MANUAL_ASSET` accounts (savings, DPS, FDR, investments, cash-on-hand) have no SMS
feed and are updated by hand. They exist so net worth reflects net worth, not just
spending wallets.

**`transactions`** — `accountId`, `amountMinor` (always positive),
`direction` (DEBIT / CREDIT), `occurredAt`, `merchantRaw`, `merchantId` (nullable),
`categoryId` (nullable), `note`, `source` (SMS / NOTIFICATION / WIDGET / MANUAL),
`confidence` (HIGH / MEDIUM / LOW), `rawMessageId` (nullable),
`transferGroupId` (nullable), `feeMinor` (nullable), `referenceNumber` (nullable).

**`categories`** — `name`, `icon`, `colorToken`, `parentId` (nullable, one level of
nesting), `isSystem`. Seeded with a default set; fully user-editable.

**`merchants`** — `canonicalName`, `categoryId`, `placeId` (nullable),
`isUserConfirmed`. Plus **`merchant_aliases`** (`merchantId`, `rawText` unique) —
bKash writes the same shop several ways, and each variant maps to one merchant.

**`budgets`** — `categoryId`, `limitMinor`, `periodType` (MONTHLY),
`effectiveFrom`, `effectiveTo` (nullable). Historical limits are preserved so past
months are evaluated against the limit that was actually in force.

**`balance_snapshots`** — `accountId`, `balanceMinor`, `capturedOn` (local date).
One row per account per day; the source of the net-worth time series.

### Satellites (shared with future modules)

**`places`** — `name`, `address`, `lat`, `lng`, `mapsUrl`, `googlePlaceId`,
`type`, `note`.

**`media`** — `localPath` (app-private), `originalUri` (nullable, link back to the
gallery), `mimeType`, `widthPx`, `heightPx`, `byteSize`, `capturedAt`.
Plus **`media_links`** (`mediaId`, `entityType`, `entityId`).

**`tags`** and **`tag_links`** (`tagId`, `entityType`, `entityId`) — free-form,
cross-module.

**`search_fts`** — Room `@Fts4` virtual table over titles, notes, merchant names,
and place names across all modules. One search box finds a name whether it lives in
a transaction, a place, or (later) a note.

### Ingestion

**`raw_messages`** — `sender`, `body`, `receivedAt`, `bodyHash`,
`status` (PENDING / PARSED / UNMATCHED / IGNORED), `matchedRuleId` (nullable).
Unique on (`sender`, `bodyHash`, `receivedAt`) to make import idempotent.
**Raw messages are never deleted.**

**`parsing_rules`** — `name`, `senderPattern`, `bodyPattern` (regex with named
groups: `amount`, `balance`, `merchant`, `refId`, `datetime`, `fee`),
`direction`, `accountMatcher`, `kind` (NORMAL / TRANSFER_OUT / TRANSFER_IN /
ATM_WITHDRAWAL / FEE / IGNORE), `priority`, `origin` (BUILTIN / AI / USER),
`isEnabled`, `sampleMessage`.

### Cross-module linking

Later modules link to a transaction rather than duplicating it. A restaurant visit
will carry a nullable `transactionId`; dinner is **one** ৳1,200 record appearing in
both the restaurant log and the wallet, never counted twice. This pattern is
established now and validated when the second module is built.

## 7. Ingestion pipeline

```
message arrives (SMS broadcast, notification, or backfill scan)
  │
  ├─ persist to raw_messages          ← always, before anything else
  │
  ├─ rule engine: match parsing_rules by priority
  │     hit  → extract fields         ← offline, free, instant, ~95%
  │     miss → AI fallback (§13)      ← extracts fields AND drafts a new rule,
  │                                     validated against the message, then
  │                                     saved. Never asked again for this format.
  │     still no result → status = UNMATCHED, surfaced in Settings, no data lost
  │
  ├─ merchant resolution
  │     alias known    → merchant + category applied silently, confidence HIGH
  │     alias unknown  → AI suggests a category, merchant created,
  │                      confidence MEDIUM, flagged for later confirmation
  │
  ├─ transfer detection (§8.2)
  │
  └─ write transaction · update account balance · reconcile (§9) · index for FTS
```

**Source abstraction.** `SmsSource` and `NotificationSource` both implement one
`MessageSource` interface and feed an identical downstream pipeline. Nothing past
the interface knows which one is active. See §13.3.

**Threading.** Parsing and AI calls run in `WorkManager` with retry and backoff.
Nothing parses on the main thread; nothing blocks a screen.

**Timestamp precedence.** If the message body contains an explicit datetime (bKash
includes one), that wins. Otherwise the SMS received timestamp is used.

**Confidence levels:**

- `HIGH` — matched a rule, merchant alias already known.
- `MEDIUM` — matched a rule but the merchant is new, or AI-parsed with a rule
  successfully generated and validated.
- `LOW` — AI-parsed without a validated rule, or fields recovered heuristically.

Every transaction is recorded regardless of confidence (D5). Confidence drives a
subtle marker in the ledger and a "needs attention" filter, so if totals ever look
wrong the suspects are found in seconds rather than by scrolling years of history.

### 7.1 Re-runnable parsing

Because raw messages are retained permanently, parsing is **idempotent and
re-runnable**. A "Reparse history" action in Settings re-runs the current rule set
over all stored messages. Adding a rule six months from now retroactively fixes
every message that previously failed or parsed incorrectly. This is the primary
repair mechanism for a system with no review gate — errors are corrected in bulk,
not one at a time.

## 8. Transfers

### 8.1 Why this matters

A bKash cash-in from EBL produces two messages: a debit from EBL and a credit to
bKash. Recorded naively that is ৳5,000 of spending plus ৳5,000 of income, and every
total in the app becomes wrong.

### 8.2 Two detection mechanisms

**Explicit** — rules classify known phrasings directly: bKash "Cash In",
"Add Money", ATM withdrawal, credit card bill payment. The rule `kind` field
carries the classification.

**Pairing heuristic** — a DEBIT and a CREDIT with equal `amountMinor`, on two
different accounts, within a **15-minute window**, are grouped under a shared
`transferGroupId`. Runs on write and again during backfill.

Neither leg of a transfer counts as income or expenditure in any aggregate.

### 8.3 ATM withdrawals

An ATM withdrawal is a transfer **into the Cash account**, not a ৳10,000 expense.
The cash then becomes itemizable through the widget as it is actually spent. If it
is never itemized, reconciliation (§9) reports the gap rather than silently
mis-attributing it.

## 9. Balance reconciliation

bKash and EBL messages usually report a running balance. That figure is
authoritative and free.

On every parse, the reported balance is stored on the account. A background check
compares it against the balance computed from recorded transactions. When they
diverge, the app surfaces the gap explicitly — *"EBL is ৳340 below recorded
transactions since 12 Aug"* — with a one-tap option to record the difference as an
uncategorized adjustment.

This is the arithmetic safety net that makes auto-record-without-review (D5) safe.
Missed cash purchases, unparsed fees, and misparses are caught by the numbers not
adding up, rather than by the user policing an inbox.

## 10. Historical backfill

On first launch, and on demand afterwards, the app scans the entire existing SMS
inbox and runs it through the pipeline. The app opens on day one with years of real
history, meaningful charts, and a net-worth line that already has shape — instead
of an empty app that becomes useful in a month.

Runs as a chunked `WorkManager` job with visible progress, cancellable and
resumable. Cheap by construction: rules handle nearly everything, and the AI
fallback fires once per unknown *format*, not per message.

## 11. Budgets and net worth

**Budgets** — a monthly limit per category, evaluated over calendar months in
`Asia/Dhaka`. Progress is shown against spending excluding transfers. Limit changes
are versioned via `effectiveFrom` / `effectiveTo` so past months stay historically
accurate.

**Net worth** — a nightly `WorkManager` job (00:05 local, plus on-demand) writes a
`balance_snapshots` row per account, including manual assets. The time series is
read directly for charting; it is never recomputed by summing transactions at
render time.

## 12. Places, media, and search

**Place capture — three paths, all supported:**

1. **Share from Google Maps.** Khata registers as a share target; sharing a place
   captures it directly. Primary path, best UX, no API key.
2. **Paste a link.** A Maps URL pasted into the app is parsed for coordinates and
   name. Short links (`maps.app.goo.gl`) contain no coordinates, so the app follows
   the redirect once at save time to resolve them. On failure it still stores the
   URL, and the place remains openable in Maps.
3. **Manual entry** — name and address typed directly.

Google Places API is deliberately not used in Phase 1: it requires a billing
account for what is a handful of lookups. It remains available as an optional
typeahead enhancement later.

**Media** — imported photos are **downscaled to 2048px on the long edge, JPEG q85,
and copied into app-private storage**. The original gallery URI is retained as an
optional link back. Rationale: URI references break when the gallery is cleaned out
or the app is reinstalled, and originals at ~4 MB each make backup infeasible while
copies at ~400 KB do not, with no visible quality difference at app scale.

**Search** — a single FTS-backed search box spanning every module.

## 13. AI layer, privacy, and the Play Store path

### 13.1 Scope of AI use

Gemini does exactly two jobs:

1. Parse a message that matched no rule — and draft a new rule so it is never asked
   about that format again.
2. Suggest a category for a merchant not seen before.

Nothing else. It is a fallback, not a dependency.

### 13.2 Privacy controls

| Concern | Handling |
|---|---|
| Data egress | Only rule-misses are sent, and only once per *format*. Egress trends to zero within weeks. |
| Redaction | Account numbers, card last-4, and phone numbers are stripped before sending. The parser does not need them. |
| API key | Entered by the user in Settings, stored in `EncryptedSharedPreferences`. Never in source, never committed. |
| Kill switch | An AI-off toggle. Rules-only mode is fully functional; unmatched messages queue as `UNMATCHED`. |
| Model | Gemini Flash with structured JSON output — this is field extraction, not reasoning. |
| Execution | `WorkManager` with retry and backoff. Never on the UI thread. |

### 13.3 Play Store path

`READ_SMS` is a Google-restricted permission and expense tracking is not among its
permitted use cases. It cannot ship publicly.

The `MessageSource` abstraction (§7) resolves this at near-zero cost: `SmsSource`
(`READ_SMS`, personal sideloaded build) and `NotificationSource`
(`NotificationListenerService`, no restricted permission, publicly shippable) feed
an identical pipeline. Combined with optional Google sign-in (D9), a future public
build is a build-variant question rather than a rewrite.

A public build would additionally require proxying Gemini calls rather than holding
a user-supplied key. Out of scope for Phase 1.

## 14. Backup

**Two tiers, because the data has two shapes.** The database is small and must be
backed up often; media is large and changes incrementally.

- **Always:** a nightly encrypted snapshot written locally, plus one-tap Share to
  move it anywhere manually.
- **If signed in to Google:** automatic upload to a private Drive app folder.
  Survives phone loss, theft, and factory reset.
- **Signed out:** everything above except the upload. The app is fully functional
  without an account.

**The encryption key is derived from a user passphrase, not generated in the
Android Keystore.** A Keystore-generated key never leaves the device, which would
make every backup unrestorable on a replacement phone — precisely the scenario
backup exists for.

Concretely: the user sets a backup passphrase once. The key is derived from it
(PBKDF2 or Argon2) and the *derived key* is cached in the Keystore so nightly
backups can run unattended without prompting. The *passphrase* is what makes the
backup portable — entering it on any device reproduces the key and restores the
archive. Losing the passphrase means losing the backups, and the app must say so
plainly at the point where it is set.

Media is backed up separately and incrementally, so a large photo library never
blocks or delays the database snapshot.

## 15. UI and design requirements

Fluidity and visual quality are **stated hard requirements**, not polish. They
constrain implementation.

**Design system.** A `core/ui` package defines color, typography, spacing,
elevation, shape, and motion tokens from day one. Every module consumes it. Modules
must not define ad-hoc styling; six modules built ad-hoc become six visual
languages, and by then it is unfixable.

**Visual identity** is settled in a dedicated design pass before UI code is
written, using the `impeccable`, `mobile-app-ui-design`, and
`android-skills:android-ux` skills. Direction to explore: *খাতা* is a ledger book,
which offers a concrete design language (paper, ruled lines, ink, warm neutrals) as
an alternative to generic fintech dark-mode. Material 3 with dynamic color is the
fallback if a custom identity does not earn its place.

**Motion is systematic, not per-screen** — a shared easing and duration scale,
shared-element transitions from list to detail, and predictive-back support.

**Screens:** Dashboard (month spend, budget rings, net-worth sparkline, recent
activity) · Ledger (paged, day-grouped, searchable, filterable) · Transaction
detail and edit · Insights (category breakdown, month-over-month, top merchants) ·
Accounts (balances, reconciliation) · Budgets · Settings (rule editor, categories,
API key, backup, reparse, unmatched messages) · Glance widget.

**Widget target: three taps** — amount, account, category, saved — with recent and
frequent choices ordered first.

## 16. Performance requirements

Fluidity is decided in the data layer, not in a later styling pass. These are
binding:

- **Paging 3** for the ledger. It will hold thousands of rows; it is never loaded
  whole.
- **Precomputed aggregates.** Charts and dashboard figures read summary tables
  maintained on write. Nothing sums thousands of rows during a scroll.
- **Stable, immutable UI types.** No unstable parameters into composables; no
  recomposition storms.
- **Coil** for all image loading, with explicit size hints in lists.
- **All database access through Flows off the main thread.** No synchronous reads
  anywhere in the UI path.
- **No parsing, AI, or backup work on the main thread**, ever.

## 17. Testing

**Parsing corpus tests are the priority.** The parsing engine is simultaneously the
highest-risk and most testable component. A corpus of real SMS samples paired with
expected parse output runs as unit tests; every new rule adds a case. A regex edit
or an upstream format change is caught immediately rather than surfacing weeks
later as corrupted totals.

Also covered: transfer-pairing logic (including near-miss cases that must *not*
pair), reconciliation arithmetic, budget period boundaries across month edges, Room
DAO tests, and Compose UI tests for widget entry, ledger scrolling, and transaction
editing.

## 18. Implementation prerequisites

Needed during implementation, not blocking the plan:

1. **Real SMS samples** — bKash and EBL, covering: payment, send money, cash out,
   cash in, received money, ATM withdrawal, POS purchase, account credit, and fee
   messages. These become both the initial rule set and the test corpus. The user
   has agreed to provide these.
2. **A Gemini API key**, entered in-app at runtime.
3. **A backup passphrase**, chosen at first run (§14).

## 19. Future modules (not in scope)

Candidates: restaurants, movies (via TMDB or OMDb — IMDb has no free public API),
notes with inline images, car service tracking, deadlines, and a lending ledger
(*khata* in its original sense). Each gets its own spec, plan, and implementation
cycle.

**Order is deliberately undecided (D11).** It will be chosen once the wallet module
is complete, when the UI system has settled and the reusable patterns have been
validated in practice.
