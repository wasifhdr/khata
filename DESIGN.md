# Design System

The interface as **built**, not as intended. Where this and the spec disagree, this file is right
and the spec is stale — say so and fix the spec.

- **Direction and rationale:** `docs/superpowers/specs/2026-08-28-khata-petrol-design.md`
- **Product rules (copy, language, platform):** `PRODUCT.md`
- **This file:** the tokens, the constraints they encode, and how to add to them without breaking
  the guarantees.

Everything below lives under `app/src/main/java/com/wasif/khata/core/ui/`.

---

## 1. The rules that cannot be broken

These are enforced by tests. Breaking one fails the build, not a review.

1. **No colour literal outside `core/ui/theme`.** Every colour comes from
   `MaterialTheme.colorScheme`, `KhataPalette`, or `LocalCategoryColors`. The theme is tuned at
   runtime across 8 × 4 × 4 × 4 combinations; a literal is correct in exactly one of them.
2. **Text ≥ 4.5:1, graphical objects ≥ 3:1**, measured against the colour *actually composited
   beneath* the element — not a raw gradient stop, and not the pre-tuner scheme.
3. **Colour is never the only signal.** Anything colour says, a shape and a word also say.
4. **Only two text tiers exist over the field.** Paper and one dim. See §3.3 — this is the
   constraint most likely to be violated by accident.
5. **The field is the only Haze source.** Never a list, never a scrolling surface.
6. **Every Material role is set explicitly.** An unset role is Material purple, and it leaks in
   through whichever component happens to read it.

---

## 2. The world

A dark instrument that reads as a book. Petrol-green glass floating on a verdigris field, amounts
set in a serif because a serif reads as a page and a grotesk reads as a dashboard.

Dark is the product, not a mode. There is no light scheme, and `isSystemInDarkTheme` is
deliberately never consulted — rule 1 is what keeps adding one later an addition rather than a
rewrite.

---

## 3. Colour

`core/ui/theme/Color.kt`

### 3.1 Fixed palette

| Token | Value | Used for |
|---|---|---|
| `ground` | `#061214` | The base surface. Also the default ground swatch. |
| `onSurface` | `#EDF2F1` | Paper. Primary text. |
| `onSurfaceDim` | `#A8B8B8` | Secondary text (`onSurfaceVariant`). |
| `onSurfaceFaint` | `#8A9E9C` | Borders and disabled controls (`outline`). **Not text.** |
| `accent` | `#8FE0CE` | Default accent. |
| `accentDeep` | `#5FC9B2` | Its pressed/deeper partner. |
| `alert` | `#FF7A6B` | Errors, balance drift. |
| `warn` | `#F2A63E` | Declared, currently unused. |
| `heroStops` | `#12403F` → `#0C2E30` → `#08211F` | Petrol. The active module card's gradient. |

`heroStops` is **fixed** — it does not move with the tuner. That is deliberate: it is the one
surface whose contrast can be reasoned about once and relied on everywhere.

### 3.2 The tuner

Four axes, persisted in DataStore, applied at `KhataTheme`:

| Axis | Options |
|---|---|
| **Field** (8) | Verdigris `#124740` · Abyss `#12464D` · Counterpoint `#0F474C` · Cyan `#10474B` · Violet `#452F76` · Monochrome `#124746` · Deep sea `#0E464D` · Mist `#2C414D` |
| **Ground** (4) | Teal black `#061214` · Indigo black `#0B0C18` · Navy black `#08111C` · Cool black `#0A0D0F` |
| **Accent** (4) | Pale aqua `#8FE0CE` · Marigold `#FFB627` · Chartreuse `#B6E24A` · Warm sand `#E8C9A0` |
| **Intensity** (4) | Off `0.0` · Dim `0.34` · Mid `0.64` · Full `1.0` |

A field's value is its **key stop**: the lightest point the mesh reaches, already composited over
the ground. It is both the colour drawn and the colour asserted against.

**The key stops are trimmed 12–33% below their design values.** The grain (§4) lifts the brightest
pixel by ~9/255, so a stop sized against its raw value fails on screen while passing on paper — dim
text measured 3.92:1 over the mesh while the test file was green. Violet needed no trim; it is dark
enough already.

`ThemeSpec` carries the four axes. `composeScheme(spec)` derives the full `ColorScheme`. Note that
**the accent family is derived, not copied**: `deepenAccent()` converts to HSV, drops value to 90%
and raises saturation to 145%, holding hue. That is what lets Marigold get a matching deep stop
rather than inheriting pale aqua's. Copying only `primary` is what once left every selected chip
aqua after picking Marigold.

### 3.3 The two-tier rule

