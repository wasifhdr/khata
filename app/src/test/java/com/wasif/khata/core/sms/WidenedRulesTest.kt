package com.wasif.khata.core.sms

import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The built-in rules were written from a 40-message sample. Against a real
 * six-year inbox of 6,018 messages they read 892 and left 836 unread — most of
 * the misses being the same events in wording the sample never showed.
 *
 * Every message here is a real shape from that inbox with third-party numbers
 * masked. The wording, spacing and punctuation are the thing under test.
 */
class WidenedRulesTest {

    private val engine = RuleEngine()

    private fun parse(sender: String, body: String) = engine.parse(sender, body, BUILT_IN_RULES)

    private fun parsed(sender: String, body: String): ParsedMessage {
        val outcome = parse(sender, body)
        assertTrue("expected a parse, got $outcome for: $body", outcome is ParseOutcome.Parsed)
        return (outcome as ParseOutcome.Parsed).value
    }

    private fun assertIgnored(sender: String, body: String) {
        val outcome = parse(sender, body)
        assertTrue("must not become a transaction, got $outcome for: $body", outcome is ParseOutcome.Ignored)
    }

    // --- The wording differences that caused most of the misses ---

    @Test
    fun `payment without the word of is still a payment`() {
        // 220 messages. The rule demanded "Payment of Tk", bKash also writes
        // "Payment Tk", and the second spelling is the more common one.
        val p = parsed(
            "bKash",
            "Payment Tk 120.00 to Grameenphone Ltd-SKITTO-RM44474 is successful. " +
                "Balance Tk 205.23. TrxID 9KH51OFGZJ at 17/11/2022 18:36",
        )
        assertEquals(12000L, p.amount.minor)
        assertEquals(TransactionDirection.DEBIT, p.direction)
        assertEquals("Grameenphone Ltd-SKITTO-RM44474", p.merchant)
    }

