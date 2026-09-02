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
    fun theMonthFiguresKeepTheirLabelsAndAmountsOnGlass() {
        // The month figures moved from a flat surfaceContainer Column to
        // KhataGlass. The paint is not assertable here, and asserting that a
        // KhataGlass was called would only test the code against itself --
        // losing the content while rewrapping is the real risk, so that is
        // what this pins.
        compose.setContent {
            KhataTheme {
                WalletContent(
                    WalletUiState(
                        monthSpend = Money(2_400_00),
                        monthReceived = Money(50_000_00),
                    ),
                    {},
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithText("SPENT").assertIsDisplayed()
        compose.onNodeWithText("RECEIVED").assertIsDisplayed()
        compose.onNodeWithText("৳2,400.00").assertIsDisplayed()
        compose.onNodeWithText("৳50,000.00").assertIsDisplayed()
    }

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
                    {},
                )
            }
        }

        compose.onNodeWithText("Wallet").assertIsDisplayed()
        // M12: "1 need checking" was ungrammatical -- the drifting count is 1
        // here, so this is the singular form the fix owes.
        compose.onNodeWithText("2 accounts · 1 needs checking").assertIsDisplayed()
    }

    @Test
    fun theSublineSingularisesBothCountsRatherThanAlwaysReadingAsPlural() {
        // M12: "${size} accounts" didn't singularise ("1 accounts"), and
        // neither did the drifting count ("1 need checking"). A one-account
        // wallet with its one account drifting exercises both singulars at
        // once.
        compose.setContent {
            KhataTheme {
                WalletContent(
                    WalletUiState(accounts = listOf(account("EBL", 2_98_606_00, 2_98_846_00))),
                    {},
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithText("1 account · 1 needs checking").assertIsDisplayed()
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
                    {},
                )
            }
        }

        compose.onNodeWithText("Balance disagrees · check").assertIsDisplayed()
    }

    @Test
    fun theAppNameNeverAppearsOnAModulePage() {
        compose.setContent {
            KhataTheme { WalletContent(WalletUiState(), {}, {}, {}, {}) }
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
                WalletContent(WalletUiState(), null, {}, {}, {})
            }
        }

        compose.onNodeWithContentDescription("All modules").assertIsDisplayed()
        val back = compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes()
        assertTrue("a root screen must not show a back arrow", back.isEmpty())
    }

    @Test
    fun asAPushedScreenItOffersBackRatherThanTheHub() {
        compose.setContent {
            KhataTheme {
                // non-null onBack, null onOpenHub means this screen was pushed onto
                // something, mirroring the isRoot=false branch in KhataNavHost.
                WalletContent(WalletUiState(), {}, null, {}, {})
            }
        }

        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        val hub = compose.onAllNodesWithContentDescription("All modules").fetchSemanticsNodes()
        assertTrue("a pushed screen must not show the hub glyph", hub.isEmpty())
    }
}
