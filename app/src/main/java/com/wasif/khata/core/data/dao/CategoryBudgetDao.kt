package com.wasif.khata.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wasif.khata.core.data.entity.CategoryBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {

    @Upsert
    suspend fun upsert(row: CategoryBudgetEntity): Long

    // "In force at this instant": started on or before it, and not yet closed or
    // closed after it. The same predicate serves one category and all of them, so the
    // rule cannot drift between the two.
    @Query(
        """
        SELECT * FROM category_budgets
        WHERE deletedAt IS NULL AND effectiveFrom <= :atMillis
          AND (effectiveTo IS NULL OR effectiveTo > :atMillis)
        """,
    )
    suspend fun activeAt(atMillis: Long): List<CategoryBudgetEntity>

    @Query(
        """
        SELECT * FROM category_budgets
        WHERE deletedAt IS NULL AND effectiveFrom <= :atMillis
          AND (effectiveTo IS NULL OR effectiveTo > :atMillis)
        """,
    )
    fun observeActiveAt(atMillis: Long): Flow<List<CategoryBudgetEntity>>

    @Query(
        """
        SELECT * FROM category_budgets
        WHERE deletedAt IS NULL AND categoryId = :categoryId AND effectiveFrom <= :atMillis
          AND (effectiveTo IS NULL OR effectiveTo > :atMillis)
        LIMIT 1
        """,
    )
    suspend fun rowFor(categoryId: Long, atMillis: Long): CategoryBudgetEntity?

    @Query("SELECT * FROM category_budgets WHERE categoryId = :categoryId ORDER BY effectiveFrom")
    suspend fun allFor(categoryId: Long): List<CategoryBudgetEntity>
}
