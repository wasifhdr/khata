package com.wasif.khata.feature.notes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.repository.ENTITY_NOTE
import com.wasif.khata.core.data.repository.NoteRepository
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.note.Block
import com.wasif.khata.core.note.Mark
import com.wasif.khata.core.note.NoteDocument
import com.wasif.khata.core.note.TextKind
import com.wasif.khata.core.note.applyEdit
import com.wasif.khata.core.note.diffRange
import com.wasif.khata.core.note.insertImageAfter
import com.wasif.khata.core.note.mergeBlocks
import com.wasif.khata.core.note.splitBlock
import com.wasif.khata.core.note.toggleMark
import com.wasif.khata.core.time.KhataClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Long enough that a sentence is one write, short enough to survive a swipe away. */
const val AUTOSAVE_DELAY_MS = 600L

// Hilt needs the assisted factory named here to resolve hiltViewModel's generic <VM, VMF>
// overload; without it, injection silently falls back to a no-arg constructor and crashes.
@HiltViewModel(assistedFactory = NoteEditorViewModel.Factory::class)
class NoteEditorViewModel @AssistedInject constructor(
    private val repository: NoteRepository,
    private val mediaStore: MediaStore,
    private val clock: KhataClock,
    @Assisted private val noteId: Long,
) : ViewModel(), NoteEditorActions {

    @AssistedFactory
    interface Factory {
        fun create(noteId: Long): NoteEditorViewModel
    }

    private val _state = MutableStateFlow(NoteEditorUiState())
    val state: StateFlow<NoteEditorUiState> = _state.asStateFlow()

    private val _effects = Channel<NoteEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val document = repository.observeNote(noteId).first() ?: NoteDocument.empty(::newId)
            val pinned = repository.find(noteId)?.pinned ?: false
            _state.update { it.copy(document = document, pinned = pinned) }
        }
    }

    // -- text -------------------------------------------------------------------------------

    /**
     * The screen hands over the new text and nothing else, so the edit is recovered by diffing
     * against what was held. That cannot tell a paste from a keystroke, and does not need to:
     * spans map through one replaced range either way.
     */
    override fun onTextChange(blockId: String, text: String) {
        edit(blockId) { block ->
            val (replaced, inserted) = diffRange(block.text, text)
            val spans = applyEdit(block.spans, replaced, inserted)
            val armed = _state.value.pendingMarks
            block.copy(
                text = text,
                spans = if (armed.isEmpty() || inserted == 0) {
                    spans
                } else {
                    // The armed marks apply to exactly the run just typed.
                    armed.fold(spans) { acc, mark ->
                        toggleMark(acc, replaced.first until replaced.first + inserted, mark)
                    }
                },
            )
        }
        if (_state.value.pendingMarks.isNotEmpty()) _state.update { it.copy(pendingMarks = emptySet()) }
        scheduleSave()
    }

    override fun onSplit(blockId: String, offset: Int) {
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(index) as? Block.Text ?: return

        // Enter on an empty list item ends the list rather than adding another empty one.
        if (block.text.isEmpty() && block.kind != TextKind.PARAGRAPH && block.kind != TextKind.HEADING) {
            replaceBlocks(blocks.toMutableList().apply { this[index] = block.copy(kind = TextKind.PARAGRAPH) })
            focusOn(block.id, 0)
            scheduleSave()
            return
        }

        val (first, second) = splitBlock(block, offset, newId())
        replaceBlocks(blocks.toMutableList().apply { this[index] = first; add(index + 1, second) })
        focusOn(second.id, 0)
        scheduleSave()
    }

    /**
     * Backspace at the very start. A non-paragraph leaves its list first -- deleting nothing --
     * and only a paragraph merges upward. An image above is selected rather than swallowed.
     */
    override fun onBackspaceAtStart(blockId: String) {
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(index) as? Block.Text ?: return

        if (block.kind != TextKind.PARAGRAPH) {
            replaceBlocks(blocks.toMutableList().apply { this[index] = block.copy(kind = TextKind.PARAGRAPH) })
            scheduleSave()
            return
        }

        when (val previous = blocks.getOrNull(index - 1)) {
            null -> Unit
            is Block.Image -> {
                if (_state.value.selectedImageId == previous.id) {
                    replaceBlocks(blocks.toMutableList().apply { removeAt(index - 1) })
                    _state.update { it.copy(selectedImageId = null) }
                    scheduleSave()
                } else {
                    _state.update { it.copy(selectedImageId = previous.id) }
                }
            }
            is Block.Text -> {
                val merged = mergeBlocks(previous, block)
                replaceBlocks(
                    blocks.toMutableList().apply {
                        this[index - 1] = merged
                        removeAt(index)
                    },
                )
                focusOn(merged.id, previous.text.length)
                scheduleSave()
            }
        }
    }

    // -- marks and kinds --------------------------------------------------------------------

    override fun onToggleMark(blockId: String, range: IntRange, mark: Mark) {
        if (range.isEmpty()) {
            // No selection: arm it for the next run typed.
            _state.update { state ->
                val armed = state.pendingMarks
                state.copy(pendingMarks = if (mark in armed) armed - mark else armed + mark)
            }
            return
        }
        edit(blockId) { it.copy(spans = toggleMark(it.spans, range, mark)) }
        scheduleSave()
    }

    override fun onKindChange(blockId: String, kind: TextKind) {
        edit(blockId) { block ->
            // Tapping the kind a block already has returns it to a paragraph, so every
            // formatting button is its own off switch.
            block.copy(kind = if (block.kind == kind) TextKind.PARAGRAPH else kind)
        }
        scheduleSave()
    }

    override fun onToggleChecked(blockId: String) {
        edit(blockId) { it.copy(checked = !it.checked) }
        scheduleSave()
    }

    override fun onCaretMoved(blockId: String) {
        if (_state.value.pendingMarks.isNotEmpty() || _state.value.selectedImageId != null) {
            _state.update { it.copy(pendingMarks = emptySet(), selectedImageId = null) }
        }
    }

    override fun onFocusConsumed(token: Long) {
        _state.update { if (it.focus?.token == token) it.copy(focus = null) else it }
    }

    // -- images -----------------------------------------------------------------------------

    override fun onImagesPicked(uris: List<String>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                val media = mediaStore.import(Uri.parse(uri), ENTITY_NOTE, noteId)
                if (media == null) {
                    _effects.trySend(NoteEditorEffect.Failed("That picture could not be read."))
                } else {
                    insertImage(media.sha256, media.widthPx, media.heightPx)
                }
            }
        }
    }

    override fun onImagePasted(bytes: ByteArray) {
        viewModelScope.launch {
            val media = mediaStore.importBytes(bytes, ENTITY_NOTE, noteId)
            if (media == null) {
                // Nothing is inserted: an empty block where a picture failed is worse than
                // the picture simply not arriving.
                _effects.trySend(NoteEditorEffect.Failed("That picture could not be read."))
            } else {
                insertImage(media.sha256, media.widthPx, media.heightPx)
            }
        }
    }

    override fun onImageSelected(blockId: String?) = _state.update { it.copy(selectedImageId = blockId) }

    private fun insertImage(sha256: String, widthPx: Int, heightPx: Int) {
        val paragraphId = newId()
        replaceBlocks(
            insertImageAfter(
                blocks = _state.value.blocks,
                focusedBlockId = _state.value.focus?.blockId,
                image = Block.Image(newId(), sha256, widthPx, heightPx),
                newParagraphId = paragraphId,
            ),
        )
        focusOn(paragraphId, 0)
        scheduleSave()
    }

    // -- the note itself --------------------------------------------------------------------

    override fun onTogglePinned() {
        viewModelScope.launch {
            val pinned = !_state.value.pinned
            repository.setPinned(noteId, pinned)
            _state.update { it.copy(pinned = pinned) }
        }
    }

    override fun onDelete() {
        viewModelScope.launch {
            saveJob?.cancel()
            repository.delete(noteId)
            _effects.trySend(NoteEditorEffect.Deleted)
        }
    }

    /** The file a picture block draws from, resolved by its content hash. */
    fun fileFor(sha256: String): String = mediaStore.fileFor(sha256).toURI().toString()

    /** Leaving the screen must not lose the last few hundred milliseconds of typing. */
    fun saveNow() {
        saveJob?.cancel()
        val document = _state.value.document
        if (document.blocks.isEmpty()) return
        viewModelScope.launch { repository.save(noteId, document) }
    }

    // -- plumbing ---------------------------------------------------------------------------

    private fun edit(blockId: String, change: (Block.Text) -> Block.Text) {
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        val block = blocks.getOrNull(index) as? Block.Text ?: return
        replaceBlocks(blocks.toMutableList().apply { this[index] = change(block) })
    }

    private fun replaceBlocks(blocks: List<Block>) =
        _state.update { it.copy(document = NoteDocument(blocks)) }

    private fun focusOn(blockId: String, offset: Int) = _state.update {
        it.copy(focus = FocusRequest(blockId, offset, clock.now() + focusToken++))
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            repository.save(noteId, _state.value.document)
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private var focusToken = 0L
}
