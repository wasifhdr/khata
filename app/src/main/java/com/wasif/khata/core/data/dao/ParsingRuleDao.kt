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

    /**
     * The rule a given message already taught, if any. @Upsert resolves conflicts by
     * primary key, not by the unique uuid, so writing a stable uuid with id = 0 would
     * fail its insert and then update nothing. Callers look the row up first and carry
     * its id.
     */
    @Query("SELECT * FROM parsing_rules WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): ParsingRuleEntity?
}
