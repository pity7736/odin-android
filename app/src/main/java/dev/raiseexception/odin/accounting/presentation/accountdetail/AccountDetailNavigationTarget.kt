package dev.raiseexception.odin.accounting.presentation.accountdetail

sealed interface AccountDetailNavigationTarget {
    data class CreateIncome(val accountId: String) : AccountDetailNavigationTarget
}
