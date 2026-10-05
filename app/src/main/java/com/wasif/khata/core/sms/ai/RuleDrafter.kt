package com.wasif.khata.core.sms.ai

import com.wasif.khata.core.data.dao.ParsingRuleDao
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.sms.deriveIgnorePattern
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI rules live in a reserved band above every hand-written priority number, so a rule
 * you wrote always beats one that was guessed. RuleEngine sorts ascending, so a larger
 * number is a lower rank. User rules take max+1 among non-AI rules, which is why the
 * band has to be this far out of the way.
 */
const val AI_PRIORITY_BASE = 100_000

@Singleton
class RuleDrafter @Inject constructor(
    private val rules: ParsingRuleDao,
    private val clock: KhataClock,
) {
    /**
     * Stores [drafted] and reports whether it did. False when the pattern matches
     * nothing in the message it was drafted from: a rule that matches no message is not
     * a rule, and storing it would leave the engine skipping it forever.
     */
    suspend fun store(drafted: DraftedRule, sender: String, sample: String): Boolean {
        val senderRegex = runCatching { Regex(drafted.senderPattern) }.getOrNull() ?: return false
        if (!senderRegex.containsMatchIn(sender)) return false

        val bodyPattern = when {
            runCatching { Regex(drafted.bodyPattern).find(sample) }.getOrNull() != null ->
                drafted.bodyPattern
            drafted.kind == RuleKind.IGNORE ->
                deriveIgnorePattern(sample) ?: return false
            else -> return false
        }

        val now = clock.now()
        val existing = rules.allIncludingDisabled().count { it.origin == "AI" }
        rules.upsertAll(
            listOf(
                ParsingRuleEntity(
                    uuid = UUID.randomUUID().toString(),
                    name = drafted.name,
                    senderPattern = drafted.senderPattern,
                    bodyPattern = bodyPattern,
                    direction = drafted.direction,
                    kind = drafted.kind,
                    priority = AI_PRIORITY_BASE + existing,
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
