package com.wasif.khata.core.model

enum class AccountType { MFS, BANK, CARD, CASH, MANUAL_ASSET }

enum class TransactionDirection { DEBIT, CREDIT }

enum class TransactionSource { SMS, NOTIFICATION, WIDGET, MANUAL }

enum class Confidence { HIGH, MEDIUM, LOW }

enum class RawMessageStatus { PENDING, PARSED, UNMATCHED, IGNORED }

enum class RuleKind { NORMAL, TRANSFER_OUT, TRANSFER_IN, ATM_WITHDRAWAL, FEE, LOAN_DISBURSEMENT, IGNORE }

// Schema-level rather than a category: categories are user-editable, and a renamed
// or deleted one must not be able to break net-worth arithmetic.
enum class TransactionKind { NORMAL, TRANSFER, LOAN_DISBURSEMENT, LOAN_REPAYMENT, FEE, ADJUSTMENT }
