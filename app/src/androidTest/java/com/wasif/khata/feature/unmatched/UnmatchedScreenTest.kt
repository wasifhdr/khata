package com.wasif.khata.feature.unmatched

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wasif.khata.core.ui.theme.KhataTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UnmatchedScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private fun message(
        id: Long = 1,
        sender: String = "bKash",
        body: String = "Some entirely new wording nobody planned for",
    ) = UnmatchedMessage(
        id = id,
        sender = sender,
        body = body,
        receivedAt = Instant.parse("2026-08-12T06:00:00Z").toEpochMilli(),
    )

    private fun setContent(
        state: UnmatchedUiState,
        onWriteRule: (Long) -> Unit = {},
        onNotATransaction: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            KhataTheme {
                UnmatchedContent(
                    state = state,
                    onBack = {},
                    onWriteRule = onWriteRule,
                    onNotATransaction = onNotATransaction,
                )
            }
        }
    }

    @Test
    fun an_empty_list_explains_itself_rather_than_rendering_blank() {
        setContent(UnmatchedUiState(messages = emptyList(), isLoaded = true))

        composeRule.onNodeWithText("Nothing unread").assertIsDisplayed()
    }

    @Test
    fun a_message_shows_its_sender_body_and_date() {
        setContent(UnmatchedUiState(messages = listOf(message()), isLoaded = true))

        composeRule.onNodeWithText("bKash").assertIsDisplayed()
        composeRule.onNodeWithText("Some entirely new wording nobody planned for").assertIsDisplayed()
        composeRule.onNodeWithText("12 Aug 2026").assertIsDisplayed()
    }

    @Test
    fun writing_a_rule_reports_the_message_it_was_invoked_for() {
        var chosen: Long? = null
        // Named, not a trailing lambda: there are two callbacks now and a trailing
        // lambda binds to the last one.
        setContent(
            UnmatchedUiState(messages = listOf(message(id = 42)), isLoaded = true),
            onWriteRule = { chosen = it },
        )

        composeRule.onNodeWithText("Write a rule").performClick()

        assertEquals(42L, chosen)
    }

    @Test
    fun the_subline_counts_the_messages_without_a_rule() {
        setContent(
            UnmatchedUiState(
                messages = listOf(message(id = 1), message(id = 2, body = "another")),
                isLoaded = true,
            ),
        )

        composeRule.onNodeWithText("2 messages without a rule").assertIsDisplayed()
    }

    @Test
    fun one_message_is_singular() {
        setContent(UnmatchedUiState(messages = listOf(message()), isLoaded = true))

        composeRule.onNodeWithText("1 message without a rule").assertIsDisplayed()
    }

    @Test
    fun a_bengali_body_still_renders_its_text() {
        setContent(
            UnmatchedUiState(messages = listOf(message(body = "আপনার হিসাব")), isLoaded = true),
        )

        composeRule.onNodeWithText("আপনার হিসাব").assertIsDisplayed()
    }

    @Test
    fun every_message_offers_its_own_write_a_rule_action() {
        setContent(
            UnmatchedUiState(
                messages = listOf(message(id = 1), message(id = 2, body = "another")),
                isLoaded = true,
            ),
        )

        assertEquals(2, composeRule.onAllNodesWithText("Write a rule").fetchSemanticsNodes().size)
    }

    @Test
    fun every_message_offers_both_answers() {
        setContent(UnmatchedUiState(messages = listOf(message()), isLoaded = true))

        // Most of what a bank sends is not a transaction, so "teach me" cannot be
        // the only way out of this list.
        composeRule.onNodeWithText("Write a rule").assertIsDisplayed()
        composeRule.onNodeWithText("Not a transaction").assertIsDisplayed()
    }

    @Test
    fun not_a_transaction_reports_the_message_it_was_tapped_for() {
        var hidden: Long? = null
        setContent(
            UnmatchedUiState(messages = listOf(message(id = 42)), isLoaded = true),
            onNotATransaction = { hidden = it },
        )

        composeRule.onNodeWithText("Not a transaction").performClick()

        assertEquals(42L, hidden)
    }

    @Test
    fun what_the_last_action_did_is_said_in_words() {
        setContent(
            UnmatchedUiState(
                messages = listOf(message()),
                isLoaded = true,
                notice = "Hidden, along with 108 others like it.",
            ),
        )

        composeRule.onNodeWithText("Hidden, along with 108 others like it.").assertIsDisplayed()
    }

    @Test
    fun a_notice_is_shown_even_once_the_list_is_empty() {
        // Hiding the last message empties the list; without this the confirmation
        // would vanish at the exact moment it matters most.
        setContent(
            UnmatchedUiState(messages = emptyList(), isLoaded = true, notice = "Hidden."),
        )

        composeRule.onNodeWithText("Hidden.").assertIsDisplayed()
    }

    @Test
    fun both_actions_are_disabled_while_one_is_running() {
        setContent(UnmatchedUiState(messages = listOf(message()), isLoaded = true, isWorking = true))

        composeRule.onNodeWithText("Write a rule").assertIsNotEnabled()
        composeRule.onNodeWithText("Not a transaction").assertIsNotEnabled()
    }
}
