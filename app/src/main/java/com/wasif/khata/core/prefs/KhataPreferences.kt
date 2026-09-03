package com.wasif.khata.core.prefs

import com.wasif.khata.core.ui.theme.ThemeSpec

/** Which screen the app opens onto. The start destination is the back-stack root. */
enum class HomeView { Modules, Wallet }

data class KhataPreferences(
    val themeSpec: ThemeSpec,
    val homeView: HomeView,
    /** Null means no budget set, which is a different answer from a budget of zero. */
    val monthlyBudgetMinor: Long?,
    /**
     * Separates "never asked" from "asked and refused". Android's
     * shouldShowRequestPermissionRationale is false in both cases, so it cannot tell
     * them apart on its own.
     */
    val hasRequestedSmsPermission: Boolean = false,
    /**
     * Guards the one automatic backfill. Not "is the ledger empty" -- that is also
     * the honest state of a user whose inbox holds no bank messages, and testing for
     * it would rescan the whole inbox on every launch, forever.
     */
    val hasBackfilled: Boolean = false,
) {
    companion object {
        val Default = KhataPreferences(
            themeSpec = ThemeSpec.Default,
            homeView = HomeView.Modules,
            monthlyBudgetMinor = null,
            hasRequestedSmsPermission = false,
            hasBackfilled = false,
        )
    }
}
