package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wasif.khata.core.model.RawMessageStatus

@Entity(
    tableName = "raw_messages",
    indices = [
        Index(value = ["uuid"], unique = true),
        // Makes backfill idempotent: re-scanning the inbox inserts nothing new.
        Index(value = ["sender", "bodyHash", "receivedAt"], unique = true),
        Index(value = ["status"]),
    ],
)
data class RawMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val bodyHash: String,
    val status: RawMessageStatus,
    val matchedRuleId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
