package dev.raiseexception.odin.accounts.application.usecase

import dev.raiseexception.odin.accounts.domain.LoginError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserAuthenticator(
    private val vaultCrypto: VaultCrypto,
    private val userRepository: UserRepository,
    private val masterKeyRepository: MasterKeyRepository,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun authenticate(rawPassword: String): Outcome<User> {
        if (rawPassword.isBlank()) {
            return this.emptyPasswordFailure()
        }
        val user = when (val userOutcome = this.userRepository.get()) {
            is Outcome.Success -> userOutcome.value
            is Outcome.Failure -> return userOutcome
        }
        return this.verifyPassword(rawPassword, user)
    }

    private fun emptyPasswordFailure() = Outcome.Failure(
        LoginError.EmptyPassword(
            internalMessage = "Password must not be blank",
            externalMessage = "Ingrese su contraseña"
        )
    )

    private suspend fun verifyPassword(rawPassword: String, user: User): Outcome<User> =
        withContext(this.cpuDispatcher) {
            val derivedKeys = when (val keysOutcome = vaultCrypto.deriveKeys(rawPassword, user.salt)) {
                is Outcome.Success -> keysOutcome.value
                is Outcome.Failure -> return@withContext cryptoFailure(keysOutcome.error.internalMessage)
            }
            val unwrapOutcome = vaultCrypto.unwrapMasterKey(user.wrappedMasterKey, derivedKeys.encryptionKey)
            when (unwrapOutcome) {
                is Outcome.Success -> storeMasterKey(unwrapOutcome.value, user)
                is Outcome.Failure -> mapUnwrapFailure(unwrapOutcome.error)
            }
        }

    private fun storeMasterKey(masterKey: ByteArray, user: User): Outcome<User> {
        this.masterKeyRepository.store(masterKey)
        masterKey.fill(0)
        return Outcome.Success(user)
    }

    private fun mapUnwrapFailure(error: DomainError): Outcome<User> = when (error) {
        is CryptoError.DecryptionFailed -> this.invalidCredentialsFailure()
        else -> this.cryptoFailure(error.internalMessage)
    }

    private fun invalidCredentialsFailure() = Outcome.Failure(
        LoginError.InvalidCredentials(
            internalMessage = "Master key unwrap failed: incorrect password",
            externalMessage = "Contraseña incorrecta"
        )
    )

    private fun cryptoFailure(internalMessage: String) = Outcome.Failure(
        LoginError.CryptoFailure(
            internalMessage = internalMessage,
            externalMessage = "Algo salió mal. Intente de nuevo más tarde"
        )
    )
}
