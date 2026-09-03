package com.wasif.khata.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wasif.khata.core.data.repository.SnapshotWriter
import com.wasif.khata.core.time.KhataClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SnapshotWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val writer: SnapshotWriter,
    private val clock: KhataClock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching { writer.fillThrough(clock.now()) }
        .fold(
            onSuccess = { Result.success() },
            // Retry, not failure: writing only missing days means a repeat run is
            // free, and a failure would leave a hole nothing else fills.
            onFailure = { Result.retry() },
        )
}

@Singleton
class SnapshotScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * KEEP, so relaunching does not reschedule and shift the run time. The first run
     * is also the backfill: on a fresh upgrade it fills years, and after that it finds
     * nothing missing.
     */
    fun scheduleNightly() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NIGHTLY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SnapshotWorker>(Duration.ofDays(1))
                .setInitialDelay(untilNextRun())
                .build(),
        )
    }

    /**
     * 00:05 Dhaka. WorkManager cannot promise a wall-clock minute, and does not need
     * to: the writer fills whatever days are missing, so a run that lands late or not
     * at all costs nothing but freshness.
     */
    private fun untilNextRun(): Duration {
        val now = ZonedDateTime.now(DHAKA)
        var next = now.with(LocalTime.of(0, 5))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    private companion object {
        const val NIGHTLY = "net-worth-snapshot"
        val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")
    }
}
