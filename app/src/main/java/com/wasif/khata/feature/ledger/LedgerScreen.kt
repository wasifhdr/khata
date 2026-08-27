package com.wasif.khata.feature.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.theme.LocalSpacing
import java.time.format.DateTimeFormatter

@Composable
fun LedgerScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    LedgerContent(
        items = viewModel.items.collectAsLazyPagingItems(),
        onAddTransaction = onAddTransaction,
        onOpenTransaction = onOpenTransaction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerContent(
    items: LazyPagingItems<LedgerItem>,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ledger") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
    ) { padding ->
        if (items.itemCount == 0) {
            EmptyLedger(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                horizontal = spacing.screenHorizontal,
                vertical = spacing.sm,
            ),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { item ->
                    when (item) {
                        is LedgerItem.Row -> "row-${item.transaction.id}"
                        is LedgerItem.DayHeader -> "header-${item.date}"
                    }
                },
                // Headers and rows are different shapes, so separate content types let
                // LazyColumn recycle each against its own pool rather than one mixed pool.
                contentType = items.itemContentType { item ->
                    when (item) {
                        is LedgerItem.Row -> "row"
                        is LedgerItem.DayHeader -> "header"
                    }
                },
            ) { index ->
                when (val item = items[index]) {
                    is LedgerItem.DayHeader -> DayHeaderRow(item)
                    is LedgerItem.Row -> TransactionRow(
                        item = item,
                        onClick = { onOpenTransaction(item.transaction.id) },
                    )
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun EmptyLedger(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = spacing.xl),
        ) {
            Text(
                text = "No transactions yet",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap the button below to record your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.sm),
            )
        }
    }
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
private fun DayHeaderRow(header: LedgerItem.DayHeader) {
    val spacing = LocalSpacing.current
    Text(
        text = header.date.format(dayFormatter),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.md, bottom = spacing.xs),
    )
}

@Composable
private fun TransactionRow(item: LedgerItem.Row, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val transaction = item.transaction

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = spacing.sm)) {
            Text(
                text = transaction.merchantRaw ?: "Uncategorized",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            transaction.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MoneyText(money = transaction.amount, direction = transaction.direction)
    }
}
