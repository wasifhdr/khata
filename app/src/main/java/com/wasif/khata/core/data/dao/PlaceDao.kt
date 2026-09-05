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

    /**
     * Workshop autocomplete: a LIKE prefix over a short list, most recently used
     * first -- not FTS, which returns fuzzier results in a field where the user is
     * trying to hit one specific row. The same call the restaurant name field makes.
     */
    @Query(
        """
        SELECT * FROM places
        WHERE deletedAt IS NULL AND name LIKE :prefix || '%' COLLATE NOCASE
        ORDER BY updatedAt DESC, name COLLATE NOCASE
        LIMIT 8
        """,
    )
    suspend fun namesLike(prefix: String): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): PlaceEntity?


    /** Newest first: FTS4 has no relevance ranking, so recency is the ordering. */
    @Query("SELECT * FROM places WHERE id IN (:ids) AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun findByIds(ids: List<Long>): List<PlaceEntity>
}
