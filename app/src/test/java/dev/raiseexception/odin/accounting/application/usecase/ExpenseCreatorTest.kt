package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.TagNameError
import dev.raiseexception.odin.accounting.domain.TagResolutionError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.model.TagInput
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class ExpenseCreatorTest {

    private val accountRepository = mockk<AccountRepository>()
    private val expenseRepository = mockk<ExpenseRepository>()
    private val categoryRepository = mockk<dev.raiseexception.odin.accounting.domain.repository.CategoryRepository>()
    private val categoryCreator = mockk<CategoryCreator>()
    private val tagResolver = mockk<TagResolver>()
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> = block()
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
        tagResolver = tagResolver,
        transactionRunner = transactionRunner,
        clock = fixedClock
    )

    private val account = AccountBuilder().id("acc-1").build()
    private val expenseCategory = CategoryBuilder().type(CategoryType.EXPENSE).build()
    private val visaCard = AccountBuilder()
        .id("card-1")
        .name("Visa")
        .creditCard(
            creditLimit = Money.of(BigDecimal("3000000"), Currency.COP),
            initialDebt = Money.of(BigDecimal("500000"), Currency.COP)
        )
        .build()

    @Before
    fun stubNoTags() {
        coEvery { tagResolver.resolve(emptyList()) } returns Outcome.Success(emptyList())
    }

    @Test
    fun `given valid input with existing category, when creating expense, then expense is saved`() = runTest {
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(account))
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))
        coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)
        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = "Mercado",
            tagInputs = emptyList()
        )
        assertTrue(result is Outcome.Success)
        coVerify { expenseRepository.add(any()) }
    }

    @Test
    fun `given valid input with new category, when creating expense, then category is created and expense is saved`() =
        runTest {
            every {
                accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
            } returns flowOf(Outcome.Success(account))
            coEvery { categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null) } returns
                Outcome.Success(expenseCategory)
            coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)
            val result = expenseCreator.create(
                accountId = "acc-1",
                amount = "500.00",
                date = today.toString(),
                categoryInput = CategoryInput.New("Transporte"),
                description = "",
                tagInputs = emptyList()
            )
            assertTrue(result is Outcome.Success)
            coVerify { categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null) }
            coVerify { expenseRepository.add(any()) }
        }

    @Test
    fun `given zero amount, when creating expense, then returns amount error`() = runTest {
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(account))
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))
        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "0",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = "",
            tagInputs = emptyList()
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        assertNotNull((error as ExpenseCreationError.InvalidInput).amountError)
    }

    @Test
    fun `given future date, when creating expense, then returns date error`() = runTest {
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(account))
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))
        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = "2099-01-01",
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = "",
            tagInputs = emptyList()
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        assertNotNull((error as ExpenseCreationError.InvalidInput).dateError)
    }

    @Test
    fun `given missing required field, when creating expense, then returns field error`() = runTest {
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(account))
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))
        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "",
            date = "",
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = "",
            tagInputs = emptyList()
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
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(account))
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(emptyList()))
        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing("non-existent-id"),
            description = "",
            tagInputs = emptyList()
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
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(smallBalanceAccount))
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))
        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "200.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = "",
            tagInputs = emptyList()
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        assertNotNull((error as ExpenseCreationError.InvalidInput).amountError)
    }

    @Test
    fun `given category of wrong type, when creating expense, then returns category wrong type error`() = runTest {
        val incomeCategory = CategoryBuilder().type(CategoryType.INCOME).build()
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(account))
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(incomeCategory)))
        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(incomeCategory.id),
            description = "",
            tagInputs = emptyList()
        )
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is ExpenseCreationError.CategoryWrongType)
    }

    @Test
    fun `given empty category name, when creating expense, then returns category error`() = runTest {
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(account))
        coEvery { categoryCreator.create("", CategoryType.EXPENSE, "", null) } returns
            Outcome.Failure(
                CategoryCreationError.InvalidInput(
                    nameError = "El nombre es obligatorio.",
                    typeError = null,
                    descriptionError = null
                )
            )
        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.New(""),
            description = "",
            tagInputs = emptyList()
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        assertEquals("El nombre es obligatorio.", (error as ExpenseCreationError.InvalidInput).categoryError)
    }

    @Test
    fun `given whitespace-only category name, when creating expense, then returns category error`() = runTest {
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(account))
        coEvery { categoryCreator.create("   ", CategoryType.EXPENSE, "", null) } returns
            Outcome.Failure(
                CategoryCreationError.InvalidInput(
                    nameError = "El nombre es obligatorio.",
                    typeError = null,
                    descriptionError = null
                )
            )
        val result = expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.New("   "),
            description = "",
            tagInputs = emptyList()
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        assertEquals("El nombre es obligatorio.", (error as ExpenseCreationError.InvalidInput).categoryError)
    }

    @Test
    fun `given a card, when creating an expense within its available credit, then expense is saved`() = runTest {
        every {
            accountRepository.findById("card-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(visaCard))
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))
        coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)
        val result = expenseCreator.create(
            accountId = "card-1",
            amount = "200000",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = "",
            tagInputs = emptyList()
        )
        assertTrue(result is Outcome.Success)
        coVerify { expenseRepository.add((result as Outcome.Success).value) }
    }

    @Test
    fun `given a card, when creating an expense above its available credit, then returns cupo error unsaved`() =
        runTest {
            every {
                accountRepository.findById("card-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
            } returns flowOf(Outcome.Success(visaCard))
            every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))
            val result = expenseCreator.create(
                accountId = "card-1",
                amount = "2500001",
                date = today.toString(),
                categoryInput = CategoryInput.Existing(expenseCategory.id),
                description = "",
                tagInputs = emptyList()
            )
            assertTrue(result is Outcome.Failure)
            val error = (result as Outcome.Failure).error
            assertTrue(error is ExpenseCreationError.InvalidInput)
            assertEquals(
                "El monto supera el cupo disponible.",
                (error as ExpenseCreationError.InvalidInput).amountError
            )
            coVerify(exactly = 0) { expenseRepository.add(any()) }
        }

    @Test
    fun `given a card and a new category, when the amount is above available credit, then the transaction fails`() =
        runTest {
            val blockOutcomes = mutableListOf<Outcome<*>>()
            val recordingTransactionRunner = object : TransactionRunner {
                override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> =
                    block().also { outcome -> blockOutcomes.add(outcome) }
            }
            val recordingExpenseCreator = ExpenseCreator(
                accountRepository = accountRepository,
                expenseRepository = expenseRepository,
                categoryRepository = categoryRepository,
                categoryCreator = categoryCreator,
                tagResolver = tagResolver,
                transactionRunner = recordingTransactionRunner,
                clock = fixedClock
            )
            every {
                accountRepository.findById("card-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
            } returns flowOf(Outcome.Success(visaCard))
            coEvery { categoryCreator.create("Viajes", CategoryType.EXPENSE, "", null) } returns
                Outcome.Success(expenseCategory)
            val result = recordingExpenseCreator.create(
                accountId = "card-1",
                amount = "2500001",
                date = today.toString(),
                categoryInput = CategoryInput.New("Viajes"),
                description = "",
                tagInputs = emptyList()
            )
            coVerify { categoryCreator.create("Viajes", CategoryType.EXPENSE, "", null) }
            assertEquals(listOf(result), blockOutcomes)
            assertTrue(result is Outcome.Failure)
            assertEquals(
                "El monto supera el cupo disponible.",
                ((result as Outcome.Failure).error as ExpenseCreationError.InvalidInput).amountError
            )
            coVerify(exactly = 0) { expenseRepository.add(any()) }
        }

    @Test
    fun `given tag inputs, when creating expense, then saves an expense carrying the resolved tag ids`() = runTest {
        val tagInputs = listOf(TagInput.Existing("tag-nala"), TagInput.New("Comida"))
        stubAccountAndCategory()
        coEvery { tagResolver.resolve(tagInputs) } returns Outcome.Success(listOf("tag-nala", "tag-comida"))
        coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)
        val result = createWithTags(tagInputs)
        assertEquals(listOf("tag-nala", "tag-comida"), (result as Outcome.Success).value.tagIds)
        coVerify { expenseRepository.add(match { it.tagIds == listOf("tag-nala", "tag-comida") }) }
    }

    @Test
    fun `given a tag name too long, when creating expense, then returns the tags error and saves nothing`() =
        runTest {
            val tagInputs = listOf(TagInput.New("a".repeat(Tag.MAX_NAME_LENGTH + 1)))
            stubAccountAndCategory()
            coEvery { tagResolver.resolve(tagInputs) } returns
                Outcome.Failure(TagResolutionError.InvalidName(TagNameError.TooLong()))
            val result = createWithTags(tagInputs)
            val error = (result as Outcome.Failure).error as ExpenseCreationError.InvalidInput
            assertEquals("La etiqueta no puede superar 30 caracteres.", error.tagsError)
            coVerify(exactly = 0) { expenseRepository.add(any()) }
        }

    @Test
    fun `given tag resolution fails for a technical reason, when creating expense, then returns StorageFailure`() =
        runTest {
            val tagInputs = listOf(TagInput.New("Carro"))
            stubAccountAndCategory()
            coEvery { tagResolver.resolve(tagInputs) } returns
                Outcome.Failure(TagResolutionError.StorageFailure(internalMessage = "tag write error"))
            val result = createWithTags(tagInputs)
            val error = (result as Outcome.Failure).error
            assertTrue(error is ExpenseCreationError.StorageFailure)
            assertEquals("tag write error", error.internalMessage)
            coVerify(exactly = 0) { expenseRepository.add(any()) }
        }

    @Test
    fun `given tag inputs, when creating expense, then the tags are resolved inside the transaction`() = runTest {
        val tagInputs = listOf(TagInput.New("Carro"))
        var isInsideTransaction = false
        val trackingTransactionRunner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> {
                isInsideTransaction = true
                return block().also { isInsideTransaction = false }
            }
        }
        val trackingExpenseCreator = ExpenseCreator(
            accountRepository = accountRepository,
            expenseRepository = expenseRepository,
            categoryRepository = categoryRepository,
            categoryCreator = categoryCreator,
            tagResolver = tagResolver,
            transactionRunner = trackingTransactionRunner,
            clock = fixedClock
        )
        val resolvedInsideTransaction = mutableListOf<Boolean>()
        stubAccountAndCategory()
        coEvery { tagResolver.resolve(tagInputs) } coAnswers {
            resolvedInsideTransaction.add(isInsideTransaction)
            Outcome.Success(listOf("tag-carro"))
        }
        coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)
        trackingExpenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = today.toString(),
            categoryInput = CategoryInput.Existing(expenseCategory.id),
            description = "",
            tagInputs = tagInputs
        )
        assertEquals(listOf(true), resolvedInsideTransaction)
    }

    private fun stubAccountAndCategory() {
        every {
            accountRepository.findById("acc-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(account))
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(expenseCategory)))
    }

    private suspend fun createWithTags(tagInputs: List<TagInput>): Outcome<Expense> =
        this.expenseCreator.create(
            accountId = "acc-1",
            amount = "500.00",
            date = this.today.toString(),
            categoryInput = CategoryInput.Existing(this.expenseCategory.id),
            description = "",
            tagInputs = tagInputs
        )
}
