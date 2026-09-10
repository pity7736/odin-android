package dev.raiseexception.odin.accounts.application.usecase

import com.github.f4b6a3.uuid.UuidCreator
import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.domain.model.validatePassword
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.crypto.domain.SensitivePassword
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.domain.repository.SaltRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.VaultUnlocker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("LongParameterList")
class UserRegistrar(
    private val vaultCrypto: VaultCrypto,
    private val userRepository: UserRepository,
    private val masterKeyRepository: MasterKeyRepository,
    private val saltRepository: SaltRepository,
    private val vaultUnlocker: VaultUnlocker,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun register(password: SensitivePassword, confirmation: SensitivePassword): Outcome<User> {
        val result = this.performValidationAndRegistration(password, confirmation)
        password.wipe()
        confirmation.wipe()
        return result
    }

    private suspend fun performValidationAndRegistration(
        password: SensitivePassword,
        confirmation: SensitivePassword
    ): Outcome<User> {
        if (this.saltRepository.exists()) {
            return this.alreadyRegisteredFailure()
        }
        val validationOutcome = validatePassword(password)
        if (validationOutcome is Outcome.Failure) return validationOutcome
        if (password != confirmation) {
            return this.passwordsDoNotMatchFailure()
        }
        return this.performRegistration(password)
    }

    private suspend fun performRegistration(password: SensitivePassword): Outcome<User> {
        val salt = this.vaultCrypto.generateSalt()
        val saveOutcome = this.saltRepository.save(salt)
        if (saveOutcome is Outcome.Failure) return saveOutcome
        val result = withContext(this.cpuDispatcher) {
            val derivedKeys = when (val keysOutcome = vaultCrypto.deriveKeys(password, salt)) {
                is Outcome.Success -> keysOutcome.value
                is Outcome.Failure -> return@withContext cryptoFailure(keysOutcome.error.internalMessage)
            }
            val unlockOutcome = vaultUnlocker.unlock(derivedKeys.encryptionKey)
            if (unlockOutcome is Outcome.Failure) {
                derivedKeys.encryptionKey.fill(0)
                return@withContext cryptoFailure(unlockOutcome.error.internalMessage)
            }
            val masterKey = vaultCrypto.generateMasterKey()
            val wrapOutcome = vaultCrypto.wrapMasterKey(masterKey, derivedKeys.encryptionKey)
            val wrappedMasterKey = when (wrapOutcome) {
                is Outcome.Success -> wrapOutcome.value
                is Outcome.Failure -> {
                    masterKey.fill(0)
                    derivedKeys.encryptionKey.fill(0)
                    return@withContext cryptoFailure(wrapOutcome.error.internalMessage)
                }
            }
            saveUser(wrappedMasterKey, masterKey, derivedKeys.encryptionKey)
        }
        if (result is Outcome.Failure) {
            this.saltRepository.delete()
        }
        return result
    }

    private suspend fun saveUser(
        wrappedMasterKey: ByteArray,
        masterKey: ByteArray,
        encryptionKey: ByteArray
    ): Outcome<User> {
        val user = User(
            id = UuidCreator.getTimeOrderedEpoch().toString(),
            wrappedMasterKey = wrappedMasterKey
        )
        val addOutcome = this.userRepository.add(user)
        if (addOutcome is Outcome.Failure) {
            masterKey.fill(0)
            encryptionKey.fill(0)
            return addOutcome
        }
        this.masterKeyRepository.store(masterKey)
        masterKey.fill(0)
        encryptionKey.fill(0)
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
