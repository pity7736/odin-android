package dev.raiseexception.odin.di

import android.content.Context
import androidx.room.Room
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.VaultAccountRepository
import dev.raiseexception.odin.accounting.presentation.accountcreation.CreateAccountViewModel
import dev.raiseexception.odin.accounts.application.usecase.UserAuthenticator
import dev.raiseexception.odin.accounts.application.usecase.UserRegistrar
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.accounts.infrastructure.repository.RoomUserRepository
import dev.raiseexception.odin.accounts.presentation.login.LoginViewModel
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationViewModel
import dev.raiseexception.odin.accounts.presentation.startup.StartupViewModel
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.crypto.infrastructure.InMemoryMasterKeyRepository
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.infrastructure.vault.EncryptedRecordStore
import dev.raiseexception.odin.shared.infrastructure.vault.InMemoryEncryptedRecordStore
import java.security.SecureRandom

class AppContainer(context: Context) {

    private val database: OdinDatabase = Room.databaseBuilder(
        context,
        OdinDatabase::class.java,
        "odin_db"
    ).build()
    private val secureRandom: SecureRandom = SecureRandom()
    private val vaultCrypto: VaultCrypto = BouncyCastleVaultCrypto(secureRandom)
    private val masterKeyRepository: MasterKeyRepository = InMemoryMasterKeyRepository()
    private val userRepository: UserRepository = RoomUserRepository(database.userDao())
    private val userRegistrar: UserRegistrar = UserRegistrar(vaultCrypto, userRepository, masterKeyRepository)
    private val userAuthenticator: UserAuthenticator =
        UserAuthenticator(vaultCrypto, userRepository, masterKeyRepository)
    private val encryptedRecordStore: EncryptedRecordStore =
        InMemoryEncryptedRecordStore(vaultCrypto, masterKeyRepository)
    private val accountRepository: AccountRepository = VaultAccountRepository(encryptedRecordStore)
    private val accountCreator: AccountCreator = AccountCreator(accountRepository)

    fun registrationViewModel(): RegistrationViewModel = RegistrationViewModel(userRegistrar)

    fun loginViewModel(): LoginViewModel = LoginViewModel(userAuthenticator)

    fun startupViewModel(): StartupViewModel = StartupViewModel(userRepository)

    fun createAccountViewModel(): CreateAccountViewModel = CreateAccountViewModel(accountCreator)
}
