package com.wasif.khata.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensTest {

    @Test
    fun `the screen gutter and touch target match the spec`() {
        val s = Spacing()
        assertEquals(18.dp, s.screenHorizontal)
        assertEquals(48.dp, s.minTouchTarget)
    }

    @Test
    fun `the ledger headspace is fixed`() {
        // The ledger is the one screen that cannot bottom-anchor: a list of
        // 3,000 rows has no bottom, and a flexible spacer would push rows off
        // screen. It gets a fixed headspace instead.
        assertEquals(132.dp, Spacing().headspaceLedger)
    }

    @Test
    fun `field intensity runs from zero to one`() {
        assertEquals(0f, FieldIntensity.Off.alpha)
        assertEquals(1f, FieldIntensity.Full.alpha)
        assertTrue(FieldIntensity.Dim.alpha < FieldIntensity.Mid.alpha)
        assertTrue(FieldIntensity.Mid.alpha < FieldIntensity.Full.alpha)
    }

    @Test
    fun `only Off disables the field entirely`() {
        // Glass needs something to refract. At Off it has nothing, so the glass
        // component has to fall back to a solid fill -- that branch keys off
        // this exact condition.
        val zero = FieldIntensity.entries.filter { it.alpha == 0f }
        assertEquals(listOf(FieldIntensity.Off), zero)
    }
}
