package dev.raiseexception.odin.accounting.presentation.incomeedit

import dev.raiseexception.odin.accounting.domain.model.Category
import kotlinx.datetime.LocalDate

sealed interface EditIncomeUiState {
    data object Loading : EditIncomeUiState
    data object NotFound : EditIncomeUiState
    data object UnexpectedFailure : EditIncomeUiState
    data class Editing(
        val amount: String,
        val date: String,
        val categoryId: String,
        val categoryName: String,
        val description: String,
        val accountName: String,
        val accountCreatedAt: LocalDate,
        val categories: List<Category>,
        val amountError: String? = null,
        val dateError: String? = null,
        val categoryError: String? = null,
        val descriptionError: String? = null,
        val isSaving: Boolean = false,
        val saveError: String? = null
    ) : EditIncomeUiState
}
