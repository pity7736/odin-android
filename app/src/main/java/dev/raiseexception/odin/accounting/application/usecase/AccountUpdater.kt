package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.first

class AccountUpdater(
    private val accountFinder: AccountFinder,
    private val accountRepository: AccountRepository
) {

    private val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    @Suppress("LongParameterList")
    suspend fun update(
        id: String,
        name: String,
        initialBalance: String,
        currency: Currency?,
        type: AccountType?,
        description: String
    ): Outcome<Account> {
        val findOutcome = this.accountFinder.find(id, this.criteria).first()
        if (findOutcome is Outcome.Failure) return findOutcome
        val existing = (findOutcome as Outcome.Success).value
        val editOutcome = existing.edit(
            name = name,
            initialBalance = this.effectiveBalance(existing, initialBalance),
            currency = this.effectiveCurrency(existing, currency),
            type = type,
            description = description
        )
        if (editOutcome is Outcome.Failure) return editOutcome
        val edited = (editOutcome as Outcome.Success).value
        val duplicateOutcome = this.checkDuplicate(existing, edited)
        if (duplicateOutcome is Outcome.Failure) return duplicateOutcome
        return this.persist(edited)
    }

    private fun effectiveBalance(existing: Account, incoming: String): String =
        if (existing.hasTransactions()) {
            when (val funding = existing.funding) {
                is AccountFunding.Funds -> funding.initialBalance.amount.toPlainString()
                is AccountFunding.Credit -> funding.debt.amount.toPlainString()
            }
        } else {
            incoming
        }

    private fun effectiveCurrency(existing: Account, incoming: Currency?): Currency? =
        if (existing.hasTransactions()) existing.currency else incoming

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
