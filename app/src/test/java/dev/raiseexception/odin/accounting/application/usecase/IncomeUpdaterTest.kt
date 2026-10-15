package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.IncomeUpdateError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Transaction
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.shared.domain.TransactionRunner
import dev.raiseexception.odin.testutil.AccountBuilder
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

class IncomeUpdaterTest {

    private val transactionFinder = mockk<TransactionFinder>()
    private val accountFinder = mockk<AccountFinder>()
    private val incomeRepository = mockk<IncomeRepository>()
    private val categoryResolver = mockk<CategoryResolver>()
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> = block()
    }
    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val fullCriteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    private val incomeUpdater = IncomeUpdater(
        transactionFinder = transactionFinder,
        accountFinder = accountFinder,
        incomeRepository = incomeRepository,
        categoryResolver = categoryResolver,
        transactionRunner = transactionRunner,
        clock = fixedClock
    )

    private val income: Income = Income.restore(
        id = "income-1",
        accountId = "acc-1",
        amount = Money.of(BigDecimal("100000"), Currency.COP),
        date = LocalDate.parse("2026-03-05"),
        categoryId = "cat-salary",
        description = "Salario",
        createdAt = Instant.parse("2026-03-05T12:00:00Z")
    )
    private val expense: Expense = Expense.restore(
        id = "expense-1",
        accountId = "acc-1",
        amount = Money.of(BigDecimal("80000"), Currency.COP),
        date = LocalDate.parse("2026-03-08"),
        categoryId = "cat-food",
        description = "",
        createdAt = Instant.parse("2026-03-08T12:00:00Z"),
        tagIds = emptyList()
    )
    private val account: Account = AccountBuilder()
        .id("acc-1")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .initialBalance(Money.of(BigDecimal("0"), Currency.COP))
        .incomes(listOf(income))
        .expenses(listOf(expense))
        .build()

    @Test
    fun `given a valid edit with an existing income category, when updating, then saves and returns it`() = runTest {
        stubIncomeFound()
        stubCategoryResolved(CategoryInput.Existing("cat-bonus"), "cat-bonus")
        coEvery { incomeRepository.update(any()) } returns Outcome.Success(Unit)
        val result = update(categoryInput = CategoryInput.Existing("cat-bonus"))
        assertTrue(result is Outcome.Success)
        val edited = (result as Outcome.Success).value
        assertEquals(income.id, edited.id)
        assertEquals(Money.of(BigDecimal("150000"), Currency.COP), edited.amount)
        assertEquals(LocalDate.parse("2026-03-06"), edited.date)
        assertEquals("cat-bonus", edited.categoryId)
        assertEquals("Bono", edited.description)
        coVerify { incomeRepository.update(edited) }
    }

    @Test
    fun `given a new category name, when updating, then resolves it as income and saves the income under it`() =
        runTest {
            stubIncomeFound()
            stubCategoryResolved(CategoryInput.New("Bonos"), "cat-new-bonus")
            coEvery { incomeRepository.update(any()) } returns Outcome.Success(Unit)
            val result = update(categoryInput = CategoryInput.New("Bonos"))
            assertTrue(result is Outcome.Success)
            coVerify { categoryResolver.resolve(CategoryInput.New("Bonos"), CategoryType.INCOME) }
            coVerify { incomeRepository.update(match { it.categoryId == "cat-new-bonus" }) }
        }

    @Test
    fun `given the income does not exist, when updating, then returns NotFound and saves nothing`() = runTest {
        every { transactionFinder.find(income.id) } returns flowOf(
            Outcome.Failure(TransactionLookupError.NotFound("Transaction not found", "Transacción no encontrada"))
        )
        val result = update(categoryInput = CategoryInput.Existing("cat-bonus"))
        assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
        coVerify(exactly = 0) { incomeRepository.update(any()) }
    }

    @Test
    fun `given the id belongs to an expense, when updating, then returns NotFound and saves nothing`() = runTest {
        every { transactionFinder.find(income.id) } returns flowOf(Outcome.Success(detail(transaction = expense)))
        val result = update(categoryInput = CategoryInput.Existing("cat-bonus"))
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransactionLookupError.NotFound)
        assertEquals("Transacción no encontrada", error.externalMessage)
        coVerify(exactly = 0) { incomeRepository.update(any()) }
    }

    @Test
    fun `given the income belongs to a transfer, when updating, then returns TransferNotEditable and saves nothing`() =
        runTest {
            every { transactionFinder.find(income.id) } returns flowOf(Outcome.Success(detail(isTransfer = true)))
            val result = update(categoryInput = CategoryInput.Existing("cat-bonus"))
            val error = (result as Outcome.Failure).error
            assertTrue(error is IncomeUpdateError.TransferNotEditable)
            assertEquals("Las transferencias no se pueden editar.", error.externalMessage)
            coVerify(exactly = 0) { incomeRepository.update(any()) }
        }

    @Test
    fun `given the transaction lookup fails with a storage error, when updating, then returns StorageFailure`() =
        runTest {
            every { transactionFinder.find(income.id) } returns flowOf(Outcome.Failure(StorageError("disk error")))
            val result = update(categoryInput = CategoryInput.Existing("cat-bonus"))
            assertStorageFailure(result, "disk error")
        }

    @Test
    fun `given the account lookup fails, when updating, then returns StorageFailure`() = runTest {
        every { transactionFinder.find(income.id) } returns flowOf(Outcome.Success(detail()))
        every { accountFinder.find("acc-1", fullCriteria) } returns
            flowOf(Outcome.Failure(StorageError("account error")))
        val result = update(categoryInput = CategoryInput.Existing("cat-bonus"))
        assertStorageFailure(result, "account error")
    }

    @Test
    fun `given a rejected category and invalid amount and date, when updating, then returns every error at once`() =
        runTest {
            stubIncomeFound()
            coEvery { categoryResolver.resolve(CategoryInput.New(""), CategoryType.INCOME) } returns
                Outcome.Success(CategoryResolution.Rejected("El nombre es obligatorio."))
            val result = update(amount = "0", date = "2099-01-01", categoryInput = CategoryInput.New(""))
            val error = invalidInput(result)
            assertEquals("El nombre es obligatorio.", error.categoryError)
            assertEquals("El monto debe ser mayor que cero.", error.amountError)
            assertEquals("La fecha debe ser hoy o en el pasado.", error.dateError)
            coVerify(exactly = 0) { incomeRepository.update(any()) }
        }

    @Test
    fun `given a rejected category, when updating, then the transaction returns the failure and saves nothing`() =
        runTest {
            val transactionOutcomes = mutableListOf<Outcome<*>>()
            val recordingTransactionRunner = object : TransactionRunner {
                override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> =
                    block().also { transactionOutcomes.add(it) }
            }
            val recordingIncomeUpdater = IncomeUpdater(
                transactionFinder = transactionFinder,
                accountFinder = accountFinder,
                incomeRepository = incomeRepository,
                categoryResolver = categoryResolver,
                transactionRunner = recordingTransactionRunner,
                clock = fixedClock
            )
            stubIncomeFound()
            coEvery { categoryResolver.resolve(CategoryInput.Existing("cat-food"), CategoryType.INCOME) } returns
                Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no es de tipo ingreso."))
            recordingIncomeUpdater.update(
                incomeId = income.id,
                amount = "150000",
                date = "2026-03-06",
                categoryInput = CategoryInput.Existing("cat-food"),
                description = ""
            )
            assertEquals(1, transactionOutcomes.size)
            assertTrue(transactionOutcomes.first() is Outcome.Failure)
            coVerify(exactly = 0) { incomeRepository.update(any()) }
        }

    @Test
    fun `given the category resolution fails with a storage error, when updating, then returns StorageFailure`() =
        runTest {
            stubIncomeFound()
            coEvery { categoryResolver.resolve(CategoryInput.New("Bonos"), CategoryType.INCOME) } returns
                Outcome.Failure(StorageError("category write error"))
            val result = update(categoryInput = CategoryInput.New("Bonos"))
            assertStorageFailure(result, "category write error")
            coVerify(exactly = 0) { incomeRepository.update(any()) }
        }

    @Test
    fun `given the income update fails, when updating, then returns StorageFailure with the spec message`() = runTest {
        stubIncomeFound()
        stubCategoryResolved(CategoryInput.Existing("cat-bonus"), "cat-bonus")
        coEvery { incomeRepository.update(any()) } returns Outcome.Failure(StorageError("update failed"))
        val result = update(categoryInput = CategoryInput.Existing("cat-bonus"))
        assertStorageFailure(result, "update failed")
    }

    @Test
    fun `given a balance of 20000, when lowering the income to 79999, then returns the floor error unsaved`() =
        runTest {
            stubIncomeFound()
            stubCategoryResolved(CategoryInput.Existing("cat-bonus"), "cat-bonus")
            val result = update(amount = "79999", categoryInput = CategoryInput.Existing("cat-bonus"))
            val error = invalidInput(result)
            assertEquals("El saldo de la cuenta no puede quedar negativo.", error.amountError)
            coVerify(exactly = 0) { incomeRepository.update(any()) }
        }

    @Test
    fun `given the income is missing from its account, when updating, then returns NotFound and saves nothing`() =
        runTest {
            val accountWithoutIncome = AccountBuilder()
                .id("acc-1")
                .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
                .build()
            every { transactionFinder.find(income.id) } returns flowOf(Outcome.Success(detail()))
            every { accountFinder.find("acc-1", fullCriteria) } returns flowOf(Outcome.Success(accountWithoutIncome))
            stubCategoryResolved(CategoryInput.Existing("cat-bonus"), "cat-bonus")
            val result = update(categoryInput = CategoryInput.Existing("cat-bonus"))
            assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
            coVerify(exactly = 0) { incomeRepository.update(any()) }
        }

    private fun stubIncomeFound() {
        every { transactionFinder.find(income.id) } returns flowOf(Outcome.Success(detail()))
        every { accountFinder.find("acc-1", fullCriteria) } returns flowOf(Outcome.Success(account))
    }

    private fun stubCategoryResolved(categoryInput: CategoryInput, categoryId: String) {
        coEvery { categoryResolver.resolve(categoryInput, CategoryType.INCOME) } returns
            Outcome.Success(CategoryResolution.Resolved(categoryId))
    }

    private fun detail(
        transaction: Transaction = this.income,
        isTransfer: Boolean = false
    ): TransactionDetail = TransactionDetail(
        transaction,
        "Salario",
        "Ahorros",
        isTransfer = isTransfer,
        accountType = AccountType.SAVINGS,
        tags = emptyList()
    )

    private suspend fun update(
        amount: String = "150000",
        date: String = "2026-03-06",
        categoryInput: CategoryInput
    ): Outcome<Income> = this.incomeUpdater.update(
        incomeId = this.income.id,
        amount = amount,
        date = date,
        categoryInput = categoryInput,
        description = "Bono"
    )

    private fun invalidInput(result: Outcome<Income>): IncomeUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is IncomeUpdateError.InvalidInput)
        return error as IncomeUpdateError.InvalidInput
    }

    private fun assertStorageFailure(result: Outcome<Income>, internalMessage: String) {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is IncomeUpdateError.StorageFailure)
        assertEquals("No se pudo editar el ingreso. Inténtalo de nuevo.", error.externalMessage)
        assertEquals(internalMessage, error.internalMessage)
    }
}
