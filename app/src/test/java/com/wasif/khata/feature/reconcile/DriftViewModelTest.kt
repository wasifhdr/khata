package com.wasif.khata.feature.reconcile

import com.wasif.khata.core.data.repository.BalanceDrift
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.repository.TransactionDraft
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

class DriftViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val drift = MutableStateFlow(emptyList<BalanceDrift>())
    private var adjusted: Pair<Long, Money>? = null

    private val clock = object : KhataClock {
        override fun now(): Long = Instant.parse("2026-09-02T06:00:00Z").toEpochMilli()
    }

    private val recorder = object : AdjustmentRecorder {
        override suspend fun record(accountId: Long, gap: Money, occurredAt: Long): Result<Long> {
            adjusted = accountId to gap
            return Result.success(1L)
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = DriftViewModel({ drift }, recorder, clock)

    private fun drifting(
        id: Long = 1,
        name: String = "EBL Salary",
        computed: Long = 466000,
        reported: Long = 500000,
    ) = BalanceDrift(
        accountId = id,
        accountName = name,
        computed = Money(computed),
        reported = Money(reported),
        reportedAt = Instant.parse("2026-08-12T06:00:00Z").toEpochMilli(),
        gap = Money(reported - computed),
    )

    @Test
    fun `no drift renders an explicit all-reconciled state, not an empty box`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.drifts.isEmpty())
        assertTrue(vm.state.value.isReconciled)
    }

    @Test
    fun `a drifting account reports the gap and the date it opened`() = runTest(dispatcher) {
        drift.value = listOf(drifting())
        val vm = viewModel()
        advanceUntilIdle()

        val row = vm.state.value.drifts.single()
        assertEquals("EBL Salary", row.accountName)
        assertEquals(Money(34000), row.gap)
        assertEquals(false, vm.state.value.isReconciled)
    }

    @Test
    fun `nothing is written until the adjustment is explicitly invoked`() = runTest(dispatcher) {
        drift.value = listOf(drifting())
        viewModel()
        advanceUntilIdle()

        // The gap is usually real cash spending that was never entered. Absorbing it
        // automatically would destroy exactly the signal this screen exists to show.
        assertNull(adjusted)
    }

    @Test
    fun `recording an adjustment writes one transaction for exactly the gap`() = runTest(dispatcher) {
        drift.value = listOf(drifting())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRecordAdjustment(vm.state.value.drifts.single())
        advanceUntilIdle()

        assertEquals(1L to Money(34000), adjusted)
    }

    @Test
    fun `a negative gap adjusts in the other direction`() = runTest(dispatcher) {
        drift.value = listOf(drifting(computed = 500000, reported = 466000))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRecordAdjustment(vm.state.value.drifts.single())
        advanceUntilIdle()

        assertEquals(Money(-34000), adjusted?.second)
    }

    @Test
    fun `a failed adjustment surfaces durable state rather than a transient effect`() = runTest(dispatcher) {
        val failing = object : AdjustmentRecorder {
            override suspend fun record(accountId: Long, gap: Money, occurredAt: Long) =
                Result.failure<Long>(IllegalStateException("nope"))
        }
        drift.value = listOf(drifting())
        val vm = DriftViewModel({ drift }, failing, clock)
        advanceUntilIdle()

        vm.onRecordAdjustment(vm.state.value.drifts.single())
        advanceUntilIdle()

        // Still true after a rotation, so it belongs in state.
        assertEquals("Could not record the adjustment. Please try again.", vm.state.value.error)
    }

    @Test
    fun `the adjustment is dated now, not when the drift opened`() = runTest(dispatcher) {
        var seenOccurredAt: Long? = null
        val capturing = object : AdjustmentRecorder {
            override suspend fun record(accountId: Long, gap: Money, occurredAt: Long): Result<Long> {
                seenOccurredAt = occurredAt
                return Result.success(1L)
            }
        }
        drift.value = listOf(drifting())
        val vm = DriftViewModel({ drift }, capturing, clock)
        advanceUntilIdle()

        vm.onRecordAdjustment(vm.state.value.drifts.single())
        advanceUntilIdle()

        // Backdating would silently rewrite a past month's totals.
        assertEquals(clock.now(), seenOccurredAt)
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
