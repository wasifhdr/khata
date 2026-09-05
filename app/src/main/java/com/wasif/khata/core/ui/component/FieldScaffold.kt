package com.wasif.khata.core.ui.component

import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalThemeSpec
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Ground, mesh field, and the [HazeState] the field registers itself against --
 * the stack every screen needs and none of them should own.
 *
 * The field is one of only two Haze sources; the other is a page's own scrolling
 * content, registered so the collapsed top bar can blur what passes under it.
 * Never a list *row*. Blur cost scales
 * with how often the backdrop changes, so sampling a static mesh is affordable
 * on every screen while sampling a Paging list would not be affordable on any.
 * Glass drawn over this therefore refracts the field, never the content.
 */
@Composable
fun FieldScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(HazeState) -> Unit,
) {
    val spec = LocalThemeSpec.current
    val haze = remember { HazeState() }

    Box(modifier.background(spec.ground)) {
        // Put the source on the mesh field, never on a scrolling list: blur cost
        // scales with how often the backdrop changes.
        FieldBackdrop(Modifier.fillMaxSize().hazeSource(haze))
        content(haze)
    }
}

/**
 * A hash-noise speckle, generated per pixel rather than tiled from a bitmap so
 * there is no asset to keep resident and no visible repeat.
 *
 * The alpha is the whole effect: the RGB is flat white and only the *coverage*
 * varies, so the grain lands as a fine dither rather than a grey fog. That is
 * what breaks the banding a large, dark, smooth gradient shows on OLED -- the
 * same reason the glass carries grain in ingredient 4.
 */
private const val GrainShaderSource = """
uniform float alpha;

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 coord) {
    float n = hash(coord);
    // Signed: half the pixels darken and half lighten, so the mean colour is
    // preserved. An add-only grain can only lighten, which fogs a dark ground
    // -- measured at +3/255 on the field before this was split.
    half lum = half(step(0.5, n));
    half a = half(alpha);
    // Premultiplied, so RGB is already scaled by alpha.
    return half4(lum * a, lum * a, lum * a, a);
}
"""

/** How far the grain is pushed. Above roughly 0.05 it stops reading as texture and starts reading as noise. */
internal const val FieldGrainAlpha = 0.035f

/**
 * Soft radial pools rather than a full-bleed linear gradient: a linear wash
 * covers every pixel at its own alpha and swamps the content, where pools leave
 * most of the ground untouched and read as light falling on a surface.
 *
 * Each pool is capped well below full opacity even at Full intensity, because
 * the key stop is already the lightest colour the mesh should ever reach.
 */
@Composable
fun FieldBackdrop(modifier: Modifier = Modifier) {
    val spec = LocalThemeSpec.current
    val strength = spec.intensity.alpha
    val grain = remember { RuntimeShader(GrainShaderSource) }

    Box(
        // drawWithCache, not drawBehind: the brushes depend on the size and the
        // spec and on nothing else, but a `Brush` built inside the draw lambda is
        // a fresh Skia shader on every frame -- five of them, behind every screen,
        // for a picture that never changes. Built here they survive until the size
        // or the theme actually moves.
        modifier.drawWithCache {
            fun pool(colour: Color, alpha: Float, cx: Float, cy: Float, r: Float) =
                Brush.radialGradient(
                    colors = listOf(colour.copy(alpha = alpha * strength), Color.Transparent),
                    center = Offset(size.width * cx, size.height * cy),
                    radius = size.minDimension * r,
                )

            val pools = if (strength > 0f) {
                listOf(
                    pool(spec.field.keyStop, 0.85f, 0.14f, 0.02f, 1.15f),
                    pool(spec.field.keyStop, 0.55f, 0.92f, 0.16f, 0.95f),
                    pool(KhataPalette.heroStops.first(), 0.60f, 0.70f, 0.78f, 1.00f),
                    pool(spec.ground, 0.70f, 0.10f, 0.95f, 0.90f),
                )
            } else {
                emptyList()
            }

            grain.setFloatUniform("alpha", FieldGrainAlpha)
            val grainBrush = ShaderBrush(grain)

            onDrawBehind {
                drawRect(spec.ground)
                pools.forEach { drawRect(brush = it) }
                // Grain goes on last, over the ground as well as the pools: the
                // flat ground is the darkest surface in the app and banding shows
                // there too, so gating this on intensity would leave Off the one
                // theme that bands.
                drawRect(brush = grainBrush)
            }
        },
    )
}
