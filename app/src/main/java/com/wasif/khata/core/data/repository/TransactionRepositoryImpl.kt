package com.wasif.khata.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.MerchantDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.dao.pagingSourceMatching
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaDayIndexToLocalDate
import com.wasif.khata.domain.error.DataError
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl @Inject constructor(
    private val db: KhataDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val merchantDao: MerchantDao,
    private val clock: KhataClock,
) : TransactionRepository {

    override fun pagedTransactions(): Flow<PagingData<Transaction>> =
        Pager(PagingConfig(pageSize = 50, prefetchDistance = 25, enablePlaceholders = false)) {
            transactionDao.pagingSource()
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun pagedTransactionsBetween(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<PagingData<Transaction>> =
        Pager(PagingConfig(pageSize = 50, prefetchDistance = 25, enablePlaceholders = false)) {
            transactionDao.pagingSourceBetween(fromInclusive, toExclusive)
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> =
        Pager(PagingConfig(pageSize = 50, prefetchDistance = 25, enablePlaceholders = false)) {
            if (query.isBlank()) {
                transactionDao.pagingSource()
            } else {
                transactionDao.pagingSourceMatching(query)
            }
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Transaction?> =
        transactionDao.observeById(id).map { it?.toDomain() }

    override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
        transactionDao.observeTotalMinorBetween(
            direction = TransactionDirection.DEBIT,
            fromInclusive = fromInclusive,
            toExclusive = toExclusive,
        ).map { Money(it) }

    override fun observeMostRecent(): Flow<Transaction?> =
        transactionDao.observeMostRecent().map { it?.toDomain() }

    override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
        transactionDao.observeTotalMinorBetween(
            direction = TransactionDirection.CREDIT,
            fromInclusive = fromInclusive,
            toExclusive = toExclusive,
        ).map { Money(it) }

    override fun pagedNeedsAttention(): Flow<PagingData<Transaction>> =
        Pager(PagingConfig(pageSize = 50, prefetchDistance = 25, enablePlaceholders = false)) {
            transactionDao.pagingSourceNeedsAttention()
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observeNeedsAttentionCount(): Flow<Int> = transactionDao.observeNeedsAttentionCount()

    override fun observeDayTotals(): Flow<Map<LocalDate, Money>> =
        transactionDao.observeDayTotals().map { rows ->
            rows.associate { it.dhakaDayIndex.dhakaDayIndexToLocalDate() to Money(it.spentMinor) }
        }

    override suspend fun save(draft: TransactionDraft): Result<Long> = runCatchingData {
        db.withTransaction {
            val now = clock.now()
            val existing = draft.id?.let { transactionDao.findById(it) }
            if (draft.id != null && existing == null) throw DataError.NotFound

            // Reverse the previous effect before applying the new one, or an edit
            // compounds onto the balance instead of replacing.
            if (existing != null) {
                accountDao.adjustBalance(existing.accountId, -existing.signedMinor(), now)
            }

            val rowId = transactionDao.upsert(
                TransactionEntity(
                    id = existing?.id ?: 0,
                    uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                    accountId = draft.accountId,
                    amountMinor = draft.amount.minor,
                    direction = draft.direction,
                    occurredAt = draft.occurredAt,
                    merchantRaw = draft.merchantRaw,
                    merchantId = existing?.merchantId,
                    categoryId = draft.categoryId,
                    note = draft.note,
                    counterparty = draft.counterparty,
                    source = existing?.source ?: TransactionSource.MANUAL,
                    // Choosing a category by hand IS the confirmation. Without this
                    // a parsed row stays MEDIUM after you have categorised it, and
                    // the needs-attention list can never be emptied.
                    confidence = if (draft.categoryId != null) {
                        Confidence.HIGH
                    } else {
                        existing?.confidence ?: Confidence.HIGH
                    },
                    // An edit never reclassifies: a transfer stays a transfer when its
                    // note changes. Only a fresh row takes the draft's kind.
                    kind = existing?.kind ?: draft.kind,
                    rawMessageId = existing?.rawMessageId,
                    transferGroupId = existing?.transferGroupId,
                    feeMinor = existing?.feeMinor,
                    referenceNumber = existing?.referenceNumber,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )

            accountDao.adjustBalance(draft.accountId, signedMinor(draft.amount.minor, draft.direction), now)

            // Confirming a merchant is a one-time job: remember the choice, then
            // apply it to that merchant's other rows Khata had left uncertain.
            // Same database transaction, so a merchant can never end up confirmed
            // with its transactions left behind.
            val merchantId = existing?.merchantId
            if (merchantId != null && draft.categoryId != null) {
                merchantDao.confirmCategory(merchantId, draft.categoryId, now)
                transactionDao.adoptMerchantCategory(merchantId, draft.categoryId, now)
            }

            // @Upsert returns -1 when it updated rather than inserted.
            if (rowId == -1L) existing!!.id else rowId
        }
    }

    override suspend fun recordUnexplained(draft: TransactionDraft): Result<Long> = runCatchingData {
        db.withTransaction {
            val now = clock.now()
            val rowId = transactionDao.upsert(
                TransactionEntity(
                    uuid = UUID.randomUUID().toString(),
                    accountId = draft.accountId,
                    amountMinor = draft.amount.minor,
                    direction = draft.direction,
                    occurredAt = draft.occurredAt,
                    merchantRaw = draft.merchantRaw,
                    merchantId = null,
                    categoryId = draft.categoryId,
                    note = draft.note,
                    counterparty = draft.counterparty,
                    source = TransactionSource.MANUAL,
                    confidence = Confidence.HIGH,
                    kind = draft.kind,
                    rawMessageId = null,
                    transferGroupId = null,
                    feeMinor = null,
                    referenceNumber = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            // No adjustBalance: the balance came from the bank's own statement and
            // is already right. Adding this row is what makes the transactions add
            // up to it, so applying it twice would put the ledger back out.
            accountDao.clearUnexplained(draft.accountId, now)
            rowId
        }
    }

    override suspend fun resetToZero(accountId: Long, at: Long): Result<Long?> = runCatchingData {
        db.withTransaction {
            val account = accountDao.getAll().firstOrNull { it.id == accountId } ?: throw DataError.NotFound
            val balance = account.currentBalanceMinor
            if (balance == 0L) return@withTransaction null

            val now = clock.now()
            // Opposite sign to the balance, so the two cancel.
            val rowId = transactionDao.upsert(
                TransactionEntity(
                    uuid = UUID.randomUUID().toString(),
                    accountId = accountId,
                    amountMinor = kotlin.math.abs(balance),
                    direction = if (balance > 0) TransactionDirection.DEBIT else TransactionDirection.CREDIT,
                    occurredAt = at,
                    merchantRaw = null,
                    merchantId = null,
                    categoryId = null,
                    note = "Starting again from zero",
                    counterparty = null,
                    source = TransactionSource.MANUAL,
                    confidence = Confidence.HIGH,
                    kind = TransactionKind.ADJUSTMENT,
                    rawMessageId = null,
                    transferGroupId = null,
                    feeMinor = null,
                    referenceNumber = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            accountDao.adjustBalance(accountId, -balance, now)
            rowId
        }
    }

    override suspend fun delete(id: Long): Result<Unit> = runCatchingData {
        db.withTransaction {
            val existing = transactionDao.findById(id) ?: throw DataError.NotFound
            val now = clock.now()
            accountDao.adjustBalance(existing.accountId, -existing.signedMinor(), now)
            transactionDao.softDelete(id, now)
        }
    }
}

private fun signedMinor(amountMinor: Long, direction: TransactionDirection): Long =
    if (direction == TransactionDirection.DEBIT) -amountMinor else amountMinor

private fun TransactionEntity.signedMinor(): Long = signedMinor(amountMinor, direction)

private inline fun <T> runCatchingData(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    // Rethrow first — a broad catch would swallow structured-concurrency cancellation.
    throw e
} catch (e: DataError) {
    Result.failure(e)
} catch (e: Throwable) {
    Result.failure(DataError.Unknown(e))
}
