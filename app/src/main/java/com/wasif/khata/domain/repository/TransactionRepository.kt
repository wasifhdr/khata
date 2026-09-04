package com.wasif.khata.domain.repository

import androidx.paging.PagingData
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.domain.model.Transaction
import java.time.LocalDate
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
    /** The person on the other side, when the money is owed one way or the other. */
    val counterparty: String? = null,
    /**
     * Defaulted so the editor's call sites are unaffected: a hand-entered
     * transaction is ordinary. Reconciliation is the first caller that needs to say
     * otherwise.
     */
    val kind: TransactionKind = TransactionKind.NORMAL,
    /**
     * Defaulted for the same reason `kind` is: every existing call site is an
     * ordinary hand-entered row and should not have to say so. The widget is the
     * first caller that is something else.
     */
    val source: TransactionSource = TransactionSource.MANUAL,
)

interface TransactionRepository {
    /** One month of the ledger. The half-open window is the caller's to compute in Dhaka. */
    fun pagedTransactionsBetween(fromInclusive: Long, toExclusive: Long): Flow<PagingData<Transaction>>

    /** Blank query returns everything, so the ledger has one code path. */
    fun pagedTransactions(query: String): Flow<PagingData<Transaction>>
    fun observe(id: Long): Flow<Transaction?>
    suspend fun save(draft: TransactionDraft): Result<Long>

    /**
     * Writes an adjustment for money that moved with no message, and clears that
     * account's running total of it.
     *
     * Deliberately does not touch the balance: the balance already came from the
     * bank's own statement and is correct. This only makes the *transactions* add
     * up to it, so the ledger stops being short by an amount it cannot explain.
     */
    suspend fun recordUnexplained(draft: TransactionDraft): Result<Long>

    /**
     * Writes off whatever an account currently holds, so it starts again from zero
     * today. Written as a dated adjustment rather than by editing the balance, so
     * the ledger still says what happened and when.
     */
    suspend fun resetToZero(accountId: Long, at: Long): Result<Long?>
    suspend fun delete(id: Long): Result<Unit>

    /**
     * Records that a movement went to another of the owner's accounts: writes the
     * other side, pairs the two, and takes both out of spending.
     */
    suspend fun settleAsOwnTransfer(transactionId: Long, otherAccountId: Long): Result<Unit>

    /** Records that it did not: the row stays ordinary spending, and stops asking. */
    suspend fun dismissTransferReview(transactionId: Long): Result<Unit>

    /** Debits only, over a half-open window. Boundaries are the caller's to compute in Dhaka. */
    fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money>

    fun observeMostRecent(): Flow<Transaction?>

    /** Credits only, over a half-open window. */
    fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money>

    /** Spending per Dhaka calendar day, for ledger day headers. */
    /** Anything the pipeline was not certain about — MEDIUM as well as LOW. */


    fun observeDayTotals(): Flow<Map<LocalDate, Money>>
}
