package com.wasif.khata.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.DHAKA
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A category's row-facing identity: the dot's colour and the name that must always sit beside it. */
data class CategoryChip(
    val name: String,
    val colorToken: String,
)

data class LedgerHeaderState(
    val monthLabel: String = "",
    val monthSpend: Money = Money.ZERO,
    /** Null for any month that is not the current one -- a past month has no days left. */
    val daysLeft: Int? = null,
)

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repository: TransactionRepository,
    referenceData: ReferenceDataRepository,
    private val clock: KhataClock,
) : ViewModel() {

    private val currentMonth = YearMonth.from(clock.now().toDhakaLocalDate())

    private val _viewedMonth = MutableStateFlow(currentMonth)

    /** The current month is the newest that can hold anything, so forward stops there. */
    val canGoForward: StateFlow<Boolean> = _viewedMonth
        .map { it < currentMonth }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onPreviousMonth() {
        _viewedMonth.value = _viewedMonth.value.minusMonths(1)
    }

    fun onNextMonth() {
        if (_viewedMonth.value < currentMonth) {
            _viewedMonth.value = _viewedMonth.value.plusMonths(1)
        }
    }

    /** categoryId -> chip, so a row resolves its dot and name without a per-row query. */
    val categoryTokens: StateFlow<Map<Long, CategoryChip>> = referenceData.observeCategories()
        .map { categories -> categories.associate { it.id to CategoryChip(it.name, it.colorToken) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val header: StateFlow<LedgerHeaderState> = _viewedMonth
        .flatMapLatest { month ->
            val (from, to) = month.dhakaWindow()
            repository.observeSpentBetween(from, to).map { spend ->
                LedgerHeaderState(
                    monthLabel = month.format(monthFormatter),
                    monthSpend = spend,
                    daysLeft = if (month == currentMonth) {
                        month.lengthOfMonth() - clock.now().toDhakaLocalDate().dayOfMonth
                    } else {
                        null
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerHeaderState())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    private val _needsAttentionOnly = MutableStateFlow(false)
    val needsAttentionOnly: StateFlow<Boolean> = _needsAttentionOnly.asStateFlow()

    /** Drives the filter's own badge, so it can say how much it would show. */
    val needsAttentionCount: StateFlow<Int> = repository.observeNeedsAttentionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onNeedsAttentionToggled() {
        _needsAttentionOnly.value = !_needsAttentionOnly.value
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<LedgerItem>> =
        combine(_viewedMonth, _query, _needsAttentionOnly) { month, q, attention ->
            Triple(month, q, attention)
        }
        // Search spans all time: hunting for one past transaction is a distinct
        // job from reviewing a month, and confining it to the viewed month would
        // make the common case -- "I know I bought it, I forget when" -- fail.
        // flatMapLatest cancels the previous stream rather than stacking one page
        // load per keystroke.
        .flatMapLatest { (month, q, attention) ->
            when {
                // Needs-attention spans all time for the same reason search does: the
                // point is to find every uncertain row, not the uncertain rows in one
                // month. It outranks the month window but not an active search.
                q.isNotBlank() -> repository.pagedTransactions(q)
                attention -> repository.pagedNeedsAttention()
                else -> {
                    val (from, to) = month.dhakaWindow()
                    repository.pagedTransactionsBetween(from, to)
                }
            }
        }
        // cachedIn goes BEFORE the combine, not after. observeDayTotals() is a
        // Room flow that re-emits on every write to transactions; with the cache
        // last, each of those emissions re-derives a PagingData wrapping the same
        // pageEventFlow and Paging throws "Attempt to collect twice". Caching
        // first makes the result re-collectable, which is what lets a second flow
        // be combined into it at all.
        .cachedIn(viewModelScope)
        .combine(repository.observeDayTotals()) { paging, totals ->
            paging.map { LedgerItem.Row(it) }
                // insertSeparators<T, R> widens Row to LedgerItem, so the generator
                // receives typed Rows and needs no casts.
                .insertSeparators<LedgerItem.Row, LedgerItem> { before, after ->
                    if (after == null) {
                        null
                    } else {
                        val afterDate = after.transaction.occurredAt.toDhakaLocalDate()
                        val beforeDate = before?.transaction?.occurredAt?.toDhakaLocalDate()
                        if (beforeDate != afterDate) {
                            // The totals map and the paged rows are two queries and
                            // can disagree for a frame. Zero is a safe reading; a
                            // crash in a 3,000-row list is not.
                            LedgerItem.DayHeader(afterDate, totals[afterDate] ?: Money.ZERO)
                        } else {
                            null
                        }
                    }
                }
        }
}

/** The month's half-open bounds in epoch millis, computed in Dhaka. */
private fun YearMonth.dhakaWindow(): Pair<Long, Long> {
    val start = atDay(1).atStartOfDay(DHAKA).toInstant().toEpochMilli()
    return start to start.dhakaNextMonthStart()
}
