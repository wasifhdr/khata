package com.wasif.khata.core.sms

import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection

/**
 * The alternation reads a figure with no leading digit. EBL sends
 * "Balance is BDT .56" and "debited with BDT .06", which `[\d,]+` alone rejects.
 */
private const val AMT = """(?:BDT|Tk)\.? ?(?:[\d,]+(?:\.\d{1,2})?|\.\d{1,2})"""
private const val EBL_DT = """\d{2}-[A-Za-z]{3}-\d{2} \d{2}:\d{2}:\d{2} [AP]M"""
private const val BKASH_DT = """\d{2}/\d{2}/\d{4} \d{2}:\d{2}"""
private const val MASK = """[\d*]+"""
private const val TRX = """[A-Z0-9]+"""

/** Any Bengali codepoint. See the catch-all at the bottom of the list. */
private const val BENGALI = """[ঀ-৿]"""

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

    // 1-19: IGNORE, ahead of everything. An OTP message carries a real amount and
    // a real merchant and would otherwise parse cleanly into a phantom payment.
    //
    // These are matched case-insensitively where the bank varies its own casing:
    // the OTP rule used to demand "Do NOT share your OTP" and every one of the
    // 108 verification codes says "Please do NOT share", so none were caught.
    rule(
        "ignore-bkash-otp", "bKash OTP", "bKash",
        """(?i)verification code|one[- ]time password|never share the otp|do not share your otp""",
        1, RuleKind.IGNORE,
    ),
    rule(
        "ignore-ebl-otp", "EBL OTP", "EBL",
        """(?i)use OTP:|The OTP request is for|one[- ]time password|never ever share your otp""",
        2, RuleKind.IGNORE,
    ),
    rule(
        "ignore-bkash-loan-terms", "bKash loan terms", "bKash",
        """You have received Loan of .+ in your bKash Account""", 3, RuleKind.IGNORE,
    ),
    rule("ignore-bkash-binding", "bKash account binding", "bKash", """Account Binding""", 4, RuleKind.IGNORE),
    rule("ignore-ebl-standing", "EBL standing instruction", "EBL", """Standing Instruction Execution Status""", 5, RuleKind.IGNORE),

    // A declined card is not a transaction: no money moved.
    rule("ignore-ebl-declined", "EBL declined", "EBL", """Not sufficient fund""", 6, RuleKind.IGNORE),
    rule(
        "ignore-ebl-statement", "EBL statement notice", "EBL",
        """Dear Customer, your Deposit Account|Dear Cardholder""", 7, RuleKind.IGNORE,
    ),
    // The money already left at "Received Recharge request"; this later note
    // carries no balance and no TrxID, so recording it would double the recharge.
    rule(
        "ignore-bkash-recharge-ack", "bKash recharge confirmation", "bKash",
        """Your bKash Mobile Recharge request of .+ was successful""", 8, RuleKind.IGNORE,
    ),

    // Card and account admin: no money moves in any of these.
    // "Card replacement" alone also appears in "Debit Card Replacement Fee", which
    // is a real charge, so the request wording is matched instead.
    rule(
        "ignore-ebl-card-admin", "EBL card notices", "EBL",
        """(?i)has not been activated|request for Card replacement|VISA DEBIT CARD has been sent|Smart IVR service|Stay alert to fraud|discount coupons|Add Money transaction at bKash merchant|cheque book is now available|Skybanking account has been activated|One Time PIN for EBL Skybanking|internet outage""",
        9, RuleKind.IGNORE,
    ),
    rule(
        "ignore-bkash-admin", "bKash notices", "bKash",
        """(?i)registration reference number|Account registration|discount coupon|received BDT \d+ discount|Welcome to bKash App|Priyo Agent Number""",
        10, RuleKind.IGNORE,
    ),

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
        "bkash-card-deposit", "bKash deposit from card", "bKash",
        """You have received deposit of (?<amount>$AMT) from (?<merchant>.+?)\. Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        23, RuleKind.TRANSFER_IN, TransactionDirection.CREDIT,
    ),
    // No balance in this one, and the incentive is a second figure that must not
    // be mistaken for the amount -- "Total" is what reached the account.
    rule(
        "bkash-remittance", "bKash remittance", "bKash",
        """(?s)You have received remittance\.\s*Total:\s*(?<amount>$AMT).*?TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        24, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-cash-in", "bKash cash in", "bKash",
        """Cash In (?<amount>$AMT) from (?<merchant>\d+) successful\. Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        25, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    // The sender is not always digits -- commission and cashback arrive from ids
    // like DM1473 -- and an "as Commission" clause can sit before the sender.
    rule(
        "bkash-received", "bKash received money", "bKash",
        """You have received (?<amount>$AMT)(?: as [^.]+?)? from (?<merchant>[A-Za-z0-9]+)\.\s*(?:Ref .*?\.)?\s*Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        26, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-savings-deposit", "bKash savings deposit", "bKash",
        """Savings Deposit Payment (?<amount>$AMT) to (?<merchant>.+?) is successful\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        27, RuleKind.TRANSFER_OUT, TransactionDirection.DEBIT,
    ),
    // The balance is already reduced here, so this is where the money leaves.
    rule(
        "bkash-recharge-request", "bKash mobile recharge", "bKash",
        """Received Recharge request of (?<amount>$AMT) for (?<merchant>\d+)\. Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        28, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    rule(
        "bkash-recharge-failed", "bKash recharge refund", "bKash",
        """Mobile Recharge request has failed\. (?<amount>$AMT) returned to your bKash Account\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        29, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    // bKash writes this both ways -- "Payment of Tk 20" and "Payment Tk 20" --
    // and the second spelling is the more common one across six years.
    rule(
        "bkash-payment-success", "bKash payment", "bKash",
        """Payment (?:of )?(?<amount>$AMT) to (?<merchant>.+?) is successful\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        30, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    rule(
        "bkash-payment-reserved", "bKash payment reserved", "bKash",
        """Payment of (?<amount>$AMT) is being reserved for (?<merchant>.+?)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        31, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    // Deliberately captures no refId. A release carries the reserve's own TrxID,
    // so capturing it would make this an *update* of the reserve -- replacing a
    // debit with a credit and crediting the money twice. Left uncaptured, it
    // becomes its own row that cancels the hold, which is what happened.
    rule(
        "bkash-reserve-released", "bKash reservation released", "bKash",
        """Reserved amount (?<amount>$AMT) for (?<merchant>.+?) has been released\. Balance (?<balance>$AMT)\.""",
        32, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),

    // bKash writes interest in Banglish rather than English.
    rule(
        "bkash-interest", "bKash interest", "bKash",
        """Apni bKash-e (?<amount>$AMT) Interest peyechhen\. Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        36, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    // Laid out over several lines with labelled fields rather than a sentence.
    rule(
        "bkash-bill-pay", "bKash bill payment", "bKash",
        """(?s)Bill successfully paid\..*?Biller:\s*(?<merchant>[^
]+?)\s*
.*?Amount:\s*(?<amount>$AMT).*?TrxID:\s*(?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        35, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    rule(
        "bkash-send-money", "bKash send money", "bKash",
        """Send Money (?<amount>$AMT) to (?<merchant>\d+) successful\.\s*(?:Ref .*?\.)?\s*Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        37, RuleKind.NORMAL, TransactionDirection.DEBIT,
    ),
    // A refund from a merchant rather than a person: named sender, and no fee line.
    rule(
        "bkash-received-merchant", "bKash received from merchant", "bKash",
        """You have received (?<amount>$AMT) from (?<merchant>[^.]+?)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        38, RuleKind.NORMAL, TransactionDirection.CREDIT,
    ),
    rule(
        "bkash-cash-out", "bKash cash out", "bKash",
        """Cash Out (?<amount>$AMT) to (?<merchant>\d+) successful\. Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        33, RuleKind.ATM_WITHDRAWAL, TransactionDirection.DEBIT,
    ),
    rule(
        "bkash-to-bank", "bKash to bank", "bKash",
        """bKash to Bank of (?<amount>$AMT) for (?<merchant>.+?) is successful\. Fee (?<fee>$AMT)\. Balance (?<balance>$AMT)\. TrxID (?<refId>$TRX) at (?<datetime>$BKASH_DT)""",
        34, RuleKind.TRANSFER_OUT, TransactionDirection.DEBIT,
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

    // Money leaving the card to top up a wallet. Same shape as a purchase but
    // worded as a transfer, and it is a transfer between the user's own places.
    rule(
        "ebl-fund-transfer", "EBL fund transfer", "EBL",
        """Fund Transfer of (?<amount>$AMT) from (?<merchant>.+?)\.Card $MASK on (?<datetime>$EBL_DT) BST\.Your A/C (?<account>$MASK) Balance (?<balance>$AMT)""",
        43, RuleKind.TRANSFER_OUT, TransactionDirection.DEBIT,
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

    // 60+: catch-alls, deliberately *after* every extracting rule so they can only
    // ever silence a message nothing above could read.
    //
    // Neither bank has ever sent a transaction in Bengali -- across 892 parsed
    // messages, not one contains a Bengali codepoint, while 226 unread marketing
    // and service notices are written in it. Placed here, this cannot hide a real
    // transaction: if a Bengali one ever arrives, add a rule above and it wins.
    rule("ignore-bengali", "Bank marketing in Bengali", """bKash|EBL""", BENGALI, 60, RuleKind.IGNORE),
    // Separate sender ids that only ever carry promotions.
    rule(
        "ignore-bkash-promo-sender", "bKash promotions", """bKash\s?(?:Offer|Notice|Bonus|App)""",
        """.""", 61, RuleKind.IGNORE,
    ),
)
