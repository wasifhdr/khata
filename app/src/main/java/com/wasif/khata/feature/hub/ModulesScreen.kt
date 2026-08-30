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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.khataFieldSource
import com.wasif.khata.core.ui.component.rememberKhataHazeState
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.LocalThemeSpec
import com.wasif.khata.core.ui.theme.TaglineTextStyle
import com.wasif.khata.core.ui.theme.WordmarkTextStyle
import dev.chrisbanes.haze.HazeState

@Composable
fun ModulesScreen(
    onOpenWallet: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ModulesViewModel = hiltViewModel(),
) {
    ModulesContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onOpenWallet = onOpenWallet,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun ModulesContent(
    state: ModulesUiState,
    onOpenWallet: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val spec = LocalThemeSpec.current
    val haze = rememberKhataHazeState()

    Box(Modifier.fillMaxSize().background(spec.ground)) {
        // The field is the backdrop the glass samples. It is a static surface,
        // which is why glass is affordable here and forbidden on a Paging list.
        FieldBackdrop(Modifier.fillMaxSize().khataFieldSource(haze))

        // enableEdgeToEdge draws behind the system bars, so the content insets
        // itself or the wordmark sits under the status bar.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            // Settings earns a top corner because the thumb-arc rule governs
            // routine controls, and this is the least-used destination there is.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    Modifier
                        .size(spacing.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
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
                    color = MaterialTheme.colorScheme.outline,
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

/**
 * Soft radial pools, not a full-bleed sweep. The ground stays dominant and the
 * field reads as atmosphere -- a linear gradient across the whole screen floods
 * it, and the petrol wallet card then has nothing to sit against.
 *
 * Each pool is capped well below full opacity even at Full intensity, because
 * the key stop is already the lightest colour the mesh should ever reach.
 */
@Composable
private fun FieldBackdrop(modifier: Modifier = Modifier) {
    val spec = LocalThemeSpec.current
    val strength = spec.intensity.alpha

    Box(
        modifier.drawBehind {
            drawRect(spec.ground)
            if (strength <= 0f) return@drawBehind

            fun pool(colour: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(colour.copy(alpha = alpha * strength), Color.Transparent),
                        center = Offset(size.width * cx, size.height * cy),
                        radius = size.minDimension * r,
                    ),
                )
            }

            pool(spec.field.keyStop, 0.85f, 0.14f, 0.02f, 1.15f)
            pool(spec.field.keyStop, 0.55f, 0.92f, 0.16f, 0.95f)
            pool(KhataPalette.heroStops.first(), 0.60f, 0.70f, 0.78f, 1.00f)
            pool(spec.ground, 0.70f, 0.10f, 0.95f, 0.90f)
        },
    )
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
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = "Not built",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
