package com.wasif.khata.core.search

import com.wasif.khata.core.data.dao.PlaceDao
import com.wasif.khata.core.data.dao.TagDao
import com.wasif.khata.core.data.dao.VehicleDao
import com.wasif.khata.core.data.repository.ENTITY_VEHICLE_SERVICE
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One indexed row per service, never one per item: "brake" should land you on the job
 * rather than on a fragment inside it -- the same call RestaurantIndexSource makes
 * about visits.
 *
 * The workshop's name is folded in here rather than searched through places, so
 * "Navana" finds every service done there and not merely the place itself.
 */
@Singleton
class VehicleServiceIndexSource @Inject constructor(
    private val vehicles: VehicleDao,
    private val places: PlaceDao,
    private val tags: TagDao,
) : IndexSource {
    override val entityType = ENTITY_VEHICLE_SERVICE

    override suspend fun textFor(entityId: Long): String? {
        val row = vehicles.findService(entityId)?.takeIf { it.deletedAt == null } ?: return null

        val place = row.placeId?.let { places.findById(it) }
        val items = vehicles.itemsFor(entityId).map { it.name }
        val tagNames = tags.tagsFor(ENTITY_VEHICLE_SERVICE, entityId).map { it.name }

        return (listOf(row.note, place?.name, place?.address) + items + tagNames)
            .filterNot { it.isNullOrBlank() }
            .joinToString(" ")
    }

    override suspend fun allIds(): List<Long> = vehicles.allServiceIdsForIndex()
}
