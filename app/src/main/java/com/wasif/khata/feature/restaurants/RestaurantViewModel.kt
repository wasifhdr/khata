package com.wasif.khata.feature.restaurants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.RestaurantSummary
import com.wasif.khata.core.data.entity.MediaEntity
import com.wasif.khata.core.data.repository.ENTITY_RESTAURANT_VISIT
import com.wasif.khata.core.data.repository.RestaurantRepository
import com.wasif.khata.core.media.MediaStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = RestaurantViewModel.Factory::class)
class RestaurantViewModel @AssistedInject constructor(
    private val repository: RestaurantRepository,
    private val mediaStore: MediaStore,
    @Assisted private val restaurantId: Long,
) : ViewModel(), RestaurantActions {

    @AssistedFactory
    interface Factory {
        fun create(restaurantId: Long): RestaurantViewModel
    }

    private val _state = MutableStateFlow(RestaurantUiState())
    val state: StateFlow<RestaurantUiState> = _state.asStateFlow()

    init {
        // The summary is a Flow, so a visit logged elsewhere lands here without a
        // refresh. Its dishes, companions and photos are read alongside each emission
        // rather than observed separately: three more Flows to keep four lists in step
        // is the sync problem this module has otherwise avoided everywhere.
        viewModelScope.launch {
            repository.observeSummary(restaurantId).collect { summary -> load(summary) }
        }
    }

    private suspend fun load(summary: RestaurantSummary?) {
        if (summary == null) return
        val visits = repository.visits(restaurantId).map { visit ->
            VisitCard(
                id = visit.id,
                visitedAt = visit.visitedAt,
                ambianceRating = visit.ambianceRating,
                costMinor = visit.costMinor,
                note = visit.note,
                dishes = repository.dishesFor(visit.id).map { it.name to it.rating },
                companions = repository.companionsFor(visit.id).map { it.name },
                photos = repository.photosFor(ENTITY_RESTAURANT_VISIT, visit.id).map(::photo),
            )
        }

        _state.update {
            it.copy(
                summary = summary,
                coverModel = summary.coverSha?.let { sha -> mediaStore.fileFor(sha).toURI().toString() },
                visits = visits,
                recommenders = repository.recommendersFor(restaurantId).map { tag -> tag.name },
                allPhotos = repository.photosAcross(restaurantId).map(::photo),
            )
        }
    }

    private fun photo(media: MediaEntity) =
        Photo(mediaId = media.id, model = mediaStore.fileFor(media.sha256).toURI().toString())

    override fun onSetCoverRequested() = _state.update { it.copy(pickingCover = true) }

    override fun onCoverDismissed() = _state.update { it.copy(pickingCover = false) }

    /**
     * A pointer at a row that already exists. The photo stays exactly where it was --
     * one file, one media row, and now one more thing referring to it.
     */
    override fun onCoverPicked(mediaId: Long) {
        _state.update { it.copy(pickingCover = false) }
        viewModelScope.launch { repository.setCover(restaurantId, mediaId) }
    }
}
