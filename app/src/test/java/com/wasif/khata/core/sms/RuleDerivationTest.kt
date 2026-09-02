package com.wasif.khata.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BKASH_PAYMENT =
    "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01"

private const val EBL_DEBIT =
    "AC 115***352 is debited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 58.56 Thanks. EBL Helpline 16230"

class RuleDerivationTest {

    /** Selects the first occurrence of [text], so tests read as what a user tapped. */
    private fun span(body: String, text: String, kind: FieldKind): LabelledSpan {
        val start = body.indexOf(text)
        require(start >= 0) { "'$text' not in body" }
        return LabelledSpan(start, start + text.length, kind)
    }

    @Test
    fun `a labelled amount becomes a named group that matches the original`() {
        val spans = listOf(span(BKASH_PAYMENT, "Tk 856.00", FieldKind.AMOUNT))

        val derived = derivePattern(BKASH_PAYMENT, spans)

        assertNull(derived.error)
        assertTrue("must contain an amount group", derived.pattern.contains("(?<amount>"))
        assertEquals("Tk 856.00", derived.captures["amount"])
    }

    @Test
    fun `the derived pattern always matches the message it came from`() {
        val spans = listOf(
            span(BKASH_PAYMENT, "Tk 856.00", FieldKind.AMOUNT),
            span(BKASH_PAYMENT, "FOODPANDA BANGLADESH LIMITED", FieldKind.MERCHANT),
            span(BKASH_PAYMENT, "Tk 41.98", FieldKind.BALANCE),
            span(BKASH_PAYMENT, "DHV41FIPGY", FieldKind.REFERENCE),
        )

        val derived = derivePattern(BKASH_PAYMENT, spans)

        // A pattern that cannot match its own source message is always a bug, and it
        // is the one guarantee the guided flow can make unconditionally.
        assertNull(derived.error)
        assertEquals("Tk 856.00", derived.captures["amount"])
        assertEquals("FOODPANDA BANGLADESH LIMITED", derived.captures["merchant"])
        assertEquals("Tk 41.98", derived.captures["balance"])
        assertEquals("DHV41FIPGY", derived.captures["refId"])
    }

    @Test
    fun `an amount selected without its unit gets a bare numeric pattern`() {
        val spans = listOf(span(BKASH_PAYMENT, "856.00", FieldKind.AMOUNT))

        val derived = derivePattern(BKASH_PAYMENT, spans)

        assertEquals("856.00", derived.captures["amount"])
        assertTrue("should not demand a unit it did not see", !derived.pattern.contains("(?<amount>(?:BDT|Tk)"))
    }

    @Test
    fun `an EBL datetime is recognised and generalised`() {
        val spans = listOf(
            span(EBL_DEBIT, "BDT 60", FieldKind.AMOUNT),
            span(EBL_DEBIT, "01-SEP-26 06:46:08 PM", FieldKind.DATETIME),
        )

        val derived = derivePattern(EBL_DEBIT, spans)

        assertEquals("01-SEP-26 06:46:08 PM", derived.captures["datetime"])
        // Generalised, not literal: another day must match the same rule.
        val otherDay = EBL_DEBIT.replace("01-SEP-26 06:46:08 PM", "02-OCT-26 11:02:59 AM")
        assertNotNull(Regex(derived.pattern).find(otherDay))
    }

    @Test
    fun `a bKash datetime is recognised and generalised`() {
        val spans = listOf(
            span(BKASH_PAYMENT, "Tk 856.00", FieldKind.AMOUNT),
            span(BKASH_PAYMENT, "31/08/2026 19:01", FieldKind.DATETIME),
        )

        val derived = derivePattern(BKASH_PAYMENT, spans)

        val otherDay = BKASH_PAYMENT.replace("31/08/2026 19:01", "01/09/2026 08:15")
        assertNotNull(Regex(derived.pattern).find(otherDay))
    }

