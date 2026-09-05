package com.wasif.khata.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.PlaceDao
import com.wasif.khata.core.data.dao.RestaurantDao
import com.wasif.khata.core.data.dao.SearchDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.dao.VehicleDao
import com.wasif.khata.core.data.dao.NoteDao
import com.wasif.khata.core.data.dao.WatchlistDao
import com.wasif.khata.core.data.repository.ENTITY_RESTAURANT
import com.wasif.khata.core.data.repository.ENTITY_NOTE
import com.wasif.khata.core.data.repository.ENTITY_TITLE
import com.wasif.khata.core.data.repository.ENTITY_VEHICLE_SERVICE
import com.wasif.khata.core.data.repository.toDomain
import com.wasif.khata.core.search.ftsQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TYPING_PAUSE_MS = 180L

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchDao: SearchDao,
    private val transactions: TransactionDao,
    private val restaurants: RestaurantDao,
    private val vehicles: VehicleDao,
    private val titles: WatchlistDao,
    private val notes: NoteDao,
    private val places: PlaceDao,
) : ViewModel(), SearchActions {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state
                .map { it.query }
                .distinctUntilChanged()
                // Dropped rather than debounced away: the first value is the empty
                // box this screen opens on, and running a search for it would be
                // three queries answering nothing.
                .drop(1)
                .debounce(TYPING_PAUSE_MS)
                .collect { raw -> search(raw) }
        }
    }

    override fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    private suspend fun search(raw: String) {
        // Null means the box holds nothing searchable -- punctuation, or whitespace.
        // Emit empty without touching the database.
        val query = ftsQuery(raw) ?: run {
            _state.update {
                it.copy(
                    transactions = emptyList(),
                    restaurants = emptyList(),
                    services = emptyList(),
                    titles = emptyList(),
                    notes = emptyList(),
                    places = emptyList(),
                    searching = false,
                )
            }
            return
        }

        _state.update { it.copy(searching = true) }

        val foundTransactions = transactions
            .findByIds(searchDao.idsMatching("transaction", query))
            .map { it.toDomain() }
        val foundRestaurants = restaurants.summariesByIds(searchDao.idsMatching(ENTITY_RESTAURANT, query))
        val foundServices = vehicles.summariesByIds(searchDao.idsMatching(ENTITY_VEHICLE_SERVICE, query))
        val foundTitles = titles.summariesByIds(searchDao.idsMatching(ENTITY_TITLE, query))
        val foundNotes = notes.findByIds(searchDao.idsMatching(ENTITY_NOTE, query))
        val foundPlaces = places.findByIds(searchDao.idsMatching("place", query))

        _state.update {
            // Guard against a slow query landing after the box has moved on.
            if (it.query != raw) {
                it
            } else {
                it.copy(
                    transactions = foundTransactions,
                    restaurants = foundRestaurants,
                    services = foundServices,
                    titles = foundTitles,
                    notes = foundNotes,
                    places = foundPlaces,
                    searching = false,
                )
            }
        }
    }
}
