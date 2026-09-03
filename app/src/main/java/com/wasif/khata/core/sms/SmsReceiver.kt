package com.wasif.khata.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var ingestion: IngestionScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A multipart message arrives as several parts of one logical message; joining
        // the bodies keeps a long bank SMS parseable as a whole.
        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }

        // Enqueued rather than parsed here. goAsync() held the process for about ten
        // seconds, which was ample -- but a parse that outran it dropped the message,
        // recoverable only by a later backfill. Work retries instead, and no window
        // needs holding open because enqueueing returns immediately.
        ingestion.message(sender, body, messages.first().timestampMillis)
    }
}
