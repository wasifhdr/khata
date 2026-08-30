package com.wasif.khata.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.FieldPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PreferencesRepository,
) : ViewModel() {

    val state: StateFlow<KhataPreferences> = repository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KhataPreferences.Default,
    )

    // Each setter edits one axis and carries the other three forward. The tuner
    // is four independent controls over one value.
    fun onFieldSelected(field: FieldPalette) = save { it.copy(field = field) }

    fun onGroundSelected(ground: Color) = save { it.copy(ground = ground) }

    fun onAccentSelected(accent: Color) = save { it.copy(accent = accent) }

    fun onIntensitySelected(intensity: FieldIntensity) = save { it.copy(intensity = intensity) }

    fun onResetTheme() = viewModelScope.launch { repository.resetTheme() }

    /**
     * Wired to the HOME VIEW control in Settings since Task 6. Writing here
     * only changes what `KhataNavHost` resolves as its start destination on the
     * *next* process launch (I6) — it freezes that value for the lifetime of
     * the current one, because rebuilding the graph under a live back stack
     * would pop it and eject the user out of Settings mid-interaction. The
     * supporting text under HOME VIEW says as much.
     */
    fun onHomeViewSelected(view: HomeView) = viewModelScope.launch { repository.setHomeView(view) }

    fun onMonthlyBudgetChanged(minor: Long?) = viewModelScope.launch {
        repository.setMonthlyBudget(minor)
    }

    // Reads the store rather than state.value. `state` is WhileSubscribed, so
    // with no collector it never leaves its initial value -- and a save made
    // before the first collection would then overwrite the stored theme with
    // the default plus one edited axis.
    private fun save(edit: (ThemeSpec) -> ThemeSpec) = viewModelScope.launch {
        repository.setTheme(edit(repository.preferences.first().themeSpec))
    }
}
