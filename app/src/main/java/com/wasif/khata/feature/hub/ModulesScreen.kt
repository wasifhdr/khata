package com.wasif.khata.feature.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.TaglineTextStyle
import com.wasif.khata.core.ui.theme.WordmarkTextStyle
import dev.chrisbanes.haze.HazeState

@Composable
fun ModulesScreen(
    onOpenWallet: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: ModulesViewModel = hiltViewModel(),
) {
    ModulesContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onOpenWallet = onOpenWallet,
        onOpenSettings = onOpenSettings,
        onOpenSearch = onOpenSearch,
    )
}

@Composable
fun ModulesContent(
    state: ModulesUiState,
    onOpenWallet: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { haze ->

        // enableEdgeToEdge draws behind the system bars, so the content insets
        // itself or the wordmark sits under the status bar.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            // Settings earns a top corner because the thumb-arc rule governs
            // routine controls, and this is the least-used destination there is.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                // Search sits beside settings rather than on the field: it reaches
                // every module at once, so it belongs to the hub rather than to any
                // one of the tiles below.
                NavCircle(
                    icon = Icons.Filled.Search,
                    description = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = 22.dp,
                    onClick = onOpenSearch,
                )
                NavCircle(
                    icon = Icons.Filled.Settings,
                    description = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = 22.dp,
                    onClick = onOpenSettings,
                )
            }

            // Air at the top with the wordmark centred in it; the modules
            // anchored below. On a taller device the air grows, never the card.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "খাতা",
                    style = WordmarkTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "সব হিসাব, এক খাতায়",
                    style = TaglineTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = spacing.md),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal)
                    .padding(bottom = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                WalletCard(state = state, onClick = onOpenWallet)
                DormantRow(haze = haze, left = "Restaurants", right = "Watchlist")
                DormantRow(haze = haze, left = "Notes", right = "Car service")
            }
        }
    }
}

@Composable
private fun WalletCard(state: ModulesUiState, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(KhataPalette.heroStops))
            .clickable(onClick = onClick)
            .padding(spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "WALLET",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyText(
                    money = state.monthSpend,
                    direction = null,
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(top = spacing.sm),
                )
                Text(
                    text = "spent this month",
                    style = MaterialTheme.typography.bodySmall,
                    // outline is only 2.79:1 on this card's own gradient -- below
                    // the text floor on the app's first viewport. onSurfaceVariant
                    // (already used for WALLET above) clears every hero stop.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.lastTransaction?.let { last ->
                    Text(
                        text = "Last · ${last.merchantRaw ?: "Uncategorized"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                }
            }

            // Absent, not zero, when no budget is set. A ring drawn against a
            // budget the user never entered is a number the app invented.
            state.budgetFraction?.let { fraction -> BudgetRing(fraction = fraction) }
        }
    }
}

@Composable
private fun BudgetRing(fraction: Float) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val value = MaterialTheme.colorScheme.primary
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = value,
                // -90 so the ring starts at twelve o'clock rather than three.
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${(fraction * 100).toInt()}",
            style = MaterialTheme.typography.labelLarge,
            color = value,
        )
    }
}

@Composable
private fun DormantRow(haze: HazeState, left: String, right: String) {
    val spacing = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        listOf(left, right).forEach { name ->
            KhataGlass(
                hazeState = haze,
                modifier = Modifier.weight(1f).height(96.dp),
            ) {
                // Unbuilt modules read as unbuilt. Hiding them would make the
                // hub a launcher with one tile; faking data would be worse.
                Column(
                    Modifier.fillMaxSize().padding(spacing.md),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Not built",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
