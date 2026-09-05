package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    fun observeNote(id: Long): Flow<NoteEntity?>

    /** Pinned first, then most recently edited -- the whole of this module's organisation. */
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id IN (:ids) AND deletedAt IS NULL ORDER BY updatedAt DESC")
    suspend fun findByIds(ids: List<Long>): List<NoteEntity>

    @Query("SELECT id FROM notes WHERE deletedAt IS NULL")
    suspend fun allIdsForIndex(): List<Long>

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}
