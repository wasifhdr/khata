package com.wasif.khata.feature.owed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.time.DHAKA
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.LocalSpacing
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@Composable
fun OwedScreen(
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: OwedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OwedContent(state = state, onBack = onBack, onOpenTransaction = onOpenTransaction)
}

@Composable
fun OwedContent(
    state: OwedUiState,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { _ ->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
                Box(
                    Modifier.size(spacing.minTouchTarget).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = spacing.xxl)) {
                ContextHeader(
                    heading = "Owed",
                    subline = when {
                        !state.isLoaded -> "Counting"
                        state.isSettled -> "You are square with everyone"
                        else -> "${state.owedToYou.format()} out · ${state.owedByYou.format()} in"
                    },
                )

                if (state.owesYou.isNotEmpty()) {
                    SectionTitle("Owes you")
                    state.owesYou.forEach { PersonRow(it) }
                }

                if (state.youOwe.isNotEmpty()) {
                    SectionTitle("You owe")
                    state.youOwe.forEach { PersonRow(it) }
                }

                // Naming is what turns a pile of loans into a ledger, so the ones
                // still missing a name are listed rather than quietly netted away.
                if (state.unnamed.isNotEmpty()) {
                    SectionTitle("Not named yet")
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
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val spacing = LocalSpacing.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = spacing.screenHorizontal,
            end = spacing.screenHorizontal,
            top = spacing.lg,
            bottom = spacing.sm,
        ),
    )
}

@Composable
private fun PersonRow(person: Person) {
    val spacing = LocalSpacing.current
    val direction = if (person.owesYou) "owes you" else "you owe"
    Row(
        Modifier
            .fillMaxWidth()
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
