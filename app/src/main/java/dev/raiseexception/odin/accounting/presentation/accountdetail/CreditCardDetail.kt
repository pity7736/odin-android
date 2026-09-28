package dev.raiseexception.odin.accounting.presentation.accountdetail

import dev.raiseexception.odin.accounting.domain.model.Money

data class CreditCardDetail(
    val name: String,
    val debt: Money,
    val availableCredit: Money,
    val creditLimit: Money
)
