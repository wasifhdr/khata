package com.wasif.khata.core.ui.motion

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
}
