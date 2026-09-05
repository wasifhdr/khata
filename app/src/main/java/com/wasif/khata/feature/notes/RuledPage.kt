package com.wasif.khata.feature.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The page the note is written on.
 *
 * The rules are drawn *behind this content*, which scrolls with it -- painted on the viewport
 * they would sit still while the text moved past them, and the alignment would only be right at
 * the top of the page. That is exactly what makes fake paper look fake.
 *
 * [ruleSpacing] is derived from the body line height by the caller rather than fixed here: at a
 * 1.3x font scale a hardcoded spacing leaves every line floating between rules instead of
 * resting on one.
 */
@Composable
fun RuledPage(
    ruleSpacing: Dp,
    ruleColor: Color,
    marginColor: Color,
    marginX: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (ruleSpacing <= 0.dp) return@drawBehind

                val spacing = ruleSpacing.toPx()
                val stroke = 1.dp.toPx()

                var y = spacing
                while (y < size.height) {
                    drawLine(
                        color = ruleColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                    y += spacing
                }

                // The vertical line a notepad has, which is also the gutter bullets, numbers
                // and checkboxes sit in -- so it earns its place twice.
                val x = marginX.toPx()
                drawLine(
                    color = marginColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = stroke,
                )
            },
        content = content,
    )
}
