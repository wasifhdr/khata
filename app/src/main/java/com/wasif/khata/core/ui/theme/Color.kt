package com.wasif.khata.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** One palette option for the mesh field behind the glass. */
data class FieldPalette(
    val name: String,
    /**
     * The lightest stop in the mesh. Contrast is only ever at risk against the
     * lightest point a gradient reaches, so that is the one worth asserting --
     * every darker stop clears by construction.
     */
    val keyStop: Color,
)

object KhataPalette {

    val ground: Color = Color(0xFF061214)
    val onSurface: Color = Color(0xFFEDF2F1)
    val onSurfaceDim: Color = Color(0xFFA8B8B8)
    val onSurfaceFaint: Color = Color(0xFF6E8180)

    val accent: Color = Color(0xFF8FE0CE)
    val accentDeep: Color = Color(0xFF5FC9B2)

    val alert: Color = Color(0xFFFF7A6B)
    val warn: Color = Color(0xFFF2A63E)

    /** Petrol. The gradient the active module card is filled with. */
    val heroStops: List<Color> = listOf(
        Color(0xFF12403F),
        Color(0xFF0C2E30),
        Color(0xFF08211F),
    )

    val grounds: List<Color> = listOf(
        Color(0xFF061214), // teal black -- default
        Color(0xFF0B0C18), // indigo black
        Color(0xFF08111C), // navy black
        Color(0xFF0A0D0F), // cool black
    )

    val accents: List<Color> = listOf(
        Color(0xFF8FE0CE), // pale aqua -- default
        Color(0xFFFFB627), // marigold
        Color(0xFFB6E24A), // chartreuse
        Color(0xFFE8C9A0), // warm sand
    )

    /**
     * Key stops are **composited** values -- the colour that actually reaches
     * the screen once the mesh stop is drawn at its own alpha over the ground,
     * not the raw gradient colour. Asserting the raw colour would test a pixel
     * that is never rendered, and would fail the default theme for no reason.
     */
    val fields: List<FieldPalette> = listOf(
        FieldPalette("Verdigris", Color(0xFF185F56)), // default
        FieldPalette("Abyss", Color(0xFF155159)),
        FieldPalette("Counterpoint", Color(0xFF12555B)),
        // Cyan is deliberately darker than the concept's swatch. At the value it
        // was drawn, paper text over it lands at 2.78:1 -- unreadable. Being the
        // brightest field is not worth being the one nothing can sit on.
        FieldPalette("Cyan", Color(0xFF186A70)),
        FieldPalette("Violet", Color(0xFF452F76)),
        FieldPalette("Monochrome", Color(0xFF155553)),
        FieldPalette("Deep sea", Color(0xFF105057)),
        FieldPalette("Mist", Color(0xFF334B58)),
    )

    /**
     * Quarantined: these appear only inside an 8dp dot or a chip, never as a
     * background or a text colour, and the category name is always present so
     * colour is never the sole signal.
     *
     * Keys are the tokens already seeded in DefaultData.kt and are deliberately
     * left alone -- renaming them would need a data migration to buy nothing.
     * The names no longer describe the hues; the category each token is
     * attached to is what the hue was chosen for.
     */
    val categories: Map<String, Color> = mapOf(
        // Living -- warm arc
        "category_green" to Color(0xFFE8C15A), // Groceries
        "category_orange" to Color(0xFFFF8A6B), // Eating Out
        "category_indigo" to Color(0xFFF2A63E), // Education
        // Recurring -- cool blues
        "category_amber" to Color(0xFF9DB4C8), // Bills & Utilities
        "category_teal" to Color(0xFF7FB8EC), // Mobile & Internet
        "category_blue" to Color(0xFF93A9F2), // Transport
        // Discretionary -- pink to violet
        "category_violet" to Color(0xFFF293A8), // Shopping
        "category_pink" to Color(0xFFB7A2EF), // Entertainment
        "category_rose" to Color(0xFFDF8CCC), // Family & Gifts
        // Place -- earth
        "category_slate" to Color(0xFFDCC099), // Fuel
        "category_bronze" to Color(0xFFC9A6BC), // Car & Maintenance
        // Body
        "category_red" to Color(0xFFEE6F80), // Health
        // System -- neutral
        "category_grey" to Color(0xFFB3B0A8), // Fees & Charges
        "category_neutral" to Color(0xFF98A6B8), // Transfer, Uncategorized
        // Income keeps a green: the ledger encodes credit as green, so this is
        // the one place the reserved hue belongs.
        "category_emerald" to Color(0xFF7FD4A8), // Income
    )
}

/**
 * Dark is the product, not a mode. There is deliberately no light scheme here:
 * light mode is deferred, and the no-hardcoded-colour rule is what keeps adding
 * one later an addition rather than a rewrite.
 */
/**
 * Every role is set explicitly, including the ones this app never reads by
 * name. Material's defaults are purple, and any role left unset leaks that
 * purple into a teal world through whichever component happens to read it --
 * FilterChip reads secondaryContainer, and got Material's #4A4458 until this
 * was completed. `noRoleKeepsItsMaterialDefault` in ContrastTest holds the line.
 */
val DarkColors: ColorScheme = darkColorScheme(
    primary = KhataPalette.accent,
    onPrimary = KhataPalette.heroStops.last(),
    primaryContainer = KhataPalette.heroStops.first(),
    onPrimaryContainer = KhataPalette.onSurface,
    inversePrimary = KhataPalette.heroStops.first(),

    secondary = KhataPalette.accentDeep,
    onSecondary = KhataPalette.heroStops.last(),
    // The selected-chip pair: aqua on petrol, at 7.5:1.
    secondaryContainer = KhataPalette.heroStops.first(),
    onSecondaryContainer = KhataPalette.accent,

    tertiary = KhataPalette.accentDeep,
    onTertiary = KhataPalette.heroStops.last(),
    tertiaryContainer = KhataPalette.heroStops[1],
    onTertiaryContainer = KhataPalette.onSurface,

    background = KhataPalette.ground,
    onBackground = KhataPalette.onSurface,
    surface = KhataPalette.ground,
    onSurface = KhataPalette.onSurface,
    surfaceVariant = KhataPalette.heroStops[1],
    onSurfaceVariant = KhataPalette.onSurfaceDim,
    surfaceTint = KhataPalette.accent,
    inverseSurface = KhataPalette.onSurface,
    inverseOnSurface = KhataPalette.ground,

    // Petrol-tinted neutrals, so elevated Material surfaces stay in the world.
    surfaceBright = Color(0xFF1C2E30),
    surfaceDim = Color(0xFF040E10),
    surfaceContainerLowest = Color(0xFF030B0C),
    surfaceContainerLow = Color(0xFF0A1A1C),
    surfaceContainer = Color(0xFF0E2022),
    surfaceContainerHigh = Color(0xFF12282A),
    surfaceContainerHighest = Color(0xFF173032),

    outline = KhataPalette.onSurfaceFaint,
    outlineVariant = Color(0xFF1C2E30),
    scrim = Color(0xFF000000),

    error = KhataPalette.alert,
    onError = Color(0xFF2A0A06),
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFFFDAD5),
)

fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) +
        0.7152 * channel(color.green) +
        0.0722 * channel(color.blue)
}
