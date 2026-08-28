package com.wasif.khata.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.wasif.khata.core.ui.theme.LocalThemeSpec
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

@Composable
fun rememberKhataHazeState(): HazeState = remember { HazeState() }

/**
 * Marks a composable as the backdrop the glass samples. Put this on the mesh
 * field, never on a scrolling list: blur cost scales with how often the
 * backdrop changes, and a Paging list changes every frame.
 */
fun Modifier.khataFieldSource(hazeState: HazeState): Modifier = this.hazeSource(hazeState)

/**
 * The five ingredients. Most glassmorphism ships blur and tint, stops, and
 * looks like a grey card:
 *
 *  1. backdrop blur          2. translucent tint
 *  3. gradient edge highlight -- a lit bevel, the one that sells it
 *  4. grain                  5. saturation, which Haze gets from sampling the
 *                               real backdrop rather than compositing a flat fill
 *
 * Ingredients 1, 2 and 4 come from Haze's own style; 3 is drawn here because
 * Haze has no equivalent, and skipping it is what makes most glass read as a
 * flat translucent rectangle.
 */
@Composable
fun KhataGlass(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    raised: Boolean = false,
    content: @Composable () -> Unit,
) {
    val spec = LocalThemeSpec.current
    val tintAlpha = if (raised) 0.085f else 0.055f

    // A lit bevel, brighter at the top-left and fading to nothing.
    val edge = Brush.linearGradient(
        0.00f to Color.White.copy(alpha = 0.48f),
        0.34f to Color.White.copy(alpha = 0.10f),
        0.62f to Color.Transparent,
        1.00f to Color.White.copy(alpha = 0.08f),
    )

    val surface = if (spec.usesSolidSurfaces) {
        // No field means nothing to refract. A solid fill is a deliberate flat
        // theme; a blur over nothing is a bug that looks like one.
        Modifier.background(spec.ground.copy(alpha = 0.92f), shape)
    } else {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = spec.ground,
                tint = HazeTint(Color.White.copy(alpha = tintAlpha)),
                blurRadius = 22.dp,
                noiseFactor = 0.04f,
            ),
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(surface)
            .border(1.dp, edge, shape),
    ) {
        content()
    }
}
