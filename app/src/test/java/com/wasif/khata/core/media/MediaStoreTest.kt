package com.wasif.khata.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreTest {

    @Test
    fun `the same bytes hash the same, and different bytes do not`() {
        assertEquals(sha256(byteArrayOf(1, 2, 3)), sha256(byteArrayOf(1, 2, 3)))
        assertTrue(sha256(byteArrayOf(1)) != sha256(byteArrayOf(2)))
        // 64 hex characters, because it names a file.
        assertEquals(64, sha256(byteArrayOf(1)).length)
    }

    @Test
    fun `a small JPEG is copied rather than re-encoded`() {
        // Re-encoding an already-small JPEG spends quality to save nothing.
        assertFalse(needsDownscale("image/jpeg", 1600))
        assertFalse(needsDownscale("image/jpeg", 2048))
    }

    @Test
    fun `a large JPEG or any other format is re-encoded`() {
        assertTrue(needsDownscale("image/jpeg", 4032))
        // PNG at any size: a screenshot of a menu is megabytes as PNG and small as JPEG.
        assertTrue(needsDownscale("image/png", 800))
        assertTrue(needsDownscale("image/heic", 1000))
    }
}
