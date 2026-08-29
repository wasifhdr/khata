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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.wasif.khata.core.model.TransactionDirection
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(spacing.minTouchTarget).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (state.isEditing) {
                Box(
                    Modifier
                        .size(spacing.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = actions::onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete transaction",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
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
                BasicTextField(
                    value = state.amountInput,
                    onValueChange = actions::onAmountChange,
                    textStyle = MaterialTheme.typography.displayLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontFeatureSettings = "tnum",
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs)
                        .testTag("amountField"),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "৳",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Box(Modifier.weight(1f)) { inner() }
                            // Semantics-only: the symbol and the typed digits are
                            // two separate nodes for layout, but assistive tech
                            // (and anything asserting on displayed text) should
                            // still be able to find the amount as one string.
                            Text(
                                text = "৳${state.amountInput}",
                                style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.size(0.dp),
                            )
                        }
                    },
                )
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
                    Box(
                        Modifier
                            .weight(1f)
                            .height(spacing.minTouchTarget)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            )
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

            FieldLabel("Account")
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                state.accounts.forEach { account ->
                    EditorChip(
                        label = account.name,
                        selected = state.accountId == account.id,
                        token = null,
                        onClick = { actions.onAccountSelected(account.id) },
                    )
                }
            }

            FieldLabel("Category")
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                state.categories.forEach { category ->
                    EditorChip(
                        label = category.name,
                        selected = state.categoryId == category.id,
                        token = category.colorToken,
                        onClick = { actions.onCategorySelected(category.id) },
                    )
                }
            }

            FieldLabel("Merchant")
            OutlinedTextField(
                value = state.merchantInput,
                onValueChange = actions::onMerchantChange,
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
            )

            FieldLabel("Note")
            OutlinedTextField(
                value = state.noteInput,
                onValueChange = actions::onNoteChange,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
            )

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
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            )
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

@Composable
private fun FieldLabel(text: String) {
    val spacing = LocalSpacing.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(
            start = spacing.screenHorizontal,
            end = spacing.screenHorizontal,
            top = spacing.md,
            bottom = spacing.sm,
        ),
    )
}

@Composable
private fun EditorChip(
    label: String,
    selected: Boolean,
    token: String?,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
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
