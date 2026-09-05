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

    /** A fork and a knife, the plainest mark for "somewhere you ate". */
    val Restaurant: ImageVector = ImageVector.Builder(
        name = "Restaurant",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Fork: three tines over a stem.
            moveTo(6f, 3f); lineTo(7.4f, 3f); lineTo(7.4f, 8f); lineTo(8.6f, 8f)
            lineTo(8.6f, 3f); lineTo(10f, 3f); lineTo(10f, 9.5f)
            curveTo(10f, 10.6f, 9.3f, 11.4f, 8.7f, 11.6f)
            lineTo(8.7f, 21f); lineTo(7.3f, 21f); lineTo(7.3f, 11.6f)
            curveTo(6.7f, 11.4f, 6f, 10.6f, 6f, 9.5f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            // Knife: a blade tapering into the same stem line.
            moveTo(16.5f, 3f)
            curveTo(18f, 3f, 18.6f, 5.5f, 18.6f, 8f)
            curveTo(18.6f, 10.5f, 18f, 12f, 17.2f, 12.3f)
            lineTo(17.2f, 21f); lineTo(15.8f, 21f); lineTo(15.8f, 12.3f)
            curveTo(15f, 12f, 14.4f, 10.5f, 14.4f, 8f)
            curveTo(14.4f, 5.5f, 15f, 3f, 16.5f, 3f)
            close()
        }
    }.build()

    /** A play triangle inside a frame: a film or an episode, without choosing between them. */
    val Watchlist: ImageVector = ImageVector.Builder(
        name = "Watchlist",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Frame, drawn as an outline so the triangle inside reads as separate.
            moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f)
            close()
            moveTo(5.6f, 5.6f); lineTo(5.6f, 18.4f); lineTo(18.4f, 18.4f)
            lineTo(18.4f, 5.6f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 8.5f); lineTo(16f, 12f); lineTo(10f, 15.5f)
            close()
        }
    }.build()

    /** A car from the side: cabin, body, two wheels. */
    val Car: ImageVector = ImageVector.Builder(
        name = "Car",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 11f)
            lineTo(6.6f, 6.8f)
            curveTo(6.8f, 6.3f, 7.2f, 6f, 7.7f, 6f)
            lineTo(16.3f, 6f)
            curveTo(16.8f, 6f, 17.2f, 6.3f, 17.4f, 6.8f)
            lineTo(19f, 11f)
            lineTo(20f, 11.6f)
            lineTo(20f, 16f)
            lineTo(4f, 16f)
            lineTo(4f, 11.6f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            // Wheels, sitting below the body so the shape reads at 20dp.
            moveTo(7.5f, 15f)
            curveTo(8.6f, 15f, 9.5f, 15.9f, 9.5f, 17f)
            curveTo(9.5f, 18.1f, 8.6f, 19f, 7.5f, 19f)
            curveTo(6.4f, 19f, 5.5f, 18.1f, 5.5f, 17f)
            curveTo(5.5f, 15.9f, 6.4f, 15f, 7.5f, 15f)
            close()
            moveTo(16.5f, 15f)
            curveTo(17.6f, 15f, 18.5f, 15.9f, 18.5f, 17f)
            curveTo(18.5f, 18.1f, 17.6f, 19f, 16.5f, 19f)
            curveTo(15.4f, 19f, 14.5f, 18.1f, 14.5f, 17f)
            curveTo(14.5f, 15.9f, 15.4f, 15f, 16.5f, 15f)
            close()
        }
    }.build()

    /** A page with lines on it -- the same ruled paper the notes module is written on. */
    val Notes: ImageVector = ImageVector.Builder(
        name = "Notes",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); lineTo(5f, 21f)
            close()
            moveTo(6.6f, 4.6f); lineTo(6.6f, 19.4f); lineTo(17.4f, 19.4f)
            lineTo(17.4f, 4.6f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            // Three rules, the shortest last, so it reads as writing rather than a table.
            moveTo(8.4f, 8f); lineTo(15.6f, 8f); lineTo(15.6f, 9.2f); lineTo(8.4f, 9.2f)
            close()
            moveTo(8.4f, 11.4f); lineTo(15.6f, 11.4f); lineTo(15.6f, 12.6f); lineTo(8.4f, 12.6f)
            close()
            moveTo(8.4f, 14.8f); lineTo(12.6f, 14.8f); lineTo(12.6f, 16f); lineTo(8.4f, 16f)
            close()
        }
    }.build()
}
