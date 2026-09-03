# Cash Widget and Quick Entry — Design

**Date:** 2026-09-03
**Status:** Design decided. Implementation plan not yet written.
**Extends:** `2026-08-26-khata-wallet-design.md` §15 (widget target: three taps)
**Settles:** `2026-08-28-khata-petrol-design.md` §8 and §11 — "the decimal keypad" and
"the three-tap widget flow", both recorded there as *not yet designed*.

SMS captures every digital transaction on its own. Cash is the half no message can
see, and the product's claim — that the user never does bookkeeping — holds only if
recording a cash spend is faster than deciding not to bother. This is that surface.

---

## 1. The flow

The widget face carries two targets, `−` and `+`. **The direction is chosen on the
home screen, before anything opens.** Tapping either launches a quick-entry sheet
that already knows whether this is money out or money in, so the sheet needs no
Spent/Received control and the editor's two-state pill does not reappear here.

Three taps past the digits: **category → Save → done.** The sheet closing is the
confirmation; nothing else on a home screen would show a toast to.

Direction cannot be corrected inside the sheet. A mis-tap is one dismiss and one
re-tap, which is cheaper than a permanent control that exists for a rare mistake.

---

## 2. The widget face is RemoteViews, not Glance

`PRODUCT.md` named Glance in the stack at planning time. The face that got designed
carries no live data — two buttons — so Glance would buy a dependency and a second
theming dialect for re-rendering nothing re-renders.

**Glance would not have given us `KhataTheme` either.** It has its own
`ColorProviders`; the palette gets re-expressed for the widget under either choice.
Its one genuine advantage is that it observes DataStore itself. §3 buys that back in
about ten lines.

`PRODUCT.md`'s stack line names Glance and is wrong as of this decision; the
implementation amends it to RemoteViews carrying this reason. Glance earns its place
the day the face grows live data — a cash balance, recent-spend shortcuts — and not
before.

**The face itself** (revised 2026-09-03, after seeing it on a launcher): a 2×1 cell,
two equal targets split by a hairline rule — a red `−` and a green `+`, and nothing
else. No labels.

The words were in the first draft to satisfy `DESIGN.md` §1.3, colour never being the
only signal. They are not needed: **`−` and `+` are shapes, and the shape is what
carries the meaning here.** Colour reinforces it. The words survive as
`contentDescription`, for the one reader that cannot see a minus sign.

Red and green are **new colours**, and they are deliberately not the ledger's
convention — that one gives credits the accent and leaves debits paper, which is
tuned for finding a credit while scrolling a list. A pair of controls being offered
is a different question from a row being read. They live in `core/ui/theme` as
`moneyOut`/`moneyIn`, **fixed rather than tuned**, exactly as category colours are:
they encode which way money moves, not taste. An accent-derived "in" would turn
marigold the moment the tuner did. Both clear 7:1 on every shipped ground.

**Sizing:** `targetCellWidth`/`Height` of 2×1, with `minWidth` at 110dp. The first
draft set 180dp and the launcher rounded the widget up to three columns regardless of
`targetCellWidth` — `minWidth` is the binding constraint, not the target.

---

## 3. The widget follows the tuner

**A widget is not exempt from the no-hardcoded-colour rule.** `composeScheme(spec)`
is a plain function, not a composable, and `ThemeSpec` and `KhataPalette` are plain
data — so the provider resolves exactly the colours the app resolves and pushes them
as ints (`setColorStateList`, `setTextColor`, background tint, all available well
below `minSdk 33`).

This is distinct from `themes.xml`, which does hardcode `#061214`. A window
background is resolved before any of our code runs. A widget update is our code
running, so the same exemption does not apply.

**Two paths keep it current:**

1. `onUpdate` reads preferences itself — covers the widget being added, a reboot, and
   the launcher's own refreshes. It runs on the main thread with a short window, so
   it reads through `goAsync()`, never blocking.
2. An application-scope collector pushes an update when `ThemeSpec` changes
   distinctly. The theme can only be changed from inside Settings, so the app process
   is alive whenever it changes and this cannot miss one. It sits beside the existing
   seeder launch in `KhataApplication`.

`@AndroidEntryPoint` on an `AppWidgetProvider` injects `PreferencesRepository`
directly; `SmsReceiver` already proves the pattern on a `BroadcastReceiver`.

**The layout XML therefore carries no colour at all** — not even a placeholder. Every
fill, tint and text colour arrives from the provider. `DESIGN.md` §1.1 bans colour
literals outside `core/ui/theme` and enforces it with a test; a layout literal would
be correct in one of the tuner's 512 combinations. The surface follows the tuner; the
two glyph colours do not, for the reason given in §2.

**One implementation constraint worth recording:** RemoteViews inflates only a
whitelisted set of view classes, and a bare `<View>` is not among them — it fails with
"Class not allowed to be inflated", which a launcher surfaces as the useless message
"Can't load widget". The hairline separator is a `FrameLayout` for that reason and no
other.

**What cannot cross into the widget is blur and the mesh** — no `RenderEffect`, no
AGSL grain. The face gets the tuner's ground, petrol and accent as flat fills and no
glass. This costs nothing real: glass on a launcher would be refracting the user's
wallpaper, which was never the backdrop the system was designed against.

---

## 4. The sheet is a transparent activity

