package com.wasif.khata.core.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.TransactionDirection
import kotlinx.coroutines.flow.Flow

/** One person, and what stands between you after everything nets out. */
data class OwedRow(
    val name: String,
    /** Positive when they owe you, negative when you owe them. */
    val netMinor: Long,
    val entries: Int,
    val lastAt: Long,
)

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

    @Query("SELECT * FROM transactions WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): TransactionEntity?

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

    // Paired rows are excluded: money that left one of your accounts and arrived
    // in another was never spent. Keyed on transferGroupId rather than on kind,
    // because a rule can only guess -- an EBL fund transfer is labelled a transfer
    // whether it went to your own wallet or to a university, and only a matching
    // opposite side proves it stayed with you.
    //
    // COALESCE, because SUM over no rows is NULL and this runs against an empty
    // database on every first launch.
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE deletedAt IS NULL
          AND direction = :direction
          AND transferGroupId IS NULL
          AND kind NOT IN ('TRANSFER', 'ADJUSTMENT', 'LENT', 'BORROWED_RETURNED', 'LOAN_REPAYMENT', 'COVERED_FOR_SOMEONE')
          AND occurredAt >= :fromInclusive
          AND occurredAt < :toExclusive
        """,
    )
    fun observeTotalMinorBetween(
        direction: TransactionDirection,
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<Long>

    /**
     * One row per person, netted. A name is grouped case-insensitively and without
     * surrounding spaces, so "rafi" and "Rafi " are one person rather than three
     * separate debts.
     */
    @Query(
        """
        SELECT TRIM(counterparty) AS name,
               SUM(CASE WHEN kind IN ('LENT', 'COVERED_FOR_SOMEONE', 'BORROWED_RETURNED') THEN amountMinor
                        WHEN kind IN ('LENT_RETURNED', 'REIMBURSEMENT', 'BORROWED') THEN -amountMinor
                        ELSE 0 END) AS netMinor,
               COUNT(*) AS entries,
               MAX(occurredAt) AS lastAt
        FROM transactions
        WHERE deletedAt IS NULL
          AND counterparty IS NOT NULL AND TRIM(counterparty) != ''
          AND kind IN ('LENT', 'COVERED_FOR_SOMEONE', 'BORROWED_RETURNED', 'LENT_RETURNED', 'REIMBURSEMENT', 'BORROWED')
        GROUP BY LOWER(TRIM(counterparty))
        HAVING netMinor != 0
        ORDER BY ABS(netMinor) DESC
        """,
    )
    fun observeOwedByPerson(): Flow<List<OwedRow>>

    /** Loans and covered bills with nobody's name on them yet. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND (counterparty IS NULL OR TRIM(counterparty) = '')
          AND kind IN ('LENT', 'COVERED_FOR_SOMEONE', 'BORROWED_RETURNED', 'LENT_RETURNED', 'REIMBURSEMENT', 'BORROWED')
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun observeUnnamedOwed(): Flow<List<TransactionEntity>>

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
        WHERE deletedAt IS NULL AND direction = 'DEBIT' AND transferGroupId IS NULL
          AND kind NOT IN ('TRANSFER', 'ADJUSTMENT', 'LENT', 'BORROWED_RETURNED', 'LOAN_REPAYMENT', 'COVERED_FOR_SOMEONE')
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
