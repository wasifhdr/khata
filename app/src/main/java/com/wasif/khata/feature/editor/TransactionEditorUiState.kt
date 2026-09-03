package com.wasif.khata.feature.editor

import androidx.compose.runtime.Stable
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category

data class TransactionEditorUiState(
    val amountInput: String = "",
    val merchantInput: String = "",
    val noteInput: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val kind: TransactionKind = TransactionKind.NORMAL,
    val counterpartyInput: String = "",
    val occurredAt: Long = 0L,

    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),

    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    // Derived as getters, never constructor params, so no copy() can leave a
    // validity flag disagreeing with the input it describes.
    val amount: Money? get() = if (amountInput.isBlank()) null else Money.parse(amountInput)

    val amountHasError: Boolean get() = amountInput.isNotBlank() && amount == null

    /**
     * The kinds worth offering for this direction, in the words they are thought
     * about rather than the enum's. Money owed in one direction or the other is a
     * different question from what was bought.
     */
    val kindChoices: List<Pair<TransactionKind, String>>
        get() = if (direction == TransactionDirection.DEBIT) {
            listOf(
                TransactionKind.NORMAL to "Spending",
                TransactionKind.LENT to "Lent to someone",
                TransactionKind.COVERED_FOR_SOMEONE to "Paid a bill for someone",
                TransactionKind.BORROWED_RETURNED to "Paid back what I borrowed",
            )
        } else {
            listOf(
                TransactionKind.NORMAL to "Earning",
                TransactionKind.REIMBURSEMENT to "Their share of a bill I paid",
                TransactionKind.LENT_RETURNED to "Loan I gave, returned",
                TransactionKind.BORROWED to "Borrowed from someone",
            )
        }

    /** Anything owed in either direction wants a name against it. */
    val wantsCounterparty: Boolean get() = kind != TransactionKind.NORMAL

    val counterpartyLabel: String
        get() = if (direction == TransactionDirection.DEBIT) "To whom" else "From whom"

    val canSave: Boolean
        get() = amount.let { it != null && !it.isZero } && accountId != null && !isSaving
}

sealed interface TransactionEditorEffect {
    data object Saved : TransactionEditorEffect
    data object Deleted : TransactionEditorEffect
}

@Stable
interface TransactionEditorActions {
    fun onAmountChange(value: String)
    fun onMerchantChange(value: String)
    fun onNoteChange(value: String)
    fun onAccountSelected(id: Long)
    fun onCategorySelected(id: Long?)
    fun onDirectionChange(direction: TransactionDirection)
    fun onKindChange(kind: TransactionKind)
    fun onCounterpartyChange(value: String)
    fun onSave()
    fun onDelete()
}
