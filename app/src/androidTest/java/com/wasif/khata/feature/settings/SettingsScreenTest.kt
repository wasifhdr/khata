package com.wasif.khata.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wasif.khata.core.permission.SmsPermissionState
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.sms.IngestProgress
import com.wasif.khata.core.sms.IngestSummary
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Assert.assertTrue
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
                    ingestion = IngestionState(),
                    onBack = {},
                    onPermissionRequested = {},
                    onBackfill = {},
                    onResetCash = {},
                    onReparse = {},
                    onOpenUnmatched = {},
                    onOpenReconcile = {},
                    onHomeViewSelected = {},
                    onOpenCategories = {},
                    onGeminiKeyChanged = {},
                    backupSection = {},
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

    @Test
    fun theHomeViewControlSaysItTakesEffectNextLaunch() {
        // I6: KhataNavHost freezes its start destination for the process
        // lifetime, so selecting a new home view here cannot move the user
        // there mid-interaction the way it used to. Freezing without saying so
        // would make the control look unresponsive; this is the other half of
        // that fix -- the part reachable without Hilt navigation test infra.
        compose.setContent {
            KhataTheme {
                SettingsContent(
                    prefs = KhataPreferences.Default,
                    ingestion = IngestionState(),
                    onBack = {},
                    onPermissionRequested = {},
                    onBackfill = {},
                    onResetCash = {},
                    onReparse = {},
                    onOpenUnmatched = {},
                    onOpenReconcile = {},
                    onHomeViewSelected = {},
                    onOpenCategories = {},
                    onGeminiKeyChanged = {},
                    backupSection = {},
                    onFieldSelected = {},
                    onGroundSelected = {},
                    onAccentSelected = {},
                    onIntensitySelected = {},
                    onResetTheme = {},
                )
            }
        }

        compose.onNodeWithText("Takes effect the next time you open Khata").assertIsDisplayed()
    }

    // --- The MESSAGES section ---------------------------------------------

    private fun messages(
        state: IngestionState,
        onBackfill: () -> Unit = {},
        onOpenUnmatched: () -> Unit = {},
    ): @Composable () -> Unit = {
        KhataTheme {
            SettingsContent(
                prefs = KhataPreferences.Default,
                ingestion = state,
                onBack = {},
                onPermissionRequested = {},
                onBackfill = onBackfill,
                onResetCash = {},
                onReparse = {},
                onOpenUnmatched = onOpenUnmatched,
                onOpenReconcile = {},
                onHomeViewSelected = {},
                onOpenCategories = {},
                onGeminiKeyChanged = {},
                backupSection = {},
                onFieldSelected = {},
                onGroundSelected = {},
                onAccentSelected = {},
                onIntensitySelected = {},
                onResetTheme = {},
            )
        }
    }

    @Test
    fun permissionIsDescribedByWhatItDoesNotByItsEnumName() {
        compose.setContent(messages(IngestionState(permission = SmsPermissionState.NOT_REQUESTED)))

        compose.onNodeWithText("Read bKash and EBL messages").assertIsDisplayed()
        compose.onNodeWithText("NOT_REQUESTED").assertDoesNotExist()
    }

    @Test
    fun refusingSmsIsPresentedAsAWorkingChoice() {
        compose.setContent(messages(IngestionState(permission = SmsPermissionState.NOT_REQUESTED)))

        // Nothing about permission may gate entry -- the copy has to say so.
        compose.onNodeWithText("Khata works without this", substring = true).assertIsDisplayed()
    }

    @Test
    fun aPermanentDenialSendsYouToAppSettingsRatherThanPromisingAnotherPrompt() {
        compose.setContent(messages(IngestionState(permission = SmsPermissionState.PERMANENTLY_DENIED)))

        // Android stops showing the dialog after two refusals, so "tap to ask
        // again" would be a promise the system will not keep.
        compose.onNodeWithText("app settings", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tap to ask again").assertDoesNotExist()
    }

    @Test
    fun backfillCannotBeStartedWithoutPermission() {
        compose.setContent(messages(IngestionState(permission = SmsPermissionState.DENIED)))

        compose.onNodeWithText("Read my message history").assertIsNotEnabled()
    }

    @Test
    fun backfillStartsWhenPermissionIsGranted() {
        var started = false
        compose.setContent(
            messages(IngestionState(permission = SmsPermissionState.GRANTED), onBackfill = { started = true }),
        )

        compose.onNodeWithText("Read my message history").performClick()

        assertTrue(started)
    }

    @Test
    fun progressIsReportedInWordsNotOnlyAsABar() {
        compose.setContent(
            messages(
                IngestionState(
                    permission = SmsPermissionState.GRANTED,
                    backfill = IngestProgress(processed = 40, total = 900, summary = IngestSummary()),
                ),
            ),
        )

        compose.onNodeWithText("40 of 900 messages").assertIsDisplayed()
    }

    @Test
    fun aRunInFlightCannotBeStartedAgain() {
        compose.setContent(
            messages(
                IngestionState(
                    permission = SmsPermissionState.GRANTED,
                    backfill = IngestProgress(processed = 40, total = 900, summary = IngestSummary()),
                ),
            ),
        )

        compose.onNodeWithText("Read my message history").assertIsNotEnabled()
        compose.onNodeWithText("Re-read with the current rules").assertIsNotEnabled()
    }

    @Test
    fun theUnmatchedRowSaysHowManyAreWaitingBeforeYouOpenIt() {
        compose.setContent(messages(IngestionState(unmatchedCount = 7)))

        compose.onNodeWithText("7 waiting", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingUnmatchedNavigates() {
        var opened = false
        compose.setContent(messages(IngestionState(unmatchedCount = 7), onOpenUnmatched = { opened = true }))

        compose.onNodeWithText("Messages Khata could not read").performClick()

        assertTrue(opened)
    }
}
