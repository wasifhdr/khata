package com.wasif.khata.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.model.RawMessageStatus
import com.wasif.khata.core.permission.SmsPermissionRepository
import com.wasif.khata.core.permission.SmsPermissionState
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import android.util.Base64
import com.wasif.khata.core.backup.BackupFile
import com.wasif.khata.core.backup.BackupRepository
import com.wasif.khata.core.backup.BackupResult
import com.wasif.khata.core.drive.DriveAuth
import com.wasif.khata.core.drive.DriveBackups
import com.wasif.khata.core.drive.DriveFile
import com.wasif.khata.core.drive.DriveUploader
import com.wasif.khata.core.drive.UploadOutcome
import com.wasif.khata.core.prefs.PreferencesRepository
import java.io.File
import com.wasif.khata.core.sms.IngestProgress
import com.wasif.khata.core.sms.IngestSummary
import com.wasif.khata.core.sms.IngestionScheduler
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.FieldPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the MESSAGES section needs, in one value. Grouped so adding a row
 * there does not add a parameter to [SettingsContent] each time.
 */
data class IngestionState(
    val permission: SmsPermissionState = SmsPermissionState.NOT_REQUESTED,
    val unmatchedCount: Int = 0,
    val backfill: IngestProgress? = null,
    val lastRun: String? = null,
    val cashBalance: Money = Money.ZERO,
) {
    val isWorking: Boolean get() = backfill != null && !backfill.isComplete
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PreferencesRepository,
    private val smsPermission: SmsPermissionRepository,
    private val scheduler: IngestionScheduler,
    private val backups: BackupRepository,
    rawMessageDao: RawMessageDao,
    private val accountDao: AccountDao,
    private val transactions: com.wasif.khata.domain.repository.TransactionRepository,
    private val clock: com.wasif.khata.core.time.KhataClock,
    private val driveAuth: DriveAuth,
    private val driveBackups: DriveBackups,
    private val driveUploader: DriveUploader,
) : ViewModel() {

    /** Only what a pass cannot say for itself, like the cash reset. */
    private val _lastAction = MutableStateFlow<String?>(null)

    val ingestion: StateFlow<IngestionState> = combine(
        smsPermission.observe(),
        // Drives the row's own count, so it says how much is waiting before you open it.
        rawMessageDao.observeByStatus(RawMessageStatus.UNMATCHED).map { it.size },
        scheduler.observePass(),
        _lastAction,
        accountDao.observeAll().map { accounts ->
            Money(accounts.firstOrNull { it.type == AccountType.CASH }?.currentBalanceMinor ?: 0L)
        },
    ) { permission, unmatched, pass, lastAction, cash ->
        IngestionState(
            permission = permission,
            unmatchedCount = unmatched,
            backfill = pass.running,
            // A finished pass speaks for itself; _lastAction carries only what it
            // cannot say.
            lastRun = pass.finished?.inWords() ?: lastAction,
            cashBalance = cash,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IngestionState(),
    )

    /**
     * Records that the dialog was shown. Without this the repository cannot tell
     * "never asked" from "asked and refused", and every refusal would look like
     * a first run.
     */
    fun onPermissionRequested() = viewModelScope.launch { smsPermission.onRequested() }

    /**
     * Enqueued, not collected. A pass now outlives this screen, and the unique-work
     * policy is what stops a second one starting -- the guard that used to live here
     * was forgotten the moment the ViewModel died.
     */
    fun onBackfill() = scheduler.backfill()

    /**
     * Six years of ATM withdrawals with no cash spending entered against them leave
     * the cash account holding money that is long gone. This writes that off as of
     * today so the figure starts meaning something.
     */
    fun onResetCash() {
        viewModelScope.launch {
            val cash = accountDao.getAll().firstOrNull { it.type == AccountType.CASH } ?: return@launch
            transactions.resetToZero(cash.id, clock.now()).fold(
                onSuccess = { _lastAction.value = "Cash starts again from zero" },
                onFailure = { _lastAction.value = "Could not reset cash. Please try again." },
            )
        }
    }

    fun onReparse() = scheduler.reparse()

    val state: StateFlow<KhataPreferences> = repository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KhataPreferences.Default,
    )

    // Each setter edits one axis and carries the other three forward. The tuner
    // is four independent controls over one value.
    fun onFieldSelected(field: FieldPalette) = save { it.copy(field = field) }

    fun onGroundSelected(ground: Color) = save { it.copy(ground = ground) }

    fun onAccentSelected(accent: Color) = save { it.copy(accent = accent) }

    fun onIntensitySelected(intensity: FieldIntensity) = save { it.copy(intensity = intensity) }

    fun onResetTheme() = viewModelScope.launch { repository.resetTheme() }

    /**
     * Wired to the HOME VIEW control in Settings since Task 6. Writing here
     * only changes what `KhataNavHost` resolves as its start destination on the
     * *next* process launch (I6) — it freezes that value for the lifetime of
     * the current one, because rebuilding the graph under a live back stack
     * would pop it and eject the user out of Settings mid-interaction. The
     * supporting text under HOME VIEW says as much.
     */
    fun onHomeViewSelected(view: HomeView) = viewModelScope.launch { repository.setHomeView(view) }

    fun onGeminiKeyChanged(key: String?) = viewModelScope.launch { repository.setGeminiKey(key) }

    fun onBackupPassphraseChanged(passphrase: String?) =
        viewModelScope.launch { repository.setBackupPassphrase(passphrase) }

    fun onBackUpNow() = viewModelScope.launch {
        val prefs = repository.preferences.first()
        val key = prefs.backupKey ?: return@launch
        val salt = prefs.backupSalt ?: return@launch

        val file = backups.backUp(
            Base64.decode(key, Base64.NO_WRAP),
            Base64.decode(salt, Base64.NO_WRAP),
        )
        if (file == null) {
            _lastAction.value = "Could not back up"
            return@launch
        }

        // The same upload the nightly worker does. Without it "Back up now" would
        // write a local copy and quietly leave Drive a day behind, which is the one
        // thing the status line is there to rule out.
        _lastAction.value = when (driveUploader.upload(file)) {
            UploadOutcome.UPLOADED -> "Backed up, and uploaded"
            UploadOutcome.SKIPPED -> "Backed up"
            UploadOutcome.FAILED -> "Backed up, but the upload failed"
        }
    }

    /**
     * [launch] receives the consent sender when one is needed. Nothing is launched
     * when access was already granted -- the account is stored and there is no
     * screen to show.
     */
    fun onConnectDrive(activity: android.app.Activity, launch: (android.content.IntentSender) -> Unit) =
        viewModelScope.launch { driveAuth.beginConnect(activity)?.let(launch) }

    fun onConnectResult(data: android.content.Intent?) =
        viewModelScope.launch { driveAuth.completeConnect(data) }

    fun onDisconnectDrive() = viewModelScope.launch { repository.setDriveConnected(false) }

    /** Empty when Drive is unreachable, which the chooser reports as such. */
    suspend fun driveBackupList(): List<DriveFile> = driveBackups.list()

    suspend fun downloadFromDrive(id: String): ByteArray? = driveBackups.download(id)

    /** The newest backup, or null when there is none to share. */
    fun latestBackup(): File? = backups.latest()

    /**
     * The app's own backups, newest first. They live in app-private storage, which the
     * system file picker cannot browse -- so without this list, restoring last night's
     * backup would mean exporting it out and picking it back in, which defeats keeping
     * seven of them.
     */
    fun localBackups(): List<File> =
        backups.backupDir().listFiles()?.sortedByDescending { it.name } ?: emptyList()

    fun onRestore(bytes: ByteArray, passphrase: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val salt = BackupFile.saltOf(bytes)
            if (salt == null) {
                onResult("That is not a Khata backup file.")
                return@launch
            }
            val result = backups.restore(bytes, BackupFile.deriveKey(passphrase, salt))
            onResult(
                when (result) {
                    is BackupResult.Restored ->
                        "Restored. Khata will close so it can reopen the restored data."
                    BackupResult.NotABackup -> "That is not a Khata backup file."
                    BackupResult.TooNew -> "That backup was made by a newer version of Khata."
                    BackupResult.WrongPassphrase ->
                        "Wrong passphrase, or the file has been altered."
                },
            )
        }
    }

    // Reads the store rather than state.value. `state` is WhileSubscribed, so
    // with no collector it never leaves its initial value -- and a save made
    // before the first collection would then overwrite the stored theme with
    // the default plus one edited axis.
    private fun save(edit: (ThemeSpec) -> ThemeSpec) = viewModelScope.launch {
        repository.setTheme(edit(repository.preferences.first().themeSpec))
    }
}

/** Numbers a person can act on, rather than a struct dump. */
fun IngestSummary.inWords(): String = buildList {
    add("$recorded recorded")
    if (updated > 0) add("$updated updated")
    if (unmatched > 0) add("$unmatched unread")
    if (ignored > 0) add("$ignored ignored")
}.joinToString(" · ")
