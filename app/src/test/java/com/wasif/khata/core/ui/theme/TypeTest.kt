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
            TaglineTextStyle,
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

    @Test
    fun `the tagline style resolves to the bundled Bengali family`() {
        // I8: the tagline is the app's only Bengali UI string. bodyMedium is
        // Instrument Sans, which has no Bengali glyphs, so it was silently
        // falling through to whichever font the device happens to ship --
        // the exact per-device variance the bundled Noto cut exists to remove.
        assertEquals(KhataFonts.Bengali, TaglineTextStyle.fontFamily)
    }
}
