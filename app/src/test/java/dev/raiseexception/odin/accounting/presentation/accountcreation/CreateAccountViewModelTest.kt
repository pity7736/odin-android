package dev.raiseexception.odin.accounting.presentation.accountcreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.shared.domain.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAccountViewModelTest {

    private val accountCreator = mockk<AccountCreator>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CreateAccountViewModel

    private val savingsAccount = (
        Account.create(
            name = "Ahorros",
            initialBalance = "1500.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = "Fondo de emergencia"
        ) as Outcome.Success
        ).value

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CreateAccountViewModel(accountCreator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given initial state, when observed, then emits Idle`() = runTest {
        viewModel.uiState.test {
            assertEquals(CreateAccountUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `given a valid account, when creating, then emits navigation to the accounts list`() = runTest {
        coEvery { accountCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Success(savingsAccount)
        viewModel.create("Ahorros", "1500.00", Currency.COP, AccountType.SAVINGS, "Fondo de emergencia")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.AccountsList, viewModel.navigationEvent.first())
    }

    @Test
    fun `given a creation in progress, when creating again, then ignores the second attempt`() = runTest {
        coEvery { accountCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Success(savingsAccount)

        viewModel.create("Ahorros", "1500.00", Currency.COP, AccountType.SAVINGS, "")
        viewModel.create("Ahorros", "1500.00", Currency.COP, AccountType.SAVINGS, "")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { accountCreator.create(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given a valid account, when creating, then emits Loading and stays Loading`() = runTest {
        coEvery { accountCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Success(savingsAccount)
        viewModel.uiState.test {
            assertEquals(CreateAccountUiState.Idle, awaitItem())
            viewModel.create("Ahorros", "1500.00", Currency.COP, AccountType.SAVINGS, "")
            assertEquals(CreateAccountUiState.Loading, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `given invalid input, when creating, then maps each field error into the validation state`() = runTest {
        coEvery { accountCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
            AccountCreationError.InvalidInput(
                nameError = "El nombre es obligatorio.",
                balanceError = "El saldo inicial es obligatorio.",
                currencyError = "La moneda es obligatoria.",
                typeError = "El tipo de cuenta es obligatorio.",
                descriptionError = "La descripción no puede superar los 500 caracteres."
            )
        )
        viewModel.uiState.test {
            assertEquals(CreateAccountUiState.Idle, awaitItem())
            viewModel.create("", "", null, null, "")
            assertEquals(CreateAccountUiState.Loading, awaitItem())
            val error = awaitItem() as CreateAccountUiState.ValidationError
            assertEquals("El nombre es obligatorio.", error.nameError)
            assertEquals("El saldo inicial es obligatorio.", error.balanceError)
            assertEquals("La moneda es obligatoria.", error.currencyError)
            assertEquals("El tipo de cuenta es obligatorio.", error.typeError)
            assertEquals("La descripción no puede superar los 500 caracteres.", error.descriptionError)
        }
    }

    @Test
    fun `given a duplicate name, when creating, then emits validation error next to the name`() = runTest {
        coEvery { accountCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
            AccountCreationError.DuplicateName(
                internalMessage = "duplicate",
                externalMessage = "Ya tienes una cuenta con ese nombre."
            )
        )
        viewModel.uiState.test {
            assertEquals(CreateAccountUiState.Idle, awaitItem())
            viewModel.create("Ahorros", "10.00", Currency.COP, AccountType.SAVINGS, "")
            assertEquals(CreateAccountUiState.Loading, awaitItem())
            val error = awaitItem() as CreateAccountUiState.ValidationError
            assertEquals("Ya tienes una cuenta con ese nombre.", error.nameError)
        }
    }

    @Test
    fun `given a crypto failure, when creating, then emits a general error`() = runTest {
        coEvery { accountCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
            AccountCreationError.CryptoFailure(
                internalMessage = "crypto",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )
        viewModel.uiState.test {
            assertEquals(CreateAccountUiState.Idle, awaitItem())
            viewModel.create("Ahorros", "10.00", Currency.COP, AccountType.SAVINGS, "")
            assertEquals(CreateAccountUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is CreateAccountUiState.Error)
            assertEquals("Algo salió mal. Intente de nuevo más tarde", (state as CreateAccountUiState.Error).message)
        }
    }

    @Test
    fun `given a storage failure, when creating, then emits a general error`() = runTest {
        coEvery { accountCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
            AccountCreationError.StorageFailure(
                internalMessage = "storage",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )
        viewModel.uiState.test {
            assertEquals(CreateAccountUiState.Idle, awaitItem())
            viewModel.create("Ahorros", "10.00", Currency.COP, AccountType.SAVINGS, "")
            assertEquals(CreateAccountUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is CreateAccountUiState.Error)
            assertEquals("Algo salió mal. Intente de nuevo más tarde", (state as CreateAccountUiState.Error).message)
        }
    }
}
