package com.wasif.khata.feature.hub

import androidx.paging.PagingData
import app.cash.turbine.test
import com.wasif.khata.core.data.repository.MonthLimits
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.domain.model.Transaction
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ModulesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val spend = MutableStateFlow(Money.ZERO)
    private var requestedSpendWindow: Pair<Long, Long>? = null

    private val clock = object : KhataClock {
        override fun now(): Long = Instant.parse("2026-08-28T09:41:00Z").toEpochMilli()
    }

    private val transactions = object : TransactionRepository {
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> =
            flowOf(PagingData.empty())
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: TransactionDraft) = Result.success(0L)
        override suspend fun recordUnexplained(draft: TransactionDraft) = Result.success(0L)
        override suspend fun resetToZero(accountId: Long, at: Long): Result<Long?> = Result.success(null)
        override suspend fun delete(id: Long) = Result.success(Unit)
        override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> {
            requestedSpendWindow = fromInclusive to toExclusive
            return spend
        }
        override fun observeMostRecent(): Flow<Transaction?> = flowOf(null)
        override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = flowOf(emptyMap())
    }

    /** What each category is allowed this month; the ring shows their sum. */
    private val limits = MutableStateFlow<Map<Long, Long>>(emptyMap())
    private val monthLimits = MonthLimits { limits }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `month spend comes from the Dhaka month window`() = runTest(dispatcher) {
        spend.value = Money(47_382_50)

        val vm = ModulesViewModel(transactions, monthLimits, clock)

        vm.state.test {
            // stateIn emits its initialValue before the upstream combine has run
            // under StandardTestDispatcher, so the first item is the placeholder.
            advanceUntilIdle()
            assertEquals(Money(47_382_50), expectMostRecentItem().monthSpend)
            cancelAndIgnoreRemainingEvents()
        }

        // I9: the fake above used to discard fromInclusive/toExclusive
        // entirely, so this name asserted nothing about the window -- replacing
        // the ViewModel's window computation with 0L to 0L still passed. The
        // clock is fixed at 28 August 2026, so the half-open bounds below are
        // the exact Dhaka month edges, not epoch-0 placeholders: a window off
        // by even an hour would fail this.
        val now = clock.now()
        val expectedWindow = now.dhakaMonthStart() to now.dhakaNextMonthStart()
        // 1 August 00:00 Dhaka is 31 July 18:00 UTC.
        assertEquals(Instant.parse("2026-07-31T18:00:00Z").toEpochMilli(), expectedWindow.first)
        // 1 September 00:00 Dhaka is 31 August 18:00 UTC.
        assertEquals(Instant.parse("2026-08-31T18:00:00Z").toEpochMilli(), expectedWindow.second)
        assertEquals(expectedWindow, requestedSpendWindow)
    }

    @Test
    fun `the budget ring is absent when no budget is set`() = runTest(dispatcher) {
        // A ring drawn against an unset budget would be showing a number the
        // user never entered. Absent is the honest state.
        spend.value = Money(47_382_50)

        ModulesViewModel(transactions, monthLimits, clock).state.test {
            advanceUntilIdle()
            assertNull(expectMostRecentItem().budgetFraction)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the budget fraction is spend over budget, clamped at one`() = runTest(dispatcher) {
        spend.value = Money(75_000_00)
        limits.value = mapOf(1L to 30_000_00L, 2L to 20_000_00L)

        ModulesViewModel(transactions, monthLimits, clock).state.test {
            // Overspending is real and must show as a full ring, never as 150%
            // of a circle.
            advanceUntilIdle()
            assertEquals(1f, expectMostRecentItem().budgetFraction)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a zero budget does not divide by zero`() = runTest(dispatcher) {
        spend.value = Money(1_000_00)
        limits.value = mapOf(1L to 0L)

        ModulesViewModel(transactions, monthLimits, clock).state.test {
            advanceUntilIdle()
            assertEquals(1f, expectMostRecentItem().budgetFraction)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
