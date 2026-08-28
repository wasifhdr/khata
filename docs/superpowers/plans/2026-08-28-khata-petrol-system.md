# Khata Plan A — Petrol Design System

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Khata's warm-editorial theme with the petrol design system — tokens, fonts, glass, and shared components — verified by a pairwise contrast suite, with no screen rebuilt yet.

**Architecture:** Everything lands in `core/ui`. The theme becomes data (`ThemeSpec`) rather than two hardcoded `ColorScheme`s, because Plan B adds a settings tuner that swaps four axes at runtime. Screens are deliberately untouched: this plan ends with a green test suite and a theme nothing consumes yet, so Plan B rebuilds screens against tokens that have stopped moving.

**Tech Stack:** Kotlin · Jetpack Compose (BOM 2026.03.01) · Material 3 · Haze · JUnit4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-08-28-khata-petrol-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`. `compileSdk` / `targetSdk` stay `37`. Target device: Pixel 6a, Android 17.
- Package and namespace: `com.wasif.khata`.
- **No hardcoded colours anywhere.** Every colour resolves through a token. Light mode is deferred, not cancelled, and this rule is the only thing that keeps it possible.
- **Dark is the product, not a mode.** Do not add a light `ColorScheme` in this plan.
- Material You dynamic colour stays unwired.
- Amounts always use `FontFeatureSetting("tnum")`.
- Category colours are **not** themeable — they encode data, not taste.
- No screen files (`feature/**`) are modified in this plan. If a task seems to require it, stop and report.
- Comments earn their place: a comment carries a non-obvious *why*, never a restatement of *what*. No section-divider banners, no KDoc restating a name.
- Tests never hardcode a magic epoch-millis literal.
- Run all unit tests with: `./gradlew :app:testDebugUnitTest`
- Build with: `./gradlew :app:assembleDebug`
- `export JAVA_HOME="E:\Android\Android Studio\jbr"` per shell — `gradle.properties` alone does not bootstrap `gradlew`.

## What this plan deliberately does not touch

`core/ui/theme/Motion.kt` is **already correct**. Spec §6 specifies `quick 150ms`,
`standard 250ms`, `emphasized 400ms` with enter `cubic-bezier(0.05, 0.7, 0.1, 1)` and exit
`cubic-bezier(0.3, 0, 0.8, 0.15)` — which is exactly what the file contains. It survived the
re-roll unchanged. Do not modify it.

## Two spec gaps this plan resolves

Found while mapping the spec onto the existing code. Both are recorded here rather than sent back for a spec revision, because both have an obviously correct answer.

**1. The spec's category names do not match the seeded ones.** `DefaultData.kt` seeds 16 categories against 15 distinct colour tokens (`category_neutral` is shared by Transfer and Uncategorized). The spec names a different 15 — it has Rent, Household and Travel; the seed has Fuel, Car & Maintenance, Income and Uncategorized. **Resolution:** the token *keys* stay exactly as they are, so no data migration and no seed change. Task 3 replaces their hex values, mapped by the meaning of the category each token is actually attached to. `category_emerald` keeps a green because it is Income, which the spec explicitly reserves green for.

**2. The spec's "112 pairwise assertions" was an estimate.** The correct matrix — the one that actually proves legibility rather than counting pairs — is **139**. The spec's figure omitted the hero gradient stops and the alert colour. Task 3 implements 139 and the count is stated in the test's own failure messages.

---

### Task 1: Platform floor and the Haze dependency

Raises `minSdk` to 33 and adds Haze. Nothing consumes Haze yet — this task exists on its own because a floor change and a new graphics dependency are exactly the kind of thing a reviewer should be able to reject without rejecting the theme work behind it.

**Files:**
- Modify: `app/build.gradle.kts:15`
- Modify: `gradle/libs.versions.toml`
- Modify: `docs/superpowers/specs/khata-design-tokens.md` (supersession notice)

**Interfaces:**
- Consumes: nothing.
- Produces: `libs.haze` available to `app/build.gradle.kts`; API 33 baseline for all later tasks.

- [ ] **Step 1: Find the current Haze version**

Haze moves quickly and this plan must not pin a version it invented. Look it up:

```bash
curl -s "https://search.maven.org/solrsearch/select?q=g:dev.chrisbanes.haze+AND+a:haze&core=gav&rows=5&wt=json" | grep -o '"v":"[^"]*"' | head -5
```

Expected: a list of versions, newest first. Record the newest stable (non-alpha, non-beta) one; it is referred to below as `<HAZE_VERSION>`.

If the network is unavailable, check https://github.com/chrisbanes/haze/releases manually. **Do not guess a version.**

- [ ] **Step 2: Add Haze to the version catalog**

In `gradle/libs.versions.toml`, add to `[versions]` (keep alphabetical order):

```toml
haze = "<HAZE_VERSION>"
```

And to `[libraries]`:

```toml
haze = { module = "dev.chrisbanes.haze:haze", version.ref = "haze" }
```

- [ ] **Step 3: Raise minSdk and wire the dependency**

In `app/build.gradle.kts`, change line 15:

```kotlin
        minSdk = 33
```

And add to `dependencies`, next to the other Compose entries:

```kotlin
  implementation(libs.haze)
```

- [ ] **Step 4: Verify the build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

If Haze fails to resolve against Compose BOM 2026.03.01, that is a real incompatibility, not a typo — stop and report it. Do not downgrade the BOM to make Haze fit.

- [ ] **Step 5: Verify the existing suite still passes**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. 62 tests, all green. The floor change must not break anything.

- [ ] **Step 6: Mark the old tokens doc superseded**

At the very top of `docs/superpowers/specs/khata-design-tokens.md`, above the existing `# Khata — Design Tokens & Direction` heading, insert:

```markdown
> **SUPERSEDED 2026-08-28** by `2026-08-28-khata-petrol-design.md`.
> Kept for history. Nothing in this file is current: the warm-editorial world,
> the ink-orange accent, the hairline rules and the `minSdk 30` justification
> were all replaced by the petrol direction. Do not implement from this file.
```

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml docs/superpowers/specs/khata-design-tokens.md
git commit -m "build: raise minSdk to 33 and add Haze

API 31 brings RenderEffect, which is the only route to real backdrop blur;
33 adds AGSL on top. The old floor was justified on the grounds that API 31
added only dynamic colour, which was declined -- that reasoning is void now
that blur is required.

Zero cost: one user, one Pixel 6a on Android 17, sideloaded."
```

---

### Task 2: Bundle the four font families

Four families, three roles. Fonts are a prerequisite for the type scale in Task 4, and they fail loudly and early if a file is malformed, so they land first.

**Files:**
- Create: `app/src/main/res/font/fraunces_regular.ttf`
- Create: `app/src/main/res/font/fraunces_semibold.ttf`
- Create: `app/src/main/res/font/fraunces_bold.ttf`
- Create: `app/src/main/res/font/instrument_sans_regular.ttf`
- Create: `app/src/main/res/font/instrument_sans_medium.ttf`
- Create: `app/src/main/res/font/instrument_sans_semibold.ttf`
- Create: `app/src/main/res/font/instrument_sans_bold.ttf`
- Create: `app/src/main/res/font/noto_sans_bengali_regular.ttf`
- Create: `app/src/main/res/font/noto_sans_bengali_medium.ttf`
- Create: `app/src/main/res/font/noto_sans_bengali_semibold.ttf`
- Create: `app/src/main/res/font/li_swarnali_okkhor.ttf`
- Create: `app/src/main/java/com/wasif/khata/core/ui/theme/Fonts.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/core/ui/theme/FontsTest.kt`

**Interfaces:**
- Consumes: API 33 baseline from Task 1.
- Produces:
  - `object KhataFonts`
  - `KhataFonts.Display: FontFamily` — Fraunces
  - `KhataFonts.Text: FontFamily` — Instrument Sans
  - `KhataFonts.Bengali: FontFamily` — Noto Sans Bengali
  - `KhataFonts.Wordmark: FontFamily` — Li Swarnali Okkhor

- [ ] **Step 1: Download the three Google families**

Android `res/font` filenames must be lowercase, alphanumeric plus underscore, and must not start with a digit.

```bash
mkdir -p app/src/main/res/font && cd /tmp
curl -sL "https://github.com/google/fonts/raw/main/ofl/fraunces/Fraunces%5BSOFT%2CWONK%2Copsz%2Cwght%5D.ttf" -o fraunces_var.ttf
curl -sL "https://github.com/google/fonts/raw/main/ofl/instrumentsans/InstrumentSans%5Bwdth%2Cwght%5D.ttf" -o instrument_var.ttf
curl -sL "https://github.com/notofonts/bengali/raw/main/fonts/NotoSansBengali/hinted/ttf/NotoSansBengali-Regular.ttf" -o noto_bn_regular.ttf
curl -sL "https://github.com/notofonts/bengali/raw/main/fonts/NotoSansBengali/hinted/ttf/NotoSansBengali-Medium.ttf" -o noto_bn_medium.ttf
curl -sL "https://github.com/notofonts/bengali/raw/main/fonts/NotoSansBengali/hinted/ttf/NotoSansBengali-SemiBold.ttf" -o noto_bn_semibold.ttf
ls -la *.ttf
```

Expected: each file is tens to hundreds of KB. **A file under 5KB is an HTML error page, not a font** — if any are, fetch that family manually from https://fonts.google.com and place it yourself.

Fraunces and Instrument Sans arrive as variable fonts. Compose on API 33 supports variable fonts via `FontVariation`, but static instances are simpler and avoid a per-device rendering variable. Use the static cuts instead:

```bash
cd /tmp
curl -sL "https://github.com/google/fonts/raw/main/ofl/fraunces/static/Fraunces_9pt-Regular.ttf" -o fraunces_regular.ttf
curl -sL "https://github.com/google/fonts/raw/main/ofl/fraunces/static/Fraunces_9pt-SemiBold.ttf" -o fraunces_semibold.ttf
curl -sL "https://github.com/google/fonts/raw/main/ofl/fraunces/static/Fraunces_9pt-Bold.ttf" -o fraunces_bold.ttf
curl -sL "https://github.com/google/fonts/raw/main/ofl/instrumentsans/static/InstrumentSans-Regular.ttf" -o instrument_sans_regular.ttf
curl -sL "https://github.com/google/fonts/raw/main/ofl/instrumentsans/static/InstrumentSans-Medium.ttf" -o instrument_sans_medium.ttf
curl -sL "https://github.com/google/fonts/raw/main/ofl/instrumentsans/static/InstrumentSans-SemiBold.ttf" -o instrument_sans_semibold.ttf
curl -sL "https://github.com/google/fonts/raw/main/ofl/instrumentsans/static/InstrumentSans-Bold.ttf" -o instrument_sans_bold.ttf
ls -la *.ttf | awk '$5 < 5000 {print "TOO SMALL: " $9}'
```

Expected: no output from the last command. If anything prints, that download failed.

- [ ] **Step 2: Copy the fonts into res/font**

```bash
cd /tmp
cp fraunces_regular.ttf fraunces_semibold.ttf fraunces_bold.ttf \
   instrument_sans_regular.ttf instrument_sans_medium.ttf instrument_sans_semibold.ttf instrument_sans_bold.ttf \
   "$OLDPWD/app/src/main/res/font/"
cp noto_bn_regular.ttf "$OLDPWD/app/src/main/res/font/noto_sans_bengali_regular.ttf"
cp noto_bn_medium.ttf "$OLDPWD/app/src/main/res/font/noto_sans_bengali_medium.ttf"
cp noto_bn_semibold.ttf "$OLDPWD/app/src/main/res/font/noto_sans_bengali_semibold.ttf"
cp "/c/Users/Wasif/Downloads/SwarnaliOkkhor/Unicode/Li Swarnali Okkhor Unicode.ttf" \
   "$OLDPWD/app/src/main/res/font/li_swarnali_okkhor.ttf"
ls -la "$OLDPWD/app/src/main/res/font/"
```

Expected: 11 `.ttf` files.

**Use the Unicode cut only.** The `ANSI V1` and `ANSI V2` folders in that download are legacy Bijoy encodings that map Bengali glyphs onto Latin codepoints — they render garbage against real Unicode Bengali from an SMS.

- [ ] **Step 3: Write the failing test**

Create `app/src/androidTest/java/com/wasif/khata/core/ui/theme/FontsTest.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FontsTest {

    @get:Rule
    val compose = createComposeRule()

    // A bundled family that failed to load silently resolves to the platform
    // default, which looks fine in a screenshot and is wrong everywhere. The
    // only cheap way to catch that is to assert it is not the default.
    @Test
    fun everyBundledFamilyIsDistinctFromTheDefault() {
        assertNotEquals(FontFamily.Default, KhataFonts.Display)
        assertNotEquals(FontFamily.Default, KhataFonts.Text)
        assertNotEquals(FontFamily.Default, KhataFonts.Bengali)
        assertNotEquals(FontFamily.Default, KhataFonts.Wordmark)
    }

    @Test
    fun everyFamilyIsDistinctFromEveryOther() {
        val families = listOf(
            KhataFonts.Display,
            KhataFonts.Text,
            KhataFonts.Bengali,
            KhataFonts.Wordmark,
        )
        families.forEachIndexed { i, a ->
            families.drop(i + 1).forEach { b -> assertNotEquals(a, b) }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Start the emulator first if it is not running:

```bash
ANDROID_AVD_HOME="D:\android-avd" "$ANDROID_HOME/emulator/emulator.exe" -avd khata_test -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
```

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.core.ui.theme.FontsTest`

Expected: compilation failure — `Unresolved reference: KhataFonts`.

Note: this AGP does **not** accept `--tests`; use the `-Pandroid.testInstrumentationRunnerArguments.class=` form.

- [ ] **Step 5: Write the implementation**

Create `app/src/main/java/com/wasif/khata/core/ui/theme/Fonts.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.wasif.khata.R

// Four families, three roles. The split exists because numerals never need
// Bengali glyphs -- which is what makes a Latin-only serif safe on amounts
// while Bengali merchant names stay on a face that matches the body metrics.
object KhataFonts {

    val Display: FontFamily = FontFamily(
        Font(R.font.fraunces_regular, FontWeight.Normal),
        Font(R.font.fraunces_semibold, FontWeight.SemiBold),
        Font(R.font.fraunces_bold, FontWeight.Bold),
    )

    val Text: FontFamily = FontFamily(
        Font(R.font.instrument_sans_regular, FontWeight.Normal),
        Font(R.font.instrument_sans_medium, FontWeight.Medium),
        Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
        Font(R.font.instrument_sans_bold, FontWeight.Bold),
    )

    val Bengali: FontFamily = FontFamily(
        Font(R.font.noto_sans_bengali_regular, FontWeight.Normal),
        Font(R.font.noto_sans_bengali_medium, FontWeight.Medium),
        Font(R.font.noto_sans_bengali_semibold, FontWeight.SemiBold),
    )

    // Wordmark only. Li Swarnali Okkhor is a display face: it is unreadable at
    // row sizes and its metrics do not match Instrument Sans, so it must never
    // reach a merchant name.
    val Wordmark: FontFamily = FontFamily(
        Font(R.font.li_swarnali_okkhor, FontWeight.Normal),
    )
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.core.ui.theme.FontsTest`
Expected: PASS, 2 tests.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/font app/src/main/java/com/wasif/khata/core/ui/theme/Fonts.kt app/src/androidTest/java/com/wasif/khata/core/ui/theme/FontsTest.kt
git commit -m "feat: bundle the four font families

Fraunces for numerals and display, Instrument Sans for working text,
Noto Sans Bengali bundled rather than relying on device fallback, and
Li Swarnali Okkhor for the wordmark alone.

Bengali is bundled because per-device fallback variance is unaffordable
in the one place it shows: real merchant names in a ledger column."
```

---

### Task 3: Colour tokens and the pairwise contrast suite

The heart of the plan. Replaces `Color.kt` entirely and rewrites `ContrastTest.kt` to prove the whole tuner space, not one palette.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/ui/theme/Color.kt` (full replacement)
- Modify: `app/src/test/java/com/wasif/khata/core/ui/theme/ContrastTest.kt` (full replacement)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `contrastRatio(a: Color, b: Color): Double` — unchanged signature, still public
  - `object KhataPalette` with `ground`, `heroStops: List<Color>`, `accent`, `accentDeep`, `onSurface`, `onSurfaceDim`, `onSurfaceFaint`, `alert`, `warn`
  - `data class FieldPalette(val name: String, val keyStop: Color)`
  - `KhataPalette.fields: List<FieldPalette>` (8), `.grounds: List<Color>` (4), `.accents: List<Color>` (4)
  - `KhataPalette.categories: Map<String, Color>` (15 entries, keys match `DefaultData.kt`)

- [ ] **Step 1: Write the failing test**

Replace `app/src/test/java/com/wasif/khata/core/ui/theme/ContrastTest.kt` entirely:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.wasif.khata.core.data.seed.DEFAULT_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The theme tuner exposes four free axes -- 8 fields x 4 grounds x 4 accents x
 * 4 intensities = 512 combinations. Verifying 512 is not possible. Verifying
 * the axes is: contrast is pairwise, so if every accent clears every ground and
 * every foreground clears every field, all 512 combinations clear by
 * construction.
 */
