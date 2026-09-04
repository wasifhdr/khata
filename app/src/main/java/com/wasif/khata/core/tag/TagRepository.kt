package com.wasif.khata.core.tag

import com.wasif.khata.core.data.dao.TagDao
import com.wasif.khata.core.data.entity.TagEntity
import com.wasif.khata.core.data.entity.TagLinkEntity
import com.wasif.khata.core.search.SearchIndex
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class TagRepository @Inject constructor(
    private val dao: TagDao,
    private val searchIndex: SearchIndex,
    private val clock: KhataClock,
) {
    /**
     * Case-folded by TagDao.findByName. Rafi and rafi being two people is the failure
     * this whole table exists to avoid.
     */
    suspend fun findOrCreate(name: String): Long {
        val trimmed = name.trim()
        dao.findByName(trimmed)?.let { return it.id }
        val now = clock.now()
        return dao.upsert(
            TagEntity(
                uuid = UUID.randomUUID().toString(),
                name = trimmed,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Reindexes what the tag was attached to rather than adding a tag-search path:
     * names are part of an entity's indexed text, so one method covers ordinary
     * writes, tag changes and full rebuilds.
     */
    suspend fun attach(tagId: Long, entityType: String, entityId: Long) {
        val now = clock.now()
        val existing = dao.findLink(tagId, entityType, entityId)
        dao.upsertLink(
            TagLinkEntity(
                // Reusing the row keeps the compound unique index from rejecting a
                // re-attach, and revives one that had been detached.
                id = existing?.id ?: 0,
                uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                tagId = tagId,
                entityType = entityType,
                entityId = entityId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        searchIndex.reindex(entityType, entityId)
    }

    suspend fun detach(tagId: Long, entityType: String, entityId: Long) {
        dao.detach(tagId, entityType, entityId, clock.now())
        searchIndex.reindex(entityType, entityId)
    }

    suspend fun tagsFor(entityType: String, entityId: Long): List<TagEntity> =
        dao.tagsFor(entityType, entityId)

    fun observeAll(): Flow<List<TagEntity>> = dao.observeAll()
}
