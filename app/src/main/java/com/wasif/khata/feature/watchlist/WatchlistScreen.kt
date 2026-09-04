package com.wasif.khata.feature.watchlist

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.LocalSpacing
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun WatchlistScreen(
    onBack: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onAddTitle: () -> Unit,
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
 * Up next, then watched. A title with no poster shows its name set large rather than a
 * placeholder graphic: an absent poster is normal, and inventing an image the product
 * does not have would be worse than the gap.
 */
@Composable
fun WatchlistContent(
    state: WatchlistUiState,
    onBack: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onAddTitle: () -> Unit,
) {
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { _ ->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Text(
                    text = "WATCHLIST",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.padding(end = spacing.sm))
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = spacing.lg),
            ) {
                if (state.isEmpty) {
                    item {
                        Text(
                            text = "Nothing here yet. Add something you mean to watch.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = spacing.screenHorizontal,
                                vertical = spacing.lg,
                            ),
                        )
                    }
                }

                if (state.queue.isNotEmpty()) {
                    item { SectionLabel("Up next", top = spacing.sm) }
                    items(state.queue, key = { "q-${it.id}" }) { card ->
                        TitleRow(card = card, onClick = { onOpenTitle(card.id) })
                    }
                }

                if (state.watched.isNotEmpty()) {
                    item { SectionLabel("Watched", top = spacing.lg) }
                    items(state.watched, key = { "w-${it.id}" }) { card ->
                        TitleRow(card = card, onClick = { onOpenTitle(card.id) })
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            ) {
                Pill(
                    text = "Add a title",
                    selected = true,
                    modifier = Modifier.fillMaxWidth().testTag("addTitle"),
                    onClick = onAddTitle,
                )
            }
        }
    }
}

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
private fun Poster(model: String?, name: String, width: Int = 56) {
    val spacing = LocalSpacing.current
    val shape = MaterialTheme.shapes.small

    Box(
        Modifier
            .width(width.dp)
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
