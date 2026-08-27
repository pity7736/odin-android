package dev.raiseexception.odin.accounting.presentation.categorycreation

sealed class CreateCategoryUiState {
    data object Idle : CreateCategoryUiState()
    data object Loading : CreateCategoryUiState()
    data class ValidationError(
        val nameError: String? = null,
        val typeError: String? = null,
        val descriptionError: String? = null,
        val colorError: String? = null
    ) : CreateCategoryUiState()
    data class Error(val message: String) : CreateCategoryUiState()
}
