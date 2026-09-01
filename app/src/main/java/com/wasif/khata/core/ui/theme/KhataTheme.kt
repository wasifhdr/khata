package com.wasif.khata.core.ui.theme

import androidx.compose.material3.ColorScheme
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
            ground = KhataPalette.grounds.first().color,
            accent = KhataPalette.accents.first().color,
            intensity = FieldIntensity.Full,
        )
    }
}

val LocalThemeSpec = staticCompositionLocalOf { ThemeSpec.Default }

val LocalCategoryColors = staticCompositionLocalOf<Map<String, Color>> { emptyMap() }

/**
 * The scheme `KhataTheme` actually hands to `MaterialTheme` for a given
 * [spec]. Kept as a plain function -- not inlined into the composable below --
 * so a test can assert what the tuner *produces* for a non-default spec
 * rather than only `DarkColors`, the pre-copy scheme it never ships (I5:
 * `ContrastTest` used to assert `DarkColors` and could not see this at all).
 *
 * DarkColors only ever holds the *default* accent baked in by name, so
 * copying just primary/background/surface here left every other
 * accent-derived role (secondary, the selected-chip pair, the tint Material
 * uses for elevation) pointing at the default aqua forever -- picking
 * Marigold recoloured the primary button and nothing else. The whole accent
 * family is re-derived from spec.accent below instead.
 */
fun composeScheme(spec: ThemeSpec): ColorScheme {
    val accentDeep = deepenAccent(spec.accent)
    return DarkColors.copy(
        primary = spec.accent,
        inversePrimary = accentDeep,

        secondary = accentDeep,
        tertiary = accentDeep,
        // The selected-chip pair reads spec.accent on the fixed petrol
        // container -- see ContrastTest for why that pairing holds for every
        // shipped accent.
        onSecondaryContainer = spec.accent,
        surfaceTint = spec.accent,

        background = spec.ground,
        surface = spec.ground,
    )
}

@Composable
fun KhataTheme(
    spec: ThemeSpec = ThemeSpec.Default,
    content: @Composable () -> Unit,
) {
    // Dark is the product, not a mode. isSystemInDarkTheme is deliberately not
    // consulted: there is no light scheme to switch to yet, and pretending
    // otherwise would hide the fact when one is added.
    val scheme = composeScheme(spec)

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalMotion provides rememberSystemMotion(),
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
