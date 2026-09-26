package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.application.usecase.CreateAccountCommand
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.application.usecase.ExpenseUpdater
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.application.usecase.TransferCreator
import dev.raiseexception.odin.accounting.domain.ExpenseUpdateError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
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
class ExpenseUpdateIntegrationTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    private val fullCriteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
    private lateinit var database: OdinDatabase
    private lateinit var categoryRepository: RoomCategoryRepository
    private lateinit var accountCreator: AccountCreator
    private lateinit var accountFinder: AccountFinder
    private lateinit var categoryCreator: CategoryCreator
    private lateinit var expenseCreator: ExpenseCreator
    private lateinit var transferCreator: TransferCreator
    private lateinit var transactionFinder: TransactionFinder
    private lateinit var expenseUpdater: ExpenseUpdater

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val accountRepository = RoomAccountRepository(database.accountDao())
        categoryRepository = RoomCategoryRepository(database.categoryDao())
        val transactionDao = database.transactionDao()
        val expenseRepository = RoomExpenseRepository(transactionDao)
        val transactionRunner = RoomTransactionRunner(database)
        accountCreator = AccountCreator(accountRepository)
        accountFinder = AccountFinder(accountRepository)
        categoryCreator = CategoryCreator(categoryRepository)
        transactionFinder = TransactionFinder(RoomTransactionRepository(transactionDao))
        expenseCreator = ExpenseCreator(
            accountRepository = accountRepository,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = transactionRunner
        )
        transferCreator = TransferCreator(
            accountRepository = accountRepository,
            transferRepository = RoomTransferRepository(database.transferDao()),
            categoryRepository = categoryRepository,
            expenseRepository = expenseRepository,
            incomeRepository = RoomIncomeRepository(transactionDao),
            transactionRunner = transactionRunner
        )
        expenseUpdater = ExpenseUpdater(
            transactionFinder = transactionFinder,
            accountFinder = accountFinder,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = transactionRunner
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given an expense, when updating its amount, then the account balance reflects the new amount`() = runTest {
        val account = createAccount("Ahorros", "100000")
        val expense = createExpense(account, "30000")
        val result = expenseUpdater.update(
            expenseId = expense.id,
            amount = "90000",
            date = today,
            categoryInput = CategoryInput.Existing(expense.categoryId),
            description = "Mercado"
        )
        assertTrue("Update should succeed: $result", result is Outcome.Success)
        assertEquals(0, loadBalance(account).compareTo(BigDecimal("10000")))
    }

    @Test
    fun `given a new category name and an amount above the available money, when updating, then nothing is saved`() =
        runTest {
            val account = createAccount("Ahorros", "100000")
            val expense = createExpense(account, "30000")
            val result = expenseUpdater.update(
                expenseId = expense.id,
                amount = "100001",
                date = today,
                categoryInput = CategoryInput.New("Viajes"),
                description = "Mercado"
            )
            val error = (result as Outcome.Failure).error
            assertTrue(error is ExpenseUpdateError.InvalidInput)
            val categories = (categoryRepository.getAll().first() as Outcome.Success).value
            assertTrue(categories.none { it.name == "Viajes" })
            assertEquals(0, loadBalance(account).compareTo(BigDecimal("70000")))
        }

    @Test
    fun `given the expense side of a transfer, when updating, then returns TransferNotEditable and nothing changes`() =
        runTest {
            val savings = createAccount("Ahorros", "100000")
            val cash = createAccount("Efectivo", "0")
            categoryCreator.createSystem("Transferencia", CategoryType.TRANSFER, "", "#607D8B")
            val transfer = (transferCreator.create(savings.id, cash.id, "30000", today) as Outcome.Success).value
            val result = expenseUpdater.update(
                expenseId = transfer.expense.id,
                amount = "10000",
                date = today,
                categoryInput = CategoryInput.New("Viajes"),
                description = ""
            )
            assertTrue((result as Outcome.Failure).error is ExpenseUpdateError.TransferNotEditable)
            assertEquals(0, loadBalance(savings).compareTo(BigDecimal("70000")))
            val categories = (categoryRepository.getAll().first() as Outcome.Success).value
            assertTrue(categories.none { it.name == "Viajes" })
        }

    private suspend fun createAccount(name: String, initialBalance: String): Account = (
        this.accountCreator.create(
            CreateAccountCommand.MoneyAccount(name, initialBalance, Currency.COP, AccountType.SAVINGS, "")
        ) as Outcome.Success
        ).value

    private suspend fun createExpense(account: Account, amount: String): Expense = (
        this.expenseCreator.create(
            accountId = account.id,
            amount = amount,
            date = this.today,
            categoryInput = CategoryInput.New("Alimentación"),
            description = "Mercado"
        ) as Outcome.Success
        ).value

    private suspend fun loadBalance(account: Account): BigDecimal =
        (this.accountFinder.find(account.id, this.fullCriteria).first() as Outcome.Success).value.balance.amount
}
