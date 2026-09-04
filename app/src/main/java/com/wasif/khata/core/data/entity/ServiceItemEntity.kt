package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Free text, no catalogue. "What have brake pads cost me" is a GROUP BY. */
@Entity(
    tableName = "service_items",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["serviceId"])],
)
data class ServiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val serviceId: Long,
    val name: String,
    val costMinor: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
