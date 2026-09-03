package com.wasif.khata.feature.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalThemeSpec
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState

@Composable
fun WalletScreen(
    onBack: (() -> Unit)?,
    onOpenHub: (() -> Unit)?,
    onOpenLedger: () -> Unit,
    onOpenOwed: () -> Unit,
    onOpenInsights: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    WalletContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onOpenHub = onOpenHub,
        onOpenLedger = onOpenLedger,
        onOpenOwed = onOpenOwed,
        onOpenInsights = onOpenInsights,
    )
}

@Composable
fun WalletContent(
    state: WalletUiState,
    onBack: (() -> Unit)?,
    onOpenHub: (() -> Unit)?,
    onOpenLedger: () -> Unit,
    onOpenOwed: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val scroll = rememberScrollState()

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    .verticalScroll(scroll)
                    .padding(top = CollapsingHeaderHeight, bottom = spacing.lg)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                // Above the card, not beside ACCOUNTS: sat there they read as
                // column headers for the table under them.
                WalletLinks(
                    onOpenInsights = onOpenInsights,
                    onOpenOwed = onOpenOwed,
                    onOpenLedger = onOpenLedger,
                )

                NetWorthCard(state = state)

                if (state.netWorthTrend.size >= 2) {
                    NetWorthChart(points = state.netWorthTrend, haze = haze)
                }
                MonthPair(state = state, haze = haze)
                AccountList(state = state)

                if (state.categories.isNotEmpty()) {
                    CategoryBreakdown(state.categories)
                }
            }

            // Back when this screen was pushed; a hub glyph when it is the root.
            // Without the second case, choosing Wallet as home would strand the
            // user: the settings gear lives only on the hub, and back from a root
            // exits the app.
            CollapsingTopBar(
                heading = "Wallet",
                subline = walletSubline(state),
                collapse = scroll.collapseFraction(),
                hazeState = haze,
                onBack = onBack ?: onOpenHub,
                navIcon = if (onBack != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Home,
                navDescription = if (onBack != null) "Back" else "All modules",
            )
        }
    }
}

private fun walletSubline(state: WalletUiState): String {
    // M12: neither count singularises -- "1 accounts" and "1 need checking"
    // are both ungrammatical, and a one-account wallet or a single drifting
    // account are ordinary states this screen must render correctly, not
    // edge cases.
    val accounts = if (state.accounts.size == 1) "1 account" else "${state.accounts.size} accounts"
    // The subline answers "is this current?" -- the question the glance job is
    // actually asking -- rather than restating the heading.
    return if (state.driftingAccounts > 0) {
        val needsChecking = if (state.driftingAccounts == 1) {
            "1 needs checking"
        } else {
            "${state.driftingAccounts} need checking"
        }
        "$accounts · $needsChecking"
    } else {
        "$accounts · all reconciled"
    }
}

/** Right-aligned links out of the wallet. */
@Composable
private fun WalletLinks(
    onOpenInsights: () -> Unit,
    onOpenOwed: () -> Unit,
    onOpenLedger: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "Insights →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = spacing.lg).clickable(onClick = onOpenInsights),
        )
        Text(
            text = "Owed →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = spacing.lg).clickable(onClick = onOpenOwed),
        )
        Text(
            text = "Ledger →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onOpenLedger),
        )
    }
}

/**
 * Ninety days of net worth. The line is the accent, so the one coloured thing on
 * the page is the thing the page is about.
 *
 * Says four things a bare line does not: how much it moved and in which direction,
 * the high and the low it moved between, and where the window starts. Values are
 * end-of-day balances read from balance_snapshots -- what was written down each
 * night, not what today's figure implies about the past.
 */
