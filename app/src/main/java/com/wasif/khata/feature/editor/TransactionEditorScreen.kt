package com.wasif.khata.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.wasif.khata.core.model.TransactionDirection
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorContent(
    state: TransactionEditorUiState,
    actions: TransactionEditorActions,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit transaction" else "New transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = actions::onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete transaction")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(spacing.screenHorizontal),
        ) {
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = actions::onAmountChange,
                label = { Text("Amount") },
                isError = state.amountHasError,
                supportingText = if (state.amountHasError) {
                    { Text("Enter an amount like 1234.56") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ChipSection(label = "Direction", topPadding = spacing.md) {
                TransactionDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = state.direction == direction,
                        onClick = { actions.onDirectionChange(direction) },
                        label = {
                            Text(if (direction == TransactionDirection.DEBIT) "Spent" else "Received")
                        },
                        modifier = Modifier.padding(end = spacing.sm),
                    )
                }
            }

            ChipSection(label = "Account", topPadding = spacing.md) {
                state.accounts.forEach { account ->
                    FilterChip(
                        selected = state.accountId == account.id,
                        onClick = { actions.onAccountSelected(account.id) },
                        label = { Text(account.name) },
                        modifier = Modifier.padding(end = spacing.sm),
                    )
                }
            }

            ChipSection(label = "Category", topPadding = spacing.md) {
                state.categories.forEach { category ->
                    FilterChip(
                        selected = state.categoryId == category.id,
                        onClick = { actions.onCategorySelected(category.id) },
                        label = { Text(category.name) },
                        modifier = Modifier.padding(end = spacing.sm),
                    )
                }
            }

            OutlinedTextField(
                value = state.merchantInput,
                onValueChange = actions::onMerchantChange,
                label = { Text("Merchant") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
            )

            OutlinedTextField(
                value = state.noteInput,
                onValueChange = actions::onNoteChange,
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
            )

            state.saveError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = spacing.md),
                )
            }

            Button(
                onClick = actions::onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.lg, bottom = spacing.xl),
            ) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSection(
    label: String,
    topPadding: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val spacing = LocalSpacing.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = topPadding, bottom = spacing.xs),
    )
    FlowRow(modifier = Modifier.fillMaxWidth()) { content() }
}
