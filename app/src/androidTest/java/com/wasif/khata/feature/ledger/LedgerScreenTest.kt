package com.wasif.khata.feature.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LedgerScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val sample = Transaction(
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
        updatedAt = Instant.parse("2026-08-26T04:00:00Z").toEpochMilli(),
    )

    @Test
    fun `renders a day header and a transaction row`() {
        val data = flowOf(
            PagingData.from(
                listOf(
                    LedgerItem.DayHeader(LocalDate.of(2026, 8, 26)),
                    LedgerItem.Row(sample),
                )
            )
        )

        composeRule.setContent {
            KhataTheme {
                LedgerContent(
                    items = data.collectAsLazyPagingItems(),
                    onAddTransaction = {},
                    onOpenTransaction = {},
                )
            }
        }

        composeRule.onNodeWithText("SHWAPNO").assertIsDisplayed()
        composeRule.onNodeWithText("−৳1,234.56").assertIsDisplayed()
        composeRule.onNodeWithText("Wednesday, 26 August").assertIsDisplayed()
    }

    @Test
    fun `tapping a row reports the transaction id`() {
        var opened: Long? = null
        val data = flowOf(PagingData.from(listOf<LedgerItem>(LedgerItem.Row(sample))))

        composeRule.setContent {
            KhataTheme {
                LedgerContent(
                    items = data.collectAsLazyPagingItems(),
                    onAddTransaction = {},
                    onOpenTransaction = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("SHWAPNO").performClick()

        assertEquals(7L, opened)
    }

    @Test
    fun `the add button invokes its callback`() {
        var clicked = false
        val data = flowOf(PagingData.from(emptyList<LedgerItem>()))

        composeRule.setContent {
            KhataTheme {
                LedgerContent(
                    items = data.collectAsLazyPagingItems(),
                    onAddTransaction = { clicked = true },
                    onOpenTransaction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add transaction").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `an empty ledger shows guidance rather than a blank screen`() {
        val data = flowOf(PagingData.from(emptyList<LedgerItem>()))

        composeRule.setContent {
            KhataTheme {
                LedgerContent(
                    items = data.collectAsLazyPagingItems(),
                    onAddTransaction = {},
                    onOpenTransaction = {},
                )
            }
        }

        composeRule.onNodeWithText("No transactions yet").assertIsDisplayed()
    }
}
