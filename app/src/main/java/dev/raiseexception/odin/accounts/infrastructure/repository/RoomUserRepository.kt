package dev.raiseexception.odin.accounts.infrastructure.repository

import android.database.sqlite.SQLiteException
import dev.raiseexception.odin.accounts.domain.LoginError
import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.shared.domain.Outcome

class RoomUserRepository(
    private val userDao: UserDao,
) : UserRepository {

    override suspend fun add(user: User): Outcome<Unit> {
        if (exists()) {
            return Outcome.Failure(
                RegistrationError.StorageFailure(
                    internalMessage = "User already exists in storage",
                    externalMessage = "Ya existe un usuario en este dispositivo"
                )
            )
        }
        return try {
            userDao.insert(user.toEntity())
            Outcome.Success(Unit)
        } catch (exception: SQLiteException) {
            Outcome.Failure(
                RegistrationError.StorageFailure(
                    internalMessage = "Failed to save user: ${exception.message}",
                    externalMessage = "Algo salió mal. Intente de nuevo más tarde"
                )
            )
        }
    }

    override suspend fun exists(): Boolean = userDao.exists()

    override suspend fun get(): Outcome<User> {
        val entity = userDao.get()
            ?: return Outcome.Failure(
                LoginError.UserNotFound(
                    internalMessage = "No user stored on this device",
                    externalMessage = "Algo salió mal. Intente de nuevo más tarde"
                )
            )
        return Outcome.Success(entity.toDomain())
    }
}
