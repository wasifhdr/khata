package com.wasif.khata.core.data.di

import android.content.Context
import androidx.room.Room
import com.wasif.khata.core.data.KhataDatabase
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
import com.wasif.khata.core.data.migration.MIGRATION_1_2
import com.wasif.khata.core.data.migration.MIGRATION_2_3
import com.wasif.khata.core.data.migration.MIGRATION_3_4
import com.wasif.khata.core.data.migration.MIGRATION_4_5
import com.wasif.khata.core.data.migration.MIGRATION_5_6
import com.wasif.khata.core.data.migration.MIGRATION_6_7
import com.wasif.khata.core.data.migration.MIGRATION_7_8
import com.wasif.khata.core.data.migration.MIGRATION_8_9
import com.wasif.khata.core.data.migration.MIGRATION_9_10
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.SystemKhataClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KhataDatabase =
        Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .build()

    @Provides fun provideAccountDao(db: KhataDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: KhataDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: KhataDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideMerchantDao(db: KhataDatabase): MerchantDao = db.merchantDao()
    @Provides fun provideRawMessageDao(db: KhataDatabase): RawMessageDao = db.rawMessageDao()
    @Provides fun provideParsingRuleDao(db: KhataDatabase): ParsingRuleDao = db.parsingRuleDao()
    @Provides fun provideBalanceSnapshotDao(db: KhataDatabase): BalanceSnapshotDao = db.balanceSnapshotDao()
    @Provides fun provideCategoryBudgetDao(db: KhataDatabase): CategoryBudgetDao = db.categoryBudgetDao()
    @Provides fun providePlaceDao(db: KhataDatabase): PlaceDao = db.placeDao()
    @Provides fun provideMediaDao(db: KhataDatabase): MediaDao = db.mediaDao()
    @Provides fun provideTagDao(db: KhataDatabase): TagDao = db.tagDao()
    @Provides fun provideSearchDao(db: KhataDatabase): SearchDao = db.searchDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    abstract fun bindClock(impl: SystemKhataClock): KhataClock
}
