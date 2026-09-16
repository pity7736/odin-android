package dev.raiseexception.odin.accounting.presentation.transfercreation

import dev.raiseexception.odin.accounting.domain.model.Currency

data class AccountSummary(
    val id: String,
    val name: String,
    val currency: Currency
)
