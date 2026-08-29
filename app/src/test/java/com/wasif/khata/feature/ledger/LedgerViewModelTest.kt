package com.wasif.khata.feature.ledger

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LedgerViewModelTest {

    // viewModelScope runs on Dispatchers.Main.immediate; the header and
    // categoryTokens StateFlows only progress under runTest's virtual clock if
    // Main is swapped for the same test dispatcher runTest advances.
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // Dhaka is UTC+6, so these two land on 26 August and the third on 25 August.
    private val aug26Midday = Instant.parse("2026-08-26T09:00:00Z").toEpochMilli()
    private val aug26Morning = Instant.parse("2026-08-26T04:00:00Z").toEpochMilli()
    private val aug25 = Instant.parse("2026-08-25T04:00:00Z").toEpochMilli()

    private val dayTotals = MutableStateFlow(emptyMap<LocalDate, Money>())
    private val spend = MutableStateFlow(Money.ZERO)
    private var requestedWindow: Pair<Long, Long>? = null

    private val clock = object : KhataClock {
        override fun now(): Long = Instant.parse("2026-08-28T09:41:00Z").toEpochMilli()
    }

    private val referenceData = object : ReferenceDataRepository {
        override fun observeAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override fun observeCategories(): Flow<List<Category>> = flowOf(
            listOf(
                Category(
                    id = 11,
                    uuid = "seed-cat-groceries",
                    name = "Groceries",
                    icon = "shopping_cart",
                    colorToken = "category_green",
                    parentId = null,
                ),
            ),
        )
        override fun observeNetWorth(): Flow<Money> = flowOf(Money.ZERO)
    }

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
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> {
            requestedWindow = fromInclusive to toExclusive
            return pagedTransactions()
        }
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: TransactionDraft) = Result.success(0L)
        override suspend fun delete(id: Long) = Result.success(Unit)
        override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> = spend
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = dayTotals
        override fun observeMostRecent(): Flow<Transaction?> = flowOf(null)
        override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
    }

    @Test
    fun `a day header is inserted before the first transaction of each day`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(
            repositoryReturning(
                transaction(1, aug26Midday),
                transaction(2, aug26Morning),
                transaction(3, aug25),
            ),
            referenceData,
            clock,
        )

        val items = viewModel.items.asSnapshot()

        assertEquals(
            listOf(
                LedgerItem.DayHeader(LocalDate.of(2026, 8, 26), Money.ZERO),
                LedgerItem.Row(transaction(1, aug26Midday)),
                LedgerItem.Row(transaction(2, aug26Morning)),
                LedgerItem.DayHeader(LocalDate.of(2026, 8, 25), Money.ZERO),
                LedgerItem.Row(transaction(3, aug25)),
            ),
            items,
        )
    }

    @Test
    fun `transactions on the same Dhaka day share a single header`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(
            repositoryReturning(transaction(1, aug26Midday), transaction(2, aug26Morning)),
            referenceData,
            clock,
        )

        val headers = viewModel.items.asSnapshot().filterIsInstance<LedgerItem.DayHeader>()

        assertEquals(1, headers.size)
    }

    @Test
    fun `an evening UTC transaction is grouped under the next Dhaka day`() = runTest(dispatcher) {
        val lateUtc = Instant.parse("2026-08-26T20:30:00Z").toEpochMilli()
        val viewModel = LedgerViewModel(repositoryReturning(transaction(1, lateUtc)), referenceData, clock)

        val header = viewModel.items.asSnapshot().first() as LedgerItem.DayHeader

        assertEquals(LocalDate.of(2026, 8, 27), header.date)
    }

    @Test
    fun `an empty ledger produces no headers`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        assertEquals(emptyList<LedgerItem>(), viewModel.items.asSnapshot())
    }

    @Test
    fun `a day header carries that day's spending total`() = runTest(dispatcher) {
        val aug26 = Instant.parse("2026-08-26T06:00:00Z").toEpochMilli()
        dayTotals.value = mapOf(aug26.toDhakaLocalDate() to Money(3_445_50))

        val viewModel = LedgerViewModel(repositoryReturning(transaction(1, aug26)), referenceData, clock)

        val header = viewModel.items.asSnapshot().filterIsInstance<LedgerItem.DayHeader>().single()
        assertEquals(Money(3_445_50), header.total)
    }

    @Test
    fun `a day with no total entry shows zero rather than crashing`() = runTest(dispatcher) {
        // The totals map and the paged rows are two independent queries. They can
        // disagree for a frame, and a header must not blow up when they do.
        val aug26 = Instant.parse("2026-08-26T06:00:00Z").toEpochMilli()
        dayTotals.value = emptyMap()

        val viewModel = LedgerViewModel(repositoryReturning(transaction(1, aug26)), referenceData, clock)

        val header = viewModel.items.asSnapshot().filterIsInstance<LedgerItem.DayHeader>().single()
        assertEquals(Money.ZERO, header.total)
    }

    @Test
    fun `the ledger opens on the current month and says how much of it is left`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        viewModel.header.test {
            advanceUntilIdle()
            val h = expectMostRecentItem()
            // "August 2026" rather than "Ledger": you know you are in the ledger
            // because you tapped to get here; what you do not know is the month.
            assertEquals("August 2026", h.monthLabel)
            // 28 August of a 31-day month.
            assertEquals(3, h.daysLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stepping back a month re-windows the query and relabels the header`() = runTest(dispatcher) {
        // header is a WhileSubscribed StateFlow, so a bare `.value` read is only
        // trustworthy once something has actually subscribed -- turbine is that
        // subscriber, held open across the month change.
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        viewModel.header.test {
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onPreviousMonth()
            advanceUntilIdle()
            viewModel.items.asSnapshot()

            assertEquals("July 2026", expectMostRecentItem().monthLabel)
            cancelAndIgnoreRemainingEvents()
        }
        // 1 July 00:00 Dhaka is 30 June 18:00 UTC.
        assertEquals(Instant.parse("2026-06-30T18:00:00Z").toEpochMilli(), requestedWindow?.first)
    }

    @Test
    fun `a past month has no days left, and the future is unreachable`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        viewModel.canGoForward.test {
            advanceUntilIdle()
            // The current month is the newest that can hold anything; an empty
            // future month is a dead end.
            assertEquals(false, expectMostRecentItem())

            viewModel.onPreviousMonth()
            advanceUntilIdle()

            assertEquals(true, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }

        // _viewedMonth is itself a StateFlow, so header replays the month that
        // was already stepped back to as soon as something subscribes.
        viewModel.header.test {
            advanceUntilIdle()
            assertEquals(null, expectMostRecentItem().daysLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category tokens are keyed by id so a row can resolve its dot`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        viewModel.categoryTokens.test {
            advanceUntilIdle()
            assertEquals("category_green", expectMostRecentItem()[11L])
            cancelAndIgnoreRemainingEvents()
        }
    }
}
