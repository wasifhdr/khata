# Own-account transfers: settling what the message cannot say

**Status:** approved, not yet implemented
**Supersedes nothing.** Extends `2026-08-26-khata-wallet-design.md` §8 (transfers).

## 1. The problem, in numbers

Moving money between your own accounts is not spending, and the ledger currently
counts it as spending whenever it only hears one side of the movement. Measured on a
real 1,154-row ledger:

| Rows | Counted as spending | Merchant text |
|---|---|---|
| 21 | ৳84,930 | `EBL Account Transfer` |
| 36 | ৳22,555 | `EBL Skybanking MFS Transfer-bKash` |

৳107,485 of "spending" that never left the owner — larger than every genuine merchant
in the ledger combined, and enough to make every monthly total and every category
share wrong.

## 2. Why a parser cannot fix this

The same SMS body means three different things:

```
AC 112***286 is debited with BDT 5000 as EBL Account Transfer on 01-SEP-26 ...
```

That is, indistinguishably:

- a move to another of the owner's EBL accounts — not spending
- a cash withdrawal at an ATM — not spending, money moved to the Cash account
- a transfer to somebody else's EBL account — genuinely spending

`EBL Skybanking MFS Transfer-bKash` is ambiguous the same way: the owner's bKash
wallet, or a friend's. No rule, no regex and no amount threshold separates these,
because the bank does not say. Only the account holder knows.

**Consequence:** any design that tries to classify these automatically from the
message is wrong at the premise. The most a parser can do is narrow the question and
ask it rarely.

## 3. The discriminator: a second message

A movement between two accounts the owner holds produces **two** messages — the debit
from one and the credit to the other. A movement to somebody else produces **one**.
That is the only reliable signal available, and `TransferPairing` already implements
it: equal `amountMinor`, two different accounts, opposite directions, inside a
15-minute window, joined by a shared `transferGroupId`.

It already works. In the measured ledger it has formed 140 pairs covering 222 rows,
including 48 of the 84 `MFS Transfer-bKash` movements.

The gap it cannot close is the movement with no second message — an ATM withdrawal
(cash sends no SMS) or a transfer out to another person. Those two look identical and
must be told apart by the owner.

## 4. When to ask

A prompt on every unpaired debit would fire for every Uber and every mobile recharge:

| Trigger | Prompts per month |
|---|---|
| Every unpaired row | 10.7 |
| Transfer-shaped merchant text only | **1.4** |

So the trigger is gated on the merchant text matching a transfer shape — `Transfer`,
`NPSB`, `ATM`, `Withdraw`, `Cash Out`, `Send Money`. Ordinary purchases never ask.
The ATM case is still covered, because the row that turns out to be a withdrawal
reads `EBL Account Transfer`, which is transfer-shaped.

Ten notifications a month about coffee would get the feature muted in a week, and a
muted feature settles nothing. One a month is a question worth answering.

Both directions ask. An unpaired incoming transfer inflates "received" exactly as an
outgoing one inflates "spent", and both are inside the 1.4/month figure.

## 5. The three-minute wait

When ingestion writes a transfer-shaped, unpaired, `NORMAL` SMS transaction, it
schedules a one-shot job three minutes out, keyed by the transaction id.

Three minutes is measured, not guessed. Across the 140 pairs that already formed:

```
gap between the two messages:  median 23s    p90 69s    max 778s
within 3 minutes:              137 of 140    (98%)
```

At three minutes the job re-reads the row:

- **Paired since?** The partner arrived and pairing has already marked both as
  `TRANSFER`. Drop the job, ask nothing.
- **Still unpaired?** Flag the row pending and post the notification.

The 15-minute pairing window is deliberately left wider than the 3-minute prompt. A
partner arriving at minute five still pairs, which answers the question after it was
asked — so **a pending review must be cleared, and its notification retracted, when
pairing later resolves the row**. Asking is cheap; leaving a stale question that has
already been answered is not.

## 6. State

One column on `transactions`, not nullable:

```
transferReviewPending  INTEGER NOT NULL DEFAULT 0
```

Pending means flagged and unanswered. Both answers clear it, as does a late pairing.

The default of 0 is what makes this forward-only: every row already in the database is
silent from the moment the migration runs. **History is deliberately not backfilled**
(§11).

## 7. Answering

**No** — resolved straight from the notification, without opening the app, via a
broadcast receiver. The row stays ordinary spending; the flag clears. This is the
commoner answer and it costs one tap.

**Yes** — opens a settle sheet showing the amount, the merchant, the original SMS
(`OriginalMessage`, already built) and a picker for the account the money moved to or
from. The message is on the sheet because it is frequently the only thing that will
remind the owner what a three-day-old `EBL Account Transfer` actually was.

On confirm, in one database transaction:

1. write the mirror row on the chosen account — opposite direction, same amount, same
   `occurredAt`
2. adjust that account's balance by the mirror row's signed amount
3. give both rows a shared `transferGroupId` and `kind = TRANSFER`
4. clear `transferReviewPending`
5. reindex both rows for search

Step 3 is what removes the original from spending totals; the day-total query already
excludes `kind = 'TRANSFER'` and any row carrying a `transferGroupId`. Step 1 and 2
are what keep net worth correct: the ৳5,000 leaves EBL and arrives in Cash rather
than evaporating from the total. Doing it in one transaction means the ledger can
never hold a movement with one end.

## 8. The in-app path

Notifications are permission-gated, mutable, and missable. The in-app path is the
foundation and works with all of that against it:

- the Wallet tile on the hub carries a dot while anything is pending
- inside Wallet, an "N to settle" section lists the pending rows
- tapping one opens the same settle sheet as the notification's **Yes**

The notification never does anything the app cannot; it only saves a trip.

## 9. Notification permission

The app currently has no notification code at all — no channel, and no
`POST_NOTIFICATIONS` in the manifest. Both are new, and on Android 13+ (the app's
`minSdk` is 33) the permission is granted at runtime and can be revoked later.

Denied or revoked, the feature degrades to §8 and nothing is lost but immediacy.
The permission is therefore requested in context — the first time a review is
actually pending — never on first launch, where it would be a prompt for a feature
the owner has not yet met.

## 10. Testing

- **Shape matcher** — a pure function over a small corpus of merchant strings: the
  transfer shapes match, `UBER BANGLADESH` and `FOODPANDA` do not.
- **The wait** — a row paired within the window asks nothing; a row still unpaired
  goes pending.
- **Late pairing** — a row already flagged pending, then paired, clears its flag.
- **Yes** — creates exactly one mirror row, on the chosen account, with a shared
  group; both rows end `TRANSFER`; both balances move; the original leaves the
  spending total; net worth is unchanged.
- **No** — clears the flag, leaves the row spending, creates nothing.

## 11. Not included

**Backfill.** The 101 transfer-shaped unpaired rows already in the ledger — the 57
in §1 among them — and the ৳107,485 they carry, stay as they are. This was decided explicitly: the feature
starts from the day it ships. Past months stay overstated and that is accepted.

**Fees.** Where a transfer costs a fee the two sides differ by it. The mirror row is
written with the same amount as the original and can be edited afterwards. A fee
model is not worth building before one is seen to matter.

**Per-merchant memory of "No".** `EBL Account Transfer` is the same string for a
transfer to yourself and one to a friend, so remembering the answer against the
merchant would be wrong roughly every other time. Every occurrence asks on its own
merits, which is why keeping the volume at 1.4/month matters.
