package com.wasif.khata.feature.notes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.repository.NoteRepository
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.note.Block
import com.wasif.khata.core.note.NoteDocument
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class NotesViewModelTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: NoteRepository
    private lateinit var viewModel: NotesViewModel

    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000L
    private val clock = object : KhataClock {
        override fun now(): Long = now
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
        viewModel = NotesViewModel(
            repository,
            MediaStore(ApplicationProvider.getApplicationContext(), db.mediaDao(), clock),
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Collected, because a WhileSubscribed StateFlow computes nothing unobserved. */
    private fun stateAfterIdle(): NotesUiState {
        val job = CoroutineScope(dispatcher).launch { viewModel.state.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        val value = viewModel.state.value
        job.cancel()
        return value
    }

    @Test
    fun `an empty module says so rather than showing a blank row`() = runTest {
        assertTrue(stateAfterIdle().isEmpty)
    }

    @Test
    fun `the row's title and snippet are derived from the first two lines`() = runTest {
        val id = repository.create()
        repository.save(
            id,
            NoteDocument(
                listOf(
                    Block.Text("b1", text = "Shopping"),
                    Block.Text("b2", text = "rice, oil"),
                ),
            ),
        )

        val card = stateAfterIdle().notes.single()
        assertEquals("Shopping", card.title)
        assertEquals("rice, oil", card.snippet)
    }

    @Test
    fun `a note that opens with a picture has no title, and the row says Untitled for it`() = runTest {
        val id = repository.create()
        repository.save(id, NoteDocument(listOf(Block.Image("b1", "abc", 10, 10))))

        assertNull(stateAfterIdle().notes.single().title)
    }

    @Test
    fun `pinned notes sort above more recently edited ones`() = runTest {
        val old = repository.create()
        now = 2_000L
        val recent = repository.create()

        now = 3_000L
        viewModel.onTogglePinned(old, true)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(old, recent), stateAfterIdle().notes.map { it.id })
    }

    @Test
    fun `creating a note makes it before the editor opens, so the editor only edits`() = runTest {
        val id = viewModel.create()

        assertEquals(listOf(id), stateAfterIdle().notes.map { it.id })
    }

    @Test
    fun `a share whose picture cannot be read still opens a note to write in`() = runTest {
        // The picture is the reason for the share, but losing it is not a reason to lose
        // the note as well.
        val id = viewModel.createWithImage("content://nothing/here")

        val blocks = repository.observeNote(id).first()!!.blocks
        assertTrue(blocks.none { it is Block.Image })
        assertEquals(1, blocks.size)
    }
}
