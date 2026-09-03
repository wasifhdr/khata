package com.wasif.khata.core.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

/**
 * One named colour option on the ground or accent axis of the tuner. The name
 * used to live only in a `//` comment next to the hex, which is how it ended
 * up displayed nowhere -- a parallel `List<String>` is one insertion away from
 * mislabelling every swatch, so the name travels with the colour instead.
 */
data class NamedSwatch(val name: String, val color: Color)

object KhataPalette {

    val ground: Color = Color(0xFF061214)
    val onSurface: Color = Color(0xFFEDF2F1)
    val onSurfaceDim: Color = Color(0xFFA8B8B8)
    /**
     * Borders and disabled controls. Raised from #6E8180 when the field moved
     * onto every screen: at the old value a border over a lit field sat at
     * 2.30:1, under the 3:1 graphical floor. The cost is a narrower
     * enabled/disabled separation -- 1.37x on the ground rather than 1.60x --
     * which is why disabled controls also carry `enabled = false` semantics
     * rather than relying on the colour alone.
     */
    val onSurfaceFaint: Color = Color(0xFF8A9E9C)

    val accent: Color = Color(0xFF8FE0CE)
    val accentDeep: Color = Color(0xFF5FC9B2)

    val alert: Color = Color(0xFFFF7A6B)
    val warn: Color = Color(0xFFF2A63E)

    /**
     * Direction on a control, where out and in are a choice being offered rather
     * than a row being read. Fixed rather than tuned, for the reason category
     * colours are: they encode which way money moves, not taste — and an accent
     * that follows the tuner would turn the "in" target marigold.
     *
     * Distinct from the ledger's convention (credits take the accent, debits stay
     * paper, see MoneyText), which is about finding a credit while scrolling. Both
     * clear 7:1 on every shipped ground; the glyph still carries the meaning alone,
     * so colour is reinforcement here and never the signal.
     */
    val moneyOut: Color = alert
    val moneyIn: Color = Color(0xFF6BD99A)

    /** Petrol. The gradient the active module card is filled with. */
    val heroStops: List<Color> = listOf(
        Color(0xFF12403F),
        Color(0xFF0C2E30),
        Color(0xFF08211F),
    )

    val grounds: List<NamedSwatch> = listOf(
        NamedSwatch("Teal black", Color(0xFF061214)), // default
        NamedSwatch("Indigo black", Color(0xFF0B0C18)),
        NamedSwatch("Navy black", Color(0xFF08111C)),
        NamedSwatch("Cool black", Color(0xFF0A0D0F)),
    )

    val accents: List<NamedSwatch> = listOf(
        NamedSwatch("Pale aqua", Color(0xFF8FE0CE)), // default
        NamedSwatch("Marigold", Color(0xFFFFB627)),
        NamedSwatch("Chartreuse", Color(0xFFB6E24A)),
        NamedSwatch("Warm sand", Color(0xFFE8C9A0)),
    )

    /**
     * Key stops are **composited** values -- the colour that actually reaches
     * the screen once the mesh stop is drawn at its own alpha over the ground,
     * not the raw gradient colour. Asserting the raw colour would test a pixel
     * that is never rendered, and would fail the default theme for no reason.
     */
    val fields: List<FieldPalette> = listOf(
        // Trimmed so the *grain-lit* peak -- not the raw stop -- clears 4.5:1
        // against onSurfaceVariant. The grain lifts the brightest pixel by
        // ~9/255, so a stop that passes on paper fails on screen; sizing to the
        // raw value is what let dim text land at 3.92:1 over the mesh.
        FieldPalette("Verdigris", Color(0xFF124740)), // default
        FieldPalette("Abyss", Color(0xFF12464D)),
        FieldPalette("Counterpoint", Color(0xFF0F474C)),
        // Cyan is deliberately the most trimmed. It was already darkened once
        // for this same reason; being the brightest field is not worth being
        // the one nothing can sit on.
        FieldPalette("Cyan", Color(0xFF10474B)),
        // Violet needs no trim -- it is dark enough already at full strength.
        FieldPalette("Violet", Color(0xFF452F76)),
        FieldPalette("Monochrome", Color(0xFF124746)),
        FieldPalette("Deep sea", Color(0xFF0E464D)),
        FieldPalette("Mist", Color(0xFF2C414D)),
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
    // Decorative only (the ledger row hairline): a disabled control is exempt
    // from the border floor and uses `outline` instead, so this value only has
    // to clear it as a border. The old #1C2E30 sat at 1.34:1 against every
    // ground -- a "separator" nothing could see. #456865 clears 3.09:1 (the
    // worst ground) while staying visibly quieter than `outline`'s 4.6:1+.
    outlineVariant = Color(0xFF456865),
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

/**
 * True when a badge or icon drawn on top of [color] reads better dark than
 * light. The tuner's own swatches span both a near-black ground/field and a
 * pastel accent, so a single fixed "on swatch" colour cannot stay legible
 * across all of them -- this is what lets one call site pick correctly for
 * either.
 */
fun isLightColor(color: Color): Boolean = relativeLuminance(color) > 0.5

/**
 * The tuner exposes one accent stop, but several roles (the selected-chip
 * pair, `secondary`/`tertiary`) need a second, deeper stop of the same hue --
 * "this accent, pressed" rather than some unrelated colour. The relationship
 * is fitted to the shipped pair (accent `#8FE0CE` -> `accentDeep` `#5FC9B2`):
 * converted to HSV, value drops to 90% and saturation rises to 145% of the
 * source, hue held fixed -- reproduces `accentDeep` within rounding.
 * Applying the same transform to an arbitrary accent is what lets a tuned
 * accent (e.g. Marigold) get a matching deep stop instead of inheriting the
 * default's.
 */
fun deepenAccent(color: Color): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.45f).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * 0.90f).coerceIn(0f, 1f)
    return Color(AndroidColor.HSVToColor(hsv))
}
