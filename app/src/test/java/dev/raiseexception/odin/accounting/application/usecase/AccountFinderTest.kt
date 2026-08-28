package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountFinderTest {

    private val accountRepository = mockk<AccountRepository>()
    private val accountFinder = AccountFinder(accountRepository)

    @Test
    fun `given an existing account, when find is called with its id, then returns the account`() = runTest {
        val savings = AccountBuilder().id("abc-123").build()
        coEvery { accountRepository.findById("abc-123") } returns Outcome.Success(savings)

        val result = accountFinder.find("abc-123")

        assertTrue(result is Outcome.Success)
        assertEquals(savings, (result as Outcome.Success).value)
    }

    @Test
    fun `given no account with the id, when find is called, then returns NotFound`() = runTest {
        coEvery { accountRepository.findById("missing") } returns Outcome.Failure(
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
        coEvery { accountRepository.findById("any") } returns Outcome.Failure(
            AccountLookupError.StorageFailure(
                internalMessage = "Storage error",
                externalMessage = "Error al cargar la cuenta"
            )
        )

        val result = accountFinder.find("any")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountLookupError.StorageFailure)
    }
}
