package com.wasif.khata.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@HiltViewModel
class LedgerViewModel @Inject constructor(
    repository: TransactionRepository,
) : ViewModel() {

    val items: Flow<PagingData<LedgerItem>> = repository.pagedTransactions()
        .map { paging ->
            paging.map { LedgerItem.Row(it) }
                // insertSeparators<T, R> widens Row to LedgerItem, so the generator
                // receives typed Rows and needs no casts.
                .insertSeparators<LedgerItem.Row, LedgerItem> { before, after ->
                    if (after == null) {
                        null
                    } else {
                        val afterDate = after.transaction.occurredAt.toDhakaLocalDate()
                        val beforeDate = before?.transaction?.occurredAt?.toDhakaLocalDate()
                        if (beforeDate != afterDate) LedgerItem.DayHeader(afterDate) else null
                    }
                }
        }
        .cachedIn(viewModelScope)
}
