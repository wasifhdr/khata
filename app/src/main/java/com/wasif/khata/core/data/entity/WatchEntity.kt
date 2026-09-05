package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One sitting. A rewatch four years later is a new watch rather than an edit, which is
 * what makes "did I like it less the second time" answerable.
 */
@Entity(
    tableName = "watches",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["titleId"]),
        Index(value = ["watchedAt"]),
    ],
)
data class WatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val titleId: Long,
    /** UTC epoch millis, displayed in Asia/Dhaka. */
    val watchedAt: Long,
    /** 1-5, nullable. A watch you did not rate is still a watch. */
    val rating: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
