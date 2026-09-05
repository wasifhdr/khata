package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.ServiceEntity
import com.wasif.khata.core.data.entity.ServiceItemEntity
import com.wasif.khata.core.data.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * One service with everything the list shows, computed rather than stored: the
 * workshop's name, the item names on one line, and the sum of whatever items carry a
 * cost. Nothing here can drift out of step with the rows underneath it.
 */
data class ServiceSummary(
    val id: Long,
    val servicedAt: Long,
    val odometerKm: Int?,
    val costMinor: Long?,
    val note: String?,
    val placeId: Long?,
    val placeName: String?,
    val mapsUrl: String?,
    /** Comma-joined item names, for the quiet line under each row. Null when none. */
    val itemNames: String?,
    val itemCount: Int,
    /** Sum across items that carry a cost. */
    val itemCostTotal: Long?,
    /** Items carrying no cost. Zero is what lets the "other" line show at all. */
    val unpricedItemCount: Int,
)

/** Totals for the header. All derived, none stored. */
data class CostTotals(
    val totalMinor: Long?,
    val serviceCount: Int,
    val minOdometerKm: Int?,
    val maxOdometerKm: Int?,
)

data class ItemSpend(val name: String, val totalMinor: Long, val occurrences: Int)

data class YearSpend(val year: String, val totalMinor: Long)

private const val SUMMARY_COLUMNS = """
    SELECT s.id, s.servicedAt, s.odometerKm, s.costMinor, s.note, s.placeId,
      (SELECT p.name FROM places p WHERE p.id = s.placeId) AS placeName,
      (SELECT p.mapsUrl FROM places p WHERE p.id = s.placeId) AS mapsUrl,
      (SELECT GROUP_CONCAT(i.name, ', ') FROM service_items i
       WHERE i.serviceId = s.id AND i.deletedAt IS NULL) AS itemNames,
      (SELECT COUNT(*) FROM service_items i
       WHERE i.serviceId = s.id AND i.deletedAt IS NULL) AS itemCount,
      (SELECT SUM(i.costMinor) FROM service_items i
       WHERE i.serviceId = s.id AND i.deletedAt IS NULL) AS itemCostTotal,
      (SELECT COUNT(*) FROM service_items i
       WHERE i.serviceId = s.id AND i.deletedAt IS NULL AND i.costMinor IS NULL)
        AS unpricedItemCount
    FROM services s
"""

/** The three the hub tile shows. The car is the first live vehicle, since there is only ever one. */
data class VehicleHubStats(
    val carName: String?,
    val lastServicedAt: Long?,
    val spentMinor: Long?,
)

@Dao
interface VehicleDao {

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity): Long

    @Upsert
    suspend fun upsertService(service: ServiceEntity): Long

    @Upsert
    suspend fun upsertItem(item: ServiceItemEntity): Long

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL ORDER BY id LIMIT 1")
    suspend fun firstVehicle(): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL ORDER BY id LIMIT 1")
    fun observeVehicle(): Flow<VehicleEntity?>

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun findService(id: Long): ServiceEntity?

    @Query(
        SUMMARY_COLUMNS + """
        WHERE s.vehicleId = :vehicleId AND s.deletedAt IS NULL
        ORDER BY s.servicedAt DESC, s.id DESC
        """,
    )
    fun observeServices(vehicleId: Long): Flow<List<ServiceSummary>>

    @Query(SUMMARY_COLUMNS + " WHERE s.id = :id AND s.deletedAt IS NULL")
    fun serviceSummary(id: Long): Flow<ServiceSummary?>

    /** Search results: most recent job first, as everywhere -- FTS4 has no ranking. */
    @Query(
        SUMMARY_COLUMNS + """
        WHERE s.id IN (:ids) AND s.deletedAt IS NULL
        ORDER BY s.servicedAt DESC, s.id DESC
        """,
    )
    suspend fun summariesByIds(ids: List<Long>): List<ServiceSummary>

    @Query(
        "SELECT * FROM service_items WHERE serviceId = :serviceId AND deletedAt IS NULL " +
            "ORDER BY sortOrder, id",
    )
    suspend fun itemsFor(serviceId: Long): List<ServiceItemEntity>

    @Query(
        "SELECT * FROM service_items WHERE serviceId = :serviceId AND deletedAt IS NULL " +
            "ORDER BY sortOrder, id",
    )
    fun observeItemsFor(serviceId: Long): Flow<List<ServiceItemEntity>>

    /**
     * The odometer span comes back as two numbers rather than one difference: cost per
     * km is null on fewer than two readings and on a zero span, and that is a decision
     * about meaning rather than arithmetic. It belongs in Kotlin, where it is tested.
     */
    @Query(
        """
        SELECT SUM(costMinor) AS totalMinor,
               COUNT(*) AS serviceCount,
               MIN(odometerKm) AS minOdometerKm,
               MAX(odometerKm) AS maxOdometerKm
        FROM services WHERE vehicleId = :vehicleId AND deletedAt IS NULL
        """,
    )
    fun costTotals(vehicleId: Long): Flow<CostTotals>

    /**
     * COLLATE NOCASE so "Oil Filter" and "oil filter" are one answer rather than two
     * halves of one. MIN(name) picks a stable spelling to show it under.
     */
    @Query(
        """
        SELECT MIN(i.name) AS name, SUM(i.costMinor) AS totalMinor, COUNT(*) AS occurrences
        FROM service_items i
        JOIN services s ON s.id = i.serviceId
        WHERE s.vehicleId = :vehicleId AND s.deletedAt IS NULL AND i.deletedAt IS NULL
          AND i.costMinor IS NOT NULL
        GROUP BY i.name COLLATE NOCASE
        ORDER BY totalMinor DESC
        """,
    )
    fun spendByItem(vehicleId: Long): Flow<List<ItemSpend>>

    /**
     * Grouped in Asia/Dhaka, not UTC. '+6 hours' is the whole of the offset: Dhaka has
     * no DST, so this is a constant rather than a zone table.
     */
    @Query(
        """
        SELECT strftime('%Y', datetime(servicedAt / 1000, 'unixepoch', '+6 hours')) AS year,
               SUM(costMinor) AS totalMinor
        FROM services
        WHERE vehicleId = :vehicleId AND deletedAt IS NULL AND costMinor IS NOT NULL
        GROUP BY year ORDER BY year DESC
        """,
    )
    fun spendByYear(vehicleId: Long): Flow<List<YearSpend>>

    @Query("SELECT id FROM services WHERE deletedAt IS NULL")
    suspend fun allServiceIdsForIndex(): List<Long>

    @Query("UPDATE services SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteService(id: Long, now: Long)

    @Query("UPDATE service_items SET deletedAt = :now, updatedAt = :now WHERE serviceId = :serviceId")
    suspend fun softDeleteItemsFor(serviceId: Long, now: Long)

    @Query(
        """
        SELECT
          (SELECT name FROM vehicles WHERE deletedAt IS NULL ORDER BY id LIMIT 1) AS carName,
          (SELECT MAX(servicedAt) FROM services WHERE deletedAt IS NULL) AS lastServicedAt,
          (SELECT SUM(costMinor) FROM services WHERE deletedAt IS NULL) AS spentMinor
        """,
    )
    fun hubStats(): Flow<VehicleHubStats>
}
