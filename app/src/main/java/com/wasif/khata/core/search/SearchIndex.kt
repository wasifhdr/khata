package com.wasif.khata.core.search

import com.wasif.khata.core.data.dao.SearchDao
import com.wasif.khata.core.data.entity.SearchFtsEntity
import javax.inject.Inject
import javax.inject.Singleton

/** FTS operators, and the quote that turns a typo into a SQL error. */
private val OPERATORS = Regex("""["'*^():]|\b(AND|OR|NOT|NEAR)\b""")

/**
 * Null when there is nothing to search for. Callers treat that as "no query" rather
 * than as "match nothing", because an empty box should show the whole ledger.
 */
fun ftsQuery(raw: String): String? {
    val terms = raw.replace(OPERATORS, " ").split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return null
    // Trailing * on the last term only: the user is still typing that one.
    return terms.joinToString(" ") + "*"
}

/**
 * What a module contributes to the index. One @IntoSet binding and a module is
 * searchable, including full rebuilds -- which is the "module two is UI work" promise
 * made checkable rather than asserted.
 */
interface IndexSource {
    val entityType: String

    /** Null when the row is deleted or has nothing worth indexing. */
    suspend fun textFor(entityId: Long): String?

    suspend fun allIds(): List<Long>
}

@Singleton
class SearchIndex @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards IndexSource>,
    private val dao: SearchDao,
) {
    /**
     * Delete-then-insert rather than update: FTS4 has no upsert, and one row per
     * entity is the invariant the join depends on.
     *
     * This is also how tags fold in. Tag names are part of an entity's text, so
     * attaching one calls reindex on the entity it was attached to and the source
     * rebuilds that row -- no separate tag-search path, and no join at query time.
     */
    suspend fun reindex(entityType: String, entityId: Long) {
        dao.delete(entityType, entityId)
        val text = sources.firstOrNull { it.entityType == entityType }
            ?.textFor(entityId)
            ?: return
        dao.insert(SearchFtsEntity(entityType = entityType, entityId = entityId, text = text))
    }

    suspend fun remove(entityType: String, entityId: Long) = dao.delete(entityType, entityId)

    suspend fun reindexAll() {
        sources.forEach { source ->
            dao.deleteAll(source.entityType)
            source.allIds().forEach { id ->
                source.textFor(id)?.let {
                    dao.insert(
                        SearchFtsEntity(entityType = source.entityType, entityId = id, text = it),
                    )
                }
            }
        }
    }
}
