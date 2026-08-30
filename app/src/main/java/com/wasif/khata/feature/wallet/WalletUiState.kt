package com.wasif.khata.feature.wallet

import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Account

data class WalletUiState(
    val netWorth: Money = Money.ZERO,
    val monthSpend: Money = Money.ZERO,
    val monthReceived: Money = Money.ZERO,
    val accounts: List<Account> = emptyList(),
) {
    val driftingAccounts: Int get() = accounts.count { it.hasBalanceDrift }
}
