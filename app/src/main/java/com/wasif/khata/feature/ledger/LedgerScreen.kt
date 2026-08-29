package com.wasif.khata.feature.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.ui.component.CategoryDot
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.PageHeadingStyle
import com.wasif.khata.core.ui.theme.PageSublineStyle
import java.time.format.DateTimeFormatter

@Composable
fun LedgerScreen(
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    LedgerContent(
        items = viewModel.items.collectAsLazyPagingItems(),
        header = viewModel.header.collectAsStateWithLifecycle().value,
        categoryTokens = viewModel.categoryTokens.collectAsStateWithLifecycle().value,
        canGoForward = viewModel.canGoForward.collectAsStateWithLifecycle().value,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onBack = onBack,
        onAddTransaction = onAddTransaction,
        onOpenTransaction = onOpenTransaction,
    )
}

@Composable
fun LedgerContent(
    items: LazyPagingItems<LedgerItem>,
    header: LedgerHeaderState,
    categoryTokens: Map<Long, CategoryChip>,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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

            // A list of 3,000 rows has no bottom to anchor to, so the ledger gets
            // a fixed headspace where the other screens get a flexible one --
            // enough to read as the same family, small enough that rows stay
            // visible before scrolling.
            MonthHeader(
                header = header,
                canGoForward = canGoForward,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                modifier = Modifier.fillMaxWidth().height(spacing.headspaceLedger),
            )

            MonthStrip(header = header)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.screenHorizontal,
                    end = spacing.screenHorizontal,
                    top = spacing.sm,
                    // Clear the FAB, or the last row hides under it.
                    bottom = spacing.xxl + spacing.xl,
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
                    // Headers and rows are different shapes, so separate content
                    // types let LazyColumn recycle each against its own pool.
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
                            chip = item.transaction.categoryId?.let { categoryTokens[it] },
                            onClick = { onOpenTransaction(item.transaction.id) },
                        )
                        null -> Unit
                    }
                }
            }
        }

        // enablePlaceholders = false drops itemCount to 0 during every refresh, so
        // itemCount alone would flash this on each month step before rows arrive.
        if (items.itemCount == 0 && items.loadState.refresh !is LoadState.Loading) {
            EmptyLedger(Modifier.align(Alignment.Center), monthLabel = header.monthLabel)
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(spacing.screenHorizontal)
                .size(58.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(KhataPalette.heroStops))
                .clickable(onClick = onAddTransaction),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add transaction",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    header: LedgerHeaderState,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Row(modifier.padding(horizontal = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        MonthArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = "Previous month",
            enabled = true,
            onClick = onPreviousMonth,
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = header.monthLabel,
                style = PageHeadingStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                // A past month has no days left; "0 days left" would be a
                // different and wrong claim. 0 and 1 need their own words too:
                // "0 days left" is wrong on the last day, and "1 days left" is
                // ungrammatical on the second-to-last.
                text = when (val daysLeft = header.daysLeft) {
                    null -> "Complete month"
                    0 -> "Last day"
                    1 -> "1 day left"
                    else -> "$daysLeft days left"
                },
                style = PageSublineStyle,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
        MonthArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = "Next month",
            enabled = canGoForward,
            onClick = onNextMonth,
        )
    }
}

@Composable
private fun MonthArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Box(
        Modifier
            .size(spacing.minTouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            // Disabled rather than hidden: a control that vanishes is harder to
            // understand than one that is visibly unavailable.
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
    }
}

@Composable
private fun MonthStrip(header: LedgerHeaderState) {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(KhataPalette.heroStops))
            .padding(spacing.md),
    ) {
        Text(
            text = "SPENT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = header.monthSpend.format(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
private fun DayHeaderRow(header: LedgerItem.DayHeader) {
    val spacing = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().padding(top = spacing.md, bottom = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = header.date.format(dayFormatter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = header.total.format(),
            style = AmountTextStyle,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun TransactionRow(
    item: LedgerItem.Row,
    chip: CategoryChip?,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val transaction = item.transaction

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.minTouchTarget)
                .clickable(onClick = onClick)
                .padding(vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CategoryDot(
                // Null passes straight through: CategoryDot only announces
                // "Uncategorised" for an unknown token, and substituting a real
                // key like "category_neutral" here would silently suppress that.
                token = chip?.colorToken,
                lowConfidence = transaction.confidence == Confidence.LOW,
            )
            Column(Modifier.weight(1f).padding(start = spacing.sm, end = spacing.sm)) {
                Text(
                    text = transaction.merchantRaw ?: "Uncategorized",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Colour is never the sole signal: the category name is always
                // present in words, and low confidence is a ring on the dot
                // plus the word here.
                val meta = buildList {
                    chip?.name?.let { add(it) }
                    transaction.note?.let { add(it) }
                    if (transaction.confidence == Confidence.LOW) add("low confidence")
                }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MoneyText(money = transaction.amount, direction = transaction.direction)
        }
        // Rows separate with a hairline, never glass: one blur pass per row per
        // frame on a Paging list is not affordable.
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EmptyLedger(modifier: Modifier = Modifier, monthLabel: String) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // Names the month, so an empty past month does not read as an empty app.
            text = "Nothing in $monthLabel",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Use the arrows to look at another month, or tap + to record something.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}
