package com.wasif.khata.core.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.wasif.khata.core.ui.theme.Motion
import com.wasif.khata.navigation.KhataRoutes

/**
 * How far a screen travels on the shared axis, as a fraction of its width.
 * A full-width slide reads as a page turn; this reads as two panels on one
 * track, which is what the hub and a module actually are.
 */
private const val SharedAxisTravel = 0.20f

/**
 * True when this route pair crosses the hub boundary, which is the only place
 * a spatial relationship exists to express. Everything else is a move *within*
 * a module, where sliding would assert a sideways adjacency that is not real.
 *
 * Null-safe on both sides: a graph node has no route, and fade-through is the
 * honest fallback because it makes no spatial claim at all.
 */
fun isHubTransition(from: String?, to: String?): Boolean =
    from == KhataRoutes.Modules || to == KhataRoutes.Modules

/**
 * Both halves cross-faded across the whole move, which on this app is close to
 * invisible: every screen paints the same field, so the background never changes
 * and a slow dissolve of sparse content over an unchanging ground reads as a
 * blink rather than a movement. That is why opening settings looked like it had
 * no animation at all. The crossfade is confined to [FadeThrough] instead, so
 * one screen is gone before the next arrives and the slide is left to carry it.
 */
fun sharedAxisXEnter(m: Motion): EnterTransition =
    slideInHorizontally(tween(m.standard, easing = m.enter)) { (it * SharedAxisTravel).toInt() } +
        fadeIn(m.thresholdIn())

fun sharedAxisXExit(m: Motion): ExitTransition =
    slideOutHorizontally(tween(m.standard, easing = m.exit)) { -(it * SharedAxisTravel).toInt() } +
        fadeOut(m.thresholdOut())

/**
 * Back is not forward played backwards, and back is not *played* at all when it
 * comes from the gesture: predictive back **seeks** these by how far the thumb
 * has travelled. The numbers below are the platform's own, from the predictive
 * back design spec, rather than anything invented here.
 *
 * The curve is the interpolator SystemUI animates its own back with, so an app
 * transition and the system one under it agree. It is deliberately front-loaded
 * -- 79% travelled at 35% progress -- because a back gesture should leave
 * quickly and let the destination arrive.
 */
internal val SeekedBack = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * The fade-through threshold. The outgoing screen is fully faded by here and the
 * incoming one only starts arriving after it, so the two are never both half
 * visible over each other.
 *
 * This is the fix for the first version of these transitions, which faded the
 * outgoing screen across the whole gesture while the destination sat fully
 * opaque underneath it from the first frame. Scale travels 10% and alpha travels
 * 100% in the same time, so the fade always won: the screen read as *gone* while
 * the shrink had barely started -- "zooms out slowly and disappears fast".
 *
 * A delay is normally dead time in a seeked transition, and this one is not only
 * because the *scale* runs the full duration underneath it. Something is moving
 * at every point of the drag; only the crossfade is confined to the threshold.
 */
internal const val FadeThrough = 0.35f

private fun Motion.thresholdOut() = tween<Float>((standard * FadeThrough).toInt(), easing = SeekedBack)

private fun Motion.thresholdIn() = tween<Float>(
    durationMillis = (standard * (1f - FadeThrough)).toInt(),
    delayMillis = (standard * FadeThrough).toInt(),
    easing = SeekedBack,
)

/**
 * The destination is revealed rather than introduced -- it was already on the
 * stack underneath -- so it arrives at 110% and settles to 100% while the screen
 * being left recedes to 90% off the top of it. Both scales are the spec's.
 */
fun popRevealEnter(m: Motion): EnterTransition =
    scaleIn(tween(m.standard, easing = SeekedBack), initialScale = 1.10f) +
        fadeIn(m.thresholdIn())

fun popRevealExit(m: Motion): ExitTransition =
    scaleOut(tween(m.standard, easing = SeekedBack), targetScale = 0.90f) +
        fadeOut(m.thresholdOut())

fun sharedAxisXPopEnter(m: Motion): EnterTransition =
    slideInHorizontally(tween(m.standard, easing = SeekedBack)) { -(it * SharedAxisTravel).toInt() } +
        fadeIn(m.thresholdIn())

fun sharedAxisXPopExit(m: Motion): ExitTransition =
    slideOutHorizontally(tween(m.standard, easing = SeekedBack)) { (it * SharedAxisTravel).toInt() } +
        fadeOut(m.thresholdOut())

/**
 * Fade-through: the outgoing screen leaves before the incoming one arrives, so
 * the two never cross-dissolve into an unreadable double image. The incoming
 * screen also grows slightly, which reads as "forward" without claiming a
 * direction on any axis.
 *
 * At an instant Motion every duration and delay here is 0, so this collapses to
 * a hard cut rather than a very fast animation.
 */
fun fadeThroughEnter(m: Motion): EnterTransition =
    fadeIn(m.thresholdIn()) +
        scaleIn(tween(m.standard, easing = m.enter), initialScale = 0.92f)

fun fadeThroughExit(m: Motion): ExitTransition = fadeOut(m.thresholdOut())
