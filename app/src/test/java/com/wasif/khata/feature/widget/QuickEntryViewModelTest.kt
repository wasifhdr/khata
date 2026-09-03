package com.wasif.khata.feature.widget

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cash.turbine.test
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.error.DataError
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuickEntryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-09-03T09:00:00Z").toEpochMilli()
    private val clock = object : KhataClock { override fun now(): Long = now }

    private val cash = Account(
        id = 4L,
        uuid = "acc-cash",
        name = "Cash",
        type = AccountType.CASH,
        currentBalance = Money.ZERO,
        reportedBalance = null,
        includeInNetWorth = true,
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        direction: String = "DEBIT",
        transactions: FakeTransactionRepository = FakeTransactionRepository(),
    ) = QuickEntryViewModel(
        savedState = SavedStateHandle(mapOf(EXTRA_DIRECTION to direction)),
        transactions = transactions,
        reference = FakeReferenceDataRepository(accounts = listOf(cash)),
        recentCategoryIds = { _, _ -> flowOf(emptyList()) },
        clock = clock,
    )

    /**
     * state is `WhileSubscribed`, so it stays on its initial value until something
     * collects. Tests that assert on updates need a live subscriber.
     */
    private fun CoroutineScope.keepStateHot(vm: QuickEntryViewModel) {
        launch { vm.state.collect {} }
    }

    @Test
    fun `the direction comes from the intent, not from a control`() {
        assertEquals(TransactionDirection.CREDIT, viewModel(direction = "CREDIT").state.value.direction)
        assertEquals(TransactionDirection.DEBIT, viewModel(direction = "DEBIT").state.value.direction)
    }

    @Test
    fun `a saved entry is written to cash, as a widget entry, and closes the sheet`() = runTest {
        val transactions = FakeTransactionRepository()
        val vm = viewModel(transactions = transactions)
        vm.onAmountChange("50")
        vm.onCategorySelected(9L)

        vm.effects.test {
            vm.onSave()
            advanceUntilIdle()
            assertEquals(QuickEntryEffect.Saved, awaitItem())
        }

        val draft = transactions.saved.single()
        assertEquals(TransactionSource.WIDGET, draft.source)
        assertEquals(4L, draft.accountId)
        assertEquals(Money(5_000), draft.amount)
        assertEquals(TransactionDirection.DEBIT, draft.direction)
        assertEquals(9L, draft.categoryId)
        assertEquals(now, draft.occurredAt)
    }

    @Test
    fun `a failed save keeps the sheet open with the amount intact`() = runTest {
        val transactions = FakeTransactionRepository(failure = IllegalStateException("disk"))
        val vm = viewModel(transactions = transactions)
        backgroundScope.keepStateHot(vm)
        vm.onAmountChange("50")

        vm.effects.test {
            vm.onSave()
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals("50", vm.state.value.amountInput)
        assertNotNull(vm.state.value.saveError)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `an empty or zero amount cannot be saved`() = runTest {
        val vm = viewModel()
        backgroundScope.keepStateHot(vm)
        advanceUntilIdle()

        assertFalse(vm.state.value.canSave)
        vm.onAmountChange("0")
        advanceUntilIdle()
        assertFalse(vm.state.value.canSave)
        vm.onAmountChange("0.01")
        advanceUntilIdle()
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `a note is optional and travels with the draft`() = runTest {
        val transactions = FakeTransactionRepository()
        val vm = viewModel(transactions = transactions)
        vm.onAmountChange("50")
        vm.onNoteChange("rickshaw to office")

        vm.onSave()
        advanceUntilIdle()

        assertEquals("rickshaw to office", transactions.saved.single().note)
    }

    @Test
    fun `a blank note is stored as absent rather than as an empty string`() = runTest {
        val transactions = FakeTransactionRepository()
        val vm = viewModel(transactions = transactions)
        vm.onAmountChange("50")

        vm.onSave()
        advanceUntilIdle()

        assertEquals(null, transactions.saved.single().note)
    }
}

/**
 * Only save and observeAccounts are ever reached. The rest are TODO() rather than
 * empty flows so a future test cannot quietly pass while asserting nothing.
 */
private class FakeTransactionRepository(
    private val failure: Throwable? = null,
) : TransactionRepository {
    val saved = mutableListOf<TransactionDraft>()

    override suspend fun save(draft: TransactionDraft): Result<Long> {
        failure?.let { return Result.failure(DataError.Unknown(it)) }
        saved += draft
        return Result.success(saved.size.toLong())
    }

    override fun pagedTransactionsBetween(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<PagingData<Transaction>> = TODO()

    override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> = TODO()
    override fun observe(id: Long): Flow<Transaction?> = TODO()
    override suspend fun recordUnexplained(draft: TransactionDraft): Result<Long> = TODO()
    override suspend fun resetToZero(accountId: Long, at: Long): Result<Long?> = TODO()
    override suspend fun delete(id: Long): Result<Unit> = TODO()
    override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> = TODO()
    override fun observeMostRecent(): Flow<Transaction?> = TODO()
    override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> = TODO()
    override fun pagedNeedsAttention(): Flow<PagingData<Transaction>> = TODO()
    override fun observeNeedsAttentionCount(): Flow<Int> = TODO()
    override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = TODO()
}

private class FakeReferenceDataRepository(
    private val accounts: List<Account>,
) : ReferenceDataRepository {
    override fun observeAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())
    override fun observeNetWorth(): Flow<Money> = flowOf(Money.ZERO)
}
