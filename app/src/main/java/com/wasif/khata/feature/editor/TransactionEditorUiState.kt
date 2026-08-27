package com.wasif.khata.feature.editor

import androidx.compose.runtime.Stable
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category

data class TransactionEditorUiState(
    val amountInput: String = "",
    val merchantInput: String = "",
    val noteInput: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val direction: TransactionDirection = TransactionDirection.DEBIT,
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
    fun onDateChange(epochMillis: Long)
    fun onSave()
    fun onDelete()
}
