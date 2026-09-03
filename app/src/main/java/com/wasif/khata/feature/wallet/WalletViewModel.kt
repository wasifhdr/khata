package com.wasif.khata.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WalletViewModel @Inject constructor(
    transactions: TransactionRepository,
    reference: ReferenceDataRepository,
    transactionDao: TransactionDao,
    clock: KhataClock,
) : ViewModel() {

    private val now = clock.now()
    private val monthStart = now.dhakaMonthStart()
    private val trendFrom = now - TREND_DAYS * DAY_MILLIS

    val state: StateFlow<WalletUiState> = combine(
        reference.observeNetWorth(),
        transactions.observeSpentBetween(monthStart, now.dhakaNextMonthStart()),
        transactions.observeReceivedBetween(monthStart, now.dhakaNextMonthStart()),
        reference.observeAccounts(),
        combine(
            transactionDao.observeSpendByCategory(monthStart, now.dhakaNextMonthStart()),
            reference.observeCategories(),
            transactionDao.observeDailyNet(trendFrom),
        ) { rows, categories, daily -> Triple(rows, categories, daily) },
    ) { netWorth, spend, received, accounts, (rows, categories, daily) ->
        val names = categories.associateBy { it.id }
        val total = rows.sumOf { it.totalMinor }.coerceAtLeast(1L)

        WalletUiState(
            netWorth = netWorth,
            monthSpend = spend,
            monthReceived = received,
            accounts = accounts,
            categories = rows.map { row ->
                val category = row.categoryId?.let(names::get)
                CategorySlice(
                    name = category?.name ?: "Uncategorised",
                    colorToken = category?.colorToken ?: "category_neutral",
                    amount = Money(row.totalMinor),
                    share = row.totalMinor.toFloat() / total,
                )
            },
            netWorthTrend = netWorthTrend(netWorth.minor, daily.map { it.netMinor }),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WalletUiState(),
    )

    private companion object {
        const val TREND_DAYS = 90L
        const val DAY_MILLIS = 86_400_000L
    }
}

/**
 * Walks the daily movements backwards from today's known net worth to give a value
 * for the end of each day, oldest first.
 *
 * ponytail: derived, not stored. No snapshot table, so the *shape* is exact but
 * historic absolute values drift by whatever the ledger cannot explain -- deleted
 * messages, mostly. Add snapshots if the absolute past ever needs to be trusted
 * rather than glanced at.
 */
internal fun netWorthTrend(netWorthMinor: Long, dailyNet: List<Long>): List<Long> {
    if (dailyNet.size < 2) return emptyList()
    val series = ArrayList<Long>(dailyNet.size)
    var running = netWorthMinor
    for (delta in dailyNet.asReversed()) {
        series += running
        running -= delta
    }
    return series.asReversed()
}
