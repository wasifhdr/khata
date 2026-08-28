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
    fun `contrast ratio is symmetric and bounded`() {
        assertTrue(contrastRatio(Color.White, Color.Black) in 20.9..21.1)
        assertTrue(contrastRatio(Color.Black, Color.White) in 20.9..21.1)
        assertTrue(contrastRatio(Color.White, Color.White) in 0.99..1.01)
    }
}
