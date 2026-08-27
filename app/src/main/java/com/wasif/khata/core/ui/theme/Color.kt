package com.wasif.khata.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFB23E06),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE0CC),
    onPrimaryContainer = Color(0xFF431300),
    secondary = Color(0xFF5C5445),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F0E8),
    onBackground = Color(0xFF241E17),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF241E17),
    surfaceVariant = Color(0xFFE8E0D2),
    onSurfaceVariant = Color(0xFF554B3D),
    outline = Color(0xFF8A7D6B),
    outlineVariant = Color(0xFFD6CCBB),
    error = Color(0xFFA32116),
    onError = Color(0xFFFFFFFF),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFF9A62),
    onPrimary = Color(0xFF4A1600),
    primaryContainer = Color(0xFF8A2F03),
    onPrimaryContainer = Color(0xFFFFE0CC),
    secondary = Color(0xFFD6C7AC),
    onSecondary = Color(0xFF3A3223),
    background = Color(0xFF16130F),
    onBackground = Color(0xFFF0E8DA),
    surface = Color(0xFF1E1A15),
    onSurface = Color(0xFFF0E8DA),
    surfaceVariant = Color(0xFF332D25),
    onSurfaceVariant = Color(0xFFD3C8B6),
    outline = Color(0xFF9A8D7B),
    outlineVariant = Color(0xFF4A4237),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690006),
)

val CategoryColorsLight: Map<String, Color> = mapOf(
    "category_green" to Color(0xFF4A7C36),
    "category_orange" to Color(0xFFC2611F),
    "category_blue" to Color(0xFF2A5D8F),
    "category_slate" to Color(0xFF4F5D68),
    "category_amber" to Color(0xFF9C6F0A),
    "category_teal" to Color(0xFF1C6E63),
    "category_red" to Color(0xFFA32116),
    "category_violet" to Color(0xFF6B4A9E),
    "category_pink" to Color(0xFFA63A6B),
    "category_indigo" to Color(0xFF3B4A8F),
    "category_rose" to Color(0xFF9E3450),
    "category_bronze" to Color(0xFF7D5522),
    "category_grey" to Color(0xFF6B6255),
    "category_emerald" to Color(0xFF17694E),
    "category_neutral" to Color(0xFF756B5C),
)

val CategoryColorsDark: Map<String, Color> = mapOf(
    "category_green" to Color(0xFF93C47D),
    "category_orange" to Color(0xFFE9A06A),
    "category_blue" to Color(0xFF8CB4DC),
    "category_slate" to Color(0xFFA7B4BE),
    "category_amber" to Color(0xFFDDB55E),
    "category_teal" to Color(0xFF6FBDB0),
    "category_red" to Color(0xFFEC9C93),
    "category_violet" to Color(0xFFBCA3E0),
    "category_pink" to Color(0xFFE29BBB),
    "category_indigo" to Color(0xFFA3AEE0),
    "category_rose" to Color(0xFFE09CAB),
    "category_bronze" to Color(0xFFC9A472),
    "category_grey" to Color(0xFFB9B1A3),
    "category_emerald" to Color(0xFF6FC0A0),
    "category_neutral" to Color(0xFFB3AA9B),
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
