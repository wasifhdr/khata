package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row, in practice. The table exists so services have a real parent to hang off:
 * a second car is plausible, and a migration that has to invent a parent for orphaned
 * services later is not. The UI shows no picker.
 */
@Entity(tableName = "vehicles", indices = [Index(value = ["uuid"], unique = true)])
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val registration: String? = null,
    /** The dash reading as of the last time it was entered. Nullable: unknown is not zero. */
    val odometerKm: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
