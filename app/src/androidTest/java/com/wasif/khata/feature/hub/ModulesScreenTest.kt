package com.wasif.khata.feature.hub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModulesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theWordmarkIsBengaliAndNeverTransliterated() {
        compose.setContent {
            KhataTheme {
                ModulesContent(ModulesUiState(monthSpend = Money(47_382_50)), {}, {}, {}, {}, {}, {}, {})
            }
        }

        compose.onNodeWithText("খাতা").assertIsDisplayed()
        compose.onNodeWithText("সব হিসাব, এক খাতায়").assertIsDisplayed()

        // "Khata" in Latin must appear nowhere in the interface.
        val latin = compose.onAllNodesWithText("Khata").fetchSemanticsNodes()
        assertTrue("Latin 'Khata' is on screen", latin.isEmpty())
    }

    @Test
    fun everyTileNamesWhatItHoldsRatherThanStandingEmpty() {
        compose.setContent {
            KhataTheme { ModulesContent(ModulesUiState(), {}, {}, {}, {}, {}, {}, {}) }
        }

        // This test used to assert the opposite: that dormant tiles said "Not built". Notes
        // was the last unbuilt module, so there is nothing left to label -- and a tile still
        // saying it would now be a lie rather than an honest placeholder.
        listOf("RESTAURANTS", "WATCHLIST", "CAR SERVICE", "NOTES").forEach { name ->
            compose.onNodeWithText(name).assertIsDisplayed()
        }
        val dormant = compose.onAllNodesWithText("Not built").fetchSemanticsNodes()
        assertTrue("every module is built, so nothing may claim otherwise", dormant.isEmpty())
    }

    @Test
    fun theBudgetRingIsAbsentUntilABudgetIsSet() {
        compose.setContent {
            KhataTheme {
                ModulesContent(ModulesUiState(monthSpend = Money(47_382_50)), {}, {}, {}, {}, {}, {}, {})
            }
        }

        // The ring renders its percentage as text, so its absence is checkable.
        val ring = compose.onAllNodesWithText("63").fetchSemanticsNodes()
        assertTrue("no ring should be drawn without a budget", ring.isEmpty())
    }

    @Test
    fun theSettingsGearIsReachable() {
        var opened = false
        compose.setContent {
            KhataTheme { ModulesContent(ModulesUiState(), {}, { opened = true }, {}, {}, {}, {}, {}) }
        }

        compose.onNodeWithContentDescription("Settings").performClick()

        assertTrue(opened)
    }
}
