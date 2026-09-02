package com.wasif.khata.feature.reconcile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wasif.khata.core.data.repository.BalanceDrift
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.ui.theme.KhataTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DriftScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private fun drift(
        id: Long = 1,
        name: String = "EBL Salary",
        computed: Long = 466_000,
        reported: Long = 500_000,
    ) = BalanceDrift(
        accountId = id,
        accountName = name,
        computed = Money(computed),
        reported = Money(reported),
        reportedAt = Instant.parse("2026-08-12T06:00:00Z").toEpochMilli(),
    )

    private fun setContent(state: DriftUiState, onRecord: (BalanceDrift) -> Unit = {}) {
        composeRule.setContent {
            KhataTheme {
                DriftContent(state = state, onBack = {}, onRecordAdjustment = onRecord)
            }
        }
    }

    @Test
    fun reconciled_state_says_so_rather_than_rendering_an_empty_screen() {
        setContent(DriftUiState(drifts = emptyList(), isLoaded = true))

        composeRule.onNodeWithText("Every balance matches").assertIsDisplayed()
    }

    @Test
    fun a_drifting_account_shows_its_name_the_gap_and_the_date() {
        setContent(DriftUiState(drifts = listOf(drift()), isLoaded = true))

        composeRule.onNodeWithText("EBL Salary").assertIsDisplayed()
        composeRule.onNodeWithText("৳340.00").assertIsDisplayed()
        composeRule.onNodeWithText("12 August", substring = true).assertIsDisplayed()
    }

    @Test
    fun the_direction_of_the_gap_is_stated_in_words_not_only_colour() {
        setContent(DriftUiState(drifts = listOf(drift()), isLoaded = true))

        composeRule.onNodeWithText("more than", substring = true).assertIsDisplayed()
    }

    @Test
    fun a_negative_gap_reads_as_less_than() {
        setContent(
            DriftUiState(drifts = listOf(drift(computed = 500_000, reported = 466_000)), isLoaded = true),
        )

        composeRule.onNodeWithText("less than", substring = true).assertIsDisplayed()
    }

    @Test
    fun recording_the_difference_reports_the_drift_it_was_invoked_for() {
        var recorded: BalanceDrift? = null
        setContent(DriftUiState(drifts = listOf(drift()), isLoaded = true)) { recorded = it }

        composeRule
            .onNodeWithContentDescription("Record the difference for EBL Salary as an adjustment")
            .performClick()

        assertEquals(1L, recorded?.accountId)
    }

    @Test
    fun an_error_is_shown_as_durable_text_rather_than_a_transient_message() {
        setContent(
            DriftUiState(
                drifts = listOf(drift()),
                isLoaded = true,
                error = "Could not record the adjustment. Please try again.",
            ),
        )

        composeRule
            .onNodeWithText("Could not record the adjustment. Please try again.")
            .assertIsDisplayed()
    }

    @Test
    fun the_subline_counts_the_disagreeing_accounts() {
        setContent(
            DriftUiState(drifts = listOf(drift(), drift(id = 2, name = "bKash")), isLoaded = true),
        )

        composeRule.onNodeWithText("2 accounts disagree").assertIsDisplayed()
    }
}
