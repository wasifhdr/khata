package com.wasif.khata.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wasif.khata.MainActivity
import com.wasif.khata.R
import com.wasif.khata.core.model.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "transfer-review"

/** The transaction a notification is about, carried through both of its intents. */
const val EXTRA_TRANSACTION_ID = "transactionId"

@Singleton
class TransferNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * The transaction id is the notification id, so a second question never replaces
     * the first and retracting one leaves the others standing.
     */
    fun ask(transactionId: Long, amountMinor: Long, merchantRaw: String?) {
        ensureChannel()

        val yes = PendingIntent.getActivity(
            context,
            transactionId.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val no = PendingIntent.getBroadcast(
            context,
            transactionId.toInt(),
            Intent(context, TransferReviewReceiver::class.java)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        manager.notify(
            transactionId.toInt(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Was this a transfer?")
                .setContentText("${Money(amountMinor).format()} · ${merchantRaw ?: "Unknown"}")
                .setAutoCancel(true)
                // "Not mine" answers without opening anything, because it is the
                // commoner answer and an app launch to say "nothing to do here" is a
                // tax on the common case.
                .addAction(0, "Not mine", no)
                .addAction(0, "Own transfer", yes)
                .build(),
        )
    }

    fun retract(transactionId: Long) = manager.cancel(transactionId.toInt())

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Transfers",
                // Low: this is a question that can wait, not an alarm. It must be
                // findable, never loud -- the same rule the ledger's marker follows.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
