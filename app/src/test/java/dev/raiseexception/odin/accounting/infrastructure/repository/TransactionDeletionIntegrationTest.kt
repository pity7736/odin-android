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
import dev.raiseexception.odin.accounting.application.usecase.GmfInput
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.application.usecase.TagResolver
import dev.raiseexception.odin.accounting.application.usecase.TransactionDeleter
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.TransactionDeletionError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.TagRepository
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
class TransactionDeletionIntegrationTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    private val fullCriteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
    private lateinit var database: OdinDatabase
    private lateinit var categoryRepository: RoomCategoryRepository
    private lateinit var tagRepository: RoomTagRepository
    private lateinit var accountCreator: AccountCreator
    private lateinit var accountFinder: AccountFinder
    private lateinit var expenseCreator: ExpenseCreator
    private lateinit var incomeCreator: IncomeCreator
    private lateinit var transactionDeleter: TransactionDeleter
    private lateinit var failingCleanupTransactionDeleter: TransactionDeleter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val accountRepository = RoomAccountRepository(database.accountDao())
        categoryRepository = RoomCategoryRepository(database.categoryDao())
        val transactionDao = database.transactionDao()
        val expenseRepository = RoomExpenseRepository(transactionDao, database.expenseTagDao())
        val incomeRepository = RoomIncomeRepository(transactionDao)
        val transactionRunner = RoomTransactionRunner(database)
        val categoryCreator = CategoryCreator(categoryRepository)
        val transactionFinder = TransactionFinder(RoomTransactionRepository(transactionDao, database.tagDao()))
        tagRepository = RoomTagRepository(database.tagDao())
        accountCreator = AccountCreator(
            accountRepository,
            CategoryResolver(categoryRepository, categoryCreator),
            transactionRunner
        )
        accountFinder = AccountFinder(accountRepository)
        expenseCreator = ExpenseCreator(
            accountRepository = accountRepository,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            tagResolver = TagResolver(tagRepository),
            transactionRunner = transactionRunner
        )
        incomeCreator = IncomeCreator(
            accountRepository = accountRepository,
            incomeRepository = incomeRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            transactionRunner = transactionRunner
        )
        transactionDeleter = TransactionDeleter(
            transactionFinder = transactionFinder,
            accountFinder = accountFinder,
            incomeRepository = incomeRepository,
            expenseRepository = expenseRepository,
            tagRepository = tagRepository,
            transactionRunner = transactionRunner
        )
        failingCleanupTransactionDeleter = TransactionDeleter(
            transactionFinder = transactionFinder,
            accountFinder = accountFinder,
            incomeRepository = incomeRepository,
            expenseRepository = expenseRepository,
            tagRepository = FailingCleanupTagRepository(tagRepository),
            transactionRunner = transactionRunner
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given a tag used only on one expense, when deleting that expense, then the tag is removed`() = runTest {
        val account = createAccount("100000")
        val expense = createExpense(account, "30000", listOf(TagInput.New("Nala")))
        val result = transactionDeleter.delete(expense.id)
        assertEquals(Outcome.Success(Unit), result)
        assertTrue(storedTagNames().isEmpty())
    }

    @Test
    fun `given a tag on two expenses, when deleting one of them, then the tag is kept`() = runTest {
        val account = createAccount("100000")
        val firstExpense = createExpense(account, "30000", listOf(TagInput.New("Nala")))
        val nalaTagId = linkedTagIds(firstExpense.id).single()
        val secondExpense = createExpense(
            account,
            "10000",
            listOf(TagInput.Existing(nalaTagId)),
            CategoryInput.Existing(firstExpense.categoryId)
        )
        val result = transactionDeleter.delete(firstExpense.id)
        assertEquals(Outcome.Success(Unit), result)
        assertEquals(listOf("Nala"), storedTagNames())
        assertEquals(listOf(nalaTagId), linkedTagIds(secondExpense.id))
    }

    @Test
    fun `given an expense with the only use of its category, when deleting it, then the category is kept`() =
        runTest {
            val account = createAccount("100000")
            val expense = createExpense(account, "30000", emptyList(), CategoryInput.New("Mascotas"))
            val result = transactionDeleter.delete(expense.id)
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(
                Outcome.Success(true),
                categoryRepository.existsByNameAndType("Mascotas", CategoryType.EXPENSE)
            )
        }

    @Test
    fun `given an income paying an expense, when deleting the income, then it is rejected and the row stays`() =
        runTest {
            val account = createAccount("0")
            val income = createIncome(account, "100000")
            createExpense(account, "80000", emptyList())
            val result = transactionDeleter.delete(income.id)
            assertTrue((result as Outcome.Failure).error is TransactionDeletionError.HistoryBreach)
            val loadedAccount = loadAccount(account)
            assertEquals(listOf(income.id), loadedAccount.incomes.map { it.id })
            assertEquals(0, loadedAccount.balance.amount.compareTo(BigDecimal("20000")))
        }

    @Test
    fun `given a failing step after the row is deleted, when deleting an expense, then the delete is rolled back`() =
        runTest {
            val account = createAccount("100000")
            val expense = createExpense(account, "30000", listOf(TagInput.New("Nala")))
            val nalaTagId = linkedTagIds(expense.id).single()
            val result = failingCleanupTransactionDeleter.delete(expense.id)
            assertTrue((result as Outcome.Failure).error is TransactionDeletionError.StorageFailure)
            assertEquals(listOf(expense.id), loadAccount(account).expenses.map { it.id })
            assertEquals(listOf(nalaTagId), linkedTagIds(expense.id))
            assertEquals(listOf("Nala"), storedTagNames())
        }

    @Test
    fun `given an expense of 30000 on 100000, when deleting it, then the balance is 100000`() = runTest {
        val account = createAccount("100000")
        val expense = createExpense(account, "30000", emptyList())
        val result = transactionDeleter.delete(expense.id)
        assertEquals(Outcome.Success(Unit), result)
        val loadedAccount = loadAccount(account)
        assertTrue(loadedAccount.expenses.isEmpty())
        assertEquals(0, loadedAccount.balance.amount.compareTo(BigDecimal("100000")))
    }

    private suspend fun createAccount(initialBalance: String): Account = (
        this.accountCreator.create(
            CreateAccountCommand.MoneyAccount(
                "Ahorros",
                initialBalance,
                Currency.COP,
                MoneyAccountKind.SAVINGS,
                "",
                GmfInput.Exempt
            )
        ) as Outcome.Success
        ).value

    private suspend fun createExpense(
        account: Account,
        amount: String,
        tagInputs: List<TagInput>,
        categoryInput: CategoryInput = CategoryInput.New("Perros")
    ): Expense = (
        this.expenseCreator.create(
            accountId = account.id,
            amount = amount,
            date = this.today,
            categoryInput = categoryInput,
            description = "",
            tagInputs = tagInputs
        ) as Outcome.Success
        ).value

    private suspend fun createIncome(account: Account, amount: String): Income = (
        this.incomeCreator.create(
            accountId = account.id,
            amount = amount,
            date = this.today,
            categoryInput = CategoryInput.New("Salario"),
            description = ""
        ) as Outcome.Success
        ).value

    private suspend fun loadAccount(account: Account): Account =
        (this.accountFinder.find(account.id, this.fullCriteria).first() as Outcome.Success).value

    private suspend fun storedTagNames(): List<String> =
        (this.tagRepository.getAll().first() as Outcome.Success).value.map { it.name }

    private suspend fun linkedTagIds(expenseId: String): List<String> =
        this.database.tagDao().findByExpenseId(expenseId).first().map { it.id }

    private class FailingCleanupTagRepository(
        private val delegate: TagRepository
    ) : TagRepository by delegate {

        override suspend fun deleteUnused(): Outcome<Unit> =
            Outcome.Failure(StorageError("cleanup failed"))
    }
}
