package com.wasif.khata.feature.vehicle

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.repository.ENTITY_VEHICLE_SERVICE
import com.wasif.khata.core.data.repository.ItemDraft
import com.wasif.khata.core.data.repository.ServiceDraft
import com.wasif.khata.core.data.repository.VehicleRepository
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
@HiltViewModel(assistedFactory = ServiceEditorViewModel.Factory::class)
class ServiceEditorViewModel @AssistedInject constructor(
    private val repository: VehicleRepository,
    private val mediaStore: MediaStore,
    private val clock: KhataClock,
    @Assisted private val serviceId: Long?,
) : ViewModel(), ServiceEditorActions {

    @AssistedFactory
    interface Factory {
        fun create(serviceId: Long?): ServiceEditorViewModel
    }

    private val _state = MutableStateFlow(
        ServiceEditorUiState(servicedAt = clock.now(), isEditing = serviceId != null),
    )
    val state: StateFlow<ServiceEditorUiState> = _state.asStateFlow()

    // Channel, not SharedFlow: an effect emitted while the screen is backgrounded
    // buffers and replays on resume instead of being dropped.
    private val _effects = Channel<ServiceEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        serviceId?.let { id -> viewModelScope.launch { load(id) } }
    }

    private suspend fun load(id: Long) {
        val service = repository.findService(id) ?: return
        val items = repository.items(id)
        val workshop = service.placeId?.let { repository.place(it) }
        val photos = repository.photosFor(id).map { media ->
            PhotoItem(model = mediaStore.fileFor(media.sha256).toURI().toString(), mediaId = media.id)
        }

        _state.update {
            it.copy(
                servicedAt = service.servicedAt,
                odometerInput = service.odometerKm?.toString().orEmpty(),
                workshopInput = workshop?.name.orEmpty(),
                placeId = service.placeId,
                costInput = service.costMinor?.let { minor -> Money(minor).format(withSymbol = false) }
                    .orEmpty(),
                items = items
                    .map { item ->
                        ItemRow(
                            name = item.name,
                            costInput = item.costMinor
                                ?.let { minor -> Money(minor).format(withSymbol = false) }
                                .orEmpty(),
                        )
                    }
                    .ifEmpty { listOf(ItemRow()) },
                photos = photos,
                noteInput = service.note.orEmpty(),
            )
        }
    }

    override fun onDateChange(millis: Long) = _state.update { it.copy(servicedAt = millis) }

    override fun onOdometerChange(value: String) = _state.update { it.copy(odometerInput = value) }

    /**
     * Editing the workshop drops the resolved id: the text is the truth, and a stale
     * id would silently attach the job to whichever place happened to be picked first.
     */
    override fun onWorkshopChange(value: String) {
        _state.update { it.copy(workshopInput = value, placeId = null) }
        viewModelScope.launch {
            val suggestions = repository.workshopSuggestions(value).map { it.name }
                .filterNot { it.equals(value.trim(), ignoreCase = true) }
            _state.update { if (it.workshopInput == value) it.copy(suggestions = suggestions) else it }
        }
    }

    override fun onSuggestionPicked(name: String) {
        _state.update { it.copy(workshopInput = name, suggestions = emptyList()) }
        viewModelScope.launch {
            val existing = repository.workshopSuggestions(name).firstOrNull { it.name.equals(name, true) }
            _state.update { if (it.workshopInput == name) it.copy(placeId = existing?.id) else it }
        }
    }

    override fun onCostChange(value: String) = _state.update { it.copy(costInput = value) }

    override fun onItemNameChange(index: Int, value: String) = _state.update { state ->
        state.copy(items = state.items.mapIndexed { i, row -> if (i == index) row.copy(name = value) else row })
    }

    override fun onItemCostChange(index: Int, value: String) = _state.update { state ->
        state.copy(items = state.items.mapIndexed { i, row -> if (i == index) row.copy(costInput = value) else row })
    }

    override fun onItemRemoved(index: Int) = _state.update { state ->
        // Never down to nothing: an empty row is the invitation to add one, so
        // removing the last would leave the section with no way back into it.
        val remaining = state.items.filterIndexed { i, _ -> i != index }
        state.copy(items = remaining.ifEmpty { listOf(ItemRow()) })
    }

    override fun onItemAdded() = _state.update { it.copy(items = it.items + ItemRow()) }

    override fun onPhotosPicked(uris: List<String>) = _state.update { state ->
        val known = state.photos.map { it.model }.toSet()
        state.copy(photos = state.photos + uris.filterNot { it in known }.map { PhotoItem(it) })
    }

    override fun onPhotoRemoved(model: String) {
        val photo = _state.value.photos.firstOrNull { it.model == model } ?: return
        _state.update { it.copy(photos = it.photos.filterNot { p -> p.model == model }) }
        val mediaId = photo.mediaId
        val id = serviceId
        // Only a photo already linked has anything to detach; a pending one has not
        // been imported yet, so dropping it from the list is the whole of removing it.
        if (mediaId != null && id != null) {
            viewModelScope.launch { mediaStore.detach(mediaId, ENTITY_VEHICLE_SERVICE, id) }
        }
    }

    override fun onNoteChange(value: String) = _state.update { it.copy(noteInput = value) }

    override fun onSave() {
        val state = _state.value
        if (!state.canSave) return
        _state.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            runCatching {
                val vehicleId = repository.findOrCreateVehicle()
                // findOrCreate is what keeps entry quick: type a workshop that matches
                // nothing and the place arrives with the job.
                val placeId = state.placeId
                    ?: state.workshopInput.takeIf { it.isNotBlank() }
                        ?.let { repository.findOrCreateWorkshop(it) }

                val savedId = repository.saveService(
                    ServiceDraft(
                        id = serviceId ?: 0,
                        vehicleId = vehicleId,
                        servicedAt = state.servicedAt,
                        odometerKm = state.odometerKm,
                        placeId = placeId,
                        costMinor = state.cost?.minor,
                        note = state.noteInput,
                    ),
                    items = state.items
                        .filter { it.name.isNotBlank() }
                        .map { ItemDraft(it.name, it.cost?.minor) },
                )

                // The dash reading is the car's as well as the job's, and the newest
                // one is the current one. An older reading entered later must not
                // wind the odometer back.
                repository.vehicle()?.let { vehicle ->
                    val reading = state.odometerKm
                    if (reading != null && reading > (vehicle.odometerKm ?: 0)) {
                        repository.saveVehicle(vehicle.copy(odometerKm = reading))
                    }
                }

                // Imported only now: until the service exists there is nothing for the
                // media link to point at.
                state.photos.filter { it.isPending }.forEach { photo ->
                    mediaStore.import(Uri.parse(photo.model), ENTITY_VEHICLE_SERVICE, savedId)
                }
            }.fold(
                onSuccess = {
                    _state.update { it.copy(isSaving = false) }
                    _effects.trySend(ServiceEditorEffect.Saved)
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
        val id = serviceId ?: return
        viewModelScope.launch {
            repository.deleteService(id)
            _effects.trySend(ServiceEditorEffect.Deleted)
        }
    }
}
