package com.wasif.khata.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theHomeViewControlOffersBothRoots() {
        // Without this control, a module that becomes the app's root has no
        // way to expose the preference that put it there -- Plan B built and
        // tested the preference itself but withheld the control for exactly
        // this reason.
        compose.setContent {
            KhataTheme {
                SettingsContent(
                    prefs = KhataPreferences.Default,
                    onBack = {},
                    onHomeViewSelected = {},
                    onMonthlyBudgetChanged = {},
                    onFieldSelected = {},
                    onGroundSelected = {},
                    onAccentSelected = {},
                    onIntensitySelected = {},
                    onResetTheme = {},
                )
            }
        }

        compose.onNodeWithText("HOME VIEW").assertIsDisplayed()
        compose.onNodeWithText("Modules").assertIsDisplayed()
        compose.onNodeWithText("Wallet").assertIsDisplayed()
    }
}
