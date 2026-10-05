package com.wasif.khata.feature.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.watch.TmdbResult
import com.wasif.khata.core.watch.posterUrl

@Composable
fun AddTitleScreen(
    onDone: () -> Unit,
    initialWatched: Boolean = false,
    viewModel: AddTitleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(initialWatched) { viewModel.onLogWatchNowChange(initialWatched) }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    AddTitleEffect.Saved -> onDone()
                }
            }
        }
    }

    AddTitleContent(state = state, actions = viewModel, onBack = onDone)
}

/**
 * A search box on top and the fields it fills in underneath, both always present. With
 * no key and no connection the box simply returns nothing and the fields are the whole
 * screen -- which is what keeps this module usable on a plane.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTitleContent(
    state: AddTitleUiState,
    actions: AddTitleActions,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NavCircle(Icons.Filled.Close, "Close", onClick = onBack)
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                SectionLabel("Search", top = spacing.sm)
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = actions::onQueryChange,
                        placeholder = { Text("A film or a series") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().testTag("tmdbSearchField"),
                    )
                }

                state.results.forEach { result ->
                    ResultRow(result) { actions.onResultPick(result) }
                }

                SectionLabel("Title")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = actions::onNameChange,
                        placeholder = { Text("What is it called?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("titleNameField"),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.yearInput,
                            onValueChange = actions::onYearChange,
                            label = { Text("Year") },
                            singleLine = true,
                            isError = state.yearHasError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(128.dp).testTag("titleYearField"),
                        )
                        Row(
                            Modifier.padding(start = spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            TitleKind.entries.forEach { kind ->
                                Pill(
                                    text = if (kind == TitleKind.FILM) "Film" else "Series",
                                    selected = state.kind == kind,
                                    onClick = { actions.onKindChange(kind) },
                                )
                            }
                        }
                    }
                }

                SectionLabel("Who recommended it")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.recommenderInput,
                        onValueChange = actions::onRecommenderInputChange,
                        placeholder = { Text("A name, then add") },
                        singleLine = true,
                        trailingIcon = {
                            NavCircle(
                                icon = Icons.Filled.Add,
                                description = "Add recommender",
                                onClick = actions::onRecommenderAdded,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("recommenderField"),
                    )
                    if (state.recommenders.isNotEmpty()) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(top = spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            state.recommenders.forEach { name ->
                                Pill(
                                    text = name,
                                    selected = true,
                                    contentDescription = "Remove $name",
                                    onClick = { actions.onRecommenderRemoved(name) },
                                )
                            }
                        }
                    }
                }

                SectionLabel("Notes")
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.noteInput,
                        onValueChange = actions::onNoteChange,
                        placeholder = { Text("Why you want to see it") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Box(Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.md)) {
                    Pill(
                        text = "Already watched it",
                        selected = state.logWatchNow,
                        onClick = { actions.onLogWatchNowChange(!state.logWatchNow) },
                    )
                }

                state.saveError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(
                            horizontal = spacing.screenHorizontal,
                            vertical = spacing.sm,
                        ),
                    )
                }

                Spacer(Modifier.height(spacing.lg))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            ) {
                Pill(
                    text = "Save title",
                    selected = true,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().testTag("saveTitle"),
                    onClick = actions::onSave,
                )
            }
        }
    }
}

@Composable
private fun ResultRow(result: TmdbResult, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        result.posterPath?.let { path ->
            AsyncImage(
                model = posterUrl(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 40.dp, height = 60.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        }
        Column(Modifier.weight(1f).padding(start = spacing.sm)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    result.year?.toString(),
                    if (result.kind == TitleKind.FILM) "Film" else "Series",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.rating?.let { rating ->
            // TMDB's own scale, labelled as theirs. It is never mixed with the stars
            // the user gives a watch, which run 1 to 5.
            Text(
                text = "%.1f".format(rating),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
