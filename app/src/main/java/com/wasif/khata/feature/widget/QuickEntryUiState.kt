package com.wasif.khata.feature.widget

import androidx.compose.runtime.Stable
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.domain.model.Category

const val EXTRA_DIRECTION = "direction"

data class QuickEntryUiState(
    val amountInput: String = "",
    val noteInput: String = "",
    val categoryId: Long? = null,
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    // Derived as getters, never constructor params, so no copy() can leave a
    // validity flag disagreeing with the input it describes.
    val amount: Money? get() = if (amountInput.isBlank()) null else Money.parse(amountInput)

    val canSave: Boolean get() = amount.let { it != null && !it.isZero } && !isSaving

    /** The word on the sheet, matching the word on the widget target that opened it. */
    val heading: String
        get() = if (direction == TransactionDirection.DEBIT) "Cash spent" else "Cash received"
}

sealed interface QuickEntryEffect {
    data object Saved : QuickEntryEffect
}

@Stable
interface QuickEntryActions {
    fun onAmountKey(key: Char)
    fun onBackspace()
    fun onAmountChange(value: String)
    fun onNoteChange(value: String)
    fun onCategorySelected(id: Long?)
    fun onSave()
}
