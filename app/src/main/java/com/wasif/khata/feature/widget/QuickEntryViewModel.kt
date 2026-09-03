package com.wasif.khata.feature.widget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.ui.component.appendAmountKey
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one DAO query the sheet needs, as a function type. Injecting the whole
 * `TransactionDao` would put a Room type in a ViewModel that otherwise talks only
 * to repositories, and would make the test build a database for one list.
 */
fun interface RecentCategoryIds {
    operator fun invoke(accountId: Long, limit: Int): Flow<List<Long>>
}

@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val reference: ReferenceDataRepository,
    recentCategoryIds: RecentCategoryIds,
    private val clock: KhataClock,
) : ViewModel(), QuickEntryActions {

    private val inputs = MutableStateFlow(
        QuickEntryUiState(
            // The widget face already asked. There is no control for this on the
            // sheet, so a mis-tap is a dismiss and a re-tap.
            direction = savedState.get<String>(EXTRA_DIRECTION)
                ?.let(TransactionDirection::valueOf)
                ?: TransactionDirection.DEBIT,
        ),
    )

    private val effectChannel = Channel<QuickEntryEffect>(Channel.BUFFERED)
    val effects: Flow<QuickEntryEffect> = effectChannel.receiveAsFlow()

    private val cashAccount = reference.observeAccounts()
        .map { accounts -> accounts.firstOrNull { it.type == AccountType.CASH } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val orderedCategories = cashAccount.flatMapLatest { account ->
        if (account == null) {
            reference.observeCategories()
        } else {
            combine(
                reference.observeCategories(),
                recentCategoryIds(account.id, RECENT_LIMIT),
            ) { categories, recent ->
                val rank = recent.withIndex().associate { (index, id) -> id to index }
                // sortedBy is stable, so everything unranked keeps its seeded order
                // rather than being shuffled by an arbitrary tiebreak.
                categories.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            }
        }
    }

    val state: StateFlow<QuickEntryUiState> =
        combine(inputs, orderedCategories) { input, categories ->
            input.copy(categories = categories)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = inputs.value,
        )

    override fun onAmountKey(key: Char) =
        inputs.update { it.copy(amountInput = appendAmountKey(it.amountInput, key)) }

    override fun onBackspace() =
        inputs.update { it.copy(amountInput = it.amountInput.dropLast(1)) }

    override fun onAmountChange(value: String) = inputs.update { it.copy(amountInput = value) }

    override fun onNoteChange(value: String) = inputs.update { it.copy(noteInput = value) }

    override fun onCategorySelected(id: Long?) = inputs.update {
        it.copy(categoryId = if (it.categoryId == id) null else id)
    }

    override fun onSave() {
        val amount = inputs.value.amount ?: return
        if (!inputs.value.canSave) return
        inputs.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            // Read at save time rather than holding the id in state: the account
            // list is a Room flow that is already warm, and a second copy of the id
            // is a second thing that can be stale.
            val account = reference.observeAccounts().first()
                .firstOrNull { it.type == AccountType.CASH }
            if (account == null) {
                inputs.update { it.copy(isSaving = false, saveError = "No cash account") }
                return@launch
            }

            val current = inputs.value
            transactions.save(
                TransactionDraft(
                    id = null,
                    accountId = account.id,
                    amount = amount,
                    direction = current.direction,
                    occurredAt = clock.now(),
                    merchantRaw = null,
                    categoryId = current.categoryId,
                    note = current.noteInput.ifBlank { null },
                    source = TransactionSource.WIDGET,
                ),
            ).onSuccess {
                inputs.update { it.copy(isSaving = false) }
                effectChannel.send(QuickEntryEffect.Saved)
            }.onFailure {
                // Durable, not a toast: the sheet must not close on a failed write.
                // The user is standing in a shop and the record is the whole point.
                inputs.update { it.copy(isSaving = false, saveError = "Could not save. Try again.") }
            }
        }
    }

    private companion object {
        const val RECENT_LIMIT = 8
    }
}
