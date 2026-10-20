package dev.raiseexception.odin.accounts.infrastructure.repository

import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.persistence.DatabaseProvider
import dev.raiseexception.odin.shared.domain.Outcome

class DeferredUserRepository(
    private val databaseProvider: DatabaseProvider
) : UserRepository {

    override suspend fun add(user: User): Outcome<Unit> = this.delegate().add(user)

    override suspend fun get(): Outcome<User> = this.delegate().get()

    private fun delegate() = RoomUserRepository(this.databaseProvider.requireDatabase().userDao())
}
