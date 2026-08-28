package com.wasif.khata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val preferences: KhataPreferences) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    repository: PreferencesRepository,
) : ViewModel() {

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
