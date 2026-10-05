package com.wasif.khata.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icons Khata draws using Lucide vector geometry.
 *
 * Built on Lucide's 24x24 grid with 2dp rounded strokes (StrokeCap.Round, StrokeJoin.Round).
 * The black stroke/fill is the Material convention for an icon vector: [androidx.compose.material3.Icon]
 * tints it with LocalContentColor or the explicit tint parameter at the call site.
 */
object KhataIcons {

    private fun lucideIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.lucidePath(
        pathData: String,
        fill: Boolean = false,
    ): ImageVector.Builder {
        val nodes = PathParser().parsePathString(pathData).toNodes()
        return if (fill) {
            addPath(
                pathData = nodes,
                fill = SolidColor(Color.Black),
            )
        } else {
            addPath(
                pathData = nodes,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }

    /** A four-pointed sparkle: the conventional mark for "a model wrote this". */
    val Sparkle: ImageVector = lucideIcon("Sparkle") {
        lucidePath(
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
            fill = true,
        )
    }

    /** A fork and a knife: the Lucide utensils mark for "somewhere you ate". */
    val Restaurant: ImageVector = lucideIcon("Restaurant") {
        lucidePath("M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2")
        lucidePath("M7 2v20")
        lucidePath("M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7")
    }

    /** A clapperboard: film or episode without choosing between them. */
    val Watchlist: ImageVector = lucideIcon("Watchlist") {
        lucidePath("m12.296 3.464 3.02 3.956")
        lucidePath("M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z")
        lucidePath("M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
        lucidePath("m6.18 5.276 3.1 3.899")
    }

    /** A car from the side: sleek cabin, chassis line, and wheels. */
    val Car: ImageVector = lucideIcon("Car") {
        lucidePath("M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2")
        lucidePath("M 5 17 a 2 2 0 1 0 4 0 a 2 2 0 1 0 -4 0")
        lucidePath("M9 17h6")
        lucidePath("M 15 17 a 2 2 0 1 0 4 0 a 2 2 0 1 0 -4 0")
    }

    /** A ruled document with a folded corner: notes and writing. */
    val Notes: ImageVector = lucideIcon("Notes") {
        lucidePath("M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z")
        lucidePath("M14 2v5a1 1 0 0 0 1 1h5")
        lucidePath("M10 9H8")
        lucidePath("M16 13H8")
        lucidePath("M16 17H8")
    }

    /** A wallet: modern silhouette with card flap and clasp. */
    val Wallet: ImageVector = lucideIcon("Wallet") {
        lucidePath("M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1")
        lucidePath("M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4")
    }
}
