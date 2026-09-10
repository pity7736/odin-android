package dev.raiseexception.odin.accounts.application.usecase

import dev.raiseexception.odin.accounts.domain.LoginError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.crypto.domain.SensitivePassword
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.domain.repository.SaltRepository
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.VaultUnlocker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("LongParameterList")
class UserAuthenticator(
    private val vaultCrypto: VaultCrypto,
    private val userRepository: UserRepository,
    private val masterKeyRepository: MasterKeyRepository,
    private val saltRepository: SaltRepository,
    private val vaultUnlocker: VaultUnlocker,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun authenticate(password: SensitivePassword): Outcome<User> {
        val result = this.performAuthentication(password)
        password.wipe()
        return result
    }

    private suspend fun performAuthentication(password: SensitivePassword): Outcome<User> {
        if (password.isBlank()) {
            return this.emptyPasswordFailure()
        }
        val salt = this.saltRepository.get() ?: return this.userNotFoundFailure()
        return this.verifyPassword(password, salt)
    }

    private suspend fun verifyPassword(password: SensitivePassword, salt: ByteArray): Outcome<User> =
        withContext(this.cpuDispatcher) {
            val derivedKeys = when (val keysOutcome = vaultCrypto.deriveKeys(password, salt)) {
                is Outcome.Success -> keysOutcome.value
                is Outcome.Failure -> return@withContext cryptoFailure(keysOutcome.error.internalMessage)
            }
            val unlockOutcome = vaultUnlocker.unlock(derivedKeys.encryptionKey)
            if (unlockOutcome is Outcome.Failure) {
                derivedKeys.encryptionKey.fill(0)
                return@withContext invalidCredentialsFailure()
            }
            val user = when (val userOutcome = userRepository.get()) {
                is Outcome.Success -> userOutcome.value
                is Outcome.Failure -> {
                    derivedKeys.encryptionKey.fill(0)
                    return@withContext userOutcome
                }
            }
            val unwrapOutcome = vaultCrypto.unwrapMasterKey(user.wrappedMasterKey, derivedKeys.encryptionKey)
            when (unwrapOutcome) {
                is Outcome.Success -> storeMasterKey(unwrapOutcome.value, user, derivedKeys.encryptionKey)
                is Outcome.Failure -> {
                    derivedKeys.encryptionKey.fill(0)
                    mapUnwrapFailure(unwrapOutcome.error)
                }
            }
        }

    private fun storeMasterKey(masterKey: ByteArray, user: User, encryptionKey: ByteArray): Outcome<User> {
        this.masterKeyRepository.store(masterKey)
        masterKey.fill(0)
        encryptionKey.fill(0)
        return Outcome.Success(user)
    }

    private fun mapUnwrapFailure(error: DomainError): Outcome<User> = when (error) {
        is CryptoError.DecryptionFailed -> this.invalidCredentialsFailure()
        else -> this.cryptoFailure(error.internalMessage)
    }

    private fun emptyPasswordFailure() = Outcome.Failure(
        LoginError.EmptyPassword(
            internalMessage = "Password must not be blank",
            externalMessage = "Ingrese su contraseña"
        )
    )

    private fun userNotFoundFailure() = Outcome.Failure(
        LoginError.UserNotFound(
            internalMessage = "No salt stored — no user registered on this device",
            externalMessage = "Algo salió mal. Intente de nuevo más tarde"
        )
    )

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
