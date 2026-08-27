package com.wasif.khata.feature.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionEditorScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val bkash = Account(
        id = 3,
        uuid = "acc-3",
        name = "bKash",
        type = AccountType.MFS,
        currentBalance = Money.ZERO,
        reportedBalance = null,
        includeInNetWorth = true,
    )

    private val groceries = Category(
        id = 11,
        uuid = "seed-cat-groceries",
        name = "Groceries",
        icon = "shopping_cart",
        colorToken = "category_green",
        parentId = null,
    )

    private class RecordingActions : TransactionEditorActions {
        var amount: String? = null
        var selectedCategory: Long? = null
        var saved = false
        override fun onAmountChange(value: String) { amount = value }
        override fun onMerchantChange(value: String) = Unit
        override fun onNoteChange(value: String) = Unit
        override fun onAccountSelected(id: Long) = Unit
        override fun onCategorySelected(id: Long?) { selectedCategory = id }
        override fun onDirectionChange(direction: TransactionDirection) = Unit
        override fun onDateChange(epochMillis: Long) = Unit
        override fun onSave() { saved = true }
        override fun onDelete() = Unit
    }

    private fun setContent(state: TransactionEditorUiState, actions: TransactionEditorActions) {
        composeRule.setContent {
            KhataTheme {
                TransactionEditorContent(state = state, actions = actions, onBack = {})
            }
        }
    }

    @Test
    fun `save is disabled while the form is incomplete`() {
        setContent(
            TransactionEditorUiState(accounts = listOf(bkash), categories = listOf(groceries)),
            RecordingActions(),
        )

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `save is enabled once an amount and account are set`() {
        setContent(
            TransactionEditorUiState(
                amountInput = "250",
                accountId = 3,
                accounts = listOf(bkash),
                categories = listOf(groceries),
            ),
            RecordingActions(),
        )

        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `typing an amount reports the change`() {
        val actions = RecordingActions()
        setContent(TransactionEditorUiState(accounts = listOf(bkash)), actions)

        composeRule.onNodeWithText("Amount").performTextInput("250")

        assertEquals("250", actions.amount)
    }

    @Test
    fun `an invalid amount shows guidance`() {
        setContent(
            TransactionEditorUiState(amountInput = "12.345", accountId = 3, accounts = listOf(bkash)),
            RecordingActions(),
        )

        composeRule.onNodeWithText("Enter an amount like 1234.56").assertExists()
    }

    @Test
    fun `tapping a category reports its id`() {
        val actions = RecordingActions()
        setContent(
            TransactionEditorUiState(accounts = listOf(bkash), categories = listOf(groceries)),
            actions,
        )

        composeRule.onNodeWithText("Groceries").performClick()

        assertEquals(11L, actions.selectedCategory)
    }

    @Test
    fun `tapping save invokes the action`() {
        val actions = RecordingActions()
        setContent(
            TransactionEditorUiState(amountInput = "250", accountId = 3, accounts = listOf(bkash)),
            actions,
        )

        composeRule.onNodeWithText("Save").performClick()

        assertTrue(actions.saved)
    }

    @Test
    fun `a save error is displayed`() {
        setContent(
            TransactionEditorUiState(
                accounts = listOf(bkash),
                saveError = "Could not save. Please try again.",
            ),
            RecordingActions(),
        )

        composeRule.onNodeWithText("Could not save. Please try again.").assertExists()
    }
}
