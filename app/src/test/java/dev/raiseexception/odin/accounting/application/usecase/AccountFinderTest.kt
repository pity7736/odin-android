package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
        every { accountRepository.findById("abc-123", AccountCriteria()) } returns flowOf(Outcome.Success(savings))
        val result = accountFinder.find("abc-123").first()
        assertTrue(result is Outcome.Success)
        assertEquals(savings, (result as Outcome.Success).value)
    }

    @Test
    fun `given no account with the id, when find is called, then returns NotFound`() = runTest {
        every { accountRepository.findById("missing", AccountCriteria()) } returns flowOf(
            Outcome.Failure(
                AccountLookupError.NotFound(
                    internalMessage = "Account with id missing not found",
                    externalMessage = "Cuenta no encontrada"
                )
            )
        )
        val result = accountFinder.find("missing").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountLookupError.NotFound)
    }

    @Test
    fun `given a storage failure, when find is called, then returns StorageFailure`() = runTest {
        every { accountRepository.findById("any", AccountCriteria()) } returns flowOf(
            Outcome.Failure(
                dev.raiseexception.odin.shared.domain.StorageError(
                    internalMessage = "Storage error"
                )
            )
        )
        val result = accountFinder.find("any").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is dev.raiseexception.odin.shared.domain.StorageError)
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
        every { accountRepository.findById("abc-123", criteria) } returns flowOf(Outcome.Success(accountWithIncomes))
        val result = accountFinder.find("abc-123", criteria).first()
        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        assertEquals(1, account.incomes.size)
        assertEquals("inc-1", account.incomes.first().id)
    }
}
