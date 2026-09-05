package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "visit_dishes",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["visitId"]),
    ],
)
data class VisitDishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val visitId: Long,
    /** Free text, no catalogue: maintaining a menu is friction on the quickest screen. */
    val name: String,
    val rating: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
