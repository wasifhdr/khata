# Khata — Visual Direction: Petrol

**Date:** 2026-08-28
**Status:** Direction decided with the user. Values below are implementation-ready.
**Supersedes:** `docs/superpowers/specs/khata-design-tokens.md` in full.
**Amends:** `PRODUCT.md` — the pinned visual constraint and the Bengali-strings line.

---

## Why this replaces the previous direction

The previous tokens doc described a warm editorial world: bone-paper ground, ink orange,
hairline rules, small radii, system Roboto. Two things were wrong with it.

**The first is that it was never built.** The shipped screens are stock Material 3 with a
warm palette swapped in — a default `TopAppBar`, default outlined fields, default filter
chips, 16sp rows. `displaySmall` was defined and never used anywhere, so the doc's central
claim that "the editorial character lives in the scale" was unrealised: every glyph on
screen sat between 12 and 20sp. `LocalCategoryColors` was provided by the theme and
consumed by nothing. That flatness, not the palette, was the main cause of the app reading
as generic.

**The second is that the user does not want a quiet world.** Asked what bothered them, they
named three symptoms: everything the same size, colourless and lifeless, and nothing on
screen. Two of those three are structural rather than stylistic. A restrained, hairline,
single-accent world executed perfectly would still have failed the second and third.

The user reopened the pinned constraint deliberately, and chose a full re-roll after seeing
worked alternatives. Reference material supplied by the user: two Dribbble shots
(an insurance app by Ghani Pradita for Paperpillar; an AI-learning app by Bogdan Nikitin for
Nixtio). Their shared traits — mesh-gradient hero surfaces, large radii, card-on-card
layering with soft depth, big type on the gradient, pill controls, circular icon containers,
contained bottom nav, tinted (never neutral) grounds, heavy photography.

---

## Direction contract

Goes verbatim at the top of `core/ui/theme/KhataTheme.kt`.

**THESIS** — A dark instrument that reads as a book. Petrol-green glass floating on a
verdigris field, amounts set in a serif because a serif reads as a page and a grotesk reads
as a dashboard.

**OWN-WORLD** — A cool blue-green world with warmth quarantined into the category dots.
Recognisable with every word removed by the petrol card on the verdigris mesh, the pale-aqua
numerals, and a Bengali wordmark that is never transliterated.

**STORY** — The user opens it, sees the record is already complete without them having done
anything, and either glances and leaves or sits down and works.

**FIRST VIEWPORT** — খাতা centred on both axes in a large open field, the tagline beneath
it, and the module cards anchored to the bottom edge inside the thumb arc.

**FORM** — Dark-first glassmorphism, restrained hue, wide type contrast. Chosen by the user
against nine bright and ten muted alternatives.

**FINISH** — unreviewed and undocumented is unfinished.

---

## 1. Colour

Dark is the product, not a mode. The dark palette is designed on its own terms. **Light mode
is deferred, not cancelled** — which only works if no colour is ever hardcoded. Every value
below lives behind a token from day one, or the light palette becomes a rewrite instead of an
addition.

### Core palette

| Role | Hex | Notes |
|---|---|---|
| `ground` | `#061214` | teal black — real chroma, never neutral |
| `heroA` | `#12403F` | petrol, gradient start |
| `heroB` | `#0C2E30` | petrol, gradient middle (62%) |
| `heroC` | `#08211F` | petrol, gradient end |
| `accent` | `#8FE0CE` | pale aqua — numerals, ring, FAB, selected states |
| `accentDeep` | `#5FC9B2` | pressed / secondary accent |
| `onSurface` | `#EDF2F1` | paper |
| `onSurfaceDim` | `#A8B8B8` | secondary text |
| `onSurfaceFaint` | `#6E8180` | metadata, labels |
| `alert` | `#FF7A6B` | reconciliation gaps and errors |
| `warn` | `#F2A63E` | advisory, non-blocking |

### Verified contrast

Computed, not estimated. `ContrastTest.kt` must assert these.

| Pair | Ratio | |
|---|---|---|
| `onSurface` / `ground` | **16.8 : 1** | |
| `onSurface` / `heroA` | **10.1 : 1** | worst case of the hero gradient |
| `accent` / `ground` | **12.4 : 1** | |
| `accent` / `heroA` | **7.5 : 1** | worst case of the hero gradient |
| `alert` / `ground` | **7.5 : 1** | |

