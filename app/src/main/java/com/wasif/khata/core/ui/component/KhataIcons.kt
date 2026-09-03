package com.wasif.khata.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons Khata draws itself.
 *
 * The alternative is material-icons-extended, which ships several thousand vectors so
 * that one of them can be a sparkle. Not worth a dependency that size.
 *
 * The black fill is the Material convention for an icon vector: [androidx.compose.material3.Icon]
 * tints it at the call site, so nothing here decides a colour.
 */
object KhataIcons {

    /** A four-pointed sparkle: the conventional mark for "a model wrote this". */
    val Sparkle: ImageVector = ImageVector.Builder(
        name = "Sparkle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Concave sides, so it reads as a sparkle rather than a diamond.
            moveTo(12f, 2f)
            curveTo(12.6f, 7.4f, 16.6f, 11.4f, 22f, 12f)
            curveTo(16.6f, 12.6f, 12.6f, 16.6f, 12f, 22f)
            curveTo(11.4f, 16.6f, 7.4f, 12.6f, 2f, 12f)
            curveTo(7.4f, 11.4f, 11.4f, 7.4f, 12f, 2f)
            close()
        }
    }.build()
}
