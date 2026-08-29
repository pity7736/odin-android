package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AccountFinderTest {

    private val accountRepository = mockk<AccountRepository>()
    private val accountFinder = AccountFinder(accountRepository)

    @Test
    fun `given an existing account, when find is called with its id, then returns the account`() = runTest {
        val savings = AccountBuilder().id("abc-123").build()
        coEvery { accountRepository.findById("abc-123", AccountCriteria()) } returns Outcome.Success(savings)

        val result = accountFinder.find("abc-123")

        assertTrue(result is Outcome.Success)
        assertEquals(savings, (result as Outcome.Success).value)
    }

    @Test
    fun `given no account with the id, when find is called, then returns NotFound`() = runTest {
        coEvery { accountRepository.findById("missing", AccountCriteria()) } returns Outcome.Failure(
            AccountLookupError.NotFound(
                internalMessage = "Account with id missing not found",
                externalMessage = "Cuenta no encontrada"
            )
        )

        val result = accountFinder.find("missing")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountLookupError.NotFound)
    }

    @Test
    fun `given a storage failure, when find is called, then returns StorageFailure`() = runTest {
        coEvery { accountRepository.findById("any", AccountCriteria()) } returns Outcome.Failure(
            AccountLookupError.StorageFailure(
                internalMessage = "Storage error",
                externalMessage = "Error al cargar la cuenta"
            )
        )

        val result = accountFinder.find("any")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountLookupError.StorageFailure)
    }

    @Test
    fun `given existing account, when finding with criteria, then returns account with incomes`() = runTest {
        val income = Income.restore(
            id = "inc-1",
            accountId = "abc-123",
            amount = Money.of(BigDecimal("500.00"), Currency.COP),
            date = LocalDate.parse("2026-08-28"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-08-28T10:00:00Z")
        )
        val accountWithIncomes = AccountBuilder()
            .id("abc-123")
            .incomes(listOf(income))
            .build()
        val criteria = AccountCriteria(includeIncomes = true)
        coEvery { accountRepository.findById("abc-123", criteria) } returns Outcome.Success(accountWithIncomes)

        val result = accountFinder.find("abc-123", criteria)

        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        assertEquals(1, account.incomes.size)
        assertEquals("inc-1", account.incomes.first().id)
    }
}
