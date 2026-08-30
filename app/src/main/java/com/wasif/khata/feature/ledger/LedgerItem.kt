package com.wasif.khata.feature.ledger

import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Transaction
import java.time.LocalDate

sealed interface LedgerItem {
    data class Row(val transaction: Transaction) : LedgerItem
    data class DayHeader(val date: LocalDate, val total: Money) : LedgerItem
}
