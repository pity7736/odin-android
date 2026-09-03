package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Money
import kotlinx.datetime.LocalDate

sealed interface AccountTransaction {
    val id: String
    val amount: Money
    val date: LocalDate
    val categoryId: String
    val description: String
    val runningBalance: Money?

    data class IncomeTransaction(
        override val id: String,
        override val amount: Money,
        override val date: LocalDate,
        override val categoryId: String,
        override val description: String,
        override val runningBalance: Money?,
    ) : AccountTransaction

    data class ExpenseTransaction(
        override val id: String,
        override val amount: Money,
        override val date: LocalDate,
        override val categoryId: String,
        override val description: String,
        override val runningBalance: Money?,
    ) : AccountTransaction
}
