package com.wasif.khata.feature.restaurants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.RestaurantSummary
import com.wasif.khata.core.data.repository.RestaurantRepository
import com.wasif.khata.core.media.MediaStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RestaurantsViewModel @Inject constructor(
    repository: RestaurantRepository,
    private val mediaStore: MediaStore,
) : ViewModel() {

    val state: StateFlow<RestaurantsUiState> = combine(
        repository.observeBeen(),
        repository.observeWishlist(),
    ) { been, wishlist ->
        RestaurantsUiState(been = been.map(::card), wishlist = wishlist.map(::card))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RestaurantsUiState(),
    )

    private fun card(summary: RestaurantSummary) = RestaurantCard(
        summary = summary,
        coverModel = summary.coverSha?.let { mediaStore.fileFor(it).toURI().toString() },
    )
}
