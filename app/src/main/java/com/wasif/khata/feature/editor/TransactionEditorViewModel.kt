package com.wasif.khata.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
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
    private val originalMessage: OriginalMessage,
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
                val ordered = accounts.sortedBy { it.type != AccountType.CASH }
                _uiState.update { state ->
                    // Preselect Cash (first in ordered) so a new entry is one field closer to saveable.
                    state.copy(
                        accounts = ordered,
                        accountId = state.accountId ?: ordered.firstOrNull()?.id,
                    )
                }
            }
        }
        viewModelScope.launch {
            referenceData.observeCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            repository.observeRecentCounterparties(6).collect { people ->
                _uiState.update { it.copy(recentPeople = people) }
            }
        }
        transactionId?.let { id ->
            viewModelScope.launch {
                repository.observe(id).collect { existing ->
                    if (existing == null) return@collect
                    val splitInput = if (
                        existing.owed.minor > 0L &&
                        existing.owed.minor < existing.amount.minor
                    ) {
                        existing.owed.format(withSymbol = false)
                    } else {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            amountInput = existing.amount.format(withSymbol = false),
                            merchantInput = existing.merchantRaw.orEmpty(),
                            noteInput = existing.note.orEmpty(),
                            accountId = existing.accountId.takeIf { accId -> accId != 0L } ?: it.accountId,
                            categoryId = existing.categoryId,
                            kind = existing.kind,
                            counterpartyInput = existing.counterparty.orEmpty(),
                            customOwedInput = splitInput,
                            direction = existing.direction,
                            occurredAt = existing.occurredAt,
                        )
                    }

                    // Read once. The message it was parsed from does not change, and
                    // observe() re-emits on every edit.
                    val rawId = existing.rawMessageId
                    if (rawId != null && _uiState.value.originalMessage == null) {
                        val body = originalMessage.forRawMessage(rawId)
                        _uiState.update { it.copy(originalMessage = body) }
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
        onModeChange(if (direction == TransactionDirection.DEBIT) EditorMode.SPENT else EditorMode.RECEIVED)

    override fun onModeChange(mode: EditorMode) = _uiState.update { state ->
        when (mode) {
            EditorMode.SPENT -> state.copy(
                direction = TransactionDirection.DEBIT,
                kind = TransactionKind.NORMAL,
            )
            EditorMode.RECEIVED -> state.copy(
                direction = TransactionDirection.CREDIT,
                kind = TransactionKind.NORMAL,
                customOwedInput = null,
            )
            EditorMode.THEY_PAID -> state.copy(
                direction = TransactionDirection.DEBIT,
                kind = TransactionKind.IOU,
                customOwedInput = null,
            )
        }
    }

    override fun onKindChange(kind: TransactionKind) = _uiState.update { it.copy(kind = kind) }

    override fun onCounterpartyChange(value: String) =
        _uiState.update {
            it.copy(
                counterpartyInput = value,
                customOwedInput = if (value.isBlank()) null else it.customOwedInput,
            )
        }

    override fun onSelectAllOwed() = _uiState.update { it.copy(customOwedInput = null) }

    override fun onSelectSplitOwed() = _uiState.update { state ->
        if (state.customOwedInput != null) return@update state
        val halfMinor = (state.amount?.minor ?: 0L) / 2
        state.copy(customOwedInput = Money(halfMinor).format(withSymbol = false))
    }

    override fun onCustomOwedChange(value: String) =
        _uiState.update { it.copy(customOwedInput = value) }

    override fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        val amount = state.amount ?: return
        val accountId = if (state.isIou) 0L else (state.accountId ?: return)

        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            val result = repository.save(
                TransactionDraft(
                    id = transactionId,
                    accountId = accountId,
                    amount = amount,
                    direction = state.direction,
                    occurredAt = state.occurredAt,
                    merchantRaw = state.merchantInput.takeIf { it.isNotBlank() && (state.wantsMerchant || state.isEditing) },
                    categoryId = state.categoryId,
                    note = state.noteInput.takeIf { it.isNotBlank() },
                    counterparty = state.counterpartyInput.trim().takeIf { it.isNotBlank() },
                    owed = state.owedAmount,
                    kind = state.kind,
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
