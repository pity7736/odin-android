package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.RecordingTransactionRunner
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
    private val categoryResolver = mockk<CategoryResolver>()
    private val transactionRunner = RecordingTransactionRunner()
    private val creator = AccountCreator(accountRepository, categoryResolver, transactionRunner)

    private fun moneyAccount(
        name: String = "Ahorros",
        currency: Currency? = Currency.COP,
        kind: MoneyAccountKind? = MoneyAccountKind.SAVINGS,
        description: String = "",
        gmf: GmfInput = GmfInput.Exempt
    ) = CreateAccountCommand.MoneyAccount(name, "1500.00", currency, kind, description, gmf)

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
    fun `given a cash kind money account, when creating, then funding is Funds with CASH kind and type is CASH`() =
        runTest {
            coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Success(false)
            coEvery { accountRepository.add(any()) } returns Outcome.Success(Unit)

            val result = creator.create(moneyAccount(kind = MoneyAccountKind.CASH))

            val account = (result as Outcome.Success).value
            assertEquals(MoneyAccountKind.CASH, (account.funding as AccountFunding.Funds).kind)
            assertEquals(AccountType.CASH, account.type)
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

    @Test
    fun `given an existing tax category, when creating a non-exempt account, then it is saved with that category`() =
        runTest {
            stubUniqueAndSaved()
            coEvery {
                categoryResolver.resolve(CategoryInput.Existing("cat-impuestos"), CategoryType.EXPENSE)
            } returns Outcome.Success(CategoryResolution.Resolved("cat-impuestos"))

            val result = creator.create(moneyAccount(gmf = taxed(CategoryInput.Existing("cat-impuestos"))))

            val funds = (result as Outcome.Success).value.funding as AccountFunding.Funds
            assertEquals(GmfTreatment.Taxed("cat-impuestos", listOf("4x1000")), funds.gmf)
            assertTrue(transactionRunner.committed)
        }

    @Test
    fun `given no category named GMF, when creating a non-exempt account typing GMF, then GMF is created and used`() =
        runTest {
            stubUniqueAndSaved()
            coEvery { categoryResolver.resolve(CategoryInput.New("GMF"), CategoryType.EXPENSE) } returns
                Outcome.Success(CategoryResolution.Resolved("cat-gmf"))

            val result = creator.create(moneyAccount(gmf = taxed(CategoryInput.New("GMF"))))

            val funds = (result as Outcome.Success).value.funding as AccountFunding.Funds
            assertEquals(GmfTreatment.Taxed("cat-gmf", listOf("4x1000")), funds.gmf)
            coVerify(exactly = 1) { categoryResolver.resolve(CategoryInput.New("GMF"), CategoryType.EXPENSE) }
        }

    @Test
    fun `given an empty tax category, when creating a non-exempt account, then the tax category is required`() =
        runTest {
            val result = creator.create(moneyAccount(gmf = taxed(CategoryInput.New(" "))))

            val error = (result as Outcome.Failure).error as AccountCreationError.InvalidInput
            assertEquals("La categoría del 4x1000 es obligatoria.", error.gmfCategoryError)
            coVerify(exactly = 0) { categoryResolver.resolve(any(), any()) }
            coVerify(exactly = 0) { accountRepository.add(any()) }
        }

    @Test
    fun `given a rejected tax category and no name, when creating, then both errors are reported`() = runTest {
        coEvery {
            categoryResolver.resolve(CategoryInput.Existing("cat-salario"), CategoryType.EXPENSE)
        } returns Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no es de tipo gasto."))

        val result = creator.create(
            moneyAccount(name = "", gmf = taxed(CategoryInput.Existing("cat-salario")))
        )

        val error = (result as Outcome.Failure).error as AccountCreationError.InvalidInput
        assertEquals("La categoría seleccionada no es de tipo gasto.", error.gmfCategoryError)
        assertEquals("El nombre es obligatorio.", error.nameError)
        assertTrue(transactionRunner.rolledBack)
    }

    @Test
    fun `given a new tax category and a duplicate name, when creating, then everything is rolled back`() = runTest {
        coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Success(true)
        coEvery { categoryResolver.resolve(CategoryInput.New("GMF"), CategoryType.EXPENSE) } returns
            Outcome.Success(CategoryResolution.Resolved("cat-gmf"))

        val result = creator.create(moneyAccount(gmf = taxed(CategoryInput.New("GMF"))))

        assertTrue((result as Outcome.Failure).error is AccountCreationError.DuplicateName)
        assertTrue(transactionRunner.rolledBack)
        coVerify(exactly = 0) { accountRepository.add(any()) }
    }

    @Test
    fun `given the tax category cannot be resolved for a technical reason, when creating, then it fails unsaved`() =
        runTest {
            coEvery { categoryResolver.resolve(CategoryInput.New("GMF"), CategoryType.EXPENSE) } returns
                Outcome.Failure(StorageError("disk full"))

            val result = creator.create(moneyAccount(gmf = taxed(CategoryInput.New("GMF"))))

            assertTrue((result as Outcome.Failure).error is StorageError)
            coVerify(exactly = 0) { accountRepository.add(any()) }
        }

    @Test
    fun `given an exempt account, when creating, then it is exempt and no category is resolved`() = runTest {
        stubUniqueAndSaved()

        val result = creator.create(moneyAccount(gmf = GmfInput.Exempt))

        val funds = (result as Outcome.Success).value.funding as AccountFunding.Funds
        assertEquals(GmfTreatment.Exempt, funds.gmf)
        coVerify(exactly = 0) { categoryResolver.resolve(any(), any()) }
    }

    private fun taxed(categoryInput: CategoryInput): GmfInput = GmfInput.Taxed(categoryInput, listOf("4x1000"))

    private fun stubUniqueAndSaved() {
        coEvery { accountRepository.existsByName("Ahorros") } returns Outcome.Success(false)
        coEvery { accountRepository.add(any()) } returns Outcome.Success(Unit)
    }
}
