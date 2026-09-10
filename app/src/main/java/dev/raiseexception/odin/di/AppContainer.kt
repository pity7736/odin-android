package dev.raiseexception.odin.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomAccountRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomCategoryRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomExpenseRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomIncomeRepository
import dev.raiseexception.odin.accounting.presentation.accountcreation.CreateAccountViewModel
import dev.raiseexception.odin.accounting.presentation.accountdetail.AccountDetailViewModel
import dev.raiseexception.odin.accounting.presentation.accountslist.AccountsListViewModel
import dev.raiseexception.odin.accounting.presentation.categorieslist.CategoriesListViewModel
import dev.raiseexception.odin.accounting.presentation.categorycreation.CreateCategoryViewModel
import dev.raiseexception.odin.accounting.presentation.expensecreation.CreateExpenseViewModel
import dev.raiseexception.odin.accounting.presentation.incomecreation.CreateIncomeViewModel
import dev.raiseexception.odin.accounts.application.usecase.UserAuthenticator
import dev.raiseexception.odin.accounts.application.usecase.UserRegistrar
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.accounts.infrastructure.repository.RoomUserRepository
import dev.raiseexception.odin.accounts.presentation.login.LoginViewModel
import dev.raiseexception.odin.accounts.presentation.registration.RegistrationViewModel
import dev.raiseexception.odin.accounts.presentation.startup.StartupViewModel
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.domain.repository.SaltRepository
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.crypto.infrastructure.DataStoreSaltRepository
import dev.raiseexception.odin.crypto.infrastructure.InMemoryMasterKeyRepository
import dev.raiseexception.odin.home.application.usecase.RecentTransactionLister
import dev.raiseexception.odin.home.presentation.home.HomeViewModel
import dev.raiseexception.odin.persistence.DatabaseProvider
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner
import dev.raiseexception.odin.shared.infrastructure.persistence.RoomTransactionRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.security.SecureRandom

private val Context.saltDataStore: DataStore<Preferences> by preferencesDataStore(name = "odin_salt")

@Suppress("TooManyFunctions")
class AppContainer(context: Context) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val databaseProvider: DatabaseProvider = DatabaseProvider(context)
    private val secureRandom: SecureRandom = SecureRandom()
    private val vaultCrypto: VaultCrypto = BouncyCastleVaultCrypto(secureRandom)
    private val masterKeyRepository: MasterKeyRepository = InMemoryMasterKeyRepository()
    private val saltRepository: SaltRepository = DataStoreSaltRepository(context.saltDataStore)
    private val userRepository: UserRepository = DeferredUserRepository(databaseProvider)
    private val userRegistrar: UserRegistrar = UserRegistrar(
        vaultCrypto,
        userRepository,
        masterKeyRepository,
        saltRepository,
        databaseProvider
    )
    private val userAuthenticator: UserAuthenticator = UserAuthenticator(
        vaultCrypto,
        userRepository,
        masterKeyRepository,
        saltRepository,
        databaseProvider
    )
    private val accountRepository: AccountRepository by lazy {
        RoomAccountRepository(databaseProvider.requireDatabase().accountDao())
    }
    private val accountCreator by lazy { AccountCreator(accountRepository) }
    private val accountLister by lazy { AccountLister(accountRepository) }
    private val accountFinder by lazy { AccountFinder(accountRepository) }
    private val accountTransactionLister by lazy { AccountTransactionLister() }
    private val recentTransactionLister by lazy { RecentTransactionLister() }
    private val categoryRepository: CategoryRepository by lazy {
        RoomCategoryRepository(databaseProvider.requireDatabase().categoryDao())
    }
    private val categoryCreator by lazy { CategoryCreator(categoryRepository) }
    private val categoryLister by lazy { CategoryLister(categoryRepository) }
    private val incomeRepository: IncomeRepository by lazy {
        RoomIncomeRepository(databaseProvider.requireDatabase().transactionDao())
    }
    private val expenseRepository: ExpenseRepository by lazy {
        RoomExpenseRepository(databaseProvider.requireDatabase().transactionDao())
    }
    private val transactionRunner: TransactionRunner by lazy {
        RoomTransactionRunner(databaseProvider.requireDatabase())
    }
    private val incomeCreator by lazy {
        IncomeCreator(
            accountRepository = accountRepository,
            incomeRepository = incomeRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = transactionRunner
        )
    }
    private val expenseCreator by lazy {
        ExpenseCreator(
            accountRepository = accountRepository,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = transactionRunner
        )
    }

    fun registrationViewModel(): RegistrationViewModel = RegistrationViewModel(userRegistrar)

    fun loginViewModel(): LoginViewModel {
        if (BuildConfig.DEBUG) {
            return LoginViewModel(userAuthenticator) {
                DevDataSeeder(accountCreator, categoryCreator, incomeCreator, accountLister).seed()
            }
        }
        return LoginViewModel(userAuthenticator)
    }

    fun startupViewModel(): StartupViewModel = StartupViewModel(saltRepository)

    fun createAccountViewModel(): CreateAccountViewModel = CreateAccountViewModel(accountCreator)

    fun accountsListViewModel(): AccountsListViewModel =
        AccountsListViewModel(accountLister, ioDispatcher)

    fun accountDetailViewModelFactory(accountId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                AccountDetailViewModel(accountId, accountFinder, accountTransactionLister, ioDispatcher)
            }
        }

    fun homeViewModel(): HomeViewModel = HomeViewModel(accountLister, recentTransactionLister, ioDispatcher)

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

private class DeferredUserRepository(
    private val databaseProvider: DatabaseProvider
) : UserRepository {

    private val delegate by lazy { RoomUserRepository(databaseProvider.requireDatabase().userDao()) }

    override suspend fun add(user: User): Outcome<Unit> = this.delegate.add(user)

    override suspend fun get(): Outcome<User> = this.delegate.get()
}
