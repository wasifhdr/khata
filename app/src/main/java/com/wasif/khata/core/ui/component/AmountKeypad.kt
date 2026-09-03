package com.wasif.khata.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.LocalSpacing

internal const val BACKSPACE = '⌫'

private val KEY_ROWS = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
    listOf('.', '0', BACKSPACE),
)

/**
 * The amount field's whole grammar, as a function over the string rather than
 * state inside the composable -- these are the rules worth testing, and a Compose
 * runtime is not needed to test a string.
 *
 * [key] is '0'..'9' or '.'. Backspace is `dropLast(1)`, which is already correct
 * on an empty string.
 */
fun appendAmountKey(current: String, key: Char): String {
    if (key == '.') {
        if (current.contains('.')) return current
        return if (current.isEmpty()) "0." else "$current."
    }
    val dot = current.indexOf('.')
    // Money is Long paisa. A third decimal place has nowhere to be stored.
    if (dot >= 0 && current.length - dot > 2) return current
    // "0" then "5" is 5, not 05. Only a bare zero is replaced -- "10" then "0"
    // is 100.
    if (current == "0") return key.toString()
    return current + key
}

@Composable
fun AmountKeypad(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        KEY_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { if (key == BACKSPACE) onBackspace() else onKey(key) }
                            .semantics {
                                contentDescription =
                                    if (key == BACKSPACE) "Backspace" else key.toString()
                            }
                            .padding(vertical = spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key.toString(),
                            style = AmountTextStyle.copy(fontSize = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
