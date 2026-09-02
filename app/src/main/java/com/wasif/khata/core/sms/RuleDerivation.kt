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

/**
 * A money figure. The alternation exists because EBL really does send
 * "Balance is BDT .56" -- with no digit before the point -- and `[\d,]+` alone
 * cannot read it.
 */
private const val NUMBER = """(?:[\d,]+(?:\.\d{1,2})?|\.\d{1,2})"""
private const val MONEY = """(?:BDT|Tk)\.? ?$NUMBER"""
private const val EBL_STAMP = """\d{2}-[A-Za-z]{3}-\d{2}\s+\d{2}:\d{2}:\d{2}\s+[AP]M"""
private const val BKASH_STAMP = """\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}"""

private val EBL_DATETIME = Regex(EBL_STAMP)
private val BKASH_DATETIME = Regex(BKASH_STAMP)
private val AMOUNT_WITH_UNIT = Regex(MONEY, RegexOption.IGNORE_CASE)

/**
 * Shapes that must vary even where the user did not tap them, paired with what
 * they become.
 *
 * Without this, everything outside a labelled span is literal -- so a rule taught
 * from one EBL transfer carried "on 25-JUL-26 01:20:52 PM" inside it and matched
 * exactly that one message out of forty identical ones. Nobody tapping "the
 * amount" means "and only ever at 01:20:52 PM".
 *
 * Deliberately narrow: only timestamps and money, both of which are values by
 * definition. A bare number keeps its literal meaning, because "EBL Helpline
 * 16230" is part of what identifies the message, not a quantity that varies.
 * Timestamps are tried before money since a date is also full of digits.
 */
private val VARYING_SHAPES: List<Pair<Regex, String>> = listOf(
    EBL_DATETIME to EBL_STAMP,
    BKASH_DATETIME to BKASH_STAMP,
    AMOUNT_WITH_UNIT to MONEY,
)

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
        if (AMOUNT_WITH_UNIT.matches(selected.trim())) MONEY else NUMBER

    FieldKind.DATETIME -> when {
        EBL_DATETIME.matches(selected.trim()) -> EBL_STAMP
        BKASH_DATETIME.matches(selected.trim()) -> BKASH_STAMP
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
 * The text between the labelled spans. Escapes metacharacters one at a time rather
 * than with \Q…\E, because the pattern is shown to the user and has to stay
 * readable. Runs of whitespace become \s+ so a double space in a later message
 * does not break the rule.
 *
 * Anything matching a [VARYING_SHAPES] entry is generalised rather than escaped:
 * the surrounding words are what identify a message, not the timestamp or the
 * balance that happened to be in the one example.
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

        val varying = VARYING_SHAPES.firstNotNullOfOrNull { (shape, pattern) ->
            shape.matchAt(text, i)?.let { it.range.last + 1 to pattern }
        }
        if (varying != null) {
            append(varying.second)
            i = varying.first
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

/**
 * A pattern that matches this message's *shape*, for the "not a transaction"
 * action on the unread list.
 *
 * The opening words are what identify a bank message: 108 verification codes on a
 * real phone differed only in the code itself, and "Your bKash verification code
 * is" separates them from everything else that sender sends. So the pattern is the
 * leading run of words that carry no digits, anchored at the start.
 *
 * Anchored deliberately: an unanchored phrase would also match a real transaction
 * that happened to quote it.
 *
 * Returns null when the opening is too short to be distinctive. "Payment Tk 20.00
 * to Grameenphone" reduces to two words, and an IGNORE rule that broad would
 * silence a whole class of real payments -- better to refuse and say so than to
 * guess.
 */
fun deriveIgnorePattern(body: String): String? {
    val opening = body.trim().split(Regex("""\s+"""))
        .takeWhile { word -> word.none { it.isDigit() } }
        .take(MAX_IGNORE_WORDS)

    if (opening.size < MIN_IGNORE_WORDS) return null
    return "^" + opening.joinToString("""\s+""") { literal(it) }
}

/** Enough words to be about one kind of message, short enough to survive a reworded tail. */
private const val MIN_IGNORE_WORDS = 3
private const val MAX_IGNORE_WORDS = 6
