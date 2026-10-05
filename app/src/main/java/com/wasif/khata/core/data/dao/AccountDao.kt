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

    @Query(
        "SELECT * FROM accounts WHERE deletedAt IS NULL " +
            "ORDER BY CASE WHEN type = 'CASH' THEN 0 ELSE 1 END, name COLLATE NOCASE",
    )
    fun observeAll(): Flow<List<AccountEntity>>

    @Query(
        "SELECT * FROM accounts WHERE deletedAt IS NULL " +
            "ORDER BY CASE WHEN type = 'CASH' THEN 0 ELSE 1 END, name COLLATE NOCASE",
    )
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun countIncludingDeleted(): Int

    @Query(
        "UPDATE accounts SET currentBalanceMinor = currentBalanceMinor + :deltaMinor, " +
            "updatedAt = :updatedAt WHERE id = :accountId"
    )
    suspend fun adjustBalance(accountId: Long, deltaMinor: Long, updatedAt: Long)

    /**
     * Takes the balance a message states as fact: the running total is set to it,
     * and whatever it disagreed with is banked as unexplained.
     *
     * Guarded on [at] so an out-of-order or re-read message cannot drag the balance
     * back to an older figure — messages do not always arrive in the order the
     * transactions happened, and reparse walks history from the beginning.
     */
    @Query(
        "UPDATE accounts SET " +
            // The very first statement is not a disagreement -- it is the first time
            // Khata learns what the account already held. That difference is the
            // opening balance, not money that went missing.
            "openingBalanceMinor = openingBalanceMinor + (CASE WHEN reportedBalanceAt IS NULL " +
            "THEN :balanceMinor - currentBalanceMinor ELSE 0 END), " +
            "unexplainedMinor = unexplainedMinor + (CASE WHEN reportedBalanceAt IS NULL " +
            "THEN 0 ELSE :balanceMinor - currentBalanceMinor END), " +
            "currentBalanceMinor = :balanceMinor, " +
            "reportedBalanceMinor = :balanceMinor, reportedBalanceAt = :at, updatedAt = :now " +
            "WHERE id = :accountId AND (reportedBalanceAt IS NULL OR reportedBalanceAt <= :at)"
    )
    suspend fun applyStatedBalance(accountId: Long, balanceMinor: Long, at: Long, now: Long)

    /**
     * Forgets everything Khata worked out for this account and takes [balanceMinor]
     * as the whole truth: it is the opening balance, the current balance and the
     * last statement all at once, because after a start over there are no
     * transactions left for it to be the sum of.
     *
     * [at] guards later messages the same way [applyStatedBalance] does -- anything
     * that happened at or before the statement this balance came from is already
     * inside it.
     */
    @Query(
        "UPDATE accounts SET openingBalanceMinor = :balanceMinor, " +
            "currentBalanceMinor = :balanceMinor, reportedBalanceMinor = :balanceMinor, " +
            "reportedBalanceAt = :at, unexplainedMinor = 0, updatedAt = :now WHERE id = :accountId"
    )
    suspend fun startOverAt(accountId: Long, balanceMinor: Long, at: Long, now: Long)

    @Query("UPDATE accounts SET unexplainedMinor = 0, updatedAt = :now WHERE id = :accountId")
    suspend fun clearUnexplained(accountId: Long, now: Long)

    @Query(
        """
        SELECT COALESCE(SUM(currentBalanceMinor), 0) FROM accounts
        WHERE deletedAt IS NULL AND includeInNetWorth = 1
        """,
    )
    fun observeNetWorthMinor(): Flow<Long>
}
