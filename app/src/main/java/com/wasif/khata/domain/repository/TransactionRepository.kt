package com.wasif.khata.domain.repository

import androidx.paging.PagingData
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

data class TransactionDraft(
    val id: Long?,
    val accountId: Long,
    val amount: Money,
    val direction: TransactionDirection,
    val occurredAt: Long,
    val merchantRaw: String?,
    val categoryId: Long?,
    val note: String?,
)

interface TransactionRepository {
    fun pagedTransactions(): Flow<PagingData<Transaction>>
    fun observe(id: Long): Flow<Transaction?>
    suspend fun save(draft: TransactionDraft): Result<Long>
    suspend fun delete(id: Long): Result<Unit>

    /** Debits only, over a half-open window. Boundaries are the caller's to compute in Dhaka. */
    fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money>

    fun observeMostRecent(): Flow<Transaction?>
}
