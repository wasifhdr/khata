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

        syncBuiltInRules(now)
    }

    /**
     * Runs every launch, not only on a fresh install. A widened rule is worthless
     * if it only reaches people who have never opened the app -- the built-ins
     * were seeded once and then frozen, so an existing install kept reading
     * messages with whatever wording the rules shipped with on day one.
     *
     * Matched by uuid rather than inserted blindly, because @Upsert resolves
     * conflicts by primary key: a built-in with id = 0 and a uuid already present
     * would fail its insert and then update nothing.
     *
     * A built-in the user has switched off stays off, and rules they wrote
     * themselves are never touched.
     */
    private suspend fun syncBuiltInRules(now: Long) {
        val existing = ruleDao.allIncludingDisabled().associateBy { it.uuid }
        ruleDao.upsertAll(
            BUILT_IN_RULES.map { builtIn ->
                val previous = existing[builtIn.uuid]
                builtIn.copy(
                    id = previous?.id ?: 0,
                    isEnabled = previous?.isEnabled ?: true,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now,
                )
            },
        )
    }
}