    @Test
    fun `money received from a named sender rather than a phone number`() {
        // The rule required the sender to be digits, so commission and cashback
        // credits from ids like DM1473 were never read.
        val p = parsed(
            "bKash",
            "You have received Tk 50.00 as Commission from DM1472. Fee Tk 0.00. " +
                "Balance Tk 2,050.00. TrxID 7FJ79H37BP at 22/06/2020 18:15",
        )
        assertEquals(5000L, p.amount.minor)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `a reference with no space before it`() {
        val p = parsed(
            "bKash",
            "You have received Tk 500.00 from 01700000001.Ref thankyou. Fee Tk 0.00. " +
                "Balance Tk 700.00. TrxID 7GU508FY9J at 30/07/2020 23:12",
        )
        assertEquals(50000L, p.amount.minor)
    }

    @Test
    fun `an empty reference`() {
        val p = parsed(
            "bKash",
            "You have received Tk 1,000.00 from 01700000001.Ref . Fee Tk 0.00. " +
                "Balance Tk 1,056.20. TrxID 9C699OS8MN at 06/03/2022 02:42",
        )
        assertEquals(100000L, p.amount.minor)
    }

    @Test
    fun `remittance arrives across several lines`() {
        // Sent with newlines, and the govt incentive is a second figure that must
        // not be mistaken for the amount.
        val p = parsed(
            "bKash",
            "You have received remittance.\nTotal: Tk 4,105.13\nGovt. incentive: Tk 100.13\n" +
                "TrxID CJ5114OTQR at 05/10/2025 17:56.",
        )
        assertEquals(410513L, p.amount.minor)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `an amount with no leading digit`() {
        // EBL really sends "BDT .06". [\d,]+ needs a digit before the point.
        val p = parsed(
            "EBL",
            "AC 112***286 is debited with BDT .06 as WITHOLDING SOURCE TAX ON CASA ACCOUNTS " +
                "on 29-JUN-23 01:56:48 AM Balance is BDT 9828 Thanks. EBL Helpline 16230",
        )
        assertEquals(6L, p.amount.minor)
    }

    // --- Event types the sample never contained ---

    @Test
    fun `cash in is money arriving`() {
        val p = parsed(
            "bKash",
            "Cash In Tk 500.00 from 01700000001 successful. Fee Tk 0.00. Balance Tk 1,249.20. " +
                "TrxID 7JK9D1UQFJ at 20/10/2020 12:41. Download App: https://bKa.sh/8app",
        )
        assertEquals(50000L, p.amount.minor)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `cash out is money leaving`() {
        val p = parsed(
            "bKash",
            "Cash Out Tk 100.00 to 01700000001 successful. Fee Tk 1.85. Balance Tk 108.85. " +
                "TrxID 8BN6NN8P8O at 23/02/2021 16:12. Download App: https://bKa.sh/8app",
        )
        assertEquals(10000L, p.amount.minor)
        assertEquals(TransactionDirection.DEBIT, p.direction)
        assertEquals(RuleKind.ATM_WITHDRAWAL, p.kind)
    }

    @Test
    fun `send money is money leaving`() {
        val p = parsed(
            "bKash",
            "Send Money Tk 1,200.00 to 01700000001 successful. Ref ref. Fee Tk 5.00. " +
                "Balance Tk 1,431.44. TrxID BGJ18JNM5T at 19/07/2024 11:02",
        )
        assertEquals(120000L, p.amount.minor)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `a card top-up is money arriving`() {
        val p = parsed(
            "bKash",
            "You have received deposit of Tk 100.00 from VISA Card. Fee Tk 0.00. " +
                "Balance Tk 100.41. TrxID AGO3N1P0Z9 at 24/07/2023 17:35",
        )
        assertEquals(10000L, p.amount.minor)
        assertEquals(RuleKind.TRANSFER_IN, p.kind)
    }

    @Test
    fun `a savings instalment is money leaving`() {
        val p = parsed(
            "bKash",
            "Savings Deposit Payment Tk 500.00 to IDLC is successful. Balance Tk 155.75. " +
                "TrxID 9F34J62U5M at 03/06/2022 23:21",
        )
        assertEquals(50000L, p.amount.minor)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `a mobile recharge leaves at the request, and a failure returns it`() {
        val out = parsed(
            "bKash",
            "Received Recharge request of Tk 39.00 for 01700000001. Fee Tk 0.00. " +
                "Balance Tk 2,647.44. TrxID BGJ48HJIF2 at 19/07/2024 10:41. Wait for confirmation.",
        )
        assertEquals(TransactionDirection.DEBIT, out.direction)

        val back = parsed(
            "bKash",
            "Mobile Recharge request has failed. Tk 11.00 returned to your bKash Account. " +
                "Balance Tk 766.08. TrxID 7I42JPEXJC at 04/09/2020 22:53",
        )
        assertEquals(TransactionDirection.CREDIT, back.direction)
        assertEquals(1100L, back.amount.minor)
    }

    @Test
    fun `the later recharge confirmation is ignored so the money is not counted twice`() {
        // It carries no balance and no TrxID; the money already left at the request.
        assertIgnored(
            "bKash",
            "Your bKash Mobile Recharge request of Tk 50.00 for 01700000001 was successful. " +
                "Use bKash App for convenience & offers! TCA Download App: https://bKa.sh/5app",
        )
    }

    @Test
    fun `bill payment is laid out as labelled lines rather than a sentence`() {
        val p = parsed(
            "bKash",
            "Bill successfully paid.\nBiller: 01700000001 \nMMYYYY/Contact: 032022\nA/C: 21203832 \n" +
                "Amount: Tk 500.00 \nFee: Tk 5.00 \nTrxID: 9C3184HQZB at 03/03/2022 23:08",
        )
        assertEquals(50000L, p.amount.minor)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `interest is written in banglish`() {
        val p = parsed(
            "bKash",
            "Apni bKash-e Tk 0.98 Interest peyechhen. Fee Tk 0.00. Balance Tk 1,650.98. " +
                "TrxID 7HA95EIFO3 at 10/08/2020 06:14. Helpline 16247",
        )
        assertEquals(98L, p.amount.minor)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    // --- A held amount and its release have to cancel out ---

    @Test
    fun `a released reservation credits the money back`() {
        val p = parsed(
            "bKash",
            "Reserved amount Tk 385.47 for Uber Bangladesh Ltd-Uber has been released. " +
                "Balance Tk 527.19. TrxID AHA18706X7 at 10/08/2023 17:37",
        )
        assertEquals(38547L, p.amount.minor)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `a release captures no reference, so it cannot overwrite the hold it cancels`() {
        val p = parsed(
            "bKash",
            "Reserved amount Tk 385.47 for Uber Bangladesh Ltd-Uber has been released. " +
                "Balance Tk 527.19. TrxID AHA18706X7 at 10/08/2023 17:37",
        )
        // The release carries the reserve's own TrxID. Capturing it would make this
        // an update of the reserve -- replacing a debit with a credit -- instead of
        // a second row that nets it to zero.
        assertEquals(null, p.providerTxnId)
    }

    // --- IGNORE, which must never swallow a real transaction ---

    @Test
    fun `a verification code is ignored whatever the casing`() {
        // The rule demanded "Do NOT share your OTP"; all 108 of these say
        // "Please do NOT share", so every one of them slipped through.
        assertIgnored(
            "bKash",
            "Your bKash verification code is 308559. The code will expire in 2 minutes. " +
                "Please do NOT share your OTP or PIN with others.",
        )
    }

    @Test
    fun `EBL one-time passwords are ignored in both spellings`() {
        assertIgnored("EBL", "Your One-Time Password (OTP) for E-Commerce Transaction is 029431. Validity for OTP is 5 minutes.")
        assertIgnored("EBL", "Your One Time Password (OTP) is 462003 for SMART IVR Service. OTP duration is 3 minutes.")
    }

    @Test
    fun `a declined card moved no money`() {
        assertIgnored(
            "EBL",
            "EBL CARDS: Not sufficient fund for USD0 from AMAZON WEB SERVICES AWS.A. " +
                "Card 452017**9386 on 26-Feb-24 11:04:13 PM BST. EBL Helpline 16230",
        )
    }

    @Test
    fun `marketing in Bengali is ignored`() {
        assertIgnored("EBL", "এই রমজানে ইবিএল কার্ডে উপভোগ করুন আকর্ষণীয় সব অফার! বিস্তারিত: www.ebl.com.bd/ramadanoffers")
        assertIgnored("bKashNotice", "সেভিংস আইডি: IDLC-376093 মাসিক কিস্তি: 500.00 টাকা")
    }

    @Test
    fun `the Bengali catch-all sits below every transaction rule`() {
        // It is last on purpose: a transaction rule that matches always wins, so
        // this can only ever silence something nothing above could read.
        val bengali = BUILT_IN_RULES.single { it.uuid == "builtin-ignore-bengali" }
        val extracting = BUILT_IN_RULES.filter { it.kind != RuleKind.IGNORE }
        assertTrue(extracting.all { it.priority < bengali.priority })
    }

    @Test
    fun `a card replacement fee is a charge, not a card notice`() {
        // "Card replacement" also appears in the admin notice, and matching it
        // loosely silenced two real fees.
        val p = parsed(
            "EBL",
            "AC 112***286 is debited with BDT 575 as Debit Card Replacement Fee " +
                "on 15-MAR-26 04:50:21 PM Balance is BDT 1234 Thanks. EBL Helpline 16230",
        )
        assertEquals(57500L, p.amount.minor)
    }

    @Test
    fun `promotional sender ids carry nothing worth reading`() {
        assertIgnored("bKash Offer", "কার্ড থেকে বিকাশ-এ ৫,০৫০ টাকা আনলেই ৫০ টাকা ক্যাশব্যাক, ১ বার! TCA")
    }

    @Test
    fun `every built-in rule has its own priority`() {
        val priorities = BUILT_IN_RULES.map { it.priority }
        assertEquals("two rules at the same priority make matching order arbitrary", priorities.size, priorities.toSet().size)
    }

    @Test
    fun `every ignore rule outranks every extracting rule`() {
        // Except the deliberate catch-alls at the bottom, which are ordered last
        // so they cannot pre-empt a real reading.
        val catchAlls = setOf("builtin-ignore-bengali", "builtin-ignore-bkash-promo-sender")
        val highestIgnore = BUILT_IN_RULES
            .filter { it.kind == RuleKind.IGNORE && it.uuid !in catchAlls }
            .maxOf { it.priority }
        val lowestExtracting = BUILT_IN_RULES.filter { it.kind != RuleKind.IGNORE }.minOf { it.priority }
        assertTrue(highestIgnore < lowestExtracting)
    }
}
