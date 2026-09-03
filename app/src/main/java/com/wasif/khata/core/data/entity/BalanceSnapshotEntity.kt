package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One account's closing balance for one Dhaka day.
 *
 * A row records what the balance was understood to be that night and is never
 * rewritten -- editing a transaction from last March does not silently redraw last
 * March. The ledger says what happened; this says what was believed.
 */
@Entity(
    tableName = "balance_snapshots",
    indices = [
        Index(value = ["uuid"], unique = true),
        // A day has one closing balance per account. The uniqueness is what lets the
        // fill routine be idempotent rather than careful.
        Index(value = ["accountId", "dayIndex"], unique = true),
        Index(value = ["dayIndex"]),
    ],
)
data class BalanceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val accountId: Long,
    /** Dhaka day, as `Long.toDhakaDayIndex()` computes it. */
    val dayIndex: Long,
    val balanceMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
