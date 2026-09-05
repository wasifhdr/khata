package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.note.Block
import com.wasif.khata.core.note.NoteDocument
import com.wasif.khata.core.note.TextKind
import com.wasif.khata.core.search.NoteIndexSource
import com.wasif.khata.core.search.ftsQuery
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.time.KhataClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {

    private lateinit var db: KhataDatabase
    private lateinit var repository: NoteRepository
    private lateinit var source: NoteIndexSource

    private var now = 1_000L
    private val clock = object : KhataClock {
        override fun now(): Long = now
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = NoteRepository(db.noteDao(), searchIndex(db), clock)
        source = NoteIndexSource(db.noteDao())
    }

    @After
    fun tearDown() = db.close()

    private fun document(vararg text: String) = NoteDocument(
        text.mapIndexed { i, line -> Block.Text(id = "b$i", text = line) },
    )

    @Test
    fun `a new note is one empty paragraph, so there is somewhere to type`() = runTest {
        val id = repository.create()

        val blocks = repository.observeNote(id).first()!!.blocks
        assertEquals(1, blocks.size)
        assertTrue((blocks.single() as Block.Text).text.isEmpty())
    }

    @Test
    fun `saving replaces the document and moves the note to the top`() = runTest {
        val first = repository.create()
        now = 2_000L
        val second = repository.create()

        now = 3_000L
        repository.save(first, document("edited last"))

        assertEquals(listOf(first, second), repository.observeAll().first().map { it.id })
        assertEquals("edited last", (repository.observeNote(first).first()!!.blocks.single() as Block.Text).text)
    }

    @Test
    fun `a pinned note sorts above a more recently edited one`() = runTest {
        val old = repository.create()
        now = 2_000L
        val recent = repository.create()

        now = 3_000L
        repository.setPinned(old, true)

        assertEquals(listOf(old, recent), repository.observeAll().first().map { it.id })
    }

    @Test
    fun `a soft-deleted note leaves the list and the index`() = runTest {
        val id = repository.create()
        repository.save(id, document("passport renewal"))

        repository.delete(id)

        assertEquals(emptyList<Long>(), repository.observeAll().first().map { it.id })
        assertEquals(emptyList<Long>(), db.noteDao().allIdsForIndex())
        assertNull(source.textFor(id))
    }

    @Test
    fun `the indexed text is the text blocks, and an image contributes nothing`() = runTest {
        val id = repository.create()
        repository.save(
            id,
            NoteDocument(
                listOf(
                    Block.Text("b1", TextKind.HEADING, "kacchi at Sultans"),
                    Block.Image("b2", "abc123def", 10, 10),
                ),
            ),
        )

        val text = source.textFor(id)!!
        assertTrue(text.contains("kacchi"))
        assertFalse(text.contains("abc123def"))
    }

    @Test
    fun `a note is findable by a word in its body`() = runTest {
        val id = repository.create()
        repository.save(id, document("renew the passport before December"))

        assertEquals(
            listOf(id),
            db.searchDao().idsMatching(ENTITY_NOTE, ftsQuery("passport")!!),
        )
    }

    @Test
    fun `an empty note indexes as nothing rather than as a blank hit`() = runTest {
        val id = repository.create()

        assertNull(source.textFor(id))
    }

    @Test
    fun `a note whose content is not json still opens, holding its raw text`() = runTest {
        val id = repository.create()
        // However it got there -- a bad restore, a hand-edited backup -- it is not lost.
        db.noteDao().upsert(db.noteDao().findById(id)!!.copy(content = "corrupted {"))

        val blocks = repository.observeNote(id).first()!!.blocks
        assertEquals("corrupted {", (blocks.single() as Block.Text).text)
    }
}
