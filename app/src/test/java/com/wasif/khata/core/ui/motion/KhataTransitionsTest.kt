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
    fun `the back curve keeps up with the thumb instead of stalling`() {
        // Predictive back *seeks* the pop transitions by finger travel rather
        // than playing them over a duration, so the curve is read at whatever
        // fraction of the drag the thumb has reached. A curve that has barely
        // moved at the halfway point is a screen that ignores the first half of
        // the gesture and then lurches -- which is exactly how the forward exit
        // curve behaves, and why reusing it for back felt broken.
        // A band, not a floor. There are two ways to get this wrong and the
        // forward curves are one of each: Motion.exit stalls at 0.15, so the
        // screen ignores the drag then lurches; Motion.enter races to 0.95, so
        // it is gone before the thumb is halfway and snaps back from nowhere if
        // the gesture is released. Near-proportional is the only answer that
        // holds under a finger.
        val halfway = SeekedBack.transform(0.5f)
        assertTrue(
            "the back curve should track the thumb near-proportionally, was $halfway",
            halfway in 0.40f..0.62f,
        )

        val m = Motion()
        assertTrue("Motion.exit stalls; it is what this curve exists to avoid", m.exit.transform(0.5f) < 0.40f)
        assertTrue("Motion.enter races; it is the other way to get this wrong", m.enter.transform(0.5f) > 0.62f)
    }

    @Test
    fun `the back curve starts and ends where a seek needs it to`() {
        // A seeked transition is read at 0 the moment the gesture starts and at
        // 1 when it commits. Anything else shows a jump on touch-down or on
        // release.
        assertTrue(SeekedBack.transform(0f) == 0f)
        assertTrue(SeekedBack.transform(1f) == 1f)
    }
}
