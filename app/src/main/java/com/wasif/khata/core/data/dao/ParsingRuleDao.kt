package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParsingRuleDao {

    @Upsert
    suspend fun upsertAll(entities: List<ParsingRuleEntity>)

    @Query("SELECT * FROM parsing_rules WHERE isEnabled = 1 AND deletedAt IS NULL ORDER BY priority")
    suspend fun enabled(): List<ParsingRuleEntity>

    @Query("SELECT * FROM parsing_rules WHERE deletedAt IS NULL ORDER BY priority")
    fun observeAll(): Flow<List<ParsingRuleEntity>>

    @Query("SELECT * FROM parsing_rules WHERE deletedAt IS NULL ORDER BY priority")
    suspend fun allIncludingDisabled(): List<ParsingRuleEntity>

    @Query("SELECT COUNT(*) FROM parsing_rules")
    suspend fun countIncludingDeleted(): Int
}
