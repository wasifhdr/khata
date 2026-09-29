package com.wasif.khata.feature.settle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import com.wasif.khata.feature.editor.OriginalMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettleTransferUiState(
    val amount: Money = Money.ZERO,
    val merchantRaw: String? = null,
    val originalMessage: String? = null,
    val choices: List<Account> = emptyList(),
    val recentPeople: List<String> = emptyList(),
    val counterpartyInput: String = "",
    val done: Boolean = false,
)

@HiltViewModel(assistedFactory = SettleTransferViewModel.Factory::class)
class SettleTransferViewModel @AssistedInject constructor(
    private val repository: TransactionRepository,
    private val referenceData: ReferenceDataRepository,
    private val originalMessage: OriginalMessage,
    @Assisted private val transactionId: Long,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(transactionId: Long): SettleTransferViewModel
    }

    private val _state = MutableStateFlow(SettleTransferUiState())
    val state: StateFlow<SettleTransferUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val row = repository.observe(transactionId).first() ?: return@launch
            val accounts = referenceData.observeAccounts().first()
            val recentPeople = repository.observeRecentCounterparties(6).first()
            _state.update {
                it.copy(
                    amount = row.amount,
                    merchantRaw = row.merchantRaw,
                    // Frequently the only thing that will remind anybody what a
                    // three-day-old "EBL Account Transfer" actually was.
                    originalMessage = row.rawMessageId?.let { id -> originalMessage.forRawMessage(id) },
                    // Never the account it left: money cannot move to itself, and
                    // offering it invites a transfer that balances to nothing.
                    choices = accounts.filterNot { account -> account.id == row.accountId },
                    recentPeople = recentPeople,
                )
            }
        }
    }

    fun onAccountChosen(accountId: Long) {
        viewModelScope.launch {
            repository.settleAsOwnTransfer(transactionId, accountId)
            _state.update { it.copy(done = true) }
        }
    }

    fun onCounterpartyChange(name: String) {
        _state.update { it.copy(counterpartyInput = name) }
    }

    fun onSettleAsOwed() {
        val who = _state.value.counterpartyInput.trim()
        if (who.isEmpty()) return
        viewModelScope.launch {
            repository.settleAsOwed(transactionId, who)
            _state.update { it.copy(done = true) }
        }
    }

    fun onNotMine() {
        viewModelScope.launch {
            repository.dismissTransferReview(transactionId)
            _state.update { it.copy(done = true) }
        }
    }
}