The bright-sunlight requirement was **withdrawn by the user**. Standard WCAG AA is the floor;
the elevated bar the previous doc imposed no longer applies. Night use remains a primary
condition, so contrast still matters — it is simply no longer required to exceed AA.

### The ink rule

**Ink follows the field's luminance.** Bright fields take near-black ink; dark fields take
paper ink with accent numerals. In this world every surface is dark, so the second half
applies throughout — but the rule is stated in full because the theme tuner can produce
bright fields.

This began as "never white text on a hot gradient," which came from a hard failure: white on
marigold `#FFB627` lands near **2:1**. That was never a sunlight problem; it is illegible in a
dark room too.

### The density rule

**Field intensity is inversely proportional to content density.** The verdigris mesh ships at
three fixed levels, chosen per screen, so the ground never competes with a column of numbers.

| Level | Used on | Approx. opacity |
|---|---|---|
| Full | Home, module hub | 100% |
| Mid | Wallet, editor | ~65% |
| Quiet | Ledger | ~35% |

### Why not Material You dynamic colour

Unchanged from the previous doc, and the reasoning survives the re-roll intact: a
wallpaper-derived scheme cannot guarantee the debit/credit distinction or the brand hue. It is
not wired up at all, rather than wired up and overridden.

---

## 2. Category colour

Fifteen categories. **Quarantined** — they appear only inside an 8dp dot or a chip, never as a
background or a text colour.

The world took two hues off the table. **Teal and aqua are brand** (hero and accent), so a
teal dot would read as interactive. **Green means income** in the ledger, so a green category
competes with the debit/credit encoding. What remains is the warm and violet arcs — which
produces the system's central inversion: *the warmth displaced from the hero card relocates
into the categories.*

Fifteen fully distinguishable hues is not achievable, and pretending otherwise is how category
palettes fall apart. They are organised into **six families**, so the family communicates the
kind of expense even when two neighbours are close. **Colour is never the sole signal** — the
category name is always present.

| Family | Category | Hex |
|---|---|---|
| **Living** | Groceries | `#E8C15A` |
| | Eating out | `#FF8A6B` |
| | Education | `#F2A63E` |
| **Recurring** | Bills & utilities | `#9DB4C8` |
| | Mobile & internet | `#7FB8EC` |
| | Transport | `#93A9F2` |
| **Discretionary** | Shopping | `#F293A8` |
| | Entertainment | `#B7A2EF` |
| | Gifts & charity | `#DF8CCC` |
| **Place** | Rent | `#DCC099` |
| | Household | `#C9A6BC` |
| | Travel | `#BCC46E` |
| **Body** | Health | `#EE6F80` |
| **System** | Fees & charges | `#B3B0A8` |
| | Transfers | `#98A6B8` |

Every value clears **3:1** against all four tuner grounds — the AA floor for graphical objects,
which is what a dot is. Verified by computation, not assertion.

**`alert` sits deliberately outside this set** so a reconciliation warning never reads as a
category, and a category never reads as a warning.

**Category colours are not themeable.** The tuner moves hero, field, ground and accent. These
stay fixed, because they encode data rather than taste — if a category changed colour between
themes, the one thing colour actually carries information for would break.

### Known collision: warm accents and the Living family

The reasoning above — *teal and aqua are brand, so a teal dot would read as interactive* —
holds for the default accent. But §9's tuner lets the accent become **Marigold**,
**Chartreuse** or **Warm sand**, any of which sits close to Groceries `#E8C15A` or Education
`#F2A63E`. Left alone, that reintroduces exactly the ambiguity the quarantine rule exists to
prevent.

**Resolved by shape, not by hue.** Interactive elements are always distinguished by form as
well as colour: the accent appears as a filled pill, a progress ring, a FAB or a large numeral.
A category appears only as an 8dp dot or a chip carrying its own name. No accent-coloured 8dp
dot exists anywhere in the app, so proximity in hue never produces ambiguity in practice.

This is the same discipline already required elsewhere — low-confidence rows use a ring *and*
the word, categories use a dot *and* the name. Colour is never load-bearing alone, which is
what makes a free-form accent axis safe.

---

## 3. Typography

Three roles, because one face cannot do all three jobs.

| Role | Face | Used for |
|---|---|---|
| Display + numerals | **Fraunces** | amounts, hero figures, page headings |
| Working text | **Instrument Sans** | merchant names, notes, labels, buttons |
| Bengali text | **Noto Sans Bengali** (bundled) | Bengali merchant names, notes, SMS-derived text |
| Wordmark | **Li Swarnali Okkhor** (bundled) | খাতা, and nothing else |

