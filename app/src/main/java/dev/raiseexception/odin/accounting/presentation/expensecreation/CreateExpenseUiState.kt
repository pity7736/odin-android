package dev.raiseexception.odin.accounting.presentation.expensecreation

import dev.raiseexception.odin.accounting.domain.model.Category

sealed interface CreateExpenseUiState {
    data object Loading : CreateExpenseUiState
    data class Idle(val categories: List<Category>) : CreateExpenseUiState
    data object Saving : CreateExpenseUiState
    data class ValidationError(
        val categories: List<Category>,
        val amountError: String? = null,
        val dateError: String? = null,
        val categoryError: String? = null,
        val descriptionError: String? = null
    ) : CreateExpenseUiState
    data class Error(val message: String) : CreateExpenseUiState
}
