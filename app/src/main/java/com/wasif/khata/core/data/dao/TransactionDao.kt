package com.wasif.khata.core.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.TransactionDirection
import kotlinx.coroutines.flow.Flow

/** One row per Dhaka day that has any spending. */
data class DayTotalRow(
    val dhakaDayIndex: Long,
    val spentMinor: Long,
)

@Dao
interface TransactionDao {

    @Upsert
    suspend fun upsert(entity: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC")
    fun pagingSource(): PagingSource<Int, TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND occurredAt >= :fromInclusive
          AND occurredAt < :toExclusive
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun pagingSourceBetween(fromInclusive: Long, toExclusive: Long): PagingSource<Int, TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    // COALESCE, because SUM over no rows is NULL and this runs against an empty
    // database on every first launch.
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE deletedAt IS NULL
          AND direction = :direction
          AND occurredAt >= :fromInclusive
          AND occurredAt < :toExclusive
        """,
    )
    fun observeTotalMinorBetween(
        direction: TransactionDirection,
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<Long>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC LIMIT 1")
    fun observeMostRecent(): Flow<TransactionEntity?>

    // Grouped rather than accumulated during paging: insertSeparators only sees
    // adjacent items, so a day spanning a page boundary would be under-reported.
    // The +21600000 shifts UTC to Dhaka before the day division, which is exact
    // because Dhaka is UTC+6 year-round.
    @Query(
        """
        SELECT ((occurredAt + 21600000) / 86400000) AS dhakaDayIndex,
               SUM(amountMinor) AS spentMinor
        FROM transactions
        WHERE deletedAt IS NULL AND direction = 'DEBIT'
        GROUP BY dhakaDayIndex
        """,
    )
    fun observeDayTotals(): Flow<List<DayTotalRow>>
}
