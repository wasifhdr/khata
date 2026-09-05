package com.wasif.khata.feature.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.wasif.khata.core.note.Block
import com.wasif.khata.core.note.Mark
import com.wasif.khata.core.note.NoteDocument
import com.wasif.khata.core.note.Span
import com.wasif.khata.core.note.TextKind
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Rule
import org.junit.Test

class NotesScreenTest {

    @get:Rule val compose = createComposeRule()

    private val noopList = object : NotesActions {
        override fun onTogglePinned(id: Long, pinned: Boolean) = Unit
    }

    private val noopEditor = object : NoteEditorActions {
        override fun onTextChange(blockId: String, text: String) = Unit
        override fun onSplit(blockId: String, offset: Int) = Unit
        override fun onBackspaceAtStart(blockId: String) = Unit
        override fun onFocusConsumed(token: Long) = Unit
        override fun onCaretMoved(blockId: String) = Unit
        override fun onToggleMark(blockId: String, range: IntRange, mark: Mark) = Unit
        override fun onKindChange(blockId: String, kind: TextKind) = Unit
        override fun onToggleChecked(blockId: String) = Unit
        override fun onImagesPicked(uris: List<String>) = Unit
        override fun onImagePasted(bytes: ByteArray) = Unit
        override fun onImageSelected(blockId: String?) = Unit
        override fun onTogglePinned() = Unit
        override fun onDelete() = Unit
    }

    @Test
    fun the_list_shows_a_derived_title_and_snippet() {
        compose.setContent {
            KhataTheme {
                NotesContent(
                    state = NotesUiState(
                        listOf(
                            NoteCard(
                                id = 1,
                                title = "Shopping",
                                snippet = "rice, oil",
                                // 2026-08-20 12:00 Dhaka.
                                updatedAt = 1_787_205_600_000L,
                                pinned = false,
                            ),
                        ),
                    ),
                    actions = noopList,
                    onBack = {},
                    onOpenNote = {},
                    onNewNote = {},
                )
            }
        }

        compose.onNodeWithText("Shopping").assertIsDisplayed()
        compose.onNodeWithText("20 Aug 2026 · rice, oil").assertIsDisplayed()
    }

    @Test
    fun a_note_with_no_text_reads_as_untitled_rather_than_as_a_blank_row() {
        compose.setContent {
            KhataTheme {
                NotesContent(
                    state = NotesUiState(
                        listOf(NoteCard(id = 1, title = null, snippet = null, updatedAt = 1_787_205_600_000L, pinned = true)),
                    ),
                    actions = noopList,
                    onBack = {},
                    onOpenNote = {},
                    onNewNote = {},
                )
            }
        }

        compose.onNodeWithText("Untitled").assertIsDisplayed()
    }

    @Test
    fun an_empty_module_says_so_and_still_offers_the_way_in() {
        compose.setContent {
            KhataTheme {
                NotesContent(
                    state = NotesUiState(),
                    actions = noopList,
                    onBack = {},
                    onOpenNote = {},
                    onNewNote = {},
                )
            }
        }

        compose.onNodeWithText("Nothing written yet.").assertIsDisplayed()
        compose.onNodeWithText("New note").assertIsDisplayed()
    }

    @Test
    fun the_editor_renders_every_block_kind_on_the_page() {
        compose.setContent {
            KhataTheme {
                NoteEditorContent(
                    state = NoteEditorUiState(
                        document = NoteDocument(
                            listOf(
                                Block.Text("b1", TextKind.HEADING, "Shopping"),
                                Block.Text("b2", TextKind.BULLET, "rice"),
                                Block.Text("b3", TextKind.NUMBERED, "oil"),
                                Block.Text("b4", TextKind.CHECK, "eggs", checked = true),
                                Block.Text("b5", TextKind.PARAGRAPH, "bold bit", listOf(Span(0, 4, Mark.BOLD))),
                            ),
                        ),
                    ),
                    actions = noopEditor,
                    fileFor = { it },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Shopping").assertIsDisplayed()
        compose.onNodeWithText("rice").assertIsDisplayed()
        compose.onNodeWithText("oil").assertIsDisplayed()
        compose.onNodeWithText("eggs").assertIsDisplayed()
        compose.onNodeWithText("bold bit").assertIsDisplayed()
        // The gutter's checkbox, by tag: the bullet and number glyphs also appear on the
        // toolbar, so asserting on those would be asserting which of the two was found.
        compose.onNodeWithTag("check-b4").assertIsDisplayed()
    }
}
