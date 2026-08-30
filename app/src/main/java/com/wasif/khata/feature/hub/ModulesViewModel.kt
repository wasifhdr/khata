package com.wasif.khata.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.prefs.PreferencesRepository
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
    preferences: PreferencesRepository,
    clock: KhataClock,
) : ViewModel() {

    private val now = clock.now()

    val state: StateFlow<ModulesUiState> = combine(
        transactions.observeSpentBetween(now.dhakaMonthStart(), now.dhakaNextMonthStart()),
        transactions.observeMostRecent(),
        preferences.preferences,
    ) { spend, last, prefs ->
        val budget = prefs.monthlyBudgetMinor
        ModulesUiState(
            monthSpend = spend,
            budgetFraction = when {
                budget == null -> null
                // Any spend against a zero budget is over it. Dividing would
                // produce infinity and the ring would refuse to draw.
                budget <= 0L -> 1f
                // Clamped, because overspending is real and must read as a full
                // ring rather than 150% of a circle.
                else -> (spend.minor.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
            },
            lastTransaction = last,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModulesUiState(),
    )
}
