package com.wasif.khata.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.wasif.khata.core.ui.theme.LocalThemeSpec
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Blur the backdrop at a fraction of its resolution.
 *
 * A blur is a low-pass filter: everything a 22dp radius would keep survives
 * downsampling, so this is invisible in the result and costs a fraction of the
 * fill rate. Haze's default is [HazeInputScale.None] -- full resolution -- which
 * is what made a screenful of glass chips unscrollable.
 */
@OptIn(ExperimentalHazeApi::class)
internal val GlassInputScale = HazeInputScale.Auto

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
 *
 * @param refracts whether this surface samples the backdrop for real. Ingredient
 * 1 is the only expensive one: each blurred surface is its own render pass, and
 * the cost is in the *count*, not the area -- the transaction editor puts a chip
 * on every account, category and kind, which is two dozen passes on one screen
 * and made it unscrollable. What it costs is not what it buys, though. These
 * chips sit on the mesh field, which is four radial pools and a grain; a 22dp
 * blur of something that smooth returns the same picture back. So a chip keeps
 * the tint and the bevel -- ingredients 2 and 3, which are what the eye actually
 * reads as glass -- and lets the field show through unblurred.
 *
 * Pass `true` wherever the backdrop has detail worth blurring: a bar with the
 * page scrolling under it, or a card over content rather than over the field.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun KhataGlass(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    raised: Boolean = false,
    refracts: Boolean = true,
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
    } else if (!refracts) {
        // The tint alone, with the field showing through it unblurred -- see the
        // `refracts` parameter for why that is the same picture here, and what it
        // saves.
        Modifier.background(Color.White.copy(alpha = tintAlpha), shape)
    } else {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = spec.ground,
                tint = HazeTint(Color.White.copy(alpha = tintAlpha)),
                blurRadius = 22.dp,
                noiseFactor = 0.04f,
            ),
        ) {
            inputScale = GlassInputScale
        }
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
