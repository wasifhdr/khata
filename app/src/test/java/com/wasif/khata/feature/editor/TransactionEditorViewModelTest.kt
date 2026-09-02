package com.wasif.khata.feature.editor

import androidx.paging.PagingData
import app.cash.turbine.test
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.error.DataError
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private var savedDraft: TransactionDraft? = null
    private var saveResult: Result<Long> = Result.success(1L)

    private val repository = object : TransactionRepository {
        override fun pagedTransactions(): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> =
            pagedTransactions()
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun recordUnexplained(draft: TransactionDraft) = Result.success(0L)

        override suspend fun save(draft: TransactionDraft): Result<Long> {
            savedDraft = draft
            return saveResult
        }
        override suspend fun delete(id: Long) = Result.success(Unit)
        override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
        override fun observeMostRecent(): Flow<Transaction?> = flowOf(null)
        override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = flowOf(emptyMap())
        override fun pagedNeedsAttention(): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun observeNeedsAttentionCount(): Flow<Int> = flowOf(0)
    }

    private val bkash = Account(
        id = 3,
        uuid = "acc-3",
        name = "bKash",
        type = AccountType.MFS,
        currentBalance = Money.ZERO,
        reportedBalance = null,
        includeInNetWorth = true,
    )

    private val groceries = Category(
        id = 11,
        uuid = "seed-cat-groceries",
        name = "Groceries",
        icon = "shopping_cart",
        colorToken = "category_green",
        parentId = null,
    )

    private val referenceData = object : ReferenceDataRepository {
        override fun observeAccounts(): Flow<List<Account>> = flowOf(listOf(bkash))
        override fun observeCategories(): Flow<List<Category>> = flowOf(listOf(groceries))
        override fun observeNetWorth(): Flow<Money> = flowOf(Money.ZERO)
    }

    private val clock = object : KhataClock {
        override fun now(): Long = 7_000L
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(transactionId: Long? = null) =
        TransactionEditorViewModel(repository, referenceData, clock, transactionId)

    @Test
    fun `a new transaction defaults its timestamp to now`() {
        assertEquals(7_000L, viewModel().uiState.value.occurredAt)
    }

    @Test
    fun `reference data populates and preselects the first account`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(bkash), vm.uiState.value.accounts)
        assertEquals(listOf(groceries), vm.uiState.value.categories)
        assertEquals(3L, vm.uiState.value.accountId)
    }

    @Test
    fun `canSave is false until an amount is present`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.canSave)

        vm.onAmountChange("250")
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `a zero amount does not enable saving`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onAmountChange("0")

        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `an unparseable amount surfaces an error and blocks saving`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onAmountChange("12.345")

        assertFalse(vm.uiState.value.canSave)
        assertTrue(vm.uiState.value.amountHasError)
    }

    @Test
    fun `a blank amount is not an error, just incomplete`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.amountHasError)
        assertNull(vm.uiState.value.amount)
    }

    @Test
    fun `saving builds a draft from the form and reports success as an effect`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAmountChange("1,234.56")
        vm.onMerchantChange("SHWAPNO")
        vm.onCategorySelected(11L)
        vm.onDirectionChange(TransactionDirection.DEBIT)

        vm.effects.test {
            vm.onSave()
            assertEquals(TransactionEditorEffect.Saved, awaitItem())
        }

        assertEquals(Money(123_456), savedDraft?.amount)
        assertEquals(3L, savedDraft?.accountId)
        assertEquals(11L, savedDraft?.categoryId)
        assertEquals("SHWAPNO", savedDraft?.merchantRaw)
        assertEquals(TransactionDirection.DEBIT, savedDraft?.direction)
    }

    @Test
    fun `blank merchant and note are stored as null rather than empty strings`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAmountChange("250")

        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(savedDraft?.merchantRaw)
        assertNull(savedDraft?.note)
    }

    @Test
    fun `a save failure becomes durable state, not a fire-once effect`() = runTest(dispatcher) {
        saveResult = Result.failure(DataError.Storage)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAmountChange("250")

        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Could not save. Please try again.", vm.uiState.value.saveError)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `editing the amount clears a previous save error`() = runTest(dispatcher) {
        saveResult = Result.failure(DataError.Storage)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onAmountChange("250")
        vm.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onAmountChange("300")

        assertNull(vm.uiState.value.saveError)
    }
}
