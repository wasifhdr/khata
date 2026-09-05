package com.wasif.khata.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.data.dao.RestaurantSummary
import com.wasif.khata.core.data.dao.ServiceSummary
import com.wasif.khata.core.data.dao.TitleSummary
import com.wasif.khata.core.data.entity.PlaceEntity
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.domain.model.Transaction
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenRestaurant: (Long) -> Unit,
    onOpenService: (Long) -> Unit,
    onOpenTitle: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        actions = viewModel,
        onBack = onBack,
        onOpenTransaction = onOpenTransaction,
        onOpenRestaurant = onOpenRestaurant,
        onOpenService = onOpenService,
        onOpenTitle = onOpenTitle,
    )
}

/**
 * One box, and results grouped by kind. Recency inside each group and nothing
 * cleverer: FTS4 has no relevance ranking, and a ranking invented on top of it would
 * be a guess wearing the clothes of an answer.
 */
@Composable
fun SearchContent(
    state: SearchUiState,
    actions: SearchActions,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenRestaurant: (Long) -> Unit,
    onOpenService: (Long) -> Unit,
    onOpenTitle: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current
    val focus = remember { FocusRequester() }

    // The screen exists to be typed into. Anything else is a tap the user has to
    // make before the screen does what they opened it for.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    FieldScaffold(Modifier.fillMaxSize()) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions::onQueryChange,
                    placeholder = { Text("Search everything") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.xs)
                        .focusRequester(focus)
                        .testTag("searchField"),
                )
            }

            when {
                !state.hasQuery -> Hint("Dishes, people, merchants, parts, titles, places.")
                state.isEmpty && !state.searching -> Hint("Nothing matches “${state.query}”.")
                else -> Results(state, onOpenTransaction, onOpenRestaurant, onOpenService, onOpenTitle)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    val spacing = LocalSpacing.current
    Box(Modifier.fillMaxSize().padding(spacing.xl), contentAlignment = Alignment.TopCenter) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Results(
    state: SearchUiState,
    onOpenTransaction: (Long) -> Unit,
    onOpenRestaurant: (Long) -> Unit,
    onOpenService: (Long) -> Unit,
    onOpenTitle: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = spacing.xxl)) {
        if (state.transactions.isNotEmpty()) {
            item { SectionLabel("Transactions", top = spacing.md) }
            items(state.transactions, key = { "t-${it.id}" }) { row ->
                TransactionRow(row) { onOpenTransaction(row.id) }
            }
        }
        if (state.restaurants.isNotEmpty()) {
            item { SectionLabel("Restaurants", top = spacing.lg) }
            items(state.restaurants, key = { "r-${it.id}" }) { row ->
                RestaurantRow(row) { onOpenRestaurant(row.id) }
            }
        }
        if (state.services.isNotEmpty()) {
            item { SectionLabel("Car service", top = spacing.lg) }
            items(state.services, key = { "s-${it.id}" }) { row ->
                ServiceRow(row) { onOpenService(row.id) }
            }
        }
        if (state.titles.isNotEmpty()) {
            item { SectionLabel("Watchlist", top = spacing.lg) }
            items(state.titles, key = { "ti-${it.id}" }) { row ->
                TitleSearchRow(row) { onOpenTitle(row.id) }
            }
        }
        if (state.places.isNotEmpty()) {
            item { SectionLabel("Places", top = spacing.lg) }
            items(state.places, key = { "p-${it.id}" }) { row -> PlaceRow(row) }
        }
    }
}

@Composable
private fun TransactionRow(row: Transaction, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.merchantRaw ?: row.counterparty ?: "Uncategorized",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.occurredAt.toDhakaLocalDate().format(DayFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText(money = row.amount, direction = row.direction)
    }
}

@Composable
private fun RestaurantRow(row: RestaurantSummary, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (row.visitCount == 0) {
                    "Want to try"
                } else {
                    "${row.visitCount} ${if (row.visitCount == 1) "visit" else "visits"}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.dishAverage?.let { average ->
            Text(
                text = "%.1f".format(average),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ServiceRow(row: ServiceSummary, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // The items are the headline: what a search for "brake" was looking for.
            // A date alone would make every result look the same.
            Text(
                text = row.itemNames ?: row.placeName ?: "Service",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.servicedAt.toDhakaLocalDate().format(DayFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.costMinor?.let { MoneyText(money = Money(it), direction = null) }
    }
}

@Composable
private fun TitleSearchRow(row: TitleSummary, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (row.watchCount == 0) "Up next" else "Watched",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.verdict?.let { verdict ->
            Text(
                text = "%.1f".format(verdict),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlaceRow(row: PlaceEntity) {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        row.address?.let { address ->
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
