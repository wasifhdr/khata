package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One category's spending limit for one period.
 *
 * Versioned rather than editable in place: a single mutable limit would judge every
 * past month against today's number, so tightening groceries in September would
 * retroactively make last March a month you overspent.
 */
@Entity(
    tableName = "category_budgets",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["effectiveFrom"]),
    ],
)
data class CategoryBudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val categoryId: Long,
    val limitMinor: Long,
    /** Dhaka month start, inclusive. */
    val effectiveFrom: Long,
    /** Dhaka month start, exclusive. Null means still in force. */
    val effectiveTo: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
