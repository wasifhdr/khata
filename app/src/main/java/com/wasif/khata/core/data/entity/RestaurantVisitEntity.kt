package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "restaurant_visits",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["restaurantId"]),
        Index(value = ["visitedAt"]),
    ],
)
data class RestaurantVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val restaurantId: Long,
    val visitedAt: Long,
    /** 1..5, or null. A visit you did not rate is still a visit. */
    val ambianceRating: Int? = null,
    /** Paisa, and owed nothing to the wallet: a meal someone else paid for has a cost. */
    val costMinor: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
