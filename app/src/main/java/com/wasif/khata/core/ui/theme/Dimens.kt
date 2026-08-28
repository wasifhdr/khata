package com.wasif.khata.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val screenHorizontal: Dp = 18.dp,
    val minTouchTarget: Dp = 48.dp,
    /**
     * The ledger cannot bottom-anchor like the other screens: a list of 3,000
     * rows has no bottom to anchor to, and a flexible spacer would push rows
     * off screen and make the monthly review worse. It gets this instead --
     * enough to read as the same family, small enough that six rows stay
     * visible before scrolling.
     */
    val headspaceLedger: Dp = 132.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** How hard the mesh field behind the glass is pushed. */
enum class FieldIntensity(val alpha: Float) {
    Off(0f),
    Dim(0.34f),
    Mid(0.64f),
    Full(1f),
}