**Over a lit field, only two text tiers can clear 4.5:1 — paper and one dim.** A third, fainter
tier cannot, at any value: lightening it far enough to pass collapses it into paper and destroys
the hierarchy.

So:

- `onSurface` — primary text. ✅ text
- `onSurfaceVariant` — all secondary text, including micro-labels like `SPENT`. ✅ text
- `outline` — **borders and disabled controls only.** ❌ never text

`ContrastTest.the outline role carries no live text` scans the source for
`color = MaterialTheme.colorScheme.outline` and fails if it finds one. A single such line
reintroduces a 2.33:1 label over the mesh, which is why this is a test and not a convention.

Disabled controls are WCAG-exempt from the floor but still use `outline` so they remain visible.
They also carry `enabled = false` semantics, because raising `outline` for the border floor narrowed
enabled-vs-disabled separation from 1.60× to 1.37× — colour alone no longer carries that state.

### 3.4 Category colour

Fifteen hues, **quarantined**: they appear only inside an 8dp dot or a chip. Never a background,
never a text colour. The category name is always beside the dot, so colour is decoration.

Grouped by meaning rather than spectrum — warm for Living, cool blues for Recurring, pink/violet for
Discretionary, earth for Place. Green is reserved: the ledger encodes credit as green, so
`category_emerald` (Income) is the only category allowed to use it.

**The map keys are legacy and deliberately not renamed.** `category_green` is Groceries and is
gold. The keys are seeded in `DefaultData.kt`; renaming them would need a data migration to buy
nothing. Read the comment, not the key.

`CategoryDot` handles the unknown-token and low-confidence cases with a ring and a content
description — the two non-colour signals rule 3 requires.

---

## 4. The field

`core/ui/component/FieldScaffold.kt`

The verdigris mesh sits on **every screen** at the user's global intensity. There is no per-screen
density rule; an earlier draft had one and it contradicted the tuner. `FieldScaffold` is the single
owner — it paints the ground, draws the mesh, registers it as the Haze source, and hands the
`HazeState` to its content:

```kotlin
FieldScaffold(Modifier.fillMaxSize()) { haze ->
    // screen content; pass `haze` to any KhataGlass
}
```

**Every screen root uses this.** A screen that paints `colorScheme.background` itself is a bug —
it will be the only screen without the field.

### Composition

Four soft radial pools rather than a full-bleed gradient. A linear wash covers every pixel at its
own alpha and swamps the content; pools leave most of the ground untouched and read as light
falling on a surface. Positions are fractional and fixed:

| Pool | Colour | Alpha | Centre | Radius |
|---|---|---|---|---|
| 1 | field key stop | 0.85 | (0.14, 0.02) | 1.15 |
| 2 | field key stop | 0.55 | (0.92, 0.16) | 0.95 |
| 3 | `heroStops[0]` | 0.60 | (0.70, 0.78) | 1.00 |
| 4 | ground | 0.70 | (0.10, 0.95) | 0.90 |

All alphas are multiplied by intensity. At `Off` the pools are skipped entirely.

> **Known:** the geometry was tuned for the hub, where the top is open space. On inner pages pool 1
> sits under the heading. Contrast holds, but the pattern is identical on every screen.

### Grain

A per-pixel AGSL hash noise (`FieldGrainAlpha = 0.035`), drawn last — over the flat ground as well
as the pools, since gating it on intensity would leave `Off` the one theme that bands.

It is **signed**: half the pixels darken and half lighten, so the mean colour is preserved. An
add-only grain can only lighten, which fogs a dark ground (measured +3/255 before the split).

Generated rather than tiled: no asset resident, no visible repeat. It is also what forces the key
stop trim in §3.2 — the grain is part of the contrast budget, not a finishing touch.

---

## 5. Glass

`core/ui/component/KhataGlass.kt`, via Haze 1.5.3.

Five ingredients. Most implementations ship two and wonder why it looks like a grey card:

1. **Backdrop blur** — 22dp
2. **Translucent tint** — white at 5.5%, 8.5% when `raised = true`
3. **Edge highlight** — a 1dp *gradient* border, bright at top-left fading to nothing. The
   most-skipped ingredient and the one that sells it.
4. **Grain** — `noiseFactor` 0.04
5. **Saturation** — from sampling the real backdrop rather than compositing a flat fill

Plus the sixth thing, which is not a property of the panel: **glass needs something worth looking at
behind it.** The field exists to give it something to refract.

### Where it goes

