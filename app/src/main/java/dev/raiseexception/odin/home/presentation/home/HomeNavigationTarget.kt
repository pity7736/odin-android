package dev.raiseexception.odin.home.presentation.home

sealed interface HomeNavigationTarget {
    data class AccountDetail(val accountId: String) : HomeNavigationTarget
    data class TransactionDetail(val transactionId: String) : HomeNavigationTarget
    data object AccountCreate : HomeNavigationTarget
}
