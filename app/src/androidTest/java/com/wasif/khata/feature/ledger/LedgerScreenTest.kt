package com.wasif.khata.feature.ledger

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
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

    private val settled = LoadStates(
        refresh = LoadState.NotLoading(endOfPaginationReached = true),
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true),
    )

    private fun content(
        items: List<LedgerItem> = emptyList(),
        header: LedgerHeaderState = LedgerHeaderState(monthLabel = "August 2026", daysLeft = 3),
        canGoForward: Boolean = false,
        query: String = "",
        onPreviousMonth: () -> Unit = {},
        onNextMonth: () -> Unit = {},
        onAddTransaction: () -> Unit = {},
        onOpenTransaction: (Long) -> Unit = {},
    ): @Composable () -> Unit = {
        KhataTheme {
            LedgerContent(
                // Explicit end-of-pagination states, because PagingData.from()
                // otherwise leaves refresh as Loading forever. The screen only
                // shows its empty state once refresh has settled -- claiming
                // "nothing here" while still loading would be a false statement
                // -- so an unsettled PagingData would never render it.
                items = flowOf(PagingData.from(items, sourceLoadStates = settled))
                    .collectAsLazyPagingItems(),
                header = header,
                categoryTokens = mapOf(11L to CategoryChip(name = "Groceries", colorToken = "category_green")),
                canGoForward = canGoForward,
                query = query,
                onQueryChange = {},
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onBack = {},
                onAddTransaction = onAddTransaction,
                onOpenTransaction = onOpenTransaction,
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
    fun theLastDayOfTheMonthSaysLastDayRatherThanZeroDaysLeft() {
        compose.setContent(content(header = LedgerHeaderState(monthLabel = "August 2026", daysLeft = 0)))

        compose.onNodeWithText("Last day").assertIsDisplayed()
    }

    @Test
    fun oneDayLeftIsSingularRatherThanOneDaysLeft() {
        compose.setContent(content(header = LedgerHeaderState(monthLabel = "August 2026", daysLeft = 1)))

        compose.onNodeWithText("1 day left").assertIsDisplayed()
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
    fun aSearchWithNoMatchesDoesNotClaimTheMonthIsEmpty() {
        // Without this branch the screen would say "Nothing in August 2026",
        // which is a claim about the month rather than about the search.
        compose.setContent(content(query = "zzzz"))

        compose.onNodeWithText("Nothing matches that").assertIsDisplayed()
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
        compose.onNodeWithText("low confidence", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Low confidence").assertIsDisplayed()
    }

    @Test
    fun tappingARowInvokesOnOpenTransactionWithTheId() {
        val sample = Transaction(
            id = 7,
            uuid = "t-7",
            accountId = 1,
            amount = Money(123_456),
            direction = TransactionDirection.DEBIT,
            occurredAt = Instant.parse("2026-08-26T04:00:00Z").toEpochMilli(),
            merchantRaw = "SHWAPNO",
            merchantId = null,
            categoryId = null,
            note = null,
            source = TransactionSource.MANUAL,
            confidence = Confidence.HIGH,
            transferGroupId = null,
            updatedAt = 0,
        )
        var opened: Long? = null

        compose.setContent(
            content(items = listOf(LedgerItem.Row(sample)), onOpenTransaction = { opened = it }),
        )

        compose.onNodeWithText("SHWAPNO").performClick()

        assertEquals(7L, opened)
    }

    @Test
    fun theAddButtonInvokesOnAddTransaction() {
        var clicked = false

        compose.setContent(content(onAddTransaction = { clicked = true }))

        compose.onNodeWithContentDescription("Add transaction").performClick()

        assertTrue(clicked)
    }
}
