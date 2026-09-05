package com.wasif.khata.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.ui.component.AmountKeypadDialog
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import dev.chrisbanes.haze.HazeState
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.CategoryDot
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun TransactionEditorScreen(
    onDone: () -> Unit,
    viewModel: TransactionEditorViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    TransactionEditorEffect.Saved, TransactionEditorEffect.Deleted -> onDone()
                }
            }
        }
    }

    TransactionEditorContent(state = state, actions = viewModel, onBack = onDone)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorContent(
    state: TransactionEditorUiState,
    actions: TransactionEditorActions,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var editingAmount by rememberSaveable { mutableStateOf(false) }

    if (editingAmount) {
        AmountKeypadDialog(
            title = "Amount",
            value = state.amountInput,
            onValueChange = actions::onAmountChange,
            onDismiss = { editingAmount = false },
            onConfirm = { editingAmount = false },
        )
    }

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NavCircle(Icons.Filled.Close, "Close", onClick = onBack)
                if (state.isEditing) {
                    NavCircle(
                        icon = Icons.Filled.Delete,
                        description = "Delete transaction",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = actions::onDelete,
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ContextHeader(
                    heading = if (state.isEditing) "Edit entry" else "New entry",
                    // The defaults the editor already assumed, stated where they can
                    // be corrected rather than left invisible.
                    subline = state.accounts.firstOrNull { it.id == state.accountId }?.name
                        ?: "No account",
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = spacing.lg),
            ) {
                // Amount first and largest. This is the single change that most
                // directly answers "everything is the same size".
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal)
                        .clip(MaterialTheme.shapes.large)
                        .background(Brush.linearGradient(KhataPalette.heroStops))
                        .padding(spacing.md),
                ) {
                    Text(
                        text = "AMOUNT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The keypad rather than the system keyboard, the same one the
                    // widget takes an amount with. The readout is the button: a
                    // caret here would promise an IME that never arrives.
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.xs)
                            .clickable { editingAmount = true }
                            .testTag("amountField"),
                    ) {
                        Text(
                            text = "৳",
                            style = MaterialTheme.typography.displayLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Text(
                            text = state.amountInput.ifEmpty { "0" },
                            style = MaterialTheme.typography.displayLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontFeatureSettings = "tnum",
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.amountHasError) {
                        // Durable text under the field, never a transient toast.
                        Text(
                            text = "Enter an amount like 1234.56",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                }

                // Two visible states, never a switch: a switch hides which state is
                // which.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    TransactionDirection.entries.forEach { direction ->
                        val selected = state.direction == direction
                        GlassChoice(
                            haze = haze,
                            selected = selected,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f).height(spacing.minTouchTarget),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable { actions.onDirectionChange(direction) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (direction == TransactionDirection.DEBIT) "Spent" else "Received",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }

                SectionLabel(top = spacing.md, text = "Account")
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    // Wrapped lines get no gap by default, so chips on the second
                    // row sit flush against the first row's.
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    state.accounts.forEach { account ->
                        EditorChip(
                            haze = haze,
                            label = account.name,
                            selected = state.accountId == account.id,
                            token = null,
                            onClick = { actions.onAccountSelected(account.id) },
                        )
                    }
                }

                SectionLabel(top = spacing.md, text = "Category")
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    // Wrapped lines get no gap by default, so chips on the second
                    // row sit flush against the first row's.
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    state.categories.forEach { category ->
                        EditorChip(
                            haze = haze,
                            label = category.name,
                            selected = state.categoryId == category.id,
                            token = category.colorToken,
                            onClick = { actions.onCategorySelected(category.id) },
                        )
                    }
                }

                // Only shown when there is more than one sensible answer, which
                // for a plain purchase there is not.
                SectionLabel(top = spacing.md, text = "What kind")
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    state.kindChoices.forEach { (kind, label) ->
                        Pill(
                            text = label,
                            selected = state.kind == kind,
                        ) { actions.onKindChange(kind) }
                    }
                }

                if (state.wantsCounterparty) {
                    SectionLabel(state.counterpartyLabel, top = spacing.md)
                    OutlinedTextField(
                        value = state.counterpartyInput,
                        onValueChange = actions::onCounterpartyChange,
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                    )
                }

                SectionLabel(top = spacing.md, text = "Merchant")
                OutlinedTextField(
                    value = state.merchantInput,
                    onValueChange = actions::onMerchantChange,
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                )

                SectionLabel(top = spacing.md, text = "Note")
                OutlinedTextField(
                    value = state.noteInput,
                    onValueChange = actions::onNoteChange,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                )

                // What the bank actually said, verbatim and read-only. "EBL Account
                // Transfer" alone cannot say whether the money went to another of your
                // accounts or to someone else, and this is where that is settled.
                // Absent entirely on a row typed by hand, which has no message.
                state.originalMessage?.let { message ->
                    SectionLabel(top = spacing.md, text = "Original message")
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                    )
                }

                state.saveError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(
                            horizontal = spacing.screenHorizontal,
                            vertical = spacing.sm,
                        ),
                    )
                }

                // Full-width, in the thumb arc, disabled until genuinely saveable.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(spacing.screenHorizontal)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (state.canSave) {
                                Brush.linearGradient(KhataPalette.heroStops)
                            } else {
                                // A flat fill, not a gradient with matching stops:
                                // the disabled state has no accent to blend.
                                SolidColor(MaterialTheme.colorScheme.surfaceContainer)
                            },
                        )
                        .clickable(enabled = state.canSave, onClick = actions::onSave)
                        .padding(vertical = spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Save entry",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.canSave) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }
            }
    }
}

@Composable
private fun EditorChip(
    haze: HazeState,
    label: String,
    selected: Boolean,
    token: String?,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    GlassChoice(haze = haze, selected = selected, shape = CircleShape) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // Colour quarantined to a dot; the name always carries the meaning.
            token?.let { CategoryDot(token = it) }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * One body, two surfaces. Only the *unselected* half is glass: a translucent
 * selected control reads as less committed than an opaque one, which inverts the
 * thing selection is meant to say.
 */
@Composable
private fun GlassChoice(
    haze: HazeState,
    selected: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (selected) {
        Box(modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer)) { content() }
    } else {
        KhataGlass(
            hazeState = haze,
            modifier = modifier,
            shape = shape,
            refracts = false,
            content = content,
        )
    }
}
