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
)

/** One category and what went to it over a window. */
data class CategoryComparisonRow(
    val categoryId: Long?,
    val thisMonthMinor: Long,
    val lastMonthMinor: Long,
)

data class MerchantTotalRow(val merchantName: String, val totalMinor: Long)

data class AccountDayNet(val accountId: Long, val dayIndex: Long, val netMinor: Long)

data class CategoryTotalRow(
    val categoryId: Long?,
    val totalMinor: Long,
    val entries: Int,
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

    @Query(
        """
        SELECT t.* FROM transactions t
        JOIN search_fts f ON f.entityType = 'transaction' AND f.entityId = t.id
        WHERE f.text MATCH :query AND t.deletedAt IS NULL
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun pagingSourceMatchingFts(query: String): PagingSource<Int, TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: Long): TransactionEntity?

    /** Every live row, for SearchIndex.reindexAll. */
    @Query("SELECT id FROM transactions WHERE deletedAt IS NULL")
    suspend fun allIdsForIndex(): List<Long>

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
        // Confidence is not touched and not tested for: filing a merchant says nothing
        // about whether its rows parsed correctly. Testing for it here would also have
        // quietly stopped this propagating at all once confidence stopped tracking
        // categories, which is the labour-saving half of categorising anything.
        "UPDATE transactions SET categoryId = :categoryId, updatedAt = :now " +
            "WHERE merchantId = :merchantId AND categoryId IS NULL " +
            "AND source = 'SMS' AND deletedAt IS NULL"
    )
    suspend fun adoptMerchantCategory(merchantId: Long, categoryId: Long, now: Long): Int

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

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

    // Clears the review flag as well as marking the pair. A partner arriving at minute
    // five answers the question that was asked at minute three, and a stale question
    // the owner can no longer answer correctly is worse than never asking.
    @Query(
        "UPDATE transactions SET transferGroupId = :groupId, kind = 'TRANSFER', " +
            "transferReviewPending = 0, updatedAt = :updatedAt WHERE id IN (:ids)"
    )
    suspend fun markAsTransfer(ids: List<Long>, groupId: String, updatedAt: Long)

    @Query(
        "UPDATE transactions SET transferReviewPending = :pending, updatedAt = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun setReviewPending(id: Long, pending: Boolean, updatedAt: Long)

    @Query(
        "SELECT * FROM transactions WHERE transferReviewPending = 1 AND deletedAt IS NULL " +
            "ORDER BY occurredAt DESC, id DESC",
    )
    fun observePendingReviews(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE transferReviewPending = 1 AND deletedAt IS NULL")
    fun observePendingReviewCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND deletedAt IS NULL")
    suspend fun allForAccount(accountId: Long): List<TransactionEntity>

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
               COUNT(*) AS entries
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

    // Same exclusions as the spending total, or the breakdown would not add up to
    // the figure printed above it.
    @Query(
        """
        SELECT categoryId, SUM(amountMinor) AS totalMinor, COUNT(*) AS entries
        FROM transactions
        WHERE deletedAt IS NULL AND direction = 'DEBIT' AND transferGroupId IS NULL
          AND kind NOT IN ('TRANSFER', 'ADJUSTMENT', 'LENT', 'BORROWED_RETURNED', 'LOAN_REPAYMENT', 'COVERED_FOR_SOMEONE')
          AND occurredAt >= :fromInclusive AND occurredAt < :toExclusive
        GROUP BY categoryId
        ORDER BY totalMinor DESC
        """,
    )
    fun observeSpendByCategory(fromInclusive: Long, toExclusive: Long): Flow<List<CategoryTotalRow>>

    // Signed daily movement per account, which is all the walk in SnapshotWriter
    // needs. One query rather than one per day per account, which would be hundreds
    // of round trips on a multi-year ledger.
    @Query(
        """
        SELECT accountId, ((occurredAt + 21600000) / 86400000) AS dayIndex,
               SUM(CASE WHEN direction = 'CREDIT' THEN amountMinor ELSE -amountMinor END) AS netMinor
        FROM transactions
        WHERE deletedAt IS NULL
        GROUP BY accountId, dayIndex
        ORDER BY dayIndex
        """,
    )
    suspend fun dailyMovementByAccount(): List<AccountDayNet>

    // ponytail: recency only. The spec asks for "recent and frequent"; for a
    // handful of repeated cash categories both orderings converge, and this is one
    // MAX() instead of a weighting nobody can tune. Add COUNT(*) as a tiebreaker if
    // the order ever reads wrong.
    @Query(
        """
        SELECT categoryId FROM transactions
        WHERE deletedAt IS NULL AND accountId = :accountId AND categoryId IS NOT NULL
        GROUP BY categoryId
        ORDER BY MAX(occurredAt) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentCategoryIds(accountId: Long, limit: Int): Flow<List<Long>>

    // Both months in one pass. Two queries subtracted in the UI would silently drop a
    // category that exists in only one of them, which is exactly the interesting case.
    @Query(
        """
        SELECT categoryId,
               COALESCE(SUM(CASE WHEN occurredAt >= :thisFrom AND occurredAt < :thisTo
                                 THEN amountMinor ELSE 0 END), 0) AS thisMonthMinor,
               COALESCE(SUM(CASE WHEN occurredAt >= :lastFrom AND occurredAt < :lastTo
                                 THEN amountMinor ELSE 0 END), 0) AS lastMonthMinor
        FROM transactions
        WHERE deletedAt IS NULL AND direction = 'DEBIT' AND transferGroupId IS NULL
          AND kind NOT IN ('TRANSFER', 'ADJUSTMENT', 'LENT', 'BORROWED_RETURNED', 'LOAN_REPAYMENT', 'COVERED_FOR_SOMEONE')
          AND occurredAt >= :lastFrom AND occurredAt < :thisTo
        GROUP BY categoryId
        """,
    )
    fun compareCategorySpend(
        thisFrom: Long,
        thisTo: Long,
        lastFrom: Long,
        lastTo: Long,
    ): Flow<List<CategoryComparisonRow>>

    // The merchant's canonical name where one was resolved, the raw text otherwise --
    // an unresolved merchant is still where the money went.
    @Query(
        """
        SELECT COALESCE(m.canonicalName, t.merchantRaw) AS merchantName,
               SUM(t.amountMinor) AS totalMinor
        FROM transactions t
        LEFT JOIN merchants m ON m.id = t.merchantId
        WHERE t.deletedAt IS NULL AND t.direction = 'DEBIT' AND t.transferGroupId IS NULL
          AND t.kind NOT IN ('TRANSFER', 'ADJUSTMENT', 'LENT', 'BORROWED_RETURNED', 'LOAN_REPAYMENT', 'COVERED_FOR_SOMEONE')
          AND t.occurredAt >= :fromInclusive AND t.occurredAt < :toExclusive
          AND COALESCE(m.canonicalName, t.merchantRaw) IS NOT NULL
        GROUP BY merchantName
        ORDER BY totalMinor DESC
        LIMIT :limit
        """,
    )
    fun observeTopMerchants(fromInclusive: Long, toExclusive: Long, limit: Int): Flow<List<MerchantTotalRow>>

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
