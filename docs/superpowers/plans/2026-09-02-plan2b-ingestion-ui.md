# Khata Plan 2b — Ingestion UI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.
>
> **DO NOT START THIS PLAN until the preconditions in §Preconditions are met.** It is written to be executed against a design system that did not exist when it was written.

**Goal:** Make Plan 2a's ingestion visible and controllable — permission, backfill,
drift, unmatched messages, rule editing, and confidence in the ledger.

**Architecture:** Every screen is MVVM with the MVI-style state/effect split already
used by `LedgerViewModel` and `TransactionEditorViewModel`: one immutable `UiState`
as `StateFlow`, one-shot imperatives through `Channel(BUFFERED).receiveAsFlow()`,
derived values as getters rather than constructor parameters.

**Spec:** `docs/superpowers/specs/2026-09-02-plan2b-ingestion-ui-design.md`

## Preconditions — all met as of 2026-09-02

- [x] `design/glass-and-motion` merged to `main` (`0c9f74b`).
- [x] `plan2a/sms-ingestion` merged to `main` (`18310ac`).
- [x] `./gradlew testDebugUnitTest` passes on `main` — 227 tests, 0 failures.
- [x] `DESIGN.md` exists at the repo root and has been read. **It is the authority.** Where it and this plan disagree, DESIGN.md wins and this plan is stale.

## Global Constraints

- Package `com.wasif.khata`. Money is always `Long` paisa. Instants UTC millis, boundaries in `Asia/Dhaka`.
- Async is Coroutines/Flow — no `LiveData`. DI is Hilt + KSP.
- Derived UI values are getters on `UiState`, never constructor parameters.
- Durable state (an error still true after rotation) lives in `UiState`; one-shot imperatives go through the effects `Channel`.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- **No AI, no network.** Plan 3 owns that.
- Instrumented tests need a device or the `khata_test` emulator; it lives on `D:` because `C:` is full — see `.superpowers/sdd/progress.md`.

## Design system contract — from `DESIGN.md`

These are enforced by tests, not review. Breaking one fails the build.

1. **No colour literal outside `core/ui/theme`.** Every colour from `MaterialTheme.colorScheme`, `KhataPalette`, or `LocalCategoryColors`.
2. **`outline` is never text.** It is borders and disabled controls only. `ContrastTest` scans the source for `color = MaterialTheme.colorScheme.outline` and fails on a hit. Only two text tiers exist over the field: `onSurface` and `onSurfaceVariant` (§3.3).
3. **Colour is never the only signal** — a shape and a word say whatever colour says.
4. **Every screen root is `FieldScaffold`.** A screen that paints `colorScheme.background` itself is a bug: it will be the only screen without the field. Follow `SettingsScreen` as the reference (§4, §11).
5. **Layout rule (§9):** air at the top, content anchored to the bottom, heading centred in the air — a `weight(1f)` headspace above a wrap-content block. This is how one-handed reach is satisfied, rather than each screen remembering a rule. Long lists use `headspaceLedger` (132dp) instead.
6. **Heading + subline via `ContextHeader`**, not hand-rolled Text pairs.
7. **Glass placement (§5):** never on list rows (one blur pass per row per frame), never on settings selection controls (the tuner needs neutral surfaces). Selectable controls are **opaque when selected, glass when not, one shared body lambda** — a translucent selected chip reads as less committed, which inverts what selection means.
8. **Money uses `AmountTextStyle`** (Fraunces, `tnum`). Bengali strings need `BengaliBodyStyle` — Instrument Sans has no Bengali glyphs and will fall through to the device font. A raw SMS body or merchant name can contain Bengali.
9. **`alert` (`#FF7A6B`) is the designated colour for errors and balance drift** (§3.1).
10. **New destinations need no transition work** — anything hanging off the hub is shared-axis automatically, everything else fades through (§8, §11).
11. **A new colour goes in `Color.kt` with a `ContrastTest` assertion**, never at the call site.

---

### Task 1: Permission state and the runtime request

The highest-value screen in the plan: until this exists the app records nothing
automatically.

**Produces:** `SmsPermissionState` (`GRANTED` / `DENIED` / `PERMANENTLY_DENIED` / `NOT_REQUESTED`), `SmsPermissionRepository` exposing `observe(): Flow<SmsPermissionState>` and `refresh()`, and an `OnboardingViewModel`.

**Behaviour to test (JVM, with a fake permission checker):**

1. A never-requested permission reports `NOT_REQUESTED`, not `DENIED` — the two need different copy.
2. Granted reports `GRANTED` and enables the backfill action.
3. Denied once reports `DENIED` and keeps the in-app rationale available.
4. Denied twice reports `PERMANENTLY_DENIED` and the state exposes a route to app settings, because Android will no longer show the dialog.
5. `PERMANENTLY_DENIED` does **not** disable the rest of the app — a test asserts manual entry remains available. Denied is a legitimate way to use Khata.

