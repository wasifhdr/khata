package com.wasif.khata.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingSource
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.BalanceSnapshotDao
import com.wasif.khata.core.data.dao.MediaDao
import com.wasif.khata.core.data.dao.TagDao
import com.wasif.khata.core.data.dao.MerchantDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.data.entity.signedMinor
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.search.SearchIndex
import com.wasif.khata.core.search.ftsQuery
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaDayIndexToLocalDate
import com.wasif.khata.domain.error.DataError
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.StatedBalance
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One page shape for every list in the app.
private val PAGING = PagingConfig(pageSize = 50, prefetchDistance = 25, enablePlaceholders = false)

class TransactionRepositoryImpl @Inject constructor(
    private val db: KhataDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val merchantDao: MerchantDao,
    private val snapshots: BalanceSnapshotDao,
    private val tagDao: TagDao,
    private val mediaDao: MediaDao,
    private val searchIndex: SearchIndex,
    private val clock: KhataClock,
) : TransactionRepository {

    override fun pagedTransactionsBetween(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<PagingData<Transaction>> =
        paged { transactionDao.pagingSourceBetween(fromInclusive, toExclusive) }

    override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> = paged {
        // ftsQuery is null when the box holds nothing searchable -- punctuation only,
        // say. That is "no query", not "match nothing", so the whole ledger shows.
        val match = ftsQuery(query)
        if (match == null) transactionDao.pagingSource() else transactionDao.pagingSourceMatchingFts(match)
    }

    private fun paged(source: () -> PagingSource<Int, TransactionEntity>): Flow<PagingData<Transaction>> =
        Pager(PAGING, pagingSourceFactory = source).flow
            .map { pagingData -> pagingData.map { it.toDomain() } }

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

    override fun observeRecentCounterparties(limit: Int): Flow<List<String>> =
        transactionDao.observeRecentCounterparties(limit)

    override suspend fun save(draft: TransactionDraft): Result<Long> = runCatchingData {
        db.withTransaction {
            val now = clock.now()
            val existing = draft.id?.let { transactionDao.findById(it) }
            if (draft.id != null && existing == null) throw DataError.NotFound

            // Reverse the previous effect before applying the new one, or an edit
            // compounds onto the balance instead of replacing.
            if (existing != null && existing.kind != TransactionKind.IOU) {
                accountDao.adjustBalance(existing.accountId, -existing.signedMinor(), now)
            }

            val resolvedKind = if (
                existing != null &&
                existing.kind !in setOf(TransactionKind.NORMAL, TransactionKind.IOU) &&
                draft.kind == TransactionKind.NORMAL
            ) {
                existing.kind
            } else {
                draft.kind
            }
            val clampedOwedMinor = draft.owed.minor.coerceIn(0L, draft.amount.minor)

            val rowId = transactionDao.upsert(
                TransactionEntity(
                    id = existing?.id ?: 0,
                    uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                    accountId = if (resolvedKind == TransactionKind.IOU) 0L else draft.accountId,
                    amountMinor = draft.amount.minor,
                    direction = draft.direction,
                    occurredAt = draft.occurredAt,
                    merchantRaw = draft.merchantRaw,
                    merchantId = existing?.merchantId,
                    categoryId = draft.categoryId,
                    note = draft.note,
                    counterparty = draft.counterparty,
                    owedMinor = clampedOwedMinor,
                    source = existing?.source ?: draft.source,
                    // Saving by hand IS the review: the row was on screen, in an
                    // editor, and the person looked at it. That is the affordance for
                    // clearing a flag, and it does not require filing the thing under
                    // a category to get it.
                    confidence = Confidence.HIGH,
                    kind = resolvedKind,
                    rawMessageId = existing?.rawMessageId,
                    transferGroupId = existing?.transferGroupId,
                    feeMinor = existing?.feeMinor,
                    referenceNumber = existing?.referenceNumber,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )

            if (resolvedKind != TransactionKind.IOU) {
                accountDao.adjustBalance(draft.accountId, signedMinor(draft.amount.minor, draft.direction), now)
            }

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
            val id = if (rowId == -1L) existing!!.id else rowId
            // Inside the same database transaction as the write, so a row and its
            // index entry can never disagree about what the ledger contains.
            searchIndex.reindex("transaction", id)
            id
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
                    owedMinor = draft.owed.minor.coerceIn(0L, draft.amount.minor),
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
            searchIndex.reindex("transaction", rowId)
            rowId
        }
    }

    override suspend fun setBalance(accountId: Long, targetMinor: Long, at: Long): Result<Long?> = runCatchingData {
        db.withTransaction {
            val account = accountDao.getAll().firstOrNull { it.id == accountId } ?: throw DataError.NotFound
            // What has to move to land on the target, which is what the adjustment
            // row records -- the target itself is never written to the balance.
            val delta = targetMinor - account.currentBalanceMinor
            if (delta == 0L) return@withTransaction null

            val now = clock.now()
            // Opposite sign to the balance, so the two cancel.
            val rowId = transactionDao.upsert(
                TransactionEntity(
                    uuid = UUID.randomUUID().toString(),
                    accountId = accountId,
                    amountMinor = kotlin.math.abs(delta),
                    direction = if (delta < 0) TransactionDirection.DEBIT else TransactionDirection.CREDIT,
                    occurredAt = at,
                    merchantRaw = null,
                    merchantId = null,
                    categoryId = null,
                    note = "Balance set by hand to ${Money(targetMinor).format()}",
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
            accountDao.adjustBalance(accountId, delta, now)
            searchIndex.reindex("transaction", rowId)
            rowId
        }
    }

    override suspend fun startOver(
        statedBalances: Map<Long, StatedBalance>,
        cashMinor: Long,
    ): Result<Unit> = runCatchingData {
        val now = clock.now()
        db.withTransaction {
            transactionDao.deleteAll()
            snapshots.deleteAll()
            // The rows deleteAll leaves pointing nowhere. Ids are handed out again
            // from an emptied table, so these would attach themselves to whatever
            // lands on the id next.
            tagDao.deleteLinksOfType("transaction")
            mediaDao.deleteLinksOfType("transaction")

            for (account in accountDao.getAll()) {
                // Cash has no message to read a balance off, so the person holding
                // it is the source. Dated now: it is true as of this moment and
                // nothing older should be applied on top.
                val stated = if (account.type == AccountType.CASH) {
                    StatedBalance(cashMinor, now)
                } else {
                    // An account with no message stating a balance has nothing to
                    // rebuild from; zero is the honest answer, not a guess.
                    statedBalances[account.id] ?: StatedBalance(0L, now)
                }
                accountDao.startOverAt(account.id, stated.minor, stated.at, now)
            }
        }
        searchIndex.reindexAll()
    }

    override suspend fun settleAsOwnTransfer(
        transactionId: Long,
        otherAccountId: Long,
    ): Result<Unit> = runCatchingData {
        db.withTransaction {
            val original = transactionDao.findById(transactionId) ?: throw DataError.NotFound
            // Already settled -- by the other door, or by a partner arriving late.
            // Writing a second mirror would credit the account twice.
            if (original.transferGroupId != null) return@withTransaction

            val now = clock.now()
            // The mirror is the same movement seen from the other side: same amount,
            // same moment, opposite direction.
            val mirrorDirection = when (original.direction) {
                TransactionDirection.DEBIT -> TransactionDirection.CREDIT
                TransactionDirection.CREDIT -> TransactionDirection.DEBIT
            }
            val mirrorId = transactionDao.upsert(
                TransactionEntity(
                    uuid = UUID.randomUUID().toString(),
                    accountId = otherAccountId,
                    amountMinor = original.amountMinor,
                    direction = mirrorDirection,
                    occurredAt = original.occurredAt,
                    merchantRaw = original.merchantRaw,
                    merchantId = null,
                    categoryId = null,
                    note = null,
                    counterparty = null,
                    // MANUAL, because no message said this: the owner did.
                    source = TransactionSource.MANUAL,
                    confidence = Confidence.HIGH,
                    kind = TransactionKind.TRANSFER,
                    rawMessageId = null,
                    transferGroupId = null,
                    feeMinor = null,
                    referenceNumber = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            // The other account gains what this one lost. Without this the money
            // leaves net worth, which is worse than counting it as spending.
            accountDao.adjustBalance(
                otherAccountId,
                signedMinor(original.amountMinor, mirrorDirection),
                now,
            )

            // Marks both TRANSFER, joins them, and clears the review flag in one go.
            transactionDao.markAsTransfer(
                listOf(original.id, mirrorId),
                UUID.randomUUID().toString(),
                now,
            )

            searchIndex.reindex("transaction", original.id)
            searchIndex.reindex("transaction", mirrorId)
        }
    }

    override suspend fun settleAsOwed(
        transactionId: Long,
        counterparty: String,
    ): Result<Unit> = runCatchingData {
        val trimmed = counterparty.trim()
        require(trimmed.isNotEmpty()) { "Counterparty name cannot be blank" }
        db.withTransaction {
            transactionDao.findById(transactionId) ?: throw DataError.NotFound
            transactionDao.settleAsOwed(transactionId, trimmed, clock.now())
            searchIndex.reindex("transaction", transactionId)
        }
    }

    override suspend fun dismissTransferReview(transactionId: Long): Result<Unit> =
        runCatchingData {
            transactionDao.setReviewPending(transactionId, pending = false, updatedAt = clock.now())
        }

    override suspend fun delete(id: Long): Result<Unit> = runCatchingData {
        db.withTransaction {
            val existing = transactionDao.findById(id) ?: throw DataError.NotFound
            val now = clock.now()
            if (existing.kind != TransactionKind.IOU) {
                accountDao.adjustBalance(existing.accountId, -existing.signedMinor(), now)
            }
            transactionDao.softDelete(id, now)
            searchIndex.remove("transaction", id)
        }
    }
}

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
