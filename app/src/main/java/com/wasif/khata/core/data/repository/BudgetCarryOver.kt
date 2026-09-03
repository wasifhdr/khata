package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.prefs.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Carries the retired global monthly budget into a category row, once.
 *
 * Without this, upgrading silently discards a number the user set and the hub ring
 * disappears with no explanation. Clearing the preference afterwards is what makes it
 * run at most once.
 */
@Singleton
class BudgetCarryOver @Inject constructor(
    private val preferences: PreferencesRepository,
    private val budgets: BudgetRepository,
    private val categories: CategoryDao,
) {
    suspend fun runIfNeeded() {
        val legacy = preferences.preferences.first().monthlyBudgetMinor ?: return
        // Seeded categories are identified by uuid; the slug exists only in
        // DefaultData and never reaches the table.
        val uncategorized = categories.observeAll().first()
            .firstOrNull { it.uuid == "seed-cat-uncategorized" } ?: return

        budgets.setLimit(uncategorized.id, legacy)
        preferences.setMonthlyBudget(null)
    }
}
