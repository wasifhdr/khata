package com.wasif.khata.feature.ruleeditor

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.sms.FieldKind
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun RuleEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RuleEditorViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RuleEditorContent(
        state = state,
        onBack = onBack,
        onTokenTapped = viewModel::onTokenTapped,
        onFieldChosen = viewModel::onFieldChosen,
        onSelectionCleared = viewModel::onSelectionCleared,
        onDirectionChanged = viewModel::onDirectionChanged,
        onKindChanged = viewModel::onKindChanged,
        onNameChanged = viewModel::onNameChanged,
        onSave = viewModel::onSave,
        onDone = onSaved,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditorContent(
    state: RuleEditorUiState,
    onBack: () -> Unit,
    onTokenTapped: (Int) -> Unit,
    onFieldChosen: (FieldKind) -> Unit,
    onSelectionCleared: () -> Unit,
    onDirectionChanged: (TransactionDirection) -> Unit,
    onKindChanged: (RuleKind) -> Unit,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
) {
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
                Box(
                    Modifier
                        .size(spacing.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = spacing.xxl),
            ) {
                ContextHeader(
                    heading = "Teach a rule",
                    subline = state.sender,
                )

                Instruction(state)

                MessageTokens(state = state, onTokenTapped = onTokenTapped)

                if (state.awaitingLabel) {
                    FieldChoices(onFieldChosen = onFieldChosen, onCancel = onSelectionCleared)
                }

                if (state.derived.captures.isNotEmpty()) {
                    Captures(state)
                }

                DirectionChoice(state.direction, onDirectionChanged)
                KindChoice(state.kind, onKindChanged)

                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChanged,
                    label = { Text("Name this rule") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
                )

                state.reparseSummary?.let { summary ->
                    Saved(summary = summary, onDone = onDone)
                }

                state.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                    )
                }

                if (state.reparseSummary == null) {
                    SaveButton(enabled = state.canSave, isSaving = state.isSaving, onSave = onSave)
                }
            }
        }
    }
}

@Composable
private fun Instruction(state: RuleEditorUiState) {
    val spacing = LocalSpacing.current
    val text = when {
        state.awaitingLabel -> "Now say what that is."
        state.derived.error != null -> state.derived.error!!
        else -> "Tap more words to teach Khata the rest, or save."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageTokens(state: RuleEditorUiState, onTokenTapped: (Int) -> Unit) {
    val spacing = LocalSpacing.current
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        state.tokens.forEachIndexed { index, token ->
            val labelled = state.kindOfToken(index)
            val pending = state.pending?.contains(index) == true
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        when {
                            labelled != null -> MaterialTheme.colorScheme.secondaryContainer
                            pending -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        },
                    )
                    .clickable { onTokenTapped(index) }
                    .padding(horizontal = spacing.xs, vertical = spacing.xs)
                    // semantics, not clearAndSetSemantics: clearing would replace the
                    // word's own text, so the message would be unreadable to a screen
                    // reader and unfindable by text.
                    .semantics {
                        // Colour is never the only signal: the label is spoken too.
                        labelled?.let { contentDescription = "${token.text}, ${it.label}" }
                    },
            ) {
                Text(
                    text = token.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (labelled != null || pending) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldChoices(onFieldChosen: (FieldKind) -> Unit, onCancel: () -> Unit) {
    val spacing = LocalSpacing.current
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        FieldKind.entries.forEach { kind ->
            Pill(text = kind.label, selected = false) { onFieldChosen(kind) }
        }
        Pill(text = "Cancel", selected = false, onClick = onCancel)
    }
}

@Composable
private fun Captures(state: RuleEditorUiState) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal)) {
        Text(
            text = "Khata reads",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FieldKind.entries.forEach { kind ->
            state.derived.captures[kind.groupName]?.let { value ->
                Text(
                    text = "${kind.label}: $value",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionChoice(
    selected: TransactionDirection,
    onChanged: (TransactionDirection) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.md)) {
        Text(
            text = "Money",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TransactionDirection.entries.forEach { direction ->
                Pill(
                    text = if (direction == TransactionDirection.DEBIT) "Out" else "In",
                    selected = direction == selected,
                ) { onChanged(direction) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindChoice(selected: RuleKind, onChanged: (RuleKind) -> Unit) {
    val spacing = LocalSpacing.current
    // IGNORE is deliberately absent: this flow teaches Khata to read a message, not
    // to discard one, and an ignore rule written here could not outrank the built-ins
    // anyway.
    val offered = listOf(
        RuleKind.NORMAL to "Spending",
        RuleKind.TRANSFER_OUT to "Transfer out",
        RuleKind.TRANSFER_IN to "Transfer in",
        RuleKind.ATM_WITHDRAWAL to "Cash out",
        RuleKind.FEE to "Fee",
    )
    Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
        Text(
            text = "Kind",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            offered.forEach { (kind, label) ->
                Pill(text = label, selected = kind == selected) { onChanged(kind) }
            }
        }
    }
}

@Composable
private fun Saved(summary: String, onDone: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth().padding(spacing.screenHorizontal)) {
        Text(
            text = "Rule saved",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "History re-read · $summary",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs),
        )
        Box(
            Modifier
                .padding(top = spacing.md)
                .fillMaxWidth()
                .height(spacing.minTouchTarget)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun SaveButton(enabled: Boolean, isSaving: Boolean, onSave: () -> Unit) {
    val spacing = LocalSpacing.current
    Box(
        Modifier
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md)
            .fillMaxWidth()
            .height(spacing.minTouchTarget)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(enabled = enabled, onClick = onSave),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isSaving) "Saving…" else "Save and re-read history",
            style = MaterialTheme.typography.labelLarge,
            // outline is borders and disabled controls only, never live text, so a
            // disabled label stays on a text tier.
            color = if (enabled) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
