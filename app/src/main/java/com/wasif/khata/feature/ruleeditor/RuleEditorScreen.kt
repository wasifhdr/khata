package com.wasif.khata.feature.ruleeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.sms.FieldKind
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.hazeSource

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
    val scroll = rememberScrollState()

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {

            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    .imePadding()
                    .verticalScroll(scroll)
                    .padding(top = CollapsingHeaderHeight, bottom = spacing.xxl)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {

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
                    SaveButton(enabled = state.canSave, label = state.saveLabel, onSave = onSave)
                }
            }

            CollapsingTopBar(
                heading = "Teach a rule",
                subline = state.sender,
                collapse = scroll.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )
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
        // FlowRow defaults to Arrangement.Top between wrapped lines, which is no
        // gap at all. Every child here paints its own background, so without this
        // the second line's chips sit flush against the first line's and the
        // message reads as one solid block rather than separate words.
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
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
                    // These are tap targets, not running text. 4dp all round left
                    // them smaller than a fingertip and hard to aim at in a
                    // three-line message.
                    .padding(horizontal = spacing.sm, vertical = spacing.sm)
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
                    // The same size the message is read at everywhere else; being
                    // tappable is no reason for it to be smaller.
                    style = MaterialTheme.typography.bodyLarge,
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
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal)
            .padding(top = spacing.md),
    ) {
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
        FlowRow(
            // The pills paint a background, so without this the label's descenders
            // sit on their top edge.
            Modifier.padding(top = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
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
    Column(Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.md)) {
        Text(
            text = "Kind",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.padding(top = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
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
        Pill(
            text = "Done",
            selected = true,
            modifier = Modifier.padding(top = spacing.md).fillMaxWidth(),
            onClick = onDone,
        )
    }
}

@Composable
private fun SaveButton(enabled: Boolean, label: String, onSave: () -> Unit) {
    val spacing = LocalSpacing.current
    Pill(
        text = label,
        selected = true,
        enabled = enabled,
        modifier = Modifier
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md)
            .fillMaxWidth(),
        onClick = onSave,
    )
}
