package com.wasif.khata.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Fraunces on the numerals, Instrument Sans on the working text. The previous
// direction deferred a display face because Bengali had no matching pair --
// which only ever applied to text. Numerals never need Bengali glyphs, so a
// Latin-only serif is safe on amounts.
val KhataTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = KhataFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 50.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = KhataFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 40.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = KhataFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = KhataFonts.Text,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp,
    ),
)

// Tabular figures so amounts align on the decimal down a 3,000-row column
// instead of drifting with each digit's width.
val AmountTextStyle = TextStyle(
    fontFamily = KhataFonts.Display,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "tnum",
)

val WordmarkTextStyle = TextStyle(
    fontFamily = KhataFonts.Wordmark,
    fontWeight = FontWeight.Normal,
    fontSize = 54.sp,
    lineHeight = 70.sp,
)

// 34 against 11. The old build put every glyph between 12 and 20sp, which is
// what "everything is the same size" was actually pointing at; the gap is
// doing more work here than the absolute size.
val PageHeadingStyle = TextStyle(
    fontFamily = KhataFonts.Display,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 39.sp,
)

/**
 * The same heading once a page has been scrolled and its air spent. Same family and
 * weight as [PageHeadingStyle] deliberately: a page that changes typeface halfway
 * down reads as a different page, and the heading is the one thing meant to stay
 * constant the whole way.
 */
val PageHeadingCollapsedStyle = PageHeadingStyle.copy(
    fontSize = 20.sp,
    lineHeight = 24.sp,
)

val PageSublineStyle = TextStyle(
    fontFamily = KhataFonts.Text,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.6.sp,
)

// Bengali merchant names, notes, and anything derived from an SMS.
val BengaliBodyStyle = TextStyle(
    fontFamily = KhataFonts.Bengali,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

// The hub tagline is the app's only Bengali UI string. Instrument Sans (the
// bodyMedium family) has no Bengali glyphs, so it was silently falling
// through to whichever Bengali font the device happens to ship -- exactly
// the per-device variance the bundled Noto cut was meant to remove. Same
// size/weight as bodyMedium so the fix is only the family, not the layout.
val TaglineTextStyle = TextStyle(
    fontFamily = KhataFonts.Bengali,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)
