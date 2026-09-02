# Plan 2b — Ingestion UI: Design Addendum

**Date:** 2026-09-02
**Status:** Design decided. Implementation deliberately deferred (see §7).
**Extends:** `2026-09-02-plan2a-sms-ingestion-design.md`
**Depends on:** Plan 2a (merged), and a settled design system

Plan 2a made ingestion work. It is entirely invisible: there is no way to grant the
SMS permission, trigger a backfill, see what failed to parse, edit a rule, or notice
that a balance has drifted. Plan 2b is the surface for all of that.

---

## 1. What 2b must make possible

Five capabilities, in the order a user meets them:

1. **Grant permission and backfill.** First run asks for SMS access, then offers to
   read the existing inbox. Without this, ingestion never starts.
2. **See drift.** When Khata's computed balance disagrees with the bank's reported
   one, say so, and say since when.
3. **See what did not parse.** Unmatched messages are stored but silent. They need a
   list, because each one is a rule waiting to be written.
4. **Fix a rule.** Rules live in the database precisely so a format change is fixed
   in-app. That is worthless without an editor.
5. **Trust a row.** Low-confidence transactions are recorded silently by design; the
   ledger needs a quiet marker and a filter so errors are findable.

## 2. The permission flow is the whole funnel

`READ_SMS` and `RECEIVE_SMS` are declared but never requested — Plan 2a has no UI.
Until this exists the app records nothing automatically, so this is the highest-value
screen in the plan, not a settings detail.

Requirements:

- Explain **before** prompting why a ledger app wants to read SMS. A cold system
  dialog on a finance app is the single most refusable prompt in Android.
- Handle permanent denial. Android stops showing the dialog after two refusals; the
  UI must then route to app settings rather than silently doing nothing.
- **Degrade honestly.** Denied permission is a legitimate state: the app still works
  as a manual ledger. Say that, rather than nagging or breaking.
- Offer backfill as a distinct, explicit action after the grant — reading years of
  messages should be something the user chose, not a side effect.

## 3. Reconciliation is a first-class surface, not a badge

`ReconciliationRepository.observeDrift()` already emits per-account drift with the
date it opened. Plan 2a established that nearly every message reports a balance, so
drift is meaningful and rare — which makes it worth showing prominently when it
happens.

- Show the account, the gap as `Money`, and the date the drift started.
- Offer one action: record the difference as an uncategorized adjustment
  (`TransactionKind.ADJUSTMENT`, already in the schema).
- Do **not** auto-correct. The gap usually means real cash spending that was never
  entered, and silently papering over it destroys the signal.

## 4. Unmatched messages, and the rule editor

The unmatched list is the raw material for rules. Each entry shows the sender, the
body, and when it arrived.

The rule editor edits `parsing_rules` rows: name, sender pattern, body pattern,
direction, kind, priority, enabled.

Two hard requirements:

- **Test before save.** The editor must let the user run a candidate pattern against
  the selected message and show what each named group captured. A regex saved blind
  is a silent parsing failure later.
- **Reparse after save.** Saving a rule offers to re-run history
  (`ReparseUseCase`), which is what turns a new rule into retroactively corrected
  data. Plan 2a proved reparse is idempotent, so this is safe to offer freely.

Priority editing must make the IGNORE-first ordering visible, because a user who
drags an extracting rule above the OTP rules silently doubles their spending.

## 5. Confidence in the ledger

Plan 1 recorded `confidence` and Plan 2a populates it: `HIGH` when the merchant was
already known, `MEDIUM` when it was newly created.

- A subtle, non-alarming marker on `MEDIUM`/`LOW` rows. This is the compensating
  control for auto-recording without a review gate — it must be findable, not loud.
- A "needs attention" filter on the ledger.
- Confirming a merchant's category upgrades its future transactions to `HIGH`, which
  is the merchant memory closing the loop.

## 6. What 2b does not do

No AI. Unmatched messages stay unmatched until Plan 3 adds the Gemini fallback and
the rule-drafting flow. The unmatched list built here is what Plan 3 hangs off.

No budgets, no net worth, no insights — Plan 4.

## 7. Why implementation is deferred

At the time of writing, a concurrent session holds **uncommitted changes to
`LedgerScreen`, `TransactionEditorScreen`, `SettingsScreen`, `WalletScreen`, and
`ContrastTest`** on branch `design/glass-and-motion` — 399 insertions, 373 deletions
— introducing a glass/motion visual system (`KhataGlass`, `FieldScaffold`, motion
tokens honouring the system Remove-animations setting).

Every screen in Plan 2b is built from that design system. Writing Compose against its
pre-rewrite form would guarantee conflicts in exactly the files under active edit, and
would produce UI that looks nothing like the finished app.

The plan therefore specifies **behaviour, state, and contracts** — what each screen
must do, what it must never do, and what its tests assert — and deliberately leaves
visual composition to be written against the settled design system.

**Preconditions for implementing 2b:**

1. `design/glass-and-motion` is committed and merged to `main`.
2. `plan2a/sms-ingestion` is merged to `main` (no conflicts as of this writing).
3. The design system's final component vocabulary is known, so screens are assembled
   from it rather than inventing parallel styling.

## 8. Resolved: minSdk 33

Raised from 30 with the user's confirmation on 2026-09-02. API 31 adds `RenderEffect`,
without which `Modifier.blur` is a silent no-op; 33 additionally unlocks AGSL
`RuntimeShader` for procedural grain. Android 11 and 12 support is no longer a
requirement. `PRODUCT.md` and the wallet design spec have been corrected; the full
rationale lives in `2026-08-28-khata-petrol-design.md` §10.

Consequence for this plan: no capability checks or fallback branches are needed in any
2b screen. Blur, shaders, and the modern permission model can all be assumed.
