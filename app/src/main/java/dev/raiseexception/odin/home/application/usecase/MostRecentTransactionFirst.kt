package dev.raiseexception.odin.home.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Transaction

val mostRecentTransactionFirst: Comparator<Transaction> =
    compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt }
