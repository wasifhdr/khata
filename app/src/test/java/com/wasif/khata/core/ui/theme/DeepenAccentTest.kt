package com.wasif.khata.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `deepenAccent()` had no test of its own -- its fitted 1.45/0.90 constants
 * and the claim that it reproduces the shipped accent -> accentDeep pair
 * lived only in the comment above it, so changing either constant currently
 * failed nothing. These assert the derived colour, not just that the
 * function ran.
 */
@RunWith(RobolectricTestRunner::class)
class DeepenAccentTest {

    // Reproducing the shipped pair to the exact byte would tie this test to
    // the implementation's own Float rounding (the worked example in
    // Color.kt is already off by ~1/255 per channel: #60CAB2 vs #5FC9B2).
    // 2/255 absorbs that rounding but is nowhere near loose enough to hide a
    // real regression: swapping the two constants (0.90 for saturation,
    // 1.45 for value) moves every channel by 50+/255 on this same input --
    // see the swap check below.
    private val channelTolerance = 2f / 255f

    private fun assertCloseColor(expected: Color, actual: Color, tolerance: Float = channelTolerance) {
        assertTrue(
            "red: expected ${expected.red} got ${actual.red}, outside $tolerance",
            abs(expected.red - actual.red) <= tolerance,
        )
        assertTrue(
            "green: expected ${expected.green} got ${actual.green}, outside $tolerance",
            abs(expected.green - actual.green) <= tolerance,
        )
        assertTrue(
            "blue: expected ${expected.blue} got ${actual.blue}, outside $tolerance",
            abs(expected.blue - actual.blue) <= tolerance,
        )
    }

    @Test
    fun `deepenAccent reproduces the shipped accent to accentDeep pair`() {
        assertCloseColor(KhataPalette.accentDeep, deepenAccent(KhataPalette.accent))
    }

    @Test
    fun `a pure grey stays grey -- saturation 0 has nothing for the 1_45x to raise`() {
        val grey = Color(red = 0.5f, green = 0.5f, blue = 0.5f)
        val deepened = deepenAccent(grey)
        assertTrue("deepened grey was not grey: $deepened", deepened.red == deepened.green)
        assertTrue("deepened grey was not grey: $deepened", deepened.green == deepened.blue)
    }

    @Test
    fun `value 0 stays black`() {
        assertCloseColor(Color.Black, deepenAccent(Color.Black), tolerance = 0f)
    }

    @Test
    fun `hue wraparound does not produce a negative hue`() {
        // r is the max channel and b > g, so the true hue sits at ~324deg
        // (red toward magenta). rgbToHsv's r-branch computes this from a raw
        // negative fraction, (g - b) / delta, and relies on `.mod(6f)` to
        // wrap it back into [0, 360) before scaling by 60. Without that wrap,
        // hsvToRgb would see a negative h, fall into its first branch
        // (h < 60), and swap the green/blue channels of the result. Blue
        // dominating over green in the output is the signal that the hue
        // landed in the correct 300-360 branch rather than at -36.
        val input = Color(red = 1f, green = 0f, blue = 0.6f)
        val deepened = deepenAccent(input)
        assertTrue(
            "expected blue (${deepened.blue}) > green (${deepened.green}) -- hue may have gone negative",
            deepened.blue > deepened.green,
        )
        assertTrue(
            "expected red (${deepened.red}) > blue (${deepened.blue})",
            deepened.red > deepened.blue,
        )
    }
}
