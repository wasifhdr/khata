# Khata Plan — AI Fallback

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A message no rule matches is sent to Gemini, which drafts a parsing rule; the rule is stored and every message of that format — past and future — is parsed by the engine that parses everything else.

**Architecture:** The AI writes a rule, never a transaction, so one call fixes a format forever and a wrong answer is repairable in the rule editor. The key lives in app-private DataStore, entered in Settings, never in source — and with no key nothing is sent at all, which is the only gate between a message and Google. A fourth `IngestionWorker` mode does the work, so retry and backoff come free. No new dependencies: `HttpURLConnection` and `org.json`. Three tasks: the key, the client and rule drafter, then the worker mode and its wiring.

**Tech Stack:** Kotlin · WorkManager · Hilt · DataStore · `HttpURLConnection` · `org.json` · JUnit4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-03-khata-ai-fallback-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`, `compileSdk` / `targetSdk 37`, package `com.wasif.khata`.
- **The API key is never in source, never in a test fixture, and never committed.** Tests use a placeholder string. This is not negotiable and is the load-bearing half of spec §13.2.
- **The body is sent unredacted** (spec §3, user decision). The only thing standing between a message and Google is whether a key is set.
- **Only unmatched bodies are ever sent.** Nothing that parsed, and nothing a rule claimed.
- **The AI writes rules, never transactions.** No code path in this plan writes a `TransactionEntity`.
- **No new dependencies.** `HttpURLConnection` is in the JDK, `org.json` is in the Android platform.
- Money is always `Long` **paisa**. Instants UTC millis, boundaries Dhaka.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- Tests never hardcode a magic epoch-millis literal.
- **Ponytail is in force.** Reuse before writing: `IngestionWorker` already has a mode input, `ReparseUseCase` already re-runs the engine, `RuleEngine` already ranks by priority, `ParsingRuleEntity.origin` already distinguishes sources.
- Run unit tests with: `./gradlew :app:testDebugUnitTest`
- Build with: `./gradlew :app:assembleDebug`
- `export JAVA_HOME="/e/Android/Android Studio/jbr"` and `export PATH="$JAVA_HOME/bin:$PATH"` per shell.

## File Structure

**Create:**
- `app/src/main/java/com/wasif/khata/core/sms/ai/GeminiClient.kt` — request, transport, response, behind one interface so tests never touch a network.
- `app/src/main/java/com/wasif/khata/core/sms/ai/RuleDrafter.kt` — turns a response into a `ParsingRuleEntity`, or into nothing.
- Tests: `GeminiResponseTest.kt`, `RuleDrafterTest.kt`, `TeachModeTest.kt`

**Modify:**
- `core/prefs/KhataPreferences.kt`, `PreferencesRepository.kt`, `PreferencesRepositoryImpl.kt` — the key.
- `feature/settings/SettingsScreen.kt`, `SettingsViewModel.kt` — the field.
- `core/sms/IngestionWork.kt`, `IngestionWorker.kt`, `IngestionScheduler.kt` — the `TEACH` mode.
- `core/sms/IngestionPipeline.kt` — enqueue a teach when a message goes `UNMATCHED`.
- `feature/ruleeditor/RuleEditorViewModel.kt`, `feature/unmatched/UnmatchedViewModel.kt` — user priority ignores the AI band.

---

### Task 1: The key, in Settings

Spec §4.

**Files:**
- Modify: `core/prefs/KhataPreferences.kt`, `PreferencesRepository.kt`, `PreferencesRepositoryImpl.kt`, `feature/settings/SettingsScreen.kt`, `SettingsViewModel.kt`
- Test: `test/.../core/prefs/PreferencesRepositoryTest.kt`

**Interfaces:**
- Produces: `KhataPreferences.geminiKey: String?` and `PreferencesRepository.setGeminiKey(key: String?)`. Tasks 2 and 3 consume the first.

- [ ] **Step 1: Write the failing test**

Append to `PreferencesRepositoryTest`, whose repository field is named `repo`:

```kotlin
    @Test
    fun `the key round-trips, and blank clears it`() = runTest {
        assertNull(repo.preferences.first().geminiKey)

        repo.setGeminiKey("placeholder-not-a-real-key")
        assertEquals("placeholder-not-a-real-key", repo.preferences.first().geminiKey)

        // Clearing the key is how AI is switched off; there is no second toggle to
        // disagree with it.
        repo.setGeminiKey(null)
        assertNull(repo.preferences.first().geminiKey)
    }
