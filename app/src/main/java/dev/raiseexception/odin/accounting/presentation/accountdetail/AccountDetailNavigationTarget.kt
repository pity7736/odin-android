package dev.raiseexception.odin.accounting.presentation.accountdetail

sealed interface AccountDetailNavigationTarget {
    data class CreateIncome(val accountId: String) : AccountDetailNavigationTarget
    data class CreateExpense(val accountId: String) : AccountDetailNavigationTarget
    data class CreateTransfer(val accountId: String) : AccountDetailNavigationTarget
    data class TransactionDetail(val transactionId: String) : AccountDetailNavigationTarget
    data class EditAccount(val accountId: String) : AccountDetailNavigationTarget
}
