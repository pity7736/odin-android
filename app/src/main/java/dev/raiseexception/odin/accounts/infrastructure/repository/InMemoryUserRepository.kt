package dev.raiseexception.odin.accounts.infrastructure.repository

import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.shared.domain.Outcome

class InMemoryUserRepository : UserRepository {

    private var storedUser: User? = null

    override suspend fun add(user: User): Outcome<Unit> {
        if (storedUser != null) {
            return Outcome.Failure(
                RegistrationError.StorageFailure(
                    internalMessage = "User already exists in storage",
                    externalMessage = "Ya existe una cuenta en este dispositivo"
                )
            )
        }
        storedUser = user
        return Outcome.Success(Unit)
    }

    override suspend fun exists(): Boolean = storedUser != null
}
