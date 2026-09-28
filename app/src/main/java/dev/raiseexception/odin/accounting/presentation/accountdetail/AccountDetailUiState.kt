package dev.raiseexception.odin.accounting.presentation.accountdetail

import dev.raiseexception.odin.accounting.application.usecase.AccountTransaction
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter

sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data class MoneyAccountContent(
        val account: Account,
        val initialBalance: Money,
        val transactions: List<AccountTransaction>,
        val activeFilter: TransactionFilter,
    ) : AccountDetailUiState
    data class CreditCardContent(val creditCard: CreditCardDetail) : AccountDetailUiState
    data object NotFound : AccountDetailUiState
    data class Error(val message: String) : AccountDetailUiState
}
