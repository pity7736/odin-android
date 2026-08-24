package dev.raiseexception.odin.accounts.infrastructure.repository

import dev.raiseexception.odin.accounts.domain.LoginError
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

    override suspend fun get(): Outcome<User> {
        val user = this.storedUser
            ?: return Outcome.Failure(
                LoginError.UserNotFound(
                    internalMessage = "No user stored on this device",
                    externalMessage = "Algo salió mal. Intente de nuevo más tarde"
                )
            )
        return Outcome.Success(user)
    }
}
