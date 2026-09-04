package com.wasif.khata.feature.search

import androidx.compose.runtime.Stable
import com.wasif.khata.core.data.dao.RestaurantSummary
import com.wasif.khata.core.data.dao.ServiceSummary
import com.wasif.khata.core.data.dao.TitleSummary
import com.wasif.khata.core.data.entity.PlaceEntity
import com.wasif.khata.domain.model.Transaction

data class SearchUiState(
    val query: String = "",
    val transactions: List<Transaction> = emptyList(),
    val restaurants: List<RestaurantSummary> = emptyList(),
    val services: List<ServiceSummary> = emptyList(),
    val titles: List<TitleSummary> = emptyList(),
    val places: List<PlaceEntity> = emptyList(),
    val searching: Boolean = false,
) {
    /**
     * Null from ftsQuery means there is nothing to search for. An empty box shows
     * nothing, not everything -- a global search that answers with the whole ledger
     * before you have typed anything is not a hunt.
     */
    val hasQuery: Boolean get() = query.isNotBlank()

    val isEmpty: Boolean
        get() = transactions.isEmpty() && restaurants.isEmpty() && services.isEmpty() &&
            titles.isEmpty() && places.isEmpty()

    val total: Int get() = transactions.size + restaurants.size + services.size + titles.size + places.size
}

@Stable
interface SearchActions {
    fun onQueryChange(value: String)
}
