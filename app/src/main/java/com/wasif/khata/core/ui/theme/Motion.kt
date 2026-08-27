package com.wasif.khata.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.staticCompositionLocalOf

data class Motion(
    val quick: Int = 150,
    val standard: Int = 250,
    val emphasized: Int = 400,
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
)

val LocalMotion = staticCompositionLocalOf { Motion() }
