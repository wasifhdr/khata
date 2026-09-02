package com.wasif.khata.core.sms

import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.RuleKind
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEngine @Inject constructor() {

    fun parse(sender: String, body: String, rules: List<ParsingRuleEntity>): ParseOutcome {
        val ordered = rules.filter { it.isEnabled && it.deletedAt == null }.sortedBy { it.priority }

        for (rule in ordered) {
            val senderRegex = rule.senderPattern.toRegexOrNull() ?: continue
            if (!senderRegex.containsMatchIn(sender)) continue

            val bodyRegex = rule.bodyPattern.toRegexOrNull() ?: continue
            val match = bodyRegex.find(body) ?: continue

            // Checked before anything is extracted: an OTP message carries a real
            // amount and a real merchant, and must never become a transaction.
            if (rule.kind == RuleKind.IGNORE) {
                return ParseOutcome.Ignored(rule.id, rule.name)
            }

            val amount = match.group("amount")?.let(::parseAmount) ?: continue
            val direction = rule.direction ?: continue

            return ParseOutcome.Parsed(
                ParsedMessage(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    kind = rule.kind,
                    direction = direction,
                    amount = amount,
                    balance = match.group("balance")?.let(::parseAmount),
                    merchant = match.group("merchant")?.trim()?.takeIf { it.isNotEmpty() },
                    accountTail = match.group("account")?.let(::accountTail),
                    providerTxnId = match.group("refId"),
                    occurredAt = match.group("datetime")
                        ?.let { parseEblDateTime(it) ?: parseBkashDateTime(it) },
                    feeMinor = match.group("fee")?.let(::parseAmount)?.minor,
                ),
            )
        }
        return ParseOutcome.Unmatched
    }
}

// A user-edited or AI-drafted rule can be syntactically invalid; one bad pattern
// must not stop every later rule from being tried.
private fun String.toRegexOrNull(): Regex? = try {
    toRegex()
} catch (e: IllegalArgumentException) {
    null
}

// Kotlin throws rather than returning null when a named group is absent from the
// pattern, which is the normal case for rules that capture only some fields.
private fun MatchResult.group(name: String): String? = try {
    groups[name]?.value
} catch (e: IllegalArgumentException) {
    null
}
