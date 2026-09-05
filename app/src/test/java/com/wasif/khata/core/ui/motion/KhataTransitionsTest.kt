package com.wasif.khata.core.ui.motion

import com.wasif.khata.core.ui.theme.Motion
import com.wasif.khata.navigation.KhataRoutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KhataTransitionsTest {

    @Test
    fun `hub to module is a shared-axis move in both directions`() {
        assertTrue(isHubTransition(KhataRoutes.Modules, KhataRoutes.Wallet))
        assertTrue(isHubTransition(KhataRoutes.Wallet, KhataRoutes.Modules))
    }

    @Test
    fun `hub to settings is a shared-axis move`() {
        // Settings is not a module, but it is reached from the hub and returns
        // to it, so it sits on the same horizontal track.
        assertTrue(isHubTransition(KhataRoutes.Modules, KhataRoutes.Settings))
    }

    @Test
    fun `moves within a module fade through rather than sliding`() {
        // Wallet -> Ledger -> Editor are all inside the wallet module. Sliding
        // would claim a peer relationship these screens do not have.
        assertFalse(isHubTransition(KhataRoutes.Wallet, KhataRoutes.Ledger))
        assertFalse(isHubTransition(KhataRoutes.Ledger, KhataRoutes.EditorNew))
        assertFalse(isHubTransition(KhataRoutes.Ledger, KhataRoutes.EditorEdit))
    }

    @Test
    fun `an unknown route falls back to fade-through rather than crashing`() {
        // Route can be null on a graph node. Fade-through is the safe default:
        // it makes no spatial claim, so it cannot be wrong about one.
        assertFalse(isHubTransition(null, KhataRoutes.Wallet))
        assertFalse(isHubTransition(KhataRoutes.Wallet, null))
    }

    @Test
    fun `the back curve is the one SystemUI animates back with`() {
        // Not a curve of our own choosing. The predictive back design spec gives
        // the interpolator as (.1, .1, 0, 1) so that an app's transition and the
        // system animation underneath it move together, and it is deliberately
        // front-loaded -- most of the travel is spent early, because a back
        // gesture should leave quickly and let the destination arrive.
        //
        // An earlier version of this used a near-proportional curve on the
        // reasoning that a seeked transition should track the thumb one-to-one.
        // That reasoning was wrong, and this pins the spec against it.
        assertTrue(
            "should be past three quarters by the fade-through threshold, was " +
                SeekedBack.transform(0.35f),
            SeekedBack.transform(0.35f) > 0.75f,
        )
        assertTrue(SeekedBack.transform(0f) == 0f)
        assertTrue(SeekedBack.transform(1f) == 1f)
    }

    @Test
    fun `the forward crossfade is confined so it cannot outrun the movement`() {
        // Forward only. Alpha travels 100% while a slide travels 20%, so on one
        // duration the fade wins and the move reads as a blink; the threshold
        // ends the outgoing fade before the incoming one starts, so the slide is
        // what carries it.
        //
        // The pop pair deliberately does not use this. A threshold has one moment
        // where neither screen is showing, which is invisible in a transition that
        // is played but can be held indefinitely in one that predictive back
        // seeks -- so the pop expresses leaving with travel and no fade at all.
        assertTrue("the fade must end before the movement does", FadeThrough < 1f)
        assertTrue("but it must actually happen", FadeThrough > 0f)

        // And it must survive an instant Motion as a hard cut rather than as a
        // stray frame of half-faded screen.
        val instant = Motion.forDurationScale(0f)
        assertTrue((instant.standard * FadeThrough).toInt() == 0)
    }
}
