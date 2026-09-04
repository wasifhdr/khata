package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.CostTotals
import com.wasif.khata.core.data.dao.ItemSpend
import com.wasif.khata.core.data.dao.ServiceSummary
import com.wasif.khata.core.data.dao.VehicleDao
import com.wasif.khata.core.data.dao.YearSpend
import com.wasif.khata.core.data.entity.ServiceEntity
import com.wasif.khata.core.data.entity.ServiceItemEntity
import com.wasif.khata.core.data.entity.VehicleEntity
import com.wasif.khata.core.search.SearchIndex
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** The one entity type this module links against in the spine's tables. */
const val ENTITY_VEHICLE_SERVICE = "vehicle_service"

/** A service as the editor holds it: no uuid, no timestamps, and an id only when editing. */
data class ServiceDraft(
    val id: Long = 0,
    val vehicleId: Long,
    val servicedAt: Long,
    val odometerKm: Int? = null,
    val placeId: Long? = null,
    val costMinor: Long? = null,
    val note: String? = null,
)

/** Free text and an optional cost. There is no catalogue, so there is nothing else to carry. */
data class ItemDraft(val name: String, val costMinor: Long? = null)

/**
 * Null rather than a number whenever the number would be invented: no total, fewer
 * than two readings, or a span of zero. A made-up cost per km is worse than an absent
 * one, because it reads as an answer.
 */
fun costPerKm(totals: CostTotals): Double? {
    val total = totals.totalMinor ?: return null
    val min = totals.minOdometerKm ?: return null
    val max = totals.maxOdometerKm ?: return null
    val span = max - min
    return if (span <= 0) null else total.toDouble() / span
}

/**
 * The remainder is labour and VAT only when every item carries a cost. With an
 * unpriced item in the list it is partly that item, and showing it then would present
 * an unknown as a known.
 */
fun otherMinor(summary: ServiceSummary): Long? {
    if (summary.itemCount == 0 || summary.unpricedItemCount > 0) return null
    val cost = summary.costMinor ?: return null
    val items = summary.itemCostTotal ?: return null
    return (cost - items).takeIf { it > 0 }
}

@Singleton
class VehicleRepository @Inject constructor(
    private val dao: VehicleDao,
    private val searchIndex: SearchIndex,
    private val clock: KhataClock,
) {
    /**
     * Lazy rather than seeded: a fresh install builds its schema from the entities and
     * runs no migration at all, so seeding in SQL would mean the same row written in
     * two places and eventually in only one.
     */
    suspend fun findOrCreateVehicle(): Long {
        dao.firstVehicle()?.let { return it.id }
        val now = clock.now()
        return dao.upsert(
            VehicleEntity(
                uuid = UUID.randomUUID().toString(),
                name = "Car",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    fun observeVehicle(): Flow<VehicleEntity?> = dao.observeVehicle()

    suspend fun saveVehicle(vehicle: VehicleEntity) {
        dao.upsert(vehicle.copy(updatedAt = clock.now()))
    }

    fun observeServices(vehicleId: Long): Flow<List<ServiceSummary>> = dao.observeServices(vehicleId)

    fun observeService(id: Long): Flow<ServiceSummary?> = dao.serviceSummary(id)

    fun observeItems(serviceId: Long): Flow<List<ServiceItemEntity>> = dao.observeItemsFor(serviceId)

    fun costTotals(vehicleId: Long): Flow<CostTotals> = dao.costTotals(vehicleId)

    fun spendByItem(vehicleId: Long): Flow<List<ItemSpend>> = dao.spendByItem(vehicleId)

    fun spendByYear(vehicleId: Long): Flow<List<YearSpend>> = dao.spendByYear(vehicleId)

    suspend fun findService(id: Long): ServiceEntity? = dao.findService(id)

    suspend fun items(serviceId: Long): List<ServiceItemEntity> = dao.itemsFor(serviceId)

    suspend fun summariesByIds(ids: List<Long>): List<ServiceSummary> = dao.summariesByIds(ids)

    /**
     * The service and its items in one call, because they are one thing the user
     * filled in.
     *
     * Items are rewritten rather than merged: the editor holds the whole list, so
     * saving it twice must leave the list it holds rather than two copies of it --
     * the same move RestaurantRepository.saveVisit makes with dishes. Blank names are
     * dropped rather than stored, since an empty row is what an abandoned one looks
     * like.
     */
    suspend fun saveService(service: ServiceDraft, items: List<ItemDraft>): Long {
        val now = clock.now()
        val existing = service.id.takeIf { it > 0 }?.let { dao.findService(it) }
        val inserted = dao.upsertService(
            ServiceEntity(
                id = existing?.id ?: 0,
                uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                vehicleId = service.vehicleId,
                servicedAt = service.servicedAt,
                odometerKm = service.odometerKm,
                placeId = service.placeId,
                costMinor = service.costMinor,
                note = service.note?.takeIf { it.isNotBlank() },
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        // @Upsert answers -1 when it updated rather than inserted, so an edit would
        // otherwise rewrite its items against service -1 and leave the ones actually
        // on screen untouched. The same trap RestaurantRepository.saveVisit documents.
        val id = if (inserted == -1L) existing!!.id else inserted

        dao.softDeleteItemsFor(id, now)
        items.filter { it.name.isNotBlank() }.forEachIndexed { index, item ->
            dao.upsertItem(
                ServiceItemEntity(
                    uuid = UUID.randomUUID().toString(),
                    serviceId = id,
                    name = item.name.trim(),
                    costMinor = item.costMinor,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        searchIndex.reindex(ENTITY_VEHICLE_SERVICE, id)
        return id
    }

    suspend fun deleteService(id: Long) {
        val now = clock.now()
        dao.softDeleteItemsFor(id, now)
        dao.softDeleteService(id, now)
        searchIndex.remove(ENTITY_VEHICLE_SERVICE, id)
    }
}
