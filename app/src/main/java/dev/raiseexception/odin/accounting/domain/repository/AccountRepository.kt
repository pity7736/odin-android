package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.shared.domain.Outcome

interface AccountRepository {
    suspend fun existsByName(name: String): Outcome<Boolean>
    suspend fun add(account: Account): Outcome<Unit>
}
