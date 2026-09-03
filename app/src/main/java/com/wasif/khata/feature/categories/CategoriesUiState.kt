package com.wasif.khata.feature.categories

import androidx.compose.runtime.Stable
import com.wasif.khata.domain.model.Category

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    /** The row a delete is being confirmed for; null when nothing is pending. */
    val pendingDelete: Category? = null,
    /** The row being edited, or null for the add sheet; absent when neither is open. */
    val editing: Editing? = null,
) {
    /** Null [category] is the add case: a new name and colour, nothing to carry over. */
    data class Editing(val category: Category?, val name: String, val colorToken: String) {
        val canSave: Boolean get() = name.isNotBlank()
        val isNew: Boolean get() = category == null
    }
}

@Stable
interface CategoriesActions {
    fun onAddRequested()
    fun onEditRequested(category: Category)
    fun onEditName(name: String)
    fun onEditColour(colorToken: String)
    fun onEditSaved()
    fun onEditDismissed()
    fun onDeleteRequested(category: Category)
    fun onDeleteConfirmed()
    fun onDeleteDismissed()
}
