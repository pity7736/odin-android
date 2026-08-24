package dev.raiseexception.odin.accounts.presentation.registration

import app.cash.turbine.test
import dev.raiseexception.odin.accounts.application.usecase.UserRegistrar
import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.shared.domain.Outcome
import io.mockk.coEvery
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

private const val TEST_BYTE_ARRAY_SIZE = 8

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {

    private val userRegistrar = mockk<UserRegistrar>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RegistrationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = RegistrationViewModel(userRegistrar)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given initial state, when observed, then emits Idle`() = runTest {
        viewModel.uiState.test {
            assertEquals(RegistrationUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `given valid password, when registering, then emits Loading and stays Loading`() = runTest {
        val user = User(
            id = "id",
            salt = ByteArray(TEST_BYTE_ARRAY_SIZE) { it.toByte() },
            wrappedMasterKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + 1).toByte() }
        )
        coEvery { userRegistrar.register("validPassword1", "validPassword1") } returns Outcome.Success(user)

        viewModel.uiState.test {
            assertEquals(RegistrationUiState.Idle, awaitItem())
            viewModel.register("validPassword1", "validPassword1")
            assertEquals(RegistrationUiState.Loading, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `given invalid password, when registering, then emits ValidationError with password error`() = runTest {
        coEvery { userRegistrar.register("short", "short") } returns Outcome.Failure(
            RegistrationError.InvalidPassword(
                internalMessage = "Password must be at least 12 characters",
                externalMessage = "La contraseña debe tener al menos 12 caracteres"
            )
        )

        viewModel.uiState.test {
            assertEquals(RegistrationUiState.Idle, awaitItem())
            viewModel.register("short", "short")
            assertEquals(RegistrationUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is RegistrationUiState.ValidationError)
            assertEquals(
                "La contraseña debe tener al menos 12 caracteres",
                (state as RegistrationUiState.ValidationError).passwordError
            )
        }
    }

    @Test
    fun `given mismatched passwords, when registering, then emits ValidationError with confirmation error`() = runTest {
        coEvery { userRegistrar.register("validPassword1", "differentPassword") } returns Outcome.Failure(
            RegistrationError.PasswordsDoNotMatch(
                internalMessage = "Password and confirmation do not match",
                externalMessage = "Las contraseñas no coinciden"
            )
        )

        viewModel.uiState.test {
            assertEquals(RegistrationUiState.Idle, awaitItem())
            viewModel.register("validPassword1", "differentPassword")
            assertEquals(RegistrationUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is RegistrationUiState.ValidationError)
            assertEquals(
                "Las contraseñas no coinciden",
                (state as RegistrationUiState.ValidationError).passwordConfirmationError
            )
        }
    }

    @Test
    fun `given crypto failure, when registering, then emits Error with message`() = runTest {
        coEvery { userRegistrar.register("validPassword1", "validPassword1") } returns Outcome.Failure(
            RegistrationError.CryptoFailure(
                internalMessage = "Key derivation failed",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )

        viewModel.uiState.test {
            assertEquals(RegistrationUiState.Idle, awaitItem())
            viewModel.register("validPassword1", "validPassword1")
            assertEquals(RegistrationUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is RegistrationUiState.Error)
            assertEquals(
                "Algo salió mal. Intente de nuevo más tarde",
                (state as RegistrationUiState.Error).message
            )
        }
    }

    @Test
    fun `given storage failure, when registering, then emits Error with message`() = runTest {
        coEvery { userRegistrar.register("validPassword1", "validPassword1") } returns Outcome.Failure(
            RegistrationError.StorageFailure(
                internalMessage = "Storage failed",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )

        viewModel.uiState.test {
            assertEquals(RegistrationUiState.Idle, awaitItem())
            viewModel.register("validPassword1", "validPassword1")
            assertEquals(RegistrationUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is RegistrationUiState.Error)
            assertEquals(
                "Algo salió mal. Intente de nuevo más tarde",
                (state as RegistrationUiState.Error).message
            )
        }
    }

    @Test
    fun `given valid password, when registering, then emits navigation event to Home`() = runTest {
        val user = User(
            id = "id",
            salt = ByteArray(TEST_BYTE_ARRAY_SIZE) { it.toByte() },
            wrappedMasterKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + 1).toByte() }
        )
        coEvery { userRegistrar.register("validPassword1", "validPassword1") } returns Outcome.Success(user)
        viewModel.register("validPassword1", "validPassword1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.Home, viewModel.navigationEvent.first())
    }

    @Test
    fun `given user already registered, when registering, then emits Error with message`() = runTest {
        coEvery { userRegistrar.register("validPassword1", "validPassword1") } returns Outcome.Failure(
            RegistrationError.AlreadyRegistered(
                internalMessage = "User already registered on this device",
                externalMessage = "Ya existe una cuenta en este dispositivo"
            )
        )

        viewModel.uiState.test {
            assertEquals(RegistrationUiState.Idle, awaitItem())
            viewModel.register("validPassword1", "validPassword1")
            assertEquals(RegistrationUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is RegistrationUiState.Error)
            assertEquals(
                "Ya existe una cuenta en este dispositivo",
                (state as RegistrationUiState.Error).message
            )
        }
    }
}
