package com.wasif.khata.core.data.seed

import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.data.dao.ParsingRuleDao
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.sms.BUILT_IN_RULES
import com.wasif.khata.core.time.KhataClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val ruleDao: ParsingRuleDao,
    private val clock: KhataClock,
) {

    suspend fun seedIfEmpty() {
        val now = clock.now()

        if (accountDao.countIncludingDeleted() == 0) {
            DEFAULT_ACCOUNTS.forEach { seed ->
                accountDao.upsert(
                    AccountEntity(
                        uuid = "seed-acc-${seed.slug}",
                        name = seed.name,
                        type = seed.type,
                        openingBalanceMinor = 0,
                        currentBalanceMinor = 0,
                        reportedBalanceMinor = null,
                        reportedBalanceAt = null,
                        includeInNetWorth = true,
                        smsIdentifiers = seed.smsIdentifiers,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }

        if (categoryDao.countIncludingDeleted() == 0) {
            categoryDao.upsertAll(
                DEFAULT_CATEGORIES.map { seed ->
                    CategoryEntity(
                        uuid = "seed-cat-${seed.slug}",
                        name = seed.name,
                        icon = seed.icon,
                        colorToken = seed.colorToken,
                        parentId = null,
                        isSystem = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            )
        }

        if (ruleDao.countIncludingDeleted() == 0) {
            ruleDao.upsertAll(BUILT_IN_RULES.map { it.copy(createdAt = now, updatedAt = now) })
        }
    }
}
