package com.wasif.khata.feature.notes

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.wasif.khata.core.data.repository.ENTITY_NOTE
import com.wasif.khata.core.data.repository.NoteRepository
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.note.Block
import com.wasif.khata.core.note.NoteDocument
import com.wasif.khata.core.note.snippetOf
import com.wasif.khata.core.note.titleOf
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A note as a row draws it. Title and snippet are derived, so neither can go stale. */
data class NoteCard(
    val id: Long,
    val title: String?,
    val snippet: String?,
    val updatedAt: Long,
    val pinned: Boolean,
)

data class NotesUiState(val notes: List<NoteCard> = emptyList()) {
    val isEmpty: Boolean get() = notes.isEmpty()
}

@Stable
interface NotesActions {
    fun onTogglePinned(id: Long, pinned: Boolean)
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val mediaStore: MediaStore,
) : ViewModel(), NotesActions {

    val state: StateFlow<NotesUiState> = repository.observeAll()
        .map { rows ->
            NotesUiState(
                rows.map { row ->
                    val document = repository.document(row)
                    NoteCard(
                        id = row.id,
                        title = titleOf(document),
                        snippet = snippetOf(document),
                        updatedAt = row.updatedAt,
                        pinned = row.pinned,
                    )
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState(),
        )

    /** A new note exists before the editor opens: the editor edits, it does not create. */
    suspend fun create(): Long = repository.create()

    /**
     * A picture shared in from another app. The note is created either way -- a share that
     * arrives with an unreadable image still opens somewhere to write, rather than nowhere.
     */
    suspend fun createWithImage(uri: String): Long {
        val id = repository.create()
        val media = mediaStore.import(Uri.parse(uri), ENTITY_NOTE, id) ?: return id
        repository.save(
            id,
            NoteDocument(
                listOf(
                    Block.Image(UUID.randomUUID().toString(), media.sha256, media.widthPx, media.heightPx),
                    Block.Text(UUID.randomUUID().toString()),
                ),
            ),
        )
        return id
    }

    override fun onTogglePinned(id: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }
}
