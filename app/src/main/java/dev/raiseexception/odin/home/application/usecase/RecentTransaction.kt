package dev.raiseexception.odin.home.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Transaction

data class RecentTransaction(
    val transaction: Transaction,
    val accountName: String,
)
