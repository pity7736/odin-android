package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.TransactionDeletionError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Transaction
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.accounting.domain.repository.TagRepository
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

@Suppress("LongParameterList")
class TransactionDeleter(
    private val transactionFinder: TransactionFinder,
    private val accountFinder: AccountFinder,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val tagRepository: TagRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock = Clock.System
) {

    private val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    suspend fun delete(transactionId: String): Outcome<Unit> {
        val transaction = when (val lookupOutcome = this.findDeletableTransaction(transactionId)) {
            is Outcome.Success -> lookupOutcome.value
            is Outcome.Failure -> return lookupOutcome
        }
        return this.transactionRunner.run {
            val accountOutcome = this.accountFinder.find(transaction.accountId, this.criteria).first()
            when (accountOutcome) {
                is Outcome.Success -> this.deleteFromAccount(accountOutcome.value, transaction)
                is Outcome.Failure -> this.storageFailure(accountOutcome.error, this.storageMessage(transaction))
            }
        }
    }

    private suspend fun findDeletableTransaction(transactionId: String): Outcome<Transaction> {
        val detail = when (val findOutcome = this.transactionFinder.find(transactionId).first()) {
            is Outcome.Success -> findOutcome.value
            is Outcome.Failure -> return if (findOutcome.error is TransactionLookupError.NotFound) {
                findOutcome
            } else {
                this.storageFailure(findOutcome.error, UNKNOWN_KIND_STORAGE_MESSAGE)
            }
        }
        if (detail.isTransfer) {
            return Outcome.Failure(
                TransactionDeletionError.TransferNotDeletable(
                    internalMessage = "Transaction $transactionId belongs to a transfer"
                )
            )
        }
        if (detail.isGmfTax) {
            return Outcome.Failure(
                TransactionDeletionError.GmfTaxNotDeletable(
                    internalMessage = "Transaction $transactionId is the 4x1000 of a movement"
                )
            )
        }
        return Outcome.Success(detail.transaction)
    }

    private suspend fun deleteFromAccount(account: Account, transaction: Transaction): Outcome<Unit> =
        when (transaction) {
            is Income -> this.deleteIncome(account, transaction)
            is Expense -> this.deleteExpense(account, transaction)
            else -> Outcome.Failure(
                TransactionLookupError.NotFound(
                    internalMessage = "Transaction ${transaction.id} is neither an income nor an expense",
                    externalMessage = "Transacción no encontrada"
                )
            )
        }

    private suspend fun deleteIncome(account: Account, income: Income): Outcome<Unit> {
        val domainOutcome = account.deleteIncome(income.id, this.clock)
        if (domainOutcome is Outcome.Failure) return domainOutcome
        return when (val deleteOutcome = this.incomeRepository.delete(income.id)) {
            is Outcome.Success -> deleteOutcome
            is Outcome.Failure -> this.storageFailure(deleteOutcome.error, INCOME_STORAGE_MESSAGE)
        }
    }

    private suspend fun deleteExpense(account: Account, expense: Expense): Outcome<Unit> {
        val domainOutcome = account.deleteExpense(expense.id, this.clock)
        if (domainOutcome is Outcome.Failure) return domainOutcome
        val deleteOutcome = this.expenseRepository.delete(expense.id)
        if (deleteOutcome is Outcome.Failure) return this.storageFailure(deleteOutcome.error, EXPENSE_STORAGE_MESSAGE)
        return when (val cleanupOutcome = this.tagRepository.deleteUnused()) {
            is Outcome.Success -> cleanupOutcome
            is Outcome.Failure -> this.storageFailure(cleanupOutcome.error, EXPENSE_STORAGE_MESSAGE)
        }
    }

    private fun storageMessage(transaction: Transaction): String =
        if (transaction is Income) INCOME_STORAGE_MESSAGE else EXPENSE_STORAGE_MESSAGE

    private fun storageFailure(error: DomainError, externalMessage: String): Outcome.Failure =
        Outcome.Failure(
            TransactionDeletionError.StorageFailure(
                internalMessage = error.internalMessage,
                externalMessage = externalMessage
            )
        )

    companion object {
        private const val EXPENSE_STORAGE_MESSAGE = "No se pudo eliminar el gasto. Inténtalo de nuevo."
        private const val INCOME_STORAGE_MESSAGE = "No se pudo eliminar el ingreso. Inténtalo de nuevo."
        private const val UNKNOWN_KIND_STORAGE_MESSAGE = "No se pudo eliminar la transacción. Inténtalo de nuevo."
    }
}
