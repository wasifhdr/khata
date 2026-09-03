package com.wasif.khata.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
     * Runs now, and every night after.
     *
     * The immediate run is not redundant with the periodic one: the periodic request's
     * initial delay runs to 00:05, so on the launch right after an upgrade the chart
     * would sit empty until midnight -- the exact regression the backfill exists to
     * prevent. It also means the chart catches up when the app is opened, rather than
     * only at midnight. The routine writes only missing days, so a run with nothing to
     * do costs a query and returns.
     *
     * Both are KEEP: relaunching must not reschedule the nightly run and shift its
     * time, nor stack fills behind one already pending.
     */
    fun scheduleNightly() {
        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniqueWork(
            FILL_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SnapshotWorker>().build(),
        )

        workManager.enqueueUniquePeriodicWork(
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
        const val FILL_NOW = "net-worth-snapshot-now"
        val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")
    }
}
