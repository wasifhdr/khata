# Category Management — Design

**Date:** 2026-09-03
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-08-26-khata-wallet-design.md` §6 (module-owned tables)
**Builds on:** `2026-09-03-khata-budgets-and-insights-design.md` — budgets are keyed to categories, so deleting one touches them.

Categories are already rows in a table; `DEFAULT_CATEGORIES` only seeds them once, when
the table is empty. What is missing is every path to change them: `CategoryDao` has no
delete, Settings has no category surface, and `CategoryEntity.isSystem` is written on
all sixteen seeded rows and read by nothing.

---

## 1. Delete is soft, and history keeps its own label

Deleting a category sets `deletedAt`. Transactions filed under it **keep their
`categoryId`** — last March still says "Car & Maintenance", because that is what it
said in March.

**This does not work today, and that is most of the work.** `CategoryDao.observeAll()`
filters `deletedAt IS NULL`, and every label lookup goes through it — the Wallet
breakdown, the Insights screen, the ledger. A soft-deleted category's rows would find
no name in the map and fall through to "Uncategorised", so the history would be quietly
rewritten by exactly the mechanism meant to preserve it.

**So the read splits in two:**

| Read | Used by | Includes deleted |
|---|---|---|
| `observeAll()` | Pickers — the editor, the widget's quick entry | No |
| `observeAllIncludingDeleted()` | Labelling existing rows — breakdown, Insights, ledger | Yes |

A deleted category stops being *offered* and never stops being *readable*. Those are
different questions and the codebase currently answers both with one query.

---

## 2. Only Uncategorised is protected

`isSystem` is currently true on every seeded row, which would make all sixteen
permanent — a user with no car should be able to delete Car & Maintenance.

It narrows to one row: **Uncategorised**, which is genuinely load-bearing.
`BudgetCarryOver` finds it by uuid, and it is the fallback label for any transaction
with no category at all. A migration sets `isSystem = 0` on every seeded category
except `seed-cat-uncategorized`.

The Transfer category is *not* protected. Transfers are detected through
`TransactionKind.TRANSFER` and `transferGroupId`; that category is a label and nothing
depends on it.

---

## 3. Name and colour are editable; the colour comes from a fixed set

Renaming updates the row, and every transaction pointing at it follows — that is the
point of a foreign key.

**Colour is chosen from `LocalCategoryColors`, not a picker.** Category colours encode
data rather than taste and are deliberately not themeable (`DESIGN.md` §3.4). A free
colour would also let a user pick something that fails the contrast floor on the field.

The `icon` column stays untouched. It exists on the entity and no screen renders it, so
an icon picker would mean first giving icons somewhere to appear.

---

## 4. Deleting a category closes its budget

A category with an open budget row keeps counting toward the hub ring's total after it
is deleted, which would show a limit for something no longer on offer.

**Deleting closes the open row** at the current month's start, exactly as clearing a
limit does. Past months keep theirs, so a finished month is still judged against what
it was actually judged against.

---

## 5. Where it lives

Its own screen, reached from a Settings row — the shape `Unmatched` and `Reconcile`
already use. Settings is a list of controls; a list of records that can be added to and
removed from is a different thing, and inlining it would make the longest screen in the
app longer still.

Adding a category is a name and a colour. There is no confirmation step on add; there
**is** one on delete, because delete is the one action here that changes how existing
history reads.

---

## 6. Testing

- **A deleted category still labels its old transactions**, which is the whole claim of
  §1 and the thing the current single query gets wrong.
- **A deleted category is not offered in a picker.**
- **Uncategorised cannot be deleted**, and every other seeded category can.
- **Renaming carries** to existing transactions.
- **Deleting closes an open budget row** and leaves closed ones alone.
- **The migration** narrowing `isSystem`, as the other migration tests do it — Room
  drives it so the identity hash is restamped.
- **Not unit-tested:** the screen's layout. Verified on the device.

---

## 7. Out of scope

- Merging two categories, and reassigning a category's transactions in bulk. Both are
  real wants and neither is this; the editor already moves one row at a time.
- Sub-categories. `parentId` exists on the entity and nothing reads it.
- Icons (§3).
- Reordering. The list is alphabetical, as `observeAll` already sorts it.
