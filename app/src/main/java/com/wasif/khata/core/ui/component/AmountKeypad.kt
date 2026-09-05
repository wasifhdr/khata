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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import com.wasif.khata.core.model.Money
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
    // The platform constant rather than a duration of our own: KEYBOARD_TAP is the
    // tick every other keyboard on the phone gives, and it goes silent on its own
    // when the owner has turned touch feedback off.
    val view = LocalView.current
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
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (key == BACKSPACE) onBackspace() else onKey(key)
                            }
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

/**
 * The keypad over whatever screen asked for it, so a form field and the widget's
 * sheet take an amount the same way. [value] is edited in place as the keys are
 * pressed -- the same live reporting a text field gave -- and [onConfirm] only
 * closes.
 */
@Composable
fun AmountKeypadDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    body: String? = null,
    confirmLabel: String = "Done",
    confirmEnabled: Boolean = true,
) {
    val spacing = LocalSpacing.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                body?.let { Text(it) }
                Text(
                    // Never blank: an empty readout looks like the keypad is not
                    // wired to anything.
                    text = "${Money.SYMBOL}${value.ifEmpty { "0" }}",
                    // Larger than the 14sp the style carries for a row in a list:
                    // here it is the thing being edited, not a column entry.
                    style = AmountTextStyle.copy(fontSize = 32.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AmountKeypad(
                    onKey = { onValueChange(appendAmountKey(value, it)) },
                    onBackspace = { onValueChange(value.dropLast(1)) },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = confirmEnabled, onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * An amount field that never opens the system keyboard. It reads as the same
 * outlined field the forms around it use, but the whole of it is one button onto
 * [AmountKeypadDialog] -- which is why the overlay is here: a read-only text field
 * still takes focus and shows a caret, and a caret with no keyboard behind it is
 * the field looking broken.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Amount",
    label: String? = null,
    placeholder: String = "\u09E6",
    isError: Boolean = false,
) {
    var editing by rememberSaveable { mutableStateOf(false) }

    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            prefix = { Text(Money.SYMBOL) },
            singleLine = true,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.small)
                .clickable { editing = true },
        )
    }

    if (editing) {
        AmountKeypadDialog(
            title = title,
            value = value,
            onValueChange = onValueChange,
            onDismiss = { editing = false },
            onConfirm = { editing = false },
        )
    }
}
