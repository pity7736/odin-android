package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner
import kotlinx.coroutines.flow.first

class AccountUpdater(
    private val accountFinder: AccountFinder,
    private val accountRepository: AccountRepository,
    private val categoryResolver: CategoryResolver,
    private val transactionRunner: TransactionRunner
) {

    private val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    @Suppress("LongParameterList")
    suspend fun update(
        id: String,
        name: String,
        initialBalance: String,
        currency: Currency?,
        kind: MoneyAccountKind?,
        description: String,
        gmf: GmfInput
    ): Outcome<Account> {
        val findOutcome = this.accountFinder.find(id, this.criteria).first()
        if (findOutcome is Outcome.Failure) return findOutcome
        val existing = (findOutcome as Outcome.Success).value
        return this.transactionRunner.run {
            val gmfCategory = when (val categoryOutcome = gmf.resolveCategory(this.categoryResolver)) {
                is Outcome.Success -> categoryOutcome.value
                is Outcome.Failure -> return@run categoryOutcome
            }
            val editOutcome = existing.edit(
                name = name,
                initialBalance = this.effectiveBalance(existing, initialBalance),
                currency = this.effectiveCurrency(existing, currency),
                kind = kind,
                description = description,
                gmf = gmf.treatment(gmfCategory)
            )
            val edited = when (editOutcome) {
                is Outcome.Success -> editOutcome.value
                is Outcome.Failure -> return@run this.withGmfRejection(editOutcome, gmfCategory)
            }
            val duplicateOutcome = this.checkDuplicate(existing, edited)
            if (duplicateOutcome is Outcome.Failure) return@run duplicateOutcome
            this.persist(edited)
        }
    }

    private fun effectiveBalance(existing: Account, incoming: String): String =
        if (existing.hasTransactions()) {
            when (val funding = existing.funding) {
                is AccountFunding.Funds -> funding.initialBalance.amount.toPlainString()
                is AccountFunding.Credit -> funding.initialDebt.amount.toPlainString()
            }
        } else {
            incoming
        }

    private fun effectiveCurrency(existing: Account, incoming: Currency?): Currency? =
        if (existing.hasTransactions()) existing.currency else incoming

    private fun withGmfRejection(editFailure: Outcome.Failure, gmfCategory: CategoryResolution?): Outcome.Failure {
        val invalidInput = editFailure.error as? AccountUpdateError.InvalidInput ?: return editFailure
        val categoryRejection = (gmfCategory as? CategoryResolution.Rejected)?.categoryError ?: return editFailure
        return Outcome.Failure(invalidInput.copy(gmfCategoryError = categoryRejection))
    }

    private suspend fun checkDuplicate(existing: Account, edited: Account): Outcome<Unit> {
        if (edited.name.equals(existing.name, ignoreCase = true)) return Outcome.Success(Unit)
        return when (val existsOutcome = this.accountRepository.existsByName(edited.name)) {
            is Outcome.Failure -> existsOutcome
            is Outcome.Success -> if (existsOutcome.value) this.duplicateNameFailure() else Outcome.Success(Unit)
        }
    }

    private suspend fun persist(account: Account): Outcome<Account> =
        when (val updateOutcome = this.accountRepository.update(account)) {
            is Outcome.Failure -> updateOutcome
            is Outcome.Success -> Outcome.Success(account)
        }

    private fun duplicateNameFailure() = Outcome.Failure(
        AccountUpdateError.DuplicateName(
            internalMessage = "An account with the same name already exists",
            externalMessage = "Ya tienes una cuenta con ese nombre."
        )
    )
}
