package com.wasif.khata.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : PreferencesRepository {

    private object Keys {
        val Field = intPreferencesKey("theme_field")
        val Ground = intPreferencesKey("theme_ground")
        val Accent = intPreferencesKey("theme_accent")
        val Intensity = stringPreferencesKey("theme_intensity")
        val Home = stringPreferencesKey("home_view")
        val Budget = longPreferencesKey("monthly_budget_minor")
        val BudgetSet = intPreferencesKey("monthly_budget_set")
    }

    override val preferences: Flow<KhataPreferences> = store.data
        // A corrupt or unreadable store must not take the app down before any UI
        // exists to report it. Defaults are always a valid answer here.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            KhataPreferences(
                themeSpec = ThemeSpec(
                    // getOrElse rather than get: a stored index can outlive the
                    // list it pointed into, and on first launch that would throw
                    // before any UI exists to report it.
                    field = KhataPalette.fields.getOrElse(p[Keys.Field] ?: -1) { ThemeSpec.Default.field },
                    ground = KhataPalette.grounds.getOrElse(p[Keys.Ground] ?: -1) { ThemeSpec.Default.ground },
                    accent = KhataPalette.accents.getOrElse(p[Keys.Accent] ?: -1) { ThemeSpec.Default.accent },
                    intensity = p[Keys.Intensity]
                        ?.let { name -> FieldIntensity.entries.firstOrNull { it.name == name } }
                        ?: ThemeSpec.Default.intensity,
                ),
                homeView = p[Keys.Home]
                    ?.let { name -> HomeView.entries.firstOrNull { it.name == name } }
                    ?: HomeView.Modules,
                // Two keys, because null and zero are different answers and one
                // Long cannot carry both.
                monthlyBudgetMinor = if (p[Keys.BudgetSet] == 1) p[Keys.Budget] ?: 0L else null,
            )
        }

    override suspend fun setTheme(spec: ThemeSpec) {
        store.edit { p ->
            p[Keys.Field] = KhataPalette.fields.indexOf(spec.field)
            p[Keys.Ground] = KhataPalette.grounds.indexOf(spec.ground)
            p[Keys.Accent] = KhataPalette.accents.indexOf(spec.accent)
            p[Keys.Intensity] = spec.intensity.name
        }
    }

    override suspend fun resetTheme() {
        // Clears the keys rather than writing today's default as an explicit
        // value, so a later change of default still reaches anyone who has
        // pressed reset.
        store.edit { p ->
            p.remove(Keys.Field)
            p.remove(Keys.Ground)
            p.remove(Keys.Accent)
            p.remove(Keys.Intensity)
        }
    }

    override suspend fun setHomeView(view: HomeView) {
        store.edit { it[Keys.Home] = view.name }
    }

    override suspend fun setMonthlyBudget(minor: Long?) {
        store.edit { p ->
            if (minor == null) {
                p.remove(Keys.Budget)
                p[Keys.BudgetSet] = 0
            } else {
                p[Keys.Budget] = minor
                p[Keys.BudgetSet] = 1
            }
        }
    }
}