| Surface | Verdict |
|---|---|
| Module cards over the static field | ✅ |
| Wallet month figures | ✅ |
| Editor direction pill, account/category chips — **unselected only** | ✅ |
| Ledger search field | ✅ (sits above the list, not over it) |
| Settings selection controls | ❌ the tuner needs neutral surfaces to judge swatches against |
| Ledger rows | ❌ one blur pass per row per frame |
| Anything full-screen over a scrolling list | ❌ worst case on both axes |

**Selected controls stay opaque.** A translucent selected chip reads as less committed than an
opaque one, which inverts what selection means. Factor the body into one lambda and switch only the
surface — do not duplicate the content across branches.

### The Off fallback

At intensity `Off` there is no mesh, so a blur has nothing to refract and every panel would become
flat translucent grey — which reads as a rendering bug, not a theme. `ThemeSpec.usesSolidSurfaces`
switches glass to a solid fill. One conditional; it makes "Off" a deliberate flat theme.

### Cost

Blur cost scales with blurred area × how often the backdrop changes. Because the field is the only
Haze source, glass anywhere samples a **static** backdrop — that is what makes it affordable on
every screen. Registering a list as a source would break this for the whole app.

> **Unmeasured:** the Ledger search field's cost is argued structurally, not measured. The emulator
> is jank-bound at ~94% on the baseline with identical percentiles, so it cannot discriminate.
> Needs a physical device.

---

## 6. Typography

`core/ui/theme/Type.kt`

| Family | Face | Used for |
|---|---|---|
| `Display` | Fraunces (Regular/SemiBold/Bold) | Headings and **all numerals** |
| `Text` | Instrument Sans (Regular→Bold) | Working text |
| `Bengali` | Noto Sans Bengali (Regular/Medium/SemiBold) | Any Bengali string |
| `Wordmark` | Li Swarnali Okkhor | **খাতা only** |

A serif on the numerals is the point: a serif reads as a page, a grotesk reads as a dashboard.
Numerals never need Bengali glyphs, so a Latin-only serif is safe on amounts.

**The wordmark face is display-only.** It is unreadable at row sizes and its metrics do not match
Instrument Sans. It must never reach a merchant name.

**Any Bengali string needs a Bengali style.** Instrument Sans has no Bengali glyphs and will fall
through to whatever the device ships — the exact per-device variance the bundled cut exists to
remove. `TaglineTextStyle` and `BengaliBodyStyle` are the two entry points.

### Scale

`displayLarge` 50 · `displaySmall` 38 · `headlineMedium` 34 · `titleLarge` 21 · `bodyLarge` 14 ·
`bodyMedium` 13 · `bodySmall` 11 · `labelLarge` 11/1.5 tracking · `labelSmall` 10/1.4 tracking

Named styles: `WordmarkTextStyle` 54 · `PageHeadingStyle` 34 · `PageSublineStyle` 11 ·
`AmountTextStyle` 14 · `BengaliBodyStyle` 14 · `TaglineTextStyle` 13.

**34 against 11 is the working contrast.** The gap does more than absolute size — the previous
build put every glyph between 12 and 20sp, which is what "everything looks the same" was pointing
at.

**Amounts use `fontFeatureSettings = "tnum"`.** Tabular figures align on the decimal down a
3,000-row column instead of drifting with each digit's width.

---

## 7. Shape and spacing

`core/ui/theme/Shape.kt`, `core/ui/theme/Dimens.kt`

**Radii** — `extraSmall` 10 · `small` 14 · `medium` 20 · `large` 28 · `extraLarge` 28. Large radii
are deliberate: this world is glass, and glass has soft edges.

**Spacing** — a 4/8 grid: `xs` 4 · `sm` 8 · `md` 16 · `lg` 24 · `xl` 32 · `xxl` 48.
Plus `screenHorizontal` 18 · `minTouchTarget` 48 · `headspaceLedger` 132.

**Elevation** — no drop shadows on content. Depth comes from glass layering and the edge highlight.
Tonal elevation only for the FAB, sheets and dialogs.

---

## 8. Motion

`core/ui/theme/Motion.kt`, `core/ui/motion/KhataTransitions.kt`

`quick` 150ms · `standard` 250ms · `emphasized` 400ms
Enter `cubic-bezier(0.05, 0.7, 0.1, 1)` · Exit `cubic-bezier(0.3, 0, 0.8, 0.15)`

