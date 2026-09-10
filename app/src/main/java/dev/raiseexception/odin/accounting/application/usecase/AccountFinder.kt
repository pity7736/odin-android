package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow

class AccountFinder(private val accountRepository: AccountRepository) {

    fun find(id: String, criteria: AccountCriteria = AccountCriteria()): Flow<Outcome<Account>> =
        this.accountRepository.findById(id, criteria)
}
