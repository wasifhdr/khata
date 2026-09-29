package com.wasif.khata.domain.model

import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource

data class Transaction(
    val id: Long,
    val uuid: String,
    val accountId: Long,
    val amount: Money,
    val direction: TransactionDirection,
    val occurredAt: Long,
    val merchantRaw: String?,
    val merchantId: Long?,
    val categoryId: Long?,
    val note: String?,
    val counterparty: String? = null,
    val owed: Money = Money.ZERO,
    val kind: TransactionKind = TransactionKind.NORMAL,
    val source: TransactionSource,
    val confidence: Confidence,
    val transferGroupId: String?,
    /** The SMS this was parsed from, so the editor can show it. Null when typed by hand. */
    val rawMessageId: Long? = null,
    val updatedAt: Long,
)
