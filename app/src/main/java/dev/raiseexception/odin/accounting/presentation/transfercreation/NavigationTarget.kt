package dev.raiseexception.odin.accounting.presentation.transfercreation

sealed interface NavigationTarget {
    data class AccountDetail(val accountId: String) : NavigationTarget
}
