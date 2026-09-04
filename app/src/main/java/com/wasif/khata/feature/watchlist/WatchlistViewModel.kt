package com.wasif.khata.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.TitleSummary
import com.wasif.khata.core.data.repository.WatchlistRepository
import com.wasif.khata.core.media.MediaStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * A title as a card draws it. [posterModel] is the summary's content hash resolved to
 * a file Coil can load; everything else the query already derived.
 */
data class TitleCard(val summary: TitleSummary, val posterModel: String? = null) {
    val id: Long get() = summary.id
    val name: String get() = summary.name
}

data class WatchlistUiState(
    /** Titles with no watches. Derived, so nothing here was ever flagged. */
    val queue: List<TitleCard> = emptyList(),
    val watched: List<TitleCard> = emptyList(),
) {
    val isEmpty: Boolean get() = queue.isEmpty() && watched.isEmpty()
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    repository: WatchlistRepository,
    private val mediaStore: MediaStore,
) : ViewModel() {

    val state: StateFlow<WatchlistUiState> = combine(
        repository.observeQueue(),
        repository.observeWatched(),
    ) { queue, watched ->
        WatchlistUiState(queue = queue.map(::card), watched = watched.map(::card))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WatchlistUiState(),
    )

    private fun card(summary: TitleSummary) = TitleCard(
        summary = summary,
        posterModel = summary.posterSha?.let { mediaStore.fileFor(it).toURI().toString() },
    )
}
