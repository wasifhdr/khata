package com.wasif.khata.core.sms

import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection

data class ParsedMessage(
    val ruleId: Long,
    val ruleName: String,
    val kind: RuleKind,
    val direction: TransactionDirection,
    val amount: Money,
    val balance: Money?,
    val merchant: String?,
    val accountTail: String?,
    val providerTxnId: String?,
    val occurredAt: Long?,
    val feeMinor: Long?,
)

sealed interface ParseOutcome {
    data class Parsed(val value: ParsedMessage) : ParseOutcome
    data class Ignored(val ruleId: Long, val ruleName: String) : ParseOutcome
    data object Unmatched : ParseOutcome
}
