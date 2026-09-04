package com.wasif.khata.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.BalanceSnapshotDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.toDhakaDayIndex
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.wasif.khata.core.data.repository.toDomain
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WalletViewModel @Inject constructor(
    transactions: TransactionRepository,
    reference: ReferenceDataRepository,
    transactionDao: TransactionDao,
    snapshotDao: BalanceSnapshotDao,
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
            // Including deleted: this maps a stored categoryId to its label, and a
            // category deleted last week still named last March's spending.
            reference.observeCategoriesIncludingDeleted(),
            snapshotDao.observeTotalsFrom(trendFrom.toDhakaDayIndex()),
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
            netWorthTrend = daily.map { it.totalMinor },
        )
    }
        // Combined onto the end rather than folded into the five above: the pending
        // list is independent of every figure on this screen, and threading a sixth
        // source through that nest would obscure all of them.
        .combine(transactionDao.observePendingReviews()) { state, pending ->
            state.copy(pendingReviews = pending.map { it.toDomain() })
        }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WalletUiState(),
    )

    private companion object {
        const val TREND_DAYS = 90L
        const val DAY_MILLIS = 86_400_000L
    }
}
