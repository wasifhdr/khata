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
