package com.wasif.khata.core.search

import com.wasif.khata.core.data.dao.PlaceDao
import com.wasif.khata.core.data.dao.RestaurantDao
import com.wasif.khata.core.data.dao.TagDao
import com.wasif.khata.core.data.repository.ENTITY_RESTAURANT
import com.wasif.khata.core.data.repository.ENTITY_RESTAURANT_VISIT
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One indexed row per restaurant, never one per visit: "kacchi" should land you on
 * the place rather than on an evening, and "Rafi" should find everywhere you went
 * with him rather than a list of dinners.
 *
 * That is why every dish and every companion across every visit is folded up into
 * the restaurant's own text here. A visit added later reaches the index through
 * RestaurantRepository.saveVisit, which reindexes the restaurant rather than the
 * visit -- the same move TagRepository.attach makes, for the same reason.
 */
@Singleton
class RestaurantIndexSource @Inject constructor(
    private val restaurants: RestaurantDao,
    private val places: PlaceDao,
    private val tags: TagDao,
) : IndexSource {
    override val entityType = ENTITY_RESTAURANT

    override suspend fun textFor(entityId: Long): String? {
        val row = restaurants.findById(entityId)?.takeIf { it.deletedAt == null } ?: return null

        val place = row.placeId?.let { places.findById(it) }
        val dishes = restaurants.dishesForRestaurant(entityId).map { it.name }
        val companions = restaurants.visitsFor(entityId)
            .flatMap { tags.tagsFor(ENTITY_RESTAURANT_VISIT, it.id) }
            .map { it.name }
        // A wishlist entry's recommender hangs off the restaurant itself: there is no
        // visit for it to hang off yet.
        val recommenders = tags.tagsFor(ENTITY_RESTAURANT, entityId).map { it.name }
        val visitNotes = restaurants.visitsFor(entityId).mapNotNull { it.note }

        return (
            listOf(row.name, row.note, place?.name, place?.address) +
                dishes + companions + recommenders + visitNotes
            ).filterNot { it.isNullOrBlank() }.joinToString(" ")
    }

    override suspend fun allIds(): List<Long> = restaurants.allIdsForIndex()
}
