package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categories: CategoryDao,
    private val budgets: BudgetRepository,
    private val clock: KhataClock,
) {
    suspend fun add(name: String, colorToken: String): Long {
        val now = clock.now()
        return categories.upsert(
            CategoryEntity(
                uuid = UUID.randomUUID().toString(),
                name = name.trim(),
                // The entity carries an icon and no screen renders one, so a new
                // category gets the same placeholder the seed uses rather than an
                // empty string that would look like a missing value later.
                icon = "help_outline",
                colorToken = colorToken,
                parentId = null,
                isSystem = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun rename(id: Long, name: String) {
        val row = categories.findById(id) ?: return
        categories.upsert(row.copy(name = name.trim(), updatedAt = clock.now()))
    }

    suspend fun recolour(id: Long, colorToken: String) {
        val row = categories.findById(id) ?: return
        categories.upsert(row.copy(colorToken = colorToken, updatedAt = clock.now()))
    }

    /**
     * Soft-deletes, and reports whether it did. False means the category is the one
     * that cannot go: Uncategorised is found by uuid in BudgetCarryOver and is the
     * fallback label for a transaction with no category at all.
     *
     * Its budget is closed at the same time. Left open it would keep adding to the
     * hub ring's total for a category no longer on offer.
     */
    suspend fun delete(id: Long): Boolean {
        val row = categories.findById(id) ?: return false
        if (row.isSystem) return false

        budgets.setLimit(id, null)
        categories.softDelete(id, clock.now())
        return true
    }
}
