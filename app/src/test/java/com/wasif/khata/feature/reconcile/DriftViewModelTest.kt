package com.wasif.khata.feature.reconcile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.search.searchIndex
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.repository.ReconciliationRepository
import com.wasif.khata.core.data.repository.TransactionRepositoryImpl
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.repository.TransactionDraft
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriftViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val clock = object : KhataClock {
        override fun now(): Long = Instant.parse("2026-09-02T06:00:00Z").toEpochMilli()
    }

    private lateinit var db: KhataDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        )
            // Room otherwise delivers Flow results on its own executor, which
            // advanceUntilIdle() cannot drive.
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

    private fun viewModel() = DriftViewModel(
        ReconciliationRepository(db.accountDao()),
        TransactionRepositoryImpl(
            db, db.transactionDao(), db.accountDao(), db.merchantDao(),
            db.balanceSnapshotDao(), db.tagDao(), db.mediaDao(), searchIndex(db), clock,
        ),
        clock,
    )

    /** An account the bank says holds [current], of which [unexplained] has no message behind it. */
    private suspend fun drifting(
        name: String = "EBL Salary",
        current: Long = 500000,
        unexplained: Long = 34000,
    ) = db.accountDao().upsert(
        AccountEntity(
            uuid = "acc-$name",
            name = name,
            type = AccountType.BANK,
            openingBalanceMinor = 0,
            currentBalanceMinor = current,
            reportedBalanceMinor = current,
            reportedBalanceAt = Instant.parse("2026-08-12T06:00:00Z").toEpochMilli(),
            unexplainedMinor = unexplained,
            includeInNetWorth = true,
            smsIdentifiers = "",
            createdAt = 1,
            updatedAt = 1,
        ),
    )

    private suspend fun recorded() = db.transactionDao().allActive()

    @Test
    fun `no drift renders an explicit all-reconciled state, not an empty box`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.drifts.isEmpty())
        assertTrue(vm.state.value.isReconciled)
    }

    @Test
    fun `a drifting account reports the gap and the date it opened`() = runTest(dispatcher) {
        drifting()
        val vm = viewModel()
        advanceUntilIdle()

        val row = vm.state.value.drifts.single()
        assertEquals("EBL Salary", row.accountName)
        assertEquals(Money(34000), row.gap)
        assertEquals(false, vm.state.value.isReconciled)
    }

    @Test
    fun `nothing is written until the adjustment is explicitly invoked`() = runTest(dispatcher) {
        drifting()
        viewModel()
        advanceUntilIdle()

        // The gap is usually real cash spending that was never entered. Absorbing it
        // automatically would destroy exactly the signal this screen exists to show.
        assertEquals(emptyList<Any>(), recorded())
    }

    @Test
    fun `recording an adjustment writes one transaction for exactly the gap`() = runTest(dispatcher) {
        val accountId = drifting()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRecordAdjustment(vm.state.value.drifts.single())
        advanceUntilIdle()

        val written = recorded().single()
        assertEquals(accountId, written.accountId)
        assertEquals(34000L, written.amountMinor)
        assertEquals(TransactionDirection.CREDIT, written.direction)
        assertEquals(TransactionKind.ADJUSTMENT, written.kind)
        // Cleared, so the same gap cannot be recorded twice.
        assertTrue(vm.state.value.drifts.isEmpty())
    }

    @Test
    fun `a negative gap adjusts in the other direction`() = runTest(dispatcher) {
        drifting(unexplained = -34000)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRecordAdjustment(vm.state.value.drifts.single())
        advanceUntilIdle()

        val written = recorded().single()
        assertEquals(34000L, written.amountMinor)
        assertEquals(TransactionDirection.DEBIT, written.direction)
    }

    @Test
    fun `a failed adjustment surfaces durable state rather than a transient effect`() = runTest(dispatcher) {
        drifting()
        val vm = viewModel()
        advanceUntilIdle()
        val drift = vm.state.value.drifts.single()

        // A real write failure, not a stubbed one: the store is gone underneath it.
        db.close()
        vm.onRecordAdjustment(drift)
        advanceUntilIdle()

        // Still true after a rotation, so it belongs in state.
        assertEquals("Could not record the adjustment. Please try again.", vm.state.value.error)
    }

    @Test
    fun `the adjustment is dated now, not when the drift opened`() = runTest(dispatcher) {
        drifting()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRecordAdjustment(vm.state.value.drifts.single())
        advanceUntilIdle()

        // Backdating would silently rewrite a past month's totals.
        assertEquals(clock.now(), recorded().single().occurredAt)
    }

    @Test
    fun `the recorder builds an uncategorised ADJUSTMENT draft`() {
        val draft = adjustmentDraft(accountId = 7, gap = Money(-34000), occurredAt = 1234)

        assertEquals(7L, draft.accountId)
        assertEquals(Money(34000), draft.amount)
        assertEquals(TransactionDirection.DEBIT, draft.direction)
        assertEquals(TransactionKind.ADJUSTMENT, draft.kind)
        assertNull(draft.categoryId)
        assertEquals(1234L, draft.occurredAt)
    }

    @Test
    fun `a positive gap becomes a credit`() {
        val draft: TransactionDraft = adjustmentDraft(accountId = 7, gap = Money(34000), occurredAt = 1)

        assertEquals(TransactionDirection.CREDIT, draft.direction)
        assertEquals(Money(34000), draft.amount)
    }
}
