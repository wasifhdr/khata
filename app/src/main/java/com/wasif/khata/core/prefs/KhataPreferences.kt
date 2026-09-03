package com.wasif.khata.core.prefs

import com.wasif.khata.core.ui.theme.ThemeSpec

/** Which screen the app opens onto. The start destination is the back-stack root. */
enum class HomeView { Modules, Wallet }

data class KhataPreferences(
    val themeSpec: ThemeSpec,
    val homeView: HomeView,
    /**
     * Retired as a user-facing setting; budgets are per category now. It survives only
     * so BudgetCarryOver can find a value set before the change and move it into a
     * row. Nothing else reads it, and nothing writes it but that carry-over clearing
     * itself.
     */
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
    /**
     * Null means the AI fallback is off. There is deliberately no separate toggle: a
     * switch that could disagree with whether a key exists is a switch that will.
     */
    val geminiKey: String? = null,
) {
    companion object {
        val Default = KhataPreferences(
            themeSpec = ThemeSpec.Default,
            homeView = HomeView.Modules,
            monthlyBudgetMinor = null,
            hasRequestedSmsPermission = false,
            hasBackfilled = false,
            geminiKey = null,
        )
    }
}
