# Khata — Owed & Loans Redesign Spec

**Date:** 2026-09-30
**Status:** Draft for user review
**Amends:** `2026-08-26-khata-wallet-design.md` (transactions & owed ledger), `2026-09-04-own-account-transfers-design.md` (§7 settle sheet)

---

## 1. Problem

The first iteration of the Owed ledger modeled person-to-person money through six enum values on `TransactionKind` (`LENT`, `LENT_RETURNED`, `BORROWED`, `BORROWED_RETURNED`, `REIMBURSEMENT`, `COVERED_FOR_SOMEONE`) chosen from a `"What kind"` dropdown in `TransactionEditorScreen`. Four things failed in practice:

1. **Reclassifying an existing transaction silently dropped the change:** `TransactionRepositoryImpl.save()` evaluated `kind = existing?.kind ?: draft.kind`, which kept `NORMAL` on every edit of an existing row even after the user picked a debt kind and typed a `counterparty`.
2. **Six enum values for one signed number:** The user had to choose between six phrasings in a dropdown even though the ledger only ever adds `+owedMinor` on money going out or `-owedMinor` on money coming in.
3. **No bill splitting and no pure IOUs:** Covering a bill was all-or-nothing (`COVERED_FOR_SOMEONE` removed 100% of the transaction from spending and assigned 100% to the other person), and there was no way to record a bill someone else paid on your behalf where no money moved in your own accounts yet.
4. **Read-only `OwedScreen` and no transfer-settle bridge:** Person rows on `OwedScreen` could not be tapped to inspect their history or settle up, `OwedScreen` had no `+` entry button, and `SettleTransferSheet` offered no way to settle an unpaired transfer as money sent to or received from a person.

---

## 2. Data Model & Arithmetic

### 2.1 Single Table (`transactions`) with `owedMinor` and `TransactionKind.IOU`

`transactions` remains the single source of truth. No second table is introduced.

* **`TransactionEntity` changes (Schema v14 → v15):**
  * Add `owedMinor: Long = 0` (`INTEGER NOT NULL DEFAULT 0`).
  * `counterparty: String? = null` remains the person's name.
  * When `owedMinor > 0` and `counterparty` is blank/null, the row appears under **Not named yet** on `OwedScreen` until named.
* **`TransactionKind` simplification:**
  * Remove `LENT`, `LENT_RETURNED`, `BORROWED`, `BORROWED_RETURNED`, `REIMBURSEMENT`, `COVERED_FOR_SOMEONE`.
  * Add `IOU` — a debt where someone else paid on your behalf and no money moved in or out of your own accounts yet (`accountId = 0L`).
  * Keep `NORMAL`, `TRANSFER`, `LOAN_DISBURSEMENT`, `LOAN_REPAYMENT`, `FEE`, `ADJUSTMENT`.
* **Migration `MIGRATION_14_15`:**
  * `ALTER TABLE transactions ADD COLUMN owedMinor INTEGER NOT NULL DEFAULT 0`
  * Existing rows with `kind IN ('LENT', 'COVERED_FOR_SOMEONE', 'BORROWED_RETURNED')` are migrated to `owedMinor = amountMinor, direction = 'DEBIT', kind = 'NORMAL'`.
  * Existing rows with `kind IN ('LENT_RETURNED', 'REIMBURSEMENT', 'BORROWED')` are migrated to `owedMinor = amountMinor, direction = 'CREDIT', kind = 'NORMAL'`.

### 2.2 Balance, Spending, Income, and Owed Rules

| Case | `kind` | `direction` | Account Balance Effect | Spending / Income Effect | Owed Effect (`counterparty`) |
|---|---|---|---|---|---|
| Ordinary spend | `NORMAL` | `DEBIT` | `-amountMinor` | Spend `+amountMinor` | `0` (`owedMinor = 0`) |
| Split bill you paid | `NORMAL` | `DEBIT` | `-amountMinor` | Spend `+(amountMinor - owedMinor)` | `+owedMinor` (they owe you) |
| Full loan / bill covered for them / paying them back | `NORMAL` | `DEBIT` | `-amountMinor` | Spend `0` (`owedMinor == amountMinor`) | `+owedMinor` |
| Ordinary income | `NORMAL` | `CREDIT` | `+amountMinor` | Income `+amountMinor` | `0` (`owedMinor = 0`) |
| Loan returned / reimbursement / borrowed into account | `NORMAL` | `CREDIT` | `+amountMinor` | Income `+(amountMinor - owedMinor)` (`0` when full) | `-owedMinor` |
| They paid for you (pure IOU) | `IOU` | `DEBIT` | `0` (no account moved) | Spend `+amountMinor` | `-owedMinor` (you owe them) |

