package com.wasif.khata.feature.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.ui.theme.KhataTheme
import com.wasif.khata.domain.model.Account
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun account(name: String, balance: Long, reported: Long?) = Account(
        id = name.hashCode().toLong(),
        uuid = name,
        name = name,
        type = AccountType.MFS,
        currentBalance = Money(balance),
        reportedBalance = reported?.let { Money(it) },
        includeInNetWorth = true,
    )

    @Test
    fun theSublineSaysWhetherAnythingNeedsChecking() {
        compose.setContent {
            KhataTheme {
                WalletContent(
                    WalletUiState(
                        accounts = listOf(
                            account("bKash", 8_214_30, 8_214_30),
                            account("EBL", 2_98_606_00, 2_98_846_00),
                        ),
                    ),
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithText("Wallet").assertIsDisplayed()
        compose.onNodeWithText("2 accounts · 1 need checking").assertIsDisplayed()
    }

    @Test
    fun aDriftingAccountSaysSoInWordsNotOnlyColour() {
        compose.setContent {
            KhataTheme {
                WalletContent(
                    WalletUiState(accounts = listOf(account("EBL", 2_98_606_00, 2_98_846_00))),
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithText("Balance disagrees · check").assertIsDisplayed()
    }

    @Test
    fun theAppNameNeverAppearsOnAModulePage() {
        compose.setContent {
            KhataTheme { WalletContent(WalletUiState(), {}, {}, {}) }
        }

        val wordmark = compose.onAllNodesWithText("খাতা").fetchSemanticsNodes()
        assertTrue("the wordmark belongs to the hub alone", wordmark.isEmpty())
    }

    @Test
    fun asTheRootItOffersTheHubRatherThanBack() {
        compose.setContent {
            KhataTheme {
                // onBack null means this screen is the root. Without a hub glyph
                // here the user cannot reach Settings again, because the gear
                // lives only on the hub.
                WalletContent(WalletUiState(), null, {}, {})
            }
        }

        compose.onNodeWithContentDescription("All modules").assertIsDisplayed()
        val back = compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes()
        assertTrue("a root screen must not show a back arrow", back.isEmpty())
    }
}
