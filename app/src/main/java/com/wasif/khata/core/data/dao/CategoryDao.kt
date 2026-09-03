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

    /**
     * Every category, deleted ones included. This is the read for turning a stored
     * categoryId into a name or colour: a transaction filed last March under a
     * category since deleted still says what it said in March. `observeAll` is the
     * read for *offering* a choice, and the two are deliberately different questions.
     */
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAllIncludingDeleted(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    @Query("UPDATE categories SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countIncludingDeleted(): Int
}
