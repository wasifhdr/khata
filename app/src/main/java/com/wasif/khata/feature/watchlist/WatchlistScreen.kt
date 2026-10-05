package com.wasif.khata.feature.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.hazeSource
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun WatchlistScreen(
    onBack: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onAddTitle: (Boolean) -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WatchlistContent(
        state = state,
        onBack = onBack,
        onOpenTitle = onOpenTitle,
        onAddTitle = onAddTitle,
    )
}

/**
 * Watched and Watchlist tabs. A title with no poster shows its name set large rather than a
 * placeholder graphic: an absent poster is normal, and inventing an image the product
 * does not have would be worse than the gap.
 */
@Composable
fun WatchlistContent(
    state: WatchlistUiState,
    onBack: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onAddTitle: (Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    val list = rememberLazyListState()
    var watchlistTab by rememberSaveable { mutableStateOf(false) }
    val activeRows = if (watchlistTab) state.queue else state.watched

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().hazeSource(haze),
                contentPadding = PaddingValues(
                    top = CollapsingHeaderHeight,
                    bottom = spacing.xxl + spacing.xl,
                ),
            ) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Pill(
                            text = "Watched",
                            selected = !watchlistTab,
                            modifier = Modifier.weight(1f),
                            onClick = { watchlistTab = false },
                        )
                        Pill(
                            text = "Watchlist",
                            selected = watchlistTab,
                            modifier = Modifier.weight(1f),
                            onClick = { watchlistTab = true },
                        )
                    }
                }

                items(activeRows, key = { it.id }) { card ->
                    TitleRow(card = card, onClick = { onOpenTitle(card.id) })
                }

                if (activeRows.isEmpty()) {
                    item {
                        Text(
                            text = if (watchlistTab) {
                                "Nothing on the watchlist yet. Tap + to add something you mean to watch."
                            } else {
                                "Nothing watched yet. Tap + to log something you've seen."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = spacing.screenHorizontal,
                                vertical = spacing.lg,
                            ),
                        )
                    }
                }

                item { Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
            }

            CollapsingTopBar(
                heading = "Watchlist",
                subline = Subline,
                collapse = list.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(spacing.screenHorizontal)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(KhataPalette.heroStops))
                    .clickable { onAddTitle(!watchlistTab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = if (watchlistTab) "Add to watchlist" else "Log watched",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val Subline = "Watched already or lined up next"

@Composable
private fun TitleRow(card: TitleCard, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val summary = card.summary

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(model = card.posterModel, name = summary.name)

        Column(Modifier.weight(1f).padding(start = spacing.md)) {
            Text(
                text = summary.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    summary.year?.toString(),
                    if (summary.kind == TitleKind.FILM) "Film" else "Series",
                    summary.lastWatchedAt?.toDhakaLocalDate()?.format(DayFormat),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        summary.verdict?.let { verdict ->
            // The user's own 1-5, which is why it carries a star and the TMDB number
            // on the detail screen does not.
            Text(
                text = "★ %.1f".format(verdict),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 2:3, the poster aspect every service uses. With no image the card carries the name
 * set large -- a shape that reads as a poster without pretending to be one.
 */
@Composable
private fun Poster(model: String?, name: String) {
    val spacing = LocalSpacing.current
    val shape = MaterialTheme.shapes.small

    Box(
        Modifier
            .width(56.dp)
            .aspectRatio(2f / 3f)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(spacing.xs),
                )
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
