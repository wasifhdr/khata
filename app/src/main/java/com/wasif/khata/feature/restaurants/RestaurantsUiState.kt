package com.wasif.khata.feature.restaurants

import com.wasif.khata.core.data.dao.RestaurantSummary

/**
 * A restaurant as a row draws it. [coverModel] is the summary's content hash resolved
 * to a file Coil can load; everything else is already derived by the query.
 */
data class RestaurantCard(
    val summary: RestaurantSummary,
    val coverModel: String? = null,
) {
    val id: Long get() = summary.id
    val name: String get() = summary.name
}

data class RestaurantsUiState(
    /** Ordered by recency, which is the order they are thought about in. */
    val been: List<RestaurantCard> = emptyList(),
    /** Restaurants with no visits. Derived, so nothing here was ever flagged. */
    val wishlist: List<RestaurantCard> = emptyList(),
) {
    val isEmpty: Boolean get() = been.isEmpty() && wishlist.isEmpty()
}
