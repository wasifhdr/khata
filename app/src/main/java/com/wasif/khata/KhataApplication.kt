package com.wasif.khata

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.prefs.PreferencesRepository
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { seeder.seedIfEmpty() }

        // The widget is RemoteViews, so it does not observe DataStore the way
        // Glance would. The theme can only be changed from Settings, which means
        // this process is alive whenever it changes, so nothing is missed.
        applicationScope.launch {
            preferences.preferences
                .map { it.themeSpec }
                .distinctUntilChanged()
                .collect { CashWidgetProvider.refresh(this@KhataApplication, it) }
        }
    }
}