* **Why `IOU` counts as spending when incurred:** When a friend pays ৳500 for your dinner on Tuesday (`kind = IOU`, `amountMinor = 500`, `owedMinor = 500`), that ৳500 was consumed on Tuesday. When you repay them on Friday from bKash (`kind = NORMAL`, `direction = DEBIT`, `amountMinor = 500`, `owedMinor = 500`), Friday's row contributes `amountMinor - owedMinor = 0` to spending. Your spending total and category breakdown count the dinner once, on the day it happened.
* **Account balance isolation for `IOU`:**
  * `TransactionRepositoryImpl.save()` and `delete()` only call `accountDao.adjustBalance()` when `kind != TransactionKind.IOU`.
  * `TransactionDao.dailyMovementByAccount()` and `allForAccount()` exclude `kind = 'IOU'`.

### 2.3 DAO Queries (`TransactionDao`)

* **`observeOwedByPerson()`:**
  Groups active rows with `owedMinor > 0` and non-blank `counterparty` by `LOWER(TRIM(counterparty))`:
  ```sql
  SELECT TRIM(counterparty) AS name,
         SUM(CASE WHEN kind = 'IOU' THEN -owedMinor
                  WHEN direction = 'DEBIT' THEN owedMinor
                  ELSE -owedMinor END) AS netMinor,
         COUNT(*) AS entries
  FROM transactions
  WHERE deletedAt IS NULL
    AND counterparty IS NOT NULL AND TRIM(counterparty) != ''
    AND owedMinor > 0
  GROUP BY LOWER(TRIM(counterparty))
  HAVING netMinor != 0
  ORDER BY ABS(netMinor) DESC
  ```
* **`observeUnnamedOwed()`:**
  Selects active rows where `owedMinor > 0` and `(counterparty IS NULL OR TRIM(counterparty) = '')`, ordered by `occurredAt DESC, id DESC`.
* **`observeOwedEntriesForPerson(name: String)`:**
  Returns all active rows where `owedMinor > 0 AND LOWER(TRIM(counterparty)) = LOWER(TRIM(:name))` ordered by `occurredAt DESC, id DESC`, powering the person detail sheet.
* **`observeRecentCounterparties(limit: Int)`:**
  Returns the most recently used distinct `TRIM(counterparty)` values (ordered by `MAX(occurredAt) DESC`, `LIMIT :limit`) for quick-pick person chips in the editor and settle sheet.
* **Spending aggregates (`observeTotalMinorBetween`, `observeSpendByCategory`, `compareCategorySpend`, `observeTopMerchants`, `observeDayTotals`):**
  * For `DEBIT`: include `kind IN ('NORMAL', 'IOU')` (with `transferGroupId IS NULL` and `amountMinor > owedMinor OR kind = 'IOU'`), summing:
    ```sql
    CASE WHEN kind = 'IOU' THEN amountMinor ELSE MAX(amountMinor - owedMinor, 0) END
    ```
  * For `CREDIT` in `observeTotalMinorBetween`: include `kind = 'NORMAL'` (`transferGroupId IS NULL`), summing `MAX(amountMinor - owedMinor, 0)`.

---

## 3. UI & Interaction Design (Petrol + Glass)

Every surface follows `2026-08-28-khata-petrol-design.md`: `FieldScaffold`, `CollapsingTopBar`, `KhataGlass`, `GlassChoice` (`secondaryContainer` when selected, `KhataGlass(refracts = false)` when unselected), `AmountKeypadDialog` for money entry, Fraunces tabular numerals (`AmountTextStyle`), and direction/status always expressed in words.

### 3.1 `TransactionEditorScreen`

1. **Top mode row (segmented `GlassChoice` pills):**
   * **Spent** (`direction = DEBIT, kind = NORMAL`)
   * **Received** (`direction = CREDIT, kind = NORMAL`)
   * **They paid** (`direction = DEBIT, kind = IOU`) — shown when creating a new row or editing an existing `IOU` row (hidden when editing an SMS/widget row that already moved a real account).
2. **Account chips:**
   * Shown for **Spent** and **Received**; hidden when **They paid** (`IOU`) is selected (the `ContextHeader` subline reads `"Owed only · no account moved"`).
3. **"With someone" section (replaces the old `"What kind"` dropdown):**
   * **Quick-pick person chips (`FlowRow` of `EditorChip`s):** Up to 6 recent `counterparty` names from `observeRecentCounterparties(6)`. Tapping a chip fills or clears `counterpartyInput`.
   * **Text field (`OutlinedTextField`):** Labelled `"With whom"` (placeholder `"Optional"` for Spent/Received; required to enable Save when **They paid** is selected).
