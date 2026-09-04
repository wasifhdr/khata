package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "places", indices = [Index(value = ["uuid"], unique = true)])
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val address: String? = null,
    /** Null is normal: a short link that would not resolve offline still stores its URL. */
    val lat: Double? = null,
    val lng: Double? = null,
    val mapsUrl: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
