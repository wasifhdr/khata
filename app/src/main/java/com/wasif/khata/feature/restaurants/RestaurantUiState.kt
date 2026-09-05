package com.wasif.khata.feature.restaurants

import androidx.compose.runtime.Stable
import com.wasif.khata.core.data.dao.RestaurantSummary

/** One photo, wherever it hangs: a media id and a model Coil can load. */
data class Photo(val mediaId: Long, val model: String)

data class VisitCard(
    val id: Long,
    val visitedAt: Long,
    val ambianceRating: Int?,
    val costMinor: Long?,
    val note: String?,
    val dishes: List<Pair<String, Int?>> = emptyList(),
    val companions: List<String> = emptyList(),
    val photos: List<Photo> = emptyList(),
)

data class RestaurantUiState(
    val summary: RestaurantSummary? = null,
    val coverModel: String? = null,
    val visits: List<VisitCard> = emptyList(),
    val recommenders: List<String> = emptyList(),
    /** Every photo across every visit, plus the restaurant's own. The cover is chosen from here. */
    val allPhotos: List<Photo> = emptyList(),
    val pickingCover: Boolean = false,
)

@Stable
interface RestaurantActions {
    fun onSetCoverRequested()
    fun onCoverPicked(mediaId: Long)
    fun onCoverDismissed()
}