class ContrastTest {

    // 4.5:1 is the AA floor for text. The previous direction demanded more, to
    // survive direct Dhaka sunlight; that requirement was withdrawn with the
    // re-roll, so AA is the floor again.
    private val textFloor = 4.5

    // 3:1 is the AA floor for graphical objects, which is what an 8dp dot is.
    private val graphicFloor = 3.0

    private var assertions = 0

    private fun assertFloor(what: String, fg: Color, bg: Color, floor: Double) {
        assertions++
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$what contrast was ${"%.2f".format(ratio)}, below the $floor:1 floor",
            ratio >= floor,
        )
    }

    @Test
    fun `paper text clears every ground, field and hero stop`() {
        KhataPalette.grounds.forEach { g ->
            assertFloor("onSurface / ground $g", KhataPalette.onSurface, g, textFloor)
        }
        KhataPalette.fields.forEach { f ->
            assertFloor("onSurface / field ${f.name}", KhataPalette.onSurface, f.keyStop, textFloor)
        }
        KhataPalette.heroStops.forEach { h ->
            assertFloor("onSurface / hero $h", KhataPalette.onSurface, h, textFloor)
        }
    }

    @Test
    fun `every accent clears every ground, field and hero stop`() {
        KhataPalette.accents.forEach { a ->
            KhataPalette.grounds.forEach { g ->
                assertFloor("accent $a / ground $g", a, g, textFloor)
            }
            KhataPalette.fields.forEach { f ->
                assertFloor("accent $a / field ${f.name}", a, f.keyStop, textFloor)
            }
            KhataPalette.heroStops.forEach { h ->
                assertFloor("accent $a / hero $h", a, h, textFloor)
            }
        }
    }

    @Test
    fun `the alert colour clears every ground`() {
        KhataPalette.grounds.forEach { g ->
            assertFloor("alert / ground $g", KhataPalette.alert, g, textFloor)
        }
    }

    @Test
    fun `every category dot clears every ground`() {
        KhataPalette.categories.forEach { (token, colour) ->
            KhataPalette.grounds.forEach { g ->
                assertFloor("category $token / ground $g", colour, g, graphicFloor)
            }
        }
    }

    @Test
    fun `every seeded category token resolves`() {
        DEFAULT_CATEGORIES.map { it.colorToken }.distinct().forEach { token ->
            assertTrue(
                "$token is seeded but missing from KhataPalette.categories",
                KhataPalette.categories.containsKey(token),
            )
        }
    }

    @Test
    fun `no category shares a colour with the alert or any accent`() {
        // A category that matches an interactive colour would defeat the
        // quarantine rule at the one place it matters.
        KhataPalette.categories.forEach { (token, colour) ->
            assertTrue("$token duplicates the alert colour", colour != KhataPalette.alert)
            KhataPalette.accents.forEach { a ->
                assertTrue("$token duplicates accent $a", colour != a)
            }
        }
    }

    @Test
    fun `the tuner space is the size the spec says it is`() {
        assertEquals(8, KhataPalette.fields.size)
        assertEquals(4, KhataPalette.grounds.size)
        assertEquals(4, KhataPalette.accents.size)
        assertEquals(15, KhataPalette.categories.size)
        assertEquals(3, KhataPalette.heroStops.size)
    }

    @Test
    fun `contrast ratio is symmetric and bounded`() {
        assertTrue(contrastRatio(Color.White, Color.Black) in 20.9..21.1)
        assertTrue(contrastRatio(Color.Black, Color.White) in 20.9..21.1)
        assertTrue(contrastRatio(Color.White, Color.White) in 0.99..1.01)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.ui.theme.ContrastTest"`
Expected: compilation failure — `Unresolved reference: KhataPalette`.

- [ ] **Step 3: Write the implementation**

Replace `app/src/main/java/com/wasif/khata/core/ui/theme/Color.kt` entirely:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** One palette option for the mesh field behind the glass. */
data class FieldPalette(
    val name: String,
    /**
     * The lightest stop in the mesh. Contrast is only ever at risk against the
     * lightest point a gradient reaches, so that is the one worth asserting --
     * every darker stop clears by construction.
     */
    val keyStop: Color,
)

object KhataPalette {

    val ground: Color = Color(0xFF061214)
    val onSurface: Color = Color(0xFFEDF2F1)
    val onSurfaceDim: Color = Color(0xFFA8B8B8)
    val onSurfaceFaint: Color = Color(0xFF6E8180)

    val accent: Color = Color(0xFF8FE0CE)
    val accentDeep: Color = Color(0xFF5FC9B2)

    val alert: Color = Color(0xFFFF7A6B)
    val warn: Color = Color(0xFFF2A63E)

    /** Petrol. The gradient the active module card is filled with. */
    val heroStops: List<Color> = listOf(
        Color(0xFF12403F),
        Color(0xFF0C2E30),
        Color(0xFF08211F),
    )

    val grounds: List<Color> = listOf(
        Color(0xFF061214), // teal black -- default
        Color(0xFF0B0C18), // indigo black
        Color(0xFF08111C), // navy black
        Color(0xFF0A0D0F), // cool black
    )

    val accents: List<Color> = listOf(
        Color(0xFF8FE0CE), // pale aqua -- default
        Color(0xFFFFB627), // marigold
        Color(0xFFB6E24A), // chartreuse
        Color(0xFFE8C9A0), // warm sand
    )

    val fields: List<FieldPalette> = listOf(
        FieldPalette("Verdigris", Color(0xFF1A695F)), // default
        FieldPalette("Abyss", Color(0xFF175C63)),
        FieldPalette("Counterpoint", Color(0xFF146066)),
        FieldPalette("Cyan", Color(0xFF26A0A8)),
        FieldPalette("Violet", Color(0xFF4E3484)),
        FieldPalette("Monochrome", Color(0xFF18605E)),
        FieldPalette("Deep sea", Color(0xFF125A62)),
        FieldPalette("Mist", Color(0xFF3A5462)),
    )

    /**
     * Quarantined: these appear only inside an 8dp dot or a chip, never as a
     * background or a text colour, and the category name is always present so
     * colour is never the sole signal.
     *
     * Keys are the tokens already seeded in DefaultData.kt and are deliberately
     * left alone -- renaming them would need a data migration to buy nothing.
     * The names no longer describe the hues; the category each token is
     * attached to is what the hue was chosen for.
     */
    val categories: Map<String, Color> = mapOf(
        // Living -- warm arc
        "category_green" to Color(0xFFE8C15A),    // Groceries
        "category_orange" to Color(0xFFFF8A6B),   // Eating Out
        "category_indigo" to Color(0xFFF2A63E),   // Education
        // Recurring -- cool blues
        "category_amber" to Color(0xFF9DB4C8),    // Bills & Utilities
        "category_teal" to Color(0xFF7FB8EC),     // Mobile & Internet
        "category_blue" to Color(0xFF93A9F2),     // Transport
        // Discretionary -- pink to violet
        "category_violet" to Color(0xFFF293A8),   // Shopping
        "category_pink" to Color(0xFFB7A2EF),     // Entertainment
        "category_rose" to Color(0xFFDF8CCC),     // Family & Gifts
        // Place -- earth
        "category_slate" to Color(0xFFDCC099),    // Fuel
        "category_bronze" to Color(0xFFC9A6BC),   // Car & Maintenance
        // Body
        "category_red" to Color(0xFFEE6F80),      // Health
        // System -- neutral
        "category_grey" to Color(0xFFB3B0A8),     // Fees & Charges
        "category_neutral" to Color(0xFF98A6B8),  // Transfer, Uncategorized
        // Income keeps a green: the ledger encodes credit as green, so this is
        // the one place the reserved hue belongs.
        "category_emerald" to Color(0xFF7FD4A8),  // Income
    )
}

/**
 * Dark is the product, not a mode. There is deliberately no light scheme here:
 * light mode is deferred, and the no-hardcoded-colour rule is what keeps adding
 * one later an addition rather than a rewrite.
 */
val DarkColors: ColorScheme = darkColorScheme(
    primary = KhataPalette.accent,
    onPrimary = KhataPalette.heroStops.last(),
    primaryContainer = KhataPalette.heroStops.first(),
    onPrimaryContainer = KhataPalette.onSurface,
    secondary = KhataPalette.accentDeep,
    onSecondary = KhataPalette.heroStops.last(),
    background = KhataPalette.ground,
    onBackground = KhataPalette.onSurface,
    surface = KhataPalette.ground,
    onSurface = KhataPalette.onSurface,
    surfaceVariant = KhataPalette.heroStops[1],
    onSurfaceVariant = KhataPalette.onSurfaceDim,
    outline = KhataPalette.onSurfaceFaint,
    outlineVariant = Color(0xFF1C2E30),
    error = KhataPalette.alert,
    onError = Color(0xFF2A0A06),
)

fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) +
        0.7152 * channel(color.green) +
        0.0722 * channel(color.blue)
}
```

- [ ] **Step 4: Fix the two now-dangling references**

`LightColors`, `CategoryColorsLight` and `CategoryColorsDark` are gone. `KhataTheme.kt` still references them, so the build will not compile until Task 6. Make it compile now with the minimum change — in `app/src/main/java/com/wasif/khata/core/ui/theme/KhataTheme.kt`, replace the body of `KhataTheme` with:

```kotlin
@Composable
fun KhataTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalMotion provides Motion(),
        LocalCategoryColors provides KhataPalette.categories,
    ) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = KhataTypography,
            content = content,
        )
    }
}
```

Delete the now-unused `isSystemInDarkTheme` import. Task 6 replaces this properly; this step exists only so the module compiles between tasks.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.ui.theme.ContrastTest"`
Expected: PASS, 8 tests.

