package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountUpdater
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.application.usecase.CategoryResolver
import dev.raiseexception.odin.accounting.application.usecase.CreateAccountCommand
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.application.usecase.ExpenseUpdater
import dev.raiseexception.odin.accounting.application.usecase.GmfInput
import dev.raiseexception.odin.accounting.application.usecase.TagResolver
import dev.raiseexception.odin.accounting.application.usecase.TransactionDeleter
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class GmfIntegrationTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    private val fullCriteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
    private lateinit var database: OdinDatabase
    private lateinit var accountRepository: RoomAccountRepository
    private lateinit var categoryCreator: CategoryCreator
    private lateinit var accountCreator: AccountCreator
    private lateinit var accountUpdater: AccountUpdater
    private lateinit var expenseCreator: ExpenseCreator
    private lateinit var expenseUpdater: ExpenseUpdater
    private lateinit var transactionDeleter: TransactionDeleter
    private lateinit var failingTaxTagsExpenseCreator: ExpenseCreator
    private lateinit var mercadoCategoryId: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountRepository = RoomAccountRepository(database.accountDao())
        val categoryRepository = RoomCategoryRepository(database.categoryDao())
        val transactionDao = database.transactionDao()
        val expenseRepository = RoomExpenseRepository(transactionDao, database.expenseTagDao())
        val transactionRunner = RoomTransactionRunner(database)
        val tagRepository = RoomTagRepository(database.tagDao())
        val tagResolver = TagResolver(tagRepository)
        val accountFinder = AccountFinder(accountRepository)
        val transactionFinder = TransactionFinder(RoomTransactionRepository(transactionDao, database.tagDao()))
        categoryCreator = CategoryCreator(categoryRepository)
        val categoryResolver = CategoryResolver(categoryRepository, categoryCreator)
        accountCreator = AccountCreator(accountRepository, categoryResolver, transactionRunner)
        accountUpdater = AccountUpdater(accountFinder, accountRepository, categoryResolver, transactionRunner)
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
            categoryResolver = categoryResolver,
            tagResolver = tagResolver,
            tagRepository = tagRepository,
            transactionRunner = transactionRunner
        )
        transactionDeleter = TransactionDeleter(
            transactionFinder = transactionFinder,
            accountFinder = accountFinder,
            incomeRepository = RoomIncomeRepository(transactionDao),
            expenseRepository = expenseRepository,
            tagRepository = tagRepository,
            transactionRunner = transactionRunner
        )
        failingTaxTagsExpenseCreator = ExpenseCreator(
            accountRepository = accountRepository,
            expenseRepository = RoomExpenseRepository(transactionDao, FailingSecondLinkExpenseTagDao(database)),
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            tagResolver = tagResolver,
            transactionRunner = transactionRunner
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given Ahorros not exempt, when recording Mercado, then both rows are stored and the balance is 899600`() =
        runTest {
            val ahorros = createAhorros(CategoryInput.New("Impuestos"))

            val mercado = recordMercado(ahorros, "100000")

            val stored = loadAccount(ahorros.id)
            assertEquals(listOf(mercado.id, mercado.gmfTax!!.id), stored.expenses.map { it.id })
            assertEquals(0, stored.balance.amount.compareTo(BigDecimal("899600")))
            assertEquals(listOf("4x1000"), tagNamesOf(mercado.gmfTax!!.id))
        }

    @Test
    fun `given Mercado with its tax, when editing it to 50000, then the stored tax is 200`() = runTest {
        val ahorros = createAhorros(CategoryInput.New("Impuestos"))
        val mercado = recordMercado(ahorros, "100000")

        expenseUpdater.update(
            expenseId = mercado.id,
            amount = "50000",
            date = today,
            categoryInput = CategoryInput.Existing(mercado.categoryId),
            description = "Mercado",
            tagInputs = emptyList()
        )

        val stored = loadAccount(ahorros.id)
        val storedTax = stored.expenses.first().gmfTax!!
        assertEquals(mercado.gmfTax!!.id, storedTax.id)
        assertEquals(0, storedTax.amount.amount.compareTo(BigDecimal("200")))
        assertEquals(0, stored.balance.amount.compareTo(BigDecimal("949800")))
        assertEquals(listOf("4x1000"), tagNamesOf(storedTax.id))
    }

    @Test
    fun `given Mercado with its tax, when deleting Mercado, then both rows go and the balance grows 100400`() =
        runTest {
            val ahorros = createAhorros(CategoryInput.New("Impuestos"))
            val mercado = recordMercado(ahorros, "100000")

            val result = transactionDeleter.delete(mercado.id)

            assertTrue(result is Outcome.Success)
            val stored = loadAccount(ahorros.id)
            assertTrue(stored.expenses.isEmpty())
            assertEquals(0, stored.balance.amount.compareTo(BigDecimal("1000000")))
        }

    @Test
    fun `given Ahorros not exempt, when saving the tax fails, then neither Mercado nor its tax is stored`() =
        runTest {
            val ahorros = createAhorros(CategoryInput.New("Impuestos"))

            val result = failingTaxTagsExpenseCreator.create(
                accountId = ahorros.id,
                amount = "100000",
                date = today,
                categoryInput = CategoryInput.Existing(mercadoCategoryId),
                description = "Mercado",
                tagInputs = emptyList()
            )

            assertTrue((result as Outcome.Failure).error is ExpenseCreationError.StorageFailure)
            assertTrue(loadAccount(ahorros.id).expenses.isEmpty())
        }

    @Test
    fun `given Ahorros with three taxed expenses, when marking it exempt, then the taxes stay and the next has none`() =
        runTest {
            val ahorros = createAhorros(CategoryInput.New("Impuestos"))
            val taxed = listOf("10000", "20000", "30000").map { amount -> recordMercado(ahorros, amount) }

            updateAhorros(ahorros, GmfInput.Exempt)
            val next = recordMercado(ahorros, "40000")

            val stored = loadAccount(ahorros.id)
            assertNull(next.gmfTax)
            assertEquals(taxed.map { it.gmfTax!!.id }, stored.expenses.mapNotNull { it.gmfTax?.id })
        }

    @Test
    fun `given taxes recorded under GMF, when the tax category becomes Impuestos, then old stay and next uses it`() =
        runTest {
            val ahorros = createAhorros(CategoryInput.New("GMF"))
            val earlier = recordMercado(ahorros, "10000")
            val impuestosOutcome = categoryCreator.create("Impuestos", CategoryType.EXPENSE, "", null)
            val impuestos = (impuestosOutcome as Outcome.Success).value

            updateAhorros(ahorros, GmfInput.Taxed(CategoryInput.Existing(impuestos.id), listOf("4x1000")))
            val next = recordMercado(ahorros, "20000")

            val storedEarlierTax = loadAccount(ahorros.id).expenses.first { it.id == earlier.id }.gmfTax!!
            assertEquals(earlier.gmfTax!!.categoryId, storedEarlierTax.categoryId)
            assertEquals(impuestos.id, next.gmfTax!!.categoryId)
        }

    private suspend fun createAhorros(gmfCategory: CategoryInput): Account {
        val mercado = categoryCreator.create("Mercado", CategoryType.EXPENSE, "", null) as Outcome.Success
        mercadoCategoryId = mercado.value.id
        return (
            accountCreator.create(
                CreateAccountCommand.MoneyAccount(
                    name = "Ahorros",
                    balance = "1000000",
                    currency = Currency.COP,
                    kind = MoneyAccountKind.SAVINGS,
                    description = "",
                    gmf = GmfInput.Taxed(gmfCategory, listOf("4x1000"))
                )
            ) as Outcome.Success
            ).value
    }

    private suspend fun updateAhorros(ahorros: Account, gmf: GmfInput) {
        val result = accountUpdater.update(
            id = ahorros.id,
            name = "Ahorros",
            initialBalance = "1000000",
            currency = Currency.COP,
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = gmf
        )
        assertTrue("Expected the update to succeed: $result", result is Outcome.Success)
    }

    private suspend fun recordMercado(ahorros: Account, amount: String): Expense =
        (
            expenseCreator.create(
                accountId = ahorros.id,
                amount = amount,
                date = today,
                categoryInput = CategoryInput.Existing(mercadoCategoryId),
                description = "Mercado",
                tagInputs = emptyList()
            ) as Outcome.Success
            ).value

    private suspend fun loadAccount(accountId: String): Account =
        (accountRepository.findById(accountId, fullCriteria).first() as Outcome.Success).value

    private suspend fun tagNamesOf(expenseId: String): List<String> =
        database.tagDao().findByExpenseId(expenseId).first().map { it.name }

    private class FailingSecondLinkExpenseTagDao(database: OdinDatabase) : ExpenseTagDao {

        private val expenseTagDao = database.expenseTagDao()
        private var linkWrites = 0

        override suspend fun insertAll(expenseTags: List<ExpenseTagEntity>) {
            this.linkWrites += 1
            if (this.linkWrites > 1) throw SQLiteException("Simulated tax link failure")
            this.expenseTagDao.insertAll(expenseTags)
        }

        override suspend fun deleteByExpenseId(expenseId: String) = this.expenseTagDao.deleteByExpenseId(expenseId)

        override suspend fun deleteByGmfMovementId(movementId: String) =
            this.expenseTagDao.deleteByGmfMovementId(movementId)
    }
}
