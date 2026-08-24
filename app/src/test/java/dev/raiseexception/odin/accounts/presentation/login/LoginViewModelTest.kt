package dev.raiseexception.odin.accounts.presentation.login

import app.cash.turbine.test
import dev.raiseexception.odin.accounts.application.usecase.UserAuthenticator
import dev.raiseexception.odin.accounts.domain.LoginError
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
class LoginViewModelTest {

    private val userAuthenticator = mockk<UserAuthenticator>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LoginViewModel

    private val user = User(
        id = "id",
        salt = ByteArray(TEST_BYTE_ARRAY_SIZE) { it.toByte() },
        wrappedMasterKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + 1).toByte() }
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel(userAuthenticator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given initial state, when observed, then emits Idle`() = runTest {
        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `given valid credentials, when logging in, then emits Loading and stays Loading`() = runTest {
        coEvery { userAuthenticator.authenticate("validPassword1") } returns Outcome.Success(user)

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login("validPassword1")
            assertEquals(LoginUiState.Loading, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `given valid credentials, when logging in, then emits navigation event to Home`() = runTest {
        coEvery { userAuthenticator.authenticate("validPassword1") } returns Outcome.Success(user)
        viewModel.login("validPassword1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.Home, viewModel.navigationEvent.first())
    }

    @Test
    fun `given an incorrect password, when logging in, then emits Error with incorrect password message`() = runTest {
        coEvery { userAuthenticator.authenticate("wrongPassword1") } returns Outcome.Failure(
            LoginError.InvalidCredentials(
                internalMessage = "Master key unwrap failed: incorrect password",
                externalMessage = "Contraseña incorrecta"
            )
        )

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login("wrongPassword1")
            assertEquals(LoginUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is LoginUiState.Error)
            assertEquals("Contraseña incorrecta", (state as LoginUiState.Error).message)
        }
    }

    @Test
    fun `given a blank password, when logging in, then emits ValidationError and does not navigate`() = runTest {
        coEvery { userAuthenticator.authenticate("   ") } returns Outcome.Failure(
            LoginError.EmptyPassword(
                internalMessage = "Password must not be blank",
                externalMessage = "Ingrese su contraseña"
            )
        )

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login("   ")
            assertEquals(LoginUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is LoginUiState.ValidationError)
            assertEquals("Ingrese su contraseña", (state as LoginUiState.ValidationError).passwordError)
        }
    }

    @Test
    fun `given a crypto failure, when logging in, then emits Error with general message`() = runTest {
        coEvery { userAuthenticator.authenticate("validPassword1") } returns Outcome.Failure(
            LoginError.CryptoFailure(
                internalMessage = "Key derivation failed",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login("validPassword1")
            assertEquals(LoginUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is LoginUiState.Error)
            assertEquals("Algo salió mal. Intente de nuevo más tarde", (state as LoginUiState.Error).message)
        }
    }
}
