package com.wasif.khata.core.prefs

import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<KhataPreferences>
    suspend fun setTheme(spec: ThemeSpec)
    suspend fun resetTheme()
    suspend fun setHomeView(view: HomeView)
    suspend fun setMonthlyBudget(minor: Long?)
    suspend fun setSmsPermissionRequested()
    suspend fun setBackfilled()
    suspend fun setGeminiKey(key: String?)
    suspend fun setBackupPassphrase(passphrase: String?)
    suspend fun setDriveAccount(email: String?)
    suspend fun setDriveFolderId(id: String?)
    suspend fun setDriveUploaded(at: Long)
    suspend fun setDriveNeedsReconnect()
}
