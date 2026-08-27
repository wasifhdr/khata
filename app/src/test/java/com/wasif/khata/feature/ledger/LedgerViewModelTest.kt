package com.wasif.khata.feature.ledger

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerViewModelTest {

    // Dhaka is UTC+6, so these two land on 26 August and the third on 25 August.
    private val aug26Midday = Instant.parse("2026-08-26T09:00:00Z").toEpochMilli()
    private val aug26Morning = Instant.parse("2026-08-26T04:00:00Z").toEpochMilli()
    private val aug25 = Instant.parse("2026-08-25T04:00:00Z").toEpochMilli()

    private fun transaction(id: Long, occurredAt: Long) = Transaction(
        id = id,
        uuid = "t-$id",
        accountId = 1,
        amount = Money(10_000),
        direction = TransactionDirection.DEBIT,
        occurredAt = occurredAt,
        merchantRaw = "SHWAPNO",
        merchantId = null,
        categoryId = null,
        note = null,
        source = TransactionSource.MANUAL,
        confidence = Confidence.HIGH,
        transferGroupId = null,
        updatedAt = occurredAt,
    )

    // cachedIn() turns this into a non-completable shared flow, so asSnapshot() can only
    // learn loading is done from LoadState, not from flowOf's own completion — hence explicit end-of-pagination states.
    private val endOfPagination = LoadStates(
        refresh = LoadState.NotLoading(endOfPaginationReached = true),
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true),
    )

    private fun repositoryReturning(vararg transactions: Transaction) = object : TransactionRepository {
        override fun pagedTransactions(): Flow<PagingData<Transaction>> =
            flowOf(PagingData.from(transactions.toList(), sourceLoadStates = endOfPagination))
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: TransactionDraft) = Result.success(0L)
        override suspend fun delete(id: Long) = Result.success(Unit)
    }

    @Test
    fun `a day header is inserted before the first transaction of each day`() = runTest {
        val viewModel = LedgerViewModel(
            repositoryReturning(
                transaction(1, aug26Midday),
                transaction(2, aug26Morning),
                transaction(3, aug25),
            )
        )

        val items = viewModel.items.asSnapshot()

        assertEquals(
            listOf(
                LedgerItem.DayHeader(LocalDate.of(2026, 8, 26)),
                LedgerItem.Row(transaction(1, aug26Midday)),
                LedgerItem.Row(transaction(2, aug26Morning)),
                LedgerItem.DayHeader(LocalDate.of(2026, 8, 25)),
                LedgerItem.Row(transaction(3, aug25)),
            ),
            items,
        )
    }

    @Test
    fun `transactions on the same Dhaka day share a single header`() = runTest {
        val viewModel = LedgerViewModel(
            repositoryReturning(transaction(1, aug26Midday), transaction(2, aug26Morning))
        )

        val headers = viewModel.items.asSnapshot().filterIsInstance<LedgerItem.DayHeader>()

        assertEquals(1, headers.size)
    }

    @Test
    fun `an evening UTC transaction is grouped under the next Dhaka day`() = runTest {
        val lateUtc = Instant.parse("2026-08-26T20:30:00Z").toEpochMilli()
        val viewModel = LedgerViewModel(repositoryReturning(transaction(1, lateUtc)))

        val header = viewModel.items.asSnapshot().first() as LedgerItem.DayHeader

        assertEquals(LocalDate.of(2026, 8, 27), header.date)
    }

    @Test
    fun `an empty ledger produces no headers`() = runTest {
        val viewModel = LedgerViewModel(repositoryReturning())

        assertEquals(emptyList<LedgerItem>(), viewModel.items.asSnapshot())
    }
}
