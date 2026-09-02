package com.wasif.khata.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun `a duration scale of zero collapses every duration to an instant cut`() {
        val motion = Motion.forDurationScale(0f)

        assertEquals("quick should cut instantly", 0, motion.quick)
        assertEquals("standard should cut instantly", 0, motion.standard)
        assertEquals("emphasized should cut instantly", 0, motion.emphasized)
        assertTrue(motion.isInstant)
    }

    @Test
    fun `the normal duration scale keeps the spec durations`() {
        val motion = Motion.forDurationScale(1f)

        assertEquals(150, motion.quick)
        assertEquals(250, motion.standard)
        assertEquals(400, motion.emphasized)
        assertFalse(motion.isInstant)
    }

    @Test
    fun `a slowed animator scale is not treated as removed animations`() {
        // Developer options can set 0.5x or 10x. Only exactly 0 means the user
        // asked for no motion; scaling durations ourselves would double-apply,
        // because the platform already scales what it hands the animator.
        assertEquals(250, Motion.forDurationScale(0.5f).standard)
        assertEquals(250, Motion.forDurationScale(10f).standard)
        assertFalse(Motion.forDurationScale(0.5f).isInstant)
    }
}
