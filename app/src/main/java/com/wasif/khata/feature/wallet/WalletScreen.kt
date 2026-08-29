package com.wasif.khata.feature.wallet

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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
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
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun WalletScreen(
    onBack: () -> Unit,
    onOpenLedger: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    WalletContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onOpenLedger = onOpenLedger,
    )
}

@Composable
fun WalletContent(
    state: WalletUiState,
    onBack: () -> Unit,
    onOpenLedger: () -> Unit,
) {
    // Task 6 widens this to (state, onBack: (() -> Unit)?, onOpenHub, onOpenLedger)
    // when a module can be the app's root. Kept narrow here so this task stands
    // on its own.
    val spacing = LocalSpacing.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // The back circle is the one thing deliberately outside the thumb arc:
        // gesture-back is the primary way out, so this is an affordance rather
        // than a control anyone should have to stretch for.
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

        // Heading centred in open space; content anchored to the bottom. Same
        // shape as home, which is what makes the two read as one app.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ContextHeader(
                heading = "Wallet",
                subline = walletSubline(state),
            )
        }

        Column(
            Modifier.fillMaxWidth().padding(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            NetWorthCard(state = state)
            MonthPair(state = state)
            AccountList(state = state, onOpenLedger = onOpenLedger)
        }
    }
}

private fun walletSubline(state: WalletUiState): String {
    val accounts = "${state.accounts.size} accounts"
    // The subline answers "is this current?" -- the question the glance job is
    // actually asking -- rather than restating the heading.
    return if (state.driftingAccounts > 0) {
        "$accounts · ${state.driftingAccounts} need checking"
    } else {
        "$accounts · all reconciled"
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
private fun MonthPair(state: WalletUiState) {
    val spacing = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        MonthFigure(
            label = "SPENT",
            money = state.monthSpend,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MonthFigure(
            label = "RECEIVED",
            money = state.monthReceived,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthFigure(
    label: String,
    money: Money,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(spacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = money.format(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = tint,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

@Composable
private fun AccountList(state: WalletUiState, onOpenLedger: () -> Unit) {
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
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "Ledger →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenLedger),
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
                            MaterialTheme.colorScheme.outline
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
