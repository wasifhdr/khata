# Glass and Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the glass system and the motion contract real consumers — the verdigris field on every screen, glass on the Wallet and Editor surfaces the spec names, and shared-axis / fade-through navigation that honours the system "Remove animations" setting.

**Architecture:** Three token layers already exist and are wired to nothing: `KhataGlass` (used only by two dormant hub tiles), `FieldBackdrop` (private to `ModulesScreen`), and `Motion`/`LocalMotion` (consumed by nothing). This plan does not invent anything new. It promotes `FieldBackdrop` into a shared `FieldScaffold` that every screen uses, points the existing `KhataGlass` at the surfaces §5 names, and adds one new file of pure transition builders that `KhataNavHost` reads from `LocalMotion`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Haze 1.5.3 (`dev.chrisbanes.haze`), Navigation Compose, Hilt, JUnit4 + Robolectric, Compose UI test.

## Global Constraints

- Money is `Money(val minor: Long)` — Long paisa only. Never floating point in a money path.
- All date boundaries are computed in **Asia/Dhaka** (UTC+6, no DST).
- **No hardcoded colours in feature code.** Colours come from `MaterialTheme.colorScheme`, `KhataPalette`, or `LocalCategoryColors`. New literal colours belong in `core/ui/theme/Color.kt`.
- **Contrast:** body text ≥ 4.5:1, graphical objects ≥ 3:1, measured against the colour *actually composited beneath* the element — not a raw gradient stop.
- **Colour is never load-bearing alone.** Any state signalled by colour also carries a shape and a word.
- The theme is user-tunable at runtime across field × ground × accent × intensity (8×4×4×4). Every change must hold for all combinations, not just the default.
- Comments carry a non-obvious **why**, not a restatement of the what.
- A test's name must not overstate what it checks.
- `minSdk 33`, `compileSdk`/`targetSdk 37`.
- Blur cost scales with blurred area × how often the backdrop changes. **Only the static field is ever a Haze source.** A scrolling list is never a Haze source and never sits behind glass.

## Decisions already taken (do not re-litigate)

1. **Field density is the user's global `FieldIntensity` preference, applied identically on every screen.** Spec §1's per-screen Full/Mid/Quiet rule is dropped. Task 3 amends the spec so the contradiction does not survive in the document.
2. **Glass goes on:** Wallet month figures, the Editor's Spent/Received pill, the Editor's account/category chips, and the Ledger search field. **Not** on the Settings selection controls — the tuner is where surfaces must read neutrally so swatches are judged accurately.
3. Consequence of (1) that the plan must handle: Ledger row text now sits over the mesh at whatever intensity the user picks, so **every text role the Ledger renders must be asserted against the field key stops at Full intensity** (Task 4), not just against the ground.

## File Structure

| File | Responsibility |
|---|---|
| `core/ui/theme/Motion.kt` (modify) | Duration/easing tokens **plus** `Motion.forDurationScale(Float)`, the pure reduction rule. |
| `core/ui/theme/KhataTheme.kt` (modify) | Observes the system animator duration scale and provides the resulting `Motion` through `LocalMotion`. |
| `core/ui/motion/KhataTransitions.kt` (create) | Pure builders: shared-axis X (four directions) and fade-through. No Compose state, no navigation types — just `Motion` in, `EnterTransition`/`ExitTransition` out. |
| `navigation/KhataNavHost.kt` (modify) | Chooses shared-axis vs fade-through per route pair and passes the four transitions to `NavHost`. |
| `core/ui/component/FieldScaffold.kt` (create) | The public `FieldBackdrop` (moved out of `ModulesScreen`) and `FieldScaffold`, the one composable every screen uses to get ground + field + a `HazeState`. |
| `feature/hub/ModulesScreen.kt` (modify) | Drops its private `FieldBackdrop`; uses `FieldScaffold`. |
| `feature/wallet/WalletScreen.kt` (modify) | `FieldScaffold`; month figures become glass. |
| `feature/editor/TransactionEditorScreen.kt` (modify) | `FieldScaffold`; direction pill and chips become glass. |
| `feature/ledger/LedgerScreen.kt` (modify) | `FieldScaffold`; search field becomes glass. |
| `feature/settings/SettingsScreen.kt` (modify) | `FieldScaffold` only — no glass. |

---

### Task 1: Motion tokens honour "Remove animations"

`Motion` currently hardcodes 150/250/400ms. A user who turned animations off in Accessibility settings did so because motion makes them unwell; a faster version of the same motion does not help them. The spec requires an instant cut.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/ui/theme/Motion.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/ui/theme/KhataTheme.kt`
- Test: `app/src/test/java/com/wasif/khata/core/ui/theme/MotionTest.kt` (create)

**Interfaces:**
- Produces: `Motion.forDurationScale(scale: Float): Motion`, `Motion.isInstant: Boolean`, and `@Composable fun rememberSystemMotion(): Motion`. Task 2 consumes `Motion` fields (`quick`, `standard`, `enter`, `exit`); Task 2's transitions must produce a genuinely instant cut when `isInstant` is true.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/wasif/khata/core/ui/theme/MotionTest.kt`:

