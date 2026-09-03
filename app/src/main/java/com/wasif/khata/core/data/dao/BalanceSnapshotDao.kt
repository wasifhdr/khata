package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasif.khata.core.data.entity.BalanceSnapshotEntity
import kotlinx.coroutines.flow.Flow

/** A day's net worth: every included account's closing balance, summed. */
data class DayTotal(val dayIndex: Long, val totalMinor: Long)

@Dao
interface BalanceSnapshotDao {

    /**
     * IGNORE, not REPLACE: a row that already exists is the balance as it was believed
     * that night, and the fill routine must not overwrite it.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<BalanceSnapshotEntity>)

    @Query("SELECT dayIndex FROM balance_snapshots WHERE accountId = :accountId AND deletedAt IS NULL")
    suspend fun existingDayIndices(accountId: Long): List<Long>

    @Query("SELECT * FROM balance_snapshots WHERE accountId = :accountId AND deletedAt IS NULL ORDER BY dayIndex")
    suspend fun rowsFor(accountId: Long): List<BalanceSnapshotEntity>

    // Only accounts that count toward net worth, which is why this joins rather than
    // summing the table alone.
    @Query(
        """
        SELECT s.dayIndex AS dayIndex, SUM(s.balanceMinor) AS totalMinor
        FROM balance_snapshots s
        JOIN accounts a ON a.id = s.accountId
        WHERE s.deletedAt IS NULL AND a.deletedAt IS NULL AND a.includeInNetWorth = 1
          AND s.dayIndex >= :fromDayIndex
        GROUP BY s.dayIndex
        ORDER BY s.dayIndex
        """,
    )
    fun observeTotalsFrom(fromDayIndex: Long): Flow<List<DayTotal>>
}