A `@AndroidEntryPoint` activity with a transparent window, hosting Compose. It gets
the whole petrol system unchanged — the tuner's palette, the real fonts, `tnum`
numerals, the motion contract — because it is the same `KhataTheme` the app uses.

**It needs no splash gate.** `MainActivity` holds the splash until DataStore's first
emission, because a palette flash and a wrong start destination are both
unrecoverable. Here the window is already transparent, so rendering nothing for one
frame is invisible. The sheet composes when preferences arrive and not before.

- Dismiss on scrim tap and on back. Both `finish()`.
- `excludeFromRecents`, so a quick entry does not become a card in the recents list.
- Enter and exit run on `LocalMotion`, which already honours the system "Remove
  animations" setting. The window itself carries no activity transition.
- `windowSoftInputMode="adjustResize"` — the trap `AndroidManifest.xml` already
  documents for `MainActivity`. Without it the window pans and the amount leaves the
  screen when the IME opens for a note.

---

## 5. Focus decides the lower half

Top to bottom: **amount, category chips, note, Save, then the input surface.**

The lower half belongs to whichever field has focus:

| Focus | Lower half |
|---|---|
| Amount (the default on open) | The custom decimal keypad |
| Note | The system IME, keypad withdrawn |

This is the arrangement `2026-08-28-khata-petrol-design.md` §8 described for the
editor and deferred. Designing it here costs nothing extra and means notes need no
new layout idea.

**Merchant is deliberately absent.** For cash at a shop the note carries it, and a
second free-text field on a speed surface is a field that gets skipped.

---

## 6. The keypad

Lands in `core/ui/component/`, not in the widget feature, because the editor adopts
it later — that is the deferred pass in §8 of the petrol spec, and building it
somewhere the editor cannot reach would mean building it twice.

Keys: `0`–`9`, `.`, backspace. No clear-all; backspace held is not a gesture worth
supporting for a four-digit sum.

**Input rules, as a pure function over the current string.** This is the module's
only real logic and it must be testable without Compose:

- A leading `0` is replaced, not appended to — `0` then `5` is `5`, not `05`.
- At most one `.`. A second is ignored rather than swallowed silently into a
  reformat.
- At most two digits after the `.`. Money is `Long` paisa; a third digit has nowhere
  to go.
- `.` on an empty string yields `0.` — `.56` is a legitimate sum the parser already
  accepts, but the field should read as a number while being typed.
- Backspace on an empty string is a no-op, not a crash and not a negative.

The string is parsed with the existing `Money.parse`. No second parser.

Touch targets are ≥ 48dp and the keypad occupies the bottom of the screen by
construction, which is the one-handed requirement satisfied rather than asserted.

---

## 7. Category order is recency-first

One new query: distinct categories used on the Cash account, most recently used
first, then the remainder in their seeded order. Category is optional — Save is
enabled by amount alone.

§15 of the wallet spec asks for "recent *and* frequent". Recency alone is what ships;
it is a single `MAX(occurredAt)` and it converges to the same answer for a user whose
cash spending is a handful of repeated categories. Marked `ponytail:` at the query,
with frequency as the named upgrade if the ordering ever feels wrong.

---

## 8. A widget entry is distinguishable

`TransactionRepositoryImpl.save()` currently writes `TransactionSource.MANUAL` for
every new row, so a widget entry would be indistinguishable from a hand-typed one and
`TransactionSource.WIDGET` would stay the unused enum constant it has been since
Plan 1.

**`TransactionDraft` gains `source: TransactionSource = MANUAL`**, and `save()`
honours it for new rows. The default keeps every existing call site unchanged — the
same pattern `kind` already uses, and for the same reason.

Everything else about the write is ordinary: `occurredAt` is now, `kind` is `NORMAL`,
the account is the seeded Cash account resolved by `type == CASH` off
`observeAccounts()`. No new repository method. Confidence lands `HIGH` through the
existing rule, correctly — the user typed it.

---

## 9. Failure

Save failure leaves the sheet open with durable error text under the amount, and the
typed amount intact. The sheet must never close on a failed write: the user is
standing in a shop and the whole contract is that the record exists.

`DataError` is mapped at the repository boundary as everywhere else. The widget face
has no error state — it launches an activity, which cannot fail meaningfully.

---

## 10. Testing

- **The keypad reducer**, as a plain JVM test over the rules in §6. Leading zero, the
  second dot, the third decimal, dot-on-empty, backspace-on-empty.
- **`QuickEntryViewModel`**: the draft carries `source = WIDGET` and the direction the
  intent supplied; a repository failure leaves the state saveable with an error and
  emits no `Saved` effect; the Cash account is the one resolved.
- **Category ordering**: a DAO test that a recently used category sorts above an
  older one.
- **The provider's two intents** carry different directions. One Robolectric test;
  the rest of the face is a layout.

No instrumented test for the launcher's rendering of the widget. It cannot be
asserted meaningfully and it is verified by looking at the home screen.

---

## 11. Out of scope

- Preset amount buttons. Invented numbers until there is history to derive them from.
- Recent-spend shortcuts on the widget face, and any live figure on it. Both are the
  point at which Glance is reconsidered (§2).
- Account switching and non-cash entry. The editor is one tap away inside the app and
  already does this.
- A merchant field (§5).
- Editing or deleting from the sheet. It records; the ledger corrects.
- Light mode, as everywhere else. Deferred, not cancelled.