@Composable
private fun NetWorthChart(points: List<Long>, haze: HazeState) {
    val spacing = LocalSpacing.current
    val accent = LocalThemeSpec.current.accent
    val low = points.min()
    val high = points.max()
    val change = points.last() - points.first()
    // Direction is a word, never only a colour or a sign glyph.
    val movement = if (change >= 0) "up" else "down"

    KhataGlass(
        hazeState = haze,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(spacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "LAST 90 DAYS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$movement ${Money(kotlin.math.abs(change)).format()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(top = spacing.sm)
                    .clearAndSetSemantics {
                        contentDescription = "Net worth over ninety days, $movement " +
                            "${Money(kotlin.math.abs(change)).format()}. " +
                            "High ${Money(high).format()}, low ${Money(low).format()}."
                    },
            ) {
                val span = (high - low).takeIf { it != 0L }?.toFloat() ?: 1f
                val stepX = size.width / (points.size - 1).toFloat()
                fun yOf(v: Long) = if (high == low) size.height / 2f
                else size.height * (1f - (v - low) / span)

                val line = Path()
                points.forEachIndexed { i, value ->
                    val x = i * stepX
                    if (i == 0) line.moveTo(x, yOf(value)) else line.lineTo(x, yOf(value))
                }

                // The fill is what turns a squiggle into a quantity.
                val area = Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    area,
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f)),
                    ),
                )
                drawPath(line, color = accent, style = Stroke(width = 2.dp.toPx()))
                // Where it stands today, so the eye lands on the end of the line.
                drawCircle(accent, radius = 3.dp.toPx(), center = Offset(size.width, yOf(points.last())))
            }

            Row(
                Modifier.fillMaxWidth().padding(top = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "low ${Money(low).format()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "high ${Money(high).format()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Where the month went, biggest first. Bars rather than a pie: comparing lengths
 * on a shared baseline is the one comparison people read accurately, and the
 * category name has to be legible anyway.
 */
@Composable
private fun CategoryBreakdown(categories: List<CategorySlice>) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal)) {
        Text(
            text = "BY CATEGORY",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = spacing.sm),
        )
        categories.forEach { slice ->
            val colour = KhataPalette.categories[slice.colorToken] ?: MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.sm)
                    // One sentence, not three fragments read in a row.
                    .clearAndSetSemantics {
                        contentDescription = "${slice.name}, ${slice.amount.format()}"
                    },
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = slice.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = spacing.sm),
                    )
                    MoneyText(money = slice.amount, direction = null)
                }
                // Colour is never the only signal: the name and the figure carry it,
                // and the bar is the comparison.
                Box(
                    Modifier
                        .padding(top = spacing.xs)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(slice.share.coerceAtLeast(0.02f))
                            .height(4.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(colour),
                    )
                }
            }
        }
    }
}

@Composable
private fun NetWorthCard(state: WalletUiState) {
    val spacing = LocalSpacing.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(KhataPalette.heroStops))
            .padding(spacing.md),
    ) {
        Column {
            Text(
                text = "NET WORTH",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(
                money = state.netWorth,
                direction = null,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = spacing.sm),
            )
        }
    }
}

@Composable
private fun MonthPair(state: WalletUiState, haze: HazeState) {
    val spacing = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        MonthFigure(
            haze = haze,
            label = "SPENT",
            money = state.monthSpend,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MonthFigure(
            haze = haze,
            label = "RECEIVED",
            money = state.monthReceived,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthFigure(
    haze: HazeState,
    label: String,
    money: Money,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    KhataGlass(
        hazeState = haze,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
    ) {
        // Padding lives inside the glass: KhataGlass clips to `shape`, so
        // padding applied outside would sit beyond the clip and the content
        // would touch the bevel.
        Column(Modifier.padding(spacing.md)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = money.format(),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = tint,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    }
}

@Composable
private fun AccountList(state: WalletUiState) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal)
                .padding(top = spacing.md, bottom = spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "ACCOUNTS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        }

        state.accounts.forEach { account ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        // The gap is stated in words, not colour alone.
                        text = if (account.hasBalanceDrift) "Balance disagrees · check" else "Reconciled",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (account.hasBalanceDrift) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = account.currentBalance.format(),
                    style = AmountTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
