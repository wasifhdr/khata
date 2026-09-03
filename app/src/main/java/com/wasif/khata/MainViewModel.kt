package com.wasif.khata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.sms.StartBackfill
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val preferences: KhataPreferences) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: PreferencesRepository,
    private val startBackfill: StartBackfill,
) : ViewModel() {

    /**
     * Spec 10: backfill runs on first launch as well as on demand, or a fresh install
     * shows an empty app until the user finds the button.
     *
     * [isGranted] is passed in rather than read here, because the permission check
     * needs a real ActivityThread -- an application-scope collector doing it on a
     * background dispatcher throws, which is how this arrived in MainActivity. The
     * flag is the guard, not an empty ledger: no transactions is also the honest
     * state of an inbox holding no bank messages, and testing for that would rescan
     * everything on every launch, forever.
     */
    fun onPermissionKnown(isGranted: Boolean) {
        if (!isGranted) return
        viewModelScope.launch {
            if (repository.preferences.first().hasBackfilled) return@launch
            startBackfill()
            repository.setBackfilled()
        }
    }

    // Loading is a real state, not a formality. The palette and the start
    // destination both come from DataStore, and painting either one wrong for a
    // frame is visible: the palette flashes, and the start destination is the
    // back-stack root so it cannot be corrected after the graph is built.
    val state: StateFlow<MainUiState> = repository.preferences
        .map<KhataPreferences, MainUiState> { MainUiState.Ready(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MainUiState.Loading,
        )
}
