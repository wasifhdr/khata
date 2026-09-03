package com.wasif.khata.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.repository.CategoryRepository
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.repository.ReferenceDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    reference: ReferenceDataRepository,
    private val categories: CategoryRepository,
) : ViewModel(), CategoriesActions {

    private val local = MutableStateFlow(CategoriesUiState())

    val state: StateFlow<CategoriesUiState> = combine(
        // The live read: this screen manages what is on offer, so a deleted category
        // belongs here no more than it belongs in a picker.
        reference.observeCategories(),
        local,
    ) { live, ui -> ui.copy(categories = live) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriesUiState(),
        )

    override fun onAddRequested() = local.update {
        it.copy(
            editing = CategoriesUiState.Editing(
                category = null,
                name = "",
                colorToken = KhataPalette.categories.keys.first(),
            ),
        )
    }

    override fun onEditRequested(category: Category) = local.update {
        it.copy(
            editing = CategoriesUiState.Editing(category, category.name, category.colorToken),
        )
    }

    override fun onEditName(name: String) = local.update {
        it.copy(editing = it.editing?.copy(name = name))
    }

    override fun onEditColour(colorToken: String) = local.update {
        it.copy(editing = it.editing?.copy(colorToken = colorToken))
    }

    override fun onEditSaved() {
        val editing = local.value.editing ?: return
        if (!editing.canSave) return
        local.update { it.copy(editing = null) }

        viewModelScope.launch {
            val existing = editing.category
            if (existing == null) {
                categories.add(editing.name, editing.colorToken)
            } else {
                categories.rename(existing.id, editing.name)
                categories.recolour(existing.id, editing.colorToken)
            }
        }
    }

    override fun onEditDismissed() = local.update { it.copy(editing = null) }

    override fun onDeleteRequested(category: Category) =
        local.update { it.copy(pendingDelete = category) }

    override fun onDeleteConfirmed() {
        val target = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch { categories.delete(target.id) }
    }

    override fun onDeleteDismissed() = local.update { it.copy(pendingDelete = null) }
}
