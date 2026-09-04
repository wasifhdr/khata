package com.wasif.khata.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** "Not mine", answered from the notification without opening the app. */
@AndroidEntryPoint
class TransferReviewReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TransactionRepository

    @Inject lateinit var notifier: TransferNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        if (id <= 0L) return

        // goAsync keeps the process alive across the write, which is small. Without it
        // the receiver returns and the coroutine may be killed mid-update, leaving the
        // question answered on screen and still pending in the database.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.dismissTransferReview(id)
                notifier.retract(id)
            } finally {
                pending.finish()
            }
        }
    }
}