4. **Share selector (shown when **Spent** has a non-blank `counterpartyInput` or `owedMinor > 0`):**
   * Two `GlassChoice` pills side-by-side:
     * **All of it** — `owedMinor = amountMinor` (full loan, paying them back, or covering 100% of their bill).
     * **Split · `<owedAmount>`** — defaults to `amountMinor / 2` when first switched to Split; tapping opens `AmountKeypadDialog(title = "Their share")` to enter any exact amount (`0 < owedMinor < amountMinor`).
   * For **Received** with a person and **They paid** (`IOU`), `owedMinor` automatically equals `amountMinor`.

### 3.2 `OwedScreen` & Person Detail Sheet

1. **`OwedScreen`:**
   * `FieldScaffold` + `CollapsingTopBar(heading = "Owed", subline = ...)`.
   * **Owes you** and **You owe** sections: each `PersonRow` is clickable (`minTouchTarget` height) and opens `PersonOwedSheet` for that person.
   * **Not named yet** section: rows with `owedMinor > 0` and blank `counterparty`; tapping opens `TransactionEditorScreen` to name the person.
   * **Bottom-right `+` FAB:** 58dp circle on `Brush.linearGradient(KhataPalette.heroStops)` in the thumb arc (matching `LedgerScreen`), opening `TransactionEditorScreen` (`KhataRoutes.EditorNew`).
2. **`PersonOwedSheet` (`ModalBottomSheet`):**
   * **Header:** Person's name (`titleLarge`), direction and net figure in words (`"owes you ৳1,500"` or `"you owe ৳500"`), and entry count.
   * **Entry list:** Active owed rows for this person, newest first. Each row shows what it was (`merchantRaw` or `note`, falling back to `"Lent"`, `"Split (৳X of ৳Y)"`, `"They paid"`, or `"Received"`), date in `Asia/Dhaka`, and the signed tabular amount (`+৳500` or `−৳500`). Tapping any row opens `TransactionEditorScreen` to edit it.
   * **Settle up bar at the bottom:**
     * `FlowRow` of account `EditorChip`s (`Cash` preselected first, then `bKash`, `EBL`, etc.).
     * Full-width Petrol hero-gradient pill: **"Settle `<netAmount>`"** (`"Receive ৳1,500 into Cash"` when they owe you, or `"Pay ৳500 from Cash"` when you owe them).
     * Tapping writes a settlement transaction (`CREDIT` if they owed you, `DEBIT` if you owed them, `amountMinor = abs(netMinor)`, `owedMinor = abs(netMinor)`, `counterparty = person.name`, `note = "Settled up"`) via `TransactionRepository.save()`, zeroing the net balance and closing the sheet.

### 3.3 `SettleTransferSheet` (Wallet `TO SETTLE` Integration)

In `SettleTransferSheet`, alongside **"It moved to one of my accounts"** and **"It left my accounts"**:
* Add a **"With a person (Owed)"** section:
  * Shows recent person chips (`observeRecentCounterparties(6)`) + an `OutlinedTextField` for a name and an action button **"Record as owed with `<name>`"**.
  * Confirming calls `TransactionRepository.settleAsOwed(transactionId, counterparty)`: sets `counterparty = name.trim()`, `owedMinor = amountMinor`, `kind = NORMAL`, and `transferReviewPending = false`, then reindexes the row for search.

---

## 4. Testing

* **Migration test (`Migration14To15Test`):** Verifies `owedMinor` column addition and conversion of existing `LENT`, `COVERED_FOR_SOMEONE`, `BORROWED_RETURNED`, `LENT_RETURNED`, `REIMBURSEMENT`, and `BORROWED` rows into `NORMAL` rows with `owedMinor = amountMinor` and preserved net Owed balances.
* **DAO & Repository tests (`OwedLedgerTest`, `OwedMoneyTest`, `TransactionRepositoryImplTest`):**
  * Reclassifying an existing `NORMAL` SMS row to have `counterparty` + `owedMinor` persists both fields and surfaces the row in `observeOwedByPerson()`.
  * Splitting a ৳1,000 debit (`owedMinor = 400`) deducts ৳1,000 from the account balance, counts ৳600 in monthly/category/day spending, and adds `+৳400` to the person's Owed net.
  * Recording a pure `IOU` (`kind = IOU`, `amount = 500`, `owed = 500`) leaves account balances unchanged, counts ৳500 in spending, and adds `−৳500` to the person's Owed net; settling it with a ৳500 `DEBIT` (`owed = 500`) moves the account balance by `−৳500`, adds `0` to spending, and nets the person's Owed balance to `0`.
  * `settleAsOwed(transactionId, counterparty)` clears `transferReviewPending`, sets `owedMinor = amountMinor` and `counterparty`, and removes the row from spending/income totals.
* **ViewModel tests (`TransactionEditorViewModelTest`, `OwedViewModelTest`, `SettleTransferViewModelTest`):**
  * Mode switching (`Spent`, `Received`, `They paid`), split share updates, person chip selection, and one-tap settle up from `OwedViewModel`.
