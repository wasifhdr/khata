package com.wasif.khata.feature.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CashWidgetIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `each target carries its own direction to the sheet`() {
        val spent = quickEntryIntent(context, TransactionDirection.DEBIT)
        val received = quickEntryIntent(context, TransactionDirection.CREDIT)

        assertEquals("DEBIT", spent.getStringExtra(EXTRA_DIRECTION))
        assertEquals("CREDIT", received.getStringExtra(EXTRA_DIRECTION))
        assertEquals(QuickEntryActivity::class.java.name, spent.component?.className)
    }

    // Two PendingIntents that differ only in extras are "the same" to
    // PendingIntent.getActivity unless their request codes differ -- the second
    // silently replaces the first and both buttons record a spend.
    @Test
    fun `the two targets do not collapse into one pending intent`() {
        assertNotEquals(
            requestCodeFor(TransactionDirection.DEBIT),
            requestCodeFor(TransactionDirection.CREDIT),
        )
    }
}
