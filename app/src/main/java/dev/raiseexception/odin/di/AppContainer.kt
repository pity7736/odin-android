package dev.raiseexception.odin.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import dev.raiseexception.odin.BuildConfig
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.AccountTransactionLister
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.VaultAccountRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.VaultCategoryRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.VaultExpenseRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.VaultIncomeRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.VaultTransactionRunner
import dev.raiseexception.odin.accounting.presentation.accountcreation.CreateAccountViewModel
import dev.raiseexception.odin.accounting.presentation.accountdetail.AccountDetailViewModel
import dev.raiseexception.odin.accounting.presentation.accountslist.AccountsListViewModel
import dev.raiseexception.odin.accounting.presentation.categorieslist.CategoriesListViewModel
import dev.raiseexception.odin.accounting.presentation.categorycreation.CreateCategoryViewModel
import dev.raiseexception.odin.accounting.presentation.expensecreation.CreateExpenseViewModel
import dev.raiseexception.odin.accounting.presentation.incomecreation.CreateIncomeViewModel
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
import dev.raiseexception.odin.shared.domain.TransactionRunner
import dev.raiseexception.odin.shared.infrastructure.vault.EncryptedRecordStore
import dev.raiseexception.odin.shared.infrastructure.vault.InMemoryEncryptedRecordStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.security.SecureRandom

class AppContainer(context: Context) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
    private val accountLister: AccountLister = AccountLister(accountRepository)
    private val accountFinder: AccountFinder = AccountFinder(accountRepository)
    private val accountTransactionLister: AccountTransactionLister = AccountTransactionLister()
    private val categoryRepository: CategoryRepository = VaultCategoryRepository(encryptedRecordStore)
    private val categoryCreator: CategoryCreator = CategoryCreator(categoryRepository)
    private val categoryLister: CategoryLister = CategoryLister(categoryRepository)
    private val incomeRepository: IncomeRepository = VaultIncomeRepository(encryptedRecordStore)
    private val expenseRepository: ExpenseRepository = VaultExpenseRepository(encryptedRecordStore)
    private val transactionRunner: TransactionRunner = VaultTransactionRunner()
    private val incomeCreator: IncomeCreator = IncomeCreator(
        accountRepository = accountRepository,
        incomeRepository = incomeRepository,
        categoryRepository = categoryRepository,
        categoryCreator = categoryCreator,
        transactionRunner = transactionRunner
    )
    private val expenseCreator: ExpenseCreator = ExpenseCreator(
        accountRepository = accountRepository,
        expenseRepository = expenseRepository,
        categoryRepository = categoryRepository,
        categoryCreator = categoryCreator,
        transactionRunner = transactionRunner
    )

    fun registrationViewModel(): RegistrationViewModel = RegistrationViewModel(userRegistrar)

    fun loginViewModel(): LoginViewModel {
        if (BuildConfig.DEBUG) {
            val seeder = DevDataSeeder(accountCreator, categoryCreator, incomeCreator)
            return LoginViewModel(userAuthenticator, seeder::seed)
        }
        return LoginViewModel(userAuthenticator)
    }

    fun startupViewModel(): StartupViewModel = StartupViewModel(userRepository)

    fun createAccountViewModel(): CreateAccountViewModel = CreateAccountViewModel(accountCreator)

    fun accountsListViewModel(): AccountsListViewModel =
        AccountsListViewModel(accountLister, ioDispatcher)

    fun accountDetailViewModelFactory(accountId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                AccountDetailViewModel(accountId, accountFinder, accountTransactionLister, ioDispatcher)
            }
        }

    fun createCategoryViewModel(): CreateCategoryViewModel = CreateCategoryViewModel(categoryCreator)

    fun categoriesListViewModel(): CategoriesListViewModel =
        CategoriesListViewModel(categoryLister, ioDispatcher)

    fun createIncomeViewModelFactory(accountId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                CreateIncomeViewModel(accountId, incomeCreator, categoryLister, ioDispatcher)
            }
        }

    fun createExpenseViewModelFactory(accountId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                CreateExpenseViewModel(accountId, expenseCreator, categoryLister, ioDispatcher)
            }
        }
}
