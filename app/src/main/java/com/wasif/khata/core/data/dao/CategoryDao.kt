package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Upsert
    suspend fun upsert(entity: CategoryEntity): Long

    @Upsert
    suspend fun upsertAll(entities: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countIncludingDeleted(): Int
}
