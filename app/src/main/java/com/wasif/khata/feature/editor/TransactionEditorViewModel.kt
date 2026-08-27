package com.wasif.khata.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Hilt needs the assisted factory named here to resolve hiltViewModel's
// generic <VM, VMF> overload; without it, injection silently falls back
// to a no-arg constructor and crashes at runtime.
@HiltViewModel(assistedFactory = TransactionEditorViewModel.Factory::class)
class TransactionEditorViewModel @AssistedInject constructor(
    private val repository: TransactionRepository,
    private val referenceData: ReferenceDataRepository,
    private val clock: KhataClock,
    @Assisted private val transactionId: Long?,
) : ViewModel(), TransactionEditorActions {

    @AssistedFactory
    interface Factory {
        fun create(transactionId: Long?): TransactionEditorViewModel
    }

    private val _uiState = MutableStateFlow(
        TransactionEditorUiState(occurredAt = clock.now(), isEditing = transactionId != null)
    )
    val uiState: StateFlow<TransactionEditorUiState> = _uiState.asStateFlow()

    // Channel, not SharedFlow: an effect emitted while the screen is backgrounded
    // buffers and replays on resume instead of being dropped.
    private val _effects = Channel<TransactionEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            referenceData.observeAccounts().collect { accounts ->
                _uiState.update { state ->
                    // Preselect so a new entry is one field closer to saveable.
                    state.copy(
                        accounts = accounts,
                        accountId = state.accountId ?: accounts.firstOrNull()?.id,
                    )
                }
            }
        }
        viewModelScope.launch {
            referenceData.observeCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        transactionId?.let { id ->
            viewModelScope.launch {
                repository.observe(id).collect { existing ->
                    if (existing == null) return@collect
                    _uiState.update {
                        it.copy(
                            amountInput = existing.amount.format(withSymbol = false),
                            merchantInput = existing.merchantRaw.orEmpty(),
                            noteInput = existing.note.orEmpty(),
                            accountId = existing.accountId,
                            categoryId = existing.categoryId,
                            direction = existing.direction,
                            occurredAt = existing.occurredAt,
                        )
                    }
                }
            }
        }
    }

    override fun onAmountChange(value: String) =
        _uiState.update { it.copy(amountInput = value, saveError = null) }

    override fun onMerchantChange(value: String) = _uiState.update { it.copy(merchantInput = value) }

    override fun onNoteChange(value: String) = _uiState.update { it.copy(noteInput = value) }

    override fun onAccountSelected(id: Long) = _uiState.update { it.copy(accountId = id) }

    override fun onCategorySelected(id: Long?) = _uiState.update { it.copy(categoryId = id) }

    override fun onDirectionChange(direction: TransactionDirection) =
        _uiState.update { it.copy(direction = direction) }

    override fun onDateChange(epochMillis: Long) = _uiState.update { it.copy(occurredAt = epochMillis) }

    override fun onSave() {
        val state = _uiState.value
        val amount = state.amount ?: return
        val accountId = state.accountId ?: return

        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            val result = repository.save(
                TransactionDraft(
                    id = transactionId,
                    accountId = accountId,
                    amount = amount,
                    direction = state.direction,
                    occurredAt = state.occurredAt,
                    merchantRaw = state.merchantInput.takeIf { it.isNotBlank() },
                    categoryId = state.categoryId,
                    note = state.noteInput.takeIf { it.isNotBlank() },
                )
            )
            _uiState.update { it.copy(isSaving = false) }
            result.fold(
                onSuccess = { _effects.trySend(TransactionEditorEffect.Saved) },
                onFailure = {
                    _uiState.update { s -> s.copy(saveError = "Could not save. Please try again.") }
                },
            )
        }
    }

    override fun onDelete() {
        val id = transactionId ?: return
        viewModelScope.launch {
            repository.delete(id).fold(
                onSuccess = { _effects.trySend(TransactionEditorEffect.Deleted) },
                onFailure = {
                    _uiState.update { it.copy(saveError = "Could not delete. Please try again.") }
                },
            )
        }
    }
}
