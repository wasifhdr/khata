package com.wasif.khata.feature.editor

import androidx.compose.runtime.Stable
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category

enum class EditorMode(val label: String) {
    SPENT("Spent"),
    RECEIVED("Received"),
    THEY_PAID("They paid"),
}

data class TransactionEditorUiState(
    val amountInput: String = "",
    val merchantInput: String = "",
    val noteInput: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val kind: TransactionKind = TransactionKind.NORMAL,
    val counterpartyInput: String = "",
    /** Null means "All of it" (full amount) when a person is attached; non-null is a custom split share. */
    val customOwedInput: String? = null,
    val occurredAt: Long = 0L,

    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recentPeople: List<String> = emptyList(),

    /** The SMS this row was parsed from. Null for a row typed by hand. */
    val originalMessage: String? = null,

    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    val amount: Money? get() = if (amountInput.isBlank()) null else Money.parse(amountInput)

    val amountHasError: Boolean get() = amountInput.isNotBlank() && amount == null

    val mode: EditorMode
        get() = when {
            kind == TransactionKind.IOU -> EditorMode.THEY_PAID
            direction == TransactionDirection.CREDIT -> EditorMode.RECEIVED
            else -> EditorMode.SPENT
        }

    /** An SMS-parsed row already moved a real account, so it cannot become a no-account IOU. */
    val availableModes: List<EditorMode>
        get() = if (isEditing && originalMessage != null && kind != TransactionKind.IOU) {
            listOf(EditorMode.SPENT, EditorMode.RECEIVED)
        } else {
            EditorMode.entries
        }

    val isIou: Boolean get() = kind == TransactionKind.IOU

    val hasPerson: Boolean get() = counterpartyInput.isNotBlank() || isIou || customOwedInput != null

    val isSplit: Boolean get() = mode == EditorMode.SPENT && customOwedInput != null

    val owedAmount: Money
        get() {
            val total = amount ?: return Money.ZERO
            if (!hasPerson) return Money.ZERO
            if (mode != EditorMode.SPENT || customOwedInput == null) return total
            val parsed = Money.parse(customOwedInput) ?: return Money.ZERO
            return Money(parsed.minor.coerceIn(0L, total.minor))
        }

    val wantsAccount: Boolean get() = !isIou

    val wantsMerchant: Boolean
        get() = direction == TransactionDirection.DEBIT

    val canSave: Boolean
        get() {
            val validAmount = amount.let { it != null && !it.isZero }
            val validAccount = isIou || accountId != null
            val validPerson = !isIou || counterpartyInput.isNotBlank()
            val validSplit = !isSplit || owedAmount.let { !it.isZero && it.minor <= (amount?.minor ?: 0L) }
            return validAmount && validAccount && validPerson && validSplit && !isSaving
        }
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
    fun onModeChange(mode: EditorMode)
    fun onKindChange(kind: TransactionKind)
    fun onCounterpartyChange(value: String)
    fun onSelectAllOwed()
    fun onSelectSplitOwed()
    fun onCustomOwedChange(value: String)
    fun onSave()
    fun onDelete()
}
