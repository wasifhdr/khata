package com.wasif.khata.feature.wallet

import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Transaction

/** One slice of the month's spending. */
data class CategorySlice(
    val name: String,
    val colorToken: String,
    val amount: Money,
    /** Of the month's total, 0..1. Drives the bar width. */
    val share: Float,
)

data class WalletUiState(
    val netWorth: Money = Money.ZERO,
    val monthSpend: Money = Money.ZERO,
    val monthReceived: Money = Money.ZERO,
    val accounts: List<Account> = emptyList(),
    val categories: List<CategorySlice> = emptyList(),
    /** Net worth per day, oldest first. Fewer than two points draws nothing. */
    val netWorthTrend: List<Long> = emptyList(),
    /** Movements waiting to be settled as own-account transfers, or not. */
    val pendingReviews: List<Transaction> = emptyList(),
) {
    val driftingAccounts: Int get() = accounts.count { it.hasBalanceDrift }
}
