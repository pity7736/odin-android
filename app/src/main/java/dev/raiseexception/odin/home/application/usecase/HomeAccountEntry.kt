package dev.raiseexception.odin.home.application.usecase

import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Money

sealed interface HomeAccountEntry {
    val id: String
    val name: String

    data class MoneyAccountEntry(
        override val id: String,
        override val name: String,
        val type: AccountType,
        val balance: Money,
    ) : HomeAccountEntry

    data class CreditCardEntry(
        override val id: String,
        override val name: String,
        val debt: Money,
        val availableCredit: Money,
    ) : HomeAccountEntry
}
