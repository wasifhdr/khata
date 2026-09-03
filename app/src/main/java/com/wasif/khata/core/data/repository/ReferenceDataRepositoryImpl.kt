package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.repository.ReferenceDataRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReferenceDataRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) : ReferenceDataRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeCategoriesIncludingDeleted(): Flow<List<Category>> =
        categoryDao.observeAllIncludingDeleted().map { entities -> entities.map { it.toDomain() } }

    override fun observeNetWorth(): Flow<Money> =
        accountDao.observeNetWorthMinor().map { Money(it) }
}