**UI requirements:** rationale shown *before* the system dialog; backfill offered as a
separate explicit action after the grant, never automatically.

---

### Task 2: Backfill progress

**Produces:** `BackfillViewModel` wrapping `BackfillUseCase`, exposing progress as durable state.

`BackfillUseCase` currently returns a single `IngestSummary` at the end. Extend it to
emit progress — a `Flow<BackfillProgress>` — rather than blocking silently over a
multi-year inbox.

**Behaviour to test:**

1. Progress emits monotonically and ends at the total.
2. The final summary reports recorded / ignored / unmatched / duplicate counts.
3. Re-running reports all duplicates and records nothing new (already proven at the use-case level in Plan 2a; assert it survives the ViewModel).
4. Cancellation mid-run leaves the messages already ingested intact — Plan 2a's per-message atomicity guarantees this; the test pins it.

---

### Task 3: Reconciliation drift surface

**Produces:** `DriftViewModel` over `ReconciliationRepository.observeDrift()`, and a screen listing drifting accounts.

**Behaviour to test:**

1. No drift renders an explicit all-reconciled state, not an empty box.
2. A drifting account shows name, gap as formatted `Money`, and the date drift opened.
3. Recording an adjustment creates one `TransactionKind.ADJUSTMENT` transaction for exactly the gap, and drift then reads zero.
4. The adjustment is **never** created automatically — a test asserts nothing is written until the action is invoked. The gap usually means real unentered cash spending, and silently absorbing it destroys the signal.

---

### Task 4: Unmatched messages list

**Produces:** `UnmatchedViewModel` over `RawMessageDao.countByStatus` / a new `observeByStatus(UNMATCHED)`, and a screen.

**Behaviour to test:**

1. Lists unmatched messages newest first with sender, body, and received date.
2. Empty state is explicit.
3. Selecting a message routes to the rule editor pre-filled with that message as the sample.
4. Ignored and parsed messages never appear.

---

### Task 5: Rule editor, with a live tester

The most consequential screen in the plan.

**Produces:** `RuleEditorViewModel`, editing a `ParsingRuleEntity`.

**Behaviour to test:**

1. **Test-before-save:** running a candidate `bodyPattern` against the sample message reports each named group's capture. A pattern that captures no `amount` is reported as such, and save is disabled.
2. An invalid regex reports the error and disables save rather than throwing.
3. Saving a rule and choosing reparse re-runs history; a previously unmatched message becomes a transaction. (`ReparseUseCase` is proven idempotent in Plan 2a.)
4. **Priority ordering is visible and IGNORE-first is protected:** a test asserts the editor refuses to save an extracting rule at a priority above the lowest IGNORE rule, or at minimum warns unmissably. Getting this wrong silently double-counts every OTP-carrying payment.
5. Disabling a rule and reparsing removes the transactions it had produced, without touching transactions from other rules.

---

### Task 6: Confidence marker and filter in the ledger

**Less to build than originally written.** `CategoryDot` already takes `lowConfidence` and
renders a ring plus a content description, and `LedgerScreen` already passes it and adds the
words to the row's metadata line. Two independent signals, per DESIGN.md rule 3 — that part
is done.

**The real gap:** the ledger only treats `Confidence.LOW` as low. Plan 2a records **`MEDIUM`**
for every newly-seen merchant, which is the common case for an SMS-ingested transaction — so
today virtually every ingested row is unmarked. Fixing this is the task.

**Modifies:** `LedgerScreen` row, `LedgerViewModel`, `LedgerUiState`.

**Behaviour to test:**

1. A `MEDIUM` row carries the marker, not just `LOW` — anything other than `HIGH` is "needs attention".
2. `HIGH` carries no marker.
3. The marker remains two signals — ring *and* word. Do not replace either with colour.
4. A "needs attention" filter shows only non-`HIGH` rows and reports its own count.
5. The filter composes with the existing search rather than replacing it.
6. Confirming a merchant's category upgrades that merchant's subsequent transactions to `HIGH`.

---

### Task 7: Settings entries and wiring

**Modifies:** `SettingsScreen`, navigation.

Entries for: SMS permission status, run backfill, reparse history, unmatched messages,
parsing rules, reconciliation. Each routes to the screens above.

**Verification:** run both instrumented suites, install on a device, and walk the full
loop by hand — grant permission, backfill a real inbox, open unmatched, write a rule,
reparse, see the transaction appear. That end-to-end walk is the real acceptance test;
Plan 1 proved that green unit tests missed a crash that only appeared on device.

---

## Done when

- `./gradlew testDebugUnitTest` and both instrumented suites pass.
- The app installs and the full loop above works on hardware.
- No hardcoded colour, dp, or text style was introduced in any feature file.
- `DESIGN.md` is written from the built world — it has been outstanding since Plan 1.

## Deferred to Plan 3

AI fallback for unmatched formats, AI-drafted rules, merchant category suggestion,
redaction, API key handling, and the kill switch.
