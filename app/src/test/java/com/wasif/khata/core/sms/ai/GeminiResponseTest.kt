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
}
