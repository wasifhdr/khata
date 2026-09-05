package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.TagEntity
import com.wasif.khata.core.data.entity.TagLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Upsert
    suspend fun upsert(tag: TagEntity): Long

    /**
     * NOCASE is the whole point: Rafi and rafi being two people is the failure this
     * table exists to avoid.
     */
    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Upsert
    suspend fun upsertLink(link: TagLinkEntity)

    @Query(
        "UPDATE tag_links SET deletedAt = :now, updatedAt = :now WHERE tagId = :tagId " +
            "AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun detach(tagId: Long, entityType: String, entityId: Long, now: Long)

    // Transaction ids restart at 1 once the table is emptied, so a link left
    // behind would re-attach itself to whatever new row lands on that id.
    @Query("DELETE FROM tag_links WHERE entityType = :entityType")
    suspend fun deleteLinksOfType(entityType: String)

    @Query(
        "SELECT t.* FROM tags t JOIN tag_links l ON l.tagId = t.id " +
            "WHERE l.entityType = :entityType AND l.entityId = :entityId " +
            "AND l.deletedAt IS NULL AND t.deletedAt IS NULL ORDER BY t.name COLLATE NOCASE",
    )
    suspend fun tagsFor(entityType: String, entityId: Long): List<TagEntity>

    @Query(
        "SELECT * FROM tag_links WHERE tagId = :tagId AND entityType = :entityType " +
            "AND entityId = :entityId LIMIT 1",
    )
    suspend fun findLink(tagId: Long, entityType: String, entityId: Long): TagLinkEntity?
}