```kotlin
package com.wasif.khata.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun `a duration scale of zero collapses every duration to an instant cut`() {
        val motion = Motion.forDurationScale(0f)

        assertEquals("quick should cut instantly", 0, motion.quick)
        assertEquals("standard should cut instantly", 0, motion.standard)
        assertEquals("emphasized should cut instantly", 0, motion.emphasized)
        assertTrue(motion.isInstant)
    }

    @Test
    fun `the normal duration scale keeps the spec durations`() {
        val motion = Motion.forDurationScale(1f)

        assertEquals(150, motion.quick)
        assertEquals(250, motion.standard)
        assertEquals(400, motion.emphasized)
        assertFalse(motion.isInstant)
    }

    @Test
    fun `a slowed animator scale is not treated as removed animations`() {
        // Developer options can set 0.5x or 10x. Only exactly 0 means the user
        // asked for no motion; scaling durations ourselves would double-apply,
        // because the platform already scales what it hands the animator.
        assertEquals(250, Motion.forDurationScale(0.5f).standard)
        assertEquals(250, Motion.forDurationScale(10f).standard)
        assertFalse(Motion.forDurationScale(0.5f).isInstant)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
./gradlew.bat test --tests '*MotionTest*'
```

Expected: FAIL to compile — `Unresolved reference: forDurationScale`.

- [ ] **Step 3: Add the reduction rule to `Motion`**

Replace the whole of `core/ui/theme/Motion.kt` with:

```kotlin
package com.wasif.khata.core.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

data class Motion(
    val quick: Int = 150,
    val standard: Int = 250,
    val emphasized: Int = 400,
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
) {
    /** True when the user has asked for no motion, so callers can skip work rather than animate to nowhere. */
    val isInstant: Boolean get() = standard == 0

    companion object {
        /**
         * "Remove animations" in Accessibility settings reports itself as an
         * animator duration scale of exactly 0. Honouring it means an instant
         * cut, not a fast animation -- someone who turned motion off because it
         * makes them ill is not served by a 50ms version of the same motion.
         *
         * Any other scale is left alone. Developer options' 0.5x/10x already
         * apply at the platform layer, so scaling here as well would compound.
         */
        fun forDurationScale(scale: Float): Motion =
            if (scale == 0f) Motion(quick = 0, standard = 0, emphasized = 0) else Motion()
    }
}

val LocalMotion = staticCompositionLocalOf { Motion() }

/**
 * The animator duration scale as a live value. An observer rather than a single
 * read, because toggling "Remove animations" does not recreate the Activity --
 * a one-shot read would leave the app animating until the next cold start.
 */
@Composable
fun rememberSystemMotion(): Motion {
    val resolver = LocalContext.current.contentResolver
    var scale by remember(resolver) {
        mutableFloatStateOf(
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }

    DisposableEffect(resolver) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                scale = Settings.Global.getFloat(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }
        }
        resolver.registerContentObserver(uri, false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return remember(scale) { Motion.forDurationScale(scale) }
}
```

- [ ] **Step 4: Provide the observed motion from `KhataTheme`**

In `core/ui/theme/KhataTheme.kt`, inside `@Composable fun KhataTheme`, change the `LocalMotion` line. It currently reads:

```kotlin
        LocalMotion provides Motion(),
```

Replace with:

```kotlin
        LocalMotion provides rememberSystemMotion(),
```

- [ ] **Step 5: Run the full unit suite**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
./gradlew.bat test
```

Expected: BUILD SUCCESSFUL. `MotionTest` adds 3 tests to the 131 baseline (134 total). Run the **full** suite with no `--tests` filter — filtered runs on this project have twice hidden breakage in hand-written fakes that only a full compile surfaces.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/theme/Motion.kt app/src/main/java/com/wasif/khata/core/ui/theme/KhataTheme.kt app/src/test/java/com/wasif/khata/core/ui/theme/MotionTest.kt
git commit -m "feat: motion tokens honour the system Remove animations setting"
```

---

### Task 2: Shared-axis and fade-through navigation

`KhataNavHost` uses stock `NavHost` transitions today. Spec §6 requires shared-axis between hub and module, fade-through within a module.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/ui/motion/KhataTransitions.kt`
- Modify: `app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt`
- Test: `app/src/test/java/com/wasif/khata/core/ui/motion/KhataTransitionsTest.kt` (create)

**Interfaces:**
- Consumes: `Motion` and `LocalMotion` from Task 1.
- Produces: `isHubTransition(from: String?, to: String?): Boolean` plus `sharedAxisXEnter/Exit/PopEnter/PopExit(m: Motion)` and `fadeThroughEnter/Exit(m: Motion)`.

- [ ] **Step 1: Write the failing test**

The transitions themselves are Compose animation values and are not usefully assertable in a unit test — asserting that `fadeIn()` returns a `fadeIn()` tests nothing. The route-classification rule *is* real logic with real branches, so that is what gets tested.

Create `app/src/test/java/com/wasif/khata/core/ui/motion/KhataTransitionsTest.kt`:

```kotlin
package com.wasif.khata.core.ui.motion

