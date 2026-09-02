package com.wasif.khata.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.wasif.khata.core.ui.component.FieldGrainAlpha
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

    private fun assertFloor(what: String, fg: Color, bg: Color, floor: Double) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$what contrast was ${"%.2f".format(ratio)}, below the $floor:1 floor",
            ratio >= floor,
        )
    }

    @Test
    fun `paper text clears every ground, field and hero stop`() {
        KhataPalette.grounds.forEach { g ->
            assertFloor("onSurface / ground ${g.name}", KhataPalette.onSurface, g.color, textFloor)
        }
        KhataPalette.fields.forEach { f ->
            assertFloor("onSurface / field ${f.name}", KhataPalette.onSurface, f.keyStop, textFloor)
        }
        KhataPalette.heroStops.forEach { h ->
            assertFloor("onSurface / hero $h", KhataPalette.onSurface, h, textFloor)
        }
    }

    /**
     * The brightest pixel a field can actually put on screen. The mesh draws the
     * key stop, then the grain lifts roughly half the pixels by
     * `FieldGrainAlpha` of the way to white -- so the raw stop is not the worst
     * case, and sizing against it is what let dim text land at 3.92:1 on a
     * measured device screenshot while this file was green.
     */
    private fun grainLit(c: Color): Color = Color(
        red = c.red * (1f - FieldGrainAlpha) + FieldGrainAlpha,
        green = c.green * (1f - FieldGrainAlpha) + FieldGrainAlpha,
        blue = c.blue * (1f - FieldGrainAlpha) + FieldGrainAlpha,
    )

    @Test
    fun `every text role the ledger renders clears the text floor on every lit field`() {
        // Ledger rows sit over the mesh now -- the per-screen density rule was
        // withdrawn, so the ground is no longer the darkest thing under this
        // text and every field has to hold it at Full intensity.
        // Only two text tiers can clear 4.5:1 over a lit field -- paper and one
        // dim. A third, fainter tier cannot, which is why `outline` is a border
        // and disabled-state role here and carries no live text.
        val roles = mapOf(
            "onSurface" to DarkColors.onSurface,
            "onSurfaceVariant" to DarkColors.onSurfaceVariant,
        )

        KhataPalette.fields.forEach { f ->
            roles.forEach { (name, colour) ->
                assertFloor("$name / lit field ${f.name}", colour, grainLit(f.keyStop), textFloor)
            }
            // Borders and disabled controls are graphical objects, not text.
            assertFloor("outline / lit field ${f.name}", DarkColors.outline, grainLit(f.keyStop), graphicFloor)
        }
    }

    @Test
    fun `the outline role carries no live text`() {
        // Sibling to the assertion above: it only holds while nothing renders
        // body copy in `outline`. This pins that, because a single
        // `color = colorScheme.outline` on a Text silently reintroduces a
        // 2.33:1 label over the mesh.
        val sources = java.io.File("src/main/java/com/wasif/khata")
            .walkTopDown().filter { it.extension == "kt" }
        // Anchored so `outlineVariant` -- a legitimate hairline role -- does not
        // register as an offender.
        val textInOutline = Regex("""color = MaterialTheme\.colorScheme\.outline(?![A-Za-z])""")
        val offenders = sources
            .filter { textInOutline.containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()

        assertTrue(
            "these render text in the outline role, which cannot clear 4.5:1 over the field: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every accent clears every ground and hero stop as text`() {
        // Where accent text actually goes: numerals inside the hero card, links
        // and labels on the plain ground, selected chip text on glass (which
        // sits on one of these two). Text floor applies.
        KhataPalette.accents.forEach { a ->
            KhataPalette.grounds.forEach { g ->
                assertFloor("accent ${a.name} / ground ${g.name}", a.color, g.color, textFloor)
            }
            KhataPalette.heroStops.forEach { h ->
                assertFloor("accent ${a.name} / hero $h", a.color, h, textFloor)
            }
        }
    }

    @Test
    fun `every accent clears every field as a graphical object`() {
        // The only accent-coloured thing that sits directly on the raw mesh is
        // the FAB, which is a filled shape rather than text -- so this is the
        // 3:1 graphical floor, not 4.5:1. Accent text never lands here: glass,
        // petrol or the ground is always in between.
        KhataPalette.accents.forEach { a ->
            KhataPalette.fields.forEach { f ->
                assertFloor("accent ${a.name} / field ${f.name}", a.color, f.keyStop, graphicFloor)
            }
        }
    }

    @Test
    fun `onSurfaceVariant -- the hero card's low-emphasis text -- clears every hero stop`() {
        // I1: ModulesScreen painted "spent this month" and the last-transaction
        // line in `outline`, which is only 2.79:1 on the lightest hero stop --
        // below the text floor on the app's first viewport. Nothing asserted
        // `outline` (or anything else) against the hero here, which is how it
        // shipped. onSurfaceVariant is what the WALLET label two lines above it
        // already used, and is the fix.
        KhataPalette.heroStops.forEach { h ->
            assertFloor("onSurfaceVariant / hero $h", KhataPalette.onSurfaceDim, h, textFloor)
        }
        // `outline` itself is documented as unfit for this job: if this ever
        // stops failing, the reasoning above (and the fix it justifies) is
        // stale and needs revisiting.
        //
        // Only the lightest stop is asserted. Unfitness needs the *worst* case
        // to fail, not every case -- an earlier form of this checked all three
        // and fired when `outline` was raised for the field work, even though
        // the lightest stop still failed and the routing-around was still
        // needed.
        val lightest = KhataPalette.heroStops.first()
        val ratio = contrastRatio(DarkColors.outline, lightest)
        assertTrue(
            "outline / lightest hero $lightest is now $ratio, so it may no longer need routing around",
            ratio < textFloor,
        )
    }

    @Test
    fun `outlineVariant -- the ledger row hairline -- clears every ground as a border`() {
        // I2: outlineVariant was #1C2E30, 1.34:1 against every ground -- a
        // "separator" that could not be seen, and nothing asserted it. It is
        // now a decorative border only (the disabled month-arrow moved to
        // `outline`, checked below), so the 3:1 graphical floor applies.
        KhataPalette.grounds.forEach { g ->
            assertFloor("outlineVariant / ground ${g.name}", DarkColors.outlineVariant, g.color, graphicFloor)
        }
    }

    @Test
    fun `the disabled month-arrow tint sits strictly between the hairline and the enabled tint`() {
        // WCAG exempts a disabled control from the border floor, so `outline`
        // does not have to clear 3:1 here the way the hairline above does --
        // but the whole point of I2 was that "exempt from the floor" is not
        // "exempt from visible". This locks in the ordering the fix relies on:
        // clearly above the near-invisible hairline, clearly below the enabled
        // tint, for every ground the disabled arrow can sit on.
        KhataPalette.grounds.forEach { g ->
            val hairline = contrastRatio(DarkColors.outlineVariant, g.color)
            val disabled = contrastRatio(DarkColors.outline, g.color)
            val enabled = contrastRatio(DarkColors.onSurfaceVariant, g.color)
            assertTrue(
                "disabled ratio $disabled against ${g.name} should exceed the hairline's $hairline",
                disabled > hairline,
            )
            assertTrue(
                "disabled ratio $disabled against ${g.name} should be below the enabled tint's $enabled",
                disabled < enabled,
            )
        }
    }

    @Test
    fun `the alert colour clears every ground`() {
        KhataPalette.grounds.forEach { g ->
            assertFloor("alert / ground ${g.name}", KhataPalette.alert, g.color, textFloor)
        }
    }

    @Test
    fun `every category dot clears every ground`() {
        KhataPalette.categories.forEach { (token, colour) ->
            KhataPalette.grounds.forEach { g ->
                assertFloor("category $token / ground ${g.name}", colour, g.color, graphicFloor)
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
                assertTrue("$token duplicates accent ${a.name}", colour != a.color)
            }
        }
    }

    @Test
    fun `every ground and accent swatch carries a name`() {
        // I7: SwatchGrid rendered bare coloured Boxes -- no text, no
        // contentDescription -- and the one axis with real names
        // (FieldPalette) displayed them nowhere either. Names now live on the
        // colour itself (NamedSwatch), not a parallel List<String>, so this is
        // the floor that keeps one from being added blank.
        (KhataPalette.grounds + KhataPalette.accents).forEach { swatch ->
            assertTrue("a swatch has a blank name: $swatch", swatch.name.isNotBlank())
        }
        KhataPalette.fields.forEach { f ->
            assertTrue("a field swatch has a blank name: $f", f.name.isNotBlank())
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
    fun `no role keeps its Material default`() {
        // Material's defaults are purple. Any role left unset leaks that purple
        // into a teal world through whichever component happens to read it --
        // FilterChip reads secondaryContainer, and showed #4A4458 until every
        // role was set. Nothing in the app reads most of these by name, which is
        // exactly why only a test catches it.
        val default = darkColorScheme()
        val roles = listOf<Triple<String, Color, Color>>(
            Triple("primary", DarkColors.primary, default.primary),
            Triple("onPrimary", DarkColors.onPrimary, default.onPrimary),
            Triple("primaryContainer", DarkColors.primaryContainer, default.primaryContainer),
            Triple("onPrimaryContainer", DarkColors.onPrimaryContainer, default.onPrimaryContainer),
            Triple("inversePrimary", DarkColors.inversePrimary, default.inversePrimary),
            Triple("secondary", DarkColors.secondary, default.secondary),
            Triple("onSecondary", DarkColors.onSecondary, default.onSecondary),
            Triple("secondaryContainer", DarkColors.secondaryContainer, default.secondaryContainer),
            Triple("onSecondaryContainer", DarkColors.onSecondaryContainer, default.onSecondaryContainer),
            Triple("tertiary", DarkColors.tertiary, default.tertiary),
            Triple("onTertiary", DarkColors.onTertiary, default.onTertiary),
            Triple("tertiaryContainer", DarkColors.tertiaryContainer, default.tertiaryContainer),
            Triple("onTertiaryContainer", DarkColors.onTertiaryContainer, default.onTertiaryContainer),
            Triple("background", DarkColors.background, default.background),
            Triple("onBackground", DarkColors.onBackground, default.onBackground),
            Triple("surface", DarkColors.surface, default.surface),
            Triple("onSurface", DarkColors.onSurface, default.onSurface),
            Triple("surfaceVariant", DarkColors.surfaceVariant, default.surfaceVariant),
            Triple("onSurfaceVariant", DarkColors.onSurfaceVariant, default.onSurfaceVariant),
            Triple("surfaceTint", DarkColors.surfaceTint, default.surfaceTint),
            Triple("inverseSurface", DarkColors.inverseSurface, default.inverseSurface),
            Triple("inverseOnSurface", DarkColors.inverseOnSurface, default.inverseOnSurface),
            Triple("surfaceBright", DarkColors.surfaceBright, default.surfaceBright),
            Triple("surfaceDim", DarkColors.surfaceDim, default.surfaceDim),
            Triple("surfaceContainerLowest", DarkColors.surfaceContainerLowest, default.surfaceContainerLowest),
            Triple("surfaceContainerLow", DarkColors.surfaceContainerLow, default.surfaceContainerLow),
            Triple("surfaceContainer", DarkColors.surfaceContainer, default.surfaceContainer),
            Triple("surfaceContainerHigh", DarkColors.surfaceContainerHigh, default.surfaceContainerHigh),
            Triple("surfaceContainerHighest", DarkColors.surfaceContainerHighest, default.surfaceContainerHighest),
            Triple("outline", DarkColors.outline, default.outline),
            Triple("outlineVariant", DarkColors.outlineVariant, default.outlineVariant),
            Triple("error", DarkColors.error, default.error),
            Triple("onError", DarkColors.onError, default.onError),
            Triple("errorContainer", DarkColors.errorContainer, default.errorContainer),
            Triple("onErrorContainer", DarkColors.onErrorContainer, default.onErrorContainer),
        )
        roles.forEach { (name, ours, theirs) ->
            assertTrue("$name is still Material's default $theirs", ours != theirs)
        }
    }

    @Test
    fun `every container pair in the scheme is legible`() {
        // Container/on-container pairs carry text, so they owe the text floor.
        assertFloor("onPrimaryContainer", DarkColors.onPrimaryContainer, DarkColors.primaryContainer, textFloor)
        assertFloor("onSecondaryContainer", DarkColors.onSecondaryContainer, DarkColors.secondaryContainer, textFloor)
        assertFloor("onTertiaryContainer", DarkColors.onTertiaryContainer, DarkColors.tertiaryContainer, textFloor)
        assertFloor("onErrorContainer", DarkColors.onErrorContainer, DarkColors.errorContainer, textFloor)
        assertFloor("onSurfaceVariant", DarkColors.onSurfaceVariant, DarkColors.surfaceVariant, textFloor)
        assertFloor("onSurface / containerHighest", DarkColors.onSurface, DarkColors.surfaceContainerHighest, textFloor)
    }

    @Test
    fun `the composed scheme carries the tuned accent through every accent-derived role`() {
        // I5: this suite used to assert DarkColors, the scheme *before*
        // KhataTheme copies spec over it -- so it could not see that
        // secondary, tertiary, onSecondaryContainer, surfaceTint and
        // inversePrimary stayed hard-wired to the default aqua no matter what
        // accent was picked. composeScheme is the exact function KhataTheme
        // calls; this asserts what it actually produces for a non-default
        // spec, not the pre-copy scheme.
        val tuned = ThemeSpec.Default.copy(accent = KhataPalette.accents[1].color) // Marigold
        val scheme = composeScheme(tuned)

        assertEquals(tuned.accent, scheme.primary)
        assertEquals(tuned.accent, scheme.onSecondaryContainer)
        assertEquals(tuned.accent, scheme.surfaceTint)
        // `!= DarkColors.x` is satisfied by any other colour, including a bug
        // that landed on Color.Red -- assert the actual derived value instead.
        val accentDeep = deepenAccent(tuned.accent)
        assertEquals(accentDeep, scheme.secondary)
        assertEquals(accentDeep, scheme.tertiary)
        assertEquals(accentDeep, scheme.inversePrimary)
    }

    @Test
    fun `the composed selected-chip pair clears the text floor for every accent`() {
        // Pairwise: every accent against the one secondaryContainer background
        // it is ever composited on, not the 8x4x4x4 space.
        KhataPalette.accents.forEach { a ->
            val scheme = composeScheme(ThemeSpec.Default.copy(accent = a.color))
            assertFloor(
                "composed onSecondaryContainer / secondaryContainer for accent ${a.name}",
                scheme.onSecondaryContainer,
                scheme.secondaryContainer,
                textFloor,
            )
        }
    }

    @Test
    fun `contrast ratio is symmetric and bounded`() {
        assertTrue(contrastRatio(Color.White, Color.Black) in 20.9..21.1)
        assertTrue(contrastRatio(Color.Black, Color.White) in 20.9..21.1)
        assertTrue(contrastRatio(Color.White, Color.White) in 0.99..1.01)
    }
}
