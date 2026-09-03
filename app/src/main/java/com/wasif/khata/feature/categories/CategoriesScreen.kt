package com.wasif.khata.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.CategoryDot
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.domain.model.Category
import dev.chrisbanes.haze.hazeSource

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoriesContent(state = state, actions = viewModel, onBack = onBack)
}

@Composable
fun CategoriesContent(
    state: CategoriesUiState,
    actions: CategoriesActions,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val scroll = rememberScrollState()

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {

            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    .verticalScroll(scroll)
                    .padding(top = CollapsingHeaderHeight, bottom = spacing.xxl)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    Pill(
                        text = "Add a category",
                        selected = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = actions::onAddRequested,
                    )
                }

                SectionLabel("Categories", top = spacing.lg)
                state.categories.forEach { category ->
                    CategoryRow(
                        category = category,
                        onEdit = { actions.onEditRequested(category) },
                        onDelete = { actions.onDeleteRequested(category) },
                    )
                }
            }

            CollapsingTopBar(
                heading = "Categories",
                subline = "${state.categories.size} in use",
                collapse = scroll.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )
        }
    }

    state.editing?.let { editing -> EditDialog(editing, actions) }
    state.pendingDelete?.let { target -> DeleteDialog(target, actions) }
}

@Composable
private fun CategoryRow(category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(token = category.colorToken)
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
        // The one row that cannot go offers no delete. The repository refuses it
        // regardless -- hiding an action and guaranteeing it are different jobs.
        if (!category.isSystem) {
            Text(
                text = "Delete",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(spacing.xs),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditDialog(editing: CategoriesUiState.Editing, actions: CategoriesActions) {
    val spacing = LocalSpacing.current
    AlertDialog(
        onDismissRequest = actions::onEditDismissed,
        title = { Text(if (editing.isNew) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                OutlinedTextField(
                    value = editing.name,
                    onValueChange = actions::onEditName,
                    label = { Text("Name") },
                    singleLine = true,
                )
                // A fixed set, never a free picker: category colours encode data
                // rather than taste, and an arbitrary one could fail the contrast
                // floor over the field.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    KhataPalette.categories.keys.forEach { token ->
                        Box(
                            Modifier
                                .size(spacing.minTouchTarget)
                                .clip(CircleShape)
                                .clickable { actions.onEditColour(token) },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Selection is a ring around the dot, not a colour change:
                            // the dot's own colour is the thing being chosen, so it
                            // cannot also carry the selected state.
                            Box(
                                Modifier
                                    .size(if (token == editing.colorToken) 28.dp else 20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (token == editing.colorToken) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                CategoryDot(token = token)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions::onEditSaved, enabled = editing.canSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = actions::onEditDismissed) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteDialog(target: Category, actions: CategoriesActions) {
    AlertDialog(
        onDismissRequest = actions::onDeleteDismissed,
        title = { Text("Delete ${target.name}?") },
        text = {
            Text(
                "It stops being offered for new entries. Transactions already filed " +
                    "under it keep the name, so past months read exactly as they do now.",
            )
        },
        confirmButton = {
            TextButton(onClick = actions::onDeleteConfirmed) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = actions::onDeleteDismissed) { Text("Keep") }
        },
    )
}
