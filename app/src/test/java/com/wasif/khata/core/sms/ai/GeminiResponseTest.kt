package com.wasif.khata.core.sms.ai

import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for org.json, which is a platform class rather than a JVM one.
@RunWith(RobolectricTestRunner::class)
class GeminiResponseTest {

    private fun envelope(payload: String) =
        """{"candidates":[{"content":{"parts":[{"text":${JSONObject.quote(payload)}}]}}]}"""

    @Test
    fun `a well-formed suggestion becomes a drafted rule`() {
        val payload = """
            {"name":"bKash payment","senderPattern":"bKash",
             "bodyPattern":"Payment of Tk (?<amount>[0-9.,]+) to (?<merchant>.+?) is successful",
             "direction":"DEBIT","kind":"NORMAL"}
        """.trimIndent()

        val drafted = parseSuggestion(envelope(payload))!!

        assertEquals("bKash payment", drafted.name)
        assertEquals(TransactionDirection.DEBIT, drafted.direction)
        assertEquals(RuleKind.NORMAL, drafted.kind)
    }

    @Test
    fun `a refusal or empty candidate list yields nothing, not a broken rule`() {
        assertNull(parseSuggestion("""{"candidates":[]}"""))
        assertNull(parseSuggestion("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
    }

    @Test
    fun `malformed JSON yields nothing rather than throwing`() {
        // A worker that threw here would retry forever against a reply that will
        // never parse.
        assertNull(parseSuggestion("not json at all"))
        assertNull(parseSuggestion(envelope("""{"name":"missing the pattern"}""")))
    }

    @Test
    fun `a pattern that is not valid regex is rejected`() {
        val payload =
            """{"name":"bad","senderPattern":"bKash","bodyPattern":"([unclosed","direction":"DEBIT","kind":"NORMAL"}"""

        // Storing it would make RuleEngine skip it silently on every message forever,
        // which reads as the AI having done nothing.
        assertNull(parseSuggestion(envelope(payload)))
    }

    @Test
    fun `a normal rule with no direction is rejected`() {
        // RuleEngine skips a rule whose direction is null -- `rule.direction ?: continue`
        // -- so this one would match nothing, ever, while the message stayed unmatched
        // and the app looked like it had learned something. Gemini really did return
        // this: a well-formed Cash Out pattern with the direction field omitted.
        val payload = """
            {"name":"bKash_CashOut","senderPattern":"bKash",
             "bodyPattern":"Cash Out Tk (?<amount>[0-9.]+) agent (?<merchant>.+?) ref",
             "kind":"NORMAL"}
        """.trimIndent()

        assertNull(parseSuggestion(envelope(payload)))
    }

    @Test
    fun `an ignore rule may have no direction`() {
        // The one kind that legitimately extracts nothing, and so needs none.
        val payload = """
            {"name":"promo","senderPattern":"bKash",
             "bodyPattern":"Get 10% cashback","kind":"IGNORE"}
        """.trimIndent()

        val drafted = parseSuggestion(envelope(payload))!!

        assertEquals(RuleKind.IGNORE, drafted.kind)
        assertNull(drafted.direction)
    }

    @Test
    fun `extracted entity fields are parsed alongside the reusable regex`() {
        val payload = """
            {"name":"bKash payment","senderPattern":"bKash",
             "bodyPattern":"Payment of Tk (?<amount>[0-9.,]+) to (?<merchant>.+?) is successful\\. Balance Tk (?<balance>[0-9.,]+)\\. TrxID (?<refId>[A-Z0-9]+) at (?<datetime>[0-9/: ]+)",
             "direction":"DEBIT","kind":"NORMAL",
             "amount":"856.00","merchant":"FOODPANDA","datetime":"31/08/2026 19:01","balance":"41.98"}
        """.trimIndent()

        val drafted = parseSuggestion(envelope(payload))!!

        assertEquals("856.00", drafted.amount)
        assertEquals("FOODPANDA", drafted.merchant)
        assertEquals("31/08/2026 19:01", drafted.datetime)
        assertEquals("41.98", drafted.balance)
    }

    @Test
    fun `requestBody asks for refId and IGNORE classification`() {
        val client = GeminiClient(object : com.wasif.khata.core.prefs.PreferencesRepository {
            override val preferences = kotlinx.coroutines.flow.emptyFlow<com.wasif.khata.core.prefs.KhataPreferences>()
            override suspend fun setTheme(spec: com.wasif.khata.core.ui.theme.ThemeSpec) = Unit
            override suspend fun resetTheme() = Unit
            override suspend fun setHomeView(view: com.wasif.khata.core.prefs.HomeView) = Unit
            override suspend fun setMonthlyBudget(minor: Long?) = Unit
            override suspend fun setSmsPermissionRequested() = Unit
            override suspend fun setBackfilled() = Unit
            override suspend fun setGeminiKey(key: String?) = Unit
            override suspend fun setTmdbKey(key: String?) = Unit
            override suspend fun setBackupPassphrase(passphrase: String?) = Unit
            override suspend fun setDriveConnected(connected: Boolean) = Unit
            override suspend fun setDriveFolderId(id: String?) = Unit
            override suspend fun setDriveUploaded(at: Long) = Unit
            override suspend fun setDriveNeedsReconnect() = Unit
            override suspend fun setSearchIndexVersion(version: Int) = Unit
        })
        val body = client.requestBody("bKash", "Sample SMS")
        org.junit.Assert.assertTrue(body.contains("refId"))
        org.junit.Assert.assertFalse(body.contains("reference"))
        org.junit.Assert.assertTrue(body.contains("IGNORE"))
    }
}
