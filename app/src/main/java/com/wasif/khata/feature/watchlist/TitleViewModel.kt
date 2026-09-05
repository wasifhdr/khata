package com.wasif.khata.feature.watchlist

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.TitleSummary
import com.wasif.khata.core.data.entity.WatchEntity
import com.wasif.khata.core.data.repository.WatchDraft
import com.wasif.khata.core.data.repository.WatchlistRepository
import com.wasif.khata.core.data.repository.needsRefresh
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.watch.Tmdb
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TitleUiState(
    val summary: TitleSummary? = null,
    val watches: List<WatchEntity> = emptyList(),
    val posterModel: String? = null,
    val recommenders: List<String> = emptyList(),
)

@Stable
interface TitleActions {
    fun onLogWatch(watchedAt: Long, rating: Int?, note: String)
    fun onDeleteWatch(watchId: Long)
}

// Hilt needs the assisted factory named here to resolve hiltViewModel's generic
// <VM, VMF> overload; without it, injection silently falls back to a no-arg
// constructor and crashes at runtime.
@HiltViewModel(assistedFactory = TitleViewModel.Factory::class)
class TitleViewModel @AssistedInject constructor(
    private val repository: WatchlistRepository,
    private val tmdb: Tmdb,
    private val mediaStore: MediaStore,
    private val clock: KhataClock,
    @Assisted private val titleId: Long,
) : ViewModel(), TitleActions {

    @AssistedFactory
    interface Factory {
        fun create(titleId: Long): TitleViewModel
    }

    val state: StateFlow<TitleUiState> = combine(
        repository.observeSummary(titleId),
        repository.observeWatches(titleId),
    ) { summary, watches ->
        TitleUiState(
            summary = summary,
            watches = watches,
            posterModel = summary?.posterSha?.let { mediaStore.fileFor(it).toURI().toString() },
            recommenders = repository.recommendersFor(titleId),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TitleUiState(),
    )

    init {
        viewModelScope.launch { refreshIfStale() }
    }

    /**
     * Refresh on open, gated at seven days. Ungated this is a network call every time
     * the user glances at a title; a rating that moves by hundredths in a week is not
     * worth one. A title with no tmdbId never calls out at all.
     *
     * On any failure the stored snapshot and its date are left exactly as they were:
     * a dated rating from last month is honest, and overwriting it with nothing is not.
     */
    private suspend fun refreshIfStale() {
        val title = repository.find(titleId) ?: return
        if (!needsRefresh(title.tmdbId, title.tmdbRatingAt, clock.now())) return
        val rating = tmdb.rating(title.tmdbId!!, title.name) ?: return
        repository.refreshRating(titleId, rating)
    }

    override fun onLogWatch(watchedAt: Long, rating: Int?, note: String) {
        viewModelScope.launch {
            repository.logWatch(
                WatchDraft(titleId = titleId, watchedAt = watchedAt, rating = rating, note = note),
            )
        }
    }

    override fun onDeleteWatch(watchId: Long) {
        viewModelScope.launch { repository.deleteWatch(watchId, titleId) }
    }
}
