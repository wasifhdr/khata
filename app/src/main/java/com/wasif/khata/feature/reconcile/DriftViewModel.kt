package com.wasif.khata.feature.reconcile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.repository.BalanceDrift
import com.wasif.khata.core.data.repository.ReconciliationRepository
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriftUiState(
    val drifts: List<BalanceDrift> = emptyList(),
    val isLoaded: Boolean = false,
    val recordingFor: Long? = null,
    val error: String? = null,
) {
    /** Only meaningful once loaded: an empty list before that is "not yet", not "agreed". */
    val isReconciled: Boolean get() = drifts.isEmpty()
}

/**
 * The gap is expressed as reported minus computed, so a positive gap means the bank
 * holds more than Khata recorded and the correction is a credit.
 */
fun adjustmentDraft(accountId: Long, gap: Money, occurredAt: Long) = TransactionDraft(
    id = null,
    accountId = accountId,
    amount = gap.abs(),
    direction = if (gap.minor >= 0) TransactionDirection.CREDIT else TransactionDirection.DEBIT,
    occurredAt = occurredAt,
    merchantRaw = null,
    // Uncategorised on purpose: the gap is unexplained by definition, and inventing a
    // category would put fictional money in a real breakdown.
    categoryId = null,
    note = "Balance adjustment",
    kind = TransactionKind.ADJUSTMENT,
)

@HiltViewModel
class DriftViewModel @Inject constructor(
    reconciliation: ReconciliationRepository,
    private val transactions: TransactionRepository,
    private val clock: KhataClock,
) : ViewModel() {

    private val _state = MutableStateFlow(DriftUiState())
    val state: StateFlow<DriftUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            reconciliation.observeDrift().collect { drifts ->
                _state.update { it.copy(drifts = drifts, isLoaded = true) }
            }
        }
    }

    fun onRecordAdjustment(drift: BalanceDrift) {
        if (_state.value.recordingFor != null) return
        _state.update { it.copy(recordingFor = drift.accountId, error = null) }

        viewModelScope.launch {
            // Dated now rather than when the drift opened: backdating would silently
            // rewrite a past month's totals.
            val result = transactions.recordUnexplained(
                adjustmentDraft(drift.accountId, drift.gap, clock.now()),
            )
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(recordingFor = null) },
                    onFailure = {
                        current.copy(
                            recordingFor = null,
                            error = "Could not record the adjustment. Please try again.",
                        )
                    },
                )
            }
        }
    }
}
