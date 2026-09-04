package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "services",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["vehicleId"]),
        Index(value = ["servicedAt"]),
    ],
)
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val vehicleId: Long,
    /** UTC epoch millis. Bucketed for display in Asia/Dhaka, never UTC. */
    val servicedAt: Long,
    /**
     * Nullable: a job where the dash went unread is still a job, and every derived
     * figure needing it skips this row rather than reading it as zero.
     */
    val odometerKm: Int? = null,
    /** The workshop, as a spine place. */
    val placeId: Long? = null,
    /** The bill. Item costs are optional detail beneath it, never its source. */
    val costMinor: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
