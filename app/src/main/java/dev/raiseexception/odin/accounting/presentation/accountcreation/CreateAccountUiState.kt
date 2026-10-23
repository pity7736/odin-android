package dev.raiseexception.odin.accounting.presentation.accountcreation

import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.shared.presentation.TagSelection

sealed interface CreateAccountUiState {
    data object Loading : CreateAccountUiState
    data class Idle(val form: AccountForm) : CreateAccountUiState
    data class Saving(val form: AccountForm) : CreateAccountUiState

    @Suppress("LongParameterList")
    data class ValidationError(
        val form: AccountForm,
        val nameError: String? = null,
        val balanceError: String? = null,
        val currencyError: String? = null,
        val typeError: String? = null,
        val descriptionError: String? = null,
        val creditLimitError: String? = null,
        val debtError: String? = null,
        val gmfCategoryError: String? = null
    ) : CreateAccountUiState
    data class SaveFailed(val form: AccountForm, val message: String) : CreateAccountUiState
    data class Error(val message: String) : CreateAccountUiState
    data object UnexpectedFailure : CreateAccountUiState
}

data class AccountForm(
    val type: AccountType? = null,
    val currency: Currency? = null,
    val gmfExempt: Boolean = true,
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val tagSelection: TagSelection = TagSelection()
) {
    val showsGmfOption: Boolean get() = this.type == AccountType.SAVINGS && this.currency == Currency.COP
}