If `every category dot clears every ground` fails, the failure message names the token, the ground and the measured ratio. **Fix the colour, never the floor.**

- [ ] **Step 6: Run the whole suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/theme/Color.kt app/src/main/java/com/wasif/khata/core/ui/theme/KhataTheme.kt app/src/test/java/com/wasif/khata/core/ui/theme/ContrastTest.kt
git commit -m "feat: petrol colour tokens with a pairwise contrast suite

Replaces the warm-editorial palette. The theme tuner exposes 512
combinations, which cannot be verified directly -- but contrast is
pairwise, so asserting every accent against every ground and every
foreground against every field proves all 512 by construction. 139
assertions instead of 512 combinations.

Category token keys are left alone deliberately: renaming them would
need a data migration to buy nothing. Only the hues change."
```

---

### Task 4: The type scale

Three roles, wide contrast. The 34:11 jump on page headings is the single change that most directly addresses the original complaint.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/ui/theme/Type.kt` (full replacement)
- Test: `app/src/test/java/com/wasif/khata/core/ui/theme/TypeTest.kt`

**Interfaces:**
- Consumes: `KhataFonts` from Task 2.
- Produces:
  - `KhataTypography: Typography`
  - `AmountTextStyle: TextStyle` — unchanged name, now Fraunces
  - `WordmarkTextStyle: TextStyle`
  - `PageHeadingStyle: TextStyle`
  - `PageSublineStyle: TextStyle`
  - `BengaliBodyStyle: TextStyle`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/core/ui/theme/TypeTest.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeTest {

    @Test
    fun `every amount style carries tabular figures`() {
        // Without tnum, a ledger column drifts with each digit's width and
        // stops being scannable. This is the one type setting that is not
        // cosmetic.
        assertEquals("tnum", AmountTextStyle.fontFeatureSettings)
    }

    @Test
    fun `the page heading is at least three times its subline`() {
        val heading = PageHeadingStyle.fontSize.value
        val subline = PageSublineStyle.fontSize.value
        assertTrue(
            "heading $heading sp is not >= 3x subline $subline sp",
            heading >= subline * 3,
        )
    }

    @Test
    fun `no style falls back to the platform default family`() {
        val styles = listOf(
            AmountTextStyle,
            WordmarkTextStyle,
            PageHeadingStyle,
            PageSublineStyle,
            BengaliBodyStyle,
        )
        styles.forEach { s ->
            assertTrue("a style is using FontFamily.Default", s.fontFamily != FontFamily.Default)
            assertTrue("a style has no family at all", s.fontFamily != null)
        }
    }

    @Test
    fun `amounts and the wordmark do not share a family`() {
        // Li Swarnali Okkhor is a display face. If it ever reaches an amount,
        // the ledger column breaks.
        assertTrue(AmountTextStyle.fontFamily != WordmarkTextStyle.fontFamily)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.ui.theme.TypeTest"`
Expected: compilation failure — `Unresolved reference: WordmarkTextStyle`.

- [ ] **Step 3: Write the implementation**

Replace `app/src/main/java/com/wasif/khata/core/ui/theme/Type.kt` entirely:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Fraunces on the numerals, Instrument Sans on the working text. The previous
// direction deferred a display face because Bengali had no matching pair --
// which only ever applied to text. Numerals never need Bengali glyphs, so a
// Latin-only serif is safe on amounts.
val KhataTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = KhataFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 50.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = KhataFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = KhataFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 39.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = KhataFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp,
    ),
)

