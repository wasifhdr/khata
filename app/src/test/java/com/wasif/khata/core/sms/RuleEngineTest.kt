package com.wasif.khata.core.sms

import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val engine = RuleEngine()

    private fun rule(
        id: Long,
        name: String,
        sender: String,
        body: String,
        kind: RuleKind = RuleKind.NORMAL,
        direction: TransactionDirection? = TransactionDirection.DEBIT,
        priority: Int = 100,
        enabled: Boolean = true,
    ) = ParsingRuleEntity(
        id = id,
        uuid = "r-$id",
        name = name,
        senderPattern = sender,
        bodyPattern = body,
        direction = direction,
        kind = kind,
        priority = priority,
        origin = "BUILTIN",
        isEnabled = enabled,
        sampleMessage = "",
        createdAt = 0,
        updatedAt = 0,
    )

    private val paymentRule = rule(
        id = 1,
        name = "bkash payment",
        sender = "bKash",
        body = """Payment of (?<amount>Tk [\d,.]+) to (?<merchant>.+?) is successful\. Balance (?<balance>Tk [\d,.]+)\. TrxID (?<refId>\w+)""",
    )

    private val otpRule = rule(
        id = 2,
        name = "bkash otp",
        sender = "bKash",
        body = """Do NOT share your OTP""",
        kind = RuleKind.IGNORE,
        direction = null,
        priority = 10,
    )

    @Test
    fun `a matching rule yields a parsed message`() {
        val outcome = engine.parse(
            sender = "bKash",
            body = "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01",
            rules = listOf(paymentRule),
        )

        val parsed = (outcome as ParseOutcome.Parsed).value
        assertEquals(Money(85600), parsed.amount)
        assertEquals(Money(4198), parsed.balance)
        assertEquals("FOODPANDA BANGLADESH LIMITED", parsed.merchant)
        assertEquals("DHV41FIPGY", parsed.providerTxnId)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
    }

    @Test
    fun `an IGNORE rule wins over an extracting rule regardless of list order`() {
        val body =
            "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min."

        val outcome = engine.parse("bKash", body, listOf(paymentRule, otpRule))

        assertTrue("expected Ignored, got $outcome", outcome is ParseOutcome.Ignored)
        assertEquals("bkash otp", (outcome as ParseOutcome.Ignored).ruleName)
    }

    @Test
    fun `a rule whose sender does not match is skipped`() {
        val outcome = engine.parse(
            sender = "EBL",
            body = "Payment of Tk 856.00 to X is successful. Balance Tk 1.00. TrxID ABC",
            rules = listOf(paymentRule),
        )

        assertEquals(ParseOutcome.Unmatched, outcome)
    }

    @Test
    fun `a disabled rule never matches`() {
        val outcome = engine.parse(
            sender = "bKash",
            body = "Payment of Tk 856.00 to X is successful. Balance Tk 1.00. TrxID ABC",
            rules = listOf(paymentRule.copy(isEnabled = false)),
        )

        assertEquals(ParseOutcome.Unmatched, outcome)
    }

    @Test
    fun `a soft deleted rule never matches`() {
        val outcome = engine.parse(
            sender = "bKash",
            body = "Payment of Tk 856.00 to X is successful. Balance Tk 1.00. TrxID ABC",
            rules = listOf(paymentRule.copy(deletedAt = 1L)),
        )

        assertEquals(ParseOutcome.Unmatched, outcome)
    }

    @Test
    fun `no matching rule yields Unmatched rather than a guess`() {
        val outcome = engine.parse("bKash", "Some entirely new format", listOf(paymentRule, otpRule))

        assertEquals(ParseOutcome.Unmatched, outcome)
    }

    @Test
    fun `a malformed rule pattern is skipped instead of stopping the pipeline`() {
        val broken = rule(id = 3, name = "broken", sender = "bKash", body = "(?<unclosed", priority = 1)

        val outcome = engine.parse(
            sender = "bKash",
            body = "Payment of Tk 856.00 to X is successful. Balance Tk 1.00. TrxID ABC",
            rules = listOf(broken, paymentRule),
        )

        assertTrue("a broken high-priority rule must not block later rules", outcome is ParseOutcome.Parsed)
    }

    @Test
    fun `absent optional groups become null rather than empty strings`() {
        val minimal = rule(
            id = 4,
            name = "amount only",
            sender = "bKash",
            body = """Cashback (?<amount>Tk [\d,.]+)""",
            direction = TransactionDirection.CREDIT,
        )

        val parsed = (engine.parse("bKash", "Cashback Tk 2.90", listOf(minimal)) as ParseOutcome.Parsed).value

        assertEquals(Money(290), parsed.amount)
        assertNull(parsed.merchant)
        assertNull(parsed.balance)
        assertNull(parsed.providerTxnId)
        assertNull(parsed.accountTail)
    }
}
