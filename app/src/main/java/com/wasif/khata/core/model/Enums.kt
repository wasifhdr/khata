package com.wasif.khata.core.model

enum class AccountType { MFS, BANK, CARD, CASH, MANUAL_ASSET }

enum class TransactionDirection { DEBIT, CREDIT }

enum class TransactionSource { SMS, NOTIFICATION, WIDGET, MANUAL }

enum class Confidence { HIGH, MEDIUM, LOW }

enum class RawMessageStatus { PENDING, PARSED, UNMATCHED, IGNORED }

enum class RuleKind { NORMAL, TRANSFER_OUT, TRANSFER_IN, ATM_WITHDRAWAL, FEE, LOAN_DISBURSEMENT, IGNORE }

// Schema-level rather than a category: categories are user-editable, and a renamed
// or deleted one must not be able to break net-worth arithmetic.
enum class TransactionKind {
    NORMAL,
    TRANSFER,

    /** A loan from an institution, as bKash's digital loan. Not person-to-person. */
    LOAN_DISBURSEMENT,
    LOAN_REPAYMENT,
    FEE,
    ADJUSTMENT,

    /** Money handed to someone who is expected to return it. */
    LENT,

    /** They returned it. */
    LENT_RETURNED,

    /** Money someone handed you that you owe back. */
    BORROWED,

    /** You returned it. */
    BORROWED_RETURNED,

    /** Their share of a bill you paid, handed back to you. */
    REIMBURSEMENT,
    ;

    /**
     * Whether money leaving under this kind was actually consumed.
     *
     * Lending it out, settling a debt, moving it between your own accounts or
     * correcting a balance are all money leaving an account without anything being
     * bought. Counting them as spending is how "where did my money go" starts
     * lying: on a real month it read Tk 59,252 against Tk 25,232 actually spent.
     */
    val countsAsSpending: Boolean
        get() = this !in setOf(TRANSFER, ADJUSTMENT, LENT, BORROWED_RETURNED, LOAN_REPAYMENT)

    /**
     * Whether money arriving under this kind is genuinely yours to keep. Borrowed
     * money has to go back, a returned loan was always yours, and a reimbursement
     * is your own money coming home.
     */
    val countsAsIncome: Boolean
        get() = this !in setOf(
            TRANSFER, ADJUSTMENT, LENT_RETURNED, BORROWED, REIMBURSEMENT, LOAN_DISBURSEMENT,
        )
}
