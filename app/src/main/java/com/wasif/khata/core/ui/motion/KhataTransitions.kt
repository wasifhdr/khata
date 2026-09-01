package com.wasif.khata.core.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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

fun sharedAxisXEnter(m: Motion): EnterTransition =
    slideInHorizontally(tween(m.standard, easing = m.enter)) { (it * SharedAxisTravel).toInt() } +
        fadeIn(tween(m.standard, easing = m.enter))

fun sharedAxisXExit(m: Motion): ExitTransition =
    slideOutHorizontally(tween(m.standard, easing = m.exit)) { -(it * SharedAxisTravel).toInt() } +
        fadeOut(tween(m.standard, easing = m.exit))

fun sharedAxisXPopEnter(m: Motion): EnterTransition =
    slideInHorizontally(tween(m.standard, easing = m.enter)) { -(it * SharedAxisTravel).toInt() } +
        fadeIn(tween(m.standard, easing = m.enter))

fun sharedAxisXPopExit(m: Motion): ExitTransition =
    slideOutHorizontally(tween(m.standard, easing = m.exit)) { (it * SharedAxisTravel).toInt() } +
        fadeOut(tween(m.standard, easing = m.exit))

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
    fadeIn(tween(m.standard, delayMillis = m.quick, easing = m.enter)) +
        scaleIn(
            animationSpec = tween(m.standard, delayMillis = m.quick, easing = m.enter),
            initialScale = 0.92f,
        )

fun fadeThroughExit(m: Motion): ExitTransition = fadeOut(tween(m.quick, easing = m.exit))
