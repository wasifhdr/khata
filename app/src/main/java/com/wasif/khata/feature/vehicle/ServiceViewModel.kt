package com.wasif.khata.feature.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.ServiceSummary
import com.wasif.khata.core.data.entity.ServiceItemEntity
import com.wasif.khata.core.data.repository.VehicleRepository
import com.wasif.khata.core.media.MediaStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ServiceDetailUiState(
    val summary: ServiceSummary? = null,
    val items: List<ServiceItemEntity> = emptyList(),
    val photoModels: List<String> = emptyList(),
    val mapsUrl: String? = null,
)

// Hilt needs the assisted factory named here to resolve hiltViewModel's generic
// <VM, VMF> overload; without it, injection silently falls back to a no-arg
// constructor and crashes at runtime.
@HiltViewModel(assistedFactory = ServiceViewModel.Factory::class)
class ServiceViewModel @AssistedInject constructor(
    private val repository: VehicleRepository,
    private val mediaStore: MediaStore,
    @Assisted private val serviceId: Long,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(serviceId: Long): ServiceViewModel
    }

    val state: StateFlow<ServiceDetailUiState> = combine(
        repository.observeService(serviceId),
        repository.observeItems(serviceId),
    ) { summary, items ->
        ServiceDetailUiState(
            summary = summary,
            items = items,
            photoModels = repository.photosFor(serviceId)
                .map { mediaStore.fileFor(it.sha256).toURI().toString() },
            mapsUrl = summary?.mapsUrl,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServiceDetailUiState(),
    )
}
