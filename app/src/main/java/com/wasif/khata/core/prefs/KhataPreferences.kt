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
    /**
     * The PBKDF2 output, Base64 -- not the passphrase. A nightly backup needs a key
     * without prompting, and storing the phrase would hand a compromised device the
     * thing that unlocks every backup sitting elsewhere.
     *
     * Null means no backups are taken. There is no separate toggle.
     */
    val backupKey: String? = null,
    /** The salt [backupKey] was derived from; it goes into every backup's header. */
    val backupSalt: String? = null,
    /**
     * Whether backups are uploaded to Drive. A flag, not an account name: the
     * authorize path never exposes the address, and it turned out not to be needed.
     * Play Services holds the grant and remembers which account gave it, so the app
     * stores no identifier and a stolen phone yields no lasting Drive access.
     */
    val driveConnected: Boolean = false,
    /** The Khata folder in Drive. Recreated if it 404s, so a stale id is not fatal. */
    val driveFolderId: String? = null,
    /** When the last upload succeeded. Null with an account set means none yet. */
    val driveLastUploadAt: Long? = null,
    /**
     * Set when a background run found the grant gone. A worker cannot show consent,
     * so it records this and Settings offers the reconnection, where an Activity
     * exists to run it.
     */
    val driveNeedsReconnect: Boolean = false,
) {
    companion object {
        val Default = KhataPreferences(
            themeSpec = ThemeSpec.Default,
            homeView = HomeView.Modules,
            monthlyBudgetMinor = null,
            hasRequestedSmsPermission = false,
            hasBackfilled = false,
            geminiKey = null,
            backupKey = null,
            backupSalt = null,
            driveConnected = false,
            driveFolderId = null,
            driveLastUploadAt = null,
            driveNeedsReconnect = false,
        )
    }
}
