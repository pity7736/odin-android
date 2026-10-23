package dev.raiseexception.odin.accounting.presentation.accountcreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.CreateAccountCommand
import dev.raiseexception.odin.accounting.application.usecase.GmfInput
import dev.raiseexception.odin.accounting.application.usecase.TagLister
import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.CategoryBuilder
import dev.raiseexception.odin.testutil.TagBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAccountViewModelTest {

    private val accountCreator = mockk<AccountCreator>()
    private val categoryLister = mockk<CategoryLister>()
    private val tagLister = mockk<TagLister>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val savingsAccount = AccountBuilder().build()
    private val impuestos = CategoryBuilder().id("cat-impuestos").name("Impuestos").type(CategoryType.EXPENSE).build()
    private val gmfTag = TagBuilder().id("tag-4x1000").name("4x1000").build()
    private val fields = AccountFormFields(
        name = "Ahorros",
        balance = "1500,00",
        creditLimit = "3000000",
        debt = "500000",
        description = "Fondo de emergencia",
        gmfCategoryInput = CategoryInput.Existing("cat-impuestos")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(Outcome.Success(listOf(impuestos)))
        every { tagLister.list() } returns flowOf(Outcome.Success(listOf(gmfTag)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given the form opens, when categories and tags load, then it goes from Loading to Idle with them`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(CreateAccountUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val form = (awaitItem() as CreateAccountUiState.Idle).form
                assertEquals(listOf(impuestos), form.categories)
                assertEquals(listOf(gmfTag), form.tags)
                assertTrue(form.gmfExempt)
                assertNull(form.type)
                assertNull(form.currency)
            }
        }

    @Test
    fun `given the categories cannot be listed, when the form opens, then it shows Error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns
            flowOf(Outcome.Failure(StorageError("disk", "No se pudieron cargar las categorías")))
        val viewModel = loadedViewModel()
        assertEquals(CreateAccountUiState.Error("No se pudieron cargar las categorías"), viewModel.uiState.value)
    }

    @Test
    fun `given the tags cannot be listed, when the form opens, then it shows Error`() = runTest {
        every { tagLister.list() } returns flowOf(Outcome.Failure(StorageError("disk", "No se pudieron cargar")))
        val viewModel = loadedViewModel()
        assertEquals(CreateAccountUiState.Error("No se pudieron cargar"), viewModel.uiState.value)
    }

    @Test
    fun `given loading throws, when the form opens, then UnexpectedFailure is shown and recorded`() = runTest {
        every { tagLister.list() } returns flow { error("falla") }
        val viewModel = loadedViewModel()
        assertEquals(CreateAccountUiState.UnexpectedFailure, viewModel.uiState.value)
        verify(exactly = 1) { failureRecorder.record(any()) }
    }

    @Test
    fun `given UnexpectedFailure, when loading the form again succeeds, then the form is shown`() = runTest {
        every { tagLister.list() } returns flow { error("falla") }
        val viewModel = loadedViewModel()
        every { tagLister.list() } returns flowOf(Outcome.Success(listOf(gmfTag)))
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CreateAccountUiState.Idle)
    }

    @Test
    fun `given a savings account in COP, when chosen, then the option Exenta de 4x1000 is shown and on`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onTypeSelected(AccountType.SAVINGS)
        viewModel.onCurrencySelected(Currency.COP)
        val form = currentForm(viewModel)
        assertTrue(form.showsGmfOption)
        assertTrue(form.gmfExempt)
    }

    @Test
    fun `given a cash account in COP, when chosen, then the option is not shown`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onTypeSelected(AccountType.CASH)
        viewModel.onCurrencySelected(Currency.COP)
        assertFalse(currentForm(viewModel).showsGmfOption)
    }

    @Test
    fun `given a credit card in COP, when chosen, then the option is not shown`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onTypeSelected(AccountType.CREDIT_CARD)
        viewModel.onCurrencySelected(Currency.COP)
        assertFalse(currentForm(viewModel).showsGmfOption)
    }

    @Test
    fun `given a savings account in USD, when chosen, then the option is not shown`() = runTest {
        val viewModel = loadedViewModel()
        viewModel.onTypeSelected(AccountType.SAVINGS)
        viewModel.onCurrencySelected(Currency.USD)
        assertFalse(currentForm(viewModel).showsGmfOption)
    }

    @Test
    fun `given tax tags chosen, when turning the option on and off again, then the tags are kept`() = runTest {
        val viewModel = loadedSavingsInCop()
        viewModel.onGmfExemptChanged(false)
        viewModel.pickTag(gmfTag)
        viewModel.onGmfExemptChanged(true)
        viewModel.onGmfExemptChanged(false)
        val form = currentForm(viewModel)
        assertFalse(form.gmfExempt)
        assertEquals(listOf("4x1000"), form.tagSelection.selected.map { it.name })
    }

    @Test
    fun `given tag events, when typing adding and removing, then the tax tag selection follows`() = runTest {
        val viewModel = loadedSavingsInCop()
        viewModel.onGmfExemptChanged(false)
        viewModel.onTagTextChange("Impuestos")
        viewModel.addTag()
        viewModel.pickTag(gmfTag)
        viewModel.removeTag(0)
        assertEquals(listOf("4x1000"), currentForm(viewModel).tagSelection.selected.map { it.name })
    }

    @Test
    fun `given a new savings account in COP left exempt, when creating, then it is created exempt`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Success(savingsAccount)
        val viewModel = loadedSavingsInCop()
        viewModel.pickTag(gmfTag)
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify {
            accountCreator.create(
                CreateAccountCommand.MoneyAccount(
                    name = "Ahorros",
                    balance = "1500.00",
                    currency = Currency.COP,
                    kind = MoneyAccountKind.SAVINGS,
                    description = "Fondo de emergencia",
                    gmf = GmfInput.Exempt
                )
            )
        }
    }

    @Test
    fun `given the option turned off with Impuestos and 4x1000, when creating, then it is created not exempt`() =
        runTest {
            coEvery { accountCreator.create(any()) } returns Outcome.Success(savingsAccount)
            val viewModel = loadedSavingsInCop()
            viewModel.onGmfExemptChanged(false)
            viewModel.pickTag(gmfTag)
            viewModel.create(fields)
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify {
                accountCreator.create(
                    match {
                        (it as CreateAccountCommand.MoneyAccount).gmf ==
                            GmfInput.Taxed(CategoryInput.Existing("cat-impuestos"), listOf("4x1000"))
                    }
                )
            }
        }

    @Test
    fun `given a typed tax tag not yet added, when creating not exempt, then it is included`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Success(savingsAccount)
        val viewModel = loadedSavingsInCop()
        viewModel.onGmfExemptChanged(false)
        viewModel.onTagTextChange("Impuestos")
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify {
            accountCreator.create(
                match {
                    (it as CreateAccountCommand.MoneyAccount).gmf ==
                        GmfInput.Taxed(CategoryInput.Existing("cat-impuestos"), listOf("Impuestos"))
                }
            )
        }
    }

    @Test
    fun `given the option turned off on a cash account, when creating, then it is created exempt`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Success(savingsAccount)
        val viewModel = loadedSavingsInCop()
        viewModel.onGmfExemptChanged(false)
        viewModel.onTypeSelected(AccountType.CASH)
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify {
            accountCreator.create(
                match {
                    val command = it as CreateAccountCommand.MoneyAccount
                    command.kind == MoneyAccountKind.CASH && command.gmf == GmfInput.Exempt
                }
            )
        }
    }

    @Test
    fun `given a credit card, when creating, then a credit card command is built with raw amounts`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Success(savingsAccount)
        val viewModel = loadedViewModel()
        viewModel.onTypeSelected(AccountType.CREDIT_CARD)
        viewModel.onCurrencySelected(Currency.COP)
        viewModel.create(fields.copy(creditLimit = "3000000,50", debt = "500000"))
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify {
            accountCreator.create(
                CreateAccountCommand.CreditCard(
                    name = "Ahorros",
                    creditLimit = "3000000.50",
                    existingDebt = "500000",
                    currency = Currency.COP,
                    description = "Fondo de emergencia"
                )
            )
        }
    }

    @Test
    fun `given no type chosen, when creating, then a money account command without kind is built`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Success(savingsAccount)
        val viewModel = loadedViewModel()
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { accountCreator.create(match { (it as CreateAccountCommand.MoneyAccount).kind == null }) }
    }

    @Test
    fun `given a valid account, when creating, then emits navigation to the accounts list`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Success(savingsAccount)
        val viewModel = loadedSavingsInCop()
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.AccountsList, viewModel.navigationEvent.first())
    }

    @Test
    fun `given a creation in progress, when creating again, then ignores the second attempt`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Success(savingsAccount)
        val viewModel = loadedSavingsInCop()
        viewModel.create(fields)
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { accountCreator.create(any()) }
    }

    @Test
    fun `given a valid account, when creating, then emits Saving with the form and stays Saving`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Success(savingsAccount)
        val viewModel = loadedSavingsInCop()
        val form = currentForm(viewModel)
        viewModel.uiState.test {
            awaitItem()
            viewModel.create(fields)
            assertEquals(CreateAccountUiState.Saving(form), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `given invalid input, when creating, then maps each field error into the validation state`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Failure(
            AccountCreationError.InvalidInput(
                nameError = "El nombre es obligatorio.",
                balanceError = "El saldo inicial es obligatorio.",
                currencyError = "La moneda es obligatoria.",
                typeError = "El tipo de cuenta es obligatorio.",
                descriptionError = "La descripción no puede superar los 500 caracteres.",
                creditLimitError = "El cupo es obligatorio.",
                debtError = "La deuda actual no puede ser negativa."
            )
        )
        val viewModel = loadedSavingsInCop()
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        val error = viewModel.uiState.value as CreateAccountUiState.ValidationError
        assertEquals("El nombre es obligatorio.", error.nameError)
        assertEquals("El saldo inicial es obligatorio.", error.balanceError)
        assertEquals("La moneda es obligatoria.", error.currencyError)
        assertEquals("El tipo de cuenta es obligatorio.", error.typeError)
        assertEquals("La descripción no puede superar los 500 caracteres.", error.descriptionError)
        assertEquals("El cupo es obligatorio.", error.creditLimitError)
        assertEquals("La deuda actual no puede ser negativa.", error.debtError)
        assertEquals(AccountType.SAVINGS, error.form.type)
    }

    @Test
    fun `given the option off and no tax category, when creating, then the tax category error is shown`() =
        runTest {
            coEvery { accountCreator.create(any()) } returns Outcome.Failure(
                AccountCreationError.InvalidInput(
                    nameError = null,
                    balanceError = null,
                    currencyError = null,
                    typeError = null,
                    descriptionError = null,
                    gmfCategoryError = "La categoría del 4x1000 es obligatoria.",
                    gmfTagsError = "Máximo 5 etiquetas por gasto."
                )
            )
            val viewModel = loadedSavingsInCop()
            viewModel.onGmfExemptChanged(false)
            viewModel.create(fields.copy(gmfCategoryInput = CategoryInput.New("")))
            testDispatcher.scheduler.advanceUntilIdle()
            val error = viewModel.uiState.value as CreateAccountUiState.ValidationError
            assertEquals("La categoría del 4x1000 es obligatoria.", error.gmfCategoryError)
            assertEquals("Máximo 5 etiquetas por gasto.", error.form.tagSelection.error)
        }

    @Test
    fun `given a validation error, when changing the form, then the events keep working`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Failure(
            AccountCreationError.DuplicateName(internalMessage = "duplicate", externalMessage = "Ya existe")
        )
        val viewModel = loadedSavingsInCop()
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onGmfExemptChanged(false)
        viewModel.onCurrencySelected(Currency.USD)
        val error = viewModel.uiState.value as CreateAccountUiState.ValidationError
        assertEquals("Ya existe", error.nameError)
        assertEquals(Currency.USD, error.form.currency)
        assertFalse(error.form.gmfExempt)
    }

    @Test
    fun `given a typed tax tag that is too long, when creating not exempt, then shows it without creating`() =
        runTest {
            val viewModel = loadedSavingsInCop()
            viewModel.onGmfExemptChanged(false)
            viewModel.onTagTextChange("a".repeat(TOO_LONG_TAG))
            viewModel.create(fields)
            testDispatcher.scheduler.advanceUntilIdle()
            val error = viewModel.uiState.value as CreateAccountUiState.ValidationError
            assertTrue(error.form.tagSelection.error != null)
            coVerify(exactly = 0) { accountCreator.create(any()) }
        }

    @Test
    fun `given a duplicate name, when creating, then emits validation error next to the name`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Failure(
            AccountCreationError.DuplicateName(
                internalMessage = "duplicate",
                externalMessage = "Ya tienes una cuenta con ese nombre."
            )
        )
        val viewModel = loadedSavingsInCop()
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        val error = viewModel.uiState.value as CreateAccountUiState.ValidationError
        assertEquals("Ya tienes una cuenta con ese nombre.", error.nameError)
    }

    @Test
    fun `given a crypto failure, when creating, then keeps the form with the message`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Failure(
            AccountCreationError.CryptoFailure(
                internalMessage = "crypto",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )
        val viewModel = loadedSavingsInCop()
        val formBeforeSaving = (viewModel.uiState.value as CreateAccountUiState.Idle).form
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            CreateAccountUiState.SaveFailed(formBeforeSaving, "Algo salió mal. Intente de nuevo más tarde"),
            viewModel.uiState.value
        )
    }

    @Test
    fun `given a failed save, when changing the type, then the form stays editable`() = runTest {
        coEvery { accountCreator.create(any()) } returns Outcome.Failure(
            AccountCreationError.StorageFailure("storage", "No se pudo guardar")
        )
        val viewModel = loadedSavingsInCop()
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onTypeSelected(AccountType.CASH)
        val state = viewModel.uiState.value as CreateAccountUiState.SaveFailed
        assertEquals(AccountType.CASH, state.form.type)
    }

    @Test
    fun `given a failed save, when creating again, then it saves`() = runTest {
        coEvery { accountCreator.create(any()) } returnsMany listOf(
            Outcome.Failure(AccountCreationError.StorageFailure("storage", "No se pudo guardar")),
            Outcome.Success(savingsAccount)
        )
        val viewModel = loadedSavingsInCop()
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.AccountsList, viewModel.navigationEvent.first())
    }

    @Test
    fun `given the save throws, when creating an account, then the prior state is restored and reported`() =
        runTest {
            val viewModel = loadedSavingsInCop()
            val stateBeforeSaving = viewModel.uiState.value
            coEvery { accountCreator.create(any()) } throws IllegalStateException("falla")
            viewModel.create(fields)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(stateBeforeSaving, viewModel.uiState.value)
            viewModel.unexpectedFailureEvent.test {
                assertEquals(Unit, awaitItem())
                expectNoEvents()
            }
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given a save that takes 60 seconds, when creating an account, then it completes with no failure`() = runTest {
        coEvery { accountCreator.create(any()) } coAnswers {
            delay(60.seconds)
            Outcome.Success(savingsAccount)
        }
        val viewModel = loadedSavingsInCop()
        viewModel.create(fields)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.AccountsList, viewModel.navigationEvent.first())
        verify(exactly = 0) { failureRecorder.record(any()) }
    }

    @Test
    fun `given an expected failure, when creating an account, then it shows as before and is not recorded`() =
        runTest {
            coEvery { accountCreator.create(any()) } returns Outcome.Failure(
                AccountCreationError.StorageFailure("storage", "No se pudo guardar")
            )
            val viewModel = loadedSavingsInCop()
            val formBeforeSaving = (viewModel.uiState.value as CreateAccountUiState.Idle).form
            viewModel.create(fields)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                CreateAccountUiState.SaveFailed(formBeforeSaving, "No se pudo guardar"),
                viewModel.uiState.value
            )
            verify(exactly = 0) { failureRecorder.record(any()) }
        }

    private fun buildViewModel() = CreateAccountViewModel(
        accountCreator = accountCreator,
        categoryLister = categoryLister,
        tagLister = tagLister,
        failureRecorder = failureRecorder,
        ioDispatcher = testDispatcher
    )

    private fun loadedViewModel(): CreateAccountViewModel {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun loadedSavingsInCop(): CreateAccountViewModel {
        val viewModel = loadedViewModel()
        viewModel.onTypeSelected(AccountType.SAVINGS)
        viewModel.onCurrencySelected(Currency.COP)
        return viewModel
    }

    private fun currentForm(viewModel: CreateAccountViewModel): AccountForm =
        when (val state = viewModel.uiState.value) {
            is CreateAccountUiState.Idle -> state.form
            is CreateAccountUiState.ValidationError -> state.form
            else -> error("Unexpected state $state")
        }

    private companion object {
        const val TOO_LONG_TAG = 31
    }
}
