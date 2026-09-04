package com.wasif.khata.core.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_TRANSACTION_ID = "transactionId"

/**
 * Three minutes, measured rather than guessed: 137 of the 140 pairs in a real ledger
 * formed inside it, median 23 seconds. TransferPairing's window stays at fifteen, so a
 * partner arriving after this has run still pairs and clears the flag.
 */
internal const val REVIEW_DELAY_MINUTES = 3L

@HiltWorker
class TransferReviewWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val review: TransferReview,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_TRANSACTION_ID, -1L)
        if (id <= 0L) return Result.success()
        review.reviewIfUnpaired(id)
        return Result.success()
    }
}

@Singleton
class TransferReviewScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Named per transaction and KEEP rather than REPLACE: a reparse can write the same
     * row twice, and the second pass must not restart the clock on a question already
     * counting down.
     */
    fun schedule(transactionId: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "transfer-review-$transactionId",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TransferReviewWorker>()
                .setInitialDelay(REVIEW_DELAY_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_TRANSACTION_ID to transactionId))
                .build(),
        )
    }
}
