package com.wasif.khata.feature.ledger

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import java.time.LocalDate
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
        onQueryChange: (String) -> Unit = {},
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
                onQueryChange = onQueryChange,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onBack = {},
                onAddTransaction = onAddTransaction,
                onOpenTransaction = onOpenTransaction,
            )
        }
    }

    @Test
    fun theSearchFieldStillAcceptsInputOnGlass() {
        // The search field moved inside a KhataGlass wrapper. Wrapping a text
        // field in a Box that clips and draws is where focus and hit-testing get
        // lost, so this pins the interaction rather than the paint.
        var typed = ""
        compose.setContent(content(onQueryChange = { typed = it }))

        compose.onNodeWithText("Search merchants and notes").performTextInput("shwapno")

        assertEquals("shwapno", typed)
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
    fun searchingReplacesTheMonthHeadingRatherThanAssertingAMonthTheResultsIgnore() {
        // Search spans all time, so a heading naming the viewed month would be
        // a false claim about results that have nothing to do with that month.
        compose.setContent(content(query = "coffee"))

        compose.onNodeWithText("Search").assertIsDisplayed()
        compose.onNodeWithText("All months").assertIsDisplayed()
        val monthLabel = compose.onAllNodesWithText("August 2026").fetchSemanticsNodes()
        assertTrue("the month label should not appear while searching", monthLabel.isEmpty())
    }

    @Test
    fun searchingHidesTheMonthStrip() {
        // A month-spend total beside all-time search results would name a
        // figure that has nothing to do with what is on screen.
        compose.setContent(content(query = "coffee"))

        val spent = compose.onAllNodesWithText("SPENT").fetchSemanticsNodes()
        assertTrue("the month strip should not appear while searching", spent.isEmpty())
    }

    @Test
    fun aBlankQueryShowsTheMonthLabelAndStripAsBefore() {
        compose.setContent(content(query = ""))

        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithText("SPENT").assertIsDisplayed()
    }

    @Test
    fun theMonthArrowsAreDisabledWhileSearching() {
        compose.setContent(content(query = "coffee", canGoForward = true))

        compose.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
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
        compose.onNodeWithText("needs checking", substring = true).assertIsDisplayed()
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

    @Test
    fun aDayHeaderLabelsItsFigureRatherThanShowingAnUnlabelledTotal() {
        // I3: observeDayTotals() is DEBIT-only by design, so an unlabelled
        // figure would read as the day's activity when it is really just spend
        // -- a day of pure income would show ৳0.00 beside a credit row. The
        // header itself is a stand-in: what matters is that the total on screen
        // always carries the SPENT label, never bare.
        val header = LedgerItem.DayHeader(date = LocalDate.of(2026, 8, 3), total = Money(50_000_00))
        compose.setContent(content(items = listOf(header)))

        compose.onNodeWithText("Monday, 3 August").assertIsDisplayed()
        compose.onNodeWithText("৳50,000.00").assertIsDisplayed()
        // Two SPENT labels are expected here: the month strip's and this day
        // header's. Both must be present -- if either goes missing, one of the
        // two figures on screen would be unlabelled again.
        val spentLabels = compose.onAllNodesWithText("SPENT").fetchSemanticsNodes()
        assertEquals(2, spentLabels.size)
    }

    @Test
    fun aDayHeaderDropsItsFigureWhileSearchingRatherThanShowingAnAllTimeTotal() {
        // I4: observeDayTotals() stays an unfiltered, all-time aggregate while
        // search narrows the rows on screen to whatever matches the query, so
        // the day header's total can no longer agree with the rows beneath it.
        // The date is still true under search; the figure is not, so only the
        // figure disappears.
        val header = LedgerItem.DayHeader(date = LocalDate.of(2026, 8, 3), total = Money(50_000_00))
        compose.setContent(content(items = listOf(header), query = "shwapno"))

        compose.onNodeWithText("Monday, 3 August").assertIsDisplayed()
        val total = compose.onAllNodesWithText("৳50,000.00").fetchSemanticsNodes()
        assertTrue("the day-header total should not appear while searching", total.isEmpty())
        val spentLabels = compose.onAllNodesWithText("SPENT").fetchSemanticsNodes()
        assertTrue("no SPENT label should appear while searching", spentLabels.isEmpty())
    }

}
