package com.wasif.khata.feature.notes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.note.Block
import com.wasif.khata.core.note.Mark
import com.wasif.khata.core.note.Span
import com.wasif.khata.core.note.TextKind
import com.wasif.khata.core.note.clampSpans
import com.wasif.khata.core.note.numberFor
import com.wasif.khata.core.note.rulesFor
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel,
    mediaStore: MediaStore,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    NoteEditorEffect.Deleted -> onBack()
                    is NoteEditorEffect.Failed -> Unit
                }
            }
        }
    }

    NoteEditorContent(
        state = state,
        actions = viewModel,
        fileFor = { sha -> mediaStore.fileFor(sha).toURI().toString() },
        onBack = {
            // The debounce has not necessarily fired; leaving must not cost the last sentence.
            viewModel.saveNow()
            onBack()
        },
    )
}

/**
 * A page with lines on it. Every vertical dimension here is a whole number of rules, and no
 * block carries padding of its own -- a gap that is not a multiple of the rule breaks the page
 * from that point down.
 */
@Composable
fun NoteEditorContent(
    state: NoteEditorUiState,
    actions: NoteEditorActions,
    fileFor: (String) -> String,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val bodyStyle = MaterialTheme.typography.bodyLarge
    val ruleSpacing = with(LocalDensity.current) { bodyStyle.lineHeight.toDp() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> -> actions.onImagesPicked(uris.map { it.toString() }) }

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
                NavCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Row {
                    NavCircle(
                        icon = Icons.Filled.Star,
                        description = if (state.pinned) "Unpin note" else "Pin note",
                        tint = if (state.pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = actions::onTogglePinned,
                    )
                    NavCircle(
                        icon = Icons.Filled.Delete,
                        description = "Delete note",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = actions::onDelete,
                    )
                }
            }

            RuledPage(
                ruleSpacing = ruleSpacing,
                ruleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                marginColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                marginX = spacing.screenHorizontal,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    state.blocks.forEachIndexed { index, block ->
                        when (block) {
                            is Block.Text -> TextBlockRow(
                                block = block,
                                number = numberFor(state.blocks, index),
                                ruleSpacing = ruleSpacing,
                                focus = state.focus?.takeIf { it.blockId == block.id },
                                actions = actions,
                            )

                            is Block.Image -> ImageBlockRow(
                                block = block,
                                model = fileFor(block.sha256),
                                selected = state.selectedImageId == block.id,
                                ruleSpacing = ruleSpacing,
                                onClick = {
                                    actions.onImageSelected(
                                        if (state.selectedImageId == block.id) null else block.id,
                                    )
                                },
                            )
                        }
                    }
                    // Room to tap below the last line, as any notepad has.
                    Spacer(Modifier.height(ruleSpacing * 4))
                }
            }

            Toolbar(
                state = state,
                actions = actions,
                onPickImage = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}

@Composable
private fun TextBlockRow(
    block: Block.Text,
    number: Int?,
    ruleSpacing: Dp,
    focus: FocusRequest?,
    actions: NoteEditorActions,
) {
    val spacing = LocalSpacing.current
    val requester = remember(block.id) { FocusRequester() }

    // The field owns the caret; the view model owns the text. Holding the whole TextFieldValue
    // in the view model would fight the IME over the selection on every recomposition.
    var value by remember(block.id) {
        mutableStateOf(TextFieldValue(block.text, TextRange(block.text.length)))
    }
    if (value.text != block.text) {
        value = value.copy(annotatedString = AnnotatedString(block.text))
    }

    LaunchedEffect(focus?.token) {
        if (focus != null) {
            value = value.copy(selection = TextRange(focus.offset.coerceIn(0, block.text.length)))
            requester.requestFocus()
            actions.onFocusConsumed(focus.token)
        }
    }

    val style = when (block.kind) {
        TextKind.HEADING -> MaterialTheme.typography.titleLarge.copy(lineHeight = ruleSpacing.times(2).value.sp())
        else -> MaterialTheme.typography.bodyLarge
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = spacing.screenHorizontal, end = spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Gutter(block = block, number = number, ruleSpacing = ruleSpacing, onToggle = { actions.onToggleChecked(block.id) })

        BasicTextField(
            value = value,
            onValueChange = { changed ->
                val newlineAt = changed.text.indexOf('\n')
                if (newlineAt >= 0) {
                    // Enter arrives as text: the soft keyboard does not send a key event for it
                    // reliably, so the split is driven by what the field received.
                    actions.onTextChange(block.id, changed.text.replace("\n", ""))
                    actions.onSplit(block.id, newlineAt)
                } else {
                    if (changed.selection != value.selection && changed.text == value.text) {
                        actions.onCaretMoved(block.id)
                    }
                    value = changed
                    if (changed.text != block.text) actions.onTextChange(block.id, changed.text)
                }
            },
            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = { text ->
                TransformedText(
                    styled(text.text, block.spans, block.checked),
                    OffsetMapping.Identity,
                )
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(requester)
                .onFocusChanged { if (it.isFocused) actions.onImageSelected(null) }
                .onKeyEvent { event ->
                    val atStart = value.selection.collapsed && value.selection.start == 0
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && atStart) {
                        actions.onBackspaceAtStart(block.id)
                        true
                    } else {
                        false
                    }
                }
                .testTag("block-${block.id}"),
        )
    }
}

