package com.wasif.khata.feature.widget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import com.wasif.khata.core.ui.component.AmountKeypad
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.LocalMotion
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.PageSublineStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickEntryScreen(
    state: QuickEntryUiState,
    actions: QuickEntryActions,
    saved: Boolean,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val motion = LocalMotion.current
    var noteFocused by remember { mutableStateOf(false) }
    val visible = remember { MutableTransitionState(false) }
    var closing by remember { mutableStateOf(false) }

    // The window carries no activity transition, so the sheet owns its own enter
    // and exit. onClosed waits for the exit to idle, or the window vanishes
    // mid-slide. `closing` gates that last effect: without it the initial state is
    // already idle and invisible, and the sheet would close itself on frame one.
    LaunchedEffect(Unit) { visible.targetState = true }
    LaunchedEffect(saved) { if (saved) closing = true }
    LaunchedEffect(closing) { if (closing) visible.targetState = false }
    LaunchedEffect(closing, visible.isIdle) {
        if (closing && visible.isIdle && !visible.currentState) onClosed()
    }

    // Back takes the same path as the scrim rather than finish()ing straight out,
    // or the two ways of dismissing one sheet look different.
    BackHandler { closing = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { closing = true },
            ),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(motion.quick, easing = motion.enter)),
            exit = fadeOut(tween(motion.quick, easing = motion.exit)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
            )
        }

        AnimatedVisibility(
            visibleState = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(motion.standard, easing = motion.enter)) { it },
            exit = slideOutVertically(tween(motion.standard, easing = motion.exit)) { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    // Consumes the tap so a press inside the sheet does not dismiss it.
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .padding(spacing.md)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    text = state.heading,
                    style = PageSublineStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = state.amountInput.ifEmpty { "0" },
                    style = AmountTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                state.saveError?.let { error ->
                    Text(
                        text = error,
                        style = PageSublineStyle,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    // Without this the wrapped rows sit flush and the chips touch.
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    state.categories.forEach { category ->
                        Pill(
                            text = category.name,
                            selected = state.categoryId == category.id,
                            onClick = { actions.onCategorySelected(category.id) },
                        )
                    }
                }

                OutlinedTextField(
                    value = state.noteInput,
                    onValueChange = actions::onNoteChange,
                    label = { Text("Note") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { noteFocused = it.isFocused },
                )

                Pill(
                    text = "Save",
                    selected = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSave,
                    onClick = actions::onSave,
                )

                // The lower half belongs to whichever field has focus. The keypad
                // withdraws for the IME rather than stacking above it.
                if (!noteFocused) {
                    AmountKeypad(
                        onKey = actions::onAmountKey,
                        onBackspace = actions::onBackspace,
                    )
                }
            }
        }
    }
}
