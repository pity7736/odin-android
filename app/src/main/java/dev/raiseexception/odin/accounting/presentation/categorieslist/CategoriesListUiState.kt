package dev.raiseexception.odin.accounting.presentation.categorieslist

import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType

sealed interface CategoriesListUiState {
    data object Loading : CategoriesListUiState
    data class Empty(
        val activeFilter: CategoryType?,
        val searchQuery: String,
    ) : CategoriesListUiState
    data class Content(
        val categories: List<Category>,
        val activeFilter: CategoryType?,
        val searchQuery: String,
    ) : CategoriesListUiState
    data class Error(val message: String) : CategoriesListUiState
}
