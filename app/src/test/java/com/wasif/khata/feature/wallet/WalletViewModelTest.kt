package com.wasif.khata.feature.wallet

import androidx.paging.PagingData
import app.cash.turbine.test
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val netWorth = MutableStateFlow(Money.ZERO)
    private val accounts = MutableStateFlow(emptyList<Account>())
    private val spend = MutableStateFlow(Money.ZERO)
    private val received = MutableStateFlow(Money.ZERO)
    private var requestedSpendWindow: Pair<Long, Long>? = null
    private var requestedReceivedWindow: Pair<Long, Long>? = null

    private val clock = object : KhataClock {
        override fun now(): Long = Instant.parse("2026-08-28T09:41:00Z").toEpochMilli()
    }

    private val transactions = object : TransactionRepository {
        override fun pagedTransactions(): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> =
            pagedTransactions()
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
        override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> {
            requestedReceivedWindow = fromInclusive to toExclusive
            return received
        }
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = flowOf(emptyMap())
        override fun pagedNeedsAttention(): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun observeNeedsAttentionCount(): Flow<Int> = flowOf(0)
    }

    private val reference = object : ReferenceDataRepository {
        override fun observeAccounts(): Flow<List<Account>> = accounts
        override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeNetWorth(): Flow<Money> = netWorth
    }

    private fun account(name: String, balance: Long, reported: Long?) = Account(
        id = name.hashCode().toLong(),
        uuid = name,
        name = name,
        type = AccountType.MFS,
        currentBalance = Money(balance),
        reportedBalance = reported?.let { Money(it) },
        includeInNetWorth = true,
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the month figures come from the Dhaka month window`() = runTest(dispatcher) {
        spend.value = Money(47_382_50)
        received.value = Money(85_000_00)
        netWorth.value = Money(3_14_820_00)

        WalletViewModel(transactions, reference, clock).state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(Money(47_382_50), s.monthSpend)
            assertEquals(Money(85_000_00), s.monthReceived)
            assertEquals(Money(3_14_820_00), s.netWorth)
            cancelAndIgnoreRemainingEvents()
        }

        // I9: the fakes above used to discard fromInclusive/toExclusive
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
        assertEquals(expectedWindow, requestedReceivedWindow)
    }

    @Test
    fun `an account whose reported balance disagrees is flagged as drifting`() = runTest(dispatcher) {
        // Product principle #2: the app proves itself by reconciling against
        // reported balances rather than asking the user to notice.
        accounts.value = listOf(
            account("bKash", balance = 8_214_30, reported = 8_214_30),
            account("EBL", balance = 2_98_606_00, reported = 2_98_846_00),
        )

        WalletViewModel(transactions, reference, clock).state.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertTrue(s.accounts.single { it.name == "EBL" }.hasBalanceDrift)
            assertTrue(!s.accounts.single { it.name == "bKash" }.hasBalanceDrift)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an account with no reported balance never reads as drifting`() = runTest(dispatcher) {
        // Cash has no upstream to reconcile against. Absence of a reported
        // balance is not a discrepancy.
        accounts.value = listOf(account("Cash", balance = 8_000_00, reported = null))

        WalletViewModel(transactions, reference, clock).state.test {
            advanceUntilIdle()
            assertTrue(!expectMostRecentItem().accounts.single().hasBalanceDrift)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
