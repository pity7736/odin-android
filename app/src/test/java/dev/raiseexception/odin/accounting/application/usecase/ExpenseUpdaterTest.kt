package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.accounting.domain.ExpenseUpdateError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.shared.domain.TransactionRunner
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.CategoryBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ExpenseUpdaterTest {

    private val transactionFinder = mockk<TransactionFinder>()
    private val accountFinder = mockk<AccountFinder>()
    private val expenseRepository = mockk<ExpenseRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val categoryCreator = mockk<CategoryCreator>()
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> = block()
    }
    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val fullCriteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    private val expenseUpdater = ExpenseUpdater(
        transactionFinder = transactionFinder,
        accountFinder = accountFinder,
        expenseRepository = expenseRepository,
        categoryRepository = categoryRepository,
        categoryCreator = categoryCreator,
        transactionRunner = transactionRunner,
        clock = fixedClock
    )

    private val account: Account = AccountBuilder()
        .id("acc-1")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .initialBalance(Money.of(BigDecimal("100000.00"), Currency.COP))
        .withExpense(amount = "30000", date = "2026-03-10", categoryId = "cat-food", description = "Mercado")
        .build()
    private val expense: Expense = account.expenses.first()
    private val expenseCategory = CategoryBuilder().id("cat-restaurant").type(CategoryType.EXPENSE).build()

    @Test
    fun `given a valid edit with an existing expense category, when updating, then saves and returns it`() =
        runTest {
            stubExpenseFound()
            every { categoryRepository.findById("cat-restaurant") } returns flowOf(Outcome.Success(expenseCategory))
            coEvery { expenseRepository.update(any()) } returns Outcome.Success(Unit)
            val result = update(categoryInput = CategoryInput.Existing("cat-restaurant"))
            assertTrue(result is Outcome.Success)
            val edited = (result as Outcome.Success).value
            assertEquals(expense.id, edited.id)
            assertEquals(Money.of(BigDecimal("45000"), Currency.COP), edited.amount)
            assertEquals(LocalDate.parse("2026-04-02"), edited.date)
            assertEquals("cat-restaurant", edited.categoryId)
            assertEquals("Restaurante", edited.description)
            coVerify { expenseRepository.update(edited) }
        }

    @Test
    fun `given a new category name, when updating, then creates the category and saves the expense under it`() =
        runTest {
            stubExpenseFound()
            coEvery { categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null) } returns
                Outcome.Success(expenseCategory)
            coEvery { expenseRepository.update(any()) } returns Outcome.Success(Unit)
            val result = update(categoryInput = CategoryInput.New("Transporte"))
            assertTrue(result is Outcome.Success)
            coVerify { categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null) }
            coVerify { expenseRepository.update(match { it.categoryId == "cat-restaurant" }) }
        }

    @Test
    fun `given the expense does not exist, when updating, then returns NotFound and saves nothing`() =
        runTest {
            every { transactionFinder.find(expense.id) } returns flowOf(
                Outcome.Failure(TransactionLookupError.NotFound("Transaction not found", "Transacción no encontrada"))
            )
            val result = update(categoryInput = CategoryInput.Existing("cat-restaurant"))
            assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
            coVerify(exactly = 0) { expenseRepository.update(any()) }
        }

    @Test
    fun `given the id belongs to an income, when updating, then returns NotFound and saves nothing`() =
        runTest {
            val income = Income.restore(
                id = expense.id,
                accountId = "acc-1",
                amount = Money.of(BigDecimal("1000"), Currency.COP),
                date = LocalDate.parse("2026-03-10"),
                categoryId = "cat-salary",
                description = "",
                createdAt = fixedInstant
            )
            every { transactionFinder.find(expense.id) } returns flowOf(
                Outcome.Success(TransactionDetail(income, "Salario", "Ahorros", isTransfer = false))
            )
            val result = update(categoryInput = CategoryInput.Existing("cat-restaurant"))
            assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
            coVerify(exactly = 0) { expenseRepository.update(any()) }
        }

    @Test
    fun `given the expense belongs to a transfer, when updating, then returns TransferNotEditable and saves nothing`() =
        runTest {
            every { transactionFinder.find(expense.id) } returns flowOf(
                Outcome.Success(TransactionDetail(expense, "Transferencia", "Ahorros", isTransfer = true))
            )
            val result = update(categoryInput = CategoryInput.Existing("cat-restaurant"))
            val error = (result as Outcome.Failure).error
            assertTrue(error is ExpenseUpdateError.TransferNotEditable)
            assertEquals("Las transferencias no se pueden editar.", error.externalMessage)
            coVerify(exactly = 0) { expenseRepository.update(any()) }
        }

    @Test
    fun `given an existing category that no longer exists, when updating, then returns a category error`() =
        runTest {
            stubExpenseFound()
            every { categoryRepository.findById("cat-gone") } returns flowOf(
                Outcome.Failure(CategoryLookupError.NotFound("Category not found", "Categoría no encontrada"))
            )
            val result = update(categoryInput = CategoryInput.Existing("cat-gone"))
            val error = invalidInput(result)
            assertEquals("La categoría seleccionada no existe.", error.categoryError)
            coVerify(exactly = 0) { expenseRepository.update(any()) }
        }

    @Test
    fun `given an existing category of income type, when updating, then returns a category error`() =
        runTest {
            stubExpenseFound()
            val incomeCategory = CategoryBuilder().id("cat-salary").type(CategoryType.INCOME).build()
            every { categoryRepository.findById("cat-salary") } returns flowOf(Outcome.Success(incomeCategory))
            val result = update(categoryInput = CategoryInput.Existing("cat-salary"))
            val error = invalidInput(result)
            assertEquals("La categoría seleccionada no es de tipo gasto.", error.categoryError)
            coVerify(exactly = 0) { expenseRepository.update(any()) }
        }

    @Test
    fun `given a duplicate new category name, when updating, then returns the duplicate message as category error`() =
        runTest {
            stubExpenseFound()
            coEvery { categoryCreator.create("Mercado", CategoryType.EXPENSE, "", null) } returns Outcome.Failure(
                CategoryCreationError.DuplicateName(
                    internalMessage = "Duplicate",
                    externalMessage = "Ya tienes una categoría de tipo gasto con ese nombre."
                )
            )
            val result = update(categoryInput = CategoryInput.New("Mercado"))
            val error = invalidInput(result)
            assertEquals("Ya tienes una categoría de tipo gasto con ese nombre.", error.categoryError)
            coVerify(exactly = 0) { expenseRepository.update(any()) }
        }

    @Test
    fun `given a blank new category name and a zero amount, when updating, then returns category and amount errors`() =
        runTest {
            stubExpenseFound()
            coEvery { categoryCreator.create("", CategoryType.EXPENSE, "", null) } returns Outcome.Failure(
                CategoryCreationError.InvalidInput(
                    nameError = "El nombre es obligatorio.",
                    typeError = null,
                    descriptionError = null
                )
            )
            val result = update(amount = "0", categoryInput = CategoryInput.New(""))
            val error = invalidInput(result)
            assertEquals("El nombre es obligatorio.", error.categoryError)
            assertEquals("El monto debe ser mayor que cero.", error.amountError)
            coVerify(exactly = 0) { expenseRepository.update(any()) }
        }

    @Test
    fun `given an amount above the available money, when updating, then returns the amount error and saves nothing`() =
        runTest {
            stubExpenseFound()
            every { categoryRepository.findById("cat-restaurant") } returns flowOf(Outcome.Success(expenseCategory))
            val result = update(
                amount = "100001",
                categoryInput = CategoryInput.Existing("cat-restaurant")
            )
            val error = invalidInput(result)
            assertEquals("El monto supera el saldo disponible.", error.amountError)
            coVerify(exactly = 0) { expenseRepository.update(any()) }
        }

    @Test
    fun `given the transaction lookup fails with a storage error, when updating, then returns StorageFailure`() =
        runTest {
            every { transactionFinder.find(expense.id) } returns flowOf(Outcome.Failure(StorageError("disk error")))
            val result = update(categoryInput = CategoryInput.Existing("cat-restaurant"))
            assertStorageFailure(result, "disk error")
        }

    @Test
    fun `given the account lookup fails, when updating, then returns StorageFailure with the spec message`() = runTest {
        every { transactionFinder.find(expense.id) } returns flowOf(Outcome.Success(detail()))
        every { accountFinder.find("acc-1", fullCriteria) } returns
            flowOf(Outcome.Failure(StorageError("account error")))
        val result = update(categoryInput = CategoryInput.Existing("cat-restaurant"))
        assertStorageFailure(result, "account error")
    }

    @Test
    fun `given the category lookup fails with a storage error, when updating, then returns StorageFailure`() =
        runTest {
            stubExpenseFound()
            every { categoryRepository.findById("cat-restaurant") } returns flowOf(
                Outcome.Failure(StorageError("category read error"))
            )
            val result = update(categoryInput = CategoryInput.Existing("cat-restaurant"))
            assertStorageFailure(result, "category read error")
        }

    @Test
    fun `given category creation fails with a storage error, when updating, then returns StorageFailure`() =
        runTest {
            stubExpenseFound()
            coEvery { categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null) } returns
                Outcome.Failure(StorageError("category write error"))
            val result = update(categoryInput = CategoryInput.New("Transporte"))
            assertStorageFailure(result, "category write error")
        }

    @Test
    fun `given the expense update fails, when updating, then returns StorageFailure keeping the internal message`() =
        runTest {
            stubExpenseFound()
            every { categoryRepository.findById("cat-restaurant") } returns flowOf(Outcome.Success(expenseCategory))
            coEvery { expenseRepository.update(any()) } returns Outcome.Failure(StorageError("update failed"))
            val result = update(categoryInput = CategoryInput.Existing("cat-restaurant"))
            assertStorageFailure(result, "update failed")
        }

    private fun stubExpenseFound() {
        every { transactionFinder.find(expense.id) } returns flowOf(Outcome.Success(detail()))
        every { accountFinder.find("acc-1", fullCriteria) } returns flowOf(Outcome.Success(account))
    }

    private fun detail(): TransactionDetail =
        TransactionDetail(this.expense, "Alimentación", "Ahorros", isTransfer = false)

    private suspend fun update(amount: String = "45000", categoryInput: CategoryInput): Outcome<Expense> =
        this.expenseUpdater.update(
            expenseId = this.expense.id,
            amount = amount,
            date = "2026-04-02",
            categoryInput = categoryInput,
            description = "Restaurante"
        )

    private fun invalidInput(result: Outcome<Expense>): ExpenseUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseUpdateError.InvalidInput)
        return error as ExpenseUpdateError.InvalidInput
    }

    private fun assertStorageFailure(result: Outcome<Expense>, internalMessage: String) {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseUpdateError.StorageFailure)
        assertEquals("No se pudo editar el gasto. Inténtalo de nuevo.", error.externalMessage)
        assertEquals(internalMessage, error.internalMessage)
    }
}
