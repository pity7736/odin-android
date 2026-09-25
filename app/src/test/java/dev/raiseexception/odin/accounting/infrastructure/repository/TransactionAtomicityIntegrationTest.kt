package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.application.usecase.CreateAccountCommand
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.application.usecase.TransferCreator
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.shared.infrastructure.persistence.RoomTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class TransactionAtomicityIntegrationTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    private val failingIncomeRepository = object : IncomeRepository {
        override suspend fun add(income: Income): Outcome<Unit> =
            Outcome.Failure(StorageError("Simulated income storage failure"))
    }
    private lateinit var database: OdinDatabase
    private lateinit var accountRepository: RoomAccountRepository
    private lateinit var categoryRepository: RoomCategoryRepository
    private lateinit var accountCreator: AccountCreator
    private lateinit var categoryCreator: CategoryCreator
    private lateinit var expenseCreator: ExpenseCreator
    private lateinit var incomeCreator: IncomeCreator
    private lateinit var transferCreator: TransferCreator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountRepository = RoomAccountRepository(database.accountDao())
        categoryRepository = RoomCategoryRepository(database.categoryDao())
        val transactionDao = database.transactionDao()
        val expenseRepository = RoomExpenseRepository(transactionDao)
        val transactionRunner = RoomTransactionRunner(database)
        accountCreator = AccountCreator(accountRepository)
        categoryCreator = CategoryCreator(categoryRepository)
        expenseCreator = ExpenseCreator(
            accountRepository = accountRepository,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = transactionRunner
        )
        incomeCreator = IncomeCreator(
            accountRepository = accountRepository,
            incomeRepository = RoomIncomeRepository(transactionDao),
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = transactionRunner
        )
        transferCreator = TransferCreator(
            accountRepository = accountRepository,
            transferRepository = RoomTransferRepository(database.transferDao()),
            categoryRepository = categoryRepository,
            expenseRepository = expenseRepository,
            incomeRepository = failingIncomeRepository,
            transactionRunner = transactionRunner
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given a new category and a zero amount, when creating an expense, then nothing is saved`() =
        runTest {
            val account = createAccount("Ahorros", "1000000")
            val result = expenseCreator.create(
                accountId = account.id,
                amount = "0",
                date = today,
                categoryInput = CategoryInput.New("Mascotas"),
                description = ""
            )
            assertTrue(
                "Expected InvalidInput: $result",
                (result as Outcome.Failure).error is ExpenseCreationError.InvalidInput
            )
            assertEquals(
                Outcome.Success(false),
                categoryRepository.existsByNameAndType("Mascotas", CategoryType.EXPENSE)
            )
            val loadedAccount = loadAccount(account.id)
            assertTrue(loadedAccount.transactions.isEmpty())
            assertEquals(0, loadedAccount.balance.amount.compareTo(BigDecimal("1000000")))
        }

    @Test
    fun `given a new category and a zero amount, when creating an income, then nothing is saved`() =
        runTest {
            val account = createAccount("Ahorros", "1000000")
            val result = incomeCreator.create(
                accountId = account.id,
                amount = "0",
                date = today,
                categoryInput = CategoryInput.New("Mascotas"),
                description = ""
            )
            assertTrue(
                "Expected InvalidInput: $result",
                (result as Outcome.Failure).error is IncomeCreationError.InvalidInput
            )
            assertEquals(
                Outcome.Success(false),
                categoryRepository.existsByNameAndType("Mascotas", CategoryType.INCOME)
            )
            val loadedAccount = loadAccount(account.id)
            assertTrue(loadedAccount.transactions.isEmpty())
            assertEquals(0, loadedAccount.balance.amount.compareTo(BigDecimal("1000000")))
        }

    @Test
    fun `given the income entry fails to save, when creating a transfer, then nothing of the transfer is recorded`() =
        runTest {
            val sourceAccount = createAccount("Ahorros", "1000000")
            val destinationAccount = createAccount("Efectivo", "50000")
            categoryCreator.createSystem("Transferencia", CategoryType.TRANSFER, "", "#607D8B")
            val result = transferCreator.create(
                sourceAccountId = sourceAccount.id,
                destinationAccountId = destinationAccount.id,
                amount = "100000",
                date = today
            )
            assertTrue(
                "Expected StorageFailure: $result",
                (result as Outcome.Failure).error is TransferCreationError.StorageFailure
            )
            val loadedSourceAccount = loadAccount(sourceAccount.id)
            val loadedDestinationAccount = loadAccount(destinationAccount.id)
            assertTrue(loadedSourceAccount.transactions.isEmpty())
            assertTrue(loadedDestinationAccount.transactions.isEmpty())
            assertEquals(0, loadedSourceAccount.balance.amount.compareTo(BigDecimal("1000000")))
            assertEquals(0, loadedDestinationAccount.balance.amount.compareTo(BigDecimal("50000")))
            assertEquals(0, countTransferRows())
        }

    private suspend fun createAccount(name: String, initialBalance: String): Account =
        (
            accountCreator.create(
                CreateAccountCommand.MoneyAccount(name, initialBalance, Currency.COP, AccountType.SAVINGS, "")
            ) as Outcome.Success
            ).value

    private suspend fun loadAccount(accountId: String): Account =
        (
            accountRepository.findById(
                accountId,
                AccountCriteria(includeIncomes = true, includeExpenses = true)
            ).first() as Outcome.Success
            ).value

    private fun countTransferRows(): Int =
        database.query("SELECT COUNT(*) FROM transfers", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
