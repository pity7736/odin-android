package dev.raiseexception.odin.accounting.presentation.categorydetail

import dev.raiseexception.odin.accounting.domain.model.CategoryType

sealed interface CategoryDetailUiState {
    data object Loading : CategoryDetailUiState
    data class Content(
        val name: String,
        val type: CategoryType,
        val description: String,
        val color: String,
        val formattedCreatedAt: String
    ) : CategoryDetailUiState
    data object NotFound : CategoryDetailUiState
    data class Error(val message: String) : CategoryDetailUiState
}
