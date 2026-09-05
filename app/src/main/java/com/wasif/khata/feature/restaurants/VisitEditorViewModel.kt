package com.wasif.khata.feature.restaurants

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.repository.DishDraft
import com.wasif.khata.core.data.repository.ENTITY_RESTAURANT_VISIT
import com.wasif.khata.core.data.repository.RestaurantRepository
import com.wasif.khata.core.data.repository.VisitDraft
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.KhataClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Hilt needs the assisted factory named here to resolve hiltViewModel's generic
// <VM, VMF> overload; without it, injection silently falls back to a no-arg
// constructor and crashes at runtime.
@HiltViewModel(assistedFactory = VisitEditorViewModel.Factory::class)
class VisitEditorViewModel @AssistedInject constructor(
    private val repository: RestaurantRepository,
    private val mediaStore: MediaStore,
    private val clock: KhataClock,
    @Assisted("visitId") private val visitId: Long?,
    @Assisted("restaurantId") private val prefilledRestaurantId: Long?,
) : ViewModel(), VisitEditorActions {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("visitId") visitId: Long?,
            @Assisted("restaurantId") restaurantId: Long?,
        ): VisitEditorViewModel
    }

    private val _state = MutableStateFlow(
        VisitEditorUiState(visitedAt = clock.now(), isEditing = visitId != null),
    )
    val state: StateFlow<VisitEditorUiState> = _state.asStateFlow()

    // Channel, not SharedFlow: an effect emitted while the screen is backgrounded
    // buffers and replays on resume instead of being dropped.
    private val _effects = Channel<VisitEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        prefilledRestaurantId?.let { id ->
            repository.find(id)?.let { restaurant ->
                _state.update { it.copy(nameInput = restaurant.name, restaurantId = restaurant.id) }
            }
        }

        visitId?.let { loadVisit(it) }
    }

    private suspend fun loadVisit(id: Long) {
        val dishes = repository.dishesFor(id)
        val companions = repository.companionsFor(id).map { it.name }
        val photos = repository.photosFor(ENTITY_RESTAURANT_VISIT, id).map { media ->
            PhotoItem(model = mediaStore.fileFor(media.sha256).toURI().toString(), mediaId = media.id)
        }
        val visit = repository.visit(id) ?: return
        val restaurant = repository.find(visit.restaurantId)

        _state.update {
            it.copy(
                nameInput = restaurant?.name.orEmpty(),
                restaurantId = visit.restaurantId,
                visitedAt = visit.visitedAt,
                ambianceRating = visit.ambianceRating,
                costInput = visit.costMinor?.let { minor -> Money(minor).format(withSymbol = false) }
                    .orEmpty(),
                dishes = dishes.map { d -> DishRow(d.name, d.rating) }.ifEmpty { listOf(DishRow()) },
                companions = companions,
                photos = photos,
                noteInput = visit.note.orEmpty(),
            )
        }
    }

    /**
     * Editing the name drops the resolved id: the text is the truth, and a stale id
     * would silently attach the visit to whichever row happened to be picked before.
     */
    override fun onNameChange(value: String) {
        _state.update { it.copy(nameInput = value, restaurantId = null) }
        viewModelScope.launch {
            val suggestions = repository.suggestions(value).map { it.name }
                .filterNot { it.equals(value.trim(), ignoreCase = true) }
            _state.update { if (it.nameInput == value) it.copy(suggestions = suggestions) else it }
        }
    }

    override fun onSuggestionPicked(name: String) {
        _state.update { it.copy(nameInput = name, suggestions = emptyList()) }
        viewModelScope.launch {
            val existing = repository.suggestions(name).firstOrNull { it.name.equals(name, true) }
            _state.update { if (it.nameInput == name) it.copy(restaurantId = existing?.id) else it }
        }
    }

    override fun onDateChange(millis: Long) = _state.update { it.copy(visitedAt = millis) }

    override fun onAmbianceChange(rating: Int?) = _state.update { it.copy(ambianceRating = rating) }

    override fun onCostChange(value: String) = _state.update { it.copy(costInput = value) }

    override fun onDishNameChange(index: Int, value: String) = _state.update { state ->
        state.copy(dishes = state.dishes.mapIndexed { i, d -> if (i == index) d.copy(name = value) else d })
    }

    override fun onDishRatingChange(index: Int, rating: Int?) = _state.update { state ->
        state.copy(dishes = state.dishes.mapIndexed { i, d -> if (i == index) d.copy(rating = rating) else d })
    }

    override fun onDishRemoved(index: Int) = _state.update { state ->
        // Never down to nothing: an empty row is the invitation to add one, so
        // removing the last would leave the section with no way back into it.
        val remaining = state.dishes.filterIndexed { i, _ -> i != index }
        state.copy(dishes = remaining.ifEmpty { listOf(DishRow()) })
    }

    override fun onDishAdded() = _state.update { it.copy(dishes = it.dishes + DishRow()) }

    override fun onCompanionInputChange(value: String) =
        _state.update { it.copy(companionInput = value) }

    override fun onCompanionAdded() = _state.update { state ->
        val name = state.companionInput.trim()
        if (name.isBlank() || state.companions.any { it.equals(name, ignoreCase = true) }) {
            state.copy(companionInput = "")
        } else {
            state.copy(companions = state.companions + name, companionInput = "")
        }
    }

    override fun onCompanionRemoved(name: String) = _state.update {
        it.copy(companions = it.companions.filterNot { existing -> existing == name })
    }

    override fun onPhotosPicked(uris: List<String>) = _state.update { state ->
        val known = state.photos.map { it.model }.toSet()
        state.copy(photos = state.photos + uris.filterNot { it in known }.map { PhotoItem(it) })
    }

    override fun onPhotoRemoved(model: String) {
        val photo = _state.value.photos.firstOrNull { it.model == model } ?: return
        _state.update { it.copy(photos = it.photos.filterNot { p -> p.model == model }) }
        val mediaId = photo.mediaId
        val id = visitId
        // Only a photo already linked has anything to detach; a pending one has not
        // been imported yet, so dropping it from the list is the whole of removing it.
        if (mediaId != null && id != null) {
            viewModelScope.launch { mediaStore.detach(mediaId, ENTITY_RESTAURANT_VISIT, id) }
        }
    }

    override fun onNoteChange(value: String) = _state.update { it.copy(noteInput = value) }

    override fun onSave() {
        val state = _state.value
        if (!state.canSave) return
        _state.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            runCatching {
                // findOrCreate is what makes entry visit-first: type a name that
                // matches nothing and the restaurant arrives with the dinner.
                val restaurantId = state.restaurantId ?: repository.findOrCreate(state.nameInput)
                val savedVisitId = repository.saveVisit(
                    VisitDraft(
                        id = visitId ?: 0,
                        restaurantId = restaurantId,
                        visitedAt = state.visitedAt,
                        ambianceRating = state.ambianceRating,
                        costMinor = state.cost?.minor,
                        note = state.noteInput,
                    ),
                    dishes = state.dishes
                        .filter { it.name.isNotBlank() }
                        .map { DishDraft(it.name, it.rating) },
                    companions = state.companions,
                )

                // Imported only now: until the visit exists there is nothing for the
                // media link to point at.
                state.photos.filter { it.isPending }.forEach { photo ->
                    mediaStore.import(Uri.parse(photo.model), ENTITY_RESTAURANT_VISIT, savedVisitId)
                }
            }.fold(
                onSuccess = {
                    _state.update { it.copy(isSaving = false) }
                    _effects.trySend(VisitEditorEffect.Saved)
                },
                onFailure = {
                    _state.update {
                        it.copy(isSaving = false, saveError = "Could not save. Please try again.")
                    }
                },
            )
        }
    }

    override fun onDelete() {
        val id = visitId ?: return
        viewModelScope.launch {
            repository.deleteVisit(id)
            _effects.trySend(VisitEditorEffect.Deleted)
        }
    }
}
