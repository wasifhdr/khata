package com.wasif.khata.core.model

enum class AccountType { MFS, BANK, CARD, CASH, MANUAL_ASSET }

enum class TransactionDirection { DEBIT, CREDIT }

enum class TransactionSource { SMS, NOTIFICATION, WIDGET, MANUAL }

enum class Confidence { HIGH, MEDIUM, LOW }
