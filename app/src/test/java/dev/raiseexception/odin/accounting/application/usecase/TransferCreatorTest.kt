package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.accounting.domain.repository.TransferRepository
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TransferCreatorTest {

    private val accountRepository = mockk<AccountRepository>()
    private val transferRepository = mockk<TransferRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val expenseRepository = mockk<ExpenseRepository>()
    private val incomeRepository = mockk<IncomeRepository>()
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val today = fixedInstant.toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val transferCreator = TransferCreator(
        accountRepository = accountRepository,
        transferRepository = transferRepository,
        categoryRepository = categoryRepository,
        expenseRepository = expenseRepository,
        incomeRepository = incomeRepository,
        transactionRunner = transactionRunner,
        clock = fixedClock
    )

    private val sourceAccount = AccountBuilder()
        .id("src-1")
        .name("Ahorros")
        .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
        .build()

    private val destinationAccount = AccountBuilder()
        .id("dst-1")
        .name("Efectivo")
        .initialBalance(Money.of(BigDecimal("500.00"), Currency.COP))
        .build()

    private val transferCategory = CategoryBuilder()
        .id("cat-transfer")
        .name("Transferencia")
        .type(CategoryType.TRANSFER)
        .build()

    @Test
    fun `given valid accounts and transfer category, when creating transfer, then saves all records`() =
        runTest {
            every {
                accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
            } returns flowOf(Outcome.Success(sourceAccount))
            every {
                accountRepository.findById("dst-1", AccountCriteria())
            } returns flowOf(Outcome.Success(destinationAccount))
            every {
                categoryRepository.findByType(CategoryType.TRANSFER)
            } returns flowOf(Outcome.Success(listOf(transferCategory)))
            coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)
            coEvery { incomeRepository.add(any()) } returns Outcome.Success(Unit)
            coEvery { transferRepository.add(any()) } returns Outcome.Success(Unit)
            val result = transferCreator.create(
                sourceAccountId = "src-1",
                destinationAccountId = "dst-1",
                amount = "200.00",
                date = today.toString()
            )
            assertTrue(result is Outcome.Success)
            coVerify { expenseRepository.add(any()) }
            coVerify { incomeRepository.add(any()) }
            coVerify { transferRepository.add(any()) }
        }

    @Test
    fun `given blank source account id, when creating transfer, then returns invalid input`() =
        runTest {
            val result = transferCreator.create(
                sourceAccountId = "",
                destinationAccountId = "dst-1",
                amount = "200.00",
                date = today.toString()
            )
            assertTrue(result is Outcome.Failure)
            val error = (result as Outcome.Failure).error
            assertTrue(error is TransferCreationError.InvalidInput)
            assertNotNull((error as TransferCreationError.InvalidInput).sourceAccountError)
        }

    @Test
    fun `given blank destination account id, when creating transfer, then returns invalid input`() =
        runTest {
            val result = transferCreator.create(
                sourceAccountId = "src-1",
                destinationAccountId = "",
                amount = "200.00",
                date = today.toString()
            )
            assertTrue(result is Outcome.Failure)
            val error = (result as Outcome.Failure).error
            assertTrue(error is TransferCreationError.InvalidInput)
            assertNotNull((error as TransferCreationError.InvalidInput).destinationAccountError)
        }

    @Test
    fun `given source account not found, when creating transfer, then returns storage failure`() = runTest {
        every {
            accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Failure(StorageError("Account not found")))
        val result = transferCreator.create(
            sourceAccountId = "src-1",
            destinationAccountId = "dst-1",
            amount = "200.00",
            date = today.toString()
        )
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is TransferCreationError.StorageFailure)
    }

    @Test
    fun `given destination account not found, when creating transfer, then returns storage failure`() = runTest {
        every {
            accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(sourceAccount))
        every {
            accountRepository.findById("dst-1", AccountCriteria())
        } returns flowOf(Outcome.Failure(StorageError("Account not found")))
        val result = transferCreator.create(
            sourceAccountId = "src-1",
            destinationAccountId = "dst-1",
            amount = "200.00",
            date = today.toString()
        )
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is TransferCreationError.StorageFailure)
    }

    @Test
    fun `given same source and destination, when creating transfer, then returns invalid input`() = runTest {
        every {
            accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(sourceAccount))
        every {
            accountRepository.findById("src-1", AccountCriteria())
        } returns flowOf(Outcome.Success(sourceAccount))
        every {
            categoryRepository.findByType(CategoryType.TRANSFER)
        } returns flowOf(Outcome.Success(listOf(transferCategory)))
        val result = transferCreator.create(
            sourceAccountId = "src-1",
            destinationAccountId = "src-1",
            amount = "200.00",
            date = today.toString()
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransferCreationError.InvalidInput)
        assertNotNull((error as TransferCreationError.InvalidInput).sourceAccountError)
    }

    @Test
    fun `given different currencies, when creating transfer, then returns invalid input`() = runTest {
        val usdAccount = AccountBuilder()
            .id("dst-1")
            .name("Dólares")
            .initialBalance(Money.of(BigDecimal("500.00"), Currency.USD))
            .build()
        every {
            accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(sourceAccount))
        every {
            accountRepository.findById("dst-1", AccountCriteria())
        } returns flowOf(Outcome.Success(usdAccount))
        every {
            categoryRepository.findByType(CategoryType.TRANSFER)
        } returns flowOf(Outcome.Success(listOf(transferCategory)))
        val result = transferCreator.create(
            sourceAccountId = "src-1",
            destinationAccountId = "dst-1",
            amount = "200.00",
            date = today.toString()
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransferCreationError.InvalidInput)
        assertNotNull((error as TransferCreationError.InvalidInput).destinationAccountError)
    }

    @Test
    fun `given insufficient funds, when creating transfer, then returns invalid input`() = runTest {
        every {
            accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(sourceAccount))
        every {
            accountRepository.findById("dst-1", AccountCriteria())
        } returns flowOf(Outcome.Success(destinationAccount))
        every {
            categoryRepository.findByType(CategoryType.TRANSFER)
        } returns flowOf(Outcome.Success(listOf(transferCategory)))
        val result = transferCreator.create(
            sourceAccountId = "src-1",
            destinationAccountId = "dst-1",
            amount = "5000.00",
            date = today.toString()
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransferCreationError.InvalidInput)
        assertNotNull((error as TransferCreationError.InvalidInput).amountError)
    }

    @Test
    fun `given transfer category not found, when creating transfer, then returns transfer category not found`() =
        runTest {
            every {
                accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
            } returns flowOf(Outcome.Success(sourceAccount))
            every {
                accountRepository.findById("dst-1", AccountCriteria())
            } returns flowOf(Outcome.Success(destinationAccount))
            every {
                categoryRepository.findByType(CategoryType.TRANSFER)
            } returns flowOf(Outcome.Success(emptyList()))
            val result = transferCreator.create(
                sourceAccountId = "src-1",
                destinationAccountId = "dst-1",
                amount = "200.00",
                date = today.toString()
            )
            assertTrue(result is Outcome.Failure)
            assertTrue((result as Outcome.Failure).error is TransferCreationError.TransferCategoryNotFound)
        }

    @Test
    fun `given expense save fails, when creating transfer, then returns storage failure`() = runTest {
        every {
            accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(sourceAccount))
        every {
            accountRepository.findById("dst-1", AccountCriteria())
        } returns flowOf(Outcome.Success(destinationAccount))
        every {
            categoryRepository.findByType(CategoryType.TRANSFER)
        } returns flowOf(Outcome.Success(listOf(transferCategory)))
        coEvery { expenseRepository.add(any()) } returns Outcome.Failure(StorageError("DB error"))
        val result = transferCreator.create(
            sourceAccountId = "src-1",
            destinationAccountId = "dst-1",
            amount = "200.00",
            date = today.toString()
        )
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is TransferCreationError.StorageFailure)
    }

    @Test
    fun `given income save fails, when creating transfer, then returns storage failure`() = runTest {
        every {
            accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(sourceAccount))
        every {
            accountRepository.findById("dst-1", AccountCriteria())
        } returns flowOf(Outcome.Success(destinationAccount))
        every {
            categoryRepository.findByType(CategoryType.TRANSFER)
        } returns flowOf(Outcome.Success(listOf(transferCategory)))
        coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)
        coEvery { incomeRepository.add(any()) } returns Outcome.Failure(StorageError("DB error"))
        val result = transferCreator.create(
            sourceAccountId = "src-1",
            destinationAccountId = "dst-1",
            amount = "200.00",
            date = today.toString()
        )
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is TransferCreationError.StorageFailure)
    }

    @Test
    fun `given transfer save fails, when creating transfer, then returns storage failure`() = runTest {
        every {
            accountRepository.findById("src-1", AccountCriteria(includeIncomes = true, includeExpenses = true))
        } returns flowOf(Outcome.Success(sourceAccount))
        every {
            accountRepository.findById("dst-1", AccountCriteria())
        } returns flowOf(Outcome.Success(destinationAccount))
        every {
            categoryRepository.findByType(CategoryType.TRANSFER)
        } returns flowOf(Outcome.Success(listOf(transferCategory)))
        coEvery { expenseRepository.add(any()) } returns Outcome.Success(Unit)
        coEvery { incomeRepository.add(any()) } returns Outcome.Success(Unit)
        coEvery { transferRepository.add(any()) } returns Outcome.Failure(StorageError("DB error"))
        val result = transferCreator.create(
            sourceAccountId = "src-1",
            destinationAccountId = "dst-1",
            amount = "200.00",
            date = today.toString()
        )
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is TransferCreationError.StorageFailure)
    }
}
