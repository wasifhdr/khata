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

fun sharedAxisXEnter(m: Motion): EnterTransition =
    slideInHorizontally(tween(m.standard, easing = m.enter)) { (it * SharedAxisTravel).toInt() } +
        fadeIn(tween(m.standard, easing = m.enter))

fun sharedAxisXExit(m: Motion): ExitTransition =
    slideOutHorizontally(tween(m.standard, easing = m.exit)) { -(it * SharedAxisTravel).toInt() } +
        fadeOut(tween(m.standard, easing = m.exit))

/**
 * Back is not forward played backwards, and -- more importantly -- back is not
 * *played* at all when it comes from the gesture. Predictive back **seeks** these
 * transitions by how far the thumb has travelled, so everything the forward pair
 * is allowed to do, these are not:
 *
 *  - **No delay.** A `delayMillis` is dead time when a transition is seeked: the
 *    first third of the drag would show nothing moving at all.
 *  - **One duration on both halves.** Seeking normalises both to the same 0..1
 *    progress, so a 150ms exit against a 400ms enter finishes the outgoing screen
 *    a third of the way through the gesture and then stalls.
 *  - **An easing that tracks the thumb.** Both standard curves fail here, in
 *    opposite directions: [Motion.exit] is 15% travelled at the halfway point,
 *    so the screen ignores the drag and then lurches; [Motion.enter] is 95%
 *    travelled, so the screen is all but gone before the thumb is halfway and a
 *    released gesture snaps back from nowhere. This one is ~51% at halfway --
 *    near-proportional, so the screen goes where the thumb goes, with the ends
 *    softened for the times back is a button press and this is played rather
 *    than seeked.
 *
 * Reusing the forward fade-through here broke all three, which is what made the
 * system back gesture feel wrong.
 */
internal val SeekedBack = CubicBezierEasing(0.5f, 0.3f, 0.7f, 1f)

/**
 * The destination is *revealed*, not introduced: it was already on the stack
 * underneath, so the only thing that moves is the screen being left, which
 * recedes and fades off the top of it.
 *
 * [EnterTransition.None] is deliberate rather than lazy. The outgoing screen is
 * drawn above the incoming one during a pop, so animating the incoming one as
 * well would be invisible for the part that matters and would put two translucent
 * full-screen layers over the window background at once -- both a muddier picture
 * and a second full-screen layer to composite on the frames that can least afford
 * one.
 */
fun popRevealEnter(): EnterTransition = EnterTransition.None

fun popRevealExit(m: Motion): ExitTransition =
    scaleOut(tween(m.standard, easing = SeekedBack), targetScale = 0.90f) +
        fadeOut(tween(m.standard, easing = SeekedBack))

fun sharedAxisXPopEnter(m: Motion): EnterTransition =
    slideInHorizontally(tween(m.standard, easing = SeekedBack)) { -(it * SharedAxisTravel).toInt() } +
        fadeIn(tween(m.standard, easing = SeekedBack))

fun sharedAxisXPopExit(m: Motion): ExitTransition =
    slideOutHorizontally(tween(m.standard, easing = SeekedBack)) { (it * SharedAxisTravel).toInt() } +
        fadeOut(tween(m.standard, easing = SeekedBack))

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
