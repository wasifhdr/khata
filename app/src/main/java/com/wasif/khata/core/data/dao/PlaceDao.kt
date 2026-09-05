package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Upsert
    suspend fun upsert(place: PlaceEntity): Long

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun findById(id: Long): PlaceEntity?

    @Query("SELECT * FROM places WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT id FROM places WHERE deletedAt IS NULL")
    suspend fun allIdsForIndex(): List<Long>

    /** Newest first: FTS4 has no relevance ranking, so recency is the ordering. */
    @Query("SELECT * FROM places WHERE id IN (:ids) AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun findByIds(ids: List<Long>): List<PlaceEntity>
}