### Why a serif on the numerals

The previous doc deferred a display face because Bengali had no matching pair. **That
reasoning only ever applied to text — numerals never need Bengali glyphs.** A Latin-only face
on amounts sidesteps the problem entirely.

Fraunces has properly drawn tabular figures and reads as a book rather than a dashboard, which
is the whole argument: this is a *khata*. It was chosen by the user over ten alternatives
spanning grotesk, slab, condensed, monospace and expressive display.

Monospace was reconsidered and rejected. The previous doc rejected it because it "reads as a
terminal, and this is a page" — that reasoning expired with the re-roll, so it was re-offered
on its merits and not chosen.

### Type scale

| Role | Size / line | Weight | Used for |
|---|---|---|---|
| Wordmark | 54–62 | 400 | খাতা, home only |
| Page heading | 34 / 39 | 700 | inner-page context line |
| Hero figure | 38–50 | 700 | module figure, net worth, entry amount |
| Section head | 21 / 24 | 700 | |
| Body | 13.5–14 / 20 | 500 | merchant names, primary rows |
| Meta | 10.5–11 / 15 | 400 | supporting text, sublines |
| Label | 10–11, +0.14em | 700 | uppercase section labels, day headers |

All sizes in `sp`. Layouts must survive 1.3× system font scaling.

**The 34 : 11 jump on inner pages is load-bearing.** The current build has every glyph between
12 and 20sp, which was the real cause of "everything is the same size". Wide contrast is the
fix, and it is cheaper than any other change in this document.

**Amounts** use `font-variant-numeric: tabular-nums` / `FontFeatureSetting("tnum")` without
exception, so `−৳2,340.50` and `−৳11.50` align on the same right edge down 3,000 rows.

### Bengali

**Bundle Noto Sans Bengali** rather than relying on device fallback. ~300KB, and it removes
every source of per-device variance in the one place it cannot be afforded. Instrument Sans
and Noto Sans Bengali are close enough in metrics that mixed rows do not jump height — the
failure mode that breaks a long ledger.

**Li Swarnali Okkhor (Lipighor) is the wordmark face and nothing else.** 192KB. Use
`Unicode/Li Swarnali Okkhor Unicode.ttf` only — the ANSI V1 and V2 cuts in the same download
are legacy Bijoy encodings that map Bengali glyphs onto Latin codepoints and render garbage
against real Unicode text.

The two Bengali faces coexist: Noto for text, Swarnali Okkhor for the mark. ~500KB total.

### Licence obligation — open

Lipighor's EULA restricts redistribution of the font files and requires **written permission
from `admin@lipighor.com`** for web-font use plus a footer backlink.

- **Sideloaded personal build:** no distribution occurs. Fine as-is.
- **Any published web page (including design artifacts):** requires permission. Not obtained;
  artifacts therefore use a Google Font for the wordmark.
- **A Play Store release** ships the TTF inside the APK, which is arguably distribution.
  **Permission must be obtained before any public release.**

**Deferred by the user 2026-08-28.** Not a blocker for the sideloaded build, which is the only
build that exists. This is a release-gate item, not an implementation item — it must be cleared
before the first public release and does not affect any task in the plans below.

---

## 4. Wordmark and voice

**The wordmark is খাতা alone.** No Latin transliteration anywhere in the interface. The app is
named in its own script and nothing on screen translates it.

**Tagline: সব হিসাব, এক খাতায়** — "all accounts, in one book." Chosen over three other Bangla
lines and four English ones. It points at the multi-module future rather than at automatic
capture, which is the better bet: the wallet is the first module, and the tagline outlives it.

### PRODUCT.md amendment required

`PRODUCT.md` currently states: *"English interface… Not bilingual; there is no language toggle
and no Bengali UI strings."*

A Bangla tagline is a Bengali string in the interface. This is a deliberate, user-made
exception, and it is narrow: **one fixed string in the brand lockup, no language toggle, no
translation layer, no other Bengali UI copy.** The constraint must be amended to say so
explicitly rather than be silently contradicted.

**Confirmed 2026-08-28:** the line passed a native-speaker review by the user. It ships as
written.

---

## 5. Glass

