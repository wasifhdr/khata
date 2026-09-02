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
    /** Positive when more arrived than the recorded transactions account for. */
    val gap: Money,
)

/**
 * Every bKash and EBL message states the balance after it, so the balance itself is
 * never in doubt — Khata takes each statement as fact. What this reports instead is
 * the money that moved with no message to explain it, which is the honest measure of
 * how complete the SMS history is. On a real inbox it found six years of deleted
 * messages; it also catches cash spending that was never entered.
 */
@Singleton
class ReconciliationRepository @Inject constructor(
    private val accountDao: AccountDao,
) {
    fun observeDrift(): Flow<List<BalanceDrift>> =
        accountDao.observeAll().map { accounts ->
            accounts.mapNotNull { account ->
                if (account.unexplainedMinor == 0L) return@mapNotNull null
                val at = account.reportedBalanceAt ?: return@mapNotNull null

                BalanceDrift(
                    accountId = account.id,
                    accountName = account.name,
                    // The balance is the bank's own figure; what differs is how much
                    // of it the recorded transactions can account for.
                    computed = Money(account.currentBalanceMinor - account.unexplainedMinor),
                    reported = Money(account.currentBalanceMinor),
                    reportedAt = at,
                    gap = Money(account.unexplainedMinor),
                )
            }
        }
}
