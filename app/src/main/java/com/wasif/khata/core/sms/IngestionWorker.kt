package com.wasif.khata.core.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.sms.ai.RuleDrafter
import com.wasif.khata.core.sms.ai.RuleSuggester
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow

@HiltWorker
class IngestionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backfill: BackfillUseCase,
    private val reparse: ReparseUseCase,
    private val pipeline: IngestionPipeline,
    private val rawMessages: RawMessageDao,
    private val suggester: RuleSuggester,
    private val drafter: RuleDrafter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        when (inputData.getString(KEY_MODE)?.let(IngestionMode::valueOf)) {
            IngestionMode.BACKFILL -> runPass(backfill.run())
            IngestionMode.REPARSE -> runPass(reparse.run())
            IngestionMode.TEACH -> {
                val raw = rawMessages.findById(inputData.getLong(KEY_RAW_ID, -1))
                    // A raw message that has gone is not a transient problem, so this
                    // fails rather than retrying against something that will never
                    // exist.
                    ?: return Result.failure()
                val drafted = suggester(raw.sender, raw.body)
                    // Retry rather than fail: a rate limit, a dropped connection or a
                    // reply that did not parse should come back, and the message stays
                    // unmatched until it does.
                    ?: return Result.retry()
                if (!drafter.store(drafted, raw.sender, raw.body)) return Result.retry()
                runPass(reparse.run())
            }
            IngestionMode.MESSAGE -> {
                pipeline.ingest(
                    inputData.getString(KEY_SENDER).orEmpty(),
                    inputData.getString(KEY_BODY).orEmpty(),
                    inputData.getLong(KEY_RECEIVED_AT, 0L),
                )
                Data.EMPTY
            }
            // An unreadable mode is a programming error, not a transient one --
            // retrying it forever would be the wrong answer.
            null -> return Result.failure()
        }
    }.fold(
        onSuccess = { Result.success(it) },
        // Retry rather than failure: parsing is idempotent and re-runnable, so a
        // pass that died halfway is safe to run again from the top.
        onFailure = { Result.retry() },
    )

    /** Publishes as it goes, and hands the finished tally back as output. */
    private suspend fun runPass(pass: Flow<IngestProgress>): Data {
        var last: IngestProgress? = null
        pass.collect {
            last = it
            setProgress(it.toData())
        }
        return last?.toData() ?: Data.EMPTY
    }
}