    @Test
    fun `a masked account is generalised so either masking matches`() {
        val spans = listOf(
            span(EBL_DEBIT, "BDT 60", FieldKind.AMOUNT),
            span(EBL_DEBIT, "115***352", FieldKind.ACCOUNT),
        )

        val derived = derivePattern(EBL_DEBIT, spans)

        val otherMask = EBL_DEBIT.replace("115***352", "112**0286")
        assertNotNull(Regex(derived.pattern).find(otherMask))
    }

    @Test
    fun `a different merchant still matches`() {
        val spans = listOf(
            span(BKASH_PAYMENT, "Tk 856.00", FieldKind.AMOUNT),
            span(BKASH_PAYMENT, "FOODPANDA BANGLADESH LIMITED", FieldKind.MERCHANT),
        )

        val derived = derivePattern(BKASH_PAYMENT, spans)
        val other = BKASH_PAYMENT.replace("FOODPANDA BANGLADESH LIMITED", "CINEPLEXBD")

        assertEquals("CINEPLEXBD", Regex(derived.pattern).find(other)?.groups?.get("merchant")?.value)
    }

    @Test
    fun `regex metacharacters in the literal text are escaped`() {
        val body = "Paid Tk 5.00 (ref: A+B) [ok]?"
        val spans = listOf(span(body, "Tk 5.00", FieldKind.AMOUNT))

        val derived = derivePattern(body, spans)

        assertNull(derived.error)
        assertEquals("Tk 5.00", derived.captures["amount"])
    }

    @Test
    fun `runs of whitespace are matched flexibly so a double space does not break the rule`() {
        val spans = listOf(span(BKASH_PAYMENT, "Tk 856.00", FieldKind.AMOUNT))
        val derived = derivePattern(BKASH_PAYMENT, spans)

        val doubleSpaced = BKASH_PAYMENT.replace(" to ", "  to  ")

        assertNotNull(Regex(derived.pattern).find(doubleSpaced))
    }

    @Test
    fun `a rule with no amount is rejected, because it cannot produce a transaction`() {
        val spans = listOf(span(BKASH_PAYMENT, "FOODPANDA BANGLADESH LIMITED", FieldKind.MERCHANT))

        val derived = derivePattern(BKASH_PAYMENT, spans)

        assertEquals("Select the amount — a rule cannot record a transaction without one.", derived.error)
    }

    @Test
    fun `overlapping selections are rejected rather than producing a broken pattern`() {
        val spans = listOf(
            LabelledSpan(11, 20, FieldKind.AMOUNT),
            LabelledSpan(15, 25, FieldKind.MERCHANT),
        )

        val derived = derivePattern(BKASH_PAYMENT, spans)

        assertEquals("Two selections overlap. Tap one again to clear it.", derived.error)
    }

    @Test
    fun `spans are ordered by position regardless of the order they were tapped`() {
        val tappedBackwards = listOf(
            span(BKASH_PAYMENT, "DHV41FIPGY", FieldKind.REFERENCE),
            span(BKASH_PAYMENT, "Tk 856.00", FieldKind.AMOUNT),
        )

        val derived = derivePattern(BKASH_PAYMENT, tappedBackwards)

        assertNull(derived.error)
        assertEquals("Tk 856.00", derived.captures["amount"])
        assertEquals("DHV41FIPGY", derived.captures["refId"])
    }

    @Test
    fun `two spans of the same kind are rejected`() {
        val spans = listOf(
            span(BKASH_PAYMENT, "Tk 856.00", FieldKind.AMOUNT),
            span(BKASH_PAYMENT, "Tk 41.98", FieldKind.AMOUNT),
        )

        val derived = derivePattern(BKASH_PAYMENT, spans)

        assertEquals("Amount is selected twice. Each field can be chosen once.", derived.error)
    }

    @Test
    fun `no selection at all reports the amount requirement rather than an empty pattern`() {
        val derived = derivePattern(BKASH_PAYMENT, emptyList())

        assertNotNull(derived.error)
    }
}
