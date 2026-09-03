package com.wasif.khata.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.repository.MonthLimits
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.domain.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val TOP_MERCHANTS = 5

@HiltViewModel
class InsightsViewModel @Inject constructor(
    transactionDao: TransactionDao,
    reference: ReferenceDataRepository,
    limits: MonthLimits,
    clock: KhataClock,
) : ViewModel() {

    private val now = clock.now()
    private val thisFrom = now.dhakaMonthStart()
    private val thisTo = now.dhakaNextMonthStart()

    // A millisecond before this month began is inside the previous one, so
    // dhakaMonthStart of it is that month's start -- no month arithmetic of our own.
    private val lastFrom = (thisFrom - 1).dhakaMonthStart()

    val state: StateFlow<InsightsUiState> = combine(
        transactionDao.compareCategorySpend(thisFrom, thisTo, lastFrom, thisFrom),
        // Including deleted: this maps a stored categoryId to its label, and a
        // category deleted last week still named last March's spending.
        reference.observeCategoriesIncludingDeleted(),
        limits(thisFrom),
        transactionDao.observeTopMerchants(thisFrom, thisTo, TOP_MERCHANTS),
    ) { comparison, categories, monthLimits, merchants ->
        val names = categories.associateBy { it.id }
        InsightsUiState(
            monthSpend = Money(comparison.sumOf { it.thisMonthMinor }),
            lastMonthSpend = Money(comparison.sumOf { it.lastMonthMinor }),
            categories = comparison
                .sortedByDescending { it.thisMonthMinor }
                .map { row ->
                    val category = row.categoryId?.let(names::get)
                    CategoryInsight(
                        name = category?.name ?: "Uncategorised",
                        colorToken = category?.colorToken ?: "category_neutral",
                        amount = Money(row.thisMonthMinor),
                        lastAmount = Money(row.lastMonthMinor),
                        limit = row.categoryId?.let { monthLimits[it] }?.let(::Money),
                    )
                },
            merchants = merchants.map { MerchantSpend(it.merchantName, Money(it.totalMinor)) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InsightsUiState(),
    )
}
