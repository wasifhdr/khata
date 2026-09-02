package com.wasif.khata.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var pipeline: IngestionPipeline

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A multipart message arrives as several parts of one logical message; joining
        // the bodies keeps a long bank SMS parseable as a whole.
        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val receivedAt = messages.first().timestampMillis

        // goAsync keeps the process alive past onReceive returning, which a database
        // write on a background dispatcher would otherwise race.
        val pending = goAsync()
        scope.launch {
            try {
                pipeline.ingest(sender, body, receivedAt)
            } finally {
                pending.finish()
            }
        }
    }
}
