package com.wasif.khata.feature.restaurants

import androidx.compose.runtime.Stable
import com.wasif.khata.core.model.Money

/** One dish row in the editor: free text and a rating, because there is no catalogue. */
data class DishRow(val name: String = "", val rating: Int? = null)

/**
 * A photo the grid can draw, whichever side of a save it is on.
 *
 * [model] is handed straight to Coil: a `file://` path for one already imported, the
 * gallery's own content URI for one just picked. Picked photos are imported when the
 * visit is saved, because until then there is no visit for the link to point at.
 */
data class PhotoItem(val model: String, val mediaId: Long? = null) {
    val isPending: Boolean get() = mediaId == null
}

data class VisitEditorUiState(
    val nameInput: String = "",
    /** Set once a suggestion is picked, cleared the moment the name is edited again. */
    val restaurantId: Long? = null,
    val suggestions: List<String> = emptyList(),

    val visitedAt: Long = 0L,
    val ambianceRating: Int? = null,
    val costInput: String = "",
    val dishes: List<DishRow> = listOf(DishRow()),
    val companions: List<String> = emptyList(),
    val companionInput: String = "",
    val photos: List<PhotoItem> = emptyList(),
    val noteInput: String = "",

    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    // Derived as getters, never constructor params, so no copy() can leave a validity
    // flag disagreeing with the input it describes.
    val cost: Money? get() = if (costInput.isBlank()) null else Money.parse(costInput)

    val costHasError: Boolean get() = costInput.isNotBlank() && cost == null

    /**
     * A name and nothing else. A visit with no rating, no dishes and no cost is still
     * a visit -- being made to fill in more than you remember is what gets the module
     * abandoned.
     */
    val canSave: Boolean get() = nameInput.isNotBlank() && !costHasError && !isSaving
}

sealed interface VisitEditorEffect {
    data object Saved : VisitEditorEffect
    data object Deleted : VisitEditorEffect
}

@Stable
interface VisitEditorActions {
    fun onNameChange(value: String)
    fun onSuggestionPicked(name: String)
    fun onDateChange(millis: Long)
    fun onAmbianceChange(rating: Int?)
    fun onCostChange(value: String)
    fun onDishNameChange(index: Int, value: String)
    fun onDishRatingChange(index: Int, rating: Int?)
    fun onDishRemoved(index: Int)
    fun onDishAdded()
    fun onCompanionInputChange(value: String)
    fun onCompanionAdded()
    fun onCompanionRemoved(name: String)
    fun onPhotosPicked(uris: List<String>)
    fun onPhotoRemoved(model: String)
    fun onNoteChange(value: String)
    fun onSave()
    fun onDelete()
}
