package com.wasif.khata.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.wasif.khata.core.data.seed.DEFAULT_CATEGORIES
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    private fun assertAA(name: String, ratio: Double) {
        assertTrue("$name contrast was $ratio, below the 4.5:1 AA floor", ratio >= 4.5)
    }

    @Test
    fun `light palette text pairs meet WCAG AA`() {
        assertAA("onSurface", contrastRatio(LightColors.onSurface, LightColors.surface))
        assertAA("onBackground", contrastRatio(LightColors.onBackground, LightColors.background))
        assertAA("onPrimary", contrastRatio(LightColors.onPrimary, LightColors.primary))
        assertAA("onSurfaceVariant", contrastRatio(LightColors.onSurfaceVariant, LightColors.surfaceVariant))
        assertAA("onError", contrastRatio(LightColors.onError, LightColors.error))
    }

    @Test
    fun `dark palette text pairs meet WCAG AA`() {
        assertAA("onSurface", contrastRatio(DarkColors.onSurface, DarkColors.surface))
        assertAA("onBackground", contrastRatio(DarkColors.onBackground, DarkColors.background))
        assertAA("onPrimary", contrastRatio(DarkColors.onPrimary, DarkColors.primary))
        assertAA("onSurfaceVariant", contrastRatio(DarkColors.onSurfaceVariant, DarkColors.surfaceVariant))
        assertAA("onError", contrastRatio(DarkColors.onError, DarkColors.error))
    }

    @Test
    fun `every seeded category token resolves in both palettes`() {
        DEFAULT_CATEGORIES.map { it.colorToken }.distinct().forEach { token ->
            assertTrue("$token missing from CategoryColorsLight", CategoryColorsLight.containsKey(token))
            assertTrue("$token missing from CategoryColorsDark", CategoryColorsDark.containsKey(token))
        }
    }

    @Test
    fun `category colours are legible on their own surface`() {
        DEFAULT_CATEGORIES.map { it.colorToken }.distinct().forEach { token ->
            // 3:1 is the AA floor for graphical objects and large text, which is what
            // category dots and chip labels are.
            val light = contrastRatio(CategoryColorsLight.getValue(token), LightColors.surface)
            val dark = contrastRatio(CategoryColorsDark.getValue(token), DarkColors.surface)
            assertTrue("$token light contrast $light", light >= 3.0)
            assertTrue("$token dark contrast $dark", dark >= 3.0)
        }
    }

    @Test
    fun `contrast ratio is symmetric and bounded`() {
        assertTrue(contrastRatio(Color.White, Color.Black) in 20.9..21.1)
        assertTrue(contrastRatio(Color.Black, Color.White) in 20.9..21.1)
        assertTrue(contrastRatio(Color.White, Color.White) in 0.99..1.01)
    }
}
