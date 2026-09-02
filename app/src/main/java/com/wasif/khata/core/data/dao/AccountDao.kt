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

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun countIncludingDeleted(): Int

    @Query(
        "UPDATE accounts SET currentBalanceMinor = currentBalanceMinor + :deltaMinor, " +
            "updatedAt = :updatedAt WHERE id = :accountId"
    )
    suspend fun adjustBalance(accountId: Long, deltaMinor: Long, updatedAt: Long)

    @Query(
        "UPDATE accounts SET reportedBalanceMinor = :balanceMinor, reportedBalanceAt = :at, " +
            "updatedAt = :at WHERE id = :accountId"
    )
    suspend fun setReportedBalance(accountId: Long, balanceMinor: Long, at: Long)

    @Query(
        """
        SELECT COALESCE(SUM(currentBalanceMinor), 0) FROM accounts
        WHERE deletedAt IS NULL AND includeInNetWorth = 1
        """,
    )
    fun observeNetWorthMinor(): Flow<Long>
}
