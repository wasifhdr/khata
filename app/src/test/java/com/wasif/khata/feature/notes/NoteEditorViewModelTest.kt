package com.wasif.khata.feature.notes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.repository.NoteRepository
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.note.Block
import com.wasif.khata.core.note.Mark
import com.wasif.khata.core.note.NoteDocument
import com.wasif.khata.core.note.Span
import com.wasif.khata.core.note.TextKind
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NoteEditorViewModelTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: NoteRepository
    private lateinit var mediaStore: MediaStore

    private val dispatcher = StandardTestDispatcher()
    private val clock = object : KhataClock {
        override fun now(): Long = 1_700_000_000_000L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        repository = NoteRepository(db.noteDao(), searchIndex(db), clock)
        mediaStore = MediaStore(ApplicationProvider.getApplicationContext(), db.mediaDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun editorFor(document: NoteDocument? = null): NoteEditorViewModel {
        val id = repository.create()
        document?.let { repository.save(id, it) }
        val vm = NoteEditorViewModel(repository, mediaStore, clock, id)
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    private fun NoteEditorViewModel.texts() =
        state.value.blocks.filterIsInstance<Block.Text>().map { it.text }

    private fun NoteEditorViewModel.first() = state.value.blocks.first() as Block.Text

    private fun doc(vararg blocks: Block.Text) = NoteDocument(blocks.toList())

    @Test
    fun `enter mid-line splits the block and moves focus to the new one`() = runTest {
        val vm = editorFor(doc(Block.Text("b1", text = "hello world")))

        vm.onSplit("b1", offset = 5)

        assertEquals(listOf("hello", " world"), vm.texts())
        val focus = vm.state.value.focus!!
        assertEquals(0, focus.offset)
        assertEquals(vm.state.value.blocks[1].id, focus.blockId)
    }

    @Test
    fun `enter on an empty bullet ends the list rather than adding another`() = runTest {
        val vm = editorFor(doc(Block.Text("b1", TextKind.BULLET, "")))

        vm.onSplit("b1", offset = 0)

        assertEquals(1, vm.state.value.blocks.size)
        assertEquals(TextKind.PARAGRAPH, vm.first().kind)
    }

    @Test
    fun `backspace at the start of a bullet converts it to a paragraph and deletes nothing`() = runTest {
        val vm = editorFor(doc(Block.Text("b1", text = "above"), Block.Text("b2", TextKind.BULLET, "milk")))

        vm.onBackspaceAtStart("b2")

        assertEquals(listOf("above", "milk"), vm.texts())
        assertEquals(TextKind.PARAGRAPH, (vm.state.value.blocks[1] as Block.Text).kind)
    }

    @Test
    fun `backspace at the start of a paragraph merges it into the block above`() = runTest {
        val vm = editorFor(
            doc(
                Block.Text("b1", text = "hello"),
                Block.Text("b2", text = " world", spans = listOf(Span(1, 6, Mark.BOLD))),
            ),
        )

        vm.onBackspaceAtStart("b2")

        assertEquals(listOf("hello world"), vm.texts())
        assertEquals(listOf(Span(6, 11, Mark.BOLD)), vm.first().spans)
        assertEquals(5, vm.state.value.focus!!.offset)
    }

    @Test
    fun `backspace before an image selects it, and only a second press deletes it`() = runTest {
        val vm = editorFor(
            NoteDocument(
                listOf(
                    Block.Text("b1", text = "above"),
                    Block.Image("img", "abc", 100, 100),
                    Block.Text("b3", text = ""),
                ),
            ),
        )

        vm.onBackspaceAtStart("b3")
        assertEquals("img", vm.state.value.selectedImageId)
        assertEquals(3, vm.state.value.blocks.size)

        vm.onBackspaceAtStart("b3")
        assertEquals(2, vm.state.value.blocks.size)
        assertNull(vm.state.value.selectedImageId)
    }

    @Test
    fun `bold with a selection marks it`() = runTest {
        val vm = editorFor(doc(Block.Text("b1", text = "hello world")))

        vm.onToggleMark("b1", 0 until 5, Mark.BOLD)

        assertEquals(listOf(Span(0, 5, Mark.BOLD)), vm.first().spans)
        assertTrue(vm.state.value.pendingMarks.isEmpty())
    }

    @Test
    fun `bold with a caret arms the next run typed, and only that run`() = runTest {
        val vm = editorFor(doc(Block.Text("b1", text = "say ")))

        vm.onToggleMark("b1", 4 until 4, Mark.BOLD)
        assertEquals(setOf(Mark.BOLD), vm.state.value.pendingMarks)

        vm.onTextChange("b1", "say this")
        assertEquals(listOf(Span(4, 8, Mark.BOLD)), vm.first().spans)

        // Disarmed: the words after it are not bold too.
        vm.onTextChange("b1", "say this and that")
        assertEquals(listOf(Span(4, 8, Mark.BOLD)), vm.first().spans)
    }

    @Test
    fun `moving the caret disarms a pending mark`() = runTest {
        val vm = editorFor(doc(Block.Text("b1", text = "hello")))

        vm.onToggleMark("b1", 5 until 5, Mark.ITALIC)
        vm.onCaretMoved("b1")

        assertTrue(vm.state.value.pendingMarks.isEmpty())
    }

    @Test
    fun `tapping a kind the block already has returns it to a paragraph`() = runTest {
        val vm = editorFor(doc(Block.Text("b1", text = "milk")))

        vm.onKindChange("b1", TextKind.BULLET)
        assertEquals(TextKind.BULLET, vm.first().kind)

        vm.onKindChange("b1", TextKind.BULLET)
        assertEquals(TextKind.PARAGRAPH, vm.first().kind)
    }

    @Test
    fun `autosave writes once per debounce, not once per keystroke`() = runTest {
        val vm = editorFor(doc(Block.Text("b1", text = "")))

        "hello".forEachIndexed { i, _ -> vm.onTextChange("b1", "hello".take(i + 1)) }
        // Nothing written yet: the debounce has not elapsed.
        assertEquals("", storedText(vm))

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("hello", storedText(vm))
    }

    @Test
    fun `leaving the screen saves what the debounce has not yet written`() = runTest {
        val vm = editorFor(doc(Block.Text("b1", text = "")))

        vm.onTextChange("b1", "unsaved")
        vm.saveNow()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("unsaved", storedText(vm))
    }

    // Image insertion is split between two places that can each be tested honestly: where a
    // picture lands is insertImageAfter in BlockEditTest, and what happens to bytes that will
    // not decode is MediaStoreImportTest. Asserting either through this view model would mean
    // waiting on Dispatchers.IO, which this test's scheduler does not drive -- and a test that
    // asserts "nothing was inserted" while the import is still running passes for the wrong
    // reason.

    private suspend fun storedText(vm: NoteEditorViewModel): String {
        val row = db.noteDao().findById(1L)!!
        return repository.document(row).blocks
            .filterIsInstance<Block.Text>()
            .joinToString("") { it.text }
    }

}
