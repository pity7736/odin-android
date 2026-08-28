package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

class AccountLister(private val accountRepository: AccountRepository) {

    fun list(): Flow<List<Account>> = this.accountRepository.getAll()
}
