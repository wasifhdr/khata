package com.wasif.khata.feature.restaurants

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.component.StarRow
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.hazeSource
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun RestaurantsScreen(
    onBack: () -> Unit,
    onLogVisit: () -> Unit,
    onOpenRestaurant: (Long) -> Unit,
    onAddToWishlist: () -> Unit,
    viewModel: RestaurantsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RestaurantsContent(
        state = state,
        onBack = onBack,
        onLogVisit = onLogVisit,
        onOpenRestaurant = onOpenRestaurant,
        onAddToWishlist = onAddToWishlist,
    )
}

/**
 * Two sections and one primary action. Been is ordered by recency; want-to-try is
 * whatever has no visits yet, which is a query rather than a list anyone maintains.
 */
@Composable
fun RestaurantsContent(
    state: RestaurantsUiState,
    onBack: () -> Unit,
    onLogVisit: () -> Unit,
    onOpenRestaurant: (Long) -> Unit,
    onAddToWishlist: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val list = androidx.compose.foundation.lazy.rememberLazyListState()

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().hazeSource(haze),
                contentPadding = PaddingValues(top = CollapsingHeaderHeight, bottom = spacing.xxl),
            ) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Pill(
                            text = "Log a visit",
                            selected = true,
                            leadingIcon = Icons.Filled.Add,
                            modifier = Modifier.weight(1f),
                            onClick = onLogVisit,
                        )
                        Pill(
                            text = "Add to wishlist",
                            selected = false,
                            modifier = Modifier.weight(1f),
                            onClick = onAddToWishlist,
                        )
                    }
                }

                if (state.been.isNotEmpty()) {
                    item { SectionLabel("Been") }
                    items(state.been, key = { it.id }) { card ->
                        BeenRow(card) { onOpenRestaurant(card.id) }
                    }
                }

                if (state.wishlist.isNotEmpty()) {
                    item { SectionLabel("Want to try") }
                    items(state.wishlist, key = { it.id }) { card ->
                        WishlistRow(card) { onOpenRestaurant(card.id) }
                    }
                }

                if (state.isEmpty) {
                    item {
                        Text(
                            text = "Nothing yet. Log a visit, or share a place from Maps.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = spacing.screenHorizontal,
                                vertical = spacing.xl,
                            ),
                        )
                    }
                }

                item { Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
            }

            CollapsingTopBar(
                heading = "Restaurants",
                subline = subline(state),
                collapse = list.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )
        }
    }
}

private fun subline(state: RestaurantsUiState): String = when {
    state.isEmpty -> "Nothing recorded yet"
    state.wishlist.isEmpty() -> "${state.been.size} been"
    else -> "${state.been.size} been · ${state.wishlist.size} to try"
}

@Composable
private fun BeenRow(card: RestaurantCard, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val summary = card.summary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(card.coverModel, summary.name)
        Column(Modifier.weight(1f).padding(start = spacing.md)) {
            Text(
                text = summary.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The dish average is the verdict; ambiance is reported beside it
                // rather than folded into it, because they answer different questions.
                StarRow(value = summary.dishAverage)
                summary.dishAverage?.let { average ->
                    Text(
                        text = " %.1f".format(average),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                summary.ambianceAverage?.let { ambiance ->
                    Text(
                        text = "  ·  ambiance %.1f".format(ambiance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = summary.lastVisitedAt
                    ?.let { "Last ${it.toDhakaLocalDate().format(DayFormat)}" }
                    .orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WishlistRow(card: RestaurantCard, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val summary = card.summary
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
    ) {
        Text(
            text = summary.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        listOfNotNull(summary.placeName, summary.note)
            .takeIf { it.isNotEmpty() }
            ?.let { lines ->
                Text(
                    text = lines.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}

/**
 * A cover, or the space where one would be. An empty square rather than nothing at
 * all, so a list of restaurants with and without photos still reads as one column.
 */
@Composable
private fun Cover(model: String?, name: String) {
    val shape = MaterialTheme.shapes.small
    if (model == null) {
        Box(
            Modifier
                .size(56.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(shape),
        )
    }
}
