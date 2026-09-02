package dev.raiseexception.odin.accounting.presentation.expensecreation

sealed interface NavigationTarget {
    data class AccountDetail(val accountId: String) : NavigationTarget
}
