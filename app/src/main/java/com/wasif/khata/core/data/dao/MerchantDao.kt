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

    /**
     * What the user chose, remembered. Every later message from this merchant
     * arrives already categorised and HIGH, so the same choice is never asked twice.
     */
    @Query(
        "UPDATE merchants SET categoryId = :categoryId, isUserConfirmed = 1, updatedAt = :now " +
            "WHERE id = :id AND deletedAt IS NULL"
    )
    suspend fun confirmCategory(id: Long, categoryId: Long, now: Long)

    /** Alias text only: SearchIndex wants the strings, not the rows. */
    @Query("SELECT rawText FROM merchant_aliases WHERE merchantId = :merchantId AND deletedAt IS NULL")
    suspend fun aliasesFor(merchantId: Long): List<String>
}
