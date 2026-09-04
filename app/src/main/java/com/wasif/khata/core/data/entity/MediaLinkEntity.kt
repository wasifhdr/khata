package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_links",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["mediaId"]),
        Index(value = ["entityType", "entityId"]),
    ],
)
data class MediaLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val mediaId: Long,
    val entityType: String,
    val entityId: Long,
    /** Ordering by id breaks the first time a photo is removed and re-added. */
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
