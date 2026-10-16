package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.TransactionDeletionError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Transaction
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.accounting.domain.repository.TagRepository
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

class TransactionDeleterTest {

    private val transactionFinder = mockk<TransactionFinder>()
    private val accountFinder = mockk<AccountFinder>()
    private val incomeRepository = mockk<IncomeRepository>()
    private val expenseRepository = mockk<ExpenseRepository>()
    private val tagRepository = mockk<TagRepository>()
    private val transactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> = block()
    }
    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val fullCriteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    private val transactionDeleter = TransactionDeleter(
        transactionFinder = transactionFinder,
        accountFinder = accountFinder,
        incomeRepository = incomeRepository,
        expenseRepository = expenseRepository,
        tagRepository = tagRepository,
        transactionRunner = transactionRunner,
        clock = fixedClock
    )

    private val salary: Income = Income.restore(
        id = "income-salary",
        accountId = "acc-1",
        amount = Money.of(BigDecimal("1000000"), Currency.COP),
        date = LocalDate.parse("2026-03-05"),
        categoryId = "cat-salary",
        description = "Salario",
        createdAt = Instant.parse("2026-03-05T12:00:00Z")
    )
    private val lunch: Expense = Expense.restore(
        id = "expense-lunch",
        accountId = "acc-1",
        amount = Money.of(BigDecimal("30000"), Currency.COP),
        date = LocalDate.parse("2026-03-08"),
        categoryId = "cat-food",
        description = "Almuerzo",
        createdAt = Instant.parse("2026-03-08T12:00:00Z"),
        tagIds = listOf("tag-nala")
    )

    @Test
    fun `given an expense, when deleting it, then deletes it, cleans unused tags, and succeeds`() = runTest {
        val savingsAccount = savingsAccount(initialBalance = "0")
        stubFound(lunch, savingsAccount)
        coEvery { expenseRepository.delete(lunch.id) } returns Outcome.Success(Unit)
        coEvery { tagRepository.deleteUnused() } returns Outcome.Success(Unit)
        val result = transactionDeleter.delete(lunch.id)
        assertEquals(Outcome.Success(Unit), result)
        assertTrue(savingsAccount.expenses.isEmpty())
        coVerify { expenseRepository.delete(lunch.id) }
        coVerify { tagRepository.deleteUnused() }
        coVerify(exactly = 0) { incomeRepository.delete(any()) }
    }

    @Test
    fun `given an income, when deleting it, then deletes it without cleaning tags and succeeds`() = runTest {
        val savingsAccount = savingsAccount(initialBalance = "100000")
        stubFound(salary, savingsAccount)
        coEvery { incomeRepository.delete(salary.id) } returns Outcome.Success(Unit)
        val result = transactionDeleter.delete(salary.id)
        assertEquals(Outcome.Success(Unit), result)
        assertTrue(savingsAccount.incomes.isEmpty())
        coVerify { incomeRepository.delete(salary.id) }
        coVerify(exactly = 0) { tagRepository.deleteUnused() }
        coVerify(exactly = 0) { expenseRepository.delete(any()) }
    }

    @Test
    fun `given the transaction does not exist, when deleting it, then returns NotFound and deletes nothing`() =
        runTest {
            every { transactionFinder.find("missing") } returns flowOf(
                Outcome.Failure(TransactionLookupError.NotFound("Transaction not found", "Transacción no encontrada"))
            )
            val result = transactionDeleter.delete("missing")
            assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
            assertNothingDeleted()
        }

    @Test
    fun `given a transfer side, when deleting it, then returns TransferNotDeletable and deletes nothing`() = runTest {
        every { transactionFinder.find(lunch.id) } returns flowOf(Outcome.Success(detail(lunch, isTransfer = true)))
        val result = transactionDeleter.delete(lunch.id)
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransactionDeletionError.TransferNotDeletable)
        assertEquals("Las transferencias no se pueden eliminar.", error.externalMessage)
        assertNothingDeleted()
    }

    @Test
    fun `given the lookup fails in storage, when deleting, then returns StorageFailure and deletes nothing`() =
        runTest {
            every { transactionFinder.find(lunch.id) } returns flowOf(Outcome.Failure(StorageError("lookup failed")))
            val result = transactionDeleter.delete(lunch.id)
            val error = (result as Outcome.Failure).error
            assertTrue(error is TransactionDeletionError.StorageFailure)
            assertEquals("lookup failed", error.internalMessage)
            assertNothingDeleted()
        }

    @Test
    fun `given the account cannot be loaded, when deleting an expense, then returns StorageFailure`() = runTest {
        every { transactionFinder.find(lunch.id) } returns flowOf(Outcome.Success(detail(lunch)))
        every { accountFinder.find("acc-1", fullCriteria) } returns
            flowOf(Outcome.Failure(StorageError("account load failed")))
        val result = transactionDeleter.delete(lunch.id)
        assertStorageFailure(result, "No se pudo eliminar el gasto. Inténtalo de nuevo.", "account load failed")
        assertNothingDeleted()
    }

    @Test
    fun `given the account cannot be loaded, when deleting an income, then returns StorageFailure`() = runTest {
        every { transactionFinder.find(salary.id) } returns flowOf(Outcome.Success(detail(salary)))
        every { accountFinder.find("acc-1", fullCriteria) } returns
            flowOf(Outcome.Failure(StorageError("account load failed")))
        val result = transactionDeleter.delete(salary.id)
        assertStorageFailure(result, "No se pudo eliminar el ingreso. Inténtalo de nuevo.", "account load failed")
        assertNothingDeleted()
    }

    @Test
    fun `given the deletion breaks the account history, when deleting, then returns HistoryBreach`() = runTest {
        val savingsAccount = savingsAccount(initialBalance = "0")
        stubFound(salary, savingsAccount)
        val result = transactionDeleter.delete(salary.id)
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransactionDeletionError.HistoryBreach)
        assertEquals("El saldo de la cuenta quedaría negativo el 8 de marzo.", error.externalMessage)
        assertEquals(listOf(salary), savingsAccount.incomes)
        assertNothingDeleted()
    }

    @Test
    fun `given the expense delete fails, when deleting, then returns StorageFailure for the expense`() = runTest {
        stubFound(lunch, savingsAccount(initialBalance = "0"))
        coEvery { expenseRepository.delete(lunch.id) } returns Outcome.Failure(StorageError("expense delete failed"))
        val result = transactionDeleter.delete(lunch.id)
        assertStorageFailure(result, "No se pudo eliminar el gasto. Inténtalo de nuevo.", "expense delete failed")
        coVerify(exactly = 0) { tagRepository.deleteUnused() }
    }

    @Test
    fun `given the income delete fails, when deleting, then returns StorageFailure for the income`() = runTest {
        stubFound(salary, savingsAccount(initialBalance = "100000"))
        coEvery { incomeRepository.delete(salary.id) } returns Outcome.Failure(StorageError("income delete failed"))
        val result = transactionDeleter.delete(salary.id)
        assertStorageFailure(result, "No se pudo eliminar el ingreso. Inténtalo de nuevo.", "income delete failed")
    }

    @Test
    fun `given the unused tag cleanup fails, when deleting an expense, then returns StorageFailure`() = runTest {
        stubFound(lunch, savingsAccount(initialBalance = "0"))
        coEvery { expenseRepository.delete(lunch.id) } returns Outcome.Success(Unit)
        coEvery { tagRepository.deleteUnused() } returns Outcome.Failure(StorageError("cleanup failed"))
        val result = transactionDeleter.delete(lunch.id)
        assertStorageFailure(result, "No se pudo eliminar el gasto. Inténtalo de nuevo.", "cleanup failed")
    }

    @Test
    fun `given an expense, when deleting it, then reads the account inside the transaction`() = runTest {
        val trackingRunner = TrackingTransactionRunner()
        val trackedDeleter = TransactionDeleter(
            transactionFinder = transactionFinder,
            accountFinder = accountFinder,
            incomeRepository = incomeRepository,
            expenseRepository = expenseRepository,
            tagRepository = tagRepository,
            transactionRunner = trackingRunner,
            clock = fixedClock
        )
        val savingsAccount = savingsAccount(initialBalance = "0")
        var accountReadInsideTransaction = false
        every { transactionFinder.find(lunch.id) } returns flowOf(Outcome.Success(detail(lunch)))
        every { accountFinder.find("acc-1", fullCriteria) } answers {
            accountReadInsideTransaction = trackingRunner.isRunning
            flowOf(Outcome.Success(savingsAccount))
        }
        coEvery { expenseRepository.delete(lunch.id) } returns Outcome.Success(Unit)
        coEvery { tagRepository.deleteUnused() } returns Outcome.Success(Unit)
        trackedDeleter.delete(lunch.id)
        assertTrue(accountReadInsideTransaction)
    }

    @Test
    fun `given a transaction that is neither an income nor an expense, when deleting it, then returns NotFound`() =
        runTest {
            val unknownMovement = object : Transaction {
                override val id = "unknown-movement"
                override val accountId = "acc-1"
                override val amount = Money.of(BigDecimal("1000"), Currency.COP)
                override val date = LocalDate.parse("2026-03-08")
                override val categoryId = "cat-food"
                override val description = ""
                override val createdAt = Instant.parse("2026-03-08T12:00:00Z")
            }
            stubFound(unknownMovement, savingsAccount(initialBalance = "0"))
            val result = transactionDeleter.delete(unknownMovement.id)
            assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
            assertNothingDeleted()
        }

    private fun stubFound(transaction: Transaction, account: Account) {
        every { transactionFinder.find(transaction.id) } returns flowOf(Outcome.Success(detail(transaction)))
        every { accountFinder.find("acc-1", fullCriteria) } returns flowOf(Outcome.Success(account))
    }

    private fun savingsAccount(initialBalance: String): Account = AccountBuilder()
        .id("acc-1")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .initialBalance(Money.of(BigDecimal(initialBalance), Currency.COP))
        .incomes(listOf(this.salary))
        .expenses(listOf(this.lunch))
        .build()

    private fun detail(transaction: Transaction, isTransfer: Boolean = false): TransactionDetail = TransactionDetail(
        transaction,
        "Comida",
        "Ahorros",
        isTransfer = isTransfer,
        accountType = AccountType.SAVINGS,
        tags = emptyList()
    )

    private fun assertNothingDeleted() {
        coVerify(exactly = 0) { incomeRepository.delete(any()) }
        coVerify(exactly = 0) { expenseRepository.delete(any()) }
        coVerify(exactly = 0) { tagRepository.deleteUnused() }
    }

    private fun assertStorageFailure(result: Outcome<Unit>, externalMessage: String, internalMessage: String) {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransactionDeletionError.StorageFailure)
        assertEquals(externalMessage, error.externalMessage)
        assertEquals(internalMessage, error.internalMessage)
    }

    private class TrackingTransactionRunner : TransactionRunner {
        var isRunning = false
            private set

        override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> {
            this.isRunning = true
            val outcome = block()
            this.isRunning = false
            return outcome
        }
    }
}
