package com.wasif.khata.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.wasif.khata.R

// Four families, three roles. The split exists because numerals never need
// Bengali glyphs -- which is what makes a Latin-only serif safe on amounts
// while Bengali merchant names stay on a face that matches the body metrics.
object KhataFonts {

    val Display: FontFamily = FontFamily(
        Font(R.font.fraunces_regular, FontWeight.Normal),
        Font(R.font.fraunces_semibold, FontWeight.SemiBold),
        Font(R.font.fraunces_bold, FontWeight.Bold),
    )

    val Text: FontFamily = FontFamily(
        Font(R.font.instrument_sans_regular, FontWeight.Normal),
        Font(R.font.instrument_sans_medium, FontWeight.Medium),
        Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
        Font(R.font.instrument_sans_bold, FontWeight.Bold),
    )

    val Bengali: FontFamily = FontFamily(
        Font(R.font.noto_sans_bengali_regular, FontWeight.Normal),
        Font(R.font.noto_sans_bengali_medium, FontWeight.Medium),
        Font(R.font.noto_sans_bengali_semibold, FontWeight.SemiBold),
    )

    // Wordmark only. Li Swarnali Okkhor is a display face: it is unreadable at
    // row sizes and its metrics do not match Instrument Sans, so it must never
    // reach a merchant name.
    val Wordmark: FontFamily = FontFamily(
        Font(R.font.li_swarnali_okkhor, FontWeight.Normal),
    )
}
