package dev.raiseexception.odin.accounting.presentation.expensecreation

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Category
import kotlinx.datetime.LocalDate

sealed interface CreateExpenseUiState {
    data object Loading : CreateExpenseUiState
    data class Idle(
        val categories: List<Category>,
        val accountCreatedAt: LocalDate? = null,
        val accounts: List<Account> = emptyList(),
        val selectedAccountId: String? = null,
    ) : CreateExpenseUiState
    data object Saving : CreateExpenseUiState

    @Suppress("LongParameterList")
    data class ValidationError(
        val categories: List<Category>,
        val accountCreatedAt: LocalDate? = null,
        val accounts: List<Account> = emptyList(),
        val selectedAccountId: String? = null,
        val amountError: String? = null,
        val dateError: String? = null,
        val categoryError: String? = null,
        val descriptionError: String? = null,
        val accountError: String? = null,
    ) : CreateExpenseUiState
    data class Error(val message: String) : CreateExpenseUiState
}
