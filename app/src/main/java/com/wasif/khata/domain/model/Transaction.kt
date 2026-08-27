package com.wasif.khata.domain.model

import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
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
    val source: TransactionSource,
    val confidence: Confidence,
    val transferGroupId: String?,
    val updatedAt: Long,
) {
    val signedAmount: Money
        get() = if (direction == TransactionDirection.DEBIT) -amount else amount

    val isTransfer: Boolean get() = transferGroupId != null
}
