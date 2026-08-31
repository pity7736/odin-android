package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    suspend fun existsByName(name: String): Outcome<Boolean>
    suspend fun add(account: Account): Outcome<Unit>
    suspend fun findById(id: String, criteria: AccountCriteria = AccountCriteria()): Outcome<Account>
    fun getAll(criteria: AccountCriteria = AccountCriteria()): Flow<Outcome<List<Account>>>
}
