package com.wasif.khata.feature.owed

import androidx.paging.PagingData
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.model.Transaction
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.StatedBalance
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OwedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: KhataDatabase
    private var savedDraft: TransactionDraft? = null
    private var seq = 0

    private val clock = object : KhataClock {
        override fun now(): Long = 1_700_000_000_000L
    }

    private val accounts = MutableStateFlow(
        listOf(
            Account(
                id = 1L,
                uuid = "acc-ebl",
                name = "EBL",
                type = AccountType.BANK,
                currentBalance = Money.ZERO,
                reportedBalance = null,
                includeInNetWorth = true,
            ),
            Account(
                id = 2L,
                uuid = "acc-cash",
                name = "Cash",
                type = AccountType.CASH,
                currentBalance = Money.ZERO,
                reportedBalance = null,
                includeInNetWorth = true,
            ),
        ),
    )

    private val referenceData = object : ReferenceDataRepository {
        override fun observeAccounts(): Flow<List<Account>> = accounts
        override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeCategoriesIncludingDeleted(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeNetWorth(): Flow<Money> = flowOf(Money.ZERO)
    }

    private val transactionRepository = object : TransactionRepository {
        override fun pagedTransactionsBetween(
            fromInclusive: Long,
            toExclusive: Long,
        ): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())
        override fun pagedTransactions(query: String): Flow<PagingData<Transaction>> =
            flowOf(PagingData.empty())
        override fun observe(id: Long): Flow<Transaction?> = flowOf(null)
        override suspend fun save(draft: TransactionDraft): Result<Long> {
            savedDraft = draft
            return Result.success(99L)
        }
        override suspend fun recordUnexplained(draft: TransactionDraft): Result<Long> =
            Result.success(0L)
        override suspend fun setBalance(accountId: Long, targetMinor: Long, at: Long): Result<Long?> =
            Result.success(null)
        override suspend fun startOver(statedBalances: Map<Long, StatedBalance>, cashMinor: Long): Result<Unit> =
            Result.success(Unit)
        override suspend fun delete(id: Long): Result<Unit> = Result.success(Unit)
        override suspend fun settleAsOwnTransfer(transactionId: Long, otherAccountId: Long): Result<Unit> =
            Result.success(Unit)
        override suspend fun dismissTransferReview(transactionId: Long): Result<Unit> =
            Result.success(Unit)
        override fun observeSpentBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
        override fun observeReceivedBetween(fromInclusive: Long, toExclusive: Long): Flow<Money> =
            flowOf(Money.ZERO)
        override fun observeMostRecent(): Flow<Transaction?> = flowOf(null)
        override fun observeDayTotals(): Flow<Map<LocalDate, Money>> = flowOf(emptyMap())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun insertOwedRow(
        amountMinor: Long,
        owedMinor: Long = amountMinor,
        counterparty: String,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        kind: TransactionKind = TransactionKind.NORMAL,
        merchantRaw: String? = null,
    ) = db.transactionDao().upsert(
        TransactionEntity(
            uuid = "t-${seq++}",
            accountId = if (kind == TransactionKind.IOU) 0L else 1L,
            amountMinor = amountMinor,
            direction = direction,
            occurredAt = 1_000L + seq,
            merchantRaw = merchantRaw,
            merchantId = null,
            categoryId = null,
            note = null,
            counterparty = counterparty,
            owedMinor = owedMinor,
            source = TransactionSource.MANUAL,
            confidence = Confidence.HIGH,
            rawMessageId = null,
            transferGroupId = null,
            feeMinor = null,
            referenceNumber = null,
            kind = kind,
            createdAt = 1L,
            updatedAt = 1L,
        ),
    )

    private fun viewModel() = OwedViewModel(
        transactionDao = db.transactionDao(),
        transactionRepository = transactionRepository,
        referenceDataRepository = referenceData,
        clock = clock,
    )

    @Test
    fun `selecting a person loads their owed entries and preselects Cash account`() = runTest(dispatcher) {
        insertOwedRow(amountMinor = 100_000, owedMinor = 50_000, counterparty = "Rafi", merchantRaw = "Dinner")
        val vm = viewModel()

        vm.state.test {
            advanceUntilIdle()
            vm.onSelectPerson("Rafi")
            advanceUntilIdle()

            val detail = expectMostRecentItem().selectedPerson
            assertNotNull(detail)
            assertEquals("Rafi", detail!!.person.name)
            assertEquals(Money(50_000), detail.person.amount)
            assertEquals(1, detail.entries.size)
            assertEquals("Dinner", detail.entries.single().label)
            assertEquals(2L, detail.selectedAccountId)
            assertEquals("Cash", detail.accounts.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `settling someone who owes you writes a CREDIT draft and closes the sheet`() = runTest(dispatcher) {
        insertOwedRow(amountMinor = 50_000, owedMinor = 50_000, counterparty = "Rafi")
        val vm = viewModel()

        vm.state.test {
            advanceUntilIdle()
            vm.onSelectPerson("Rafi")
            advanceUntilIdle()
            vm.onSelectSettleAccount(1L)
            advanceUntilIdle()

            vm.onSettleSelectedPerson()
            advanceUntilIdle()

            val draft = savedDraft
            assertNotNull(draft)
            assertEquals(1L, draft!!.accountId)
            assertEquals(Money(50_000), draft.amount)
            assertEquals(Money(50_000), draft.owed)
            assertEquals(TransactionDirection.CREDIT, draft.direction)
            assertEquals(TransactionKind.NORMAL, draft.kind)
            assertEquals("Rafi", draft.counterparty)
            assertEquals("Settled up", draft.note)
            assertNull(expectMostRecentItem().selectedPerson)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `settling someone you owe writes a DEBIT draft`() = runTest(dispatcher) {
        insertOwedRow(
            amountMinor = 20_000,
            owedMinor = 20_000,
            counterparty = "Sadia",
            kind = TransactionKind.IOU,
        )
        val vm = viewModel()

        vm.state.test {
            advanceUntilIdle()
            vm.onSelectPerson("Sadia")
            advanceUntilIdle()

            vm.onSettleSelectedPerson()
            advanceUntilIdle()

            val draft = savedDraft
            assertNotNull(draft)
            assertEquals(2L, draft!!.accountId)
            assertEquals(Money(20_000), draft.amount)
            assertEquals(Money(20_000), draft.owed)
            assertEquals(TransactionDirection.DEBIT, draft.direction)
            assertEquals(TransactionKind.NORMAL, draft.kind)
            assertEquals("Sadia", draft.counterparty)
            assertEquals("Settled up", draft.note)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
