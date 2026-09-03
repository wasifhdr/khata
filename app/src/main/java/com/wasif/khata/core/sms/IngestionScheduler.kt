package com.wasif.khata.core.sms

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Starting the first backfill, as a function type. MainViewModel needs to trigger one
 * but has no business holding a WorkManager-shaped dependency -- and a test of the
 * first-launch rule should not have to stand up WorkManager to watch it be called.
 */
fun interface StartBackfill {
    operator fun invoke()
}

/**
 * Asking for a message to be taught, as a function type. IngestionPipeline cannot
 * depend on the scheduler that runs the worker that calls the pipeline -- and the
 * binding is also where "is a key set" is decided, so the pipeline does not need to
 * know that the AI exists at all.
 */
fun interface TeachRequest {
    suspend operator fun invoke(rawMessageId: Long)
}

/** What a whole-inbox pass is doing, and what the last one came to. */
data class PassState(
    val running: IngestProgress? = null,
    val finished: IngestSummary? = null,
)

@Singleton
class IngestionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun backfill() = enqueuePass(IngestionMode.BACKFILL)

    fun reparse() = enqueuePass(IngestionMode.REPARSE)

    /**
     * Deliberately not under the unique name: a message arriving during a backfill
     * must not be dropped for colliding with it, and one message contends for
     * nothing.
     */
    fun message(sender: String, body: String, receivedAt: Long) {
        workManager.enqueue(
            OneTimeWorkRequestBuilder<IngestionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_MODE to IngestionMode.MESSAGE.name,
                        KEY_SENDER to sender,
                        KEY_BODY to body,
                        KEY_RECEIVED_AT to receivedAt,
                    ),
                )
                .build(),
        )
    }

    /**
     * Off the whole-inbox unique name for the same reason a single message is: a teach
     * is small and must not be dropped for colliding with a backfill.
     */
    fun teach(rawMessageId: Long) {
        workManager.enqueue(
            OneTimeWorkRequestBuilder<IngestionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_MODE to IngestionMode.TEACH.name,
                        KEY_RAW_ID to rawMessageId,
                    ),
                )
                .build(),
        )
    }

    /**
     * By unique name rather than by id, so a screen that did not start the pass can
     * still report it -- which is the whole point of moving off an in-memory flow.
     */
    fun observePass(): Flow<PassState> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_PASS).map { infos ->
            val info = infos.lastOrNull() ?: return@map PassState()
            when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED ->
                    PassState(running = info.progress.toIngestProgress())
                WorkInfo.State.SUCCEEDED ->
                    PassState(finished = info.outputData.toIngestProgress()?.summary)
                else -> PassState()
            }
        }

    /**
     * KEEP, under one name for both passes. Two whole-inbox passes would race on the
     * same tables, and the guard this replaces lived in a ViewModel -- so it was
     * forgotten the moment the screen died.
     */
    private fun enqueuePass(mode: IngestionMode) {
        workManager.enqueueUniqueWork(
            UNIQUE_PASS,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IngestionWorker>()
                .setInputData(workDataOf(KEY_MODE to mode.name))
                .build(),
        )
    }

    private companion object {
        const val UNIQUE_PASS = "ingestion-pass"
    }
}
