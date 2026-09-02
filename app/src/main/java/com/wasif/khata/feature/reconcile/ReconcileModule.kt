package com.wasif.khata.feature.reconcile

import com.wasif.khata.core.data.repository.ReconciliationRepository
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ReconcileModule {

    @Provides
    fun provideDriftSource(repository: ReconciliationRepository): DriftSource =
        DriftSource { repository.observeDrift() }

    @Provides
    fun provideAdjustmentRecorder(repository: TransactionRepository): AdjustmentRecorder =
        AdjustmentRecorder { accountId, gap, occurredAt ->
            repository.recordUnexplained(adjustmentDraft(accountId, gap, occurredAt))
        }
}
