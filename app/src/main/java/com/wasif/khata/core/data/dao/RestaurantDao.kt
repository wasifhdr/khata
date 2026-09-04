package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.RestaurantEntity
import com.wasif.khata.core.data.entity.RestaurantVisitEntity
import com.wasif.khata.core.data.entity.VisitDishEntity
import kotlinx.coroutines.flow.Flow

/**
 * A restaurant with everything the two lists show, computed rather than stored: how
 * many times, how long ago, and both averages. Every field here is derived, so nothing
 * can drift out of step with the visits underneath it.
 */
data class RestaurantSummary(
    val id: Long,
    val name: String,
    val placeId: Long?,
    val coverMediaId: Long?,
    val note: String?,
    /** Null on the wishlist, which is what "not been" means. */
    val lastVisitedAt: Long?,
    val visitCount: Int,
    val dishAverage: Double?,
    val ambianceAverage: Double?,
)

private const val SUMMARY_COLUMNS = """
    SELECT r.id, r.name, r.placeId, r.coverMediaId, r.note,
      (SELECT MAX(v.visitedAt) FROM restaurant_visits v
       WHERE v.restaurantId = r.id AND v.deletedAt IS NULL) AS lastVisitedAt,
      (SELECT COUNT(*) FROM restaurant_visits v
       WHERE v.restaurantId = r.id AND v.deletedAt IS NULL) AS visitCount,
      (SELECT AVG(d.rating) FROM visit_dishes d
       JOIN restaurant_visits v ON v.id = d.visitId
       WHERE v.restaurantId = r.id AND d.deletedAt IS NULL AND v.deletedAt IS NULL)
        AS dishAverage,
      (SELECT AVG(v.ambianceRating) FROM restaurant_visits v
       WHERE v.restaurantId = r.id AND v.deletedAt IS NULL) AS ambianceAverage
    FROM restaurants r
"""

@Dao
interface RestaurantDao {

    @Upsert
    suspend fun upsert(restaurant: RestaurantEntity): Long

    @Query("SELECT * FROM restaurants WHERE id = :id")
    suspend fun findById(id: Long): RestaurantEntity?

    /**
     * NOCASE for the same reason TagDao.findByName has it: "Sultans Dine" and
     * "sultans dine" being two restaurants is the failure visit-first entry would
     * otherwise walk straight into.
     */
    @Query("SELECT * FROM restaurants WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): RestaurantEntity?

    /**
     * Been: has at least one live visit. Derived rather than flagged, so logging the
     * first visit promotes it with nothing to flip and nothing to drift.
     */
    @Query(
        SUMMARY_COLUMNS + """
        WHERE r.deletedAt IS NULL
          AND EXISTS (SELECT 1 FROM restaurant_visits v
                      WHERE v.restaurantId = r.id AND v.deletedAt IS NULL)
        ORDER BY lastVisitedAt DESC
        """,
    )
    fun observeBeen(): Flow<List<RestaurantSummary>>

    /**
     * Want to try: the same query with the EXISTS negated. Soft-deleting the only
     * visit moves a restaurant back here for free, which a stored flag would need
     * code for and would eventually be missing.
     */
    @Query(
        SUMMARY_COLUMNS + """
        WHERE r.deletedAt IS NULL
          AND NOT EXISTS (SELECT 1 FROM restaurant_visits v
                          WHERE v.restaurantId = r.id AND v.deletedAt IS NULL)
        ORDER BY r.name COLLATE NOCASE
        """,
    )
    fun observeWishlist(): Flow<List<RestaurantSummary>>

    @Query(SUMMARY_COLUMNS + " WHERE r.id = :id")
    fun observeSummary(id: Long): Flow<RestaurantSummary?>

    /**
     * Autocomplete. A LIKE prefix against a short list, ordered by most recently
     * visited -- not FTS, which would return fuzzier results in a field where the
     * user is trying to hit one specific row.
     */
    @Query(
        """
        SELECT r.* FROM restaurants r
        WHERE r.deletedAt IS NULL AND r.name LIKE :prefix || '%' COLLATE NOCASE
        ORDER BY (SELECT MAX(v.visitedAt) FROM restaurant_visits v
                  WHERE v.restaurantId = r.id AND v.deletedAt IS NULL) DESC,
                 r.name COLLATE NOCASE
        LIMIT 8
        """,
    )
    suspend fun namesLike(prefix: String): List<RestaurantEntity>

    @Query("SELECT id FROM restaurants WHERE deletedAt IS NULL")
    suspend fun allIdsForIndex(): List<Long>

    @Upsert
    suspend fun upsertVisit(visit: RestaurantVisitEntity): Long

    @Query("SELECT * FROM restaurant_visits WHERE id = :id")
    suspend fun findVisit(id: Long): RestaurantVisitEntity?

    @Query(
        "SELECT * FROM restaurant_visits WHERE restaurantId = :restaurantId " +
            "AND deletedAt IS NULL ORDER BY visitedAt DESC",
    )
    suspend fun visitsFor(restaurantId: Long): List<RestaurantVisitEntity>

    @Query(
        "SELECT * FROM restaurant_visits WHERE restaurantId = :restaurantId " +
            "AND deletedAt IS NULL ORDER BY visitedAt DESC",
    )
    fun observeVisitsFor(restaurantId: Long): Flow<List<RestaurantVisitEntity>>

    @Query("UPDATE restaurant_visits SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun deleteVisit(id: Long, now: Long)

    @Upsert
    suspend fun upsertDishes(dishes: List<VisitDishEntity>)

    @Query(
        "SELECT * FROM visit_dishes WHERE visitId = :visitId AND deletedAt IS NULL " +
            "ORDER BY sortOrder, id",
    )
    suspend fun dishesFor(visitId: Long): List<VisitDishEntity>

    /** Every dish the restaurant has ever served you, for the search row and its verdict. */
    @Query(
        """
        SELECT d.* FROM visit_dishes d
        JOIN restaurant_visits v ON v.id = d.visitId
        WHERE v.restaurantId = :restaurantId AND d.deletedAt IS NULL AND v.deletedAt IS NULL
        ORDER BY v.visitedAt DESC, d.sortOrder, d.id
        """,
    )
    suspend fun dishesForRestaurant(restaurantId: Long): List<VisitDishEntity>

    /**
     * AVG ignores NULL, which is the behaviour wanted: an unrated dish is unrated,
     * not zero out of five.
     */
    @Query(
        """
        SELECT AVG(d.rating) FROM visit_dishes d
        JOIN restaurant_visits v ON v.id = d.visitId
        WHERE v.restaurantId = :restaurantId AND d.deletedAt IS NULL AND v.deletedAt IS NULL
        """,
    )
    suspend fun dishAverage(restaurantId: Long): Double?
}
