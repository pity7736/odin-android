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
import dev.raiseexception.odin.accounting.application.usecase.TagResolver
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.model.TagInput
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

@RunWith(RobolectricTestRunner::class)
class ExpenseTagsIntegrationTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    private lateinit var database: OdinDatabase
    private lateinit var tagRepository: RoomTagRepository
    private lateinit var accountCreator: AccountCreator
    private lateinit var expenseCreator: ExpenseCreator
    private lateinit var expenseUpdater: ExpenseUpdater
    private lateinit var failingCleanupExpenseUpdater: ExpenseUpdater

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val accountRepository = RoomAccountRepository(database.accountDao())
        val categoryRepository = RoomCategoryRepository(database.categoryDao())
        val transactionDao = database.transactionDao()
        val expenseRepository = RoomExpenseRepository(transactionDao, database.expenseTagDao())
        val transactionRunner = RoomTransactionRunner(database)
        val categoryCreator = CategoryCreator(categoryRepository)
        val transactionFinder = TransactionFinder(RoomTransactionRepository(transactionDao, database.tagDao()))
        val accountFinder = AccountFinder(accountRepository)
        tagRepository = RoomTagRepository(database.tagDao())
        val tagResolver = TagResolver(tagRepository)
        val failingCleanupTagRepository = FailingCleanupTagRepository(tagRepository)
        accountCreator = AccountCreator(accountRepository)
        expenseCreator = ExpenseCreator(
            accountRepository = accountRepository,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            tagResolver = tagResolver,
            transactionRunner = transactionRunner
        )
        expenseUpdater = ExpenseUpdater(
            transactionFinder = transactionFinder,
            accountFinder = accountFinder,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            tagResolver = tagResolver,
            tagRepository = tagRepository,
            transactionRunner = transactionRunner
        )
        failingCleanupExpenseUpdater = ExpenseUpdater(
            transactionFinder = transactionFinder,
            accountFinder = accountFinder,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            tagResolver = TagResolver(failingCleanupTagRepository),
            tagRepository = failingCleanupTagRepository,
            transactionRunner = transactionRunner
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given no tag named Carro, when creating an expense with it, then the tag and its link are stored`() =
        runTest {
            val account = createAccount("100000")
            val expense = createExpense(account, "30000", listOf(TagInput.New("Carro")))
            assertEquals(listOf("Carro"), storedTagNames())
            assertEquals(1, linkedTagIds(expense.id).size)
        }

    @Test
    fun `given an expense over the available money, when creating it with a new tag, then no tag is created`() =
        runTest {
            val account = createAccount("100000")
            val result = expenseCreator.create(
                accountId = account.id,
                amount = "100001",
                date = today,
                categoryInput = CategoryInput.New("Viajes"),
                description = "",
                tagInputs = listOf(TagInput.New("Viaje Europa"))
            )
            assertTrue(result is Outcome.Failure)
            assertTrue(storedTagNames().isEmpty())
        }

    @Test
    fun `given a tag on exactly one expense, when editing that expense removing it, then the tag is deleted`() =
        runTest {
            val account = createAccount("100000")
            val expense = createExpense(account, "30000", listOf(TagInput.New("Nalaa")))
            val result = updateExpense(expense, emptyList())
            assertTrue(result is Outcome.Success)
            assertTrue(storedTagNames().isEmpty())
            assertTrue(linkedTagIds(expense.id).isEmpty())
        }

    @Test
    fun `given a tag on two expenses, when editing one removing it, then the tag is kept`() = runTest {
        val account = createAccount("100000")
        val firstExpense = createExpense(account, "30000", listOf(TagInput.New("Nala")))
        val nalaTagId = linkedTagIds(firstExpense.id).single()
        createExpense(account, "10000", listOf(TagInput.Existing(nalaTagId)))
        val result = updateExpense(firstExpense, emptyList())
        assertTrue(result is Outcome.Success)
        assertEquals(listOf("Nala"), storedTagNames())
    }

    @Test
    fun `given a failing step inside the edit, when updating, then links and tags stay as they were`() = runTest {
        val account = createAccount("100000")
        val expense = createExpense(account, "30000", listOf(TagInput.New("Nala")))
        val nalaTagId = linkedTagIds(expense.id).single()
        val result = failingCleanupExpenseUpdater.update(
            expenseId = expense.id,
            amount = "30000",
            date = today,
            categoryInput = CategoryInput.Existing(expense.categoryId),
            description = "",
            tagInputs = listOf(TagInput.New("Carro"))
        )
        assertTrue(result is Outcome.Failure)
        assertEquals(listOf("Nala"), storedTagNames())
        assertEquals(listOf(nalaTagId), linkedTagIds(expense.id))
    }

    private suspend fun createAccount(initialBalance: String): Account {
        this.database.categoryDao().insert(
            CategoryEntity(
                id = DOGS_CATEGORY_ID,
                name = "Perros",
                type = "EXPENSE",
                description = "",
                color = "#FF0000",
                createdAt = "2026-01-01T00:00:00Z"
            )
        )
        return (
            this.accountCreator.create(
                CreateAccountCommand.MoneyAccount("Ahorros", initialBalance, Currency.COP, MoneyAccountKind.SAVINGS, "")
            ) as Outcome.Success
            ).value
    }

    private suspend fun createExpense(account: Account, amount: String, tagInputs: List<TagInput>): Expense = (
        this.expenseCreator.create(
            accountId = account.id,
            amount = amount,
            date = this.today,
            categoryInput = CategoryInput.Existing(DOGS_CATEGORY_ID),
            description = "",
            tagInputs = tagInputs
        ) as Outcome.Success
        ).value

    private suspend fun updateExpense(expense: Expense, tagInputs: List<TagInput>): Outcome<Expense> =
        this.expenseUpdater.update(
            expenseId = expense.id,
            amount = "30000",
            date = this.today,
            categoryInput = CategoryInput.Existing(expense.categoryId),
            description = "",
            tagInputs = tagInputs
        )

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

    private companion object {
        const val DOGS_CATEGORY_ID = "cat-perros"
    }
}
