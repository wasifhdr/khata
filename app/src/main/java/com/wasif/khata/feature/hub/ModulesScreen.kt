package com.wasif.khata.feature.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.wasif.khata.core.ui.component.KhataIcons
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
    onOpenRestaurants: () -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenNotes: () -> Unit,
    viewModel: ModulesViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value

    // Asked here rather than at launch: this fires the first time something is
    // actually waiting, which is the first moment the permission buys anything.
    // Denied, the dot below and the TO SETTLE list in Wallet carry the whole feature.
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(state.hasPendingReview) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (state.hasPendingReview && !granted) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ModulesContent(
        state = state,
        onOpenWallet = onOpenWallet,
        onOpenSettings = onOpenSettings,
        onOpenSearch = onOpenSearch,
        onOpenRestaurants = onOpenRestaurants,
        onOpenVehicle = onOpenVehicle,
        onOpenWatchlist = onOpenWatchlist,
        onOpenNotes = onOpenNotes,
    )
}

@Composable
fun ModulesContent(
    state: ModulesUiState,
    onOpenWallet: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRestaurants: () -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenNotes: () -> Unit,
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    ModuleTile(
                        haze = haze,
                        name = "Restaurants",
                        icon = KhataIcons.Restaurant,
                        tile = state.restaurants,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenRestaurants,
                    )
                    ModuleTile(
                        haze = haze,
                        name = "Watchlist",
                        icon = KhataIcons.Watchlist,
                        tile = state.watchlist,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenWatchlist,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    ModuleTile(
                        haze = haze,
                        name = "Car service",
                        icon = KhataIcons.Car,
                        tile = state.vehicle,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenVehicle,
                    )
                    ModuleTile(
                        haze = haze,
                        name = "Notes",
                        icon = KhataIcons.Notes,
                        tile = state.notes,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenNotes,
                    )
                }
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
        if (state.hasPendingReview) {
            // A dot, not a count: the number is not the point, and a badge reading "1"
            // on a card whose other number is money invites reading it as money.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(spacing.sm)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }

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

/** A live tile: the same glass, the same shape, and a destination behind it. */
@Composable
private fun ModuleTile(
    haze: HazeState,
    name: String,
    icon: ImageVector,
    tile: ModuleTileState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current

    // Square, and the same corner radius as the wallet card above it, so the hub reads as one
    // family of surfaces rather than a card with four smaller strangers underneath.
    KhataGlass(hazeState = haze, modifier = modifier.aspectRatio(1f)) {
        Column(
            Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(spacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = name.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = spacing.xs),
            )

            Spacer(Modifier.weight(1f))

            tile.stats.forEach { stat -> StatLine(stat) }
        }
    }
}

/**
 * One stat on one line, always. A long restaurant name scrolls sideways on a loop rather than
 * being cut off with an ellipsis: on a tile this size almost every interesting name would be
 * truncated, and a name you cannot finish reading is not worth the row it sits on.
 */
@Composable
private fun StatLine(stat: TileStat) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stat.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = stat.value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp)
                // Faded at the trailing edge, so text leaving the tile reads as scrolling
                // rather than as truncation -- a hard clip looks exactly like the ellipsis
                // the marquee is here to avoid.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.85f to Color.Black,
                            1f to Color.Transparent,
                            startX = 0f,
                            endX = size.width,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .basicMarquee(iterations = Int.MAX_VALUE),
        )
    }
}
