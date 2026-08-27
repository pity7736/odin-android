package dev.raiseexception.odin.accounting.presentation.accountslist

sealed interface AccountsListNavigationTarget {
    data class AccountDetail(val accountId: String) : AccountsListNavigationTarget
}