```

- [ ] **Step 2: Add the preference**

`KhataPreferences` gains:

```kotlin
    /**
     * Null means the AI fallback is off. There is deliberately no separate toggle: a
     * switch that could disagree with whether a key exists is a switch that will.
     */
    val geminiKey: String? = null,
```

with `geminiKey = null` in `Default`. `PreferencesRepository` gains `suspend fun setGeminiKey(key: String?)`. In `PreferencesRepositoryImpl`, a `stringPreferencesKey("gemini_key")`, the mapping line, and:

```kotlin
    override suspend fun setGeminiKey(key: String?) {
        store.edit { p ->
            if (key.isNullOrBlank()) p.remove(Keys.GeminiKey) else p[Keys.GeminiKey] = key
        }
    }
```

Four test files implement `PreferencesRepository` anonymously — `MainViewModelTest`, `SmsPermissionRepositoryTest`, `ModulesViewModelTest`, `SettingsViewModelTest`. Add `override suspend fun setGeminiKey(key: String?) = Unit` to each; the compiler will name any others.

- [ ] **Step 3: Add the Settings field**

In `SettingsScreen`, above the Categories section:

```kotlin
                SectionLabel("AI fallback")
                GeminiKeyField(current = prefs.geminiKey, onChange = onGeminiKeyChanged)
```

`GeminiKeyField` is an `OutlinedTextField` that **shows the stored key masked** — its own local text state starts empty when a key exists, with supporting text saying whether one is set. Typing replaces it; clearing it removes the key.

```kotlin
@Composable
private fun GeminiKeyField(current: String?, onChange: (String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            onChange(input.ifBlank { null })
        },
        label = { Text("Gemini API key") },
        singleLine = true,
        // The stored key is never rendered back. Nothing needs to read it on screen,
        // and a key on a screen is a key in a screenshot.
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
            Text(
                if (current == null) {
                    "Not set. Messages no rule matches stay in the review list."
                } else {
                    "Set. A message no rule matches is sent to Gemini to draft a rule."
                },
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.screenHorizontal),
    )
}
```

Thread `onGeminiKeyChanged` from `SettingsViewModel` (`fun onGeminiKeyChanged(key: String?) = viewModelScope.launch { repository.setGeminiKey(key) }`) the way `onHomeViewSelected` is threaded.

- [ ] **Step 4: Build and run the suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(ai): a key you enter in Settings, and nowhere else"
```

---

### Task 2: The client, and the rule it drafts

Spec §1, §5. Behind an interface, so no test ever opens a socket.

**Files:**
- Create: `core/sms/ai/GeminiClient.kt`, `core/sms/ai/RuleDrafter.kt`
- Test: `test/.../core/sms/ai/GeminiResponseTest.kt`, `RuleDrafterTest.kt`

**Interfaces:**
- Produces: `data class DraftedRule(val name: String, val senderPattern: String, val bodyPattern: String, val direction: TransactionDirection?, val kind: RuleKind)`; `fun interface RuleSuggester { suspend operator fun invoke(sender: String, body: String): DraftedRule? }`; `class GeminiClient` implementing it; `fun parseSuggestion(json: String): DraftedRule?`; `class RuleDrafter` turning a `DraftedRule` into a stored `ParsingRuleEntity`. Task 3 consumes `RuleSuggester` and `RuleDrafter`.

- [ ] **Step 1: Write the failing response-parsing test**

```kotlin
package com.wasif.khata.core.sms.ai

import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiResponseTest {

    private fun envelope(payload: String) =
        """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(payload)}}]}}]}"""

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
        assertNull(parseSuggestion(envelope("{\"name\":\"missing the pattern\"}")))
    }

    @Test
    fun `a pattern that is not valid regex is rejected`() {
        val payload = """{"name":"bad","senderPattern":"bKash","bodyPattern":"([unclosed","direction":"DEBIT","kind":"NORMAL"}"""

        // Storing it would make RuleEngine skip it silently on every message
        // forever, which reads as the AI having done nothing.
        assertNull(parseSuggestion(envelope(payload)))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*GeminiResponseTest*"`
