package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.TitleEntity
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.data.entity.WatchEntity
import kotlinx.coroutines.flow.Flow

/**
 * A title with everything the two lists show, computed rather than stored: how many
 * times, how long ago, and the verdict. Nothing here can drift out of step with the
 * watches underneath it.
 */
data class TitleSummary(
    val id: Long,
    val name: String,
    val year: Int?,
    val kind: TitleKind,
    val tmdbId: Int?,
    val tmdbRating: Double?,
    val tmdbRatingAt: Long?,
    val posterMediaId: Long?,
    /** The poster's content hash, which is its file name. Joined here so a grid of
     *  twenty titles is one query rather than twenty-one. */
    val posterSha: String?,
    val note: String?,
    /** Null on the queue, which is what "not watched" means. */
    val lastWatchedAt: Long?,
    val watchCount: Int,
    /** Averaged over rated watches only: an unrated watch is absent, never a zero. */
    val verdict: Double?,
)

private const val SUMMARY_COLUMNS = """
    SELECT t.id, t.name, t.year, t.kind, t.tmdbId, t.tmdbRating, t.tmdbRatingAt,
      t.posterMediaId,
      (SELECT m.sha256 FROM media m WHERE m.id = t.posterMediaId AND m.deletedAt IS NULL)
        AS posterSha,
      t.note,
      (SELECT MAX(w.watchedAt) FROM watches w
       WHERE w.titleId = t.id AND w.deletedAt IS NULL) AS lastWatchedAt,
      (SELECT COUNT(*) FROM watches w
       WHERE w.titleId = t.id AND w.deletedAt IS NULL) AS watchCount,
      (SELECT AVG(w.rating) FROM watches w
       WHERE w.titleId = t.id AND w.deletedAt IS NULL AND w.rating IS NOT NULL) AS verdict
    FROM titles t
"""

@Dao
interface WatchlistDao {

    @Upsert
    suspend fun upsert(title: TitleEntity): Long

    @Upsert
    suspend fun upsertWatch(watch: WatchEntity): Long

    @Query("SELECT * FROM titles WHERE id = :id")
    suspend fun findTitle(id: Long): TitleEntity?

    @Query("SELECT * FROM titles WHERE tmdbId = :tmdbId AND deletedAt IS NULL LIMIT 1")
    suspend fun findByTmdbId(tmdbId: Int): TitleEntity?

    /** NOCASE for the reason tags and restaurants have it: one title, one row. */
    @Query("SELECT * FROM titles WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): TitleEntity?

    /**
     * Up next: no live watch. Derived rather than flagged, so logging the first watch
     * promotes it with nothing to flip and nothing to drift. Newest addition first --
     * the queue is a stack of intentions, and the freshest one is the live one.
     */
    @Query(
        SUMMARY_COLUMNS + """
        WHERE t.deletedAt IS NULL
          AND NOT EXISTS (SELECT 1 FROM watches w
                          WHERE w.titleId = t.id AND w.deletedAt IS NULL)
        ORDER BY t.createdAt DESC, t.id DESC
        """,
    )
    fun observeQueue(): Flow<List<TitleSummary>>

    /** The same query with the EXISTS un-negated. Soft-deleting the only watch moves
     *  a title back to the queue for free. */
    @Query(
        SUMMARY_COLUMNS + """
        WHERE t.deletedAt IS NULL
          AND EXISTS (SELECT 1 FROM watches w
                      WHERE w.titleId = t.id AND w.deletedAt IS NULL)
        ORDER BY lastWatchedAt DESC
        """,
    )
    fun observeWatched(): Flow<List<TitleSummary>>

    @Query(SUMMARY_COLUMNS + " WHERE t.id = :id")
    fun observeSummary(id: Long): Flow<TitleSummary?>

    @Query(
        SUMMARY_COLUMNS + """
        WHERE t.id IN (:ids) AND t.deletedAt IS NULL
        ORDER BY lastWatchedAt DESC, t.name COLLATE NOCASE
        """,
    )
    suspend fun summariesByIds(ids: List<Long>): List<TitleSummary>

    @Query("SELECT * FROM watches WHERE titleId = :titleId AND deletedAt IS NULL ORDER BY watchedAt DESC")
    fun observeWatches(titleId: Long): Flow<List<WatchEntity>>

    @Query("SELECT id FROM titles WHERE deletedAt IS NULL")
    suspend fun allIdsForIndex(): List<Long>

    @Query("UPDATE titles SET tmdbRating = :rating, tmdbRatingAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun updateTmdbRating(id: Long, rating: Double, at: Long)

    @Query("UPDATE titles SET posterMediaId = :mediaId, updatedAt = :now WHERE id = :id")
    suspend fun updatePoster(id: Long, mediaId: Long, now: Long)

    @Query("UPDATE watches SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteWatch(id: Long, now: Long)

    @Query("UPDATE titles SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteTitle(id: Long, now: Long)
}
