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
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val postRegistration: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
) {

    suspend fun register(password: SensitivePassword, confirmation: SensitivePassword): Outcome<User> {
        try {
            return this.performValidationAndRegistration(password, confirmation)
        } finally {
            password.wipe()
            confirmation.wipe()
        }
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
        val keysOutcome = withContext(this.cpuDispatcher) { vaultCrypto.deriveKeys(password, salt) }
        val derivedKeys = when (keysOutcome) {
            is Outcome.Success -> keysOutcome.value
            is Outcome.Failure -> return this.cryptoFailure(keysOutcome.error.internalMessage)
        }
        try {
            return this.setUpVault(salt, derivedKeys.encryptionKey)
        } finally {
            derivedKeys.encryptionKey.fill(0)
        }
    }

    private suspend fun setUpVault(salt: ByteArray, encryptionKey: ByteArray): Outcome<User> {
        val discardOutcome = this.vaultUnlocker.discard()
        if (discardOutcome is Outcome.Failure) {
            return this.storageFailure("Failed to discard leftover vault: ${discardOutcome.error.internalMessage}")
        }
        val creationOutcome = this.createVault(salt, encryptionKey)
        if (creationOutcome is Outcome.Failure) {
            this.vaultUnlocker.discard()
        }
        return creationOutcome
    }

    private suspend fun createVault(salt: ByteArray, encryptionKey: ByteArray): Outcome<User> {
        val unlockOutcome = this.vaultUnlocker.unlock(encryptionKey)
        if (unlockOutcome is Outcome.Failure) {
            return this.cryptoFailure(unlockOutcome.error.internalMessage)
        }
        val masterKey = withContext(this.cpuDispatcher) { vaultCrypto.generateMasterKey() }
        try {
            return this.persistRegistration(salt, masterKey, encryptionKey)
        } finally {
            masterKey.fill(0)
        }
    }

    private suspend fun persistRegistration(
        salt: ByteArray,
        masterKey: ByteArray,
        encryptionKey: ByteArray
    ): Outcome<User> {
        val wrapOutcome = withContext(this.cpuDispatcher) { vaultCrypto.wrapMasterKey(masterKey, encryptionKey) }
        val wrappedMasterKey = when (wrapOutcome) {
            is Outcome.Success -> wrapOutcome.value
            is Outcome.Failure -> return this.cryptoFailure(wrapOutcome.error.internalMessage)
        }
        val user = User(
            id = UuidCreator.getTimeOrderedEpoch().toString(),
            wrappedMasterKey = wrappedMasterKey
        )
        val addOutcome = this.userRepository.add(user)
        if (addOutcome is Outcome.Failure) {
            return this.storageFailure("Failed to save the user: ${addOutcome.error.internalMessage}")
        }
        val postRegistrationOutcome = this.postRegistration()
        if (postRegistrationOutcome is Outcome.Failure) {
            return this.storageFailure(
                "Post-registration callback failed: ${postRegistrationOutcome.error.internalMessage}"
            )
        }
        val saveOutcome = this.saltRepository.save(salt)
        if (saveOutcome is Outcome.Failure) {
            return this.storageFailure("Failed to save the salt: ${saveOutcome.error.internalMessage}")
        }
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

    private fun storageFailure(internalMessage: String) = Outcome.Failure(
        RegistrationError.StorageFailure(
            internalMessage = internalMessage,
            externalMessage = "Algo salió mal. Intente de nuevo más tarde"
        )
    )

    private fun cryptoFailure(internalMessage: String) = Outcome.Failure(
        RegistrationError.CryptoFailure(
            internalMessage = internalMessage,
            externalMessage = "Algo salió mal. Intente de nuevo más tarde"
        )
    )
}
