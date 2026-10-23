package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner

class AccountCreator(
    private val accountRepository: AccountRepository,
    private val categoryResolver: CategoryResolver,
    private val transactionRunner: TransactionRunner
) {

    suspend fun create(command: CreateAccountCommand): Outcome<Account> = this.transactionRunner.run {
        val gmfCategory = when (val categoryOutcome = this.resolveGmfCategory(command)) {
            is Outcome.Success -> categoryOutcome.value
            is Outcome.Failure -> return@run categoryOutcome
        }
        val account = when (val creationOutcome = this.build(command, gmfCategory)) {
            is Outcome.Success -> creationOutcome.value
            is Outcome.Failure -> return@run this.withGmfRejection(creationOutcome, gmfCategory)
        }
        when (val existsOutcome = this.accountRepository.existsByName(account.name)) {
            is Outcome.Failure -> existsOutcome
            is Outcome.Success -> if (existsOutcome.value) {
                this.duplicateNameFailure()
            } else {
                this.persist(account)
            }
        }
    }

    private suspend fun resolveGmfCategory(command: CreateAccountCommand): Outcome<CategoryResolution?> =
        when (command) {
            is CreateAccountCommand.MoneyAccount -> command.gmf.resolveCategory(this.categoryResolver)
            is CreateAccountCommand.CreditCard -> Outcome.Success(null)
        }

    private fun build(command: CreateAccountCommand, gmfCategory: CategoryResolution?): Outcome<Account> =
        when (command) {
            is CreateAccountCommand.MoneyAccount -> Account.create(
                name = command.name,
                initialBalance = command.balance,
                currency = command.currency,
                kind = command.kind,
                description = command.description,
                gmf = command.gmf.treatment(gmfCategory)
            )
            is CreateAccountCommand.CreditCard -> Account.createCreditCard(
                name = command.name,
                currency = command.currency,
                description = command.description,
                creditLimit = command.creditLimit,
                existingDebt = command.existingDebt
            )
        }

    private fun withGmfRejection(creationFailure: Outcome.Failure, gmfCategory: CategoryResolution?): Outcome.Failure {
        val invalidInput = creationFailure.error as? AccountCreationError.InvalidInput ?: return creationFailure
        val categoryRejection = (gmfCategory as? CategoryResolution.Rejected)?.categoryError ?: return creationFailure
        return Outcome.Failure(invalidInput.copy(gmfCategoryError = categoryRejection))
    }

    private suspend fun persist(account: Account): Outcome<Account> =
        when (val addOutcome = this.accountRepository.add(account)) {
            is Outcome.Failure -> addOutcome
            is Outcome.Success -> Outcome.Success(account)
        }

    private fun duplicateNameFailure() = Outcome.Failure(
        AccountCreationError.DuplicateName(
            internalMessage = "An account with the same name already exists",
            externalMessage = "Ya tienes una cuenta con ese nombre."
        )
    )
}