import com.wasif.khata.navigation.KhataRoutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KhataTransitionsTest {

    @Test
    fun `hub to module is a shared-axis move in both directions`() {
        assertTrue(isHubTransition(KhataRoutes.Modules, KhataRoutes.Wallet))
        assertTrue(isHubTransition(KhataRoutes.Wallet, KhataRoutes.Modules))
    }

    @Test
    fun `hub to settings is a shared-axis move`() {
        // Settings is not a module, but it is reached from the hub and returns
        // to it, so it sits on the same horizontal track.
        assertTrue(isHubTransition(KhataRoutes.Modules, KhataRoutes.Settings))
    }

    @Test
    fun `moves within a module fade through rather than sliding`() {
        // Wallet -> Ledger -> Editor are all inside the wallet module. Sliding
        // would claim a peer relationship these screens do not have.
        assertFalse(isHubTransition(KhataRoutes.Wallet, KhataRoutes.Ledger))
        assertFalse(isHubTransition(KhataRoutes.Ledger, KhataRoutes.EditorNew))
        assertFalse(isHubTransition(KhataRoutes.Ledger, KhataRoutes.EditorEdit))
    }

    @Test
    fun `an unknown route falls back to fade-through rather than crashing`() {
        // Route can be null on a graph node. Fade-through is the safe default:
        // it makes no spatial claim, so it cannot be wrong about one.
        assertFalse(isHubTransition(null, KhataRoutes.Wallet))
        assertFalse(isHubTransition(KhataRoutes.Wallet, null))
    }
}
```

Note what is deliberately **not** tested here: there is no "an instant motion produces zero-duration transitions" case. `Motion.forDurationScale(0f)` is already pinned by `MotionTest`, and a test in this file asserting it again would carry a name promising transition coverage while checking a token — the exact naming drift the merge-gate review flagged twice on the last branch. The transitions consume `m.standard` directly, so Task 1's assertion is the one that matters; the device walkthrough in Step 6 is what confirms the wiring.

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
./gradlew.bat test --tests '*KhataTransitionsTest*'
```

Expected: FAIL to compile — `Unresolved reference: isHubTransition`.

- [ ] **Step 3: Write the transition builders**

Create `app/src/main/java/com/wasif/khata/core/ui/motion/KhataTransitions.kt`:

```kotlin
package com.wasif.khata.core.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.wasif.khata.core.ui.theme.Motion
import com.wasif.khata.navigation.KhataRoutes

/**
 * How far a screen travels on the shared axis, as a fraction of its width.
 * A full-width slide reads as a page turn; this reads as two panels on one
 * track, which is what the hub and a module actually are.
 */
private const val SharedAxisTravel = 0.20f

/**
 * True when this route pair crosses the hub boundary, which is the only place
 * a spatial relationship exists to express. Everything else is a move *within*
 * a module, where sliding would assert a sideways adjacency that is not real.
 *
 * Null-safe on both sides: a graph node has no route, and fade-through is the
 * honest fallback because it makes no spatial claim at all.
 */
fun isHubTransition(from: String?, to: String?): Boolean =
    from == KhataRoutes.Modules || to == KhataRoutes.Modules

fun sharedAxisXEnter(m: Motion): EnterTransition =
    slideInHorizontally(tween(m.standard, easing = m.enter)) { (it * SharedAxisTravel).toInt() } +
        fadeIn(tween(m.standard, easing = m.enter))

fun sharedAxisXExit(m: Motion): ExitTransition =
    slideOutHorizontally(tween(m.standard, easing = m.exit)) { -(it * SharedAxisTravel).toInt() } +
        fadeOut(tween(m.standard, easing = m.exit))

fun sharedAxisXPopEnter(m: Motion): EnterTransition =
    slideInHorizontally(tween(m.standard, easing = m.enter)) { -(it * SharedAxisTravel).toInt() } +
        fadeIn(tween(m.standard, easing = m.enter))

fun sharedAxisXPopExit(m: Motion): ExitTransition =
    slideOutHorizontally(tween(m.standard, easing = m.exit)) { (it * SharedAxisTravel).toInt() } +
        fadeOut(tween(m.standard, easing = m.exit))

/**
 * Fade-through: the outgoing screen leaves before the incoming one arrives, so
 * the two never cross-dissolve into an unreadable double image. The incoming
 * screen also grows slightly, which reads as "forward" without claiming a
 * direction on any axis.
 *
 * At an instant Motion every duration and delay here is 0, so this collapses to
 * a hard cut rather than a very fast animation.
 */
fun fadeThroughEnter(m: Motion): EnterTransition =
    fadeIn(tween(m.standard, delayMillis = m.quick, easing = m.enter)) +
        scaleIn(
            animationSpec = tween(m.standard, delayMillis = m.quick, easing = m.enter),
            initialScale = 0.92f,
        )

fun fadeThroughExit(m: Motion): ExitTransition = fadeOut(tween(m.quick, easing = m.exit))
```

- [ ] **Step 4: Wire the transitions into `KhataNavHost`**

In `navigation/KhataNavHost.kt`, add these imports:

```kotlin
import com.wasif.khata.core.ui.motion.fadeThroughEnter
import com.wasif.khata.core.ui.motion.fadeThroughExit
import com.wasif.khata.core.ui.motion.isHubTransition
import com.wasif.khata.core.ui.motion.sharedAxisXEnter
import com.wasif.khata.core.ui.motion.sharedAxisXExit
import com.wasif.khata.core.ui.motion.sharedAxisXPopEnter
import com.wasif.khata.core.ui.motion.sharedAxisXPopExit
import com.wasif.khata.core.ui.theme.LocalMotion
```

Then, immediately before the `NavHost(` call, read the motion once:

