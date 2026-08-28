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
