package com.wasif.khata.core.sms.ai

import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.prefs.PreferencesRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DraftedRule(
    val name: String,
    val senderPattern: String,
    val bodyPattern: String,
    val direction: TransactionDirection?,
    val kind: RuleKind,
)

/**
 * Asking for a rule, as a function type. The worker depends on this rather than on
 * GeminiClient, so its test needs neither a network nor a key.
 */
fun interface RuleSuggester {
    suspend operator fun invoke(sender: String, body: String): DraftedRule?
}

private const val ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"

/**
 * Null on anything that is not a usable rule: no candidates, a refusal, malformed JSON,
 * missing fields, or a body pattern that is not valid regex.
 *
 * A rule the engine would silently skip on every message is worse than no rule at all,
 * because it reads as the AI having done nothing.
 */
fun parseSuggestion(json: String): DraftedRule? = runCatching {
    val text = JSONObject(json)
        .optJSONArray("candidates")
        ?.optJSONObject(0)
        ?.optJSONObject("content")
        ?.optJSONArray("parts")
        ?.optJSONObject(0)
        ?.optString("text")
        ?: return null

    val payload = JSONObject(text)
    val sender = payload.optString("senderPattern").ifBlank { return null }
    val body = payload.optString("bodyPattern").ifBlank { return null }

    // Compiled here, not at match time: RuleEngine's toRegexOrNull would skip an
    // invalid pattern on every message forever without ever saying so.
    runCatching {
        Regex(sender)
        Regex(body)
    }.getOrElse { return null }

    val direction = payload.optString("direction")
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { TransactionDirection.valueOf(it) }.getOrNull() }

    val kind = payload.optString("kind")
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { RuleKind.valueOf(it) }.getOrNull() }
        ?: RuleKind.NORMAL

    // RuleEngine skips a rule whose direction is null -- `rule.direction ?: continue`
    // -- so a NORMAL rule without one matches nothing, ever, while the message stays
    // unmatched and the app looks like it learned something. Same reason the invalid
    // regex above is refused: silence that reads as success is the worst outcome.
    // Only IGNORE rules extract nothing and so may have none.
    if (kind != RuleKind.IGNORE && direction == null) return null

    DraftedRule(
        name = payload.optString("name").ifBlank { "AI rule" },
        senderPattern = sender,
        bodyPattern = body,
        direction = direction,
        kind = kind,
    )
}.getOrNull()

@Singleton
class GeminiClient @Inject constructor(
    private val preferences: PreferencesRepository,
) : RuleSuggester {

    override suspend fun invoke(sender: String, body: String): DraftedRule? =
        withContext(Dispatchers.IO) {
            // No key is the kill switch: nothing is built and nothing is sent.
            val key = preferences.preferences.first().geminiKey ?: return@withContext null

            val connection = (URL("$ENDPOINT?key=$key").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("Content-Type", "application/json")
                }

            try {
                connection.outputStream.use { it.write(requestBody(sender, body).toByteArray()) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    // Worth a line. A refusal, a bad key and a blocked socket all end
                    // up as "no rule", which is indistinguishable from the model
                    // declining -- and that is exactly how a missing INTERNET
                    // permission hid here for as long as it did.
                    android.util.Log.w("KhataGemini", "generateContent -> " + code)
                    return@withContext null
                }
                parseSuggestion(connection.inputStream.bufferedReader().readText())
            } catch (e: IOException) {
                // Null rather than a throw: the worker turns this into a retry with
                // backoff, which is the right answer to a flaky connection or a rate
                // limit, and a crash is not.
                null
            } finally {
                connection.disconnect()
            }
        }

    /**
     * Structured output, not prose: this is field extraction, and a sentence reply
     * would need a parser of its own.
     */
    private fun requestBody(sender: String, body: String): String {
        val instruction = """
            You are given one SMS from a Bangladeshi bank or mobile money service that an
            existing rule set failed to parse. Produce a Kotlin-compatible regular
            expression that extracts this message FORMAT, not this message. Use named
            groups from: amount, merchant, datetime, balance, account, reference. Do not
            match literal amounts or names. senderPattern should match the sender.
            Sender: $sender
            Message: $body
        """.trimIndent()

        val schema = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("name", JSONObject().put("type", "STRING"))
                    .put("senderPattern", JSONObject().put("type", "STRING"))
                    .put("bodyPattern", JSONObject().put("type", "STRING"))
                    .put(
                        "direction",
                        JSONObject()
                            .put("type", "STRING")
                            .put("enum", JSONArray(listOf("DEBIT", "CREDIT"))),
                    )
                    .put(
                        "kind",
                        JSONObject()
                            .put("type", "STRING")
                            // Left open, the model answers this with a description --
                            // "Cash Out" -- which falls back to NORMAL by luck rather
                            // than by design. These are the only values RuleKind has.
                            .put(
                                "enum",
                                JSONArray(RuleKind.entries.map { it.name }),
                            ),
                    ),
            )
            .put(
                "required",
                JSONArray(listOf("name", "senderPattern", "bodyPattern", "direction")),
            )

        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", instruction)),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schema),
            )
            .toString()
    }
}