```kotlin
    val motion = LocalMotion.current
```

And replace the `NavHost(navController = navController, startDestination = start) {` line with:

```kotlin
    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = {
            val hub = isHubTransition(initialState.destination.route, targetState.destination.route)
            if (hub) sharedAxisXEnter(motion) else fadeThroughEnter(motion)
        },
        exitTransition = {
            val hub = isHubTransition(initialState.destination.route, targetState.destination.route)
            if (hub) sharedAxisXExit(motion) else fadeThroughExit(motion)
        },
        popEnterTransition = {
            val hub = isHubTransition(initialState.destination.route, targetState.destination.route)
            if (hub) sharedAxisXPopEnter(motion) else fadeThroughEnter(motion)
        },
        popExitTransition = {
            val hub = isHubTransition(initialState.destination.route, targetState.destination.route)
            if (hub) sharedAxisXPopExit(motion) else fadeThroughExit(motion)
        },
    ) {
```

- [ ] **Step 5: Run the full unit suite**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
./gradlew.bat test
```

Expected: BUILD SUCCESSFUL, 139 tests (134 + 5 new).

- [ ] **Step 6: Verify on device**

`KhataNavHost` has no automated coverage — this project has no Hilt navigation test infra, and building it is out of scope. Verify by hand and record what you saw.

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/Wasif/AppData/Local/Android/Sdk"
export ANDROID_AVD_HOME="D:\android-avd"
./gradlew.bat installDebug
```

Walk and report each: hub → Wallet slides left as a pair; back reverses it; Wallet → Ledger fades through with no sideways movement; Ledger → Editor likewise; hub → Settings slides. Then enable Accessibility → Remove animations **without restarting the app** and confirm every one of those becomes an instant cut. That last step is the whole point of Task 1's ContentObserver — a one-shot read would still be animating.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/motion/KhataTransitions.kt app/src/main/java/com/wasif/khata/navigation/KhataNavHost.kt app/src/test/java/com/wasif/khata/core/ui/motion/KhataTransitionsTest.kt
git commit -m "feat: shared-axis and fade-through navigation transitions"
```

---

### Task 3: Promote `FieldBackdrop` into a shared `FieldScaffold`

`FieldBackdrop` is `private` inside `ModulesScreen.kt`, so the mesh cannot appear anywhere else. Five screens are about to need the identical "ground + field + haze source" stack; writing it five times is how it drifts.

This task is **behaviour-preserving** — the hub must look identical afterwards. It also amends the spec so the density contradiction does not survive in the document.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/ui/component/FieldScaffold.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/hub/ModulesScreen.kt`
- Modify: `docs/superpowers/specs/2026-08-28-khata-petrol-design.md`
- Test: `app/src/androidTest/java/com/wasif/khata/core/ui/component/FieldScaffoldTest.kt` (create)

**Interfaces:**
- Consumes: `LocalThemeSpec`, `ThemeSpec.intensity`, `FieldIntensity.alpha`, `KhataPalette.heroStops`, and `rememberKhataHazeState()` / `khataFieldSource()` from `core/ui/component/KhataGlass.kt`.
- Produces: `@Composable fun FieldScaffold(modifier: Modifier = Modifier, content: @Composable BoxScope.(HazeState) -> Unit)` and `@Composable fun FieldBackdrop(modifier: Modifier = Modifier)`. Tasks 4–6 call `FieldScaffold` and pass the `HazeState` it yields to `KhataGlass`.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/wasif/khata/core/ui/component/FieldScaffoldTest.kt`:

```kotlin
package com.wasif.khata.core.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.core.ui.theme.ThemeSpec
import org.junit.Rule
import org.junit.Test

class FieldScaffoldTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun contentRendersAboveTheField() {
        compose.setContent {
            KhataTheme {
                FieldScaffold(Modifier.fillMaxSize()) { Text("above the mesh") }
            }
        }

        compose.onNodeWithText("above the mesh").assertIsDisplayed()
    }

    @Test
    fun contentStillRendersAtZeroIntensityWhenTheMeshIsGone() {
        // At Off the backdrop early-returns after painting the ground. A scaffold
        // that dropped its content on that path would take the whole screen with it.
        compose.setContent {
            KhataTheme(spec = ThemeSpec.Default.copy(intensity = FieldIntensity.Off)) {
                FieldScaffold(Modifier.fillMaxSize()) { Text("still here") }
            }
        }

        compose.onNodeWithText("still here").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/Wasif/AppData/Local/Android/Sdk"
export ANDROID_AVD_HOME="D:\android-avd"
./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.core.ui.component.FieldScaffoldTest
```

Expected: FAIL to compile — `Unresolved reference: FieldScaffold`.

- [ ] **Step 3: Create `FieldScaffold.kt`**

Create `app/src/main/java/com/wasif/khata/core/ui/component/FieldScaffold.kt`. The `FieldBackdrop` body below is moved verbatim from `ModulesScreen.kt` — the pool geometry is tuned and must not change, or the hub's appearance changes with it.

```kotlin
package com.wasif.khata.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalThemeSpec
import dev.chrisbanes.haze.HazeState

/**
 * Ground, mesh field, and the [HazeState] the field registers itself against --
 * the stack every screen needs and none of them should own.
 *
 * The field is deliberately the *only* Haze source in the app. Blur cost scales
 * with how often the backdrop changes, so sampling a static mesh is affordable
 * on every screen while sampling a Paging list would not be affordable on any.
 * Glass drawn over this therefore refracts the field, never the content.
 */
@Composable
fun FieldScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(HazeState) -> Unit,
) {
    val spec = LocalThemeSpec.current
    val haze = rememberKhataHazeState()

    Box(modifier.background(spec.ground)) {
        FieldBackdrop(Modifier.fillMaxSize().khataFieldSource(haze))
        content(haze)
    }
}

