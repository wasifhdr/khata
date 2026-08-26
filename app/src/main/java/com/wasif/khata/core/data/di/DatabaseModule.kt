package com.wasif.khata.core.data.di

import android.content.Context
import androidx.room.Room
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.CategoryDao
import com.wasif.khata.core.data.dao.MerchantDao
import com.wasif.khata.core.data.dao.TransactionDao
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
        Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db").build()

    @Provides fun provideAccountDao(db: KhataDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: KhataDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: KhataDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideMerchantDao(db: KhataDatabase): MerchantDao = db.merchantDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    abstract fun bindClock(impl: SystemKhataClock): KhataClock
}
