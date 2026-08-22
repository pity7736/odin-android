package dev.raiseexception.odin.accounts.domain.repository

import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.shared.domain.Outcome

interface UserRepository {
    suspend fun add(user: User): Outcome<Unit>
    suspend fun exists(): Boolean
}
