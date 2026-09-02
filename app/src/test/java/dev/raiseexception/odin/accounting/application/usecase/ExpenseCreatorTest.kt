package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.shared.domain.Outcome
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCreatorTest {

    private val accountRepository = mockk<AccountRepository>()
    private val expenseRepository = mockk<ExpenseRepository>()
    private val categoryRepository = mockk<dev.raiseexception.odin.accounting.domain.repository.CategoryRepository>()
    private val categoryCreator = mockk<CategoryCreator>()
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val today = fixedInstant.toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val expenseCreator = ExpenseCreator(
        accountRepository = accountRepository,
        expenseRepository = expenseRepository,
        categoryRepository = categoryRepository,
        categoryCreator = categoryCreator,
        transactionRunner = transactionRunner,
        clock = fixedClock
    )

    private val account = AccountBuilder().id("acc-1").build()
    private val expenseCategory = CategoryBuilder().type(CategoryType.EXPENSE).build()

    @Test
    fun `given valid input with existing category, when creating expense, then expense is saved`() = runTest {
        coEvery {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))
        coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)

        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = "Mercado"
        )

        assertTrue(result is Outcome.Success)
        coVerify { expenseRepository.add(any()) }
    }

    @Test
    fun `given valid input with new category, when creating expense, then category is created and expense is saved`() =
        runTest {
            coEvery {
                accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
            } returns Outcome.Success(account)
            coEvery { categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null) } returns
                Outcome.Success(expenseCategory)
            coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)

            val result = expenseCreator.create(
                accountId = "acc-1",
                amount = "500.00",
                date = today.toString(),
                categoryInput = CategoryInput.New("Transporte"),
                description = ""
            )

            assertTrue(result is Outcome.Success)
            coVerify { categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null) }
            coVerify { expenseRepository.add(any()) }
        }

    @Test
    fun `given zero amount, when creating expense, then returns amount error`() = runTest {
        coEvery {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))

        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "0",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        assertNotNull((error as ExpenseCreationError.InvalidInput).amountError)
    }

    @Test
    fun `given future date, when creating expense, then returns date error`() = runTest {
        coEvery {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))

        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = "2099-01-01",
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        assertNotNull((error as ExpenseCreationError.InvalidInput).dateError)
    }

    @Test
    fun `given missing required field, when creating expense, then returns field error`() = runTest {
        coEvery {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))

        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "",
            date = "",
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        val invalidInput = error as ExpenseCreationError.InvalidInput
        assertNotNull(invalidInput.amountError)
        assertNotNull(invalidInput.dateError)
    }

    @Test
    fun `given category id not found, when creating expense, then returns category not found error`() = runTest {
        coEvery {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(emptyList()))

        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing("non-existent-id"),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is ExpenseCreationError.CategoryNotFound)
    }

    @Test
    fun `given amount exceeds account balance, when creating expense, then returns amount error`() = runTest {
        val smallBalanceAccount = AccountBuilder()
            .id("acc-1")
            .initialBalance(
                dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("100.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                )
            )
            .build()
        coEvery {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns Outcome.Success(smallBalanceAccount)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))

        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "200.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        assertNotNull((error as ExpenseCreationError.InvalidInput).amountError)
    }

    @Test
    fun `given category of wrong type, when creating expense, then returns category wrong type error`() = runTest {
        val incomeCategory = CategoryBuilder().type(CategoryType.INCOME).build()
        coEvery {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(incomeCategory)))

        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(incomeCategory.id),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is ExpenseCreationError.CategoryWrongType)
    }
}
