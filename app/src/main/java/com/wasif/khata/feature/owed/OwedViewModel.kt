package com.wasif.khata.feature.owed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionDraft
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One person and what stands between you. */
data class Person(
    val name: String,
    /** Always positive; [owesYou] says which way round it is. */
    val amount: Money,
    val owesYou: Boolean,
    val entries: Int,
)

/** One owed entry in a person's detail sheet. */
data class PersonOwedEntry(
    val id: Long,
    val label: String,
    val occurredAt: Long,
    val owedAmount: Money,
    val increasesWhatTheyOwe: Boolean,
)

/** Active detail sheet state for a selected person on the Owed screen. */
data class PersonOwedDetail(
    val person: Person,
    val entries: List<PersonOwedEntry>,
    val accounts: List<Account>,
    val selectedAccountId: Long?,
) {
    val selectedAccount: Account?
        get() = accounts.firstOrNull { it.id == selectedAccountId }
}

/** A loan or covered bill with nobody's name on it yet. */
data class UnnamedDebt(
    val transactionId: Long,
    val amount: Money,
    val occurredAt: Long,
    val kind: TransactionKind,
) {
    val describes: String
        get() = when (kind) {
            TransactionKind.IOU -> "Someone paid for you"
            else -> "Owed"
        }
}

data class OwedUiState(
    val owesYou: List<Person> = emptyList(),
    val youOwe: List<Person> = emptyList(),
    val unnamed: List<UnnamedDebt> = emptyList(),
    val selectedPerson: PersonOwedDetail? = null,
    val isLoaded: Boolean = false,
) {
    val owedToYou: Money get() = Money(owesYou.sumOf { it.amount.minor })
    val owedByYou: Money get() = Money(youOwe.sumOf { it.amount.minor })

    /** Only meaningful once loaded: empty before that is "not yet", not "nobody". */
    val isSettled: Boolean get() = owesYou.isEmpty() && youOwe.isEmpty() && unnamed.isEmpty()
}

@HiltViewModel
class OwedViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionRepository: TransactionRepository,
    referenceDataRepository: ReferenceDataRepository,
    private val clock: KhataClock,
) : ViewModel() {

    private val selectedPersonName = MutableStateFlow<String?>(null)
    private val selectedAccountId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedEntries = selectedPersonName.flatMapLatest { name ->
        if (name.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            transactionDao.observeOwedEntriesForPerson(name)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<OwedUiState> = combine(
        transactionDao.observeOwedByPerson(),
        transactionDao.observeUnnamedOwed(),
        referenceDataRepository.observeAccounts(),
        selectedPersonName,
        combine(selectedEntries, selectedAccountId) { entries, accountId -> entries to accountId },
    ) { people, unnamed, accounts, selectedName, (entries, chosenAccountId) ->
        val owesYou = people.filter { it.netMinor > 0 }.map {
            Person(it.name, Money(it.netMinor), owesYou = true, entries = it.entries)
        }
        val youOwe = people.filter { it.netMinor < 0 }.map {
            Person(it.name, Money(-it.netMinor), owesYou = false, entries = it.entries)
        }
        val sortedAccounts = accounts.sortedByDescending { it.type == AccountType.CASH }
        val defaultAccountId = sortedAccounts.firstOrNull { it.type == AccountType.CASH }?.id
            ?: sortedAccounts.firstOrNull()?.id
        val matchedPerson = selectedName?.let { target ->
            (owesYou + youOwe).firstOrNull {
                it.name.trim().equals(target.trim(), ignoreCase = true)
            }
        }
        val detail = matchedPerson?.let { person ->
            PersonOwedDetail(
                person = person,
                entries = entries.map(TransactionEntity::toPersonOwedEntry),
                accounts = sortedAccounts,
                selectedAccountId = chosenAccountId ?: defaultAccountId,
            )
        }
        OwedUiState(
            owesYou = owesYou,
            youOwe = youOwe,
            unnamed = unnamed.map {
                UnnamedDebt(
                    transactionId = it.id,
                    amount = Money(it.owedMinor.takeIf { m -> m > 0 } ?: it.amountMinor),
                    occurredAt = it.occurredAt,
                    kind = it.kind,
                )
            },
            selectedPerson = detail,
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OwedUiState())

    fun onSelectPerson(name: String?) {
        selectedPersonName.value = name
        if (name == null) {
            selectedAccountId.value = null
        }
    }

    fun onSelectSettleAccount(accountId: Long) {
        selectedAccountId.value = accountId
    }

    fun onSettleSelectedPerson() {
        val detail = state.value.selectedPerson ?: return
        val accountId = detail.selectedAccountId ?: return
        selectedPersonName.value = null
        selectedAccountId.value = null
        viewModelScope.launch {
            transactionRepository.save(
                TransactionDraft(
                    id = null,
                    accountId = accountId,
                    amount = detail.person.amount,
                    direction = if (detail.person.owesYou) {
                        TransactionDirection.CREDIT
                    } else {
                        TransactionDirection.DEBIT
                    },
                    kind = TransactionKind.NORMAL,
                    occurredAt = clock.now(),
                    merchantRaw = null,
                    categoryId = null,
                    counterparty = detail.person.name,
                    owed = detail.person.amount,
                    note = "Settled up",
                ),
            )
        }
    }
}

private fun TransactionEntity.toPersonOwedEntry(): PersonOwedEntry {
    val label = merchantRaw?.takeIf { it.isNotBlank() }
        ?: note?.takeIf { it.isNotBlank() }
        ?: when {
            kind == TransactionKind.IOU -> "They paid"
            direction == TransactionDirection.CREDIT -> "Received"
            owedMinor < amountMinor -> "Split (${Money(owedMinor).format()} of ${Money(amountMinor).format()})"
            else -> "Lent"
        }
    val increasesWhatTheyOwe = kind != TransactionKind.IOU && direction == TransactionDirection.DEBIT
    return PersonOwedEntry(
        id = id,
        label = label,
        occurredAt = occurredAt,
        owedAmount = Money(owedMinor),
        increasesWhatTheyOwe = increasesWhatTheyOwe,
    )
}
