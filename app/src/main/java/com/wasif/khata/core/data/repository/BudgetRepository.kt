package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.CategoryBudgetDao
import com.wasif.khata.core.data.entity.CategoryBudgetEntity
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Which limit applied to a month, and how a change is recorded.
 *
 * One place, because the rule is easy to get quietly wrong: a reader that decided for
 * itself which of two overlapping rows applied could decide differently from the
 * writer, and both answers would look reasonable.
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val budgets: CategoryBudgetDao,
    private val clock: KhataClock,
) {
    /**
     * Sets this month's limit, or clears it when [limitMinor] is null.
     *
     * A row is keyed to the month it starts, so revising within the same month updates
     * that row rather than opening a second one that overlaps it. A change in a later
     * month closes the old row at that month's start, leaving the earlier months
     * reading exactly what they were judged against.
     */
    suspend fun setLimit(categoryId: Long, limitMinor: Long?) {
        val now = clock.now()
        val monthStart = now.dhakaMonthStart()
        val current = budgets.rowFor(categoryId, monthStart)

        if (current != null && current.effectiveFrom == monthStart) {
            // Started this month: this is a revision of the same period, not a new one.
            val revised = if (limitMinor == null) {
                current.copy(deletedAt = now, updatedAt = now)
            } else {
                current.copy(limitMinor = limitMinor, updatedAt = now)
            }
            budgets.upsert(revised)
            return
        }

        // Carried in from an earlier month: close it here rather than edit it, or that
        // earlier month loses the number it was judged against.
        if (current != null) {
            budgets.upsert(current.copy(effectiveTo = monthStart, updatedAt = now))
        }
        if (limitMinor == null) return

        budgets.upsert(
            CategoryBudgetEntity(
                uuid = UUID.randomUUID().toString(),
                categoryId = categoryId,
                limitMinor = limitMinor,
                effectiveFrom = monthStart,
                effectiveTo = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun limitFor(categoryId: Long, monthStart: Long): Long? =
        budgets.rowFor(categoryId, monthStart)?.limitMinor

    fun observeLimitsForMonth(monthStart: Long): Flow<Map<Long, Long>> =
        budgets.observeActiveAt(monthStart).map { rows ->
            rows.associate { it.categoryId to it.limitMinor }
        }
}
