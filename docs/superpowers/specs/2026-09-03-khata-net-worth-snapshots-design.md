# Net Worth Snapshots — Design

**Date:** 2026-09-03
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-08-26-khata-wallet-design.md` §11 (budgets and net worth)
**Builds on:** `2026-09-03-khata-background-work-design.md` — this is the second plan on that base.

The Wallet chart derives its line by walking daily net backwards from today's balance.
Its own comment admits the consequence: the *shape* is right, but historic absolute
values drift by whatever the ledger cannot explain. This replaces the derivation with
a stored series.

---

## 1. What a snapshot is

One row per account per Dhaka day, holding that account's closing balance.

**A snapshot is a record of what was believed that night, not a live derivation.**
Once written, a row is never rewritten. Editing a transaction from last March does not
silently rewrite last March's chart — the ledger says what happened, the snapshot says
what the balance was understood to be. That distinction is the reason the table exists
at all; a table that recomputed itself would just be a slow cache of the walk it
replaces.

`balance_snapshots` carries the standard columns — `uuid`, `createdAt`, `updatedAt`,
`deletedAt` — plus `accountId`, `dayIndex`, and `balanceMinor`. `dayIndex` is the Dhaka
day index the codebase already computes as `(occurredAt + 21600000) / 86400000`, and
`(accountId, dayIndex)` is unique: a day has one closing balance per account.

Schema goes to **v5** with a migration; the table is created empty and filled by §3.

---

## 2. One routine, three callers

Writing snapshots is a single idempotent operation: **fill in every day that has no row
yet, up to today.**

That one routine serves all three callers, which is why there is only one:

| Caller | What it means there |
|---|---|
| The migration's one-time backfill | Every day from the first transaction to today is missing |
| The nightly job | Usually one day is missing |
| A phone that was off for a week | Seven days are missing, and it does not care |

**Missing a night therefore costs nothing**, which is what makes the exact scheduling
time unimportant — and that matters, because `WorkManager` cannot promise a wall-clock
minute.

**How a day's balance is computed:** not by one query per day, which would be
days × accounts round trips. One query returns the signed daily movement per account,
and the balance walks backwards in Kotlin from each account's current balance — the
same arithmetic the chart does today, done once and written down instead of on every
render.

---

## 3. Day-one history

Snapshots would otherwise begin the day this ships, and the chart would sit empty for
ninety days — deleting a working feature and calling it an improvement.

So the migration is followed by a one-time backfill over the existing ledger, through
the §2 routine. The chart keeps every day it draws today, and from tomorrow those days
are stored rather than inferred.

**This is enqueued as work, not run inside the migration.** A migration blocks the
first database open; walking years of transactions there would stall the first frame
after an upgrade, and the failure mode of a slow migration is an app that looks hung.

**It needs no flag of its own.** The §2 routine only writes days that are missing, so
the backfill and the nightly run are the same call — and the nightly job enqueued at
every launch already makes it. On a fresh upgrade that call fills years; on every
launch after, it finds nothing to do and returns. This is the opposite of the
first-launch backfill in the previous plan, which needed `hasBackfilled` precisely
because re-reading the SMS inbox is *not* free.

---

## 4. The nightly job

A `PeriodicWorkRequest` every 24 hours, with an initial delay to the next 00:05 in
`Asia/Dhaka`, enqueued as unique periodic work with `KEEP` so relaunching does not
reschedule it.

Scheduled from `KhataApplication`. This is safe where the first-launch backfill was
not: enqueueing touches no `Context` permission check and no platform state, which is
what made an application-scope collector wrong last time
(`2026-09-03-khata-background-work-design.md` §5).

No constraints. Charging and network requirements would only delay a local write.

---

## 5. The chart reads snapshots, and the walk is deleted

`WalletViewModel.netWorthTrend` and the `observeDailyNet` query behind it are removed.
The chart sums `balanceMinor` across accounts with `includeInNetWorth`, per day, over
the same ninety-day window it uses now.

**No fallback path.** Two sources for one line is how they come to disagree, and a
fallback is the branch that gets exercised least and trusted most — it would mask a
snapshot job that had silently stopped running. If the table is empty the chart draws
nothing, which is the honest report of a job that is not running.

---

## 6. Testing

- **The migration**, following `Migration1To2Test`: a real v4 database built from the
  exported schema, migrated, then reopened through Room so its identity-hash check runs.
- **The fill routine**, which is the piece with real arithmetic: a ledger with known
  movements produces known daily balances; running it twice writes nothing the second
  time; a gap of several days is filled in one pass; an account with no transactions
  gets its opening balance rather than being skipped.
- **The chart's mapping** from snapshot rows to the series the Wallet screen draws,
  including that accounts marked out of net worth are excluded.
- **Not unit-tested:** the periodic scheduling itself, for the reason the last plan
  gave — asserting it would mean faking `WorkManager` to watch it be called. Verified
  on the device.

---

## 7. Out of scope

- **Manual assets.** Wallet spec §11 mentions snapshots covering them; no such feature
  exists, so there is nothing to snapshot. It costs nothing to add later — a manual
  asset is an account.
- Budgets, which share §11 of the wallet spec but nothing else with this.
- Month-over-month and the Insights screen. They read this series, so they follow it.
- Any change to how `currentBalanceMinor` is maintained. Snapshots read it; they do not
  become a second source of it.
