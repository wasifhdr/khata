package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["transferGroupId"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val accountId: Long,
    val amountMinor: Long,
    val direction: TransactionDirection,
    val occurredAt: Long,
    val merchantRaw: String?,
    val merchantId: Long?,
    val categoryId: Long?,
    val note: String?,
    val source: TransactionSource,
    val confidence: Confidence,
    val rawMessageId: Long?,
    val transferGroupId: String?,
    val feeMinor: Long?,
    val referenceNumber: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
