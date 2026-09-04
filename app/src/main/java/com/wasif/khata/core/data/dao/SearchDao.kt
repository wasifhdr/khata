package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wasif.khata.core.data.entity.SearchFtsEntity

@Dao
interface SearchDao {

    @Insert
    suspend fun insert(row: SearchFtsEntity)

    @Query("DELETE FROM search_fts WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun delete(entityType: String, entityId: Long)

    @Query("DELETE FROM search_fts WHERE entityType = :entityType")
    suspend fun deleteAll(entityType: String)

    @Query("SELECT entityId FROM search_fts WHERE entityType = :entityType AND text MATCH :query")
    suspend fun idsMatching(entityType: String, query: String): List<Long>
}
