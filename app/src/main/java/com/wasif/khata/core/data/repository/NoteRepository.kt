package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.NoteDao
import com.wasif.khata.core.data.entity.NoteEntity
import com.wasif.khata.core.note.NoteDocument
import com.wasif.khata.core.note.NoteJson
import com.wasif.khata.core.search.SearchIndex
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The one entity type this module links against in the spine's tables. */
const val ENTITY_NOTE = "note"

@Singleton
class NoteRepository @Inject constructor(
    private val dao: NoteDao,
    private val searchIndex: SearchIndex,
    private val clock: KhataClock,
) {
    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    fun observeNote(id: Long): Flow<NoteDocument?> =
        dao.observeNote(id).map { row -> row?.let { document(it) } }

    suspend fun find(id: Long): NoteEntity? = dao.findById(id)

    suspend fun findByIds(ids: List<Long>): List<NoteEntity> = dao.findByIds(ids)

    fun document(row: NoteEntity): NoteDocument = NoteJson.decode(row.content, ::newBlockId)

    /** A new note is one empty paragraph: there has to be somewhere to put the cursor. */
    suspend fun create(): Long {
        val now = clock.now()
        return dao.upsert(
            NoteEntity(
                uuid = UUID.randomUUID().toString(),
                content = NoteJson.encode(NoteDocument.empty(::newBlockId)),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Called on a debounce rather than a keystroke -- the editor owns the timing, this owns the
     * write. Reindexing here rather than in the screen means a note saved by any path is
     * findable by every path.
     */
    suspend fun save(id: Long, document: NoteDocument) {
        val existing = dao.findById(id) ?: return
        val now = clock.now()
        dao.upsert(
            existing.copy(
                content = NoteJson.encode(document),
                updatedAt = now,
            ),
        )
        searchIndex.reindex(ENTITY_NOTE, id)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned, clock.now())

    suspend fun delete(id: Long) {
        dao.softDelete(id, clock.now())
        searchIndex.remove(ENTITY_NOTE, id)
    }

    private fun newBlockId(): String = UUID.randomUUID().toString()
}