/**
 * Soft radial pools rather than a full-bleed linear gradient: a linear wash
 * covers every pixel at its own alpha and swamps the content, where pools leave
 * most of the ground untouched and read as light falling on a surface.
 *
 * Each pool is capped well below full opacity even at Full intensity, because
 * the key stop is already the lightest colour the mesh should ever reach.
 */
@Composable
fun FieldBackdrop(modifier: Modifier = Modifier) {
    val spec = LocalThemeSpec.current
    val strength = spec.intensity.alpha

    Box(
        modifier.drawBehind {
            drawRect(spec.ground)
            if (strength <= 0f) return@drawBehind

            fun pool(colour: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(colour.copy(alpha = alpha * strength), Color.Transparent),
                        center = Offset(size.width * cx, size.height * cy),
                        radius = size.minDimension * r,
                    ),
                )
            }

            pool(spec.field.keyStop, 0.85f, 0.14f, 0.02f, 1.15f)
            pool(spec.field.keyStop, 0.55f, 0.92f, 0.16f, 0.95f)
            pool(KhataPalette.heroStops.first(), 0.60f, 0.70f, 0.78f, 1.00f)
            pool(spec.ground, 0.70f, 0.10f, 0.95f, 0.90f)
        },
    )
}
```

- [ ] **Step 4: Migrate the hub onto it**

In `feature/hub/ModulesScreen.kt`:

1. **Delete** the entire `private fun FieldBackdrop(...)` block (the KDoc above it moves with it — it is already reproduced in the new file).
2. Replace the opening of `ModulesContent`'s layout. It currently reads:

```kotlin
    val spacing = LocalSpacing.current
    val spec = LocalThemeSpec.current
    val haze = rememberKhataHazeState()

    Box(Modifier.fillMaxSize().background(spec.ground)) {
        // The field is the backdrop the glass samples. It is a static surface,
        // which is why glass is affordable here and forbidden on a Paging list.
        FieldBackdrop(Modifier.fillMaxSize().khataFieldSource(haze))
```

Replace with:

```kotlin
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
```

3. Fix the imports: add `com.wasif.khata.core.ui.component.FieldScaffold`; remove `khataFieldSource`, `rememberKhataHazeState`, and — only if nothing else in the file still uses them — `LocalThemeSpec`, `background`, `Brush`, `Offset`, `drawBehind`, and `KhataPalette`. Let the compiler's unused-import warnings guide this; do not guess.

- [ ] **Step 5: Amend the spec**

The spec now contradicts what ships. In `docs/superpowers/specs/2026-08-28-khata-petrol-design.md`, replace the whole `### The density rule` block (the paragraph and its three-row table) with:

```markdown
### The density rule

**Field intensity is one global user setting, applied identically on every screen.**

An earlier draft of this section shipped the mesh at three fixed levels chosen per screen —
Full on the hub, Mid on Wallet and editor, Quiet on the Ledger — on the reasoning that the
ground should never compete with a column of numbers. That contradicted §9, which makes
intensity one of the four axes the tuner moves, and §9 is what was built and what the user
chose. The per-screen rule is withdrawn rather than left as a second, unimplemented source
of truth.

The legibility concern behind it survives as a test obligation, not a design rule: because
Ledger rows now sit over the mesh at whatever level the user picks, every text role the
Ledger renders is asserted against the field key stops at Full intensity, which is the
lightest the mesh can ever reach. See `ContrastTest`.
```

- [ ] **Step 6: Run both suites**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/Wasif/AppData/Local/Android/Sdk"
export ANDROID_AVD_HOME="D:\android-avd"
./gradlew.bat test connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL. Unit 139 as before; instrumented 47 (45 baseline + 2 new).

- [ ] **Step 7: Confirm the hub is visually unchanged**

Install and open the hub. The mesh must be indistinguishable from before this task — same pool positions, same strength. If it moved, the geometry was not copied verbatim. Say so in your report rather than adjusting it to taste.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/ui/component/FieldScaffold.kt app/src/main/java/com/wasif/khata/feature/hub/ModulesScreen.kt app/src/androidTest/java/com/wasif/khata/core/ui/component/FieldScaffoldTest.kt docs/superpowers/specs/2026-08-28-khata-petrol-design.md
git commit -m "refactor: promote FieldBackdrop into a shared FieldScaffold"
```

---

### Task 4: The field on every screen, and the contrast obligation it creates

Wallet, Ledger, Editor and Settings paint flat `colorScheme.background` today. This task gives them the mesh — and, because Ledger text now sits over it, closes the contrast hole that creates.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/wallet/WalletScreen.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerScreen.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/core/ui/theme/ContrastTest.kt`

**Interfaces:**
- Consumes: `FieldScaffold` from Task 3.
- Produces: each screen's root is a `FieldScaffold` whose `haze` parameter is in scope for Tasks 5 and 6. Where a screen does not yet need it, name it `_` rather than inventing a use.

- [ ] **Step 1: Write the failing contrast test**

Ledger rows now render over the mesh, whose lightest reachable colour is a field's `keyStop` at Full intensity. Add to `app/src/test/java/com/wasif/khata/core/ui/theme/ContrastTest.kt`:

```kotlin
    @Test
    fun `every text role the ledger renders clears the text floor on every field`() {
        // Ledger rows sit over the mesh now (the per-screen density rule was
        // withdrawn -- see the spec's density section), so the ground is no
        // longer the darkest thing under this text. The key stop is the
        // lightest colour a field can reach, so it is the worst case.
        val roles = mapOf(
            "onSurface" to DarkColors.onSurface,
            "onSurfaceVariant" to DarkColors.onSurfaceVariant,
            "outline" to DarkColors.outline,
        )

        KhataPalette.fields.forEach { field ->
            roles.forEach { (name, colour) ->
                val ratio = contrastRatio(colour, field.keyStop)
                assertTrue(
                    "$name on the ${field.name} field key stop is ${"%.2f".format(ratio)}:1, below the 4.5:1 text floor",
                    ratio >= 4.5,
                )
            }
        }
    }
```

- [ ] **Step 2: Run it to see where it stands**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
./gradlew.bat test --tests '*ContrastTest*'
```

Expected: this may PASS already — the existing "paper text on field" assertions cover some of these roles, and `outline` was raised in the merge-gate fix wave. **If it passes, that is a real result, not a reason to skip the test**: it is now pinned against future field or role changes, which is exactly the hole that let `outline`-on-petrol ship. If it fails, the failing role's value must be raised in `DarkColors` and re-verified against the grounds too — never lower the assertion.

- [ ] **Step 3: Put the field on the Wallet**

In `feature/wallet/WalletScreen.kt`, the root currently reads:

```kotlin
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
```

Wrap it. Replace those lines with:

```kotlin
    FieldScaffold(Modifier.fillMaxSize()) { haze ->
    Column(
        Modifier
            .fillMaxSize()
```

and add the matching closing brace at the end of the composable's body. Add the import `com.wasif.khata.core.ui.component.FieldScaffold`. Keep `haze` named (not `_`) — Task 5 uses it. Re-indent the wrapped block properly; do not leave the body at its old indentation.

- [ ] **Step 4: Put the field on the Ledger**

In `feature/ledger/LedgerScreen.kt`, replace:

```kotlin
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
```

with:

```kotlin
    FieldScaffold(Modifier.fillMaxSize()) { haze ->
```

Add the `FieldScaffold` import. Keep `haze` named — Task 6 uses it. The `LazyColumn` inside must **not** be given `khataFieldSource`; only the field is ever a source.

- [ ] **Step 5: Put the field on the Editor and Settings**

`feature/editor/TransactionEditorScreen.kt` and `feature/settings/SettingsScreen.kt` both open their root with a `Column`/`Box` carrying `.background(MaterialTheme.colorScheme.background)`. In each, wrap the root in `FieldScaffold(Modifier.fillMaxSize()) { haze -> ... }` and drop the `.background(...)` call — `FieldScaffold` paints the ground itself, and a second opaque fill over it would hide the mesh.

In `SettingsScreen.kt` name the parameter `_`: Settings gets the field but **not** glass, because the tuner is where surfaces must read neutrally so swatches are judged accurately.

- [ ] **Step 6: Run both suites**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/Wasif/AppData/Local/Android/Sdk"
export ANDROID_AVD_HOME="D:\android-avd"
./gradlew.bat test connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL, unit 140, instrumented 47.

- [ ] **Step 7: Check the Ledger at Full intensity on device**

Set intensity to Full and open the Ledger with rows on screen. Report honestly whether the mesh competes with the numbers. This is the concern the withdrawn density rule existed to prevent, and the contrast assertion only proves the text is *legible*, not that the screen is *calm*. If it reads badly, say so in your report — do not silently re-introduce per-screen levels, which is a decision that has already been made the other way.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature app/src/test/java/com/wasif/khata/core/ui/theme/ContrastTest.kt
git commit -m "feat: the mesh field on every screen at the user's intensity"
```

---

### Task 5: Glass on the Wallet and Editor surfaces

`KhataGlass` exists, is fully built, and serves two dormant labels. Spec §5 names module cards, sheets, dialogs and editor chips. Sheets and dialogs do not exist yet; these do.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/wallet/WalletScreen.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/editor/TransactionEditorScreen.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/wallet/WalletScreenTest.kt`

**Interfaces:**
- Consumes: `FieldScaffold`'s `haze: HazeState` from Task 4; `KhataGlass(hazeState, modifier, shape, raised, content)` from `core/ui/component/KhataGlass.kt`.

- [ ] **Step 1: Write the failing test**

Glass is a paint treatment; asserting its pixels is brittle and asserting "a `KhataGlass` was called" tests the code against itself. What is worth pinning is that converting these surfaces did not drop their content — the actual regression risk when a `Column` is rewrapped.

Add to `app/src/androidTest/java/com/wasif/khata/feature/wallet/WalletScreenTest.kt`:

```kotlin
    @Test
    fun theMonthFiguresKeepTheirLabelsAndAmountsOnGlass() {
        // The month figures moved from a flat surfaceContainer Column to
        // KhataGlass. The paint is not assertable here; losing the content
        // while rewrapping is the real risk, so that is what this pins.
        compose.setContent {
            KhataTheme {
                WalletContent(
                    state = WalletUiState(
                        monthSpend = Money(240_000),
                        monthIncome = Money(500_000),
                    ),
                    onOpenLedger = {},
                    onBack = null,
                    onOpenHub = {},
                )
            }
        }

        compose.onNodeWithText("SPENT").assertIsDisplayed()
        compose.onNodeWithText("RECEIVED").assertIsDisplayed()
    }
```

Before writing it, open `WalletScreen.kt` and `WalletUiState` and confirm the parameter names, the exact label strings, and the `WalletContent` signature. **Use what is actually there** — the code above shows the shape, not necessarily the current names. Do not change `WalletContent`'s signature to suit the test.

- [ ] **Step 2: Run it to verify it fails or passes for the right reason**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/Wasif/AppData/Local/Android/Sdk"
export ANDROID_AVD_HOME="D:\android-avd"
./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.wallet.WalletScreenTest
```

Expected: PASS against the current flat implementation. That is correct — it is a regression pin for Step 3, not a red test. Note it as such in your report; do not manufacture a failure.

- [ ] **Step 3: Convert the Wallet month figures**

`MonthFigure` is `private` and does not currently receive the haze state. Change its signature and body:

```kotlin
@Composable
private fun MonthFigure(
    haze: HazeState,
    label: String,
    money: Money,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    KhataGlass(
        hazeState = haze,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(spacing.md)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = money.format(),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = tint,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    }
}
```

Note the padding moved inside the glass — `KhataGlass` clips to `shape`, so padding applied outside it would sit beyond the clip and the content would touch the bevel.

Update both call sites to pass `haze` as the first argument. Add imports for `KhataGlass` and `dev.chrisbanes.haze.HazeState`.

- [ ] **Step 4: Convert the Editor's direction pill and chips**

In `TransactionEditorScreen.kt`, the direction pill's unselected branch and `EditorChip`'s unselected branch both paint `MaterialTheme.colorScheme.surfaceContainer`.

**Convert the unselected state only.** The selected state stays `secondaryContainer`: selection must remain unmistakable, and a selected chip that is also translucent reads as less committed than an opaque one, which is backwards. State the reasoning in a comment at each site.

For `EditorChip`, wrap the unselected branch:

```kotlin
    if (selected) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            chipContent()
        }
    } else {
        // Only the unselected chip is glass. A translucent selected chip reads
        // as less committed than an opaque one, which inverts the meaning.
        KhataGlass(hazeState = haze, shape = CircleShape) {
            Row(
                Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                chipContent()
            }
        }
    }
```

Extract the shared row body into a local `chipContent` lambda rather than duplicating it — the review rubric treats a verbatim-duplicated block as a defect. Thread `haze` down from the `FieldScaffold` in Task 4 through every composable between it and these two, adding it as a first parameter each time.

Apply the same selected/unselected split to the direction pill.

- [ ] **Step 5: Run both suites**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/Wasif/AppData/Local/Android/Sdk"
export ANDROID_AVD_HOME="D:\android-avd"
./gradlew.bat test connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL, unit 140, instrumented 48.

- [ ] **Step 6: Check the zero-intensity fallback on device**

Set field intensity to **Off** in Settings, then open the Wallet and the Editor. `ThemeSpec.usesSolidSurfaces` should make every glass panel a solid fill. Confirm none of them render as flat translucent grey — that is the "looks like a rendering bug" state §5's fallback exists to prevent. Then set it back to Full and confirm the panels refract the mesh.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/wallet app/src/main/java/com/wasif/khata/feature/editor app/src/androidTest/java/com/wasif/khata/feature/wallet/WalletScreenTest.kt
git commit -m "feat: glass on the wallet month figures and editor controls"
```

---

### Task 6: Glass on the Ledger search field, measured

§5 marks this one **Measure** rather than **Yes**, on the reasoning that the backdrop is a scrolling list. Two things changed that: the search field sits *above* the `LazyColumn` in layout order rather than overlapping it, and the field is the only Haze source, so the blur samples a static mesh either way. The measurement stands as confirmation, not as an open question — and it has a documented fallback.

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/ledger/LedgerScreen.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/ledger/LedgerScreenTest.kt`

**Interfaces:**
- Consumes: `haze: HazeState` from the `FieldScaffold` added to `LedgerScreen` in Task 4; `KhataGlass`.

- [ ] **Step 1: Write the failing test**

Add to `app/src/androidTest/java/com/wasif/khata/feature/ledger/LedgerScreenTest.kt`:

```kotlin
    @Test
    fun theSearchFieldStillAcceptsInputOnGlass() {
        // The search field moved inside a KhataGlass wrapper. Wrapping a text
        // field in a Box that clips and draws is where focus and hit-testing
        // get lost, so this pins the interaction rather than the paint.
        var typed = ""
        compose.setContent {
            KhataTheme {
                LedgerContent(
                    items = flowOf(PagingData.from(emptyList<LedgerItem>(), sourceLoadStates = settled))
                        .collectAsLazyPagingItems(),
                    header = LedgerHeaderState(monthLabel = "August 2026", daysLeft = 3),
                    categoryTokens = emptyMap(),
                    canGoForward = false,
                    query = "",
                    onQueryChange = { typed = it },
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onBack = {},
                    onAddTransaction = {},
                    onOpenTransaction = {},
                )
            }
        }

        compose.onNodeWithText("Search").performTextInput("shwapno")

        assertEquals("shwapno", typed)
    }
```

Confirm the search field's label or placeholder text before asserting on it — `"Search"` is what the existing `searchingReplacesTheMonthHeadingRatherThanAssertingAMonthTheResultsIgnore` test matches, but check that it is the *field's* text and not the heading's, and target the field specifically if both match. Add `performTextInput` to the imports.

- [ ] **Step 2: Run it to verify it passes pre-change**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/Wasif/AppData/Local/Android/Sdk"
export ANDROID_AVD_HOME="D:\android-avd"
./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wasif.khata.feature.ledger.LedgerScreenTest
```

Expected: PASS. Regression pin for Step 3, as in Task 5.

- [ ] **Step 3: Wrap the search field in glass**

The `OutlinedTextField` around `LedgerScreen.kt:130` carries Material's own container colours, which would paint over the glass. Set its container colours to transparent and let `KhataGlass` provide the surface:

```kotlin
            KhataGlass(
                hazeState = haze,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal),
                shape = MaterialTheme.shapes.small,
            ) {
                OutlinedTextField(
                    // The glass IS the container. Material's own container fill
                    // would paint an opaque rectangle over the blur and leave a
                    // flat box with a bevel round it.
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                    // ... the field's existing value/onValueChange/label/etc,
                    // unchanged, with its own fillMaxWidth/padding removed since
                    // KhataGlass now carries them
                )
            }
```

Add imports for `KhataGlass`, `OutlinedTextFieldDefaults`, and `androidx.compose.ui.graphics.Color`. Keep every other parameter of the existing field exactly as it is.

- [ ] **Step 4: Run both suites**

```bash
export JAVA_HOME="/e/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/Wasif/AppData/Local/Android/Sdk"
export ANDROID_AVD_HOME="D:\android-avd"
./gradlew.bat test connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL, unit 140, instrumented 49.

- [ ] **Step 5: Measure it on device**

This is the step §5 asks for. Enable GPU profiling and scroll the Ledger hard with the search field on screen:

```bash
export ANDROID_HOME="/c/Users/Wasif/AppData/Local/Android/Sdk"
"$ANDROID_HOME/platform-tools/adb.exe" shell setprop debug.hwui.profile visual_bars
"$ANDROID_HOME/platform-tools/adb.exe" shell am force-stop com.wasif.khata
"$ANDROID_HOME/platform-tools/adb.exe" shell am start -n com.wasif.khata/.MainActivity
```

Scroll a long month, then read the frame timings:

```bash
"$ANDROID_HOME/platform-tools/adb.exe" shell dumpsys gfxinfo com.wasif.khata | head -40
```

Record "Janky frames" as a percentage, before and after this task's change, on the same scroll. Turn profiling off afterwards:

```bash
"$ANDROID_HOME/platform-tools/adb.exe" shell setprop debug.hwui.profile false
```

**The fallback, if jank rises measurably:** revert the search field to its flat `surfaceContainer` form and record the measured numbers in your report. §5 marks this surface "Measure", so a flat search bar is a legitimate outcome of having measured — not a failure of the task. Do not keep glass here on the grounds that it looks better if the numbers say it costs frames.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/ledger
git commit -m "feat: glass on the ledger search field"
```

---

## Self-review

**Spec coverage.** §5's five ingredients are already implemented in `KhataGlass` and unchanged here. §5's placement table: module cards (hub, already glass), editor chips (Task 5), search bar over the scrolling ledger (Task 6, measured), ledger rows never (Task 4 Step 4 states it explicitly), full-screen over a scrolling list never (no task does this). Bottom sheets and dialogs are listed in §5 but **no such surface exists in the app** — deliberately not built here, as building a sheet to have somewhere to put glass is backwards. §5's zero-field fallback: already implemented via `usesSolidSurfaces`, verified in Task 5 Step 6. §6's durations, easings, shared-axis, fade-through and Remove-animations: Tasks 1 and 2. §1's density rule: withdrawn and rewritten in Task 3 Step 5.

**Known gap, stated rather than hidden.** `KhataNavHost` still has no automated coverage, so Task 2's route classification is unit-tested but its *wiring* is verified only by the device walkthrough in Step 6. This is the same accepted gap the merge-gate review recorded; building Hilt navigation test infra is out of scope for this plan.

**Type consistency.** `Motion.forDurationScale` / `Motion.isInstant` (T1) are consumed by name in T2's transitions and test. `FieldScaffold(modifier, content: @Composable BoxScope.(HazeState) -> Unit)` (T3) is called with a trailing lambda taking `haze` in T4, and that same `haze` is passed to `KhataGlass(hazeState = ...)` in T5 and T6. `FieldBackdrop` is `public` in T3 because `FieldScaffold` is in the same file but the hub's test may reference it. `KhataGlass`'s existing signature is unchanged throughout.

**Ordering.** T2 depends on T1; T4 on T3; T5 and T6 on T4. T1–T2 and T3–T4 are independent of each other, but tasks are executed one at a time regardless.
