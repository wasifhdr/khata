# Budgets and Insights — Design

**Date:** 2026-09-03
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-08-26-khata-wallet-design.md` §11 (budgets) and §15 (the Insights screen)
**Builds on:** `2026-09-03-khata-net-worth-snapshots-design.md`

Two features, designed together because they meet: budgets are limits per category,
and the screen that shows spending per category is where a limit is worth seeing.
They ship as two plans on one design.

---

## 1. Budgets are per category, and versioned

Today a single `monthlyBudgetMinor` preference drives one ring on the hub. It cannot
answer the question budgets exist for — *which* spending went over.

`category_budgets` holds one row per category per period: `categoryId`, `limitMinor`,
`effectiveFrom`, `effectiveTo` (nullable), plus the standard `uuid`/`createdAt`/
`updatedAt`/`deletedAt`. Schema goes to **v6**.

**Changing a limit closes the current row and opens a new one.** `effectiveTo` is set
on the old, a new row starts the next day. Reviewing March then shows what the limit
actually was in March.

This is the whole reason for the table's shape, and it is worth being blunt about the
alternative: a single editable limit per category silently rewrites history. Set a
tighter grocery budget in September and last March retroactively becomes a month you
overspent. The ledger is immutable by design; a budget that judges it must be too.

**The global preference is retired.** It migrates to a row against the seeded
`Uncategorized` category so nothing set is lost, then `monthlyBudgetMinor` and its
Settings field are removed. The hub ring draws the **sum of active category limits**
against the month's spend — one source of truth rather than two numbers that can
disagree about the same month.

**A month with no limits set draws no ring**, exactly as no budget does today. Absent
is not zero.

---

## 2. Which limit applies to a month

A category's limit for a month is the row whose `effectiveFrom` is on or before the
month's start and whose `effectiveTo` is null or after it. Boundaries are Dhaka, like
every other period in this app.

**`effectiveFrom` is always the start of the current Dhaka month, and editing within
that same month updates that row rather than stacking a second one.** So setting a
grocery limit on the 14th applies to the whole of that month — which is what "my
September budget" means — and revising it on the 20th revises September, not a new
period starting the 20th. A limit that took effect mid-month would make "spent 60% of
your budget" a statement about two different budgets.

**Only the current month is editable this way. Closed months are not**, which is the
distinction §1 rests on: a budget is an intention you may revise while the month is
still running, and a fact about the month once it has ended.

**Overlapping rows are prevented on write, not resolved on read.** Closing the old row
in the same transaction as opening the new one is what keeps "which row applies" a
lookup rather than a judgement call. A reader that had to choose between two candidate
rows would be a reader that could choose differently from the writer.

---

## 3. Progress is measured against spending, not movement

Budget progress counts the same thing the category breakdown already counts: `DEBIT`
rows, excluding transfers and excluding the kinds that are not spending — lending,
repayment, and covering a bill for someone. Those exclusions already exist in
`observeSpendByCategory`, and budgets reuse that definition rather than inventing a
second one.

If the two ever disagree, the screen shows a category as over budget while the
breakdown above it shows less spent, and neither number is wrong on its own.

---

## 4. The Insights screen

Its own screen, reached from the Wallet's link row beside Owed and Ledger. Wallet is
the several-times-daily glance; Insights is the sit-down monthly review, and the
wallet spec names those as different situations.

Three sections, each one query:

| Section | What it says |
|---|---|
| **This month against last** | Total spend, the change, and its direction |
| **By category** | Spend per category, its change since last month, and progress against a limit where one is set |
| **Top merchants** | The merchants the most money went to this month |

**Month-over-month needs both months in one pass**, not two queries the UI subtracts:
a category that appeared this month and one that vanished since last month are both
real answers, and a join is what keeps them from being dropped.

**A first month has nothing to compare against.** Change is absent rather than shown
as +100%, which is what a subtraction from zero would produce and would be read as a
fact about spending rather than about missing data.

---

## 5. What Insights does not get

No new chart. The three sections are text, bars and existing components — the
category bars already exist on the Wallet screen, and budget progress is the same bar
with a limit marker. A fourth hand-drawn chart would be a fourth thing to keep
consistent for a screen whose job is comparison, which text and bars do better.

---

## 6. Testing

- **Which limit applies to a month**, including a limit changed mid-history: March
  reads March's number and September reads September's. This is the piece that would
  silently produce plausible-but-wrong answers.
- **Closing and opening on edit**: setting a new limit leaves exactly one active row
  and one closed one, with no gap and no overlap.
- **Month-over-month**: a category present in both months, one new this month, one
  gone since last, and a first month with no prior at all.
- **Top merchants**, ordered and limited, ignoring soft-deleted rows.
- **The migration**, v5 → v6, as `Migration4To5Test` does it — Room drives it so the
  identity hash is restamped.
- **Not unit-tested:** the screen's layout. Verified on the device.

---

## 7. Out of scope

- Budget notifications or warnings when a limit is approached. Nothing asks for them,
  and a budget that interrupts is a budget that gets turned off.
- Rollover of unspent budget between months.
- Budgets on anything but categories — no per-account or per-merchant limits.
- The shared spine, AI, and backup. Unrelated, and still the remaining wallet items.
