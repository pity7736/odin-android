package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.application.usecase.CategoryResolver
import dev.raiseexception.odin.accounting.application.usecase.CreateAccountCommand
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.application.usecase.IncomeUpdater
import dev.raiseexception.odin.accounting.application.usecase.TagResolver
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.application.usecase.TransferCreator
import dev.raiseexception.odin.accounting.domain.IncomeUpdateError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
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
class IncomeUpdateIntegrationTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    private val fullCriteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
    private lateinit var database: OdinDatabase
    private lateinit var categoryRepository: RoomCategoryRepository
    private lateinit var accountCreator: AccountCreator
    private lateinit var accountFinder: AccountFinder
    private lateinit var categoryCreator: CategoryCreator
    private lateinit var incomeCreator: IncomeCreator
    private lateinit var expenseCreator: ExpenseCreator
    private lateinit var transferCreator: TransferCreator
    private lateinit var incomeUpdater: IncomeUpdater

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val accountRepository = RoomAccountRepository(database.accountDao())
        categoryRepository = RoomCategoryRepository(database.categoryDao())
        val transactionDao = database.transactionDao()
        val incomeRepository = RoomIncomeRepository(transactionDao)
        val expenseRepository = RoomExpenseRepository(transactionDao, database.expenseTagDao())
        val transactionRunner = RoomTransactionRunner(database)
        accountCreator = AccountCreator(accountRepository)
        accountFinder = AccountFinder(accountRepository)
        categoryCreator = CategoryCreator(categoryRepository)
        incomeCreator = IncomeCreator(
            accountRepository = accountRepository,
            incomeRepository = incomeRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = transactionRunner
        )
        expenseCreator = ExpenseCreator(
            accountRepository = accountRepository,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            tagResolver = TagResolver(RoomTagRepository(database.tagDao())),
            transactionRunner = transactionRunner
        )
        transferCreator = TransferCreator(
            accountRepository = accountRepository,
            transferRepository = RoomTransferRepository(database.transferDao()),
            categoryRepository = categoryRepository,
            expenseRepository = expenseRepository,
            incomeRepository = incomeRepository,
            transactionRunner = transactionRunner
        )
        incomeUpdater = IncomeUpdater(
            transactionFinder = TransactionFinder(RoomTransactionRepository(transactionDao, database.tagDao())),
            accountFinder = accountFinder,
            incomeRepository = incomeRepository,
            categoryResolver = CategoryResolver(categoryRepository, categoryCreator),
            transactionRunner = transactionRunner
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given an income, when updating its amount, then the account balance reflects the new amount`() = runTest {
        val account = createAccount("Ahorros", "0")
        val income = createIncome(account, "100000")
        createExpense(account, "80000")
        val result = incomeUpdater.update(
            incomeId = income.id,
            amount = "150000",
            date = today,
            categoryInput = CategoryInput.Existing(income.categoryId),
            description = "Salario"
        )
        assertTrue("Update should succeed: $result", result is Outcome.Success)
        assertEquals(0, loadBalance(account).compareTo(BigDecimal("70000")))
    }

    @Test
    fun `given a new category name and an amount below the floor, when updating, then nothing is saved`() =
        runTest {
            val account = createAccount("Ahorros", "0")
            val income = createIncome(account, "100000")
            createExpense(account, "80000")
            val result = incomeUpdater.update(
                incomeId = income.id,
                amount = "79999",
                date = today,
                categoryInput = CategoryInput.New("Bonos"),
                description = "Salario"
            )
            val error = (result as Outcome.Failure).error as IncomeUpdateError.InvalidInput
            assertEquals("El saldo de la cuenta no puede quedar negativo.", error.amountError)
            val categories = (categoryRepository.getAll().first() as Outcome.Success).value
            assertTrue(categories.none { it.name == "Bonos" })
            assertEquals(0, loadBalance(account).compareTo(BigDecimal("20000")))
        }

    @Test
    fun `given the income side of a transfer, when updating, then returns TransferNotEditable and nothing changes`() =
        runTest {
            val savings = createAccount("Ahorros", "100000")
            val cash = createAccount("Efectivo", "0")
            categoryCreator.createSystem("Transferencia", CategoryType.TRANSFER, "", "#607D8B")
            val transfer = (transferCreator.create(savings.id, cash.id, "30000", today) as Outcome.Success).value
            val result = incomeUpdater.update(
                incomeId = transfer.income.id,
                amount = "10000",
                date = today,
                categoryInput = CategoryInput.New("Bonos"),
                description = ""
            )
            assertTrue((result as Outcome.Failure).error is IncomeUpdateError.TransferNotEditable)
            assertEquals(0, loadBalance(cash).compareTo(BigDecimal("30000")))
            val categories = (categoryRepository.getAll().first() as Outcome.Success).value
            assertTrue(categories.none { it.name == "Bonos" })
        }

    private suspend fun createAccount(name: String, initialBalance: String): Account = (
        this.accountCreator.create(
            CreateAccountCommand.MoneyAccount(name, initialBalance, Currency.COP, MoneyAccountKind.SAVINGS, "")
        ) as Outcome.Success
        ).value

    private suspend fun createIncome(account: Account, amount: String): Income = (
        this.incomeCreator.create(
            accountId = account.id,
            amount = amount,
            date = this.today,
            categoryInput = CategoryInput.New("Salario"),
            description = "Salario"
        ) as Outcome.Success
        ).value

    private suspend fun createExpense(account: Account, amount: String) {
        val creationOutcome = this.expenseCreator.create(
            accountId = account.id,
            amount = amount,
            date = this.today,
            categoryInput = CategoryInput.New("Alimentación"),
            description = "Mercado",
            tagInputs = emptyList()
        )
        assertTrue("Expense creation should succeed: $creationOutcome", creationOutcome is Outcome.Success)
    }

    private suspend fun loadBalance(account: Account): BigDecimal =
        (this.accountFinder.find(account.id, this.fullCriteria).first() as Outcome.Success).value.balance.amount
}
