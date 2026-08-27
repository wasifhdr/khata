package com.wasif.khata.core.data.di

import com.wasif.khata.core.data.repository.ReferenceDataRepositoryImpl
import com.wasif.khata.core.data.repository.TransactionRepositoryImpl
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindReferenceDataRepository(
        impl: ReferenceDataRepositoryImpl,
    ): ReferenceDataRepository
}
