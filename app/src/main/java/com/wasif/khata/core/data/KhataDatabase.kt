package com.wasif.khata.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.BalanceSnapshotDao
import com.wasif.khata.core.data.dao.CategoryBudgetDao
import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.data.dao.MediaDao
import com.wasif.khata.core.data.dao.MerchantDao
import com.wasif.khata.core.data.dao.ParsingRuleDao
import com.wasif.khata.core.data.dao.PlaceDao
import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.data.dao.SearchDao
import com.wasif.khata.core.data.dao.TagDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.BalanceSnapshotEntity
import com.wasif.khata.core.data.entity.CategoryBudgetEntity
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.data.entity.MediaEntity
import com.wasif.khata.core.data.entity.MediaLinkEntity
import com.wasif.khata.core.data.entity.MerchantAliasEntity
import com.wasif.khata.core.data.entity.MerchantEntity
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.data.entity.PlaceEntity
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.data.entity.SearchFtsEntity
import com.wasif.khata.core.data.entity.TagEntity
import com.wasif.khata.core.data.entity.TagLinkEntity
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
        PlaceEntity::class,
        MediaEntity::class,
        MediaLinkEntity::class,
        TagEntity::class,
        TagLinkEntity::class,
        SearchFtsEntity::class,
    ],
    version = 10,
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
    abstract fun placeDao(): PlaceDao
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
    abstract fun searchDao(): SearchDao
}
