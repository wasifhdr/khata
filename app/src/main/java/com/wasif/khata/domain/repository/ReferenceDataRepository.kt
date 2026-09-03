package com.wasif.khata.domain.repository

import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface ReferenceDataRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeCategories(): Flow<List<Category>>

    /** Deleted ones too, for turning a stored categoryId into a label. */
    fun observeCategoriesIncludingDeleted(): Flow<List<Category>>
    fun observeNetWorth(): Flow<Money>
}
