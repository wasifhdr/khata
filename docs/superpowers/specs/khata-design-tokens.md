# Khata — Design Tokens & Direction

**Date:** 2026-08-27
**Status:** Direction decided. Values below are implementation-ready.
**Supersedes:** the direction roll (seed `24ecd5ef`). The user pinned this world;
a pinned brief beats the roll.

---

## Direction contract

Goes verbatim at the top of `core/ui/theme/KhataTheme.kt`.

**THESIS** — A ledger that reads like a well-set page, not a fintech dashboard.
It refuses the category default: no gradient balance card, no donut chart, no
rounded-everything surfaces floating on grey.

**OWN-WORLD** — Warm paper grounds with real chroma, warm-black ink, hairline
rules instead of shadows, small radii, and a single saturated ink-orange accent
used sparingly. Recognizable with every word removed by the warmth of the ground,
the rules, and the restraint of the orange.

**STORY** — The user opens it, sees that the record is already complete and
correct without them having done anything, and either glances and leaves or sits
down and works.

**FIRST VIEWPORT** — Month-to-date spend set large in tabular figures at the top
of the ledger, the day's first header and rows immediately beneath it, a hairline
between each row. Primary action is a FAB in the bottom-right thumb arc.

**FORM** — Warm editorial, restrained colour strategy. User-pinned; overrides
assigned index 6 of seed `24ecd5ef`.

**FINISH** — unreviewed and undocumented is unfinished; this build ends with the
finish review, the verdict, DESIGN.md, and every shipping raster carrying its
provenance.

---

## Colour strategy

**Restrained** — warm neutrals carry the surface, one accent carries interaction.
The reference names this the correct default for a surface the user came to
operate, and it is what "simple interface" means in practice.

Three disciplines carried over from the direction round. They survived the change
of world because each solves a real problem in this product:

1. **Colour quarantine.** Saturation appears only inside a bounded shape — a chip,
   a dot, a filled button. It never bleeds into the field. This is what keeps 15
   category colours from destroying a 3,000-row ledger.
2. **State stamps the artifact, it does not remove it.** A deleted or reversed
   transaction reads as struck and marked, never as a gap. Matches product
   principle #3.
3. **Every state is named and pattern-differentiated.** Colour is never the sole
   signal — load-bearing given 15 categories, colour-blindness, and direct sun.

### Why not Material You dynamic colour

Android guidance prefers wallpaper-derived schemes on API 31+. Khata declines it:
a wallpaper-derived scheme cannot guarantee the debit/credit distinction or the
orange accent survive, and the pinned brief fixes the palette. Dynamic colour is
therefore not wired up at all, rather than wired up and overridden.

This is also why `minSdk 30` (Android 11) costs nothing. Dynamic colour is the one
capability API 31 would have added, and it is declined on its own merits — so the
floor drops a version with no branch, no fallback palette, and no lost feature.

### Why hairlines instead of elevation

Editorial print separates with rules, not drop shadows. Rows, headers, and
sections divide with a 1dp `outlineVariant` line. Tonal elevation is reserved for
genuinely floating surfaces — the FAB, bottom sheets, dialogs. No arbitrary drop
shadows anywhere.

---

## Light palette

Ground is a warm bone with actual chroma, not washed cream. Ink is warm-black with
brown in it, not neutral charcoal.

| Role | Hex | Notes |
|---|---|---|
| `background` | `#F5F0E8` | warm bone |
| `onBackground` | `#241E17` | warm ink |
| `surface` | `#FFFCF6` | raised paper |
| `onSurface` | `#241E17` | warm ink |
| `surfaceVariant` | `#E8E0D2` | recessed |
| `onSurfaceVariant` | `#554B3D` | warm secondary |
| `outline` | `#8A7D6B` | |
| `outlineVariant` | `#D6CCBB` | the hairline rule |
| `primary` | `#B23E06` | ink orange |
| `onPrimary` | `#FFFFFF` | |
| `primaryContainer` | `#FFE0CC` | |
| `onPrimaryContainer` | `#431300` | |
| `secondary` | `#5C5445` | warm neutral |
| `onSecondary` | `#FFFFFF` | |
| `error` | `#A32116` | |
| `onError` | `#FFFFFF` | |

