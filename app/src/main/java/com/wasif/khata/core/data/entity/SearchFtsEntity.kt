package com.wasif.khata.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * unicode61, never simple: simple is ASCII-only, so a Bengali merchant name would be
 * silently unfindable. entityType and entityId are filters and join keys, not text
 * anyone searches for, so they are excluded from the index itself.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["entityType", "entityId"])
@Entity(tableName = "search_fts")
data class SearchFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val entityType: String,
    val entityId: Long,
    val text: String,
)
