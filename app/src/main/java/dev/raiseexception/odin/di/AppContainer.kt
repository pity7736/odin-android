package dev.raiseexception.odin.di

import dev.raiseexception.odin.accounts.application.usecase.UserAuthenticator
import dev.raiseexception.odin.accounts.application.usecase.UserRegistrar
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.accounts.infrastructure.repository.InMemoryUserRepository
import dev.raiseexception.odin.accounts.presentation.login.LoginViewModel
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationViewModel
import dev.raiseexception.odin.accounts.presentation.startup.StartupViewModel
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.crypto.infrastructure.InMemoryMasterKeyRepository
import java.security.SecureRandom

class AppContainer {

    private val secureRandom: SecureRandom = SecureRandom()
    private val vaultCrypto: VaultCrypto = BouncyCastleVaultCrypto(secureRandom)
    private val masterKeyRepository: MasterKeyRepository = InMemoryMasterKeyRepository()
    private val userRepository: UserRepository = InMemoryUserRepository()
    private val userRegistrar: UserRegistrar = UserRegistrar(vaultCrypto, userRepository, masterKeyRepository)
    private val userAuthenticator: UserAuthenticator =
        UserAuthenticator(vaultCrypto, userRepository, masterKeyRepository)

    fun registrationViewModel(): RegistrationViewModel = RegistrationViewModel(userRegistrar)

    fun loginViewModel(): LoginViewModel = LoginViewModel(userAuthenticator)

    fun startupViewModel(): StartupViewModel = StartupViewModel(userRepository)
}
