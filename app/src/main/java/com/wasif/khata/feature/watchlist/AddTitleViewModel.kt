package com.wasif.khata.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.data.repository.ENTITY_TITLE
import com.wasif.khata.core.data.repository.TitleDraft
import com.wasif.khata.core.data.repository.WatchDraft
import com.wasif.khata.core.data.repository.WatchlistRepository
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.watch.Tmdb
import com.wasif.khata.core.watch.TmdbResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TYPING_PAUSE_MS = 300L

@OptIn(FlowPreview::class)
@HiltViewModel
class AddTitleViewModel @Inject constructor(
    private val repository: WatchlistRepository,
    private val tmdb: Tmdb,
    private val mediaStore: MediaStore,
    private val clock: KhataClock,
) : ViewModel(), AddTitleActions {

    private val _state = MutableStateFlow(AddTitleUiState())
    val state: StateFlow<AddTitleUiState> = _state.asStateFlow()

    private val _effects = Channel<AddTitleEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state
                .map { it.query }
                .distinctUntilChanged()
                // Dropped rather than debounced away: the first value is the empty box
                // this screen opens on, and searching for it would be a call answering
                // nothing. Longer than the ledger's pause because this one leaves the
                // device.
                .drop(1)
                .debounce(TYPING_PAUSE_MS)
                .collect { raw -> search(raw) }
        }
    }

    private suspend fun search(raw: String) {
        if (raw.isBlank()) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        // Empty covers no key, no network, a non-200 and nothing found alike. All four
        // leave the manual fields exactly where they already are.
        val found = tmdb.search(raw)
        _state.update { if (it.query == raw) it.copy(results = found) else it }
    }

    override fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    /** Fills the manual fields in rather than replacing them, so anything can be edited after. */
    override fun onResultPick(result: TmdbResult) = _state.update {
        it.copy(
            picked = result,
            name = result.name,
            yearInput = result.year?.toString().orEmpty(),
            kind = result.kind,
            results = emptyList(),
            query = "",
        )
    }

    override fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    override fun onYearChange(value: String) = _state.update { it.copy(yearInput = value) }

    override fun onKindChange(kind: TitleKind) = _state.update { it.copy(kind = kind) }

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

    override fun onLogWatchNowChange(value: Boolean) = _state.update { it.copy(logWatchNow = value) }

    override fun onSave() {
        val state = _state.value
        if (!state.canSave) return
        _state.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            runCatching {
                val now = clock.now()
                // The picked result only counts when the name still belongs to it: an
                // edited name is a different title, and carrying the poster and rating
                // over would attach one film's picture to another's row.
                val picked = state.picked?.takeIf { it.name == state.name }

                val id = repository.saveTitle(
                    TitleDraft(
                        name = state.name,
                        year = state.year,
                        kind = state.kind,
                        tmdbId = picked?.tmdbId,
                        tmdbRating = picked?.rating,
                        tmdbRatingAt = picked?.rating?.let { now },
                        note = state.noteInput,
                    ),
                    recommenders = state.recommenders,
                )

                // Fetched only now: until the title exists there is nothing for the
                // media link to point at.
                picked?.posterPath?.let { path ->
                    tmdb.poster(path)?.let { bytes ->
                        mediaStore.importBytes(bytes, ENTITY_TITLE, id, sourceUrl = path)
                            ?.let { repository.setPoster(id, it.id) }
                    }
                }

                if (state.logWatchNow) {
                    repository.logWatch(WatchDraft(titleId = id, watchedAt = now))
                }
            }.fold(
                onSuccess = {
                    _state.update { it.copy(isSaving = false) }
                    _effects.trySend(AddTitleEffect.Saved)
                },
                onFailure = {
                    _state.update {
                        it.copy(isSaving = false, saveError = "Could not save. Please try again.")
                    }
                },
            )
        }
    }
}
