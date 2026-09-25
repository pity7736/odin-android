package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCreatorTest {

    private val accountRepository = mockk<AccountRepository>()
    private val creator = AccountCreator(accountRepository)

    private fun moneyAccount(
        name: String = "Ahorros",
        balance: String = "1500.00",
        currency: Currency? = Currency.COP,
        type: AccountType? = AccountType.SAVINGS,
        description: String = ""
    ) = CreateAccountCommand.MoneyAccount(name, balance, currency, type, description)

    private fun creditCard(
        name: String = "Visa",
        creditLimit: String = "3000000",
        existingDebt: String = "500000",
        currency: Currency? = Currency.COP,
        description: String = ""
    ) = CreateAccountCommand.CreditCard(name, creditLimit, existingDebt, currency, description)

    @Test
    fun `given invalid input, when creating, then returns invalid input and does not touch the repository`() = runTest {
        val result = creator.create(moneyAccount(name = ""))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountCreationError.InvalidInput)
        coVerify(exactly = 0) { accountRepository.existsByName(any()) }
        coVerify(exactly = 0) { accountRepository.add(any()) }
    }

    @Test
    fun `given a unique valid money account, when creating, then adds it and returns success`() = runTest {
        coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Success(false)
        coEvery { accountRepository.add(any()) } returns Outcome.Success(Unit)
        val before = Clock.System.now()

        val result = creator.create(moneyAccount(description = "Fondo de emergencia"))

        val after = Clock.System.now()
        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        assertTrue(account.createdAt >= before)
        assertTrue(account.createdAt <= after)
        coVerify { accountRepository.add(any()) }
    }

    @Test
    fun `given a unique valid credit card, when creating, then adds it with credit funding and returns success`() =
        runTest {
            coEvery { accountRepository.existsByName("Visa") } returns Outcome.Success(false)
            coEvery { accountRepository.add(any()) } returns Outcome.Success(Unit)

            val result = creator.create(creditCard())

            assertTrue(result is Outcome.Success)
            val account = (result as Outcome.Success).value
            assertTrue(account.funding is AccountFunding.Credit)
            assertEquals(AccountType.CREDIT_CARD, account.type)
            coVerify { accountRepository.add(any()) }
        }

    @Test
    fun `given an invalid credit card, when creating, then returns invalid input and does not touch the repository`() =
        runTest {
            val result = creator.create(creditCard(creditLimit = ""))

            assertTrue(result is Outcome.Failure)
            val error = (result as Outcome.Failure).error
            assertTrue(error is AccountCreationError.InvalidInput)
            assertEquals("El cupo es obligatorio.", (error as AccountCreationError.InvalidInput).creditLimitError)
            coVerify(exactly = 0) { accountRepository.existsByName(any()) }
            coVerify(exactly = 0) { accountRepository.add(any()) }
        }

    @Test
    fun `given a duplicate name, when creating, then returns duplicate name and does not add`() = runTest {
        coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Success(true)

        val result = creator.create(moneyAccount())

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

        val result = creator.create(moneyAccount())

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountCreationError.CryptoFailure)
        coVerify(exactly = 0) { accountRepository.add(any()) }
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

        val result = creator.create(moneyAccount())

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountCreationError.StorageFailure)
    }
}
