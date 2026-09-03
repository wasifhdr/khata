package com.wasif.khata.feature.insights

import com.wasif.khata.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsViewModelTest {

    @Test
    fun `a first month has no comparison rather than a hundred percent rise`() {
        // Subtracting from zero yields +100%, which reads as a fact about spending
        // instead of an absence of data.
        val insight = CategoryInsight(
            name = "Groceries",
            colorToken = "category_green",
            amount = Money(500_00),
            lastAmount = Money.ZERO,
            limit = null,
        )

        assertNull(insight.changeFraction)
    }

    @Test
    fun `a category over its limit says so, and the bar does not exceed full`() {
        val insight = CategoryInsight(
            name = "Eating Out",
            colorToken = "category_orange",
            amount = Money(750_00),
            lastAmount = Money(500_00),
            limit = Money(500_00),
        )

        assertTrue(insight.isOverBudget)
        assertEquals(1f, insight.budgetFraction!!, 0.001f)
    }

    @Test
    fun `a category with no limit has no budget bar at all`() {
        val insight = CategoryInsight(
            name = "Fuel",
            colorToken = "category_slate",
            amount = Money(100_00),
            lastAmount = Money.ZERO,
            limit = null,
        )

        assertNull(insight.budgetFraction)
        assertFalse(insight.isOverBudget)
    }

    @Test
    fun `a zero limit is fully spent rather than a division by zero`() {
        val insight = CategoryInsight(
            name = "Entertainment",
            colorToken = "category_pink",
            amount = Money(1_00),
            lastAmount = Money.ZERO,
            limit = Money.ZERO,
        )

        assertEquals(1f, insight.budgetFraction!!, 0.001f)
        assertTrue(insight.isOverBudget)
    }

    @Test
    fun `the month total reports its direction and change`() {
        val state = InsightsUiState(
            monthSpend = Money(1_200_00),
            lastMonthSpend = Money(1_000_00),
        )

        assertTrue(state.spendsMore)
        assertEquals(0.2f, state.changeFraction!!, 0.001f)
    }
}
