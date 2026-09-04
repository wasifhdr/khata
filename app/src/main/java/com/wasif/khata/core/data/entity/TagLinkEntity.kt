package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tag_links",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["tagId"]),
        Index(value = ["entityType", "entityId"]),
        Index(value = ["tagId", "entityType", "entityId"], unique = true),
    ],
)
data class TagLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val tagId: Long,
    val entityType: String,
    val entityId: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
