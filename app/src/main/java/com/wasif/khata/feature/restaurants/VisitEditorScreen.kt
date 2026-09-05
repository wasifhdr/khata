package com.wasif.khata.feature.restaurants

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.component.StarRating
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.HazeState
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
fun VisitEditorScreen(
    onDone: () -> Unit,
    viewModel: VisitEditorViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    VisitEditorEffect.Saved, VisitEditorEffect.Deleted -> onDone()
                }
            }
        }
    }

    VisitEditorContent(state = state, actions = viewModel, onBack = onDone)
}

/**
 * The screen that has to be quick. Fields in the order dinner is remembered in --
 * where, when, how it felt, what it cost, what was eaten, who was there -- and a save
 * that never asks for more than the name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VisitEditorContent(
    state: VisitEditorUiState,
    actions: VisitEditorActions,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var pickingDate by remember { mutableStateOf(false) }

    // PickMultipleVisualMedia on minSdk 33 needs no permission at all: the picker runs
    // in its own process and hands back a URI already granted.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> -> actions.onPhotosPicked(uris.map { it.toString() }) }

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
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
                if (state.isEditing) {
                    NavCircle(
                        icon = Icons.Filled.Delete,
                        description = "Delete visit",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = actions::onDelete,
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionLabel("Restaurant", top = spacing.sm)
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.nameInput,
                        onValueChange = actions::onNameChange,
                        placeholder = { Text("Where did you eat?") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().testTag("restaurantNameField"),
                    )
                }
                if (state.suggestions.isNotEmpty()) {
                    // A LIKE prefix over a short list, most recently visited first --
                    // not FTS, which would answer fuzzily in a field where the user is
                    // trying to hit one specific row.
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        state.suggestions.forEach { name ->
                            Pill(
                                text = name,
                                selected = false,
                                onClick = { actions.onSuggestionPicked(name) },
                            )
                        }
                    }
                }

                SectionLabel("When")
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    Pill(
                        text = state.visitedAt.toDhakaLocalDate().format(DayFormat),
                        selected = false,
                        onClick = { pickingDate = true },
                    )
                }

                SectionLabel("Ambiance")
                StarRating(
                    value = state.ambianceRating,
                    onValueChange = actions::onAmbianceChange,
                    label = "Ambiance",
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal - spacing.sm),
                )

                SectionLabel("Cost")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.costInput,
                        onValueChange = actions::onCostChange,
                        placeholder = { Text("০") },
                        prefix = { Text("৳") },
                        singleLine = true,
                        isError = state.costHasError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("costField"),
                    )
                    if (state.costHasError) {
                        Text(
                            text = "Enter an amount like 1234.56",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                    Text(
                        // R6, stated where it would otherwise be a surprise.
                        text = "Not linked to the wallet. A meal someone else paid for still cost something.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }

                SectionLabel("Dishes")
                state.dishes.forEachIndexed { index, dish ->
                    DishRowEditor(
                        dish = dish,
                        onName = { actions.onDishNameChange(index, it) },
                        onRating = { actions.onDishRatingChange(index, it) },
                        onRemove = { actions.onDishRemoved(index) },
                    )
                }
                Box(Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm)) {
                    Pill(
                        text = "Add a dish",
                        selected = false,
                        leadingIcon = Icons.Filled.Add,
                        onClick = actions::onDishAdded,
                    )
                }

                SectionLabel("Who was there")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.companionInput,
                        onValueChange = actions::onCompanionInputChange,
                        placeholder = { Text("A name, then add") },
                        singleLine = true,
                        trailingIcon = {
                            NavCircle(
                                icon = Icons.Filled.Add,
                                description = "Add companion",
                                onClick = actions::onCompanionAdded,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("companionField"),
                    )
                    if (state.companions.isNotEmpty()) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(top = spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            state.companions.forEach { name ->
                                Pill(
                                    text = name,
                                    selected = true,
                                    contentDescription = "Remove $name",
                                    onClick = { actions.onCompanionRemoved(name) },
                                )
                            }
                        }
                    }
                }

                SectionLabel("Photos")
                PhotoGrid(
                    haze = haze,
                    photos = state.photos,
                    onAdd = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRemove = actions::onPhotoRemoved,
                )

                SectionLabel("Notes")
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.noteInput,
                        onValueChange = actions::onNoteChange,
                        placeholder = { Text("Anything worth remembering") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
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

            // Pinned rather than scrolled to: the one control that ends the screen
            // belongs where the thumb already is.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            ) {
                Pill(
                    text = if (state.isEditing) "Save changes" else "Save visit",
                    selected = true,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().testTag("saveVisit"),
                    onClick = actions::onSave,
                )
            }
        }
    }

    if (pickingDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = state.visitedAt)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let(actions::onDateChange)
                        pickingDate = false
                    },
                ) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun DishRowEditor(
    dish: DishRow,
    onName: (String) -> Unit,
    onRating: (Int?) -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = dish.name,
                onValueChange = onName,
                placeholder = { Text("What did you have?") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            NavCircle(
                icon = Icons.Filled.Close,
                description = "Remove dish",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onRemove,
            )
        }
        StarRating(
            value = dish.rating,
            onValueChange = onRating,
            label = dish.name.ifBlank { "Dish" },
            // Smaller than the ambiance control because there is one of these per
            // dish, and still a full 44dp target.
            target = 44.dp,
            star = 22.dp,
        )
    }
}

/**
 * A photo grid over glass, which is the one surface in this app that sits over
 * something arbitrary rather than over the field.
 *
 * Nothing is drawn on top of a photograph: the remove control sits in a filled circle
 * of its own and every label lives outside the thumbnail. Contrast against a
 * photograph cannot be reasoned about in advance, so nothing here depends on it.
 */
@Composable
private fun PhotoGrid(
    haze: HazeState,
    photos: List<PhotoItem>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    KhataGlass(
        hazeState = haze,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
    ) {
        Column(Modifier.padding(spacing.sm)) {
            if (photos.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    photos.forEach { photo ->
                        Box {
                            AsyncImage(
                                model = photo.model,
                                contentDescription = "Photo of this visit",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(MaterialTheme.shapes.small),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(spacing.xs)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { onRemove(photo.model) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove photo",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(spacing.sm))
            }
            Pill(text = "Add photos", selected = false, leadingIcon = Icons.Filled.Add, onClick = onAdd)
        }
    }
}
