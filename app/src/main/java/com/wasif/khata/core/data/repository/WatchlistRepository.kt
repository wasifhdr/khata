package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.TitleSummary
import com.wasif.khata.core.data.dao.WatchlistDao
import com.wasif.khata.core.data.entity.TitleEntity
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.data.entity.WatchEntity
import com.wasif.khata.core.search.SearchIndex
import com.wasif.khata.core.tag.TagRepository
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** The one entity type this module links against in the spine's tables. */
const val ENTITY_TITLE = "title"

/** A title as the add screen holds it: no uuid, no timestamps, an id only when editing. */
data class TitleDraft(
    val id: Long = 0,
    val name: String,
    val year: Int? = null,
    val kind: TitleKind,
    val tmdbId: Int? = null,
    val tmdbRating: Double? = null,
    val tmdbRatingAt: Long? = null,
    val posterMediaId: Long? = null,
    val note: String? = null,
)

data class WatchDraft(
    val titleId: Long,
    val watchedAt: Long,
    val rating: Int? = null,
    val note: String? = null,
)

/**
 * Seven days. Refresh-on-open ungated is a network call every time the user glances at
 * a title; a rating that moves by hundredths in a week is not worth one.
 */
const val REFRESH_AFTER_MS = 7L * 24 * 60 * 60 * 1000

fun needsRefresh(tmdbId: Int?, ratingAt: Long?, now: Long): Boolean =
    tmdbId != null && (ratingAt == null || now - ratingAt > REFRESH_AFTER_MS)

@Singleton
class WatchlistRepository @Inject constructor(
    private val dao: WatchlistDao,
    private val tags: TagRepository,
    private val searchIndex: SearchIndex,
    private val clock: KhataClock,
) {
    fun observeQueue(): Flow<List<TitleSummary>> = dao.observeQueue()

    fun observeWatched(): Flow<List<TitleSummary>> = dao.observeWatched()

    fun observeSummary(id: Long): Flow<TitleSummary?> = dao.observeSummary(id)

    fun observeWatches(titleId: Long): Flow<List<WatchEntity>> = dao.observeWatches(titleId)

    suspend fun find(id: Long): TitleEntity? = dao.findTitle(id)

    /**
     * Resolved by tmdbId first and by name second, so the same film added twice is one
     * row -- the findOrCreate shape the tag and restaurant repositories already use.
     * Without the name check, a title typed by hand while offline and later picked from
     * TMDB would become a second row for the same film.
     */
    suspend fun saveTitle(draft: TitleDraft, recommenders: List<String> = emptyList()): Long {
        val now = clock.now()
        val existing = draft.id.takeIf { it > 0 }?.let { dao.findTitle(it) }
            ?: draft.tmdbId?.let { dao.findByTmdbId(it) }
            ?: dao.findByName(draft.name.trim())

        val inserted = dao.upsert(
            TitleEntity(
                id = existing?.id ?: 0,
                uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                name = draft.name.trim(),
                year = draft.year ?: existing?.year,
                kind = draft.kind,
                tmdbId = draft.tmdbId ?: existing?.tmdbId,
                tmdbRating = draft.tmdbRating ?: existing?.tmdbRating,
                tmdbRatingAt = draft.tmdbRatingAt ?: existing?.tmdbRatingAt,
                posterMediaId = draft.posterMediaId ?: existing?.posterMediaId,
                note = draft.note?.takeIf { it.isNotBlank() } ?: existing?.note,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        // @Upsert answers -1 when it updated rather than inserted, so an edit would
        // otherwise attach tags to title -1 and index a row that does not exist.
        val id = if (inserted == -1L) existing!!.id else inserted

        recommenders.filter { it.isNotBlank() }.forEach { name ->
            tags.attach(tags.findOrCreate(name), ENTITY_TITLE, id)
        }

        searchIndex.reindex(ENTITY_TITLE, id)
        return id
    }

    /** Reindexes the title, not the watch: one indexed row per title, as with visits. */
    suspend fun logWatch(watch: WatchDraft): Long {
        val now = clock.now()
        val id = dao.upsertWatch(
            WatchEntity(
                uuid = UUID.randomUUID().toString(),
                titleId = watch.titleId,
                watchedAt = watch.watchedAt,
                rating = watch.rating,
                note = watch.note?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
            ),
        )
        searchIndex.reindex(ENTITY_TITLE, watch.titleId)
        return id
    }

    suspend fun deleteWatch(id: Long, titleId: Long) {
        dao.softDeleteWatch(id, clock.now())
        searchIndex.reindex(ENTITY_TITLE, titleId)
    }

    suspend fun recommendersFor(titleId: Long): List<String> =
        tags.tagsFor(ENTITY_TITLE, titleId).map { it.name }

    /** Left alone on any failure: a stale rating with an honest date beats none. */
    suspend fun refreshRating(id: Long, rating: Double) {
        dao.updateTmdbRating(id, rating, clock.now())
    }

    suspend fun setPoster(id: Long, mediaId: Long) {
        dao.updatePoster(id, mediaId, clock.now())
    }
}
