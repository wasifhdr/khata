package com.wasif.khata.feature.restaurants

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.component.StarRow
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
fun RestaurantScreen(
    onBack: () -> Unit,
    onLogVisit: (Long) -> Unit,
    onOpenVisit: (Long) -> Unit,
    viewModel: RestaurantViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RestaurantContent(
        state = state,
        actions = viewModel,
        onBack = onBack,
        onLogVisit = onLogVisit,
        onOpenVisit = onOpenVisit,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestaurantContent(
    state: RestaurantUiState,
    actions: RestaurantActions,
    onBack: () -> Unit,
    onLogVisit: (Long) -> Unit,
    onOpenVisit: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current
    val list = rememberLazyListState()
    val context = LocalContext.current
    val summary = state.summary

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().hazeSource(haze),
                contentPadding = PaddingValues(top = CollapsingHeaderHeight, bottom = spacing.xxl),
            ) {
                state.coverModel?.let { cover ->
                    item {
                        AsyncImage(
                            model = cover,
                            contentDescription = "Cover photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .padding(horizontal = spacing.screenHorizontal)
                                .clip(MaterialTheme.shapes.large),
                        )
                    }
                }

                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Pill(
                            text = "Log a visit",
                            selected = true,
                            leadingIcon = Icons.Filled.Add,
                            modifier = Modifier.weight(1f),
                            onClick = { summary?.let { onLogVisit(it.id) } },
                        )
                        if (state.allPhotos.isNotEmpty()) {
                            Pill(
                                text = "Set cover",
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = actions::onSetCoverRequested,
                            )
                        }
                    }
                }

                if (summary != null) {
                    item { Verdict(summary.dishAverage, summary.ambianceAverage, haze) }
                }

                // Tapping the place opens Maps at the stored URL. The app never draws
                // a map of its own: the one the user already has is better than any
                // this could put on screen.
                summary?.mapsUrl?.let { url ->
                    item {
                        Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                            Pill(
                                text = summary.placeName ?: "Open in Maps",
                                selected = false,
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, url.toUri()),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                if (state.recommenders.isNotEmpty()) {
                    item {
                        SectionLabel("Recommended by")
                        FlowRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.screenHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            state.recommenders.forEach { name ->
                                Pill(text = name, selected = false, onClick = {})
                            }
                        }
                    }
                }

                summary?.note?.let { note ->
                    item {
                        SectionLabel("Note")
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                        )
                    }
                }

                if (state.visits.isEmpty()) {
                    item {
                        SectionLabel("Visits")
                        Text(
                            text = "Not been yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                        )
                    }
                } else {
                    item { SectionLabel("Visits") }
                    items(state.visits, key = { it.id }) { visit ->
                        VisitRow(visit, haze) { onOpenVisit(visit.id) }
                    }
                }

                item { Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
            }

            CollapsingTopBar(
                heading = summary?.name ?: "Restaurant",
                subline = when (val count = state.visits.size) {
                    0 -> "Want to try"
                    1 -> "1 visit"
                    else -> "$count visits"
                },
                collapse = list.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )
        }
    }

    if (state.pickingCover) {
        CoverPicker(state.allPhotos, actions)
    }
}

/**
 * Both ratings, side by side and never averaged together. The dishes are the verdict
 * -- derived, so it cannot contradict the items it summarises -- and the room is a
 * separate question with its own answer.
 */
@Composable
private fun Verdict(dishAverage: Double?, ambianceAverage: Double?, haze: HazeState) {
    val spacing = LocalSpacing.current
    KhataGlass(
        hazeState = haze,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
    ) {
        Row(Modifier.fillMaxWidth().padding(spacing.md)) {
            RatingBlock("Food", dishAverage, Modifier.weight(1f))
            RatingBlock("Ambiance", ambianceAverage, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RatingBlock(label: String, value: Double?, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Column(modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            // Absent, never zero. Nothing rated means nothing to report, and a 0.0
            // would be a verdict the app invented.
            text = value?.let { "%.1f".format(it) } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = spacing.xs),
        )
        StarRow(value = value, modifier = Modifier.padding(top = spacing.xs))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisitRow(visit: VisitCard, haze: HazeState, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    KhataGlass(
        hazeState = haze,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = visit.visitedAt.toDhakaLocalDate().format(DayFormat),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                visit.costMinor?.let { minor -> MoneyText(money = Money(minor), direction = null) }
            }

            visit.ambianceRating?.let { rating ->
                StarRow(value = rating.toDouble(), modifier = Modifier.padding(top = spacing.xs))
            }

            visit.dishes.forEach { (name, rating) ->
                Row(
                    Modifier.fillMaxWidth().padding(top = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    StarRow(value = rating?.toDouble())
                }
            }

            if (visit.companions.isNotEmpty()) {
                Text(
                    text = "With ${visit.companions.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.sm),
                )
            }

            visit.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }

            if (visit.photos.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    visit.photos.forEach { photo ->
                        AsyncImage(
                            model = photo.model,
                            contentDescription = "Photo of this visit",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.small),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Any photo from any visit. Picking one writes coverMediaId -- a reference to the row
 * that already holds it, never a second copy of the file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverPicker(photos: List<Photo>, actions: RestaurantActions) {
    val spacing = LocalSpacing.current
    AlertDialog(
        onDismissRequest = actions::onCoverDismissed,
        title = { Text("Set cover") },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                photos.forEach { photo ->
                    AsyncImage(
                        model = photo.model,
                        contentDescription = "Use as cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { actions.onCoverPicked(photo.mediaId) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = actions::onCoverDismissed) { Text("Cancel") } },
    )
}