True backdrop blur, via **Haze** (`dev.chrisbanes.haze`), wrapped behind a single `KhataGlass`
composable in `core/ui` so no screen calls Haze directly.

### Five ingredients

Most implementations ship two and wonder why it looks like a grey card.

1. **Backdrop blur** — 22dp radius
2. **Translucent tint** — white at 5.5%, lifting to 8.5% for raised panels
3. **Edge highlight** — 1dp *gradient* border, bright at top-left fading to nothing. The
   most-skipped ingredient and the one that sells it.
4. **Grain** — 2–4%. Kills the plastic look, and doubles as the dither that stops dark
   gradients banding on OLED. Not optional here.
5. **Saturation boost** — 175%, so the verdigris behind the glass blooms through instead of
   going muddy.

Plus the sixth thing, which is not a property of the panel: **glass needs something worth
looking at behind it.** A glass panel over flat colour is a translucent rectangle. The
verdigris field exists to give it something to refract.

### Where glass may and may not go

Blur cost scales with blurred area × how often the backdrop changes. §16 of the wallet spec
makes fluidity binding.

| Surface | Verdict | Why |
|---|---|---|
| Module cards over the static field | **Yes** | backdrop never changes; blur caches |
| Bottom sheets, dialogs, editor chips | **Yes** | backdrop is a paused screen |
| Search bar over the scrolling ledger | **Measure** | small area, changing backdrop. Test on device. |
| Ledger rows | **Never** | one blur pass per row per frame |
| Full-screen over a scrolling list | **Never** | worst case on both axes |

### Fallback at zero field

When field intensity is set to 0 the mesh is gone, so `backdrop-filter` has nothing to refract
and every panel becomes flat translucent grey — which reads as a rendering bug, not a theme.
**At intensity 0, glass must switch to a solid surface token.** One conditional; makes "Off" a
deliberate flat theme instead of broken glass.

---

## 6. Shape, spacing, motion

**Radii** — `sm 14`, `md 20`, `lg 28`, `full` for chips, pills and the FAB. Large radii are a
deliberate reversal of the previous doc's "editorial print is not rounded".

**Spacing** — 4/8 grid: `xs 4`, `sm 8`, `md 16`, `lg 24`, `xl 32`, `xxl 48`.
`screenHorizontal 18`, `minTouchTarget 48`.

**Elevation** — no drop shadows on content. Depth comes from glass layering and the edge
highlight. Tonal elevation only for the FAB, sheets and dialogs.

**Motion** — `quick 150ms`, `standard 250ms`, `emphasized 400ms`.
Enter `cubic-bezier(0.05, 0.7, 0.1, 1)`, exit `cubic-bezier(0.3, 0, 0.8, 0.15)`.
Shared-axis between hub and module, fade-through within a module. Honours the system "Remove
animations" setting with an instant cut.

---

## 7. Navigation

**Hub and back only. No bottom navigation bar.**

- Home is the **Modules** hub by default.
- A settings preference can make **any single module the root instead**.
- Drill-down: hub → module → detail. Back climbs out.
- **Predictive back is not a nicety here — it is the navigation model.** Gesture-back is the
  only universal way out of any screen, so it must be implemented properly.

### When a module is the root

If the user sets a module as their home view, the Modules hub is no longer reachable by
climbing back — so that module's nav row changes rather than gaining a floating control:

| Position | Hub is root | Module is root |
|---|---|---|
| Top-left | *(nothing)* | **hub glyph** — pushes Modules onto the stack |
| Top-right | settings gear | settings gear |

The module-as-root screen therefore looks like home structurally: two rare controls in
mirrored top corners, nothing in the thumb arc but content. Back from a module-as-root exits
the app, as any root does; back from the hub it pushed returns to the module.

### The start destination is a preference

The stored preference **is** the back-stack root, so it must be read before the `NavHost`
composes — not after. See §9.

---

## 8. Screen layouts

### The layout rule

**Air at the top, content anchored to the bottom, heading centred in the air.**

This is one rule applied on every screen, and it does two things at once: it gives the app a
recognisable composition, and it puts every tappable element in the lower half of the display —
satisfying the one-handed thumb requirement through layout rather than through a rule that
individual screens have to remember.

Implemented as a `flex: 1` headspace with centred contents and a `flex: none` content block.
The air therefore **scales with the device**: a taller screen gets more air, never a stretched
card.

### Home

