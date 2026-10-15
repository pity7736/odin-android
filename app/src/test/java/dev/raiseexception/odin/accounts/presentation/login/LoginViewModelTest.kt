package dev.raiseexception.odin.accounts.presentation.login

import app.cash.turbine.test
import dev.raiseexception.odin.accounts.application.usecase.UserAuthenticator
import dev.raiseexception.odin.accounts.domain.LoginError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.seconds

private const val TEST_BYTE_ARRAY_SIZE = 8

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val userAuthenticator = mockk<UserAuthenticator>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LoginViewModel

    private val user = User(
        id = "id",
        wrappedMasterKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + 1).toByte() }
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel(userAuthenticator, failureRecorder)
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
        coEvery { userAuthenticator.authenticate(any()) } returns Outcome.Success(user)

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login("validPassword1")
            assertEquals(LoginUiState.Loading, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `given valid credentials, when logging in, then emits navigation event to Home`() = runTest {
        coEvery { userAuthenticator.authenticate(any()) } returns Outcome.Success(user)
        viewModel.login("validPassword1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.Home, viewModel.navigationEvent.first())
    }

    @Test
    fun `given a login in progress, when logging in again, then ignores the second attempt`() = runTest {
        coEvery { userAuthenticator.authenticate(any()) } returns Outcome.Success(user)

        viewModel.login("validPassword1")
        viewModel.login("validPassword1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { userAuthenticator.authenticate(any()) }
    }

    @Test
    fun `given an incorrect password, when logging in, then emits Error with incorrect password message`() = runTest {
        coEvery { userAuthenticator.authenticate(any()) } returns Outcome.Failure(
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
        coEvery { userAuthenticator.authenticate(any()) } returns Outcome.Failure(
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
        coEvery { userAuthenticator.authenticate(any()) } returns Outcome.Failure(
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

    @Test
    fun `given the save throws, when logging in, then the prior state is restored and reported`() =
        runTest {
            coEvery { userAuthenticator.authenticate(any()) } returns Outcome.Failure(
                LoginError.EmptyPassword(internalMessage = "blank", externalMessage = "Ingrese su contraseña")
            )
            viewModel.login("validPassword1")
            testDispatcher.scheduler.advanceUntilIdle()
            coEvery { userAuthenticator.authenticate(any()) } throws IllegalStateException("falla")
            viewModel.login("validPassword1")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                LoginUiState.ValidationError(passwordError = "Ingrese su contraseña"),
                viewModel.uiState.value
            )
            viewModel.unexpectedFailureEvent.test {
                assertEquals(Unit, awaitItem())
                expectNoEvents()
            }
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given a save that takes 60 seconds, when logging in, then it completes with no failure`() = runTest {
        coEvery { userAuthenticator.authenticate(any()) } coAnswers {
            delay(60.seconds)
            Outcome.Success(user)
        }
        viewModel.login("validPassword1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.Home, viewModel.navigationEvent.first())
        verify(exactly = 0) { failureRecorder.record(any()) }
    }

    @Test
    fun `given an expected failure, when logging in, then its own state is shown and nothing is recorded`() = runTest {
        coEvery { userAuthenticator.authenticate(any()) } returns Outcome.Failure(
            LoginError.InvalidCredentials(internalMessage = "wrong", externalMessage = "Contraseña incorrecta")
        )
        viewModel.login("validPassword1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LoginUiState.Error("Contraseña incorrecta"), viewModel.uiState.value)
        verify(exactly = 0) { failureRecorder.record(any()) }
    }

    @Test
    fun `given the authenticator throws, when logging in, then the state leaves Loading`() = runTest {
        coEvery { userAuthenticator.authenticate(any()) } throws IllegalStateException("falla")
        viewModel.login("validPassword1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LoginUiState.Idle, viewModel.uiState.value)
    }
}
