package com.wasif.khata.feature.vehicle

import androidx.compose.runtime.Stable
import com.wasif.khata.core.model.Money

/**
 * One item row in the editor: free text and an optional cost, because there is no
 * catalogue and a line on a bill is not always priced separately.
 */
data class ItemRow(val name: String = "", val costInput: String = "") {
    val cost: Money? get() = if (costInput.isBlank()) null else Money.parse(costInput)

    val costHasError: Boolean get() = costInput.isNotBlank() && cost == null
}

/**
 * A photo the grid can draw, whichever side of a save it is on. [model] goes straight
 * to Coil: a `file://` path for one already imported, the gallery's content URI for
 * one just picked. Picked photos are imported when the service is saved, because
 * until then there is no service for the link to point at.
 */
data class PhotoItem(val model: String, val mediaId: Long? = null) {
    val isPending: Boolean get() = mediaId == null
}

data class ServiceEditorUiState(
    val servicedAt: Long = 0L,
    val odometerInput: String = "",
    val workshopInput: String = "",
    /** Set once a suggestion is picked, cleared the moment the name is edited again. */
    val placeId: Long? = null,
    val suggestions: List<String> = emptyList(),
    val costInput: String = "",
    val items: List<ItemRow> = listOf(ItemRow()),
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

    /** Blank is absent, not zero. A dash that went unread is a null, never a 0 km car. */
    val odometerKm: Int? get() = odometerInput.trim().takeIf { it.isNotBlank() }?.toIntOrNull()

    val odometerHasError: Boolean
        get() = odometerInput.isNotBlank() && (odometerKm == null || odometerKm!! < 0)

    val itemCostHasError: Boolean get() = items.any { it.costHasError }

    /**
     * A date and nothing else. A job with no cost, no odometer and no items is still
     * a job -- being made to fill in more than the receipt says is what gets a module
     * abandoned.
     */
    val canSave: Boolean
        get() = !costHasError && !odometerHasError && !itemCostHasError && !isSaving
}

sealed interface ServiceEditorEffect {
    data object Saved : ServiceEditorEffect
    data object Deleted : ServiceEditorEffect
}

@Stable
interface ServiceEditorActions {
    fun onDateChange(millis: Long)
    fun onOdometerChange(value: String)
    fun onWorkshopChange(value: String)
    fun onSuggestionPicked(name: String)
    fun onCostChange(value: String)
    fun onItemNameChange(index: Int, value: String)
    fun onItemCostChange(index: Int, value: String)
    fun onItemRemoved(index: Int)
    fun onItemAdded()
    fun onPhotosPicked(uris: List<String>)
    fun onPhotoRemoved(model: String)
    fun onNoteChange(value: String)
    fun onSave()
    fun onDelete()
}
