package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.model.RawMessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RawMessageDao {

    // IGNORE rather than REPLACE: re-scanning the inbox must not renumber rows that
    // transactions already reference by rawMessageId.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicate(entity: RawMessageEntity): Long

    @Query("SELECT * FROM raw_messages WHERE id = :id")
    suspend fun findById(id: Long): RawMessageEntity?

    @Query("SELECT * FROM raw_messages WHERE status = 'PENDING' AND deletedAt IS NULL ORDER BY receivedAt LIMIT :limit")
    suspend fun pendingBatch(limit: Int): List<RawMessageEntity>

    @Query("SELECT * FROM raw_messages WHERE status = 'PENDING' AND deletedAt IS NULL ORDER BY receivedAt")
    fun observePending(): Flow<List<RawMessageEntity>>

    @Query("SELECT * FROM raw_messages WHERE deletedAt IS NULL ORDER BY receivedAt")
    suspend fun allForReparse(): List<RawMessageEntity>

    // Newest first: a format that broke recently is the one worth a rule now.
    @Query(
        "SELECT * FROM raw_messages WHERE status = :status AND deletedAt IS NULL " +
            "ORDER BY receivedAt DESC"
    )
    fun observeByStatus(status: RawMessageStatus): Flow<List<RawMessageEntity>>

    @Query("UPDATE raw_messages SET status = :status, matchedRuleId = :ruleId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markStatus(id: Long, status: RawMessageStatus, ruleId: Long?, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM raw_messages WHERE status = :status AND deletedAt IS NULL")
    suspend fun countByStatus(status: RawMessageStatus): Int
}
