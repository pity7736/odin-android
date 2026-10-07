package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind

sealed interface CreateAccountCommand {
    data class MoneyAccount(
        val name: String,
        val balance: String,
        val currency: Currency?,
        val kind: MoneyAccountKind?,
        val description: String
    ) : CreateAccountCommand

    data class CreditCard(
        val name: String,
        val creditLimit: String,
        val existingDebt: String,
        val currency: Currency?,
        val description: String
    ) : CreateAccountCommand
}
