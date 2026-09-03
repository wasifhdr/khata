package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.BalanceSnapshotDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.entity.BalanceSnapshotEntity
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.toDhakaDayIndex
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the closing balance of every account for every Dhaka day that has no row yet,
 * up to and including [today]'s.
 *
 * One routine for three callers -- the one-time backfill over existing history, the
 * nightly job, and a phone that was off for a week -- because to this code they differ
 * only in how many days are missing. That is what makes a missed night cost nothing.
 */
@Singleton
class SnapshotWriter @Inject constructor(
    private val snapshots: BalanceSnapshotDao,
    private val transactions: TransactionDao,
    private val accounts: AccountDao,
    private val clock: KhataClock,
) {
    suspend fun fillThrough(today: Long) {
        val todayIndex = today.toDhakaDayIndex()
        val movements = transactions.dailyMovementByAccount().groupBy { it.accountId }
        val now = clock.now()

        for (account in accounts.getAll()) {
            val byDay = movements[account.id].orEmpty().associate { it.dayIndex to it.netMinor }
            // An account with no history still exists and still holds its opening
            // balance. Starting from its first movement would drop it out of net worth
            // on every earlier day, which is a different claim from "it did not move".
            val firstDay = byDay.keys.minOrNull() ?: todayIndex
            if (firstDay > todayIndex) continue

            // Walk backwards from the balance we know: today's. Each step removes the
            // movement of the day above it, leaving the balance at that day's close.
            val balances = HashMap<Long, Long>()
            var running = account.currentBalanceMinor
            for (day in todayIndex downTo firstDay) {
                balances[day] = running
                running -= byDay[day] ?: 0L
            }

            val existing = snapshots.existingDayIndices(account.id).toSet()
            snapshots.insertAll(
                balances.filterKeys { it !in existing }.map { (day, balance) ->
                    BalanceSnapshotEntity(
                        uuid = UUID.randomUUID().toString(),
                        accountId = account.id,
                        dayIndex = day,
                        balanceMinor = balance,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
    }
}
