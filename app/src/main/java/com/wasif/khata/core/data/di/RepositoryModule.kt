package com.wasif.khata.core.data.di

import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.repository.ReferenceDataRepositoryImpl
import com.wasif.khata.core.data.repository.TransactionRepositoryImpl
import com.wasif.khata.domain.repository.ReferenceDataRepository
import com.wasif.khata.domain.repository.TransactionRepository
import com.wasif.khata.core.data.repository.BudgetRepository
import com.wasif.khata.core.data.repository.MonthLimits
import com.wasif.khata.core.drive.DriveBackups
import com.wasif.khata.core.drive.DriveClient
import com.wasif.khata.core.drive.DriveMedia
import com.wasif.khata.core.drive.DriveUploader
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.search.IndexSource
import com.wasif.khata.core.search.TransactionIndexSource
import com.wasif.khata.core.sms.IngestionScheduler
import com.wasif.khata.core.sms.TeachRequest
import com.wasif.khata.core.sms.ai.GeminiClient
import com.wasif.khata.core.sms.ai.RuleSuggester
import com.wasif.khata.core.sms.StartBackfill
import com.wasif.khata.feature.editor.OriginalMessage
import com.wasif.khata.feature.widget.RecentCategoryIds
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.flow.first
import dagger.hilt.InstallIn
import dagger.multibindings.IntoSet
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

    @Binds
    @Singleton
    abstract fun bindDriveUploader(impl: DriveClient): DriveUploader

    @Binds
    @Singleton
    abstract fun bindDriveBackups(impl: DriveClient): DriveBackups

    @Binds
    @Singleton
    abstract fun bindDriveMedia(impl: DriveClient): DriveMedia

    @Binds
    @IntoSet
    abstract fun bindTransactionIndexSource(impl: TransactionIndexSource): IndexSource

    companion object {
        @Provides
        fun provideRecentCategoryIds(dao: TransactionDao) =
            RecentCategoryIds { accountId, limit -> dao.observeRecentCategoryIds(accountId, limit) }

        @Provides
        fun provideOriginalMessage(dao: RawMessageDao) =
            OriginalMessage { id -> dao.findById(id)?.body }

        @Provides
        fun provideStartBackfill(scheduler: IngestionScheduler) = StartBackfill { scheduler.backfill() }

        @Provides
        fun provideTeachRequest(
            scheduler: IngestionScheduler,
            preferences: PreferencesRepository,
        ) = TeachRequest { rawMessageId ->
            // No key is the kill switch, and this is the one place it is checked.
            if (preferences.preferences.first().geminiKey != null) scheduler.teach(rawMessageId)
        }

        @Provides
        fun provideRuleSuggester(client: GeminiClient): RuleSuggester = client

        @Provides
        fun provideMonthLimits(budgets: BudgetRepository) =
            MonthLimits { monthStart -> budgets.observeLimitsForMonth(monthStart) }
    }
}
