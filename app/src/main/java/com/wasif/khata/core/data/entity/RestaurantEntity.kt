package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "restaurants", indices = [Index(value = ["uuid"], unique = true)])
data class RestaurantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    /**
     * Matched case-insensitively by findByName, exactly as tags are: without it
     * "Sultans Dine" and "sultans dine" become two restaurants and the autocomplete
     * starts suggesting duplicates of itself.
     */
    val name: String,
    val placeId: Long? = null,
    /** A pointer into media, never a second copy of the file. */
    val coverMediaId: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
