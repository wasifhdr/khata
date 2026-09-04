package com.wasif.khata.core.search

import com.wasif.khata.core.data.dao.MerchantDao
import com.wasif.khata.core.data.dao.TagDao
import com.wasif.khata.core.data.dao.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The five things a transaction is worth finding by: its raw text, its note, its
 * counterparty, the name it resolved to, and that merchant's other aliases -- plus any
 * tags. Category names are deliberately excluded: searching "Food" and getting four
 * hundred rows is not a hunt.
 */
@Singleton
class TransactionIndexSource @Inject constructor(
    private val transactions: TransactionDao,
    private val merchants: MerchantDao,
    private val tags: TagDao,
) : IndexSource {
    override val entityType = "transaction"

    override suspend fun textFor(entityId: Long): String? {
        val row = transactions.findById(entityId) ?: return null
        val merchant = row.merchantId?.let { merchants.findById(it) }
        val aliases = row.merchantId?.let { merchants.aliasesFor(it) }.orEmpty()
        val tagNames = tags.tagsFor(entityType, entityId).map { it.name }
        return (
            listOf(row.merchantRaw, row.note, row.counterparty, merchant?.canonicalName) +
                aliases + tagNames
            ).filterNot { it.isNullOrBlank() }.joinToString(" ")
    }

    override suspend fun allIds(): List<Long> = transactions.allIdsForIndex()
}
