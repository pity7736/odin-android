package dev.raiseexception.odin.accounting.presentation.incomecreation

sealed interface NavigationTarget {
    data class AccountDetail(val accountId: String) : NavigationTarget
}
