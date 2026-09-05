package com.wasif.khata.feature.restaurants

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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun AddToWishlistScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onLogVisitInstead: () -> Unit,
    /** The text a share handed us, or null when this was reached from the list. */
    sharedText: String? = null,
    viewModel: AddToWishlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sharedText) { sharedText?.let(viewModel::onSharedText) }
    LaunchedEffect(Unit) { viewModel.saved.collect(onSaved) }

    AddToWishlistContent(
        state = state,
        actions = viewModel,
        onBack = onBack,
        onLogVisitInstead = onLogVisitInstead,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddToWishlistContent(
    state: AddToWishlistUiState,
    actions: AddToWishlistActions,
    onBack: () -> Unit,
    onLogVisitInstead: () -> Unit,
) {
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
                NavCircle(Icons.Filled.Close, "Close", onClick = onBack)
            }

            Box(Modifier.fillMaxWidth().padding(vertical = spacing.md)) {
                ContextHeader(
                    heading = "Add to wishlist",
                    subline = "Somewhere to try",
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionLabel("Name", top = spacing.sm)
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.nameInput,
                        onValueChange = actions::onNameChange,
                        placeholder = { Text("What is it called?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("wishlistNameField"),
                    )
                }

                state.place?.url?.let { url ->
                    SectionLabel("Link")
                    Text(
                        // Shown rather than hidden: the user should be able to see
                        // what was understood from what they shared.
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    )
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
                        modifier = Modifier.fillMaxWidth(),
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

                SectionLabel("Note")
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.noteInput,
                        onValueChange = actions::onNoteChange,
                        placeholder = { Text("Why is it worth going?") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(spacing.lg))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Pill(
                    text = "Add to wishlist",
                    selected = true,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().testTag("saveWishlist"),
                    onClick = actions::onSave,
                )
                // The default is the wishlist because a place someone sends you is
                // usually somewhere you have not been. This is the other case, one tap
                // away rather than argued about.
                Pill(
                    text = "Log a visit instead",
                    selected = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onLogVisitInstead,
                )
            }
        }
    }
}
