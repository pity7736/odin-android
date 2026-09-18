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
import dev.raiseexception.odin.accounting.application.usecase.CategoryFinder
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.application.usecase.TransferCreator
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.accounting.domain.repository.TransactionRepository
import dev.raiseexception.odin.accounting.domain.repository.TransferRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomAccountRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomCategoryRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomExpenseRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomIncomeRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomTransactionRepository
import dev.raiseexception.odin.accounting.infrastructure.repository.RoomTransferRepository
import dev.raiseexception.odin.accounting.presentation.accountcreation.CreateAccountViewModel
import dev.raiseexception.odin.accounting.presentation.accountdetail.AccountDetailViewModel
import dev.raiseexception.odin.accounting.presentation.accountslist.AccountsListViewModel
import dev.raiseexception.odin.accounting.presentation.categorieslist.CategoriesListViewModel
import dev.raiseexception.odin.accounting.presentation.categorycreation.CreateCategoryViewModel
import dev.raiseexception.odin.accounting.presentation.categorydetail.CategoryDetailViewModel
import dev.raiseexception.odin.accounting.presentation.expensecreation.CreateExpenseViewModel
import dev.raiseexception.odin.accounting.presentation.incomecreation.CreateIncomeViewModel
import dev.raiseexception.odin.accounting.presentation.transactiondetail.TransactionDetailViewModel
import dev.raiseexception.odin.accounting.presentation.transfercreation.CreateTransferViewModel
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
    private val userRegistrar: UserRegistrar by lazy {
        UserRegistrar(
            vaultCrypto,
            userRepository,
            masterKeyRepository,
            saltRepository,
            databaseProvider,
            postRegistration = {
                val outcome = this.categoryCreator.createSystem(
                    name = "Transferencia",
                    type = CategoryType.TRANSFER,
                    description = "",
                    color = "#607D8B"
                )
                when (outcome) {
                    is Outcome.Success -> Outcome.Success(Unit)
                    is Outcome.Failure -> Outcome.Failure(outcome.error)
                }
            }
        )
    }
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
    private val categoryFinder by lazy { CategoryFinder(categoryRepository) }
    private val categoryLister by lazy { CategoryLister(categoryRepository) }
    private val incomeRepository: IncomeRepository by lazy {
        RoomIncomeRepository(databaseProvider.requireDatabase().transactionDao())
    }
    private val expenseRepository: ExpenseRepository by lazy {
        RoomExpenseRepository(databaseProvider.requireDatabase().transactionDao())
    }
    private val transactionRepository: TransactionRepository by lazy {
        RoomTransactionRepository(databaseProvider.requireDatabase().transactionDao())
    }
    private val transactionFinder by lazy { TransactionFinder(transactionRepository) }
    private val transferRepository: TransferRepository by lazy {
        RoomTransferRepository(databaseProvider.requireDatabase().transferDao())
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
    private val transferCreator by lazy {
        TransferCreator(
            accountRepository = accountRepository,
            transferRepository = transferRepository,
            categoryRepository = categoryRepository,
            expenseRepository = expenseRepository,
            incomeRepository = incomeRepository,
            transactionRunner = transactionRunner
        )
    }

    fun registrationViewModel(): RegistrationViewModel = RegistrationViewModel(userRegistrar)

    fun loginViewModel(): LoginViewModel {
        if (BuildConfig.DEBUG) {
            return LoginViewModel(userAuthenticator) {
                DevDataSeeder(
                    accountRepository,
                    categoryCreator,
                    incomeCreator,
                    accountLister
                ).seed()
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

    fun categoryDetailViewModelFactory(categoryId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                CategoryDetailViewModel(categoryId, categoryFinder, ioDispatcher)
            }
        }

    fun transactionDetailViewModelFactory(transactionId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                TransactionDetailViewModel(transactionId, transactionFinder, ioDispatcher)
            }
        }

    fun createIncomeViewModelFactory(accountId: String?): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                CreateIncomeViewModel(
                    accountId,
                    incomeCreator,
                    categoryLister,
                    accountFinder,
                    accountLister,
                    ioDispatcher,
                )
            }
        }

    fun createExpenseViewModelFactory(accountId: String?): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                CreateExpenseViewModel(
                    accountId,
                    expenseCreator,
                    categoryLister,
                    accountFinder,
                    accountLister,
                    ioDispatcher,
                )
            }
        }

    fun createTransferViewModelFactory(accountId: String?): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                CreateTransferViewModel(accountId, transferCreator, accountLister, ioDispatcher)
            }
        }
}

private class DeferredUserRepository(
    private val databaseProvider: DatabaseProvider
) : UserRepository {

    private val delegate by lazy {
        RoomUserRepository(databaseProvider.requireDatabase().userDao())
    }

    override suspend fun add(user: User): Outcome<Unit> = this.delegate.add(user)

    override suspend fun get(): Outcome<User> = this.delegate.get()
}
