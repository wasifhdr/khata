package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.model.Money
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class BalanceDrift(
    val accountId: Long,
    val accountName: String,
    val computed: Money,
    val reported: Money,
    val reportedAt: Long,
) {
    /** Positive when the bank says there is more money than Khata recorded. */
    val gap: Money get() = reported - computed
}

/**
 * Nearly every bKash and EBL message states a running balance, so this is the app's
 * primary correctness mechanism rather than an occasional cross-check: it catches
 * missed cash spending and misparses by arithmetic instead of by vigilance.
 */
@Singleton
class ReconciliationRepository @Inject constructor(
    private val accountDao: AccountDao,
) {
    fun observeDrift(): Flow<List<BalanceDrift>> =
        accountDao.observeAll().map { accounts ->
            accounts.mapNotNull { account ->
                val reported = account.reportedBalanceMinor ?: return@mapNotNull null
                val at = account.reportedBalanceAt ?: return@mapNotNull null
                if (reported == account.currentBalanceMinor) return@mapNotNull null

                BalanceDrift(
                    accountId = account.id,
                    accountName = account.name,
                    computed = Money(account.currentBalanceMinor),
                    reported = Money(reported),
                    reportedAt = at,
                )
            }
        }
}
