package com.wasif.khata.core.prefs

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wasif.khata.core.backup.BackupFile
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : PreferencesRepository {

    private object Keys {
        val Field = intPreferencesKey("theme_field")
        val Ground = intPreferencesKey("theme_ground")
        val Accent = intPreferencesKey("theme_accent")
        val Intensity = stringPreferencesKey("theme_intensity")
        val Home = stringPreferencesKey("home_view")
        val Budget = longPreferencesKey("monthly_budget_minor")
        val BudgetSet = intPreferencesKey("monthly_budget_set")
        val SmsRequested = intPreferencesKey("sms_permission_requested")
        val Backfilled = intPreferencesKey("has_backfilled")
        val GeminiKey = stringPreferencesKey("gemini_key")
        val BackupKey = stringPreferencesKey("backup_key")
        val BackupSalt = stringPreferencesKey("backup_salt")
        val DriveConnected = intPreferencesKey("drive_connected")
        val DriveFolderId = stringPreferencesKey("drive_folder_id")
        val DriveLastUploadAt = longPreferencesKey("drive_last_upload_at")
        val DriveNeedsReconnect = intPreferencesKey("drive_needs_reconnect")
        val SearchIndexVersion = intPreferencesKey("search_index_version")
    }

    override val preferences: Flow<KhataPreferences> = store.data
        // A corrupt or unreadable store must not take the app down before any UI
        // exists to report it. Defaults are always a valid answer here.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            KhataPreferences(
                themeSpec = ThemeSpec(
                    // getOrElse rather than get: a stored index can outlive the
                    // list it pointed into, and on first launch that would throw
                    // before any UI exists to report it.
                    field = KhataPalette.fields.getOrElse(p[Keys.Field] ?: -1) { ThemeSpec.Default.field },
                    ground = KhataPalette.grounds.getOrNull(p[Keys.Ground] ?: -1)?.color
                        ?: ThemeSpec.Default.ground,
                    accent = KhataPalette.accents.getOrNull(p[Keys.Accent] ?: -1)?.color
                        ?: ThemeSpec.Default.accent,
                    intensity = p[Keys.Intensity]
                        ?.let { name -> FieldIntensity.entries.firstOrNull { it.name == name } }
                        ?: ThemeSpec.Default.intensity,
                ),
                homeView = p[Keys.Home]
                    ?.let { name -> HomeView.entries.firstOrNull { it.name == name } }
                    ?: HomeView.Modules,
                // Two keys, because null and zero are different answers and one
                // Long cannot carry both.
                monthlyBudgetMinor = if (p[Keys.BudgetSet] == 1) p[Keys.Budget] ?: 0L else null,
                hasRequestedSmsPermission = p[Keys.SmsRequested] == 1,
                hasBackfilled = p[Keys.Backfilled] == 1,
                geminiKey = p[Keys.GeminiKey],
                backupKey = p[Keys.BackupKey],
                backupSalt = p[Keys.BackupSalt],
                driveConnected = p[Keys.DriveConnected] == 1,
                driveFolderId = p[Keys.DriveFolderId],
                driveLastUploadAt = p[Keys.DriveLastUploadAt],
                driveNeedsReconnect = p[Keys.DriveNeedsReconnect] == 1,
            )
        }

    override suspend fun setTheme(spec: ThemeSpec) {
        store.edit { p ->
            p[Keys.Field] = KhataPalette.fields.indexOf(spec.field)
            p[Keys.Ground] = KhataPalette.grounds.indexOfFirst { it.color == spec.ground }
            p[Keys.Accent] = KhataPalette.accents.indexOfFirst { it.color == spec.accent }
            p[Keys.Intensity] = spec.intensity.name
        }
    }

    override suspend fun resetTheme() {
        // Clears the keys rather than writing today's default as an explicit
        // value, so a later change of default still reaches anyone who has
        // pressed reset.
        store.edit { p ->
            p.clear(Keys.Field)
            p.clear(Keys.Ground)
            p.clear(Keys.Accent)
            p.clear(Keys.Intensity)
        }
    }

    override suspend fun setHomeView(view: HomeView) {
        store.edit { it[Keys.Home] = view.name }
    }

    override suspend fun setSmsPermissionRequested() {
        store.edit { it[Keys.SmsRequested] = 1 }
    }

    override suspend fun setBackfilled() {
        store.edit { it[Keys.Backfilled] = 1 }
    }

    override suspend fun setSearchIndexVersion(version: Int) {
        store.edit { it[Keys.SearchIndexVersion] = version }
    }

    /**
     * Derives and stores the key, never the passphrase.
     *
     * The salt is kept so the same passphrase derives the same key every night. A
     * restore on another phone does not use it -- it derives from the salt in the
     * backup file's own header, which is why the file travels.
     */
    override suspend fun setBackupPassphrase(passphrase: String?) {
        store.edit { p ->
            if (passphrase.isNullOrBlank()) {
                p.clear(Keys.BackupKey)
                p.clear(Keys.BackupSalt)
            } else {
                val salt = p[Keys.BackupSalt]?.let { Base64.decode(it, Base64.NO_WRAP) }
                    ?: BackupFile.newSalt().also {
                        p[Keys.BackupSalt] = Base64.encodeToString(it, Base64.NO_WRAP)
                    }
                p[Keys.BackupKey] = Base64.encodeToString(
                    BackupFile.deriveKey(passphrase, salt),
                    Base64.NO_WRAP,
                )
            }
        }
    }

    /**
     * Disconnecting clears everything that depended on the connection. A stale folder
     * id would have the next connection upload into a folder a different account
     * cannot see, and a stale timestamp would claim an offsite copy that is gone.
     */
    override suspend fun setDriveConnected(connected: Boolean) {
        store.edit { p ->
            if (connected) {
                p.clear(Keys.DriveNeedsReconnect)
                p[Keys.DriveConnected] = 1
            } else {
                p.clear(Keys.DriveConnected)
                p.clear(Keys.DriveFolderId)
                p.clear(Keys.DriveLastUploadAt)
                p.clear(Keys.DriveNeedsReconnect)
            }
        }
    }

    override suspend fun setDriveFolderId(id: String?) {
        store.edit { p -> if (id == null) p.clear(Keys.DriveFolderId) else p[Keys.DriveFolderId] = id }
    }

    /** An upload that worked is proof the grant is fine, so the warning comes down. */
    override suspend fun setDriveUploaded(at: Long) {
        store.edit { p ->
            p.clear(Keys.DriveNeedsReconnect)
            p[Keys.DriveLastUploadAt] = at
        }
    }

    override suspend fun setDriveNeedsReconnect() {
        store.edit { it[Keys.DriveNeedsReconnect] = 1 }
    }

    override suspend fun setGeminiKey(key: String?) {
        store.edit { p ->
            if (key.isNullOrBlank()) p.clear(Keys.GeminiKey) else p[Keys.GeminiKey] = key
        }
    }

    override suspend fun setMonthlyBudget(minor: Long?) {
        store.edit { p ->
            if (minor == null) {
                p.clear(Keys.Budget)
                p[Keys.BudgetSet] = 0
            } else {
                p[Keys.Budget] = minor
                p[Keys.BudgetSet] = 1
            }
        }
    }

    /**
     * remove() is declared to return T rather than T?, so where Kotlin treats the
     * call as an expression -- the last line of an if/else branch, say -- it unboxes
     * the result, and on an Int or Long key that was never set it unboxes null and
     * throws. A standalone statement is safe; the same call one line lower is not.
     *
     * That is too sharp an edge to leave lying around, so nothing in this file calls
     * remove() directly. Clearing a key that is not there is a no-op wherever it sits.
     */
    private fun <T : Any> MutablePreferences.clear(key: Preferences.Key<T>) {
        if (contains(key)) remove(key)
    }
}
