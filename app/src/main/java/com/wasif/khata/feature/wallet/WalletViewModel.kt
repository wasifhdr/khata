package com.wasif.khata.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WalletViewModel @Inject constructor(
    transactions: TransactionRepository,
    reference: ReferenceDataRepository,
    clock: KhataClock,
) : ViewModel() {

    private val now = clock.now()

    val state: StateFlow<WalletUiState> = combine(
        reference.observeNetWorth(),
        transactions.observeSpentBetween(now.dhakaMonthStart(), now.dhakaNextMonthStart()),
        transactions.observeReceivedBetween(now.dhakaMonthStart(), now.dhakaNextMonthStart()),
        reference.observeAccounts(),
    ) { netWorth, spend, received, accounts ->
        WalletUiState(
            netWorth = netWorth,
            monthSpend = spend,
            monthReceived = received,
            accounts = accounts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WalletUiState(),
    )
}
