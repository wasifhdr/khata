package com.wasif.khata.feature.owed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One person and what stands between you. */
data class Person(
    val name: String,
    /** Always positive; [owesYou] says which way round it is. */
    val amount: Money,
    val owesYou: Boolean,
    val entries: Int,
)

/** A loan or covered bill with nobody's name on it yet. */
data class UnnamedDebt(
    val transactionId: Long,
    val amount: Money,
    val occurredAt: Long,
    val kind: TransactionKind,
) {
    val describes: String
        get() = when (kind) {
            TransactionKind.LENT -> "Lent to someone"
            TransactionKind.COVERED_FOR_SOMEONE -> "A bill you paid for someone"
            TransactionKind.BORROWED -> "Borrowed from someone"
            TransactionKind.BORROWED_RETURNED -> "Paid back what you borrowed"
            TransactionKind.LENT_RETURNED -> "A loan returned to you"
            TransactionKind.REIMBURSEMENT -> "Someone's share of a bill"
            else -> "Owed"
        }
}

data class OwedUiState(
    val owesYou: List<Person> = emptyList(),
    val youOwe: List<Person> = emptyList(),
    val unnamed: List<UnnamedDebt> = emptyList(),
    val isLoaded: Boolean = false,
) {
    val owedToYou: Money get() = Money(owesYou.sumOf { it.amount.minor })
    val owedByYou: Money get() = Money(youOwe.sumOf { it.amount.minor })

    /** Only meaningful once loaded: empty before that is "not yet", not "nobody". */
    val isSettled: Boolean get() = owesYou.isEmpty() && youOwe.isEmpty() && unnamed.isEmpty()
}

@HiltViewModel
class OwedViewModel @Inject constructor(
    transactionDao: TransactionDao,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<OwedUiState> = combine(
        transactionDao.observeOwedByPerson(),
        transactionDao.observeUnnamedOwed(),
    ) { people, unnamed ->
        OwedUiState(
            // Positive means they owe you; the query nets everything first, so a
            // person who borrowed and repaid drops out rather than showing zero.
            owesYou = people.filter { it.netMinor > 0 }.map {
                Person(it.name, Money(it.netMinor), owesYou = true, entries = it.entries)
            },
            youOwe = people.filter { it.netMinor < 0 }.map {
                Person(it.name, Money(-it.netMinor), owesYou = false, entries = it.entries)
            },
            unnamed = unnamed.map {
                UnnamedDebt(
                    transactionId = it.id,
                    amount = Money(it.amountMinor),
                    occurredAt = it.occurredAt,
                    kind = it.kind,
                )
            },
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OwedUiState())
}
