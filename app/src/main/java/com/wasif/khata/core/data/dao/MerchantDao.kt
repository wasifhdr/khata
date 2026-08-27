package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.MerchantAliasEntity
import com.wasif.khata.core.data.entity.MerchantEntity

@Dao
interface MerchantDao {

    @Upsert
    suspend fun upsert(entity: MerchantEntity): Long

    @Upsert
    suspend fun upsertAlias(entity: MerchantAliasEntity): Long

    @Query("SELECT * FROM merchants WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: Long): MerchantEntity?

    @Query(
        "SELECT m.* FROM merchants m JOIN merchant_aliases a ON a.merchantId = m.id " +
            "WHERE a.rawText = :rawText AND m.deletedAt IS NULL AND a.deletedAt IS NULL LIMIT 1"
    )
    suspend fun findByAlias(rawText: String): MerchantEntity?
}
