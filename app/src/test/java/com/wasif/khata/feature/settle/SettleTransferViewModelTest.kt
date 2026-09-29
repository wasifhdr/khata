package com.wasif.khata.feature.settle

import androidx.paging.PagingData
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.StatedBalance
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import com.wasif.khata.feature.editor.OriginalMessage
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettleTransferViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private var settled: Pair<Long, Long>? = null
    private var settledOwed: Pair<Long, String>? = null
    private var dismissed: Long? = null

    private val pendingRow = Transaction(
        id = 7,
        uuid = "t-7",
        accountId = 1,
        amount = Money(500_000),
        direction = TransactionDirection.DEBIT,
        occurredAt = 1_000,
        merchantRaw = "EBL Account Transfer",
        merchantId = null,
        categoryId = null,
        note = null,
        source = TransactionSource.SMS,
        confidence = Confidence.HIGH,
        transferGroupId = null,
        rawMessageId = 9,
        updatedAt = 1_000,
    )

    private fun account(id: Long, name: String) = Account(
        id = id,
        uuid = "acc-$id",
        name = name,
        type = AccountType.BANK,
        currentBalance = Money.ZERO,
        reportedBalance = null,
        includeInNetWorth = true,
    )

    private val referenceData = object : ReferenceDataRepository {
        override fun observeAccounts(): Flow<List<Account>> =
            flowOf(listOf(account(1, "EBL Salary"), account(2, "EBL Student"), account(3, "Cash")))
        override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeCategoriesIncludingDeleted(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeNetWorth(): Flow<Money> = flowOf(Money.ZERO)
    }

    private val repository = object : TransactionRepository {
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> =
            flowOf(PagingData.empty())
        override fun observe(id: Long): Flow<Transaction?> = flowOf(pendingRow)
        override suspend fun save(draft: TransactionDraft) = Result.success(0L)
        override suspend fun recordUnexplained(draft: TransactionDraft) = Result.success(0L)
        override suspend fun setBalance(accountId: Long, targetMinor: Long, at: Long): Result<Long?> =
            Result.success(null)
        override suspend fun startOver(statedBalances: Map<Long, StatedBalance>, cashMinor: Long) =
            Result.success(Unit)
        override suspend fun delete(id: Long) = Result.success(Unit)

        override suspend fun settleAsOwnTransfer(
            transactionId: Long,
            otherAccountId: Long,
        ): Result<Unit> {
            settled = transactionId to otherAccountId
            return Result.success(Unit)
        }

        override suspend fun settleAsOwed(
            transactionId: Long,
            counterparty: String,
        ): Result<Unit> {
            settledOwed = transactionId to counterparty
            return Result.success(Unit)
        }

        override suspend fun dismissTransferReview(transactionId: Long): Result<Unit> {
            dismissed = transactionId
            return Result.success(Unit)
        }

        override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
        override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
        override fun observeMostRecent(): Flow<Transaction?> = flowOf(null)
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = flowOf(emptyMap())
        override fun observeRecentCounterparties(limit: Int): Flow<List<String>> =
            flowOf(listOf("Rafi", "Sadia"))
    }

    private fun viewModel() = SettleTransferViewModel(
        repository,
        referenceData,
        OriginalMessage { id -> if (id == 9L) "AC 112***286 is debited with BDT 5000" else null },
        transactionId = 7,
    )

    @Test
    fun `choosing an account settles it as an own transfer`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAccountChosen(3)
        advanceUntilIdle()

        assertEquals(7L to 3L, settled)
        assertTrue(viewModel.state.value.done)
    }

    @Test
    fun `not mine leaves it spending`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onNotMine()
        advanceUntilIdle()

        assertEquals(7L, dismissed)
        assertNull(settled)
    }

    @Test
    fun `the account it came from is not offered as the account it went to`() = runTest(dispatcher) {
        // Money cannot move from an account to itself, and offering it invites a
        // transfer that balances to nothing.
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(2L, 3L), viewModel.state.value.choices.map { it.id })
    }

    @Test
    fun `the sheet shows the message, because that is what settles the question`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                "AC 112***286 is debited with BDT 5000",
                viewModel.state.value.originalMessage,
            )
        }

    @Test
    fun `typing a person's name and settling as owed records it against their tab and closes`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(listOf("Rafi", "Sadia"), viewModel.state.value.recentPeople)
            viewModel.onCounterpartyChange("  Rafi  ")
            viewModel.onSettleAsOwed()
            advanceUntilIdle()

            assertEquals(7L to "Rafi", settledOwed)
            assertTrue(viewModel.state.value.done)
        }

    @Test
    fun `settling as owed with a blank name is a no-op`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCounterpartyChange("   ")
        viewModel.onSettleAsOwed()
        advanceUntilIdle()

        assertNull(settledOwed)
        assertEquals(false, viewModel.state.value.done)
    }
}
