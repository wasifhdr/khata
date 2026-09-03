package com.wasif.khata.core.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

data class Motion(
    val quick: Int = 150,
    val standard: Int = 250,
    val emphasized: Int = 400,
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
) {
    companion object {
        /**
         * "Remove animations" in Accessibility settings reports itself as an
         * animator duration scale of exactly 0. Honouring it means an instant
         * cut, not a fast animation -- someone who turned motion off because it
         * makes them ill is not served by a 50ms version of the same motion.
         *
         * Any other scale is left alone. Developer options' 0.5x/10x already
         * apply at the platform layer, so scaling here as well would compound.
         */
        fun forDurationScale(scale: Float): Motion =
            if (scale == 0f) Motion(quick = 0, standard = 0, emphasized = 0) else Motion()
    }
}

val LocalMotion = staticCompositionLocalOf { Motion() }

/**
 * The animator duration scale as a live value. An observer rather than a single
 * read, because toggling "Remove animations" does not recreate the Activity --
 * a one-shot read would leave the app animating until the next cold start.
 */
@Composable
fun rememberSystemMotion(): Motion {
    val resolver = LocalContext.current.contentResolver
    var scale by remember(resolver) {
        mutableFloatStateOf(
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        )
    }

    DisposableEffect(resolver) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                scale = Settings.Global.getFloat(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }
        }
        resolver.registerContentObserver(uri, false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return remember(scale) { Motion.forDurationScale(scale) }
}
