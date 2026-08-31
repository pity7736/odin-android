package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
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

class IncomeCreatorTest {

    private val accountRepository = mockk<AccountRepository>()
    private val incomeRepository = mockk<IncomeRepository>()
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

    private val incomeCreator = IncomeCreator(
        accountRepository = accountRepository,
        incomeRepository = incomeRepository,
        categoryRepository = categoryRepository,
        categoryCreator = categoryCreator,
        transactionRunner = transactionRunner,
        clock = fixedClock
    )

    private val account = AccountBuilder().id("acc-1").build()
    private val incomeCategory = CategoryBuilder().type(CategoryType.INCOME).build()

    @Test
    fun `given valid input with existing category, when creating income, then income is saved`() = runTest {
        coEvery { accountRepository.findById("acc-1", AccountCriteria()) } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(incomeCategory)))
        coEvery { incomeRepository.add(any()) } returns Outcome.Success(Unit)

        val result = incomeCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(incomeCategory.id),
            description = "Salario"
        )

        assertTrue(result is Outcome.Success)
        coVerify { incomeRepository.add(any()) }
    }

    @Test
    fun `given valid input with new category, when creating income, then category is created and income is saved`() =
        runTest {
            coEvery { accountRepository.findById("acc-1", AccountCriteria()) } returns Outcome.Success(account)
            coEvery { categoryCreator.create("Freelance", CategoryType.INCOME, "", null) } returns
                Outcome.Success(incomeCategory)
            coEvery { incomeRepository.add(any()) } returns Outcome.Success(Unit)

            val result = incomeCreator.create(
                accountId = "acc-1",
                amount = "500.00",
                date = today.toString(),
                categoryInput = CategoryInput.New("Freelance"),
                description = ""
            )

            assertTrue(result is Outcome.Success)
            coVerify { categoryCreator.create("Freelance", CategoryType.INCOME, "", null) }
            coVerify { incomeRepository.add(any()) }
        }

    @Test
    fun `given zero amount, when creating income, then returns amount error`() = runTest {
        coEvery { accountRepository.findById("acc-1", AccountCriteria()) } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(incomeCategory)))

        val result = incomeCreator.create(
            accountId = "acc-1",
            amount = "0",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(incomeCategory.id),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is IncomeCreationError.InvalidInput)
        assertNotNull((error as IncomeCreationError.InvalidInput).amountError)
    }

    @Test
    fun `given future date, when creating income, then returns date error`() = runTest {
        coEvery { accountRepository.findById("acc-1", AccountCriteria()) } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(incomeCategory)))

        val result = incomeCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = "2099-01-01",
            categoryInput = CategoryInput.Existing(incomeCategory.id),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is IncomeCreationError.InvalidInput)
        assertNotNull((error as IncomeCreationError.InvalidInput).dateError)
    }

    @Test
    fun `given missing required field, when creating income, then returns field error`() = runTest {
        coEvery { accountRepository.findById("acc-1", AccountCriteria()) } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(incomeCategory)))

        val result = incomeCreator.create(
            accountId = "acc-1",
            amount = "",
            date = "",
            categoryInput = CategoryInput.Existing(incomeCategory.id),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is IncomeCreationError.InvalidInput)
        val invalidInput = error as IncomeCreationError.InvalidInput
        assertNotNull(invalidInput.amountError)
        assertNotNull(invalidInput.dateError)
    }

    @Test
    fun `given category id not found, when creating income, then returns category not found error`() = runTest {
        coEvery { accountRepository.findById("acc-1", AccountCriteria()) } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(emptyList()))

        val result = incomeCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing("non-existent-id"),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is IncomeCreationError.CategoryNotFound)
    }

    @Test
    fun `given category of wrong type, when creating income, then returns category wrong type error`() = runTest {
        val expenseCategory = CategoryBuilder().type(CategoryType.EXPENSE).build()
        coEvery { accountRepository.findById("acc-1", AccountCriteria()) } returns Outcome.Success(account)
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))

        val result = incomeCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is IncomeCreationError.CategoryWrongType)
    }
}
