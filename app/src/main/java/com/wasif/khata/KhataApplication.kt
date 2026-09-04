package com.wasif.khata

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.wasif.khata.core.backup.BackupScheduler
import com.wasif.khata.core.data.SnapshotScheduler
import com.wasif.khata.core.data.repository.BudgetCarryOver
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.search.SearchIndexRebuild
import com.wasif.khata.feature.widget.CashWidgetProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class KhataApplication : Application(), Configuration.Provider {

    @Inject lateinit var seeder: DatabaseSeeder

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var preferences: PreferencesRepository

    @Inject lateinit var snapshots: SnapshotScheduler

    @Inject lateinit var backups: BackupScheduler

    @Inject lateinit var budgetCarryOver: BudgetCarryOver

    @Inject lateinit var searchIndexRebuild: SearchIndexRebuild

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { seeder.seedIfEmpty() }

        // Moves a pre-existing global budget into a category row so nothing the user
        // set is silently dropped by the change to per-category limits.
        applicationScope.launch { budgetCarryOver.runIfNeeded() }

        // Rebuilds search_fts once, so rows carry the merchant names and aliases the
        // migration's SQL backfill could not reach. Off the main thread and behind
        // the ledger appearing: search is a little incomplete for a moment on the
        // one launch that does this, which beats blocking start-up on it.
        applicationScope.launch { searchIndexRebuild.runIfNeeded() }

        // The widget is RemoteViews, so it does not observe DataStore the way
        // Glance would. The theme can only be changed from Settings, which means
        // this process is alive whenever it changes, so nothing is missed.
        applicationScope.launch {
            preferences.preferences
                .map { it.themeSpec }
                .distinctUntilChanged()
                .collect { CashWidgetProvider.refresh(this@KhataApplication, it) }
        }

        // Enqueueing touches no permission check and no platform state, so unlike the
        // first-launch SMS backfill this is safe to do from the Application. The first
        // run doubles as the backfill over existing history.
        snapshots.scheduleNightly()
        backups.scheduleNightly()
    }
}
