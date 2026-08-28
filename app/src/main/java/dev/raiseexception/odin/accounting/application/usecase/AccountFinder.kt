package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome

class AccountFinder(private val accountRepository: AccountRepository) {

    suspend fun find(id: String): Outcome<Account> = this.accountRepository.findById(id)
}
