package com.wasif.khata.core.ui.theme

import androidx.compose.material3.darkColorScheme
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
    fun `every accent clears every ground and hero stop as text`() {
        // Where accent text actually goes: numerals inside the hero card, links
        // and labels on the plain ground, selected chip text on glass (which
        // sits on one of these two). Text floor applies.
        KhataPalette.accents.forEach { a ->
            KhataPalette.grounds.forEach { g ->
                assertFloor("accent $a / ground $g", a, g, textFloor)
            }
            KhataPalette.heroStops.forEach { h ->
                assertFloor("accent $a / hero $h", a, h, textFloor)
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
                assertFloor("accent $a / field ${f.name}", a, f.keyStop, graphicFloor)
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
    fun `contrast ratio is symmetric and bounded`() {
        assertTrue(contrastRatio(Color.White, Color.Black) in 20.9..21.1)
        assertTrue(contrastRatio(Color.Black, Color.White) in 20.9..21.1)
        assertTrue(contrastRatio(Color.White, Color.White) in 0.99..1.01)
    }
}
