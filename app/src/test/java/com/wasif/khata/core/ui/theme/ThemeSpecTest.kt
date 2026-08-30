package com.wasif.khata.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSpecTest {

    @Test
    fun `the default is the one the design settled on`() {
        val d = ThemeSpec.Default
        assertEquals("Verdigris", d.field.name)
        assertEquals(KhataPalette.grounds.first().color, d.ground)
        assertEquals(KhataPalette.accents.first().color, d.accent)
        assertEquals(FieldIntensity.Full, d.intensity)
    }

    @Test
    fun `every axis value the tuner can produce is a valid spec`() {
        // The tuner is free-form, so every combination has to construct. This
        // is the 512-combination space the contrast suite proves pairwise.
        var built = 0
        KhataPalette.fields.forEach { f ->
            KhataPalette.grounds.forEach { g ->
                KhataPalette.accents.forEach { a ->
                    FieldIntensity.entries.forEach { i ->
                        ThemeSpec(field = f, ground = g.color, accent = a.color, intensity = i)
                        built++
                    }
                }
            }
        }
        assertEquals(512, built)
    }

    @Test
    fun `glass falls back to solid only when the field is off`() {
        val off = ThemeSpec.Default.copy(intensity = FieldIntensity.Off)
        assertTrue(off.usesSolidSurfaces)
        FieldIntensity.entries.filter { it != FieldIntensity.Off }.forEach { i ->
            assertTrue(!ThemeSpec.Default.copy(intensity = i).usesSolidSurfaces)
        }
    }
}
