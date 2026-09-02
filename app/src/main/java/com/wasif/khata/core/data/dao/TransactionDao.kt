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

    // ESCAPE '\' with the caller pre-escaping % and _ : an unescaped wildcard
    // turns a search that should find nothing into one that returns the whole
    // ledger, which is the worst possible answer to "find that one thing".
    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND (merchantRaw LIKE :pattern ESCAPE '\' OR note LIKE :pattern ESCAPE '\')
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun pagingSourceMatchingPattern(pattern: String): PagingSource<Int, TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: Long): TransactionEntity?

    /**
     * Backfills the category the user just confirmed onto the merchant's other
     * unreviewed rows, which is what makes confirming a merchant a one-time job
     * rather than a per-transaction one.
     *
     * Deliberately narrow: only rows Khata itself guessed and left uncertain.
     * A row with a category already on it was decided by the user or by an
     * earlier confirmation, and is never overwritten from here.
     */
    @Query(
        "UPDATE transactions SET categoryId = :categoryId, confidence = 'HIGH', updatedAt = :now " +
            "WHERE merchantId = :merchantId AND categoryId IS NULL AND confidence = 'MEDIUM' " +
            "AND source = 'SMS' AND deletedAt IS NULL"
    )
    suspend fun adoptMerchantCategory(merchantId: Long, categoryId: Long, now: Long): Int

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    // Anything other than HIGH, not just LOW: the pipeline records MEDIUM for every
    // newly-seen merchant, which is the common case for an SMS transaction.
    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL AND confidence != 'HIGH' " +
            "ORDER BY occurredAt DESC, id DESC"
    )
    fun pagingSourceNeedsAttention(): PagingSource<Int, TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND confidence != 'HIGH'")
    fun observeNeedsAttentionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND confidence != 'HIGH'")
    suspend fun countNeedsAttention(): Int

    @Query("SELECT * FROM transactions WHERE providerTxnId = :providerTxnId AND deletedAt IS NULL")
    suspend fun findByProviderTxnId(providerTxnId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY id")
    suspend fun allActive(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE rawMessageId = :rawMessageId AND deletedAt IS NULL")
    suspend fun findByRawMessageId(rawMessageId: Long): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL AND transferGroupId IS NULL " +
            "AND amountMinor = :amountMinor AND accountId != :notAccountId AND direction = :direction " +
            "AND occurredAt BETWEEN :fromMillis AND :toMillis"
    )
    suspend fun findPairCandidates(
        amountMinor: Long,
        notAccountId: Long,
        direction: TransactionDirection,
        fromMillis: Long,
        toMillis: Long,
    ): List<TransactionEntity>

    @Query(
        "UPDATE transactions SET transferGroupId = :groupId, kind = 'TRANSFER', " +
            "updatedAt = :updatedAt WHERE id IN (:ids)"
    )
    suspend fun markAsTransfer(ids: List<Long>, groupId: String, updatedAt: Long)

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

/** Escapes LIKE wildcards, then wraps in % so the term matches anywhere. */
fun TransactionDao.pagingSourceMatching(query: String): PagingSource<Int, TransactionEntity> {
    val escaped = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return pagingSourceMatchingPattern("%$escaped%")
}
