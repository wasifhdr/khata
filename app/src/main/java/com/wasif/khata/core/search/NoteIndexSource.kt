package com.wasif.khata.core.search

import com.wasif.khata.core.data.dao.NoteDao
import com.wasif.khata.core.data.repository.ENTITY_NOTE
import com.wasif.khata.core.note.NoteJson
import com.wasif.khata.core.note.plainTextOf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A note's text blocks, flattened. Image blocks contribute nothing: a photograph has no words
 * to find it by, and indexing its file name would return notes for a search matching a hash.
 */
@Singleton
class NoteIndexSource @Inject constructor(
    private val notes: NoteDao,
) : IndexSource {
    override val entityType = ENTITY_NOTE

    override suspend fun textFor(entityId: Long): String? {
        val row = notes.findById(entityId)?.takeIf { it.deletedAt == null } ?: return null
        return plainTextOf(NoteJson.decode(row.content) { UUID.randomUUID().toString() })
            .takeIf { it.isNotBlank() }
    }

    override suspend fun allIds(): List<Long> = notes.allIdsForIndex()
}
