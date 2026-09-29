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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.wasif.khata.core.ui.component.AmountKeypadDialog
import com.wasif.khata.core.ui.component.CategoryDot
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.HazeState

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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorContent(
    state: TransactionEditorUiState,
    actions: TransactionEditorActions,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var editingAmount by rememberSaveable { mutableStateOf(false) }
    var editingSplitShare by rememberSaveable { mutableStateOf(false) }

    if (editingAmount) {
        AmountKeypadDialog(
            title = "Amount",
            value = state.amountInput,
            onValueChange = actions::onAmountChange,
            onDismiss = { editingAmount = false },
            onConfirm = { editingAmount = false },
        )
    }

    if (editingSplitShare) {
        AmountKeypadDialog(
            title = "Their share",
            value = state.customOwedInput.orEmpty(),
            onValueChange = actions::onCustomOwedChange,
            onDismiss = { editingSplitShare = false },
            onConfirm = { editingSplitShare = false },
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

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = spacing.lg),
            ) {
                ContextHeader(
                    heading = if (state.isEditing) "Edit entry" else "New entry",
                    subline = if (state.isIou) {
                        "Owed only · no account moved"
                    } else {
                        state.accounts.firstOrNull { it.id == state.accountId }?.name ?: "No account"
                    },
                    modifier = Modifier.padding(bottom = spacing.md),
                )

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
                        Text(
                            text = "Enter an amount like 1234.56",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    state.availableModes.forEach { mode ->
                        val selected = state.mode == mode
                        GlassChoice(
                            haze = haze,
                            selected = selected,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f).height(spacing.minTouchTarget),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable { actions.onModeChange(mode) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = mode.label,
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

                if (state.wantsAccount) {
                    SectionLabel(top = spacing.md, text = "Account")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
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
                }

                var categoryExpanded by rememberSaveable { mutableStateOf(false) }
                val selectedCategory = state.categories.firstOrNull { it.id == state.categoryId }
                SectionLabel(top = spacing.md, text = "Category")
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        placeholder = { Text("Select category") },
                        leadingIcon = selectedCategory?.colorToken?.let { token ->
                            { CategoryDot(token = token) }
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("categoryDropdown"),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        state.categories.forEach { category ->
                            DropdownMenuItem(
                                leadingIcon = { CategoryDot(token = category.colorToken) },
                                text = { Text(category.name) },
                                onClick = {
                                    actions.onCategorySelected(category.id)
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }

                SectionLabel(top = spacing.md, text = "With someone")
                if (state.recentPeople.isNotEmpty()) {
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal)
                            .padding(bottom = spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        state.recentPeople.forEach { person ->
                            val selected = state.counterpartyInput.trim().equals(person, ignoreCase = true)
                            EditorChip(
                                haze = haze,
                                label = person,
                                selected = selected,
                                token = null,
                                onClick = {
                                    actions.onCounterpartyChange(if (selected) "" else person)
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.counterpartyInput,
                    onValueChange = actions::onCounterpartyChange,
                    placeholder = { Text(if (state.isIou) "Required" else "Optional") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                )

                if (state.mode == EditorMode.SPENT && state.counterpartyInput.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal)
                            .padding(top = spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        GlassChoice(
                            haze = haze,
                            selected = !state.isSplit,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f).height(spacing.minTouchTarget),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable(onClick = actions::onSelectAllOwed),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "All of it",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (!state.isSplit) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                        GlassChoice(
                            haze = haze,
                            selected = state.isSplit,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f).height(spacing.minTouchTarget),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        actions.onSelectSplitOwed()
                                        editingSplitShare = true
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Split · ${state.owedAmount.format()}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (state.isSplit) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }

                if (state.wantsMerchant) {
                    SectionLabel(top = spacing.md, text = "Merchant")
                    OutlinedTextField(
                        value = state.merchantInput,
                        onValueChange = actions::onMerchantChange,
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                    )
                }

                SectionLabel(top = spacing.md, text = "Note")
                OutlinedTextField(
                    value = state.noteInput,
                    onValueChange = actions::onNoteChange,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                )

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

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(spacing.screenHorizontal)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (state.canSave) {
                                Brush.linearGradient(KhataPalette.heroStops)
                            } else {
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
