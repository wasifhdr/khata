package com.wasif.khata.core.sms

import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every literal here is copied verbatim from docs/superpowers/specs/sms-corpus.md.
 * The spacing and punctuation are the thing under test — never tidy them.
 */
class CorpusTest {

    private val engine = RuleEngine()

    private fun parse(sender: String, body: String): ParseOutcome =
        engine.parse(sender, body, BUILT_IN_RULES)

    private fun parsed(sender: String, body: String): ParsedMessage {
        val outcome = parse(sender, body)
        assertTrue("expected a parse, got $outcome for: $body", outcome is ParseOutcome.Parsed)
        return (outcome as ParseOutcome.Parsed).value
    }

    private fun assertIgnored(sender: String, body: String) {
        val outcome = parse(sender, body)
        assertTrue("must not become a transaction, got $outcome for: $body", outcome is ParseOutcome.Ignored)
    }

    // --- IGNORE: these carry a real amount and must produce nothing ---

    @Test
    fun `bKash payment OTP is ignored despite carrying an amount and a merchant`() = assertIgnored(
        "bKash",
        "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min.",
    )

    @Test
    fun `bKash auto-debit OTP is ignored`() = assertIgnored(
        "bKash",
        "Do NOT share your OTP or PIN with anyone. Your bKash OTP to enable AUTO DEBIT in App or Website of Foodpanda Bangladesh Limited is 372914. Expires in 5 min.",
    )

    @Test
    fun `EBL OTP request is ignored despite carrying an amount`() = assertIgnored(
        "EBL",
        "The OTP request is for a transaction at foodibdcom. To complete your transaction BDT 232.00 at foodibdcom with Card#539***432, use OTP: 823876 . Helpline: 16230",
    )

    @Test
    fun `bKash loan terms notice is ignored so the disbursement is not double counted`() = assertIgnored(
        "bKash",
        "You have received Loan of Tk 900.00 from City Bank in your bKash Account. Your first repayment of TK 309.28 is due on 28/09/2026.",
    )

    @Test
    fun `bKash account binding confirmation is ignored`() = assertIgnored(
        "bKash",
        "Your Account Binding request for FOODPANDA BANGLADESH LIMITED is successful. You have authorized FOODPANDA BANGLADESH LIMITED to debit your account for future purchases. For queries, please call 16247.",
    )

    @Test
    fun `EBL standing instruction status is ignored`() = assertIgnored(
        "EBL",
        "Ref no : 398595SI1764909024 , Your Standing Instruction Execution Status is: Successfully Executed . Helpline 16230.",
    )

    // --- bKash ---

