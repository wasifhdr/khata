package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.MediaEntity
import com.wasif.khata.core.data.entity.MediaLinkEntity

@Dao
interface MediaDao {

    @Upsert
    suspend fun upsert(media: MediaEntity): Long

    /** Content addressing in one query: the same photo twice is one row. */
    @Query("SELECT * FROM media WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findByHash(sha256: String): MediaEntity?

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun findById(id: Long): MediaEntity?

    @Upsert
    suspend fun upsertLink(link: MediaLinkEntity)

    // Same reason as tag_links: emptied ids are handed out again.
    @Query("DELETE FROM media_links WHERE entityType = :entityType")
    suspend fun deleteLinksOfType(entityType: String)

    @Query(
        "SELECT m.* FROM media m JOIN media_links l ON l.mediaId = m.id " +
            "WHERE l.entityType = :entityType AND l.entityId = :entityId " +
            "AND l.deletedAt IS NULL AND m.deletedAt IS NULL ORDER BY l.sortOrder, l.id",
    )
    suspend fun mediaFor(entityType: String, entityId: Long): List<MediaEntity>

    @Query(
        "UPDATE media_links SET deletedAt = :now, updatedAt = :now WHERE mediaId = :mediaId " +
            "AND entityType = :entityType AND entityId = :entityId",
    )
    suspend fun detachLink(mediaId: Long, entityType: String, entityId: Long, now: Long)

    /** What MediaSync uploads from: every blob still referenced by a live row. */
    @Query("SELECT * FROM media WHERE deletedAt IS NULL")
    suspend fun allLive(): List<MediaEntity>

    @Query(
        "SELECT COUNT(*) FROM media_links WHERE mediaId = :mediaId AND entityType = :entityType " +
            "AND entityId = :entityId AND deletedAt IS NULL",
    )
    suspend fun linkCount(mediaId: Long, entityType: String, entityId: Long): Int
}
