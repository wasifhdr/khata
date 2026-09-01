# Plan 2a — SMS Ingestion: Design Addendum

**Date:** 2026-09-02
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-08-26-khata-wallet-design.md` §7–§10
**Corpus:** `sms-corpus.md` — 40 real messages, the source of truth for rules and tests

The original spec described ingestion in principle. Real messages surfaced five
things it did not anticipate. This addendum records only what is new or changed.

---

## 1. Scope split

Plan 2a is **headless**. A second session is concurrently reworking the design
system, so 2a touches no Compose file and no theme token.

**In 2a:** schema v1→v2, the rule engine, ingestion sources, merchant resolution,
transfer pairing, reconciliation, backfill, corpus tests.

**Deferred to 2b** (after the design system settles): rule-editor screen,
unmatched-messages screen, confidence markers in the ledger, settings entries.

Consequence: 2a is entirely JVM- and Robolectric-testable. No instrumented tests,
so no emulator contention with the other session either.

---

## 2. IGNORE rules run first, at the highest priority

**This is the single most important rule in the module.**

OTP messages carry a real amount *and* a real merchant:

```
Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523
The OTP request is for a transaction at foodibdcom ... BDT 232.00 ... use OTP: 823876
```

Parsed naively these become perfect-looking transactions. The genuine payment then
arrives separately, and the amount is recorded twice. Because Khata auto-records
with no review gate, nothing would catch it except the balance reconciliation
drifting — long after the fact.

Therefore the rule engine evaluates rules **strictly in priority order**, and every
`kind = IGNORE` rule sorts above every extracting rule. A message that matches an
IGNORE rule is stored in `raw_messages` with `status = IGNORED` and never produces
a transaction.

Known IGNORE classes in the corpus: OTP requests (both providers), bKash account
binding confirmations, bKash loan-terms notices, EBL standing-instruction status.

**Testing requirement:** every IGNORE sample in the corpus gets an explicit test
asserting the pipeline produced **zero** transactions. A rule that merely fails to
match is not the same as a rule that deliberately ignores, and only the second is
safe.

---

## 3. Deduplicate on provider transaction id

bKash announces one payment twice:

```
Payment of Tk 564.11 is being reserved for Uber Bangladesh Ltd-Uber. ... TrxID DHA3BH4G8R
Payment of Tk 564.11 to Uber Bangladesh Ltd-Uber is successful.      ... TrxID DHA3BH4G8R
```

Identical `TrxID`. Without dedup every Uber ride, every reserved-then-captured
payment, is double counted.

`transactions` gains **`providerTxnId TEXT`** with a unique index (nullable — manual
and EBL transactions have none; SQLite permits multiple NULLs in a unique index).
On write, a parsed transaction whose `providerTxnId` already exists **updates** the
existing row rather than inserting. The later message wins, because "is successful"
is more authoritative than "is being reserved".

---

## 4. Normalising what varies

### Amounts — four formats, one matcher

| Form | Example |
|---|---|
| Space-separated | `BDT 60` |
| No space | `BDT5000` |
| Thousands separators | `Tk 2,600.00` |
| Period after unit | `Tk.750.00` |

One `parseAmount` helper handles all four and returns `Money`. It strips the unit
(`BDT`/`Tk`, optional trailing period), strips separators, and reuses `Money.parse`.

### Datetimes — two formats

- EBL: `01-SEP-26 06:46:08 PM` — month case varies (`SEP`, `Aug`), 2-digit year
- bKash: `21/08/2026 12:35` — 4-digit year, 24-hour, no seconds

Both parse to UTC epoch millis, interpreted in `Asia/Dhaka`. The body datetime wins
over the SMS received timestamp whenever present.

### Account masks — same account, two maskings

`115***352` (transfer messages) and `115**9352` (card messages) are the same
account. Both expose the **last three digits**, which is therefore the matching key.
`accounts.smsIdentifiers` holds the tail; matching normalises by stripping non-digits
and comparing the final three.

---

## 5. Accounts and cards, revised

The seed assumed one EBL account. There are two, each with its own card:

| Account | Name | Masks seen | Card |
|---|---|---|---|
| ends `352` | **EBL Salary** | `115***352`, `115**9352` | `539280**3432` |
| ends `286` | **EBL Student** | `112***286`, `112**0286` | `452017**1835` |
| bKash | bKash | — | — |
| Cash | Cash | — | — |

Card purchases and ATM withdrawals name the card but also state the owning account
(`Your A/C 115**9352`), so routing keys off the account tail, not the card. Cards are
not modelled as separate accounts in 2a.

**Migration note:** the existing seed row named `EBL` must become one of these two
rather than being duplicated, or the user's existing transactions lose their account.
The migration renames the existing `seed-acc-ebl` row and inserts one new account.

---

## 6. Loans are a liability, not income

bKash Digital Loan raises the balance but creates debt:

```
You have received Digital Loan Tk 900.00 from City Bank. Balance Tk 897.98. TrxID ...
You have received Loan of Tk 900.00 ... first repayment of TK 309.28 is due on 28/09/2026.   [IGNORE]
```

Recording the disbursement as plain income would make a borrowing month look like a
good month and overstate net worth by the outstanding balance.

`transactions` gains **`kind`** (`NORMAL` / `TRANSFER` / `LOAN_DISBURSEMENT` /
`LOAN_REPAYMENT` / `FEE` / `ADJUSTMENT`). A `LOAN_DISBURSEMENT` still moves the
account balance — the money genuinely arrived — but is excluded from income
aggregates, and the plan that builds net worth subtracts outstanding principal.

The second message (loan terms and repayment schedule) is an IGNORE: it restates the
same disbursement, carries no balance, and would otherwise double-count.

Cashback (`Congratulations! You have received Cashback Tk 2.90`) is ordinary income,
not a loan artefact.

---

## 7. Transfer pairing

Two mechanisms, as originally specced, now with real evidence:

**Explicit** — the rule's `kind` classifies known phrasings directly:
`Own Account Transfer`, `EBL Account Transfer`, `EBL Skybanking MFS Transfer-bKash`,
`AC TRANSFER THROUGH EBL CONNECT`, `NPSB FUND TRANSFER`, `Cash WD` (→ Cash account),
bKash `received deposit from iBanking`.

**Pairing heuristic** — equal `amountMinor`, two different accounts, one DEBIT and
one CREDIT, within a **15-minute window**, grouped under a shared `transferGroupId`.

The corpus validates this directly: `Own Account Transfer` pairs debit `…286` at
`18-AUG-26 01:20:19 PM` against credit `…352` at `01:20:20 PM` — one second apart,
identical amount. Cross-institution pairs (EBL debit → bKash deposit) fall in the
same window.

Neither leg counts as income or expenditure in any aggregate.

---

## 8. Reconciliation has ground truth almost always

Nearly every message in the corpus states a balance. The original spec treated
reported balance as an occasional bonus; it is in fact near-universal, which makes
reconciliation the primary correctness mechanism rather than a fallback.

Every parse writes `reportedBalanceMinor` and `reportedBalanceAt` to the account.
A mismatch against the computed balance surfaces the gap and the date it opened.

---

## 9. Backfill

On first run, and on demand, the whole SMS inbox is scanned through the pipeline as
a chunked `WorkManager` job. Idempotent by `raw_messages` unique key
(`sender`, `bodyHash`, `receivedAt`), so re-running never duplicates.

Raw messages are retained permanently, so `reparseAll()` re-runs the current rule set
over history and retroactively repairs anything a missing rule got wrong.

---

## 10. Schema v2

New tables: `raw_messages`, `parsing_rules`.

Changed `transactions`: `+ providerTxnId` (unique index, nullable), `+ kind`.
Changed `accounts`: second EBL row seeded, existing `EBL` row renamed.

The v1→v2 migration is the first real migration in this project. It is written and
tested against the exported `1.json` rather than by `fallbackToDestructiveMigration`.

---

## 11. Out of scope

AI fallback (Plan 3) — 2a is rules-only. Unmatched messages are stored with
`status = UNMATCHED` and wait. No Gemini, no network, no API key.

All UI (Plan 2b). All budgets, net worth, and insights (Plan 4).