Expected: FAIL — "Unresolved reference 'parseSuggestion'".

- [ ] **Step 3: Write the client**

```kotlin
package com.wasif.khata.core.sms.ai

import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.prefs.PreferencesRepository
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
 * GeminiClient, so its test needs no network and no key.
 */
fun interface RuleSuggester {
    suspend operator fun invoke(sender: String, body: String): DraftedRule?
}

private const val ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"

/**
 * Null on anything that is not a usable rule: no candidates, a refusal, malformed
 * JSON, missing fields, or a body pattern that is not valid regex. A rule the engine
 * would silently skip on every message is worse than none, because it reads as the
 * AI having done nothing.
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

    // Compiled here, not at match time: RuleEngine's toRegexOrNull would just skip
    // an invalid pattern forever without saying so.
    runCatching { Regex(sender); Regex(body) }.getOrElse { return null }

    DraftedRule(
        name = payload.optString("name").ifBlank { "AI rule" },
        senderPattern = sender,
        bodyPattern = body,
        direction = payload.optString("direction")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { TransactionDirection.valueOf(it) }.getOrNull() },
        kind = payload.optString("kind")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { RuleKind.valueOf(it) }.getOrNull() }
            ?: RuleKind.NORMAL,
    )
}.getOrNull()

@Singleton
class GeminiClient @Inject constructor(
    private val preferences: PreferencesRepository,
) : RuleSuggester {

    override suspend fun invoke(sender: String, body: String): DraftedRule? =
        withContext(Dispatchers.IO) {
            val key = preferences.preferences.first().geminiKey ?: return@withContext null

            val connection = (URL("$ENDPOINT?key=$key").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
            }

            try {
                connection.outputStream.use { it.write(requestBody(sender, body).toByteArray()) }
                if (connection.responseCode !in 200..299) return@withContext null
                parseSuggestion(connection.inputStream.bufferedReader().readText())
            } catch (e: java.io.IOException) {
                // Swallowed to null; the worker turns that into a retry with backoff
                // rather than a crash on a flaky connection.
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
            existing rule set failed to parse.
            Produce a Kotlin-compatible regular expression that extracts this message
            FORMAT, not this message. Use named groups from: amount, merchant, datetime,
            balance, account, reference. Do not match literal amounts or names.
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
                    .put("direction", JSONObject().put("type", "STRING"))
                    .put("kind", JSONObject().put("type", "STRING")),
            )
            .put("required", JSONArray(listOf("name", "senderPattern", "bodyPattern")))

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
```

- [ ] **Step 4: Write `RuleDrafter` and its test**

`RuleDrafter` turns a `DraftedRule` into a stored row, and is where the priority band lives.

```kotlin
package com.wasif.khata.core.sms.ai

import com.wasif.khata.core.data.dao.ParsingRuleDao
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.sms.RuleEngine
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI rules live in a reserved band above every hand-written priority number, so a
 * rule you wrote always beats one that was guessed. User rules take max+1 among
 * non-AI rules, which is why the band has to be this far out of the way.
 */
const val AI_PRIORITY_BASE = 100_000

@Singleton
class RuleDrafter @Inject constructor(
    private val rules: ParsingRuleDao,
    private val engine: RuleEngine,
    private val clock: KhataClock,
) {
    /**
     * Stores [drafted] and reports whether it did. False when the pattern matches
     * nothing in the message it was drafted from: a rule that matches no message is
     * not a rule, and storing it would leave the engine skipping it forever.
     */
    suspend fun store(drafted: DraftedRule, sender: String, sample: String): Boolean {
        val senderRegex = runCatching { Regex(drafted.senderPattern) }.getOrNull() ?: return false
        val bodyRegex = runCatching { Regex(drafted.bodyPattern) }.getOrNull() ?: return false
        if (!senderRegex.containsMatchIn(sender)) return false
        if (bodyRegex.find(sample) == null) return false

        val now = clock.now()
        val existing = rules.allIncludingDisabled().filter { it.origin == "AI" }
        rules.upsertAll(
            listOf(
                ParsingRuleEntity(
                    uuid = UUID.randomUUID().toString(),
                    name = drafted.name,
                    senderPattern = drafted.senderPattern,
                    bodyPattern = drafted.bodyPattern,
                    direction = drafted.direction,
                    kind = drafted.kind,
                    priority = AI_PRIORITY_BASE + existing.size,
                    origin = "AI",
                    isEnabled = true,
                    sampleMessage = sample,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        return true
    }
}
```