**Verified contrast** (computed, not estimated):

| Pair | Ratio | |
|---|---|---|
| `onSurface` / `surface` | **16.1 : 1** | far above AA — this is the sunlight requirement |
| `onSurfaceVariant` / `surfaceVariant` | **6.5 : 1** | |
| `onPrimary` / `primary` | **5.9 : 1** | |
| `onPrimaryContainer` / `primaryContainer` | **12.6 : 1** | |
| `onError` / `error` | **7.5 : 1** | |

A brighter orange (`#E8590C`) was tested first and rejected: white on it lands at
3.6:1 and warm-black at 4.6:1 — either fails or barely scrapes AA, which is not
enough for a screen read in direct sun. `#B23E06` is the brightest orange that
carries white text safely.

## Dark palette

Designed on its own terms, not inverted. Warm espresso rather than neutral black,
because night use is a confirmed primary condition.

| Role | Hex | Notes |
|---|---|---|
| `background` | `#16130F` | warm near-black |
| `onBackground` | `#F0E8DA` | warm paper |
| `surface` | `#1E1A15` | |
| `onSurface` | `#F0E8DA` | |
| `surfaceVariant` | `#332D25` | |
| `onSurfaceVariant` | `#D3C8B6` | |
| `outline` | `#9A8D7B` | |
| `outlineVariant` | `#4A4237` | the hairline rule |
| `primary` | `#FF9A62` | lifted orange |
| `onPrimary` | `#4A1600` | |
| `primaryContainer` | `#8A2F03` | |
| `onPrimaryContainer` | `#FFE0CC` | |
| `error` | `#FFB4AB` | |
| `onError` | `#690006` | |

**Verified contrast:**

| Pair | Ratio |
|---|---|
| `onSurface` / `surface` | **14.2 : 1** |
| `onSurfaceVariant` / `surfaceVariant` | **8.2 : 1** |
| `onPrimary` / `primary` | **7.2 : 1** |

## Category palette

Quarantined: these appear only inside a chip or an 8dp dot, never as a background
or a text colour. Warm-biased so the set sits inside the world, and kept clear of
the brand orange so a category never reads as an interactive element.

| Token | Light | Dark |
|---|---|---|
| `category_green` | `#4A7C36` | `#93C47D` |
| `category_orange` | `#C2611F` | `#E9A06A` |
| `category_blue` | `#2A5D8F` | `#8CB4DC` |
| `category_slate` | `#4F5D68` | `#A7B4BE` |
| `category_amber` | `#9C6F0A` | `#DDB55E` |
| `category_teal` | `#1C6E63` | `#6FBDB0` |
| `category_red` | `#A32116` | `#EC9C93` |
| `category_violet` | `#6B4A9E` | `#BCA3E0` |
| `category_pink` | `#A63A6B` | `#E29BBB` |
| `category_indigo` | `#3B4A8F` | `#A3AEE0` |
| `category_rose` | `#9E3450` | `#E09CAB` |
| `category_bronze` | `#7D5522` | `#C9A472` |
| `category_grey` | `#6B6255` | `#B9B1A3` |
| `category_emerald` | `#17694E` | `#6FC0A0` |
| `category_neutral` | `#756B5C` | `#B3AA9B` |

Every value clears 3:1 against its own surface — the AA floor for graphical
objects, which is what a dot or a chip is. Category is never signalled by colour
alone; the name is always present.

---

## Typography

**One family: the platform stack (Roboto).** Two reasons, and the first is not
negotiable:

1. **Bengali has to render correctly** — a confirmed product requirement. The
   system stack falls back to Noto Sans Bengali, which is bundled, metrically sane,
   and always present. A bundled Latin display face would leave Bengali falling
   back to a mismatched font at a different optical size, which looks broken
   exactly where it matters: real merchant names.
