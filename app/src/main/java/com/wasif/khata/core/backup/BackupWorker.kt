package com.wasif.khata.core.backup

import android.content.Context
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wasif.khata.core.drive.DriveUploader
import com.wasif.khata.core.drive.MediaSync
import com.wasif.khata.core.drive.UploadOutcome
import com.wasif.khata.core.prefs.PreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: BackupRepository,
    private val preferences: PreferencesRepository,
    private val uploader: DriveUploader,
    private val mediaSync: MediaSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // No passphrase is the off switch, and it is not an error.
        val prefs = preferences.preferences.first()
        val key = prefs.backupKey ?: return Result.success()
        val salt = prefs.backupSalt ?: return Result.success()

        val file = runCatching {
            repository.backUp(
                Base64.decode(key, Base64.NO_WRAP),
                Base64.decode(salt, Base64.NO_WRAP),
            )
            // Retry: tonight's copy is worth having, and a failed write leaves the
            // previous seven untouched.
        }.getOrElse { return Result.retry() } ?: return Result.success()

        // The local copy is already on disk and stays there whatever happens next. A
        // retry re-runs a backup that already succeeded, which is cheap; the
        // alternative is a night with no offsite copy.
        val outcome = uploader.upload(file)

        // After the archive, and never instead of it. A photo that fails to upload is
        // not worth retrying the database backup for -- it is already safe on disk,
        // and the next run picks it up because the name is still missing from Drive.
        if (outcome == UploadOutcome.UPLOADED) runCatching { mediaSync.push() }

        return when (outcome) {
            UploadOutcome.UPLOADED, UploadOutcome.SKIPPED -> Result.success()
            UploadOutcome.FAILED -> Result.retry()
        }
    }
}

@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 02:00 Dhaka, after the snapshot job, so a backup contains that night's row. */
    fun scheduleNightly() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NIGHTLY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(Duration.ofDays(1))
                .setInitialDelay(untilNextRun())
                // The local write does not need a network, but the upload that
                // follows it does, and deferring the pair is cheaper than running
                // the backup twice.
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }

    private fun untilNextRun(): Duration {
        val now = ZonedDateTime.now(DHAKA)
        var next = now.with(LocalTime.of(2, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    private companion object {
        const val NIGHTLY = "nightly-backup"
        val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")
    }
}
