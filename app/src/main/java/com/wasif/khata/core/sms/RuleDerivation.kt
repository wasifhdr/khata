package com.wasif.khata.core.sms

/** The parts of a message a rule can capture, in the order they are offered. */
enum class FieldKind(val groupName: String, val label: String) {
    AMOUNT("amount", "Amount"),
    MERCHANT("merchant", "Merchant"),
    BALANCE("balance", "Balance"),
    ACCOUNT("account", "Account"),
    DATETIME("datetime", "Date"),
    REFERENCE("refId", "Reference"),
}

data class LabelledSpan(val start: Int, val endExclusive: Int, val kind: FieldKind)

data class DerivedRule(
    val pattern: String,
    val captures: Map<String, String>,
    val error: String?,
)

private val EBL_DATETIME = Regex("""\d{2}-[A-Za-z]{3}-\d{2} \d{2}:\d{2}:\d{2} [AP]M""")
private val BKASH_DATETIME = Regex("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}""")
private val AMOUNT_WITH_UNIT = Regex("""(?:BDT|Tk)\.? ?[\d,]+(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE)

/**
 * Builds a rule pattern from spans the user tapped. Everything between the spans
 * becomes literal, which is exactly right for a templated bank message: the variable
 * parts are precisely the ones that were labelled.
 *
 * The one guarantee this can always make is that the result matches the message it
 * came from — a pattern that fails its own source is never useful, so that is checked
 * rather than assumed.
 */
fun derivePattern(body: String, spans: List<LabelledSpan>): DerivedRule {
    val ordered = spans.sortedBy { it.start }

    duplicateKind(ordered)?.let { kind ->
        return DerivedRule("", emptyMap(), "${kind.label} is selected twice. Each field can be chosen once.")
    }
    if (overlaps(ordered)) {
        return DerivedRule("", emptyMap(), "Two selections overlap. Tap one again to clear it.")
    }
    if (ordered.none { it.kind == FieldKind.AMOUNT }) {
        return DerivedRule("", emptyMap(), "Select the amount — a rule cannot record a transaction without one.")
    }

    val pattern = buildString {
        var cursor = 0
        for (span in ordered) {
            append(literal(body.substring(cursor, span.start)))
            append("(?<${span.kind.groupName}>")
            append(generalise(body.substring(span.start, span.endExclusive), span.kind))
            append(")")
            cursor = span.endExclusive
        }
        append(literal(body.substring(cursor)))
    }

    val match = runCatching { Regex(pattern).find(body) }.getOrNull()
        ?: return DerivedRule(
            pattern,
            emptyMap(),
            "That selection does not produce a working rule. Try selecting whole words.",
        )

    return DerivedRule(
        pattern = pattern,
        captures = ordered.associate { it.kind.groupName to (match.groups[it.kind.groupName]?.value ?: "") },
        error = null,
    )
}

/** What the selected text should be allowed to vary into. Inferred from what was selected. */
private fun generalise(selected: String, kind: FieldKind): String = when (kind) {
    FieldKind.AMOUNT, FieldKind.BALANCE ->
        // Only demand a unit if the selection actually included one.
        if (AMOUNT_WITH_UNIT.matches(selected.trim())) """(?:BDT|Tk)\.? ?[\d,]+(?:\.\d{1,2})?"""
        else """[\d,]+(?:\.\d{1,2})?"""

    FieldKind.DATETIME -> when {
        EBL_DATETIME.matches(selected.trim()) -> """\d{2}-[A-Za-z]{3}-\d{2} \d{2}:\d{2}:\d{2} [AP]M"""
        BKASH_DATETIME.matches(selected.trim()) -> """\d{2}/\d{2}/\d{4} \d{2}:\d{2}"""
        // An unrecognised date shape: keep it loose rather than guess a format.
        else -> """[^\s].{0,30}?"""
    }

    // Both maskings of one account appear across message types, so digits and stars.
    FieldKind.ACCOUNT -> """[\d*]+"""
    FieldKind.REFERENCE -> """[A-Z0-9]+"""
    // Non-greedy: the literal text that follows is what stops it.
    FieldKind.MERCHANT -> """.+?"""
}

/**
 * Escapes metacharacters one at a time rather than with \Q…\E, because the pattern is
 * shown to the user and has to stay readable. Runs of whitespace become \s+ so a
 * double space in a later message does not break the rule.
 */
private fun literal(text: String): String = buildString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c.isWhitespace()) {
            while (i < text.length && text[i].isWhitespace()) i++
            append("""\s+""")
            continue
        }
        if (c in """\.[]{}()<>*+-=!?^$|""") append('\\')
        append(c)
        i++
    }
}

private fun duplicateKind(spans: List<LabelledSpan>): FieldKind? =
    spans.groupBy { it.kind }.entries.firstOrNull { it.value.size > 1 }?.key

private fun overlaps(ordered: List<LabelledSpan>): Boolean =
    ordered.zipWithNext().any { (a, b) -> b.start < a.endExclusive }
