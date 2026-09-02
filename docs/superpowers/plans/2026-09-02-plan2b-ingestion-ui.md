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

## Preconditions — verify before Task 1

- [ ] `design/glass-and-motion` is committed and merged to `main`.
- [ ] `plan2a/sms-ingestion` is merged to `main`.
- [ ] `./gradlew testDebugUnitTest` passes on `main`.
- [ ] The design system's component vocabulary has been read: `core/ui/component/` and `core/ui/theme/`. **Every screen below is assembled from those components.** If a screen seems to need a new visual primitive, add it to `core/ui/component/` rather than styling in place.

## Global Constraints

- Package `com.wasif.khata`. Money is always `Long` paisa. Instants UTC millis, boundaries in `Asia/Dhaka`.
- **All styling from the design system.** No hardcoded colour, dp, or text style in any feature file.
- Async is Coroutines/Flow — no `LiveData`. DI is Hilt + KSP.
- Derived UI values are getters on `UiState`, never constructor parameters.
- Durable state (an error still true after rotation) lives in `UiState`; one-shot imperatives go through the effects `Channel`.
- Touch targets ≥ 48dp. Routinely-used controls sit in the bottom third — this app is operated one-handed.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- **No AI, no network.** Plan 3 owns that.
- Instrumented tests need a device or the `khata_test` emulator; see the Plan 1 notes in `.superpowers/sdd/progress.md` for how it is started (it lives on `D:` because `C:` is full).

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

**Modifies:** the ledger row and its ViewModel.

**Behaviour to test:**

1. A `MEDIUM`/`LOW` confidence row carries a marker; `HIGH` does not.
2. The marker is not colour-alone — it must survive greyscale, per the design system's stated rule.
3. A "needs attention" filter shows only non-`HIGH` rows and reports its own count.
4. Confirming a merchant's category upgrades subsequent transactions for that merchant to `HIGH`.

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
