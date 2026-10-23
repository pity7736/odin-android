package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.RecordingTransactionRunner
import io.mockk.CapturingSlot
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
    private val categoryResolver = mockk<CategoryResolver>()
    private val transactionRunner = RecordingTransactionRunner()
    private val updater = AccountUpdater(accountFinder, accountRepository, categoryResolver, transactionRunner)
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
                kind = MoneyAccountKind.CASH,
                description = "Nueva descripción",
                gmf = GmfInput.Exempt
            )

            assertTrue(result is Outcome.Success)
            coVerify(exactly = 0) { accountRepository.existsByName(any()) }
            coVerify { accountRepository.update(any()) }
        }

    @Test
    fun `given a new kind, when update, then the persisted account carries it`() = runTest {
        val existing = AccountBuilder().id("acc-1").name("Ahorros").kind(MoneyAccountKind.SAVINGS).build()
        coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))
        val savedAccount = slot<Account>()
        coEvery { accountRepository.update(capture(savedAccount)) } returns Outcome.Success(Unit)

        updater.update(
            id = "acc-1",
            name = "Ahorros",
            initialBalance = "2000.00",
            currency = Currency.COP,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfInput.Exempt
        )

        assertEquals(MoneyAccountKind.CASH, (savedAccount.captured.funding as AccountFunding.Funds).kind)
        assertEquals(AccountType.CASH, savedAccount.captured.type)
    }

    @Test
    fun `given a credit card, when update, then returns CreditCardNotEditable and nothing is persisted`() = runTest {
        val card = AccountBuilder()
            .id("card-1")
            .name("Visa")
            .creditCard(Money.of(BigDecimal("1000000"), Currency.COP), Money.of(BigDecimal("0"), Currency.COP))
            .build()
        coEvery { accountRepository.findById("card-1", criteria) } returns flowOf(Outcome.Success(card))

        val result = updater.update(
            id = "card-1",
            name = "Visa",
            initialBalance = "1000000",
            currency = Currency.COP,
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfInput.Exempt
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountUpdateError.CreditCardNotEditable)
        coVerify(exactly = 0) { accountRepository.update(any()) }
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
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfInput.Exempt
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
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfInput.Exempt
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
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfInput.Exempt
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
        val savedAccount = slot<Account>()
        coEvery { accountRepository.update(capture(savedAccount)) } returns Outcome.Success(Unit)

        val result = updater.update(
            id = "acc-1",
            name = "Ahorros",
            initialBalance = "9999.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfInput.Exempt
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
        val savedAccount = slot<Account>()
        coEvery { accountRepository.update(capture(savedAccount)) } returns Outcome.Success(Unit)

        val result = updater.update(
            id = "acc-1",
            name = "Ahorros",
            initialBalance = "2000.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfInput.Exempt
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
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfInput.Exempt
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
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfInput.Exempt
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
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfInput.Exempt
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountUpdateError.StorageFailure)
    }

    @Test
    fun `given an existing tax category, when marking Ahorros as not exempt, then it is saved with that category`() =
        runTest {
            val saved = stubAhorros(GmfTreatment.Exempt)
            coEvery {
                categoryResolver.resolve(CategoryInput.Existing("cat-impuestos"), CategoryType.EXPENSE)
            } returns Outcome.Success(CategoryResolution.Resolved("cat-impuestos"))

            val result = update(name = "Ahorros", gmf = taxed(CategoryInput.Existing("cat-impuestos")))

            assertTrue(result is Outcome.Success)
            val funds = saved.captured.funding as AccountFunding.Funds
            assertEquals(GmfTreatment.Taxed("cat-impuestos", listOf("4x1000")), funds.gmf)
            assertTrue(transactionRunner.committed)
        }

    @Test
    fun `given no category named GMF, when marking Ahorros as not exempt typing GMF, then GMF is created and used`() =
        runTest {
            val saved = stubAhorros(GmfTreatment.Exempt)
            coEvery { categoryResolver.resolve(CategoryInput.New("GMF"), CategoryType.EXPENSE) } returns
                Outcome.Success(CategoryResolution.Resolved("cat-gmf"))

            update(name = "Ahorros", gmf = taxed(CategoryInput.New("GMF")))

            val funds = saved.captured.funding as AccountFunding.Funds
            assertEquals(GmfTreatment.Taxed("cat-gmf", listOf("4x1000")), funds.gmf)
        }

    @Test
    fun `given an empty tax category, when marking Ahorros as not exempt, then the tax category is required`() =
        runTest {
            stubAhorros(GmfTreatment.Exempt)

            val result = update(name = "Ahorros", gmf = taxed(CategoryInput.New("")))

            val error = (result as Outcome.Failure).error as AccountUpdateError.InvalidInput
            assertEquals("La categoría del 4x1000 es obligatoria.", error.gmfCategoryError)
            coVerify(exactly = 0) { categoryResolver.resolve(any(), any()) }
            coVerify(exactly = 0) { accountRepository.update(any()) }
        }

    @Test
    fun `given a rejected tax category and no name, when updating, then both errors are reported`() = runTest {
        stubAhorros(GmfTreatment.Exempt)
        coEvery {
            categoryResolver.resolve(CategoryInput.Existing("cat-salario"), CategoryType.EXPENSE)
        } returns Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no es de tipo gasto."))

        val result = update(name = "", gmf = taxed(CategoryInput.Existing("cat-salario")))

        val error = (result as Outcome.Failure).error as AccountUpdateError.InvalidInput
        assertEquals("La categoría seleccionada no es de tipo gasto.", error.gmfCategoryError)
        assertEquals("El nombre es obligatorio.", error.nameError)
        assertTrue(transactionRunner.rolledBack)
    }

    @Test
    fun `given a new tax category and a duplicate name, when updating, then everything is rolled back`() = runTest {
        stubAhorros(GmfTreatment.Exempt)
        coEvery { accountRepository.existsByName("Corriente") } returns Outcome.Success(true)
        coEvery { categoryResolver.resolve(CategoryInput.New("GMF"), CategoryType.EXPENSE) } returns
            Outcome.Success(CategoryResolution.Resolved("cat-gmf"))

        val result = update(name = "Corriente", gmf = taxed(CategoryInput.New("GMF")))

        assertTrue((result as Outcome.Failure).error is AccountUpdateError.DuplicateName)
        assertTrue(transactionRunner.rolledBack)
        coVerify(exactly = 0) { accountRepository.update(any()) }
    }

    @Test
    fun `given the tax category cannot be resolved for a technical reason, when updating, then it fails unsaved`() =
        runTest {
            stubAhorros(GmfTreatment.Exempt)
            coEvery { categoryResolver.resolve(CategoryInput.New("GMF"), CategoryType.EXPENSE) } returns
                Outcome.Failure(StorageError("disk full"))

            val result = update(name = "Ahorros", gmf = taxed(CategoryInput.New("GMF")))

            assertTrue((result as Outcome.Failure).error is StorageError)
            coVerify(exactly = 0) { accountRepository.update(any()) }
        }

    @Test
    fun `given a non-exempt Ahorros, when saving it as exempt, then it is saved exempt without resolving a category`() =
        runTest {
            val saved = stubAhorros(GmfTreatment.Taxed("cat-impuestos", listOf("4x1000")))

            update(name = "Ahorros", gmf = GmfInput.Exempt)

            assertEquals(GmfTreatment.Exempt, (saved.captured.funding as AccountFunding.Funds).gmf)
            coVerify(exactly = 0) { categoryResolver.resolve(any(), any()) }
        }

    private fun taxed(categoryInput: CategoryInput): GmfInput = GmfInput.Taxed(categoryInput, listOf("4x1000"))

    private fun stubAhorros(gmf: GmfTreatment): CapturingSlot<Account> {
        val existing = AccountBuilder().id("acc-1").name("Ahorros").gmf(gmf).build()
        val saved = slot<Account>()
        coEvery { accountRepository.findById("acc-1", criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountRepository.update(capture(saved)) } returns Outcome.Success(Unit)
        return saved
    }

    private suspend fun update(name: String, gmf: GmfInput): Outcome<Account> = updater.update(
        id = "acc-1",
        name = name,
        initialBalance = "2000.00",
        currency = Currency.COP,
        kind = MoneyAccountKind.SAVINGS,
        description = "",
        gmf = gmf
    )
}
