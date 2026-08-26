package dev.raiseexception.odin.accounts.application.usecase

import com.github.f4b6a3.uuid.UuidCreator
import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.model.Password
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRegistrar(
    private val vaultCrypto: VaultCrypto,
    private val userRepository: UserRepository,
    private val masterKeyRepository: MasterKeyRepository,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun register(rawPassword: String, rawPasswordConfirmation: String): Outcome<User> {
        if (this.userRepository.exists()) {
            return this.alreadyRegisteredFailure()
        }
        val password = when (val passwordOutcome = Password.create(rawPassword)) {
            is Outcome.Success -> passwordOutcome.value
            is Outcome.Failure -> return passwordOutcome
        }
        if (rawPassword != rawPasswordConfirmation) {
            return this.passwordsDoNotMatchFailure()
        }
        return this.performRegistration(password)
    }

    private suspend fun performRegistration(password: Password): Outcome<User> =
        withContext(this.cpuDispatcher) {
            val salt = vaultCrypto.generateSalt()
            val derivedKeys = when (val keysOutcome = vaultCrypto.deriveKeys(password.value, salt)) {
                is Outcome.Success -> keysOutcome.value
                is Outcome.Failure -> return@withContext cryptoFailure(keysOutcome.error.internalMessage)
            }
            val masterKey = vaultCrypto.generateMasterKey()
            val wrapOutcome = vaultCrypto.wrapMasterKey(masterKey, derivedKeys.encryptionKey)
            val wrappedMasterKey = when (wrapOutcome) {
                is Outcome.Success -> wrapOutcome.value
                is Outcome.Failure -> return@withContext cryptoFailure(wrapOutcome.error.internalMessage)
            }
            saveUser(salt, wrappedMasterKey, masterKey)
        }

    private suspend fun saveUser(salt: ByteArray, wrappedMasterKey: ByteArray, masterKey: ByteArray): Outcome<User> {
        val user = User(
            id = UuidCreator.getTimeOrderedEpoch().toString(),
            salt = salt,
            wrappedMasterKey = wrappedMasterKey
        )
        val addOutcome = this.userRepository.add(user)
        if (addOutcome is Outcome.Failure) return addOutcome
        this.masterKeyRepository.store(masterKey)
        return Outcome.Success(user)
    }

    private fun alreadyRegisteredFailure() = Outcome.Failure(
        RegistrationError.AlreadyRegistered(
            internalMessage = "User already registered on this device",
            externalMessage = "Ya existe un usuario en este dispositivo"
        )
    )

    private fun passwordsDoNotMatchFailure() = Outcome.Failure(
        RegistrationError.PasswordsDoNotMatch(
            internalMessage = "Password and confirmation do not match",
            externalMessage = "Las contraseñas no coinciden"
        )
    )

    private fun cryptoFailure(internalMessage: String) = Outcome.Failure(
        RegistrationError.CryptoFailure(
            internalMessage = internalMessage,
            externalMessage = "Algo salió mal. Intente de nuevo más tarde"
        )
    )
}
