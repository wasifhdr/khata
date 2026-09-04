package com.wasif.khata.feature.ledger

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import androidx.paging.PagingState
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
import kotlinx.coroutines.launch
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
    private var requestedQuery: String? = null

    private val clock = object : KhataClock {
        override fun now(): Long = Instant.parse("2026-08-28T09:41:00Z").toEpochMilli()
    }

    private val categoryFlow: Flow<List<Category>> = flowOf(
            listOf(
                Category(
                    id = 11,
                    uuid = "seed-cat-groceries",
                    name = "Groceries",
                    colorToken = "category_green",
                    parentId = null,
                ),
            ),
        )

    private val referenceData = object : ReferenceDataRepository {
        override fun observeAccounts(): Flow<List<Account>> = flowOf(emptyList())
        override fun observeCategories(): Flow<List<Category>> = categoryFlow
        override fun observeCategoriesIncludingDeleted(): Flow<List<Category>> = categoryFlow
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
        private val page = flowOf(
            PagingData.from(transactions.toList(), sourceLoadStates = endOfPagination),
        )
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> {
            requestedWindow = fromInclusive to toExclusive
            return page
        }
        override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> {
            requestedQuery = query
            return page
        }
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: TransactionDraft) = Result.success(0L)
        override suspend fun recordUnexplained(draft: TransactionDraft) = Result.success(0L)
        override suspend fun resetToZero(accountId: Long, at: Long): Result<Long?> = Result.success(null)
        override suspend fun delete(id: Long) = Result.success(Unit)
        override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> = spend
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = dayTotals
        override fun observeMostRecent(): Flow<Transaction?> = flowOf(null)
        override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
    }

    // PagingData.from() (used by repositoryReturning above) builds a plain,
    // freely re-collectable flow -- it never exercises the single-collect
    // pageEventFlow that a real Pager produces, so it cannot reproduce C1's
    // crash. This fake goes through an actual Pager/PagingSource instead, the
    // same object shape combine() re-wraps on every observeDayTotals() tick.
    private fun repositoryWithRealPaging(vararg transactions: Transaction) = object : TransactionRepository {
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> {
            requestedWindow = fromInclusive to toExclusive
            return Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                object : PagingSource<Int, Transaction>() {
                    override fun getRefreshKey(state: PagingState<Int, Transaction>): Int? = null
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Transaction> =
                        LoadResult.Page(data = transactions.toList(), prevKey = null, nextKey = null)
                }
            }.flow
        }
        override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> {
            requestedQuery = query
            return pagedTransactionsBetween(0, 0)
        }
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: TransactionDraft) = Result.success(0L)
        override suspend fun recordUnexplained(draft: TransactionDraft) = Result.success(0L)
        override suspend fun resetToZero(accountId: Long, at: Long): Result<Long?> = Result.success(null)
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
        // 1 August 00:00 Dhaka is 31 July 18:00 UTC -- the exclusive upper bound
        // of the July window, which nothing else here checks.
        assertEquals(Instant.parse("2026-07-31T18:00:00Z").toEpochMilli(), requestedWindow?.second)
    }

    @Test
    fun `a past month has no days left, and the future is unreachable`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        // header is subscribed alongside canGoForward throughout, so every
        // canGoForward assertion is paired with proof -- via monthLabel -- that
        // an emission actually happened, rather than just matching the flow's
        // initial default (false for canGoForward, null for daysLeft).
        viewModel.header.test {
            advanceUntilIdle()
            assertEquals("August 2026", expectMostRecentItem().monthLabel)

            viewModel.canGoForward.test {
                advanceUntilIdle()
                // The current month is the newest that can hold anything; an
                // empty future month is a dead end.
                assertEquals(false, expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.onPreviousMonth()
            advanceUntilIdle()
            val h = expectMostRecentItem()
            assertEquals("July 2026", h.monthLabel)
            assertEquals(null, h.daysLeft)

            viewModel.canGoForward.test {
                advanceUntilIdle()
                assertEquals(true, expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category tokens are keyed by id so a row can resolve its dot`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        viewModel.categoryTokens.test {
            advanceUntilIdle()
            val chip = expectMostRecentItem()[11L]
            assertEquals("category_green", chip?.colorToken)
            assertEquals("Groceries", chip?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank query pages the viewed month rather than searching all time`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)

        viewModel.items.asSnapshot()

        assertEquals(true, requestedWindow != null)
        assertEquals(null, requestedQuery)
    }

    @Test
    fun `a non-blank query searches all time rather than the viewed month`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)
        viewModel.items.asSnapshot()
        // Cleared so the assertion below can only pass if the search path,
        // not a fresh windowed load, is what actually ran next.
        requestedWindow = null

        viewModel.onQueryChange("coffee")
        viewModel.items.asSnapshot()

        assertEquals("coffee", requestedQuery)
        assertEquals(null, requestedWindow)
    }

    @Test
    fun `clearing the query returns to the windowed path`() = runTest(dispatcher) {
        val viewModel = LedgerViewModel(repositoryReturning(), referenceData, clock)
        viewModel.onQueryChange("coffee")
        viewModel.items.asSnapshot()
        requestedWindow = null
        requestedQuery = null

        viewModel.onQueryChange("")
        viewModel.items.asSnapshot()

        assertEquals(true, requestedWindow != null)
    }

    // C1/C2: cachedIn must run before the combine with observeDayTotals(), or
    // Paging throws IllegalStateException: Attempt to collect twice from
    // pageEventFlow the moment dayTotals re-emits mid-collection -- exactly
    // what happens in the app every time a write lands while Ledger is open.
    // Setting dayTotals before the ViewModel exists (as every other test in
    // this file does) can never exercise this: it is consumed before
    // collection of `items` even starts, and PagingData.from() (the fake used
    // by repositoryReturning) is freely re-collectable and can't reproduce
    // the crash either -- only a real Pager's pageEventFlow enforces
    // single-collection. This drives `items` the way LazyPagingItems does in
    // production: a PagingDataPresenter that calls collectFrom() on every new
    // PagingData the flow emits, while the previous generation is still
    // being actively collected.
    @Test
    fun `items keeps collecting when dayTotals emits while paging is actively being collected`() =
        runTest(dispatcher) {
            val aug26 = Instant.parse("2026-08-26T06:00:00Z").toEpochMilli()
            val viewModel = LedgerViewModel(
                repositoryWithRealPaging(transaction(1, aug26)),
                referenceData,
                clock,
            )
            val presenter = object : PagingDataPresenter<LedgerItem>() {
                override suspend fun presentPagingDataEvent(event: PagingDataEvent<LedgerItem>) = Unit
            }

            viewModel.items.test {
                val first = awaitItem()
                val firstCollection = backgroundScope.launch { presenter.collectFrom(first) }
                advanceUntilIdle()

                // A write landing while Ledger is on screen: dayTotals re-emits
                // while `firstCollection` is still actively collecting.
                dayTotals.value = mapOf(aug26.toDhakaLocalDate() to Money(500_00))
                val second = awaitItem()

                // Wrong cachedIn placement means `second` wraps the same
                // pageEventFlow `first` is already collecting, and this throws.
                // A real paging flow never completes on its own, so this must
                // run in the background rather than being awaited directly --
                // launch surfaces the exception the same way collectFrom being
                // called from a live LazyPagingItems collector would.
                val secondCollection = backgroundScope.launch { presenter.collectFrom(second) }
                advanceUntilIdle()

                firstCollection.cancel()
                secondCollection.cancel()
                cancelAndIgnoreRemainingEvents()
            }
        }

}
