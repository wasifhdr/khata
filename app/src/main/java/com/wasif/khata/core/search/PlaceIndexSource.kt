package com.wasif.khata.core.search

import com.wasif.khata.core.data.dao.PlaceDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Places are the third kind of thing the search screen groups by, so they need a row
 * in the same index rather than a LIKE query beside it -- the tokenizer is what makes
 * a Bengali name and a two-word query work at all, and there is no reason for one
 * kind of result to be worse at both.
 */
@Singleton
class PlaceIndexSource @Inject constructor(
    private val places: PlaceDao,
) : IndexSource {
    override val entityType = "place"

    override suspend fun textFor(entityId: Long): String? {
        val row = places.findById(entityId)?.takeIf { it.deletedAt == null } ?: return null
        return listOf(row.name, row.address, row.note)
            .filterNot { it.isNullOrBlank() }
            .joinToString(" ")
    }

    override suspend fun allIds(): List<Long> = places.allIdsForIndex()
}
