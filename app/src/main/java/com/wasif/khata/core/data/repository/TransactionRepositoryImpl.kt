package com.wasif.khata.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.dao.pagingSourceMatching
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
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
                    source = existing?.source ?: TransactionSource.MANUAL,
                    confidence = existing?.confidence ?: Confidence.HIGH,
                    rawMessageId = existing?.rawMessageId,
                    transferGroupId = existing?.transferGroupId,
                    feeMinor = existing?.feeMinor,
                    referenceNumber = existing?.referenceNumber,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )

            accountDao.adjustBalance(draft.accountId, signedMinor(draft.amount.minor, draft.direction), now)

            // @Upsert returns -1 when it updated rather than inserted.
            if (rowId == -1L) existing!!.id else rowId
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
