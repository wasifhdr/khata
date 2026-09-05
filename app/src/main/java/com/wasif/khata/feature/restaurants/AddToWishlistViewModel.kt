package com.wasif.khata.feature.restaurants

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.repository.RestaurantRepository
import com.wasif.khata.core.place.ParsedPlace
import com.wasif.khata.core.place.PlaceRepository
import com.wasif.khata.core.place.parseSharedPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddToWishlistUiState(
    val nameInput: String = "",
    val noteInput: String = "",
    val recommenderInput: String = "",
    val recommenders: List<String> = emptyList(),
    /** What was shared, if anything. Shown so the user can see what was understood. */
    val place: ParsedPlace? = null,
    val isSaving: Boolean = false,
) {
    val canSave: Boolean get() = nameInput.isNotBlank() && !isSaving
}

@Stable
interface AddToWishlistActions {
    fun onSharedText(text: String)
    fun onNameChange(value: String)
    fun onNoteChange(value: String)
    fun onRecommenderInputChange(value: String)
    fun onRecommenderAdded()
    fun onRecommenderRemoved(name: String)
    fun onSave()
}

/**
 * Where a place shared out of Maps lands.
 *
 * The wishlist rather than the visit editor, because somewhere someone sends you is
 * usually somewhere you have not been yet -- and "log a visit instead" is one tap
 * away for the times it is not.
 */
@HiltViewModel
class AddToWishlistViewModel @Inject constructor(
    private val restaurants: RestaurantRepository,
    private val places: PlaceRepository,
) : ViewModel(), AddToWishlistActions {

    private val _state = MutableStateFlow(AddToWishlistUiState())
    val state: StateFlow<AddToWishlistUiState> = _state.asStateFlow()

    private val _effects = Channel<Long>(Channel.BUFFERED)

    /** The restaurant that was created, so the caller can open it. */
    val saved = _effects.receiveAsFlow()

    /**
     * Applied once. The screen re-runs its LaunchedEffect on configuration change,
     * and a second application would overwrite a name the user had already corrected.
     */
    override fun onSharedText(text: String) {
        if (_state.value.place != null || _state.value.nameInput.isNotBlank()) return
        val parsed = parseSharedPlace(text) ?: return
        _state.update {
            it.copy(place = parsed, nameInput = parsed.name?.takeIf { n -> n.isNotBlank() }.orEmpty())
        }
    }

    override fun onNameChange(value: String) = _state.update { it.copy(nameInput = value) }

    override fun onNoteChange(value: String) = _state.update { it.copy(noteInput = value) }

    override fun onRecommenderInputChange(value: String) =
        _state.update { it.copy(recommenderInput = value) }

    override fun onRecommenderAdded() = _state.update { state ->
        val name = state.recommenderInput.trim()
        if (name.isBlank() || state.recommenders.any { it.equals(name, ignoreCase = true) }) {
            state.copy(recommenderInput = "")
        } else {
            state.copy(recommenders = state.recommenders + name, recommenderInput = "")
        }
    }

    override fun onRecommenderRemoved(name: String) = _state.update {
        it.copy(recommenders = it.recommenders.filterNot { existing -> existing == name })
    }

    override fun onSave() {
        val state = _state.value
        if (!state.canSave) return
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            // A restaurant with no visits is the whole of "want to try": there is no
            // flag to set, and the first visit logged promotes it on its own.
            val restaurantId = restaurants.findOrCreate(state.nameInput)
            val placeId = state.place?.let { places.save(it) }
            restaurants.setDetails(restaurantId, placeId = placeId, note = state.noteInput)
            state.recommenders.forEach { restaurants.addRecommender(restaurantId, it) }

            _state.update { it.copy(isSaving = false) }
            _effects.trySend(restaurantId)
        }
    }
}
