package com.wasif.khata.core.notify

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TransferNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notifier = TransferNotifier(context)

    @Test
    fun `asking posts one notification carrying the amount`() {
        notifier.ask(transactionId = 7, amountMinor = 500_000, merchantRaw = "EBL Account Transfer")

        val posted = shadowOf(manager).allNotifications.single()
        assertTrue(shadowOf(posted).contentText.toString().contains("5,000"))
    }

    @Test
    fun `retracting takes it back down`() {
        // A partner message arriving at minute five answers the question, and a stale
        // question the owner can no longer answer correctly is worse than no question.
        notifier.ask(transactionId = 7, amountMinor = 500_000, merchantRaw = "EBL Account Transfer")
        notifier.retract(transactionId = 7)

        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `two rows ask separately rather than replacing each other`() {
        notifier.ask(transactionId = 7, amountMinor = 500_000, merchantRaw = "EBL Account Transfer")
        notifier.ask(transactionId = 8, amountMinor = 100_000, merchantRaw = "NPSB FUND TRANSFER")

        assertEquals(2, shadowOf(manager).allNotifications.size)
    }
}
