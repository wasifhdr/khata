package com.wasif.khata.domain.repository

import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface ReferenceDataRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeCategories(): Flow<List<Category>>
}
