package com.wasif.khata.core.search

import com.wasif.khata.core.data.dao.TagDao
import com.wasif.khata.core.data.dao.WatchlistDao
import com.wasif.khata.core.data.repository.ENTITY_TITLE
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Name, note, and recommender tags. Not the year, the kind, or the TMDB rating:
 * numbers and enums are filters, and a search box is not where either is used.
 *
 * The tags are what make "Rafi" find everything he told you to watch -- the same
 * folding TagRepository.attach triggers everywhere else.
 */
@Singleton
class TitleIndexSource @Inject constructor(
    private val titles: WatchlistDao,
    private val tags: TagDao,
) : IndexSource {
    override val entityType = ENTITY_TITLE

    override suspend fun textFor(entityId: Long): String? {
        val row = titles.findTitle(entityId)?.takeIf { it.deletedAt == null } ?: return null
        val recommenders = tags.tagsFor(ENTITY_TITLE, entityId).map { it.name }
        return (listOf(row.name, row.note) + recommenders)
            .filterNot { it.isNullOrBlank() }
            .joinToString(" ")
    }

    override suspend fun allIds(): List<Long> = titles.allIdsForIndex()
}