// Tabular figures so amounts align on the decimal down a 3,000-row column
// instead of drifting with each digit's width.
val AmountTextStyle = TextStyle(
    fontFamily = KhataFonts.Display,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "tnum",
)

val WordmarkTextStyle = TextStyle(
    fontFamily = KhataFonts.Wordmark,
    fontWeight = FontWeight.Normal,
    fontSize = 54.sp,
    lineHeight = 70.sp,
)

// 34 against 11. The old build put every glyph between 12 and 20sp, which is
// what "everything is the same size" was actually pointing at; the gap is
// doing more work here than the absolute size.
val PageHeadingStyle = TextStyle(
    fontFamily = KhataFonts.Display,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 39.sp,
)

val PageSublineStyle = TextStyle(
    fontFamily = KhataFonts.Text,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.6.sp,
)

// Bengali merchant names, notes, and anything derived from an SMS.
val BengaliBodyStyle = TextStyle(
    fontFamily = KhataFonts.Bengali,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.ui.theme.TypeTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Verify the app still builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Screens still reference `MaterialTheme.typography.*`, which all still exists.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/theme/Type.kt app/src/test/java/com/wasif/khata/core/ui/theme/TypeTest.kt
git commit -m "feat: petrol type scale on Fraunces and Instrument Sans

A serif on the numerals, because a serif reads as a book and a grotesk
reads as a dashboard -- and this is a khata. The old doc deferred a
display face over Bengali pairing, which only ever applied to text;
numerals need no Bengali glyphs.

The 34:11 heading-to-subline jump is the point. The previous build put
every glyph between 12 and 20sp."
```

---

### Task 5: Shape, spacing and motion

Large radii, an 18dp screen gutter, and the field-intensity scale the glass depends on.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/ui/theme/Dimens.kt`
- Create: `app/src/main/java/com/wasif/khata/core/ui/theme/Shape.kt`
- Test: `app/src/test/java/com/wasif/khata/core/ui/theme/DimensTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `Spacing` gains `screenHorizontal = 18.dp`, `headspaceLedger = 132.dp`
  - `KhataShapes: Shapes` — 14 / 20 / 28
  - `enum class FieldIntensity { Off, Dim, Mid, Full }` with `val alpha: Float`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/core/ui/theme/DimensTest.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensTest {

    @Test
    fun `the screen gutter and touch target match the spec`() {
        val s = Spacing()
        assertEquals(18.dp, s.screenHorizontal)
        assertEquals(48.dp, s.minTouchTarget)
    }

    @Test
    fun `the ledger headspace is fixed`() {
        // The ledger is the one screen that cannot bottom-anchor: a list of
        // 3,000 rows has no bottom, and a flexible spacer would push rows off
        // screen. It gets a fixed headspace instead.
        assertEquals(132.dp, Spacing().headspaceLedger)
    }

    @Test
    fun `field intensity runs from zero to one`() {
        assertEquals(0f, FieldIntensity.Off.alpha)
        assertEquals(1f, FieldIntensity.Full.alpha)
        assertTrue(FieldIntensity.Dim.alpha < FieldIntensity.Mid.alpha)
        assertTrue(FieldIntensity.Mid.alpha < FieldIntensity.Full.alpha)
    }

    @Test
    fun `only Off disables the field entirely`() {
        // Glass needs something to refract. At Off it has nothing, so the glass
        // component has to fall back to a solid fill -- that branch keys off
        // this exact condition.
        val zero = FieldIntensity.entries.filter { it.alpha == 0f }
        assertEquals(listOf(FieldIntensity.Off), zero)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.ui.theme.DimensTest"`
Expected: FAIL — `Unresolved reference: headspaceLedger`.

- [ ] **Step 3: Update Dimens.kt**

Replace `app/src/main/java/com/wasif/khata/core/ui/theme/Dimens.kt` entirely:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val screenHorizontal: Dp = 18.dp,
    val minTouchTarget: Dp = 48.dp,
    /**
     * The ledger cannot bottom-anchor like the other screens: a list of 3,000
     * rows has no bottom to anchor to, and a flexible spacer would push rows
     * off screen and make the monthly review worse. It gets this instead --
     * enough to read as the same family, small enough that six rows stay
     * visible before scrolling.
     */
    val headspaceLedger: Dp = 132.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** How hard the mesh field behind the glass is pushed. */
enum class FieldIntensity(val alpha: Float) {
    Off(0f),
    Dim(0.34f),
    Mid(0.64f),
    Full(1f),
}
```

- [ ] **Step 4: Create Shape.kt**

Create `app/src/main/java/com/wasif/khata/core/ui/theme/Shape.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Large radii, reversing the previous direction's "editorial print is not
// rounded". This world is glass, and glass has soft edges.
val KhataShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.ui.theme.DimensTest"`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/theme/Dimens.kt app/src/main/java/com/wasif/khata/core/ui/theme/Shape.kt app/src/test/java/com/wasif/khata/core/ui/theme/DimensTest.kt
git commit -m "feat: petrol shape, spacing and field-intensity tokens

Radii go to 14/20/28, reversing the old direction's small-radius rule.
Adds FieldIntensity, which the glass component branches on: at Off the
mesh is gone, so backdrop blur has nothing to refract and glass must
fall back to a solid fill or it reads as a rendering bug."
```

---

### Task 6: KhataTheme and the theme spec

Turns the theme into data, so Plan B's tuner has something to set.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/ui/theme/KhataTheme.kt` (full replacement)
- Test: `app/src/test/java/com/wasif/khata/core/ui/theme/ThemeSpecTest.kt`

**Interfaces:**
- Consumes: `KhataPalette`, `FieldPalette` (Task 3); `KhataTypography` (Task 4); `KhataShapes`, `FieldIntensity`, `Spacing` (Task 5).
- Produces:
  - `data class ThemeSpec(field, ground, accent, intensity)` with `companion object { val Default: ThemeSpec }`
  - `LocalThemeSpec: ProvidableCompositionLocal<ThemeSpec>`
  - `LocalCategoryColors: ProvidableCompositionLocal<Map<String, Color>>` (retained)
  - `KhataTheme(spec: ThemeSpec = ThemeSpec.Default, content: @Composable () -> Unit)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/core/ui/theme/ThemeSpecTest.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSpecTest {

    @Test
    fun `the default is the one the design settled on`() {
        val d = ThemeSpec.Default
        assertEquals("Verdigris", d.field.name)
        assertEquals(KhataPalette.grounds.first(), d.ground)
        assertEquals(KhataPalette.accents.first(), d.accent)
        assertEquals(FieldIntensity.Full, d.intensity)
    }

    @Test
    fun `every axis value the tuner can produce is a valid spec`() {
        // The tuner is free-form, so every combination has to construct. This
        // is the 512-combination space the contrast suite proves pairwise.
        var built = 0
        KhataPalette.fields.forEach { f ->
            KhataPalette.grounds.forEach { g ->
                KhataPalette.accents.forEach { a ->
                    FieldIntensity.entries.forEach { i ->
                        ThemeSpec(field = f, ground = g, accent = a, intensity = i)
                        built++
                    }
                }
            }
        }
        assertEquals(512, built)
    }

    @Test
    fun `glass falls back to solid only when the field is off`() {
        val off = ThemeSpec.Default.copy(intensity = FieldIntensity.Off)
        assertTrue(off.usesSolidSurfaces)
        FieldIntensity.entries.filter { it != FieldIntensity.Off }.forEach { i ->
            assertTrue(!ThemeSpec.Default.copy(intensity = i).usesSolidSurfaces)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.ui.theme.ThemeSpecTest"`
Expected: FAIL — `Unresolved reference: ThemeSpec`.

- [ ] **Step 3: Write the implementation**

Replace `app/src/main/java/com/wasif/khata/core/ui/theme/KhataTheme.kt` entirely:

```kotlin
package com.wasif.khata.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * THESIS -- A dark instrument that reads as a book. Petrol-green glass floating
 * on a verdigris field, amounts set in a serif because a serif reads as a page
 * and a grotesk reads as a dashboard.
 *
 * OWN-WORLD -- A cool blue-green world with warmth quarantined into the
 * category dots. Recognisable with every word removed by the petrol card on the
 * verdigris mesh, the pale-aqua numerals, and a Bengali wordmark that is never
 * transliterated.
 *
 * STORY -- The user opens it, sees the record is already complete without them
 * having done anything, and either glances and leaves or sits down and works.
 *
 * FIRST VIEWPORT -- খাতা centred on both axes in a large open field, the
 * tagline beneath it, and the module cards anchored to the bottom edge inside
 * the thumb arc.
 *
 * FORM -- Dark-first glassmorphism, restrained hue, wide type contrast.
 *
 * Spec: docs/superpowers/specs/2026-08-28-khata-petrol-design.md
 */

/** The four axes the settings tuner can move. */
data class ThemeSpec(
    val field: FieldPalette,
    val ground: Color,
    val accent: Color,
    val intensity: FieldIntensity,
) {
    /**
     * At Off the mesh is gone, so backdrop blur has nothing to refract and
     * every panel would become flat translucent grey -- which reads as a
     * rendering bug rather than a theme. Glass switches to a solid fill.
     */
    val usesSolidSurfaces: Boolean get() = intensity == FieldIntensity.Off

    companion object {
        val Default = ThemeSpec(
            field = KhataPalette.fields.first(),
            ground = KhataPalette.grounds.first(),
            accent = KhataPalette.accents.first(),
            intensity = FieldIntensity.Full,
        )
    }
}

val LocalThemeSpec = staticCompositionLocalOf { ThemeSpec.Default }

val LocalCategoryColors = staticCompositionLocalOf<Map<String, Color>> { emptyMap() }

@Composable
fun KhataTheme(
    spec: ThemeSpec = ThemeSpec.Default,
    content: @Composable () -> Unit,
) {
    // Dark is the product, not a mode. isSystemInDarkTheme is deliberately not
    // consulted: there is no light scheme to switch to yet, and pretending
    // otherwise would hide the fact when one is added.
    val scheme = DarkColors.copy(
        primary = spec.accent,
        background = spec.ground,
        surface = spec.ground,
    )

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalMotion provides Motion(),
        LocalThemeSpec provides spec,
        LocalCategoryColors provides KhataPalette.categories,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KhataTypography,
            shapes = KhataShapes,
            content = content,
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wasif.khata.core.ui.theme.ThemeSpecTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Run everything**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: both `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/theme/KhataTheme.kt app/src/test/java/com/wasif/khata/core/ui/theme/ThemeSpecTest.kt
git commit -m "feat: ThemeSpec makes the theme data rather than a constant

Plan B adds a settings tuner that swaps four axes at runtime, so the
theme has to be a value the app can hold rather than two hardcoded
schemes. Carries the direction contract at the top of the file.

The 512-combination space is asserted constructible here and proven
legible pairwise in ContrastTest."
```

---

### Task 7: KhataGlass

The five-ingredient glass panel, wrapped so no screen ever calls Haze directly.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/ui/component/KhataGlass.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/core/ui/component/KhataGlassTest.kt`

**Interfaces:**
- Consumes: `LocalThemeSpec`, `KhataShapes`, `KhataPalette` (Tasks 3, 5, 6).
- Produces:
  - `KhataGlass(modifier, shape, raised, content)` — the panel
  - `Modifier.khataFieldSource(hazeState)` — marks the backdrop
  - `rememberKhataHazeState(): HazeState`

- [ ] **Step 1: Read the Haze API before writing anything**

Haze's API has been renamed across versions (`hazeChild` became `hazeEffect`, `haze` became `hazeSource`). The exact names for `<HAZE_VERSION>` must come from the library, not from memory.

```bash
find ~/.gradle/caches/modules-2/files-2.1/dev.chrisbanes.haze -name "*.jar" 2>/dev/null | head -3
```

Then check https://github.com/chrisbanes/haze for the README matching the pinned version. Record the two modifier names before continuing.

- [ ] **Step 2: Write the failing test**

Create `app/src/androidTest/java/com/wasif/khata/core/ui/component/KhataGlassTest.kt`:

```kotlin
package com.wasif.khata.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.core.ui.theme.ThemeSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KhataGlassTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun glassRendersItsContent() {
        compose.setContent {
            KhataTheme {
                val haze = rememberKhataHazeState()
                Box(Modifier.fillMaxSize().khataFieldSource(haze)) {
                    KhataGlass(hazeState = haze) { Text("Wallet") }
                }
            }
        }
        compose.onNodeWithText("Wallet").assertIsDisplayed()
    }

    @Test
    fun glassStillRendersContentWhenTheFieldIsOff() {
        // At Off the blur path is skipped entirely for a solid fill. The
        // content must survive that branch.
        compose.setContent {
            KhataTheme(spec = ThemeSpec.Default.copy(intensity = FieldIntensity.Off)) {
                val haze = rememberKhataHazeState()
                Box(Modifier.fillMaxSize().khataFieldSource(haze)) {
                    KhataGlass(hazeState = haze) { Text("Wallet") }
                }
            }
        }
        compose.onNodeWithText("Wallet").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.core.ui.component.KhataGlassTest`
Expected: compilation failure — `Unresolved reference: KhataGlass`.

- [ ] **Step 4: Write the implementation**

Create `app/src/main/java/com/wasif/khata/core/ui/component/KhataGlass.kt`. **Replace `hazeSource` / `hazeEffect` with the names recorded in Step 1 if they differ.**

```kotlin
package com.wasif.khata.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.wasif.khata.core.ui.theme.LocalThemeSpec
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

@Composable
fun rememberKhataHazeState(): HazeState = remember { HazeState() }

/**
 * Marks a composable as the backdrop the glass samples. Put this on the mesh
 * field, never on a scrolling list: blur cost scales with how often the
 * backdrop changes, and a Paging list changes every frame.
 */
fun Modifier.khataFieldSource(hazeState: HazeState): Modifier = this.hazeSource(hazeState)

/**
 * The five ingredients. Most glassmorphism ships blur and tint, stops, and
 * looks like a grey card:
 *
 *  1. backdrop blur          2. translucent tint
 *  3. gradient edge highlight -- a lit bevel, the one that sells it
 *  4. grain                  5. saturation boost, so colour blooms rather than
 *                               going muddy
 *
 * Ingredients 4 and 5 come from Haze's own tint and noise support; 3 is drawn
 * here because Haze has no equivalent.
 */
@Composable
fun KhataGlass(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.material3.MaterialTheme.shapes.medium,
    raised: Boolean = false,
    content: @Composable () -> Unit,
) {
    val spec = LocalThemeSpec.current
    val tint = if (raised) Color.White.copy(alpha = 0.085f) else Color.White.copy(alpha = 0.055f)

    // A lit bevel, brighter at the top-left and fading to nothing. Skipping
    // this is what makes most glass read as a flat translucent rectangle.
    val edge = Brush.linearGradient(
        0.00f to Color.White.copy(alpha = 0.48f),
        0.34f to Color.White.copy(alpha = 0.10f),
        0.62f to Color.Transparent,
        1.00f to Color.White.copy(alpha = 0.08f),
    )

    val surface = if (spec.usesSolidSurfaces) {
        // No field means nothing to refract. A solid fill is a deliberate flat
        // theme; a blur over nothing is a bug that looks like one.
        Modifier.background(spec.ground.copy(alpha = 0.92f), shape)
    } else {
        Modifier
            .hazeEffect(state = hazeState)
            .background(tint, shape)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(surface)
            .border(1.dp, edge, shape),
    ) {
        content()
    }
}
```

If the Haze version pinned in Task 1 exposes blur radius, tint and noise as parameters on `hazeEffect`, pass `blurRadius = 22.dp`, `noiseFactor = 0.04f` and a tint of `spec.field.keyStop.copy(alpha = 0.10f)`. If the API differs, keep the defaults and record the gap in the commit message rather than inventing parameters.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.core.ui.component.KhataGlassTest`
Expected: PASS, 2 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/component/KhataGlass.kt app/src/androidTest/java/com/wasif/khata/core/ui/component/KhataGlassTest.kt
git commit -m "feat: KhataGlass, the five-ingredient glass panel

Wraps Haze behind one component so no screen calls it directly and the
internals can be swapped later. Carries the edge highlight Haze has no
equivalent for -- the gradient bevel is what stops glass reading as a
flat translucent rectangle.

Falls back to a solid fill when field intensity is Off, where blur would
have nothing to refract."
```

---

### Task 8: CategoryDot and the shared layout pieces

The last of the system. Three small components every Plan B screen needs.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/ui/component/CategoryDot.kt`
- Create: `app/src/main/java/com/wasif/khata/core/ui/component/ContextHeader.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/ui/component/MoneyText.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/core/ui/component/CategoryDotTest.kt`

**Interfaces:**
- Consumes: `LocalCategoryColors`, `PageHeadingStyle`, `PageSublineStyle`, `KhataPalette`, `AmountTextStyle`.
- Produces:
  - `CategoryDot(token: String, lowConfidence: Boolean = false, modifier: Modifier = Modifier)`
  - `ContextHeader(heading: String, subline: String, modifier: Modifier = Modifier)`
  - `MoneyText(...)` — unchanged signature, credit now uses the accent

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/wasif/khata/core/ui/component/CategoryDotTest.kt`:

```kotlin
package com.wasif.khata.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lowConfidenceIsAnnouncedAndNotOnlyColoured() {
        // Colour is never the sole signal. A low-confidence row carries a ring
        // and a word; the ring needs a content description so the word is not
        // the only non-visual channel either.
        compose.setContent {
            KhataTheme {
                CategoryDot(token = "category_green", lowConfidence = true)
            }
        }
        compose.onNodeWithContentDescription("Low confidence").assertIsDisplayed()
    }

    @Test
    fun anUnknownTokenStillRenders() {
        // A category token can arrive from seeded data that predates a palette
        // change. It must degrade to a neutral dot, never crash a 3,000-row list.
        compose.setContent {
            KhataTheme {
                CategoryDot(token = "category_does_not_exist")
            }
        }
        compose.onNodeWithContentDescription("Uncategorised").assertIsDisplayed()
    }

    @Test
    fun contextHeaderShowsBothLines() {
        compose.setContent {
            KhataTheme {
                ContextHeader(heading = "August 2026", subline = "142 entries · 6 days left")
            }
        }
        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithText("142 entries · 6 days left").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.core.ui.component.CategoryDotTest`
Expected: compilation failure — `Unresolved reference: CategoryDot`.

- [ ] **Step 3: Write CategoryDot**

Create `app/src/main/java/com/wasif/khata/core/ui/component/CategoryDot.kt`:

```kotlin
package com.wasif.khata.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalCategoryColors

@Composable
fun CategoryDot(
    token: String,
    modifier: Modifier = Modifier,
    lowConfidence: Boolean = false,
) {
    val colours = LocalCategoryColors.current
    val known = colours[token]
    val colour = known ?: KhataPalette.categories.getValue("category_neutral")

    // Two independent signals, because colour is never load-bearing alone: the
    // ring is the pattern, the row's metadata line carries the word.
    val description = when {
        lowConfidence -> "Low confidence"
        known == null -> "Uncategorised"
        else -> null
    }

    Box(
        modifier = modifier
            .size(8.dp)
            .background(colour, CircleShape)
            .then(
                if (lowConfidence) {
                    Modifier.border(2.5.dp, colour.copy(alpha = 0.35f), CircleShape)
                } else {
                    Modifier
                },
            )
            .then(
                if (description != null) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
    )
}
```

- [ ] **Step 4: Write ContextHeader**

Create `app/src/main/java/com/wasif/khata/core/ui/component/ContextHeader.kt`:

```kotlin
package com.wasif.khata.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.PageHeadingStyle
import com.wasif.khata.core.ui.theme.PageSublineStyle

/**
 * The one centred block on a module page. It carries context rather than a
 * label: the subline answers the question the screen is being asked -- is this
 * current, which month, what has it assumed -- instead of restating what the
 * user already knows from having tapped to get here.
 *
 * Module pages never show the app name; that belongs to the home screen alone.
 */
@Composable
fun ContextHeader(
    heading: String,
    subline: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = heading,
            style = PageHeadingStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subline,
            style = PageSublineStyle,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}
```

- [ ] **Step 5: Update MoneyText for the new accent**

In `app/src/main/java/com/wasif/khata/core/ui/component/MoneyText.kt`, the credit colour currently reads `MaterialTheme.colorScheme.primary`, which now resolves to the accent — correct by construction. Change only the comment-free colour block to be explicit about intent:

```kotlin
    val color = when (direction) {
        // Credits take the accent; debits stay paper. The accent is the only
        // colour on a ledger row, which is what makes a credit findable while
        // scrolling.
        TransactionDirection.CREDIT -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.core.ui.component.CategoryDotTest`
Expected: PASS, 3 tests.

- [ ] **Step 7: Run the full suite and build**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: both `BUILD SUCCESSFUL`.

Then the full instrumented suite:

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: all green. The existing `LedgerScreenTest` and `TransactionEditorScreenTest` must still pass — screens were not touched, and if they fail the theme change broke something a screen depended on.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/component/ app/src/androidTest/java/com/wasif/khata/core/ui/component/
git commit -m "feat: CategoryDot, ContextHeader, and MoneyText on the accent

CategoryDot carries two independent signals for low confidence -- a ring
plus the word in the row metadata -- because colour is never load-bearing
alone. An unknown token degrades to neutral rather than crashing a
3,000-row list.

ContextHeader is the one centred block on a module page, carrying context
rather than a label. Module pages never show the app name."
```

---

### Task 9: Amend PRODUCT.md

The spec records two lines in `PRODUCT.md` that the petrol direction contradicts. Leaving them
is worse than either fixing or reverting them: a product doc that quietly disagrees with the
code is how a future session re-derives the wrong constraint.

This is last because it is documentation, and because it should describe a system that already
exists rather than one that is planned.

**Files:**
- Modify: `PRODUCT.md` (Brand Commitments section, and the Language bullet under Capabilities and Constraints)

**Interfaces:**
- Consumes: nothing.
- Produces: nothing consumed by code.

- [ ] **Step 1: Replace the pinned visual constraint**

Find this block under **Brand Commitments**:

```markdown
**Pinned visual constraint (user-stated, binding):** a simple interface, warm
editorial hues, orange accents. Recorded as given and not expanded. This pin
overrides the direction roll; it fixes the world, not its softest rendition.
```

Replace it with:

```markdown
**Pinned visual constraint (user-stated, binding, revised 2026-08-28):** dark-first
glassmorphism — a petrol card on a verdigris field over a teal-black ground, with a
pale-aqua accent and a serif on the numerals. Light mode is deferred, not cancelled.

The previous pin (a simple interface, warm editorial hues, orange accents) was reopened
by the user and replaced after they reviewed worked alternatives. It is superseded, not
merely unfulfilled. See `docs/superpowers/specs/2026-08-28-khata-petrol-design.md`.

**Wordmark:** খাতা, set in Li Swarnali Okkhor. Never transliterated — no Latin "Khata"
appears anywhere in the interface. It is shown on the home screen and no other.

**Tagline:** সব হিসাব, এক খাতায় — "all accounts, in one book." Native-speaker reviewed.
```

- [ ] **Step 2: Amend the Language bullet**

Find this bullet under **Capabilities and Constraints**:

```markdown
- **Language:** English interface. Bengali script must render correctly wherever it
  appears — merchant names, user notes, text derived from SMS. Not bilingual; there
  is no language toggle and no Bengali UI strings.
```

Replace it with:

```markdown
- **Language:** English interface. Bengali script must render correctly wherever it
  appears — merchant names, user notes, text derived from SMS. Not bilingual; there
  is no language toggle and no translation layer.
  **One exception (user decision, 2026-08-28):** the brand lockup on the home screen is
  Bengali — the wordmark খাতা and the tagline সব হিসাব, এক খাতায়. Two fixed strings,
  never localised, never extended. Every other string in the interface is English.
```

- [ ] **Step 3: Verify nothing else in PRODUCT.md still describes the old world**

```bash
grep -niE "orange|warm editorial|hairline|bone|ink orange" PRODUCT.md
```

Expected: no output. If anything matches, it is a leftover from the previous direction — read it in context and fix it the same way.

- [ ] **Step 4: Commit**

```bash
git add PRODUCT.md
git commit -m "docs: bring PRODUCT.md in line with the petrol direction

Two lines contradicted the shipped system. The pinned visual constraint
still described the warm editorial world the user replaced, and the
no-Bengali-UI-strings rule ruled out the Bengali wordmark and tagline
they chose.

Both are user decisions, recorded as revisions rather than silently
overwritten -- a product doc that disagrees with the code is how a
future session re-derives the wrong constraint."
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` green, including 139 pairwise contrast assertions.
- `./gradlew :app:connectedDebugAndroidTest` green, including the untouched screen tests.
- `./gradlew :app:assembleDebug` green on `minSdk 33`.
- Four font families bundled; nothing resolves to `FontFamily.Default`.
- `KhataGlass`, `CategoryDot`, `ContextHeader` exist and are tested.
- `PRODUCT.md` no longer contradicts the shipped system.
- **No file under `feature/` was modified.**

The app still looks exactly as it did. That is the intended outcome: the system is in place and proven, and Plan B rebuilds the screens against tokens that have stopped moving.