/** Bullets, numbers and checkboxes live in the margin, left of the rule the text sits on. */
@Composable
private fun Gutter(
    block: Block.Text,
    number: Int?,
    ruleSpacing: Dp,
    onToggle: () -> Unit,
) {
    if (block.kind == TextKind.PARAGRAPH || block.kind == TextKind.HEADING) return

    Box(
        Modifier
            .width(28.dp)
            .height(ruleSpacing),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (block.kind) {
            TextKind.BULLET -> Text(
                text = "•",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextKind.NUMBERED -> Text(
                text = "${number ?: 1}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextKind.CHECK -> Box(
                Modifier
                    .size(18.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onToggle)
                    .testTag("check-${block.id}"),
                contentAlignment = Alignment.Center,
            ) {
                if (block.checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun ImageBlockRow(
    block: Block.Image,
    model: String,
    selected: Boolean,
    ruleSpacing: Dp,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    // Rounded up to whole rules, so the page resumes in rhythm beneath the picture.
    val height = with(LocalDensity.current) {
        val ratio = if (block.widthPx > 0) block.heightPx.toFloat() / block.widthPx else 0.75f
        val naturalPx = (ruleSpacing.toPx() * 8) * ratio
        val rules = rulesFor(naturalPx.toInt(), ruleSpacing.toPx().toInt())
        ruleSpacing * rules
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = spacing.screenHorizontal + 28.dp, end = spacing.md)
            .height(height)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Picture in this note",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun Toolbar(
    state: NoteEditorUiState,
    actions: NoteEditorActions,
    onPickImage: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val focusedId = state.focus?.blockId ?: state.blocks.filterIsInstance<Block.Text>().firstOrNull()?.id

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // Marks with no selection arm the next run typed, which is why they show as selected.
        Mark.entries.forEach { mark ->
            Pill(
                text = when (mark) {
                    Mark.BOLD -> "B"
                    Mark.ITALIC -> "I"
                    Mark.STRIKE -> "S"
                },
                selected = mark in state.pendingMarks,
                onClick = { focusedId?.let { actions.onToggleMark(it, IntRange.EMPTY, mark) } },
            )
        }
        listOf(
            TextKind.HEADING to "H",
            TextKind.BULLET to "•",
            TextKind.NUMBERED to "1.",
            TextKind.CHECK to "☐",
        ).forEach { (kind, label) ->
            Pill(
                text = label,
                selected = false,
                onClick = { focusedId?.let { actions.onKindChange(it, kind) } },
            )
        }
        Pill(text = "Picture", selected = false, leadingIcon = Icons.Filled.Add, onClick = onPickImage)
    }
}

/** The marks, as Compose sees them. Identity offsets: nothing is added or hidden. */
private fun styled(text: String, spans: List<Span>, struckThrough: Boolean): AnnotatedString =
    AnnotatedString.Builder(text).apply {
        clampSpans(spans, text.length).forEach { span ->
            addStyle(
                when (span.mark) {
                    Mark.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Mark.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Mark.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                },
                span.start,
                span.end,
            )
        }
        // A ticked item reads as done at a glance, without needing its own colour.
        if (struckThrough) {
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), 0, text.length)
        }
    }.toAnnotatedString()

private fun Float.sp() = TextUnit(this, TextUnitType.Sp)
