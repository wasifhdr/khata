package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Upsert
    suspend fun upsert(entity: AccountEntity): Long

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY name")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query(
        "UPDATE accounts SET currentBalanceMinor = currentBalanceMinor + :deltaMinor, " +
            "updatedAt = :updatedAt WHERE id = :accountId"
    )
    suspend fun adjustBalance(accountId: Long, deltaMinor: Long, updatedAt: Long)
}