Its test, against a real in-memory database as the other rule tests do:

```kotlin
    @Test
    fun `a rule that matches nothing is discarded`() = runTest {
        val drafted = DraftedRule(
            name = "wrong", senderPattern = "bKash",
            bodyPattern = "this will never appear", direction = null, kind = RuleKind.NORMAL,
        )

        assertFalse(drafter.store(drafted, "bKash", "Payment of Tk 5.00 to SHOP"))
        assertTrue(db.parsingRuleDao().allIncludingDisabled().none { it.origin == "AI" })
    }

    @Test
    fun `a stored AI rule ranks below every hand-written one`() = runTest {
        val drafted = DraftedRule(
            name = "ok", senderPattern = "bKash",
            bodyPattern = "Payment of Tk (?<amount>[0-9.]+)", direction = null,
            kind = RuleKind.NORMAL,
        )

        assertTrue(drafter.store(drafted, "bKash", "Payment of Tk 5.00 to SHOP"))

        val stored = db.parsingRuleDao().allIncludingDisabled().single { it.origin == "AI" }
        // Lower number wins in RuleEngine, so the AI band must be the largest.
        assertTrue(stored.priority >= AI_PRIORITY_BASE)
    }
```

Seed the database with `BUILT_IN_RULES` via `DatabaseSeeder` first, as `BackfillProgressTest` does.

- [ ] **Step 5: Keep user rules out of the AI band**

In `RuleEditorViewModel` and `UnmatchedViewModel`, the new-rule priority is `max + 1` over every rule. An AI rule in the band would push every later user rule into it. Both become:

```kotlin
                val priority = existing?.priority
                    ?: ((ruleDao.allIncludingDisabled().filter { it.origin != "AI" }
                        .maxOfOrNull { it.priority } ?: 0) + 1)
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*Gemini*" --tests "*RuleDrafter*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/sms/ai/ app/src/main/java/com/wasif/khata/feature/ \
        app/src/test/java/com/wasif/khata/core/sms/ai/
git commit -m "feat(ai): ask for a rule, and refuse one that matches nothing"
```

---

### Task 3: The TEACH mode, and when it fires

Spec §2, §6.

**Files:**
- Modify: `core/sms/IngestionWork.kt`, `IngestionWorker.kt`, `IngestionScheduler.kt`, `IngestionPipeline.kt`
- Test: `test/.../core/sms/TeachModeTest.kt`

**Interfaces:**
- Consumes: `RuleSuggester`, `RuleDrafter` (Task 2).
- Produces: `IngestionMode.TEACH` and `IngestionScheduler.teach(rawMessageId: Long)`.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `an unmatched message becomes a rule, and then parses`() = runTest {
        val rawId = /* ingest a message no rule matches, then read its id back */ 0L
        val suggester = RuleSuggester { _, _ ->
            DraftedRule(
                name = "learned", senderPattern = "bKash",
                bodyPattern = "Paid Tk (?<amount>[0-9.]+) to (?<merchant>.+)",
                direction = TransactionDirection.DEBIT, kind = RuleKind.NORMAL,
            )
        }

        val result = worker(suggester, KEY_MODE to IngestionMode.TEACH.name, KEY_RAW_ID to rawId)
            .doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        // The payoff: the rule is stored AND the reparse it triggers has turned the
        // message into a transaction, through the ordinary engine.
        assertEquals(1, db.transactionDao().allActive().size)
    }

    @Test
    fun `a suggester that returns nothing asks to be retried`() = runTest {
        val rawId = /* the same unmatched message */ 0L

        val result = worker(RuleSuggester { _, _ -> null }, KEY_MODE to IngestionMode.TEACH.name, KEY_RAW_ID to rawId)
            .doWork()

        // Retry, not failure: a rate limit or a flaky connection should come back,
        // and the message stays unmatched until it does.
        assertTrue(result is ListenableWorker.Result.Retry)
    }
