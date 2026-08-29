package com.wasif.khata.feature.ledger

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.domain.model.Transaction
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LedgerScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun content(
        items: List<LedgerItem> = emptyList(),
        header: LedgerHeaderState = LedgerHeaderState(monthLabel = "August 2026", daysLeft = 3),
        canGoForward: Boolean = false,
        onPreviousMonth: () -> Unit = {},
        onNextMonth: () -> Unit = {},
    ): @Composable () -> Unit = {
        KhataTheme {
            LedgerContent(
                items = flowOf(PagingData.from(items)).collectAsLazyPagingItems(),
                header = header,
                categoryTokens = mapOf(11L to "category_green"),
                canGoForward = canGoForward,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onBack = {},
                onAddTransaction = {},
                onOpenTransaction = {},
            )
        }
    }

    @Test
    fun theHeadingNamesTheMonthNotTheScreen() {
        compose.setContent(content())

        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithText("3 days left").assertIsDisplayed()
        // "Ledger" as a title would spend the space telling the user what they
        // already know from having tapped to get here.
        val stale = compose.onAllNodesWithText("Ledger").fetchSemanticsNodes()
        assertTrue("the heading should be the period, not the screen name", stale.isEmpty())
    }

    @Test
    fun aPastMonthSaysCompleteRatherThanZeroDaysLeft() {
        compose.setContent(content(header = LedgerHeaderState(monthLabel = "July 2026", daysLeft = null)))

        compose.onNodeWithText("Complete month").assertIsDisplayed()
    }

    @Test
    fun theMonthArrowsAreReachable() {
        var back = 0
        compose.setContent(content(canGoForward = true, onPreviousMonth = { back++ }))

        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithContentDescription("Next month").assertIsDisplayed()

        assertEquals(1, back)
    }

    @Test
    fun anEmptyMonthNamesTheMonthRatherThanClaimingTheAppIsEmpty() {
        compose.setContent(content(header = LedgerHeaderState(monthLabel = "July 2026")))

        compose.onNodeWithText("Nothing in July 2026").assertIsDisplayed()
    }

    @Test
    fun aLowConfidenceRowSaysSoInWords() {
        val low = Transaction(
            id = 1,
            uuid = "t1",
            accountId = 1,
            amount = Money(165_00),
            direction = TransactionDirection.DEBIT,
            occurredAt = Instant.parse("2026-08-28T06:00:00Z").toEpochMilli(),
            merchantRaw = "Pathao",
            merchantId = null,
            categoryId = 11,
            note = null,
            source = TransactionSource.SMS,
            confidence = Confidence.LOW,
            transferGroupId = null,
            updatedAt = 0,
        )

        compose.setContent(content(items = listOf(LedgerItem.Row(low))))

        compose.onNodeWithText("Pathao").assertIsDisplayed()
        compose.onNodeWithText("low confidence").assertIsDisplayed()
        compose.onNodeWithContentDescription("Low confidence").assertIsDisplayed()
    }
}
