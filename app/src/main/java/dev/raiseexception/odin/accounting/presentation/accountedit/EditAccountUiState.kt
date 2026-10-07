package dev.raiseexception.odin.accounting.presentation.accountedit

import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind

sealed interface EditAccountUiState {
    data object Loading : EditAccountUiState
    data object NotFound : EditAccountUiState
    data class Editing(
        val name: String,
        val initialBalance: String,
        val currency: Currency?,
        val kind: MoneyAccountKind?,
        val description: String,
        val locked: Boolean,
        val lockedBalanceDisplay: String? = null,
        val nameError: String? = null,
        val balanceError: String? = null,
        val currencyError: String? = null,
        val typeError: String? = null,
        val descriptionError: String? = null,
        val isSaving: Boolean = false,
        val saveError: String? = null
    ) : EditAccountUiState
}
