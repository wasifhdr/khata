package com.wasif.khata.core.sms

import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection

private const val AMT = """(?:BDT|Tk)\.? ?[\d,]+(?:\.\d{1,2})?"""
private const val EBL_DT = """\d{2}-[A-Za-z]{3}-\d{2} \d{2}:\d{2}:\d{2} [AP]M"""
private const val BKASH_DT = """\d{2}/\d{2}/\d{4} \d{2}:\d{2}"""
private const val MASK = """[\d*]+"""
private const val TRX = """[A-Z0-9]+"""

private fun rule(
    slug: String,
    name: String,
    sender: String,
    body: String,
    priority: Int,
    kind: RuleKind = RuleKind.NORMAL,
    direction: TransactionDirection? = null,
) = ParsingRuleEntity(
    uuid = "builtin-$slug",
    name = name,
    senderPattern = sender,
    bodyPattern = body,
    direction = direction,
    kind = kind,
    priority = priority,
    origin = "BUILTIN",
    isEnabled = true,
    sampleMessage = "",
    createdAt = 0,
    updatedAt = 0,
)

val BUILT_IN_RULES: List<ParsingRuleEntity> = listOf(

    // 1-19: IGNORE. These outrank every extracting rule because an OTP message
    // carries a real amount and a real merchant and would otherwise parse cleanly.
    rule("ignore-bkash-otp", "bKash OTP", "bKash", """Do NOT share your OTP""", 1, RuleKind.IGNORE),
    rule("ignore-ebl-otp", "EBL OTP", "EBL", """use OTP:|The OTP request is for""", 2, RuleKind.IGNORE),
    rule(
        "ignore-bkash-loan-terms", "bKash loan terms", "bKash",
        """You have received Loan of .+ in your bKash Account""", 3, RuleKind.IGNORE,
    ),
    rule("ignore-bkash-binding", "bKash account binding", "bKash", """Account Binding request for""", 4, RuleKind.IGNORE),
    rule("ignore-ebl-standing", "EBL standing instruction", "EBL", """Standing Instruction Execution Status""", 5, RuleKind.IGNORE),

    // 20-39: bKash, most specific first.
    rule(
        "bkash-loan", "bKash digital loan", "bKash",
        """You have received Digital Loan (?<amount>$AMT) from (?<merchant>.+?)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        20, RuleKind.LOAN_DISBURSEMENT, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-cashback", "bKash cashback", "bKash",
        """You have received Cashback (?<amount>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        21, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-ibanking-deposit", "bKash deposit from bank", "bKash",
        """You have received deposit from iBanking of (?<amount>$AMT) from (?<merchant>.+?)\. Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        22, RuleKind.TRANSFER_IN, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-received", "bKash received money", "bKash",
        """You have received (?<amount>$AMT) from (?<merchant>\d+)\.(?: Ref .+?\.)? Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        23, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-payment-success", "bKash payment", "bKash",
        """Payment of (?<amount>$AMT) to (?<merchant>.+?) is successful\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        24, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    rule(
        "bkash-payment-reserved", "bKash payment reserved", "bKash",
        """Payment of (?<amount>$AMT) is being reserved for (?<merchant>.+?)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        25, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),

    // 40-59: EBL, most specific first.
    rule(
        "ebl-cash-wd", "EBL ATM withdrawal", "EBL",
        """Cash WD (?<amount>$AMT) from (?<merchant>.+?)\. Card $MASK on (?<datetime>$EBL_DT) BST\.Your A/C (?<account>$MASK) Balance (?<balance>$AMT)""",
        40, RuleKind.ATM_WITHDRAWAL, TransactionDirection.DEBIT,
    ),
    rule(
        "ebl-purchase", "EBL card purchase", "EBL",
        """Purchase txn (?<amount>$AMT) from (?<merchant>.+?)\s*\.Card $MASK on (?<datetime>$EBL_DT) BST\.Your A/C (?<account>$MASK) Balance (?<balance>$AMT)""",
        41, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    rule(
        "ebl-cards-npsb", "EBL cards NPSB transfer", "EBL",
        """EBL CARDS: (?<merchant>NPSB Fund Transfer) (?<amount>$AMT) using Card $MASK on (?<datetime>$EBL_DT)\.Your A/C (?<account>$MASK) Balance (?<balance>$AMT)""",
        42, RuleKind.TRANSFER_OUT, TransactionDirection.DEBIT,
    ),

    // Two rules rather than one alternation, because direction is a field on the
    // rule and not something read out of the body.
    rule(
        "ebl-debit", "EBL account debit", "EBL",
        """AC (?<account>$MASK) is debited with (?<amount>$AMT) as (?<merchant>.+?) on (?<datetime>$EBL_DT) Balance is (?<balance>$AMT)""",
        50, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    rule(
        "ebl-credit", "EBL account credit", "EBL",
        """AC (?<account>$MASK) is credited with (?<amount>$AMT) as (?<merchant>.+?) on (?<datetime>$EBL_DT) Balance is (?<balance>$AMT)""",
        51, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
)
