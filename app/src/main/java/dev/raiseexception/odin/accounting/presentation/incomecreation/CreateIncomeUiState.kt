package dev.raiseexception.odin.accounting.presentation.incomecreation

import dev.raiseexception.odin.accounting.domain.model.Category
import kotlinx.datetime.LocalDate

sealed interface CreateIncomeUiState {
    data object Loading : CreateIncomeUiState
    data class Idle(
        val categories: List<Category>,
        val accountCreatedAt: LocalDate
    ) : CreateIncomeUiState
    data object Saving : CreateIncomeUiState
    data class ValidationError(
        val categories: List<Category>,
        val accountCreatedAt: LocalDate,
        val amountError: String? = null,
        val dateError: String? = null,
        val categoryError: String? = null,
        val descriptionError: String? = null
    ) : CreateIncomeUiState
    data class Error(val message: String) : CreateIncomeUiState
}
