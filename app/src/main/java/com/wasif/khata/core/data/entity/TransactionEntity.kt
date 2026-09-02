package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["transferGroupId"]),
        Index(value = ["providerTxnId"], unique = true),
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
    /**
     * The person on the other side, for money that is owed in one direction or the
     * other. Free text rather than a table of people: "how much does Rafi owe me"
     * is a GROUP BY, and a contacts model is a bigger idea than this needs.
     */
    val counterparty: String? = null,
    val source: TransactionSource,
    val confidence: Confidence,
    val rawMessageId: Long?,
    val transferGroupId: String?,
    val feeMinor: Long?,
    val referenceNumber: String?,
    // SQLite treats NULLs as distinct in a unique index, so manual and EBL rows —
    // which have no provider id — coexist freely under the uniqueness constraint.
    val providerTxnId: String? = null,
    val kind: TransactionKind = TransactionKind.NORMAL,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