**"Remove animations" is honoured with a real cut, not a fast animation.** Someone who turned
motion off because it makes them ill is not served by a 50ms version of the same motion.
`Motion.forDurationScale(0f)` zeroes every duration; `rememberSystemMotion()` observes the setting
through a `ContentObserver`, because toggling it does *not* recreate the Activity and a one-shot
read would keep animating until the next cold start. Other scales (developer options' 0.5×/10×) are
left alone — the platform already applies those.

### Transitions

- **Shared axis (X)** — hub ↔ module. Slide 20% of width plus fade; reverses on pop. A full-width
  slide reads as a page turn; 20% reads as two panels on one track, which is what these are.
- **Fade-through** — everything else. Outgoing leaves before incoming arrives (so they never
  cross-dissolve into a double image), incoming scales from 0.92.

The rule is `isHubTransition(from, to)`: true when either endpoint is `Modules`. That is the only
place a spatial relationship exists. Everything else is a move *within* a module, where sliding
would assert a sideways adjacency that isn't real. Null-safe on both sides — fade-through is the
honest fallback because it makes no spatial claim.

---

## 9. Layout

**Air at the top, content anchored to the bottom, heading centred in the air.**

One rule on every screen. It gives the app a recognisable composition *and* puts every tappable
element in the lower half — satisfying one-handed reach through layout rather than through a rule
each screen has to remember. Implemented as a `weight(1f)` headspace with centred contents above a
wrap-content block, so the air scales with the device: a taller screen gets more air, never a
stretched card.

The Ledger bends it. A 3,000-row list has no bottom to anchor to, so it gets a fixed
`headspaceLedger` (132dp) instead — enough to read as the same family, small enough that six rows
stay visible.

---

## 10. File map

| File | Owns |
|---|---|
| `theme/Color.kt` | Palette, tuner swatches, category colours, all Material roles, `contrastRatio`, `deepenAccent` |
| `theme/Type.kt` | Families, scale, named styles |
| `theme/Shape.kt` | Radii |
| `theme/Dimens.kt` | Spacing, `FieldIntensity` |
| `theme/Motion.kt` | Durations, easings, the reduced-motion rule |
| `theme/KhataTheme.kt` | `ThemeSpec`, `composeScheme`, the composition locals |
| `motion/KhataTransitions.kt` | Transition builders, the hub-boundary rule |
| `component/FieldScaffold.kt` | Ground + mesh + grain + Haze source |
| `component/KhataGlass.kt` | The five ingredients, the Off fallback |
| `component/CategoryDot.kt` | Quarantined colour, ring, description |
| `component/ContextHeader.kt` | Heading + subline pair |

---

## 11. Adding to the system

**A new screen** — root it in `FieldScaffold`, apply §9's layout rule, and pass `haze` to any glass.
Do not paint `colorScheme.background`.

**A new colour** — it belongs in `Color.kt`, not the call site. Then add its assertion to
`ContrastTest`: against the **grain-lit** field key stops if text or a border can sit on the field,
and against every ground. Contrast is pairwise, not combinatorial — never write an 8×4×4 loop.

**A new text style** — pick `onSurface` or `onSurfaceVariant`. Those are the only two tiers (§3.3).
If it needs to be fainter, the answer is smaller or lighter-weight, not dimmer. Add it to
`TypeTest`'s fallback list so it cannot silently resolve to the platform font.

**A new selectable control** — opaque when selected, glass when not, one shared body lambda.

**A new destination** — if it hangs off the hub it is shared-axis automatically; anything else
fades through. No change needed unless the hub boundary itself moves.

---

## 12. What the tests guarantee

`ContrastTest` (19 assertions) is the gate. Beyond the obvious floors it pins several things that
have each shipped as a bug once:

- **The composed scheme, not `DarkColors`.** Asserting the pre-tuner scheme is how a half-applied
  accent survived — `DarkColors` only ever holds the default.
- **The grain-lit peak, not the raw key stop.** The brightest rendered pixel is not the stop.
- **No role keeps its Material default.** Purple leaks through whichever component reads an unset
  role.
- **No live text in `outline`** — a source scan (§3.3).
- **`outline` still fails on the lightest hero stop** — a tripwire. If it ever passes, the reason
  it is routed around is stale and needs revisiting. Asserted only on the lightest stop, because
  unfitness needs the *worst* case to fail, not every case.
- **Every ground and accent swatch carries a name**, so the tuner can label them.

`MotionTest` pins the reduced-motion rule. `TypeTest` pins that no style falls through to the
platform font.

---

## 13. Open

- **Li Swarnali Okkhor licence.** No web upload; public release needs written permission from
  `admin@lipighor.com`. A release gate, not an implementation one.
- **Field pool geometry** is hub-tuned and identical on every screen (§4).
- **Ledger search glass** is unmeasured on real hardware (§5).
- **`KhataPalette.warn`** is declared and unused.
- **`KhataNavHost` has no automated coverage** — no Hilt navigation test infra exists. The
  transition *rule* is unit-tested; the wiring is verified by hand.
