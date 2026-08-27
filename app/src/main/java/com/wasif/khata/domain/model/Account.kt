package com.wasif.khata.domain.model

import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money

data class Account(
    val id: Long,
    val uuid: String,
    val name: String,
    val type: AccountType,
    val currentBalance: Money,
    val reportedBalance: Money?,
    val includeInNetWorth: Boolean,
) {
    val hasBalanceDrift: Boolean
        get() = reportedBalance != null && reportedBalance != currentBalance
}
