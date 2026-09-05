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
    fun `the crossfade is confined so the fade cannot outrun the movement`() {
        // The bug this exists for: alpha travels 100% while a scale travels 10%,
        // so given the same duration the fade always wins and the screen reads as
        // gone before it has visibly moved. Confining the fade to a threshold and
        // leaving the scale to run the whole way is what fixes that, so the fade
        // must stay strictly shorter than the movement it sits inside.
        assertTrue("the fade must end before the movement does", FadeThrough < 1f)
        assertTrue("but it must actually happen", FadeThrough > 0f)

        // And it must survive an instant Motion as a hard cut rather than as a
        // stray frame of half-faded screen.
        val instant = Motion.forDurationScale(0f)
        assertTrue((instant.standard * FadeThrough).toInt() == 0)
    }
}
