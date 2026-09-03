package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Transaction

data class AccountTransaction(
    val transaction: Transaction,
    val runningBalance: Money?,
)
