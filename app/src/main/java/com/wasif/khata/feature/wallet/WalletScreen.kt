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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.HazeState

@Composable
fun WalletScreen(
    onBack: (() -> Unit)?,
    onOpenHub: (() -> Unit)?,
    onOpenLedger: () -> Unit,
    onOpenOwed: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    WalletContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onOpenHub = onOpenHub,
        onOpenLedger = onOpenLedger,
        onOpenOwed = onOpenOwed,
    )
}

@Composable
fun WalletContent(
    state: WalletUiState,
    onBack: (() -> Unit)?,
    onOpenHub: (() -> Unit)?,
    onOpenLedger: () -> Unit,
    onOpenOwed: () -> Unit,
) {
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            // The back circle is the one thing deliberately outside the thumb arc:
            // gesture-back is the primary way out, so this is an affordance rather
            // than a control anyone should have to stretch for.
            Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
                // Back when this screen was pushed; a hub glyph when it is the root.
                // Without the second case, choosing Wallet as home would strand the
                // user: the settings gear lives only on the hub, and back from a
                // root exits the app.
                when {
                    onBack != null -> NavCircle(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = "Back",
                        onClick = onBack,
                    )
                    onOpenHub != null -> NavCircle(
                        icon = Icons.Filled.Home,
                        description = "All modules",
                        onClick = onOpenHub,
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
                MonthPair(state = state, haze = haze)
                AccountList(state = state, onOpenLedger = onOpenLedger, onOpenOwed = onOpenOwed)
            }
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
private fun AccountList(state: WalletUiState, onOpenLedger: () -> Unit, onOpenOwed: () -> Unit) {
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
            Text(
                text = "Owed →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = LocalSpacing.current.md)
                    .clickable(onClick = onOpenOwed),
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

@Composable
private fun NavCircle(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Box(
        Modifier.size(spacing.minTouchTarget).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
