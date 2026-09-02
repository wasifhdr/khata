package com.wasif.khata.core.sms

data class IncomingMessage(
    val sender: String,
    val body: String,
    val receivedAt: Long,
)

/**
 * The seam that keeps a public build possible. READ_SMS is a Google-restricted
 * permission that cannot ship for expense tracking, so a Play Store variant supplies
 * a NotificationListenerService implementation instead and nothing downstream of this
 * interface changes.
 */
interface MessageSource {
    suspend fun readAll(): List<IncomingMessage>
}
