package com.wasif.khata.feature.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.component.StarRating
import com.wasif.khata.core.ui.theme.LocalSpacing
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun TitleScreen(
    onBack: () -> Unit,
    viewModel: TitleViewModel,
    now: Long,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TitleContent(state = state, actions = viewModel, onBack = onBack, now = now)
}

/**
 * One title, its two ratings side by side and never blended: TMDB's runs 0 to 10 and
 * is stamped with the date it was taken, the user's runs 1 to 5 and is averaged from
 * their own watches.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TitleContent(
    state: TitleUiState,
    actions: TitleActions,
    onBack: () -> Unit,
    now: Long,
) {
    val spacing = LocalSpacing.current
    val summary = state.summary
    var logging by remember { mutableStateOf(false) }

    FieldScaffold(Modifier.fillMaxSize()) { _ ->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NavCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            }

            if (summary == null) return@Column

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.sm,
                    ),
                ) {
                    Box(
                        Modifier
                            .width(112.dp)
                            .aspectRatio(2f / 3f)
                            .clip(MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.posterModel == null) {
                            Text(
                                text = summary.name.take(2).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            AsyncImage(
                                model = state.posterModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    Column(Modifier.weight(1f).padding(start = spacing.md)) {
                        Text(
                            text = summary.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = listOfNotNull(
                                summary.year?.toString(),
                                if (summary.kind == TitleKind.FILM) "Film" else "Series",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        summary.verdict?.let { verdict ->
                            Text(
                                text = "Your verdict  ★ %.1f".format(verdict),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = spacing.sm),
                            )
                        }

                        summary.tmdbRating?.let { rating ->
                            // Dated, because it came from somewhere else and is a
                            // snapshot rather than a live number.
                            Text(
                                text = "TMDB %.1f/10".format(rating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                            summary.tmdbRatingAt?.let { at ->
                                Text(
                                    text = "as of ${at.toDhakaLocalDate().format(DayFormat)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (state.recommenders.isNotEmpty()) {
                    SectionLabel("Recommended by")
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        state.recommenders.forEach { name ->
                            Pill(text = name, selected = false, onClick = {})
                        }
                    }
                }

                summary.note?.let { note ->
                    SectionLabel("Notes")
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            horizontal = spacing.screenHorizontal,
                            vertical = spacing.xs,
                        ),
                    )
                }

                SectionLabel(if (state.watches.isEmpty()) "Not watched yet" else "Watches")
                state.watches.forEach { watch ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { actions.onDeleteWatch(watch.id) }
                            .padding(
                                horizontal = spacing.screenHorizontal,
                                vertical = spacing.sm,
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = watch.watchedAt.toDhakaLocalDate().format(DayFormat),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            watch.note?.let { note ->
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        watch.rating?.let { rating ->
                            Text(
                                text = "★ $rating",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            ) {
                Pill(
                    text = "Log a watch",
                    selected = true,
                    modifier = Modifier.fillMaxWidth().testTag("logWatch"),
                    onClick = { logging = true },
                )
            }
        }
    }

    if (logging) {
        LogWatchSheet(
            now = now,
            onDismiss = { logging = false },
            onSave = { rating, note ->
                actions.onLogWatch(now, rating, note)
                logging = false
            },
        )
    }
}

/** Three fields do not earn a destination, so this is a sheet over the title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogWatchSheet(
    now: Long,
    onDismiss: () -> Unit,
    onSave: (Int?, String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState()
    var rating by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal)
                .padding(bottom = spacing.xl)
                .imePadding(),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Watched ${now.toDhakaLocalDate().format(DayFormat)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                NavCircle(Icons.Filled.Close, "Close", onClick = onDismiss)
            }

            StarRating(
                value = rating.takeIf { it > 0 },
                onValueChange = { rating = it ?: 0 },
                label = "Your rating",
                modifier = Modifier.padding(vertical = spacing.sm),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Anything worth remembering") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Pill(
                text = "Save watch",
                selected = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.md)
                    .testTag("saveWatch"),
                // An unrated watch is still a watch, so nothing here is required.
                onClick = { onSave(rating.takeIf { it > 0 }, note) },
            )
        }
    }
}
