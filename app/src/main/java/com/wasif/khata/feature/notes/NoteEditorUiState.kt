package com.wasif.khata.feature.notes

import androidx.compose.runtime.Stable
import com.wasif.khata.core.note.Block
import com.wasif.khata.core.note.Mark
import com.wasif.khata.core.note.NoteDocument
import com.wasif.khata.core.note.TextKind

/**
 * Where the caret should go, and when. A one-shot: the screen consumes it and reports back, so
 * a recomposition for any other reason does not drag the cursor away from where the user put it.
 */
data class FocusRequest(val blockId: String, val offset: Int, val token: Long)

data class NoteEditorUiState(
    val document: NoteDocument = NoteDocument(emptyList()),
    val focus: FocusRequest? = null,
    /** The block whose image is selected, if any. One more Backspace deletes it. */
    val selectedImageId: String? = null,
    /**
     * Marks armed for the next run typed, set by a formatting button with no selection.
     * Cleared when the caret moves, because an armed mark that outlives the caret is a
     * bold word appearing somewhere the user was not looking.
     */
    val pendingMarks: Set<Mark> = emptySet(),
    val pinned: Boolean = false,
) {
    val blocks: List<Block> get() = document.blocks
}

sealed interface NoteEditorEffect {
    data object Deleted : NoteEditorEffect
    data class Failed(val message: String) : NoteEditorEffect
}

@Stable
interface NoteEditorActions {
    fun onTextChange(blockId: String, text: String)
    fun onSplit(blockId: String, offset: Int)
    fun onBackspaceAtStart(blockId: String)
    fun onFocusConsumed(token: Long)
    fun onCaretMoved(blockId: String)
    fun onToggleMark(blockId: String, range: IntRange, mark: Mark)
    fun onKindChange(blockId: String, kind: TextKind)
    fun onToggleChecked(blockId: String)
    fun onImagesPicked(uris: List<String>)
    fun onImagePasted(bytes: ByteArray)
    fun onImageSelected(blockId: String?)
    fun onTogglePinned()
    fun onDelete()
}
