package com.wasif.khata.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.repository.MonthLimits
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ModulesViewModel @Inject constructor(
    transactions: TransactionRepository,
    limits: MonthLimits,
    clock: KhataClock,
) : ViewModel() {

    private val now = clock.now()

    val state: StateFlow<ModulesUiState> = combine(
        transactions.observeSpentBetween(now.dhakaMonthStart(), now.dhakaNextMonthStart()),
        transactions.observeMostRecent(),
        limits(now.dhakaMonthStart()),
    ) { spend, last, limits ->
        // The sum of what every category is allowed, which is the only total there
        // is now -- the single global budget it replaced could disagree with the
        // categories underneath it about the same month.
        val total = if (limits.isEmpty()) null else limits.values.sum()
        ModulesUiState(
            monthSpend = spend,
            budgetFraction = when {
                // Absent, not zero: no limits set means no ring, as before.
                total == null -> null
                // Any spend against a zero total is over it. Dividing would
                // produce infinity and the ring would refuse to draw.
                total <= 0L -> 1f
                // Clamped, because overspending is real and must read as a full
                // ring rather than 150% of a circle.
                else -> (spend.minor.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            },
            lastTransaction = last,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModulesUiState(),
    )
}
