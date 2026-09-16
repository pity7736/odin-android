package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class Transfer private constructor(
    val id: String,
    val expense: Expense,
    val income: Income,
    val createdAt: Instant
) {

    companion object {
        @Suppress("LongParameterList")
        fun create(
            sourceAccount: Account,
            destinationAccount: Account,
            amount: String,
            date: String,
            categoryId: String,
            clock: Clock = Clock.System
        ): Outcome<Transfer> {
            val preValidationError = validateAccounts(sourceAccount, destinationAccount)
            if (preValidationError != null) return Outcome.Failure(preValidationError)
            val expenseDescription = "Transferencia a ${destinationAccount.name}"
            val expense = when (
                val result = sourceAccount.createExpense(
                    amount = amount,
                    date = date,
                    categoryId = categoryId,
                    description = expenseDescription,
                    clock = clock
                )
            ) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> return mapExpenseError(result.error)
            }
            val incomeDescription = "Transferencia desde ${sourceAccount.name}"
            val income = when (
                val result = destinationAccount.createIncome(
                    amount = amount,
                    date = date,
                    categoryId = categoryId,
                    description = incomeDescription,
                    clock = clock
                )
            ) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> return mapIncomeError(result.error)
            }
            return Outcome.Success(
                Transfer(
                    id = com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch().toString(),
                    expense = expense,
                    income = income,
                    createdAt = clock.now()
                )
            )
        }

        fun restore(
            id: String,
            expense: Expense,
            income: Income,
            createdAt: Instant
        ): Transfer = Transfer(
            id = id,
            expense = expense,
            income = income,
            createdAt = createdAt
        )

        private fun validateAccounts(
            sourceAccount: Account,
            destinationAccount: Account
        ): TransferCreationError.InvalidInput? {
            val sourceError = if (sourceAccount.id == destinationAccount.id) {
                "La cuenta origen y destino deben ser diferentes."
            } else {
                null
            }
            val destinationError = if (sourceAccount.currency != destinationAccount.currency) {
                "Ambas cuentas deben usar la misma moneda."
            } else {
                null
            }
            if (sourceError != null || destinationError != null) {
                return TransferCreationError.InvalidInput(
                    amountError = null,
                    dateError = null,
                    sourceAccountError = sourceError,
                    destinationAccountError = destinationError
                )
            }
            return null
        }

        private fun mapExpenseError(error: dev.raiseexception.odin.shared.domain.DomainError): Outcome<Transfer> {
            val invalidInput = error as? ExpenseCreationError.InvalidInput
                ?: return Outcome.Failure(error)
            return Outcome.Failure(
                TransferCreationError.InvalidInput(
                    amountError = invalidInput.amountError,
                    dateError = invalidInput.dateError,
                    sourceAccountError = null,
                    destinationAccountError = null
                )
            )
        }

        private fun mapIncomeError(error: dev.raiseexception.odin.shared.domain.DomainError): Outcome<Transfer> {
            val invalidInput = error as? IncomeCreationError.InvalidInput
                ?: return Outcome.Failure(error)
            return Outcome.Failure(
                TransferCreationError.InvalidInput(
                    amountError = invalidInput.amountError,
                    dateError = invalidInput.dateError,
                    sourceAccountError = null,
                    destinationAccountError = null
                )
            )
        }
    }
}
