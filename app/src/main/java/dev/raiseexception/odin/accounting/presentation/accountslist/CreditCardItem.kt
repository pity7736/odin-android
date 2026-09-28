package dev.raiseexception.odin.accounting.presentation.accountslist

import dev.raiseexception.odin.accounting.domain.model.Money

data class CreditCardItem(
    val id: String,
    val name: String,
    val debt: Money,
    val availableCredit: Money
)
