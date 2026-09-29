package com.wasif.khata.feature.owed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.time.DHAKA
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@Composable
fun OwedScreen(
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onAddTransaction: () -> Unit = {},
    viewModel: OwedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OwedContent(
        state = state,
        onBack = onBack,
        onOpenTransaction = onOpenTransaction,
        onAddTransaction = onAddTransaction,
        onSelectPerson = viewModel::onSelectPerson,
        onSelectSettleAccount = viewModel::onSelectSettleAccount,
        onSettleSelectedPerson = viewModel::onSettleSelectedPerson,
    )
}

@Composable
fun OwedContent(
    state: OwedUiState,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onAddTransaction: () -> Unit = {},
    onSelectPerson: (String?) -> Unit = {},
    onSelectSettleAccount: (Long) -> Unit = {},
    onSettleSelectedPerson: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    val scroll = rememberScrollState()

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {

            // A Haze source, padded down by the header's height: the page passes
            // blurred under the bar rather than stopping at it.
            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    .imePadding()
                    .verticalScroll(scroll)
                    .padding(top = CollapsingHeaderHeight, bottom = spacing.xxl + spacing.xl)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {

                if (state.owesYou.isNotEmpty()) {
                    SectionLabel("Owes you")
                    state.owesYou.forEach { person ->
                        PersonRow(person = person, onClick = { onSelectPerson(person.name) })
                    }
                }

                if (state.youOwe.isNotEmpty()) {
                    SectionLabel("You owe")
                    state.youOwe.forEach { person ->
                        PersonRow(person = person, onClick = { onSelectPerson(person.name) })
                    }
                }

                // Naming is what turns a pile of loans into a ledger, so the ones
                // still missing a name are listed rather than quietly netted away.
                if (state.unnamed.isNotEmpty()) {
                    SectionLabel("Not named yet")
                    Text(
                        text = "Tap one to say who it was with.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    )
                    state.unnamed.forEach { debt ->
                        UnnamedRow(debt) { onOpenTransaction(debt.transactionId) }
                    }
                }

                if (state.isLoaded && state.isSettled) {
                    Text(
                        text = "Nothing is outstanding in either direction. Money you lend or " +
                            "borrow shows up here once you record it as such.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = spacing.screenHorizontal,
                            vertical = spacing.md,
                        ),
                    )
                }
            }

            CollapsingTopBar(
                heading = "Owed",
                subline = when {
                    !state.isLoaded -> "Counting"
                    state.isSettled -> "You are square with everyone"
                    else -> "${state.owedToYou.format()} out · ${state.owedByYou.format()} in"
                },
                collapse = scroll.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(spacing.screenHorizontal)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(KhataPalette.heroStops))
                    .clickable(onClick = onAddTransaction),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add entry",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    state.selectedPerson?.let { detail ->
        PersonOwedSheet(
            detail = detail,
            onDismiss = { onSelectPerson(null) },
            onOpenTransaction = { id ->
                onSelectPerson(null)
                onOpenTransaction(id)
            },
            onSelectAccount = onSelectSettleAccount,
            onSettle = onSettleSelectedPerson,
        )
    }
}

@Composable
private fun PersonRow(
    person: Person,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val direction = if (person.owesYou) "owes you" else "you owe"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm)
            // One sentence rather than three fragments read in a row.
            .clearAndSetSemantics {
                contentDescription = "${person.name} $direction ${person.amount.format()}, " +
                    "across ${person.entries} entries"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = spacing.md)) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                // Which way round is a word, never only a colour.
                text = "$direction · ${person.entries} ${if (person.entries == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = person.amount.format(),
            style = AmountTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PersonOwedSheet(
    detail: PersonOwedDetail,
    onDismiss: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSelectAccount: (Long) -> Unit,
    onSettle: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val directionText = if (detail.person.owesYou) {
        "owes you ${detail.person.amount.format()}"
    } else {
        "you owe ${detail.person.amount.format()}"
    }
    val entryWord = if (detail.person.entries == 1) "entry" else "entries"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenHorizontal)
                .padding(bottom = spacing.xl),
        ) {
            Text(
                text = detail.person.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$directionText · ${detail.person.entries} $entryWord",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs),
            )

            SectionLabel(top = spacing.md, text = "Entries")
            detail.entries.forEach { entry ->
                val on = Instant.ofEpochMilli(entry.occurredAt)
                    .atZone(DHAKA)
                    .toLocalDate()
                    .format(dateFormatter)
                val sign = if (entry.increasesWhatTheyOwe) "+" else "−"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = spacing.minTouchTarget)
                        .clickable { onOpenTransaction(entry.id) }
                        .padding(vertical = spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = spacing.md)) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = on,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "$sign${entry.owedAmount.format()}",
                        style = AmountTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (detail.accounts.isNotEmpty()) {
                SectionLabel(
                    top = spacing.md,
                    text = if (detail.person.owesYou) "Receive into" else "Pay from",
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    detail.accounts.forEach { account ->
                        EditorChip(
                            label = account.name,
                            selected = detail.selectedAccountId == account.id,
                            onClick = { onSelectAccount(account.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.md))

            val accountName = detail.selectedAccount?.name
            val settleText = if (detail.person.owesYou) {
                if (accountName != null) {
                    "Receive ${detail.person.amount.format()} into $accountName"
                } else {
                    "Receive ${detail.person.amount.format()}"
                }
            } else {
                if (accountName != null) {
                    "Pay ${detail.person.amount.format()} from $accountName"
                } else {
                    "Pay ${detail.person.amount.format()}"
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Brush.linearGradient(KhataPalette.heroStops))
                    .clickable(
                        enabled = detail.selectedAccountId != null,
                        onClick = onSettle,
                    )
                    .padding(vertical = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = settleText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EditorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
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

@Composable
private fun UnnamedRow(debt: UnnamedDebt, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val on = Instant.ofEpochMilli(debt.occurredAt).atZone(DHAKA).toLocalDate().format(dateFormatter)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm)
            .clearAndSetSemantics {
                contentDescription = "${debt.describes}, ${debt.amount.format()} on $on. Tap to name it."
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = spacing.md)) {
            Text(
                text = debt.describes,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = on,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = debt.amount.format(),
            style = AmountTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
