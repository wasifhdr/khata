package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.MediaDao
import com.wasif.khata.core.data.dao.RestaurantDao
import com.wasif.khata.core.data.dao.RestaurantSummary
import com.wasif.khata.core.data.entity.MediaEntity
import com.wasif.khata.core.data.entity.RestaurantEntity
import com.wasif.khata.core.data.entity.RestaurantVisitEntity
import com.wasif.khata.core.data.entity.TagEntity
import com.wasif.khata.core.data.entity.VisitDishEntity
import com.wasif.khata.core.search.SearchIndex
import com.wasif.khata.core.tag.TagRepository
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** The two entity types this module links against in the spine's tables. */
const val ENTITY_RESTAURANT = "restaurant"
const val ENTITY_RESTAURANT_VISIT = "restaurant_visit"

/** A visit as the editor holds it: no uuid, no timestamps, and an id only when editing. */
data class VisitDraft(
    val id: Long = 0,
    val restaurantId: Long,
    val visitedAt: Long,
    val ambianceRating: Int? = null,
    val costMinor: Long? = null,
    val note: String? = null,
)

/** Free text and a rating. There is no catalogue, so there is nothing else to carry. */
data class DishDraft(val name: String, val rating: Int? = null)

@Singleton
class RestaurantRepository @Inject constructor(
    private val dao: RestaurantDao,
    private val mediaDao: MediaDao,
    private val tags: TagRepository,
    private val searchIndex: SearchIndex,
    private val clock: KhataClock,
) {
    /**
     * Visit-first entry, and the identical pattern to TagRepository.findOrCreate:
     * being made to register a restaurant before recording dinner is the kind of
     * chore that gets the module abandoned.
     */
    suspend fun findOrCreate(name: String): Long {
        val trimmed = name.trim()
        dao.findByName(trimmed)?.let { return it.id }
        val now = clock.now()
        val id = dao.upsert(
            RestaurantEntity(
                uuid = UUID.randomUUID().toString(),
                name = trimmed,
                createdAt = now,
                updatedAt = now,
            ),
        )
        searchIndex.reindex(ENTITY_RESTAURANT, id)
        return id
    }

    suspend fun find(id: Long): RestaurantEntity? = dao.findById(id)

    fun observeBeen(): Flow<List<RestaurantSummary>> = dao.observeBeen()

    fun observeWishlist(): Flow<List<RestaurantSummary>> = dao.observeWishlist()

    fun observeSummary(id: Long): Flow<RestaurantSummary?> = dao.observeSummary(id)

    fun observeVisits(restaurantId: Long): Flow<List<RestaurantVisitEntity>> =
        dao.observeVisitsFor(restaurantId)

    suspend fun suggestions(prefix: String): List<RestaurantEntity> =
        if (prefix.isBlank()) emptyList() else dao.namesLike(prefix.trim())

    /**
     * The visit, its dishes and its companions in one call, because they are one
     * thing the user filled in.
     *
     * Dishes are rewritten rather than merged: the editor holds the whole list, so
     * saving it twice must leave the list it holds rather than two copies of it.
     * Reindexing targets the restaurant, not the visit -- one indexed row per
     * restaurant is what makes "kacchi" land on the place rather than on an evening.
     */
    suspend fun saveVisit(
        visit: VisitDraft,
        dishes: List<DishDraft>,
        companions: List<String>,
    ): Long {
        val now = clock.now()
        val existing = visit.id.takeIf { it != 0L }?.let { dao.findVisit(it) }
        val inserted = dao.upsertVisit(
            RestaurantVisitEntity(
                id = existing?.id ?: 0,
                uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                restaurantId = visit.restaurantId,
                visitedAt = visit.visitedAt,
                ambianceRating = visit.ambianceRating,
                costMinor = visit.costMinor,
                note = visit.note?.takeIf { it.isNotBlank() },
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        // @Upsert answers -1 when it updated rather than inserted, so an edit would
        // otherwise write its dishes and companions against visit -1 and leave the
        // ones actually on screen untouched.
        val visitId = if (inserted == -1L) existing!!.id else inserted

        dao.clearDishes(visitId, now)
        if (dishes.isNotEmpty()) {
            dao.upsertDishes(
                dishes.filter { it.name.isNotBlank() }.mapIndexed { index, draft ->
                    VisitDishEntity(
                        uuid = UUID.randomUUID().toString(),
                        visitId = visitId,
                        name = draft.name.trim(),
                        rating = draft.rating,
                        sortOrder = index,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }

        companions.filter { it.isNotBlank() }.forEach { name ->
            tags.attach(tags.findOrCreate(name), ENTITY_RESTAURANT_VISIT, visitId)
        }

        searchIndex.reindex(ENTITY_RESTAURANT, visit.restaurantId)
        return visitId
    }

    suspend fun deleteVisit(visitId: Long) {
        val visit = dao.findVisit(visitId) ?: return
        dao.deleteVisit(visitId, clock.now())
        searchIndex.reindex(ENTITY_RESTAURANT, visit.restaurantId)
    }

    /**
     * The overall verdict. Derived from the dishes so it cannot contradict the items
     * it summarises, and null when nothing has been rated.
     */
    suspend fun verdict(restaurantId: Long): Double? = dao.dishAverage(restaurantId)

    /** A pointer at a media row that already exists, never a second copy of the file. */
    suspend fun setCover(restaurantId: Long, mediaId: Long?) {
        val existing = dao.findById(restaurantId) ?: return
        dao.upsert(existing.copy(coverMediaId = mediaId, updatedAt = clock.now()))
    }

    suspend fun setDetails(restaurantId: Long, placeId: Long?, note: String?) {
        val existing = dao.findById(restaurantId) ?: return
        dao.upsert(
            existing.copy(
                placeId = placeId ?: existing.placeId,
                note = note?.takeIf { it.isNotBlank() } ?: existing.note,
                updatedAt = clock.now(),
            ),
        )
        searchIndex.reindex(ENTITY_RESTAURANT, restaurantId)
    }

    suspend fun dishesFor(visitId: Long): List<VisitDishEntity> = dao.dishesFor(visitId)

    suspend fun companionsFor(visitId: Long): List<TagEntity> =
        tags.tagsFor(ENTITY_RESTAURANT_VISIT, visitId)

    suspend fun recommendersFor(restaurantId: Long): List<TagEntity> =
        tags.tagsFor(ENTITY_RESTAURANT, restaurantId)

    suspend fun addRecommender(restaurantId: Long, name: String) {
        tags.attach(tags.findOrCreate(name), ENTITY_RESTAURANT, restaurantId)
    }

    suspend fun photosFor(entityType: String, entityId: Long): List<MediaEntity> =
        mediaDao.mediaFor(entityType, entityId)

    suspend fun media(id: Long): MediaEntity? = mediaDao.findById(id)

    /** Every photo across every visit, which is what the cover may be chosen from. */
    suspend fun photosAcross(restaurantId: Long): List<MediaEntity> =
        (
            mediaDao.mediaFor(ENTITY_RESTAURANT, restaurantId) +
                dao.visitsFor(restaurantId).flatMap {
                    mediaDao.mediaFor(ENTITY_RESTAURANT_VISIT, it.id)
                }
            ).distinctBy { it.id }
}
