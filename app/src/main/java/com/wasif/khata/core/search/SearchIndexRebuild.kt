package com.wasif.khata.core.search

import com.wasif.khata.core.prefs.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Rebuilds search_fts through the IndexSources, once per index shape.
 *
 * The 7->8 migration backfilled the index in SQL, where it could reach merchantRaw,
 * note and counterparty but not a resolved merchant's name or its aliases -- those
 * live in other tables and the SQL would have needed the joins the sources already
 * know how to do. So a row raw-texted FP*8823 and resolved to Foodpanda stayed
 * unfindable by "foodpanda", which is the case the index was built for.
 *
 * Running it here rather than in the migration is what makes the difference: an
 * IndexSource is Kotlin and sees everything the ledger does, and every module that
 * joins the index later is rebuilt by the same pass for free.
 */
@Singleton
class SearchIndexRebuild @Inject constructor(
    private val preferences: PreferencesRepository,
    private val index: SearchIndex,
) {
    suspend fun runIfNeeded() {
        if (preferences.preferences.first().searchIndexVersion >= VERSION) return
        index.reindexAll()
        // Only after the rebuild finishes. Recording it first would leave a half
        // index looking complete if the process died partway.
        preferences.setSearchIndexVersion(VERSION)
    }

    companion object {
        /**
         * Bump when the text an IndexSource produces changes shape, or when a new
         * source arrives with rows already in the database behind it -- version 2
         * is PlaceIndexSource, whose table the spine had already been filling.
         */
        const val VERSION = 2
    }
}