2. Operate surfaces are well served by workhorse UI faces. "Simple interface" and
   a second display face pull in opposite directions.

**The editorial character lives in the scale, not in a second font** — wide size
contrast, a genuine display step the app actually uses, tabular figures for every
amount, and hairline rules doing the separating.

| Role | Size / line | Weight | Used for |
|---|---|---|---|
| `displaySmall` | 36 / 42 | SemiBold | month-to-date total |
| `headlineMedium` | 26 / 32 | SemiBold | screen titles |
| `titleLarge` | 20 / 26 | Medium | section heads |
| `bodyLarge` | 16 / 22 | Normal | merchant names, primary rows |
| `bodyMedium` | 14 / 20 | Normal | supporting text |
| `bodySmall` | 12 / 16 | Normal | metadata |
| `labelLarge` | 13 / 18 | Medium, +0.4 tracking | day headers, chips |
| `labelSmall` | 11 / 15 | Medium, +0.5 tracking | fine labels |

**`AmountTextStyle`** — 15 / 22, Medium, `FontFeatureSetting("tnum")` for tabular
figures so amounts align on the decimal down a ledger column instead of drifting
with each digit's width. Monospace is deliberately *not* used: it reads as a
terminal, and this is a page.

All sizes in `sp`, so system font scaling works. Layouts must survive 1.3× scale.

**Later upgrade, not now:** a serif display face for amounts and the month total
would deepen the editorial character. Deferred because it needs a matching Bengali
serif to avoid the fallback problem above.

---

## Spacing, shape, motion

**Spacing** — 4/8 grid: `xs 4`, `sm 8`, `md 16`, `lg 24`, `xl 32`, `xxl 48`.
`screenHorizontal 16`, `minTouchTarget 48`.

**Shape** — small radii, because editorial print is not rounded: `sm 4`,
`md 8`, `lg 12`, `full` for chips and the FAB only. No `24dp` card corners.

**Elevation** — level 0 for content. Hairline rules separate. Tonal elevation only
for the FAB, bottom sheets, and dialogs.

**Motion** — `quick 150ms`, `standard 250ms`, `emphasized 400ms`.
Enter `cubic-bezier(0.05, 0.7, 0.1, 1)`, exit `cubic-bezier(0.3, 0, 0.8, 0.15)`.
Material shared-axis between ledger and editor; fade-through between top-level
destinations. Honors the system "Remove animations" setting with an instant cut.

---

## Screen concepts

### Ledger

The four confirmed usage situations all have to coexist here, so the screen is
layered rather than optimised for one:

- **Top:** month-to-date spend in `displaySmall` tabular figures, with the month
  name in `labelLarge` above it. This is the glance answer, readable in under a
  second without scrolling.
- **Under it:** a search field, always visible, never behind an icon — the
  "find that one transaction" job is one of four, not an edge case.
- **Body:** day headers in `labelLarge` on `onSurfaceVariant`; rows of merchant
  name (`bodyLarge`) with an 8dp category dot, amount right-aligned in
  `AmountTextStyle`. A 1dp `outlineVariant` hairline between rows. Debits carry a
  minus sign and ink colour; credits carry a plus and the orange.
- **Bottom right:** the FAB, in the thumb arc. The only orange-filled element on
  the screen, which is what makes it read as the action.
- **Low-confidence rows** carry a small outline ring on the category dot plus the
  word in the row's metadata line — pattern and text, never colour alone.
- **Empty state:** "No transactions yet" plus one line of guidance, centred.

### Transaction Editor

- Amount first, largest thing on screen, decimal keypad, tabular figures.
- Direction as two chips — "Spent" / "Received" — not a switch. A switch hides
  which state is which.
- Account and category as quarantined chips, each row labelled.
- Merchant and note as plain fields below the fold of the decision.
- Save as a full-width filled button at the bottom, inside the thumb arc,
  disabled until the form is genuinely saveable.
- Errors as durable text under the field, warm-black on the field, never a
  transient toast.
