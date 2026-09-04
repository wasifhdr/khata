package com.wasif.khata.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Five taps, drawn rather than imported: material-icons-extended is several thousand
 * vectors to get one star, the same trade [KhataIcons] already declined.
 *
 * The targets are deliberately large. A rating is the one control on the visit editor
 * that is used every single time, and the one-handed requirement rules out a row of
 * small marks -- so a star here is a full touch target with the mark drawn inside it,
 * not a 16dp glyph with a tap area wished onto it.
 *
 * Tapping the star already selected clears the rating: a visit you did not rate is
 * still a visit, and there has to be a way back to that from a mis-tap.
 */
@Composable
fun StarRating(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Rating",
    target: Dp = 48.dp,
    star: Dp = 28.dp,
) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outline

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        (1..5).forEach { position ->
            val isFilled = value != null && position <= value
            Box(
                Modifier
                    .size(target)
                    .selectable(
                        selected = isFilled,
                        onClick = { onValueChange(if (value == position) null else position) },
                    )
                    .semantics { contentDescription = "$label $position of 5" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(star)) {
                    drawStar(if (isFilled) filled else empty, isFilled)
                }
            }
        }
    }
}

/** The same mark, unfilled and untappable, for the places a rating is only reported. */
@Composable
fun StarRow(value: Double?, modifier: Modifier = Modifier, star: Dp = 14.dp) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outline
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { position ->
            // Rounded to the nearest whole star: half a star drawn at 14dp is a
            // smudge, and the numeric average is printed beside this anyway.
            val isFilled = value != null && position <= Math.round(value)
            Box(Modifier.size(star)) {
                Canvas(Modifier.size(star)) { drawStar(if (isFilled) filled else empty, isFilled) }
            }
        }
    }
}

/**
 * An outline when empty and a fill when set, so the two states differ in more than
 * colour -- the same reason selection is carried by fill on [Pill].
 */
private fun DrawScope.drawStar(color: Color, fill: Boolean) {
    val radius = size.minDimension / 2f
    val inner = radius * 0.45f
    val centre = Offset(size.width / 2f, size.height / 2f)
    val path = Path()
    // Ten points, alternating outer and inner, starting at twelve o'clock.
    (0 until 10).forEach { i ->
        val r = if (i % 2 == 0) radius else inner
        val angle = -PI / 2 + i * PI / 5
        val point = Offset(
            centre.x + (r * cos(angle)).toFloat(),
            centre.y + (r * sin(angle)).toFloat(),
        )
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    if (fill) {
        drawPath(path, color)
    } else {
        drawPath(path, color, style = Stroke(width = radius * 0.16f))
    }
}
