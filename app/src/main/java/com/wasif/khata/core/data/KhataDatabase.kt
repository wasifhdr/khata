package com.wasif.khata.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.BalanceSnapshotDao
import com.wasif.khata.core.data.dao.CategoryBudgetDao
import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.data.dao.MerchantDao
import com.wasif.khata.core.data.dao.ParsingRuleDao
import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.BalanceSnapshotEntity
import com.wasif.khata.core.data.entity.CategoryBudgetEntity
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.data.entity.MerchantAliasEntity
import com.wasif.khata.core.data.entity.MerchantEntity
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.data.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MerchantEntity::class,
        MerchantAliasEntity::class,
        RawMessageEntity::class,
        ParsingRuleEntity::class,
        BalanceSnapshotEntity::class,
        CategoryBudgetEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantDao(): MerchantDao
    abstract fun rawMessageDao(): RawMessageDao
    abstract fun parsingRuleDao(): ParsingRuleDao
    abstract fun balanceSnapshotDao(): BalanceSnapshotDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
}