- Status bar.
- **Nav row**: a 34dp settings gear, top-right. **Home only** — it appears on no other screen.
- **Headspace** (`flex: 1`): খাতা at 54–62px in Li Swarnali Okkhor, paper white, centred on
  both axes; tagline beneath at 15px.
- **Modules** (`flex: none`, anchored bottom, 22dp bottom padding): the live-module hero card
  on petrol, then a 2-column grid of glass module cards.
- This is **the only screen in the app that carries the app's name.**

**Why settings may sit in a top corner.** `PRODUCT.md` requires that *"controls used routinely
must fall within the bottom third"* and that *"primary actions cannot live in top corners."*
Settings is neither routine nor primary — it is the least-used destination in the app, and
spending thumb-arc space on it would push the module cards up. The rule governs frequency, and
this control is the explicit exception. It mirrors the back circle's position on inner pages,
so the two rare controls occupy opposite corners and never appear on the same screen.

**Module cards are live status cards, not launcher tiles.** Each carries real data on its face
— the wallet card shows month-to-date, a budget ring and the last transaction. The glance job
(usage situation #1, several times daily) is answered on the home screen without entering
anything. Tapping is for the sit-down review.

Only the active module sits on the petrol gradient; every other card is glass. That single
contrast does the work a bottom nav would otherwise do.

Unbuilt modules render dormant at 42% opacity rather than being hidden or faked.

### Inner pages (Wallet, Ledger, Editor)

- Status bar.
- **Nav row**: back circle, 34dp, top-left. Deliberately outside the thumb arc — gesture-back
  is primary, so this is a visible affordance rather than a control to stretch for.
- **Headspace**: the context line, centred on both axes. 34px heading over an 11px subline.
- **Content**, anchored bottom.

**Module pages never show the app name.** They carry *context* instead of a label:

| Screen | Heading | Subline |
|---|---|---|
| Wallet | Wallet | 4 accounts · reconciled 2h ago |
| Ledger | August 2026 | 142 entries · 6 days left |
| Editor | New entry | Today · 28 August · Cash |

The subline answers the question the screen is actually being asked — *is this current?*,
*which month?*, *what has it assumed?* — rather than restating what the user already knows.

**Nothing else on any screen is centred.** Amounts stay right-aligned; the ledger column has to
hold its right edge to stay scannable.

### Ledger — where the rule bends

A list of 3,000 rows has no bottom to anchor to, and a full-height headspace would push rows
off screen and make the monthly review worse. The ledger gets a **fixed 132dp headspace**
instead, heading centred inside it: enough to read as the same family, small enough that six
rows remain visible before scrolling. Scrolling removes it for good.

**Order stays newest-first.** Inverting the list chat-style would have matched the
bottom-anchoring more literally and read better one-handed, but a ledger that does not open on
the most recent entry fights its own convention. Considered and rejected.

Ledger specifics: quiet field; month-to-date strip on petrol; always-visible glass search bar;
day headers with a day total; rows of category dot + merchant + right-aligned tabular amount,
separated by a 1dp hairline. Low-confidence rows carry an **outline ring on the dot plus the
word in the metadata line** — pattern and text, never colour alone. FAB bottom-right in the
thumb arc.

### Editor

Amount first and largest — 50px on petrol, tabular, with a caret. Direction as a two-state
segmented pill ("Spent" / "Received"), never a switch. Account and category as quarantined
chips. Merchant and note below the fold of the decision. Save as a full-width pill at the
bottom, disabled until genuinely saveable. Errors as durable text under the field, never a
toast.

**Not yet designed:** the decimal keypad that occupies the lower half once the amount has
focus, and the three-tap widget path. Both need their own pass.

---

## 9. Theme tuner

Settings exposes a **full four-axis tuner plus a reset button.** The user was shown the risks
and chose this deliberately over a preset list.

| Axis | Options |
|---|---|
| Field palette | Abyss · Verdigris · Counterpoint · Cyan · Violet · Monochrome · Deep sea · Mist |
| Ground | Teal black · Indigo black · Navy black · Cool black |
| Accent | Pale aqua · Marigold · Chartreuse · Warm sand |
| Field intensity | Off · Dim · Mid · Full |

**Default: Petrol · Verdigris · Teal black · Pale aqua · Full.** Stored as a single named
constant that the reset button points at, so "default" remains one thing changeable in one
place.

### Verification — pairwise, not combinatorial

512 combinations cannot be designed or verified. **But contrast is pairwise:** if every accent
clears every ground and every field clears every ground, then every combination clears
automatically.

| Assertion group | Count |
|---|---|
| accent × ground | 16 |
| field × ground (worst stop of each mesh) | 32 |
| onSurface × ground | 4 |
| category dot × ground | 60 |
| **Total** | **112** |

This keeps the "computed, not estimated" standard intact while allowing free-form tuning.

---

## 10. Platform

### minSdk 30 → 33

The previous doc justified `minSdk 30` on the grounds that *"dynamic colour is the one
capability API 31 would have added, and it is declined on its own merits."* **That reasoning is
void.** API 31 also adds `RenderEffect`, which is the only way to get real backdrop blur on
Android; below it `Modifier.blur` is a silent no-op.

**33 rather than 31** additionally unlocks AGSL `RuntimeShader`, for procedural grain and edge
refraction rather than bitmap fakes.

Cost is zero: `PRODUCT.md` records one user, one Pixel 6a on Android 17, sideloaded, not
distributed. Raising the floor removes every fallback branch and capability check.

### Dependencies

- **Haze** (`dev.chrisbanes.haze`) — new. Wrapped behind `KhataGlass` in `core/ui`.
- Fonts: Fraunces, Instrument Sans, Noto Sans Bengali, Li Swarnali Okkhor Unicode — all
  bundled as resources. No downloadable fonts.

### Preferences before first composition

Two independent features have the same requirement, and it should be solved once:

- **Theme** — reading four values asynchronously from DataStore means the app paints the
  default palette for a frame and then snaps.
- **Start destination** — the preference *is* the back-stack root, so it must be known before
  the `NavHost` composes.

`KhataTheme` currently reads `isSystemInDarkTheme()` synchronously. **Hold the splash until
the first DataStore emission** and both fall out of the same fix.

---

## 11. What this does not settle

- ~~The Bengali tagline needs a native-speaker pass.~~ **Cleared 2026-08-28** — passed, ships
  as written.
- ~~Lipighor permission.~~ **Deferred 2026-08-28** — a release gate, not an implementation
  blocker. Must be cleared before any public release.
- **The decimal keypad** and the **three-tap widget** flow.
- **Insights, Budgets, Accounts and Settings** have no drawings. They get designed during
  implementation against the system above, which is now specific enough to design against.
- **Light mode.** Deferred by decision, not cancelled. Enforced by the no-hardcoded-colour rule.

---

## 12. Scope — this is two plans, not one

The work below does not fit one implementation plan. It splits cleanly along a dependency
line, and the split is worth keeping because the first half is verifiable on its own.

**Plan A — the system.** `minSdk 33`, Haze, the four bundled fonts, the full token rewrite
(colour, type, shape, spacing, motion), `KhataGlass` and the shared components, and the 112
pairwise contrast assertions. Ends with a green test suite and a theme nothing consumes yet.

**Plan B — the screens.** The Modules hub, the rebuilt Ledger and Editor, hub-and-back
navigation with a configurable start destination, the settings tuner, and the
preference-before-composition fix. Depends on Plan A entirely.

Attempting both at once means rebuilding screens against tokens that are still moving.

---

## 13. Files affected

| File | Change |
|---|---|
| `docs/superpowers/specs/khata-design-tokens.md` | superseded — mark and keep for history |
| `PRODUCT.md` | amend pinned visual constraint; amend the Bengali-strings line |
| `app/build.gradle.kts` | `minSdk 30 → 33`; Haze dependency |
| `gradle/libs.versions.toml` | Haze |
| `core/ui/theme/Color.kt` | full replacement |
| `core/ui/theme/Type.kt` | full replacement; three font families |
| `core/ui/theme/Dimens.kt` | radii, spacing |
| `core/ui/theme/KhataTheme.kt` | direction contract; theme preference wiring |
| `core/ui/component/` | `KhataGlass`, `CategoryDot`, `ContextHeader`, `Headspace` |
| `app/src/test/.../ContrastTest.kt` | rewrite to the 112 pairwise assertions |
| `feature/ledger/LedgerScreen.kt` | rebuild to §8 |
| `feature/editor/TransactionEditorScreen.kt` | rebuild to §8 |
| `navigation/KhataNavHost.kt` | hub-and-back; configurable start destination |
| new: `feature/hub/` | Modules hub |
| new: `feature/settings/` | theme tuner |
| new: `app/src/main/res/font/` | four bundled families |
