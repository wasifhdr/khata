package com.wasif.khata.core.sms

import android.content.Context
import android.provider.Telephony
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The only file in the ingestion path that touches an Android content provider. The
 * query stays in one function so a notification-based source can replace it wholesale
 * without disturbing anything downstream.
 */
@Singleton
class SmsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : MessageSource {

    override suspend fun readAll(): List<IncomingMessage> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            buildList {
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(address) ?: continue
                    val text = cursor.getString(body) ?: continue
                    add(IncomingMessage(sender, text, cursor.getLong(date)))
                }
            }
        } ?: emptyList()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MessageSourceModule {
    @Binds
    abstract fun bindMessageSource(impl: SmsSource): MessageSource
}
