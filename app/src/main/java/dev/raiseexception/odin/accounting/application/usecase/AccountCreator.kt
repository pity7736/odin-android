package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome

class AccountCreator(
    private val accountRepository: AccountRepository
) {

    suspend fun create(command: CreateAccountCommand): Outcome<Account> {
        val account = when (val creationOutcome = this.build(command)) {
            is Outcome.Success -> creationOutcome.value
            is Outcome.Failure -> return creationOutcome
        }
        return when (val existsOutcome = this.accountRepository.existsByName(account.name)) {
            is Outcome.Failure -> existsOutcome
            is Outcome.Success -> if (existsOutcome.value) {
                this.duplicateNameFailure()
            } else {
                this.persist(account)
            }
        }
    }

    private fun build(command: CreateAccountCommand): Outcome<Account> = when (command) {
        is CreateAccountCommand.MoneyAccount -> Account.create(
            name = command.name,
            initialBalance = command.balance,
            currency = command.currency,
            type = command.type,
            description = command.description
        )
        is CreateAccountCommand.CreditCard -> Account.createCreditCard(
            name = command.name,
            currency = command.currency,
            description = command.description,
            creditLimit = command.creditLimit,
            existingDebt = command.existingDebt
        )
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
