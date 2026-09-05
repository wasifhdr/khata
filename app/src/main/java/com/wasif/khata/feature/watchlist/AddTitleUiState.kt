package com.wasif.khata.feature.watchlist

import androidx.compose.runtime.Stable
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.watch.TmdbResult

data class AddTitleUiState(
    val query: String = "",
    val results: List<TmdbResult> = emptyList(),

    // The manual fields are never replaced by a search result: picking one fills them
    // in, so the keyboard is always the fallback rather than a separate mode to
    // discover. With no key and no network they are simply the only thing on screen.
    val name: String = "",
    val yearInput: String = "",
    val kind: TitleKind = TitleKind.FILM,
    val noteInput: String = "",
    val recommenders: List<String> = emptyList(),
    val recommenderInput: String = "",

    /** Set when a result was picked, so the poster and rating travel with the save. */
    val picked: TmdbResult? = null,
    val logWatchNow: Boolean = false,

    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    val year: Int? get() = yearInput.trim().takeIf { it.isNotBlank() }?.toIntOrNull()

    val yearHasError: Boolean get() = yearInput.isNotBlank() && year == null

    /** A name is the whole requirement. Everything else is optional by design. */
    val canSave: Boolean get() = name.isNotBlank() && !yearHasError && !isSaving
}

sealed interface AddTitleEffect {
    data object Saved : AddTitleEffect
}

@Stable
interface AddTitleActions {
    fun onQueryChange(value: String)
    fun onResultPick(result: TmdbResult)
    fun onNameChange(value: String)
    fun onYearChange(value: String)
    fun onKindChange(kind: TitleKind)
    fun onNoteChange(value: String)
    fun onRecommenderInputChange(value: String)
    fun onRecommenderAdded()
    fun onRecommenderRemoved(name: String)
    fun onLogWatchNowChange(value: Boolean)
    fun onSave()
}
