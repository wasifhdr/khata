package com.wasif.khata.core.ui.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KhataIconsTest {

    @Test
    fun `all KhataIcons build with standard 24dp viewport and non-empty paths`() {
        val icons = listOf(
            "Sparkle" to KhataIcons.Sparkle,
            "Restaurant" to KhataIcons.Restaurant,
            "Watchlist" to KhataIcons.Watchlist,
            "Car" to KhataIcons.Car,
            "Notes" to KhataIcons.Notes,
            "Wallet" to KhataIcons.Wallet,
        )

        for ((name, icon) in icons) {
            assertNotNull("Icon $name should not be null", icon)
            assertEquals("Icon $name should have 24dp width", 24.dp, icon.defaultWidth)
            assertEquals("Icon $name should have 24dp height", 24.dp, icon.defaultHeight)
            assertEquals("Icon $name should have 24f viewportWidth", 24f, icon.viewportWidth)
            assertEquals("Icon $name should have 24f viewportHeight", 24f, icon.viewportHeight)
            assertTrue("Icon $name should contain path nodes", icon.root.iterator().hasNext())
        }
    }
}
