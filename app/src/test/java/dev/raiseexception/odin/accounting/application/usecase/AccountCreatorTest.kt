package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCreatorTest {

    private val accountRepository = mockk<AccountRepository>()
    private val creator = AccountCreator(accountRepository)

    @Test
    fun `given invalid input, when creating, then returns invalid input and does not touch the repository`() = runTest {
        val result = creator.create(
            name = "",
            initialBalance = "10.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountCreationError.InvalidInput)
        coVerify(exactly = 0) { accountRepository.existsByName(any()) }
        coVerify(exactly = 0) { accountRepository.add(any()) }
    }

    @Test
    fun `given a unique valid account, when creating, then adds it and returns success`() = runTest {
        coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Success(false)
        coEvery { accountRepository.add(any()) } returns Outcome.Success(Unit)

        val result = creator.create(
            name = "Ahorros",
            initialBalance = "1500.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = "Fondo de emergencia"
        )

        assertTrue(result is Outcome.Success)
        coVerify { accountRepository.add(any()) }
    }

    @Test
    fun `given a duplicate name, when creating, then returns duplicate name and does not add`() = runTest {
        coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Success(true)

        val result = creator.create(
            name = "Ahorros",
            initialBalance = "1500.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountCreationError.DuplicateName)
        coVerify(exactly = 0) { accountRepository.add(any()) }
    }

    @Test
    fun `given existsByName fails, when creating, then propagates the failure`() = runTest {
        coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Failure(
            AccountCreationError.CryptoFailure(
                internalMessage = "crypto broke",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )

        val result = creator.create(
            name = "Ahorros",
            initialBalance = "1500.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountCreationError.CryptoFailure)
        coVerify(exactly = 0) { accountRepository.add(any()) }
    }

    @Test
    fun `given valid input when create succeeds then returned account has createdAt set`() = runTest {
        coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Success(false)
        coEvery { accountRepository.add(any()) } returns Outcome.Success(Unit)
        val before = Clock.System.now()

        val result = creator.create(
            name = "Ahorros",
            initialBalance = "1500.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        val after = Clock.System.now()
        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        assertTrue(account.createdAt >= before)
        assertTrue(account.createdAt <= after)
    }

    @Test
    fun `given add fails, when creating, then propagates the failure`() = runTest {
        coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Success(false)
        coEvery { accountRepository.add(any()) } returns Outcome.Failure(
            AccountCreationError.StorageFailure(
                internalMessage = "storage broke",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )

        val result = creator.create(
            name = "Ahorros",
            initialBalance = "1500.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountCreationError.StorageFailure)
    }
}
