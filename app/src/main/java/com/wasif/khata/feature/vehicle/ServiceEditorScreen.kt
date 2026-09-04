package com.wasif.khata.feature.vehicle

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.HazeState
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
fun ServiceEditorScreen(
    onDone: () -> Unit,
    viewModel: ServiceEditorViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    ServiceEditorEffect.Saved, ServiceEditorEffect.Deleted -> onDone()
                }
            }
        }
    }

    ServiceEditorContent(state = state, actions = viewModel, onBack = onDone)
}

/**
 * The screen that records a job, in the order a workshop bill is read: when, at what
 * reading, where, what it came to, and what was actually done. Nothing is required
 * but the date -- a receipt you no longer have is still a repair that happened.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServiceEditorContent(
    state: ServiceEditorUiState,
    actions: ServiceEditorActions,
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
                        description = "Delete service",
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
                SectionLabel("When", top = spacing.sm)
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    Pill(
                        text = state.servicedAt.toDhakaLocalDate().format(DayFormat),
                        selected = false,
                        onClick = { pickingDate = true },
                    )
                }

                SectionLabel("Odometer")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.odometerInput,
                        onValueChange = actions::onOdometerChange,
                        placeholder = { Text("Reading on the dash") },
                        suffix = { Text("km") },
                        singleLine = true,
                        isError = state.odometerHasError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("odometerField"),
                    )
                    if (state.odometerHasError) {
                        Text(
                            text = "Whole kilometres, like 42000",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                }

                SectionLabel("Workshop")
                Box(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    OutlinedTextField(
                        value = state.workshopInput,
                        onValueChange = actions::onWorkshopChange,
                        placeholder = { Text("Who did the work?") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().testTag("workshopField"),
                    )
                }
                if (state.suggestions.isNotEmpty()) {
                    // A LIKE prefix over a short list, most recently used first -- not
                    // FTS, which would answer fuzzily in a field where the user is
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

                SectionLabel("What it cost")
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
                        // C2 and C3, both stated where they would otherwise surprise.
                        text = "The bill total. Item costs below are detail, not its source.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }

                SectionLabel("What was done")
                state.items.forEachIndexed { index, item ->
                    ItemRowEditor(
                        item = item,
                        onName = { actions.onItemNameChange(index, it) },
                        onCost = { actions.onItemCostChange(index, it) },
                        onRemove = { actions.onItemRemoved(index) },
                    )
                }
                Box(Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm)) {
                    Pill(
                        text = "Add an item",
                        selected = false,
                        leadingIcon = Icons.Filled.Add,
                        onClick = actions::onItemAdded,
                    )
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
                        placeholder = { Text("What it was doing, what they said") },
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
                    text = if (state.isEditing) "Save changes" else "Save service",
                    selected = true,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().testTag("saveService"),
                    onClick = actions::onSave,
                )
            }
        }
    }

    if (pickingDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = state.servicedAt)
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
private fun ItemRowEditor(
    item: ItemRow,
    onName: (String) -> Unit,
    onCost: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = item.name,
            onValueChange = onName,
            placeholder = { Text("Part or repair") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = item.costInput,
            onValueChange = onCost,
            placeholder = { Text("৳") },
            singleLine = true,
            isError = item.costHasError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .padding(start = spacing.xs)
                .width(112.dp),
        )
        NavCircle(
            icon = Icons.Filled.Close,
            description = "Remove item",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onRemove,
        )
    }
}

/**
 * The same grid the visit editor uses, and for the same reason: nothing is drawn on
 * top of a photograph, because contrast against an arbitrary image cannot be reasoned
 * about in advance.
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
                                contentDescription = "Photo of this service",
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