    @Test
    fun `bKash payment successful`() {
        val p = parsed(
            "bKash",
            "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01",
        )
        assertEquals(Money(85600), p.amount)
        assertEquals(Money(4198), p.balance)
        assertEquals("FOODPANDA BANGLADESH LIMITED", p.merchant)
        assertEquals("DHV41FIPGY", p.providerTxnId)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `bKash payment with a thousands separator`() {
        val p = parsed(
            "bKash",
            "Payment of Tk 2,600.00 to CINEPLEXBD is successful. Balance Tk 338.33. TrxID DH2118Z3S1 at 02/08/2026 00:44",
        )
        assertEquals(Money(260000), p.amount)
        assertEquals("CINEPLEXBD", p.merchant)
    }

    @Test
    fun `bKash reserved payment carries the same TrxID as its successful twin`() {
        val reserved = parsed(
            "bKash",
            "Payment of Tk 564.11 is being reserved for Uber Bangladesh Ltd-Uber. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11",
        )
        val successful = parsed(
            "bKash",
            "Payment of Tk 564.11 to Uber Bangladesh Ltd-Uber is successful. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11",
        )
        assertEquals("DHA3BH4G8R", reserved.providerTxnId)
        assertEquals(reserved.providerTxnId, successful.providerTxnId)
        assertEquals(reserved.amount, successful.amount)
    }

    @Test
    fun `bKash received money`() {
        val p = parsed(
            "bKash",
            "You have received Tk 325.00 from 01700000001. Fee Tk 0.00. Balance Tk 527.09. TrxID DHL8NM6BYC at 21/08/2026 12:35",
        )
        assertEquals(Money(32500), p.amount)
        assertEquals(Money(52709), p.balance)
        assertEquals("01700000001", p.merchant)
        assertEquals("DHL8NM6BYC", p.providerTxnId)
        assertEquals(TransactionDirection.CREDIT, p.direction)
        assertEquals(0L, p.feeMinor)
    }

    @Test
    fun `bKash received money with an optional Ref segment`() {
        val p = parsed(
            "bKash",
            "You have received Tk 100.00 from 01700000003. Ref A. Fee Tk 0.00. Balance Tk 238.09. TrxID DHG5IGY2ZP at 16/08/2026 18:47",
        )
        assertEquals(Money(10000), p.amount)
        assertEquals("01700000003", p.merchant)
        assertEquals("DHG5IGY2ZP", p.providerTxnId)
    }

    @Test
    fun `bKash digital loan is a disbursement not plain income`() {
        val p = parsed(
            "bKash",
            "You have received Digital Loan Tk 900.00 from City Bank. Balance Tk 897.98. TrxID DHV31FH6VD at 31/08/2026 19:00.",
        )
        assertEquals(Money(90000), p.amount)
        assertEquals(RuleKind.LOAN_DISBURSEMENT, p.kind)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `bKash cashback is ordinary income`() {
        val p = parsed(
            "bKash",
            "Congratulations! You have received Cashback Tk 2.90. Balance Tk 1,027.11. TrxID DHN4PFHA3Y at 23/08/2026 10:55. Cashback on Loan!",
        )
        assertEquals(Money(290), p.amount)
        assertEquals(Money(102711), p.balance)
        assertEquals(RuleKind.NORMAL, p.kind)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `bKash bank deposit is a transfer in`() {
        val p = parsed(
            "bKash",
            "You have received deposit from iBanking of Tk 2,600.00 from Eastern Bank PLC. Internet Banking. Fee Tk 0.00. Balance Tk 2,938.33. TrxID DH2718YM43 at 02/08/2026 00:43",
        )
        assertEquals(Money(260000), p.amount)
        assertEquals(RuleKind.TRANSFER_IN, p.kind)
        assertEquals(Money(293833), p.balance)
    }

    // --- EBL ---

    @Test
    fun `EBL account debit`() {
        val p = parsed(
            "EBL",
            "AC 115***352 is debited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 58.56 Thanks. EBL Helpline 16230",
        )
        assertEquals(Money(6000), p.amount)
        assertEquals(Money(5856), p.balance)
        assertEquals("352", p.accountTail)
        assertEquals("Own Account Transfer", p.merchant)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `EBL account credit`() {
        val p = parsed(
            "EBL",
            "AC 112***286 is credited with BDT 10000 as NPSB FUND TRANSFER on 01-SEP-26 10:38:57 AM Balance is BDT 10034.2 Thanks. EBL Helpline 16230",
        )
        assertEquals(Money(1000000), p.amount)
        assertEquals(Money(1003420), p.balance)
        assertEquals("286", p.accountTail)
        assertEquals(TransactionDirection.CREDIT, p.direction)
    }

    @Test
    fun `EBL card purchase separates merchant from the Card token`() {
        val p = parsed(
            "EBL",
            "Purchase txn BDT 1101 from TOUR DE CYCLIST Ut.Card 539280**3432 on 31-Aug-26 06:27:44 PM BST.Your A/C 115**9352 Balance BDT 118.56. EBL Helpline 16230",
        )
        assertEquals(Money(110100), p.amount)
        assertEquals("TOUR DE CYCLIST Ut", p.merchant)
        assertEquals("352", p.accountTail)
        assertEquals(Money(11856), p.balance)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `EBL card purchase where the merchant itself contains a period`() {
        val p = parsed(
            "EBL",
            "Purchase txn BDT 232 from foodibd.com Dhaka .Card 539280**3432 on 25-Aug-26 06:27:38 PM BST.Your A/C 115**9352 Balance BDT 1269.56. EBL Helpline 16230",
        )
        assertEquals(Money(23200), p.amount)
        assertEquals("foodibd.com Dhaka", p.merchant)
    }

    @Test
    fun `EBL ATM withdrawal has no space after BDT and is an ATM kind`() {
        val p = parsed(
            "EBL",
            "Cash WD BDT5000 from North South Universi. Card 539280**3432 on 19-Aug-26 06:00:37 PM BST.Your A/C 115**9352 Balance BDT 1501.56. EBL Helpline 16230",
        )
        assertEquals(Money(500000), p.amount)
        assertEquals(RuleKind.ATM_WITHDRAWAL, p.kind)
        assertEquals("352", p.accountTail)
    }

    @Test
    fun `EBL cards NPSB transfer routes to the owning account not the card`() {
        val p = parsed(
            "EBL",
            "EBL CARDS: NPSB Fund Transfer BDT 180 using Card 452017**1835 on 18-Aug-26 12:44:55 AM.Your A/C 112**0286 Balance BDT 6534.2. Thank You. EBL Helpline 16230",
        )
        assertEquals(Money(18000), p.amount)
        assertEquals("286", p.accountTail)
        assertEquals(TransactionDirection.DEBIT, p.direction)
    }

    @Test
    fun `EBL MFS transfer to bKash keeps its account tail`() {
        val p = parsed(
            "EBL",
            "AC 112***286 is debited with BDT 420 as EBL Skybanking MFS Transfer-bKash on 17-AUG-26 06:57:20 PM Balance is BDT 6724.19 Thanks. EBL Helpline 16230",
        )
        assertEquals(Money(42000), p.amount)
        assertEquals("286", p.accountTail)
    }

    // --- whole-corpus coverage ---

    @Test
    fun `no corpus message falls through unmatched`() {
        val unmatched = CORPUS.filter { (sender, body) -> parse(sender, body) is ParseOutcome.Unmatched }

        assertTrue(
            "these messages matched no rule:\n" + unmatched.joinToString("\n") { "  ${it.second}" },
            unmatched.isEmpty(),
        )
    }

    @Test
    fun `every corpus message that parses yields a positive amount`() {
        val bad = CORPUS.mapNotNull { (sender, body) ->
            (parse(sender, body) as? ParseOutcome.Parsed)?.value?.takeIf { it.amount.minor <= 0 }?.let { body }
        }

        assertTrue("these parsed to a non-positive amount:\n${bad.joinToString("\n")}", bad.isEmpty())
    }

    private companion object {
        val CORPUS: List<Pair<String, String>> = listOf(
            "EBL" to "AC 115***352 is debited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 58.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is credited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 5004.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is debited with BDT 5000 as EBL Account Transfer on 01-SEP-26 07:03:20 PM Balance is BDT 4.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is credited with BDT 17316 as AC TRANSFER THROUGH EBL CONNECT on 01-SEP-26 07:34:56 PM Balance is BDT 17374.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is debited with BDT 50 as EBL Skybanking Mobile Recharge on 30-AUG-26 04:28:45 PM Balance is BDT 1219.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is credited with BDT 10000 as NPSB FUND TRANSFER on 01-SEP-26 10:38:57 AM Balance is BDT 10034.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is debited with BDT 5090 as EBL Account Transfer on 01-SEP-26 11:07:10 AM Balance is BDT 4944.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is debited with BDT 420 as EBL Skybanking MFS Transfer-bKash on 17-AUG-26 06:57:20 PM Balance is BDT 6724.19 Thanks. EBL Helpline 16230",
            "EBL" to "AC 112***286 is debited with BDT 6500 as Own Account Transfer on 18-AUG-26 01:20:19 PM Balance is BDT 34.2 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is credited with BDT 6500 as Own Account Transfer on 18-AUG-26 01:20:20 PM Balance is BDT 6516.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is debited with BDT 330 as EBL Account Transfer on 05-AUG-26 05:44:02 PM Balance is BDT 8923.56 Thanks. EBL Helpline 16230",
            "EBL" to "AC 115***352 is debited with BDT 6500 as EBL Account Transfer on 06-AUG-26 12:05:07 AM Balance is BDT 2423.56 Thanks. EBL Helpline 16230",
            "EBL" to "Purchase txn BDT 1101 from TOUR DE CYCLIST Ut.Card 539280**3432 on 31-Aug-26 06:27:44 PM BST.Your A/C 115**9352 Balance BDT 118.56. EBL Helpline 16230",
            "EBL" to "Purchase txn BDT 232 from foodibd.com Dhaka .Card 539280**3432 on 25-Aug-26 06:27:38 PM BST.Your A/C 115**9352 Balance BDT 1269.56. EBL Helpline 16230",
            "EBL" to "Purchase txn BDT 2407 from TOKYO KITCHEN UTTA.Card 539280**3432 on 07-Aug-26 10:51:50 PM BST.Your A/C 115**9352 Balance BDT 118.56. EBL Helpline 16230",
            "EBL" to "Cash WD BDT5000 from North South Universi. Card 539280**3432 on 19-Aug-26 06:00:37 PM BST.Your A/C 115**9352 Balance BDT 1501.56. EBL Helpline 16230",
            "EBL" to "EBL CARDS: NPSB Fund Transfer BDT 180 using Card 452017**1835 on 18-Aug-26 12:44:55 AM.Your A/C 112**0286 Balance BDT 6534.2. Thank You. EBL Helpline 16230",
            "EBL" to "The OTP request is for a transaction at foodibdcom. To complete your transaction BDT 232.00 at foodibdcom with Card#539***432, use OTP: 823876 . Helpline: 16230",
            "EBL" to "Ref no : 398595SI1764909024 , Your Standing Instruction Execution Status is: Successfully Executed . Helpline 16230.",
            "bKash" to "You have received Tk 325.00 from 01700000001. Fee Tk 0.00. Balance Tk 527.09. TrxID DHL8NM6BYC at 21/08/2026 12:35",
            "bKash" to "You have received Tk 142.00 from 01700000001. Fee Tk 0.00. Balance Tk 356.09. TrxID DHA9BTWHE1 at 10/08/2026 22:10",
            "bKash" to "You have received Tk 142.00 from 01700000002. Fee Tk 0.00. Balance Tk 498.09. TrxID DHA5BUPNF1 at 10/08/2026 22:27",
            "bKash" to "You have received Tk 100.00 from 01700000003. Ref A. Fee Tk 0.00. Balance Tk 238.09. TrxID DHG5IGY2ZP at 16/08/2026 18:47",
            "bKash" to "You have received Tk 66.00 from 01700000004. Fee Tk 0.00. Balance Tk 131.09. TrxID DHK6MX34WC at 20/08/2026 18:39",
            "bKash" to "You have received Tk 71.00 from 01700000005. Fee Tk 0.00. Balance Tk 202.09. TrxID DHK2MX5HZA at 20/08/2026 18:39",
            "bKash" to "You have received Tk 650.00 from 01700000006. Fee Tk 0.00. Balance Tk 1,083.20. TrxID DH94A7139O at 09/08/2026 17:33",
            "bKash" to "Payment of Tk 564.11 is being reserved for Uber Bangladesh Ltd-Uber. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11",
            "bKash" to "Payment of Tk 564.11 to Uber Bangladesh Ltd-Uber is successful. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11",
            "bKash" to "Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01",
            "bKash" to "Payment of Tk 750.00 to CINEPLEXBD is successful. Balance Tk 338.33. TrxID DH2918VO8R at 02/08/2026 00:38",
            "bKash" to "Payment of Tk 2,600.00 to CINEPLEXBD is successful. Balance Tk 338.33. TrxID DH2118Z3S1 at 02/08/2026 00:44",
            "bKash" to "You have received deposit from iBanking of Tk 2,600.00 from Eastern Bank PLC. Internet Banking. Fee Tk 0.00. Balance Tk 2,938.33. TrxID DH2718YM43 at 02/08/2026 00:43",
            "bKash" to "You have received Digital Loan Tk 900.00 from City Bank. Balance Tk 897.98. TrxID DHV31FH6VD at 31/08/2026 19:00.",
            "bKash" to "You have received Digital Loan Tk 500.00 from City Bank. Balance Tk 1,024.21. TrxID DHN2PFGRRC at 23/08/2026 10:55.",
            "bKash" to "Congratulations! You have received Cashback Tk 2.90. Balance Tk 1,027.11. TrxID DHN4PFHA3Y at 23/08/2026 10:55. Cashback on Loan!",
            "bKash" to "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min.",
            "bKash" to "Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.2,600.00 to Software Shop Limited-RM51177 is 746654. Expires in 2 min.",
            "bKash" to "Do NOT share your OTP or PIN with anyone. Your bKash OTP to enable AUTO DEBIT in App or Website of Foodpanda Bangladesh Limited is 372914. Expires in 5 min.",
            "bKash" to "You have received Loan of Tk 900.00 from City Bank in your bKash Account. Your first repayment of TK 309.28 is due on 28/09/2026.",
            "bKash" to "Your Account Binding request for FOODPANDA BANGLADESH LIMITED is successful. You have authorized FOODPANDA BANGLADESH LIMITED to debit your account for future purchases. For queries, please call 16247.",
        )
    }
}