```

Build the pipeline exactly as `IngestionWorkerTest` does, and construct the worker with the same `TestListenableWorkerBuilder` + `WorkerFactory` pattern, passing the fake suggester through. Replace both `/* … */` markers by ingesting a message the built-in rules do not match — `"Paid Tk 55.00 to NEW SHOP"` works — and reading `db.rawMessageDao()` for its id.

- [ ] **Step 2: Add the mode**

In `IngestionWork.kt`: `TEACH` joins the enum, and `const val KEY_RAW_ID = "raw_id"`.

In `IngestionWorker`, inject `RuleSuggester`, `RuleDrafter` and `RawMessageDao`, and add the branch:

```kotlin
            IngestionMode.TEACH -> {
                val raw = rawMessages.findById(inputData.getLong(KEY_RAW_ID, -1))
                    ?: return Result.failure()
                val drafted = suggester(raw.sender, raw.body)
                    // Retry rather than fail: a rate limit or a dropped connection
                    // should come back, and the message stays unmatched until it does.
                    ?: return Result.retry()
                if (!drafter.store(drafted, raw.sender, raw.body)) return Result.retry()
                runPass(reparse.run())
            }
```

`RawMessageDao` needs `findById` if it has none; add `@Query("SELECT * FROM raw_messages WHERE id = :id") suspend fun findById(id: Long): RawMessageEntity?`.

- [ ] **Step 3: Enqueue it when a message goes unmatched**

`IngestionScheduler` gains:

```kotlin
    /**
     * Off the whole-inbox unique name: a teach is small and must not be dropped for
     * colliding with a backfill.
     */
    fun teach(rawMessageId: Long) {
        workManager.enqueue(
            OneTimeWorkRequestBuilder<IngestionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_MODE to IngestionMode.TEACH.name,
                        KEY_RAW_ID to rawMessageId,
                    ),
                )
                .build(),
        )
    }
```

In `IngestionPipeline`, where a message is recorded `UNMATCHED`, enqueue a teach **only when a key is set and the pass is not a backfill**. Spec §6: a first backfill over years would otherwise fire hundreds of requests before the user has seen a screen. The pipeline already distinguishes its entry points — `ingest` is a live message and `process` is the re-run path — so the teach belongs on the `ingest` side only.

To avoid `IngestionPipeline` depending on the scheduler that runs the worker that calls the pipeline, inject a `fun interface TeachRequest { operator fun invoke(rawMessageId: Long) }` bound to `scheduler::teach`, the same shape `StartBackfill` uses.

- [ ] **Step 4: Build and run the whole suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Verify on hardware, once, with a real key**

This is the only step that touches the network, and the key comes from the device.

1. Settings → AI fallback → paste a real key. The field masks it and reports "Set".
2. Send the emulator an SMS in a shape no rule matches:
   `adb emu sms send bKash "Paid Tk 55.00 to NEW SHOP LIMITED on 03/09/2026"`
3. Within a few seconds it appears in the Ledger — the rule was drafted, stored and reparsed.
4. Settings → Unmatched: the message is no longer listed.
5. The rule editor shows a rule whose origin is AI.
6. Clear the key. Send another unmatched shape. It stays in the review list and no request is made.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(ai): a message no rule matches teaches the app a rule"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` passes, including the redactor and response-parsing cases.
- The hardware walkthrough passes, including step 6 — no key means no request.
- No key appears anywhere in the repository.
- An AI rule is visible in the rule editor and ranks below every hand-written rule.

## Deferred

Merchant-to-category suggestion, key proxying for a public build, and any AI involvement in categorising or writing transactions. All recorded in spec §8.
