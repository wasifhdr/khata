package com.wasif.khata.feature.ruleeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.sms.FieldKind
import com.wasif.khata.core.sms.LabelledSpan
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val BODY = "Bill payment of Tk 1,240.50 to DESCO METER done"

class RuleEditorScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private fun baseState(
        spans: List<LabelledSpan> = emptyList(),
        pending: IntRange? = null,
        name: String = "bKash rule",
        reparseSummary: String? = null,
    ) = RuleEditorUiState(
        sender = "bKash",
        body = BODY,
        tokens = tokenise(BODY),
        pending = pending,
        spans = spans,
        name = name,
        reparseSummary = reparseSummary,
    )

    private fun amountSpan(): LabelledSpan {
        val start = BODY.indexOf("Tk 1,240.50")
        return LabelledSpan(start, start + "Tk 1,240.50".length, FieldKind.AMOUNT)
    }

    private fun setContent(
        state: RuleEditorUiState,
        onTokenTapped: (Int) -> Unit = {},
        onFieldChosen: (FieldKind) -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeRule.setContent {
            KhataTheme {
                RuleEditorContent(
                    state = state,
                    onBack = {},
                    onTokenTapped = onTokenTapped,
                    onFieldChosen = onFieldChosen,
                    onSelectionCleared = {},
                    onDirectionChanged = {},
                    onKindChanged = {},
                    onNameChanged = {},
                    onSave = onSave,
                    onDone = {},
                )
            }
        }
    }

    @Test
    fun the_message_is_shown_as_tappable_words() {
        setContent(baseState())

        composeRule.onNodeWithText("DESCO").assertIsDisplayed()
        composeRule.onNodeWithText("payment").assertIsDisplayed()
    }

    @Test
    fun tapping_a_word_reports_its_index() {
        var tapped: Int? = null
        setContent(baseState(), onTokenTapped = { tapped = it })

        composeRule.onNodeWithText("Bill").performClick()

        assertEquals(0, tapped)
    }

    @Test
    fun with_nothing_selected_the_screen_asks_for_the_amount() {
        setContent(baseState())

        composeRule
            .onNodeWithText("Tap the amount in the message", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun a_pending_selection_offers_the_field_labels() {
        setContent(baseState(pending = 3..4))

        composeRule.onNodeWithText("Now say what that is.").assertIsDisplayed()
        composeRule.onNodeWithText("Amount").assertIsDisplayed()
        composeRule.onNodeWithText("Merchant").assertIsDisplayed()
    }

    @Test
    fun choosing_a_label_reports_the_field() {
        var chosen: FieldKind? = null
        setContent(baseState(pending = 3..4), onFieldChosen = { chosen = it })

        composeRule.onNodeWithText("Amount").performClick()

        assertEquals(FieldKind.AMOUNT, chosen)
    }

    @Test
    fun a_labelled_word_announces_its_field_so_colour_is_not_the_only_signal() {
        setContent(baseState(spans = listOf(amountSpan())))

        composeRule.onNodeWithContentDescription("1,240.50, Amount").assertIsDisplayed()
    }

    @Test
    fun what_khata_reads_is_shown_back_in_words() {
        setContent(baseState(spans = listOf(amountSpan())))

        composeRule.onNodeWithText("Amount: Tk 1,240.50").assertIsDisplayed()
    }

    @Test
    fun saving_is_blocked_until_an_amount_is_labelled() {
        setContent(baseState())

        composeRule.onNodeWithText("Save and re-read history").assertIsNotEnabled()
    }

    @Test
    fun saving_reports_the_action_once_an_amount_is_labelled() {
        var saved = false
        setContent(baseState(spans = listOf(amountSpan())), onSave = { saved = true })

        composeRule.onNodeWithText("Save and re-read history").performClick()

        assertEquals(true, saved)
    }

    @Test
    fun after_saving_the_reparse_outcome_is_shown_rather_than_the_save_button() {
        setContent(baseState(spans = listOf(amountSpan()), reparseSummary = "1 recorded · 0 still unread"))

        composeRule.onNodeWithText("Rule saved").assertIsDisplayed()
        composeRule.onNodeWithText("History re-read · 1 recorded · 0 still unread").assertIsDisplayed()
    }

    @Test
    fun ignore_is_not_offered_as_a_kind() {
        setContent(baseState(spans = listOf(amountSpan())))

        // A hand-made rule teaches Khata to read a message, not to discard one.
        composeRule.onNodeWithText(RuleKind.IGNORE.name).assertDoesNotExist()
    }

    @Test
    fun direction_is_two_labelled_choices_not_a_switch() {
        setContent(baseState(spans = listOf(amountSpan())))

        composeRule.onNodeWithText("Out").assertIsDisplayed()
        composeRule.onNodeWithText("In").assertIsDisplayed()
        assertEquals(TransactionDirection.DEBIT, baseState().direction)
    }
}
