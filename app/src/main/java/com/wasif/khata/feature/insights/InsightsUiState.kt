package com.wasif.khata.feature.insights

import com.wasif.khata.core.model.Money

data class CategoryInsight(
    val name: String,
    val colorToken: String,
    val amount: Money,
    val lastAmount: Money,
    val limit: Money?,
) {
    /**
     * Null when there is nothing to compare against. A rise from zero is +100% by
     * arithmetic and nonsense by meaning -- it says something about missing data, not
     * about spending.
     */
    val changeFraction: Float?
        get() = if (lastAmount.isZero) {
            null
        } else {
            (amount.minor - lastAmount.minor).toFloat() / lastAmount.minor.toFloat()
        }

    /** Null when no limit is set: absent is not a limit of zero. */
    val budgetFraction: Float?
        get() = limit?.let {
            if (it.isZero) 1f else (amount.minor.toFloat() / it.minor.toFloat()).coerceIn(0f, 1f)
        }

    val isOverBudget: Boolean get() = limit != null && amount > limit

    val spendsMore: Boolean get() = amount > lastAmount
}

data class MerchantSpend(val name: String, val amount: Money)

data class InsightsUiState(
    val monthSpend: Money = Money.ZERO,
    val lastMonthSpend: Money = Money.ZERO,
    val categories: List<CategoryInsight> = emptyList(),
    val merchants: List<MerchantSpend> = emptyList(),
) {
    val changeFraction: Float?
        get() = if (lastMonthSpend.isZero) {
            null
        } else {
            (monthSpend.minor - lastMonthSpend.minor).toFloat() / lastMonthSpend.minor.toFloat()
        }

    val spendsMore: Boolean get() = monthSpend > lastMonthSpend
}
