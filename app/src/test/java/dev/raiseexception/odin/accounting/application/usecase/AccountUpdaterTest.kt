package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AccountUpdaterTest {

    private val accountRepository = mockk<AccountRepository>()
    private val accountFinder = AccountFinder(accountRepository)
    private val updater = AccountUpdater(accountFinder, accountRepository)
    private val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    @Test
    fun `given valid changes and no name change, when update, then edits and saves without a uniqueness check`() =
        runTest {
            val existing = AccountBuilder().id("acc-1").name("Ahorros").build()
            coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))
            coEvery { accountRepository.update(any()) } returns Outcome.Success(Unit)

            val result = updater.update(
                id = "acc-1",
                name = "Ahorros",
                initialBalance = "2000.00",
                currency = Currency.COP,
                type = AccountType.CASH,
                description = "Nueva descripción"
            )

            assertTrue(result is Outcome.Success)
            coVerify(exactly = 0) { accountRepository.existsByName(any()) }
            coVerify { accountRepository.update(any()) }
        }

    @Test
    fun `given a changed name that is unique, when update, then saves`() = runTest {
        val existing = AccountBuilder().id("acc-1").name("Ahorros").build()
        coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountRepository.existsByName("Corriente") } returns Outcome.Success(false)
        coEvery { accountRepository.update(any()) } returns Outcome.Success(Unit)

        val result = updater.update(
            id = "acc-1",
            name = "Corriente",
            initialBalance = "2000.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Success)
        coVerify { accountRepository.update(any()) }
    }

    @Test
    fun `given a changed name that duplicates another, when update, then DuplicateName and no save`() = runTest {
        val existing = AccountBuilder().id("acc-1").name("Ahorros").build()
        coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountRepository.existsByName("Corriente") } returns Outcome.Success(true)

        val result = updater.update(
            id = "acc-1",
            name = "Corriente",
            initialBalance = "2000.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountUpdateError.DuplicateName)
        coVerify(exactly = 0) { accountRepository.update(any()) }
    }

    @Test
    fun `given the same name in different case, when update, then no duplicate error`() = runTest {
        val existing = AccountBuilder().id("acc-1").name("Ahorros").build()
        coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountRepository.update(any()) } returns Outcome.Success(Unit)

        val result = updater.update(
            id = "acc-1",
            name = "ahorros",
            initialBalance = "2000.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Success)
        coVerify(exactly = 0) { accountRepository.existsByName(any()) }
    }

    @Test
    fun `given an account with movements, when update, then incoming currency and balance are ignored`() = runTest {
        val existing = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "500.00", date = "2026-01-01")
            .build()
        coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))
        val savedAccount = slot<dev.raiseexception.odin.accounting.domain.model.Account>()
        coEvery { accountRepository.update(capture(savedAccount)) } returns Outcome.Success(Unit)

        val result = updater.update(
            id = "acc-1",
            name = "Ahorros",
            initialBalance = "9999.00",
            currency = Currency.USD,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Success)
        assertEquals(Currency.COP, savedAccount.captured.currency)
        val funds = savedAccount.captured.funding as AccountFunding.Funds
        assertEquals(0, funds.initialBalance.amount.compareTo(BigDecimal("1000.00")))
    }

    @Test
    fun `given an account with no movements, when update, then new currency and balance are saved`() = runTest {
        val existing = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()
        coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))
        val savedAccount = slot<dev.raiseexception.odin.accounting.domain.model.Account>()
        coEvery { accountRepository.update(capture(savedAccount)) } returns Outcome.Success(Unit)

        val result = updater.update(
            id = "acc-1",
            name = "Ahorros",
            initialBalance = "2000.00",
            currency = Currency.USD,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Success)
        assertEquals(Currency.USD, savedAccount.captured.currency)
        val funds = savedAccount.captured.funding as AccountFunding.Funds
        assertEquals(0, funds.initialBalance.amount.compareTo(BigDecimal("2000.00")))
    }

    @Test
    fun `given invalid input, when update, then InvalidInput and no save`() = runTest {
        val existing = AccountBuilder().id("acc-1").name("Ahorros").build()
        coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))

        val result = updater.update(
            id = "acc-1",
            name = "",
            initialBalance = "2000.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountUpdateError.InvalidInput)
        coVerify(exactly = 0) { accountRepository.update(any()) }
    }

    @Test
    fun `given the account does not exist, when update, then NotFound and no save`() = runTest {
        coEvery { accountRepository.findById("missing", criteria) } returns flowOf(
            Outcome.Failure(
                AccountLookupError.NotFound(
                    internalMessage = "Account with id missing not found",
                    externalMessage = "Cuenta no encontrada"
                )
            )
        )

        val result = updater.update(
            id = "missing",
            name = "Ahorros",
            initialBalance = "2000.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountLookupError.NotFound)
        coVerify(exactly = 0) { accountRepository.update(any()) }
    }

    @Test
    fun `given the repository update fails, when update, then StorageFailure`() = runTest {
        val existing = AccountBuilder().id("acc-1").name("Ahorros").build()
        coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountRepository.update(any()) } returns Outcome.Failure(
            AccountUpdateError.StorageFailure(
                internalMessage = "storage broke",
                externalMessage = "No se pudo guardar la cuenta. Inténtalo de nuevo."
            )
        )

        val result = updater.update(
            id = "acc-1",
            name = "Ahorros",
            initialBalance = "2000.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = ""
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountUpdateError.StorageFailure)
    }
}
